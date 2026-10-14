// ============================================================
//  Helpers compartidos por las pruebas de carga y concurrencia de SIGAPP.
//  No es una prueba por si sola: la importan todos los scripts de la carpeta.
//
//  Todo se parametriza por variable de entorno para no tocar los scripts:
//    k6 run -e BASE_URL=http://localhost:8082 -e EMPRESA=01 02-carga.js
// ============================================================
import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Rate } from 'k6/metrics';

// ------------------------------------------------------------
//  Configuracion
// ------------------------------------------------------------
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';
export const EMPRESA = __ENV.EMPRESA || '01';

export const ADMIN_USER = __ENV.ADMIN_USER;
export const ADMIN_PASS = __ENV.ADMIN_PASS;
export const CONTADOR_DOC = __ENV.CONTADOR_DOC || '666';
export const CONTADOR_COD = __ENV.CONTADOR_COD || '846166';

// Conteo fisico
export const BODEGA_CONTEO = __ENV.BODEGA_CONTEO || 'FMIA';
export const DIVISION = __ENV.DIVISION || '16023';

// Traspasos: una persona con activos libres en una bodega personal (PE)
export const BODEGA_PE = __ENV.BODEGA_PE || '1111';
export const PERSONA_FUENTE = __ENV.PERSONA_FUENTE || 'PV11011';

// Requisiciones: bodega de la bandeja
export const REQ_BODEGA = __ENV.REQ_BODEGA || 'F11';

// Endpoints que se dejan fuera de la mezcla, separados por coma. Sirve para
// aislar uno que ya se sabe roto y que no tape la medida del resto:
//   -e EXCLUIR="GET /bodegas/division/{d}"
const EXCLUIR = (__ENV.EXCLUIR || '').split(',').map((x) => x.trim()).filter(Boolean);

/** Firma PNG de 1x1 pixel. Es una imagen valida para FirmaUtil (magic bytes PNG). */
export const FIRMA_PNG =
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// ------------------------------------------------------------
//  Metricas comunes
// ------------------------------------------------------------

/**
 * Errores que NO son una respuesta de negocio: timeouts, conexion rechazada,
 * pool de conexiones agotado, errores de Oracle de infraestructura. Es la
 * metrica que dice si el backend aguanta la carga. Un rechazo de negocio
 * ("ya fue procesado") es una respuesta correcta y no cuenta aqui.
 */
export const erroresTecnicos = new Counter('errores_tecnicos');

/**
 * Peticiones que fallaron porque Hikari no entrego una conexion a tiempo. El
 * pool tiene 5 conexiones (spring.datasource.hikari.maximum-pool-size): con
 * concurrencia alta este es el primer limite que se alcanza.
 */
export const poolAgotado = new Counter('pool_agotado');

/** Proporcion de respuestas correctas (exito o rechazo de negocio esperado). */
export const respuestasValidas = new Rate('respuestas_validas');

// ------------------------------------------------------------
//  Utilidades HTTP
// ------------------------------------------------------------
export function cuerpo(res) {
    try {
        return res.json();
    } catch (e) {
        return null;
    }
}

export function conToken(token, endpoint) {
    const params = { headers: Object.assign({ Authorization: `Bearer ${token}` }, JSON_HEADERS) };
    if (endpoint) {
        params.tags = { name: endpoint, endpoint };
    }
    return params;
}

export function sinToken(endpoint) {
    return { headers: JSON_HEADERS, tags: { name: endpoint, endpoint } };
}

// Hikari: "Connection is not available, request timed out after 30000ms".
// Oracle: ORA-12519/12520 sin manejador, ORA-00018/00020 sesiones o procesos
// agotados, ORA-01000 cursores abiertos.
const PATRON_POOL = /Connection is not available|Unable to acquire JDBC|request timed out/i;
const PATRON_INFRA = /ORA-0001[89]|ORA-00020|ORA-01000|ORA-125\d\d|ORA-03113|ORA-03114|Closed Connection|Socket read timed out/i;

