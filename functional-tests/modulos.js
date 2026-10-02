// ============================================================
//  Prueba funcional de los tres modulos contra un backend vivo.
//
//  Recorre, con datos REALES del ambiente, todas las APIs de lectura de
//  conteo fisico, traspasos y requisiciones, y opcionalmente un ciclo de
//  escritura acotado por modulo. No mide rendimiento: un VU, una iteracion,
//  y termina con codigo distinto de cero si algun check falla.
//
//  Para que sirve: confirmar que el backend sigue funcionando de punta a punta
//  despues de un cambio transversal (usuario de base de datos, sinonimos,
//  renombre de tablas, version de Spring...). Los tests unitarios no llegan a
//  Oracle; esto si.
//
//  Ejecutar (lectura sola):
//    k6 run -e BASE_URL=http://localhost:8082 \
//           -e ADMIN_USER=dflorez -e ADMIN_PASS=... \
//           functional-tests/modulos.js
//
//  Con escrituras:
//    -e ESCRIBIR=1                 traspasos: crea un tramite y lo RECHAZA.
//                                  Queda un tramite en 'na' sin movimiento de
//                                  inventario.
//    -e REQUISICION=2602/202500603 requisiciones: APRUEBA una unidad de la
//                                  primera linea de esa requisicion, que debe
//                                  estar en 'in'. Pasa a 'ap' y no hay vuelta
//                                  atras por API: elegirla a conciencia.
//
//  Conteo fisico solo se prueba en lectura: asignar articulos exige una bodega
//  bloqueada con snapshot en CONTARBO y eso no se improvisa en una prueba.
//
//  Credenciales: ADMIN_USER/ADMIN_PASS es un usuario de USUABADA (ver
//  docs/pruebas-requisiciones.md para sacar la clave); el contador es un
//  usuario de FI_COFIUSCO con su codigo temporal vigente.
// ============================================================
import http from 'k6/http';
import { check, group, fail } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';
const EMPRESA = __ENV.EMPRESA || '01';

const ADMIN_USER = __ENV.ADMIN_USER;
const ADMIN_PASS = __ENV.ADMIN_PASS;
const CONTADOR_DOC = __ENV.CONTADOR_DOC || '666';
const CONTADOR_COD = __ENV.CONTADOR_COD || '846166';

// Traspasos: una persona con activos libres en una bodega personal (PE).
const BODEGA_PE = __ENV.BODEGA_PE || '1111';
const PERSONA_FUENTE = __ENV.PERSONA_FUENTE || 'PV11011';

// Requisiciones: bodega de la bandeja y, si se quiere escribir, la terna.
const REQ_BODEGA = __ENV.REQ_BODEGA || 'F11';
const REQUISICION = __ENV.REQUISICION || '';

const ESCRIBIR = __ENV.ESCRIBIR === '1';

export const options = {
    vus: 1,
    iterations: 1,
    thresholds: {
        // Un solo check caido tumba la corrida: no hay "casi pasa".
        checks: ['rate==1'],
    },
};

const JSON_HEADERS = { 'Content-Type': 'application/json' };

function conToken(token) {
    return { headers: Object.assign({ Authorization: `Bearer ${token}` }, JSON_HEADERS) };
}

function cuerpo(res) {
    try {
        return res.json();
    } catch (e) {
        return null;
    }
}

// Las respuestas envueltas traen code 0 cuando el proceso fue exitoso.
function checkSobre(res, nombre) {
    const b = cuerpo(res);
    return check(res, {
        [`${nombre}: HTTP 200`]: (r) => r.status === 200,
        [`${nombre}: code 0`]: () => b !== null && b.code === 0,
    });
}

function checkLista(res, nombre) {
    return check(res, {
        [`${nombre}: HTTP 200 o 204`]: (r) => r.status === 200 || r.status === 204,
    });
}