/**
 * Clasifica una respuesta en uno de cuatro resultados:
 *
 *   ok       HTTP 2xx y, si la respuesta va envuelta, code 0.
 *   negocio  Rechazo de negocio: los modulos de traspasos y requisiciones lo
 *            devuelven como HTTP 500 con code -1 y el mensaje del paquete; el
 *            conteo fisico como HTTP 400 por el GlobalExceptionHandler.
 *   pool     El pool de conexiones no entrego conexion a tiempo.
 *   auth     HTTP 401 o 403: token ausente, vencido o sin el rol. Invalida,
 *            pero no dice nada del rendimiento.
 *   tecnico  Cualquier otra falla: timeout del cliente (status 0), 5xx sin
 *            envoltura, error de infraestructura de Oracle.
 *
 * Distinguir negocio de tecnico es lo que permite que una prueba de
 * concurrencia diga "una gano y las demas fueron rechazadas" sin confundir el
 * rechazo con una caida.
 */
export function clasificar(res) {
    if (res.status === 0) {
        return 'tecnico';
    }
    if (res.status === 401 || res.status === 403) {
        return 'auth';
    }
    // Recurso inexistente (p. ej. una firma que aun no se registro): el
    // endpoint decide si lo acepta con 'esperado'.
    if (res.status === 404) {
        return 'negocio';
    }

    const b = cuerpo(res);
    const texto = b ? `${b.msg || ''} ${b.message || ''}` : `${res.body || ''}`;

    if (PATRON_POOL.test(texto)) {
        return 'pool';
    }
    if (PATRON_INFRA.test(texto)) {
        return 'tecnico';
    }
    if (res.status >= 200 && res.status < 300) {
        if (b && typeof b.code === 'number' && b.code !== 0) {
            return 'negocio';
        }
        if (b && b.success === false) {
            return 'negocio';
        }
        return 'ok';
    }
    // Envoltura de traspasos y requisiciones: 500 + code -1 = rechazo controlado.
    if (res.status === 500 && b && b.code === -1) {
        return 'negocio';
    }
    // GlobalExceptionHandler: BussinessException -> 400, validacion -> 400.
    if (res.status === 400 && b && b.success === false) {
        return 'negocio';
    }
    return 'tecnico';
}

/** Registra la clasificacion en las metricas comunes y la devuelve. */
export function registrar(res) {
    const tipo = clasificar(res);
    if (tipo === 'pool') {
        poolAgotado.add(1, { endpoint: res.request && res.request.tags ? res.request.tags.endpoint : '' });
        erroresTecnicos.add(1);
    } else if (tipo === 'tecnico') {
        erroresTecnicos.add(1);
    }
    return tipo;
}

/** Mensaje legible de una respuesta, para los reportes de concurrencia. */
export function mensaje(res) {
    const b = cuerpo(res);
    if (b && (b.msg || b.message)) {
        return String(b.msg || b.message).slice(0, 200);
    }
    return `HTTP ${res.status} ${String(res.body || res.error || '').slice(0, 60)}`;
}

// ------------------------------------------------------------
//  Autenticacion
// ------------------------------------------------------------

/**
 * Login del contador. Se hace en setup(): que cada VU haga login en cada
 * iteracion mediria el login en vez del endpoint bajo prueba.
 *
 * El login NO consume el codigo temporal, asi que se puede repetir.
 */
export function loginContador() {
    const res = http.post(`${BASE_URL}/api/v1/auth/login/contador`,
        JSON.stringify({ documento: CONTADOR_DOC, codigoTemporal: CONTADOR_COD }),
        sinToken('POST /auth/login/contador'));
    const b = cuerpo(res);
    if (res.status !== 200 || !b || !b.token) {
        fail(`Login de contador fallido (HTTP ${res.status}): ${res.body}`);
    }
    return { token: b.token, refreshToken: b.refreshToken };
}

/**
 * Login de administrador (usuario de USUABADA). Sin credenciales se devuelve
 * un token de relleno: solo sirve para lecturas y solo con
 * security.developer-mode=true, porque las escrituras sacan el usuario del token.
 */
export function loginAdmin(exigido) {
    if (!ADMIN_USER || !ADMIN_PASS) {
        if (exigido) {
            fail('Faltan ADMIN_USER / ADMIN_PASS: las escrituras exigen un token de administrador real');
        }
        console.warn('Sin ADMIN_USER / ADMIN_PASS: traspasos y requisiciones solo en lectura, exige developer-mode');
        return 'sin-credenciales';
    }
    const res = http.post(`${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ username: ADMIN_USER, password: ADMIN_PASS }),
        sinToken('POST /auth/login'));
    const b = cuerpo(res);
    if (res.status !== 200 || !b || !b.token) {
        fail(`Login de administrador fallido (HTTP ${res.status}): ${res.body}`);
    }
    return b.token;
}

// ------------------------------------------------------------
//  Descubrimiento de datos reales
// ------------------------------------------------------------

/**
 * Busca en el ambiente los identificadores que necesitan los detalles: un
 * tramite de traspaso, uno con firma, una requisicion abierta y una
 * registrada. Se llama en setup() y el resultado se reparte a todos los VUs.
 */
export function descubrirDatos(tokenAdmin) {
    const h = conToken(tokenAdmin);
    const datos = { tramite: null, tramiteFirmado: null, requisicion: null, requisicionFirmada: null };

    for (const estado of ['re', 'ap', 'pe', 'na']) {
        const b = cuerpo(http.get(`${BASE_URL}/api/v1/traspasos/list?estado=${estado}&empresa=${EMPRESA}`, h));
        const lista = b && b.list ? b.list : [];
        if (lista.length > 0) {
            if (datos.tramite === null) {
                datos.tramite = lista[0].id;
            }
            if (estado === 're' && datos.tramiteFirmado === null) {
                datos.tramiteFirmado = lista[0].id;
            }
        }
    }

    const abiertas = cuerpo(http.get(
        `${BASE_URL}/api/v1/requisiciones?estado=in&empresa=${EMPRESA}&bodega=${REQ_BODEGA}&desde=2025-01-01`, h));
    if (abiertas && abiertas.list && abiertas.list.length > 0) {
        datos.requisicion = refRequisicion(abiertas.list[0]);
    }

    const registradas = cuerpo(http.get(
        `${BASE_URL}/api/v1/requisiciones?estado=rg&empresa=${EMPRESA}&desde=2025-01-01`, h));
    if (registradas && registradas.list && registradas.list.length > 0) {
        datos.requisicionFirmada = refRequisicion(registradas.list[0]);
        if (datos.requisicion === null) {
            datos.requisicion = datos.requisicionFirmada;
        }
    }

    console.log(`Datos descubiertos: ${JSON.stringify(datos)}`);
    return datos;
}

function refRequisicion(r) {
    return `${r.empresa}/${r.tipoDocumento}/${r.numero}`;
}

// ------------------------------------------------------------
//  Catalogo de endpoints de lectura
// ------------------------------------------------------------
//
//  Cada entrada:
//    nombre    etiqueta 'endpoint' de las metricas
//    rol       'publico' | 'contador' | 'admin' | 'refresh': que token usa
//    perfil    quien lo llama en la vida real, si no se deduce del rol
//    peso      frecuencia relativa en la mezcla de carga, segun el uso real:
//              la app del contador consulta sus pendientes mucho mas que el
//              catalogo de empresas
//    esperado  codigos HTTP validos. 204 = sin datos, no es fallo. 404 en las
//              firmas = el documento no tiene esa firma, tampoco lo es
//    sobre     true si la respuesta va envuelta y debe traer code 0
//    url       funcion que arma la URL con los datos descubiertos
//
//  Las autenticaciones (login, refresh) son POST pero no escriben: entran en
//  la mezcla porque la app las llama al abrir y al renovar el token.
// ------------------------------------------------------------
export const ENDPOINTS = [
    // Publicos
    { nombre: 'GET /health', rol: 'publico', perfil: 'publico', peso: 2, esperado: [200],
        url: () => '/api/v1/health' },
    { nombre: 'GET /health/info', rol: 'publico', perfil: 'publico', peso: 2, esperado: [200],
        url: () => '/api/v1/health/info' },

    // Autenticacion
    { nombre: 'POST /auth/login/contador', rol: 'publico', peso: 1, esperado: [200], metodo: 'POST',
        url: () => '/api/v1/auth/login/contador',
        body: () => JSON.stringify({ documento: CONTADOR_DOC, codigoTemporal: CONTADOR_COD }) },
    { nombre: 'POST /auth/refresh', rol: 'refresh', peso: 1, esperado: [200], metodo: 'POST',
        url: () => '/api/v1/auth/refresh' },
    { nombre: 'POST /auth/login', rol: 'publico', perfil: 'admin', peso: 1, esperado: [200], metodo: 'POST', requiereAdmin: true,
        url: () => '/api/v1/auth/login',
        body: () => JSON.stringify({ username: ADMIN_USER, password: ADMIN_PASS }) },

    // Conteo fisico (app del contador)
    { nombre: 'GET /conteo-fisico/pendientes', rol: 'contador', peso: 10, esperado: [200, 204],
        url: () => '/api/v1/conteo-fisico/pendientes' },
    { nombre: 'GET /empresas/getAll', rol: 'contador', peso: 2, esperado: [200],
        url: () => '/api/v1/empresas/getAll' },
    { nombre: 'GET /bodegas/empresa/{e}/{tipo}', rol: 'contador', peso: 3, esperado: [200, 204],
        url: () => `/api/v1/bodegas/empresa/${EMPRESA}/PE` },
    { nombre: 'GET /bodegas/division/{d}', rol: 'contador', peso: 1, esperado: [200, 204],
        url: () => `/api/v1/bodegas/division/${DIVISION}` },
    { nombre: 'GET /bodegas/conteo-pendientes/{e}', rol: 'contador', peso: 3, esperado: [200, 204],
        url: () => `/api/v1/bodegas/conteo-pendientes/${EMPRESA}` },
    { nombre: 'GET /articulos/asignados/{b}/{e}', rol: 'contador', peso: 4, esperado: [200, 204],
        url: () => `/api/v1/articulos/asignados/${BODEGA_CONTEO}/${EMPRESA}` },
    { nombre: 'GET /articulos/asignados?responsable', rol: 'contador', peso: 2, esperado: [200, 204],
        url: () => `/api/v1/articulos/asignados?responsable=${CONTADOR_DOC}&bodega=${BODEGA_CONTEO}` },
    { nombre: 'GET /personal/buscar', rol: 'contador', peso: 1, esperado: [200, 204],
        url: () => `/api/v1/personal/buscar?cedula=${CONTADOR_DOC}` },

    // Traspasos (administrador)
    { nombre: 'GET /traspasos/list?pe', rol: 'admin', peso: 4, esperado: [200], sobre: true,
        url: () => `/api/v1/traspasos/list?estado=pe&empresa=${EMPRESA}` },
    { nombre: 'GET /traspasos/list?pr', rol: 'admin', peso: 2, esperado: [200], sobre: true,
        url: () => `/api/v1/traspasos/list?estado=pr&empresa=${EMPRESA}` },
    { nombre: 'GET /traspasos/personas', rol: 'admin', peso: 2, esperado: [200], sobre: true,
        url: () => `/api/v1/traspasos/personas?bodega=${BODEGA_PE}&empresa=${EMPRESA}` },
    { nombre: 'GET /traspasos/activos', rol: 'admin', peso: 3, esperado: [200], sobre: true,
        url: () => `/api/v1/traspasos/activos?persona=${PERSONA_FUENTE}&bodega=${BODEGA_PE}&empresa=${EMPRESA}` },
    { nombre: 'GET /traspasos/get/{id}', rol: 'admin', peso: 3, esperado: [200], sobre: true, requiere: 'tramite',
        url: (d) => `/api/v1/traspasos/get/${d.tramite}` },
    { nombre: 'GET /traspasos/sign/{id}/{tipo}', rol: 'admin', peso: 1, esperado: [200, 404], requiere: 'tramiteFirmado',
        url: (d) => `/api/v1/traspasos/sign/${d.tramiteFirmado}/FU` },

    // Requisiciones (administrador)
    { nombre: 'GET /requisiciones/tipos', rol: 'admin', peso: 1, esperado: [200], sobre: true,
        url: () => '/api/v1/requisiciones/tipos' },
    { nombre: 'GET /requisiciones?in', rol: 'admin', peso: 4, esperado: [200], sobre: true,
        url: () => `/api/v1/requisiciones?estado=in&empresa=${EMPRESA}&bodega=${REQ_BODEGA}` },
    { nombre: 'GET /requisiciones?rg', rol: 'admin', peso: 1, esperado: [200], sobre: true,
        url: () => `/api/v1/requisiciones?estado=rg&empresa=${EMPRESA}` },
    { nombre: 'GET /requisiciones/{e}/{t}/{n}', rol: 'admin', peso: 3, esperado: [200], sobre: true, requiere: 'requisicion',
        url: (d) => `/api/v1/requisiciones/${d.requisicion}` },
    { nombre: 'GET /requisiciones/.../firmas/{tipo}', rol: 'admin', peso: 1, esperado: [200, 404], requiere: 'requisicionFirmada',
        url: (d) => `/api/v1/requisiciones/${d.requisicionFirmada}/firmas/SA` },
];

/**
 * Endpoints que se pueden ejecutar con los datos y credenciales disponibles.
 * Los que dependen de un dato que el ambiente no tiene se omiten con aviso,
 * en vez de contarse como fallos.
 */
export function endpointsDisponibles(ctx, perfil) {
    const datos = ctx.datos;
    return ENDPOINTS.filter((e) => {
        if (perfil && perfilDe(e) !== perfil) {
            return false;
        }
        if (EXCLUIR.includes(e.nombre)) {
            return false;
        }
        if (e.rol === 'admin' && !ctx.adminDisponible) {
            return false;
        }
        if (e.requiereAdmin && (!ADMIN_USER || !ADMIN_PASS)) {
            return false;
        }
        if (e.requiere && !datos[e.requiere]) {
            return false;
        }
        return true;
    });
}

/**
 * Quien llama cada endpoint en la vida real: 'publico' (la app al validar el
 * QR), 'contador' (la app de conteo) o 'admin' (traspasos y requisiciones).
 * 'rol' en cambio dice que token lleva la peticion.
 */
export function perfilDe(e) {
    if (e.perfil) {
        return e.perfil;
    }
    return e.rol === 'admin' ? 'admin' : 'contador';
}

/** Ejecuta un endpoint del catalogo, valida y registra su resultado. */
export function ejecutar(e, ctx) {
    const token = e.rol === 'admin' ? ctx.tokenAdmin
        : e.rol === 'refresh' ? ctx.refreshToken
            : ctx.tokenContador;
    const params = e.rol === 'publico' && !token ? sinToken(e.nombre) : conToken(token, e.nombre);

    const url = BASE_URL + e.url(ctx.datos);
    const res = e.metodo === 'POST'
        ? http.post(url, e.body ? e.body() : null, params)
        : http.get(url, params);

    const tipo = registrar(res);
    const b = e.sobre ? cuerpo(res) : null;

    const valido = check(res, {
        [`${e.nombre}: HTTP esperado`]: (r) => e.esperado.includes(r.status),
        [`${e.nombre}: code 0`]: () => !e.sobre || (b !== null && b.code === 0),
    });
    respuestasValidas.add(valido && (tipo === 'ok' || tipo === 'negocio'));

    return res;
}

/**
 * Elige un endpoint al azar respetando los pesos. Asi la mezcla de carga
 * reproduce la proporcion de uso real en vez de martillar todo por igual.
 */
export function elegirPonderado(lista) {
    const total = lista.reduce((s, e) => s + e.peso, 0);
    let r = Math.random() * total;
    for (const e of lista) {
        r -= e.peso;
        if (r <= 0) {
            return e;
        }
    }
    return lista[lista.length - 1];
}

/** Contexto comun que setup() entrega a los VUs. */
export function prepararContexto(adminExigido) {
    const contador = loginContador();
    const tokenAdmin = loginAdmin(adminExigido);
    const adminDisponible = moduloAdminAccesible(tokenAdmin);
    return {
        tokenContador: contador.token,
        refreshToken: contador.refreshToken,
        tokenAdmin,
        adminDisponible,
        datos: adminDisponible ? descubrirDatos(tokenAdmin) : {},
    };
}

/**
 * Traspasos y requisiciones exigen el rol ADMIN (SecurityConfig). Sin
 * credenciales solo se llega a ellos con security.developer-mode=true. Se
 * prueba una vez: si responde 401 o 403, esos endpoints se omiten con aviso
 * en vez de inflar el reporte con rechazos de autorizacion.
 */
export function moduloAdminAccesible(tokenAdmin) {
    const res = http.get(`${BASE_URL}/api/v1/requisiciones/tipos`, conToken(tokenAdmin));
    if (res.status === 401 || res.status === 403) {
        console.warn(`Traspasos y requisiciones responden HTTP ${res.status} sin ADMIN_USER/ADMIN_PASS: se omiten. `
            + 'Dar credenciales o levantar el backend con security.developer-mode=true');
        return false;
    }
    return true;
}

/** Pausa de "usuario pensando", entre min y max segundos. */
export function pensar(min, max) {
    return min + Math.random() * (max - min);
}