// ------------------------------------------------------------
//  Autenticacion
// ------------------------------------------------------------
function loginAdmin() {
    if (!ADMIN_USER || !ADMIN_PASS) {
        // Sin credenciales solo se puede leer, y solo con security.developer-mode=true:
        // las lecturas no abren el token, las escrituras si (sacan el usuario de el).
        if (ESCRIBIR || REQUISICION) {
            fail('Faltan ADMIN_USER / ADMIN_PASS: las escrituras exigen un token de administrador real');
        }
        console.warn('Sin ADMIN_USER / ADMIN_PASS: traspasos y requisiciones solo en lectura, exige developer-mode');
        return 'sin-credenciales';
    }
    const res = http.post(`${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ username: ADMIN_USER, password: ADMIN_PASS }), { headers: JSON_HEADERS });
    const b = cuerpo(res);
    check(res, {
        'login admin: HTTP 200': (r) => r.status === 200,
        'login admin: trae token': () => b !== null && typeof b.token === 'string',
    });
    if (!b || !b.token) {
        fail(`Login admin fallido (HTTP ${res.status}): ${res.body}`);
    }
    return b.token;
}

function loginContador() {
    const res = http.post(`${BASE_URL}/api/v1/auth/login/contador`,
        JSON.stringify({ documento: CONTADOR_DOC, codigoTemporal: CONTADOR_COD }), { headers: JSON_HEADERS });
    const b = cuerpo(res);
    check(res, {
        'login contador: HTTP 200': (r) => r.status === 200,
        'login contador: trae token': () => b !== null && typeof b.token === 'string',
    });
    if (!b || !b.token) {
        fail(`Login contador fallido (HTTP ${res.status}): ${res.body}`);
    }
    return b.token;
}

// ------------------------------------------------------------
//  Conteo fisico (lectura)
// ------------------------------------------------------------
function conteoFisico(tokenContador) {
    group('conteo fisico', () => {
        const h = conToken(tokenContador);

        check(http.get(`${BASE_URL}/api/v1/health`), { 'health: HTTP 200': (r) => r.status === 200 });

        const info = http.get(`${BASE_URL}/api/v1/health/info`);
        // Sin URLSIGAPP cargado responde code -1 con el motivo: tambien es valido.
        check(info, {
            'health/info: HTTP 200': (r) => r.status === 200,
            'health/info: responde con code': () => { const b = cuerpo(info); return b !== null && (b.code === 0 || b.code === -1); },
        });

        const empresas = http.get(`${BASE_URL}/api/v1/empresas/getAll`, h);
        check(empresas, {
            'empresas: HTTP 200': (r) => r.status === 200,
            'empresas: lista no vacia': () => { const b = cuerpo(empresas); return Array.isArray(b) && b.length > 0; },
        });

        const bodegas = http.get(`${BASE_URL}/api/v1/bodegas/empresa/${EMPRESA}/PE`, h);
        check(bodegas, {
            'bodegas PE: HTTP 200': (r) => r.status === 200,
            'bodegas PE: lista no vacia': () => { const b = cuerpo(bodegas); return Array.isArray(b) && b.length > 0; },
        });

        checkLista(http.get(`${BASE_URL}/api/v1/bodegas/conteo-pendientes/${EMPRESA}`, h), 'bodegas con conteo pendiente');
        checkLista(http.get(`${BASE_URL}/api/v1/articulos/asignados/${BODEGA_PE}/${EMPRESA}`, h), 'articulos asignados');

        // Lee FI_COFIARAS por el usuario del token: cubre el sinonimo nuevo.
        const pendientes = http.get(`${BASE_URL}/api/v1/conteo-fisico/pendientes`, h);
        check(pendientes, { 'conteo pendientes del contador: HTTP 200 o 204': (r) => r.status === 200 || r.status === 204 });

        checkLista(http.get(`${BASE_URL}/api/v1/personal/buscar?cedula=${CONTADOR_DOC}`, h), 'personal buscar');
    });
}

// ------------------------------------------------------------
//  Traspasos
// ------------------------------------------------------------
function traspasos(token) {
    group('traspasos', () => {
        const h = conToken(token);

        checkSobre(http.get(`${BASE_URL}/api/v1/traspasos/list?estado=pe`, h), 'traspasos bandeja pe');
        checkSobre(http.get(`${BASE_URL}/api/v1/traspasos/list?estado=ap`, h), 'traspasos bandeja ap');

        const personas = http.get(`${BASE_URL}/api/v1/traspasos/personas?bodega=${BODEGA_PE}&empresa=${EMPRESA}`, h);
        checkSobre(personas, 'traspasos personas');

        const activos = http.get(`${BASE_URL}/api/v1/traspasos/activos?persona=${PERSONA_FUENTE}&bodega=${BODEGA_PE}&empresa=${EMPRESA}`, h);
        checkSobre(activos, 'traspasos activos');

        const ultimo = primerTramite(h);
        if (ultimo !== null) {
            checkSobre(http.get(`${BASE_URL}/api/v1/traspasos/get/${ultimo}`, h), `traspasos detalle ${ultimo}`);
        }

        if (!ESCRIBIR) {
            return;
        }

        // Ciclo acotado: crear -> consultar -> rechazar. El rechazo cierra el
        // tramite sin generar documento de inventario.
        const libres = (cuerpo(activos).list || []).filter((a) => !a.enTramite).slice(0, 2);
        const destino = (cuerpo(personas).list || []).find((p) => p.cedula !== PERSONA_FUENTE);
        if (libres.length === 0 || !destino) {
            fail(`No hay datos para crear un traspaso: activos libres=${libres.length}, destino=${destino ? destino.cedula : 'ninguno'}`);
        }

        const crear = http.post(`${BASE_URL}/api/v1/traspasos/crear`, JSON.stringify({
            empresa: EMPRESA,
            personaFuente: PERSONA_FUENTE,
            personaDestino: destino.cedula,
            observacion: 'Prueba funcional automatica: se rechaza en el siguiente paso',
            articulos: libres.map((a) => ({ articulo: a.articulo, placa: a.placa })),
        }), h);
        checkSobre(crear, 'traspasos crear');
        const id = cuerpo(crear) && cuerpo(crear).object ? cuerpo(crear).object.id : null;
        if (!id) {
            fail(`Crear traspaso no devolvio id: ${crear.body}`);
        }

        const detalle = http.get(`${BASE_URL}/api/v1/traspasos/get/${id}`, h);
        checkSobre(detalle, `traspasos detalle creado ${id}`);
        check(detalle, {
            [`traspaso ${id}: estado pe`]: () => cuerpo(detalle).object.estado === 'pe',
            [`traspaso ${id}: ${libres.length} articulos`]: () => cuerpo(detalle).object.articulos.length === libres.length,
            [`traspaso ${id}: dos firmas vacias`]: () => cuerpo(detalle).object.firmas.filter((f) => !f.firmada).length === 2,
        });

        const rechazo = http.post(`${BASE_URL}/api/v1/traspasos/process/${id}`, JSON.stringify({
            estado: 'na',
            observacion: 'Prueba funcional automatica',
        }), h);
        checkSobre(rechazo, `traspasos rechazar ${id}`);

        const final = http.get(`${BASE_URL}/api/v1/traspasos/get/${id}`, h);
        check(final, { [`traspaso ${id}: queda en na`]: () => cuerpo(final).object.estado === 'na' });
    });
}

function primerTramite(h) {
    for (const estado of ['ap', 're', 'na']) {
        const res = http.get(`${BASE_URL}/api/v1/traspasos/list?estado=${estado}`, h);
        const b = cuerpo(res);
        if (b && b.list && b.list.length > 0) {
            return b.list[0].id;
        }
    }
    return null;
}

// ------------------------------------------------------------
//  Requisiciones
// ------------------------------------------------------------
function requisiciones(token) {
    group('requisiciones', () => {
        const h = conToken(token);

        const tipos = http.get(`${BASE_URL}/api/v1/requisiciones/tipos`, h);
        checkSobre(tipos, 'requisiciones tipos');
        check(tipos, { 'requisiciones tipos: hay equivalencia cargada': () => (cuerpo(tipos).list || []).length > 0 });

        const bandejaIn = http.get(`${BASE_URL}/api/v1/requisiciones?estado=in&empresa=${EMPRESA}&bodega=${REQ_BODEGA}&desde=2025-01-01`, h);
        checkSobre(bandejaIn, 'requisiciones bandeja in');
        checkSobre(http.get(`${BASE_URL}/api/v1/requisiciones?estado=rg&empresa=${EMPRESA}&desde=2026-01-01`, h), 'requisiciones bandeja rg');

        const alguna = (cuerpo(bandejaIn).list || [])[0];
        if (alguna) {
            const det = http.get(`${BASE_URL}/api/v1/requisiciones/${alguna.empresa}/${alguna.tipoDocumento}/${alguna.numero}`, h);
            checkSobre(det, 'requisiciones detalle');
            check(det, { 'requisiciones detalle: trae lineas y firmas': () => {
                const o = cuerpo(det).object;
                return Array.isArray(o.lineas) && o.lineas.length > 0 && Array.isArray(o.firmas);
            } });
        }

        if (!REQUISICION) {
            return;
        }

        // Aprobar una unidad de la primera linea. Pasa la requisicion a 'ap'.
        const [tipo, numero] = REQUISICION.split('/');
        const antes = http.get(`${BASE_URL}/api/v1/requisiciones/${EMPRESA}/${tipo}/${numero}`, h);
        checkSobre(antes, `requisicion ${REQUISICION} antes`);
        const obj = cuerpo(antes).object;
        if (!obj || obj.estado !== 'in') {
            fail(`La requisicion ${REQUISICION} no esta en 'in' (estado=${obj ? obj.estado : '?'}): no se aprueba`);
        }
        const linea = obj.lineas[0];

        const aprobar = http.put(`${BASE_URL}/api/v1/requisiciones/${EMPRESA}/${tipo}/${numero}/aprobar`, JSON.stringify({
            lineas: [{ articulo: linea.articulo, bodega: linea.bodega, secuencia: linea.secuencia, cantidad: 1 }],
        }), h);
        checkSobre(aprobar, `requisicion ${REQUISICION} aprobar`);

        const despues = http.get(`${BASE_URL}/api/v1/requisiciones/${EMPRESA}/${tipo}/${numero}`, h);
        check(despues, {
            [`requisicion ${REQUISICION}: queda en ap`]: () => cuerpo(despues).object.estado === 'ap',
            [`requisicion ${REQUISICION}: linea aprobada = 1`]: () => cuerpo(despues).object.lineas[0].aprobada === 1,
        });
    });
}

// ------------------------------------------------------------
export default function () {
    const tokenContador = loginContador();
    conteoFisico(tokenContador);

    const tokenAdmin = loginAdmin();
    traspasos(tokenAdmin);
    requisiciones(tokenAdmin);
}
