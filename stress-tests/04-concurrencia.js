// ============================================================
//  PASO 4 - Concurrencia: carreras sobre el MISMO recurso.
//
//  La carga mide si el backend aguanta muchos usuarios. Esto mide otra cosa:
//  si sigue siendo CORRECTO cuando varias peticiones tocan lo mismo en el mismo
//  instante. Un doble toque en la app, un reintento por mala señal o dos
//  almacenistas abriendo la misma requisicion son casos reales.
//
//  Cada carrera dispara N peticiones identicas a la vez (http.batch, todas en
//  vuelo simultaneamente) y verifica el invariante:
//
//    - exactamente UNA gana y las demas reciben un rechazo de negocio,
//    - ninguna termina en error tecnico (deadlock no resuelto, pool, 5xx crudo),
//    - el estado final, leido despues, es el que dejo la ganadora.
//
//  Solo las carreras de lectura y autenticacion corren por defecto. Las de
//  escritura se activan una por una, porque tocan datos reales:
//
//    -e ESCRIBIR=1             Traspasos. Todo termina en 'na': sin movimiento
//                              de inventario, pero deja documentos REQUSUMI.
//    -e TRASPASO_CICLO=1       Traspasos, ciclo completo con firmas y recepcion.
//                              MUEVE UN ACTIVO de PERSONA_FUENTE a
//                              PERSONA_DESTINO y lo DEVUELVE con un segundo
//                              traspaso: quedan dos DOCUINVE que se compensan.
//                              Exige PERSONA_DESTINO y BODEGA_DESTINO.
//    -e REQ_CICLO=tipo/numero  Requisiciones. Aprueba, entrega, firma y REGISTRA
//                              una unidad de la primera linea. Genera la salida
//                              de inventario en el ERP: IRREVERSIBLE. Si la
//                              requisicion tiene mas lineas, las aprueba con 1 y
//                              las ANULA para que no frenen el documento. Usar
//                              una requisicion de prueba de una sola linea.
//    -e CONTEO_REPORTAR=1      Conteo fisico. Reporta varias veces a la vez el
//                              mismo articulo pendiente del contador en
//                              BODEGA_CONTEO, conteo NUMERO_CONTEO. Escribe en
//                              CONTARBO, tabla del ERP.
//    -e CONTEO_CERRAR=1        Conteo fisico. Cierra BODEGA_CONTEO varias veces a
//                              la vez. Cierra el conteo de verdad.
//    -e CONTEO_GENERAR='{json}' Conteo fisico. Cuerpo de /conteo-fisico/registrar
//                              (genera el snapshot en CONTARBO). Varias a la vez.
//    -e CONTEO_ASIGNAR='{json}' Conteo fisico. Cuerpo de /asignar_articulos.
//                              Varias a la vez. Crea usuarios temporales.
//
//  Ejecutar (desde la carpeta stress-tests):
//    k6 run -e ADMIN_USER=... -e ADMIN_PASS=... 04-concurrencia.js
//    k6 run -e ADMIN_USER=... -e ADMIN_PASS=... -e ESCRIBIR=1 -e N=8 04-concurrencia.js
// ============================================================
import http from 'k6/http';
import { check, group, fail, sleep } from 'k6';
import {
    BASE_URL, EMPRESA, BODEGA_PE, PERSONA_FUENTE, BODEGA_CONTEO, CONTADOR_DOC, FIRMA_PNG,
    cuerpo, conToken, clasificar, registrar, mensaje,
    prepararContexto,
} from './lib/sigapp.js';

// Peticiones simultaneas por carrera. El pool de Hikari tiene 5 conexiones:
// con N mayor, parte de la carrera se resuelve haciendo cola en el pool y no
// en Oracle, que tambien es un caso que hay que cubrir.
const N = parseInt(__ENV.N || '6', 10);
// Para la carrera de saturacion del pool: bastante mas que sus 5 conexiones.
const N_POOL = parseInt(__ENV.N_POOL || '25', 10);

const ESCRIBIR = __ENV.ESCRIBIR === '1';
const TRASPASO_CICLO = __ENV.TRASPASO_CICLO === '1';
const PERSONA_DESTINO = __ENV.PERSONA_DESTINO || '';
const BODEGA_DESTINO = __ENV.BODEGA_DESTINO || '';
const REQ_CICLO = __ENV.REQ_CICLO || '';
const CONTEO_REPORTAR = __ENV.CONTEO_REPORTAR === '1';
const NUMERO_CONTEO = parseInt(__ENV.NUMERO_CONTEO || '1', 10);
const CONTEO_CERRAR = __ENV.CONTEO_CERRAR === '1';
const CONTEO_GENERAR = __ENV.CONTEO_GENERAR || '';
const CONTEO_ASIGNAR = __ENV.CONTEO_ASIGNAR || '';

const MAX_BATCH = Math.max(N * 2, N_POOL);

export const options = {
    scenarios: {
        carreras: { executor: 'per-vu-iterations', vus: 1, iterations: 1, maxDuration: '30m' },
    },
    // Sin esto k6 manda como maximo 6 peticiones a la vez al mismo host y la
    // "carrera" seria una cola.
    batch: MAX_BATCH,
    batchPerHost: MAX_BATCH,
    thresholds: {
        // Un solo invariante roto tumba la corrida.
        checks: ['rate==1'],
    },
};

// ------------------------------------------------------------
//  Motor de carreras
// ------------------------------------------------------------

function peticion(metodo, ruta, body, token, endpoint) {
    return {
        method: metodo,
        url: BASE_URL + ruta,
        body: body === null || body === undefined ? null : JSON.stringify(body),
        params: conToken(token, endpoint),
    };
}

/**
 * Dispara todas las peticiones a la vez y devuelve cuantas ganaron.
 *
 * @param ganadoras cuantas deben ganar. Normalmente 1; 0 si todas deben
 *                  perder; null si todas deben ganar (lecturas, logins).
 * @param gano      funcion que dice si una respuesta 'ok' es realmente una
 *                  victoria. Por defecto cualquier 'ok'.
 */
function carrera(nombre, peticiones, ganadoras, gano) {
    const esGanadora = gano || ((res) => clasificar(res) === 'ok');
    const respuestas = http.batch(peticiones);

    let ganan = 0;
    let negocio = 0;
    let tecnicos = 0;
    const lineas = [];

    respuestas.forEach((res, i) => {
        const tipo = registrar(res);
        const g = tipo === 'ok' && esGanadora(res);
        if (g) {
            ganan++;
        } else if (tipo === 'ok' || tipo === 'negocio') {
            negocio++;
        } else {
            tecnicos++;
        }
        lineas.push(`    #${String(i + 1).padStart(2)} ${String(Math.round(res.timings.duration)).padStart(6)} ms  ${(g ? 'GANA' : tipo).padEnd(8)} ${mensaje(res)}`);
    });

    const esperadas = ganadoras === null ? peticiones.length : ganadoras;
    console.log(`\n  [${nombre}] ${peticiones.length} simultaneas: ${ganan} ganan, ${negocio} rechazo de negocio, ${tecnicos} error tecnico\n${lineas.join('\n')}`);

    check(null, {
        [`${nombre}: ganan exactamente ${esperadas}`]: () => ganan === esperadas,
        [`${nombre}: sin errores tecnicos`]: () => tecnicos === 0,
    });

    return { respuestas, ganan };
}

function repetir(n, fabrica) {
    const lista = [];
    for (let i = 0; i < n; i++) {
        lista.push(fabrica(i));
    }
    return lista;
}

function objeto(res) {
    const b = cuerpo(res);
    return b && b.code === 0 ? b.object : null;
}

// ------------------------------------------------------------
//  Setup
// ------------------------------------------------------------
export function setup() {
    const escribe = ESCRIBIR || TRASPASO_CICLO || REQ_CICLO !== '';
    return prepararContexto(escribe);
}

export default function (ctx) {
    group('lectura y autenticacion', () => carrerasDeLectura(ctx));

    if (ESCRIBIR) {
        group('traspasos: alta y rechazo', () => carrerasTraspasoBasicas(ctx));
    }
    if (TRASPASO_CICLO) {
        group('traspasos: ciclo completo ida y vuelta', () => carrerasTraspasoCiclo(ctx));
    }
    if (REQ_CICLO) {
        group('requisiciones: ciclo completo', () => carrerasRequisicion(ctx));
    }
    if (CONTEO_REPORTAR || CONTEO_CERRAR || CONTEO_GENERAR || CONTEO_ASIGNAR) {
        group('conteo fisico', () => carrerasConteo(ctx));
    }
}

// ------------------------------------------------------------
//  Lectura y autenticacion (siempre)
// ------------------------------------------------------------
function carrerasDeLectura(ctx) {
    // Login simultaneo del mismo contador: todos deben recibir un token valido.
    const logins = carrera('login contador simultaneo',
        repetir(N, () => ({
            method: 'POST',
            url: `${BASE_URL}/api/v1/auth/login/contador`,
            body: JSON.stringify({ documento: CONTADOR_DOC, codigoTemporal: __ENV.CONTADOR_COD || '846166' }),
            params: { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'POST /auth/login/contador' } },
        })), null);

    // Cada token emitido en paralelo debe servir: un token cruzado o corrupto
    // daria 401 aqui.
    const tokens = logins.respuestas.map((r) => (cuerpo(r) || {}).token).filter(Boolean);
    carrera('tokens emitidos en paralelo son validos',
        tokens.map((t) => peticion('GET', '/api/v1/conteo-fisico/pendientes', null, t, 'GET /conteo-fisico/pendientes')),
        null, (res) => res.status === 200 || res.status === 204);

    carrera('refresh simultaneo',
        repetir(N, () => peticion('POST', '/api/v1/auth/refresh', null, ctx.refreshToken, 'POST /auth/refresh')), null);

    // El mismo detalle leido N veces a la vez debe dar N respuestas identicas.
    if (ctx.datos.tramite) {
        identicas('detalle de traspaso concurrente',
            repetir(N, () => peticion('GET', `/api/v1/traspasos/get/${ctx.datos.tramite}`, null, ctx.tokenAdmin, 'GET /traspasos/get/{id}')));
    }
    if (ctx.datos.requisicion) {
        identicas('detalle de requisicion concurrente',
            repetir(N, () => peticion('GET', `/api/v1/requisiciones/${ctx.datos.requisicion}`, null, ctx.tokenAdmin, 'GET /requisiciones/{e}/{t}/{n}')));
    }

    // Saturacion del pool: muchas mas consultas pesadas que conexiones. Todas
    // deben terminar bien, haciendo cola; ninguna debe morir por timeout de
    // Hikari (30 s).
    // Sin acceso al modulo administrativo se usan las consultas mas lentas del
    // contador, que el baseline mide por encima de medio segundo.
    const pesadas = repetir(N_POOL, (i) => {
        if (ctx.adminDisponible) {
            return i % 2 === 0
                ? peticion('GET', `/api/v1/requisiciones?estado=rg&empresa=${EMPRESA}`, null, ctx.tokenAdmin, 'GET /requisiciones?rg')
                : peticion('GET', `/api/v1/traspasos/list?estado=pr&empresa=${EMPRESA}`, null, ctx.tokenAdmin, 'GET /traspasos/list?pr');
        }
        return i % 2 === 0
            ? peticion('GET', `/api/v1/bodegas/empresa/${EMPRESA}/PE`, null, ctx.tokenContador, 'GET /bodegas/empresa/{e}/{tipo}')
            : peticion('GET', `/api/v1/articulos/asignados/${BODEGA_CONTEO}/${EMPRESA}`, null, ctx.tokenContador, 'GET /articulos/asignados/{b}/{e}');
    });
    const sat = carrera(`pool saturado (${N_POOL} consultas, 5 conexiones)`, pesadas, null);
    const maximo = Math.max(...sat.respuestas.map((r) => r.timings.duration));
    console.log(`    la mas lenta tardo ${Math.round(maximo)} ms`);
}

function identicas(nombre, peticiones) {
    const respuestas = http.batch(peticiones);
    respuestas.forEach(registrar);
    const cuerpos = respuestas.map((r) => r.body);
    const ok = respuestas.every((r) => r.status === 200);
    check(null, {
        [`${nombre}: todas HTTP 200`]: () => ok,
        [`${nombre}: respuestas identicas`]: () => cuerpos.every((c) => c === cuerpos[0]),
    });
    console.log(`\n  [${nombre}] ${peticiones.length} simultaneas: ${ok ? 'todas 200' : 'con fallos'}, ${new Set(cuerpos).size} cuerpo(s) distinto(s)`);
}

// ------------------------------------------------------------
//  Traspasos: alta y rechazo (ESCRIBIR=1)
// ------------------------------------------------------------
function activosLibres(ctx, persona, bodega) {
    const b = cuerpo(http.get(
        `${BASE_URL}/api/v1/traspasos/activos?persona=${persona}&bodega=${bodega}&empresa=${EMPRESA}`,
        conToken(ctx.tokenAdmin)));
    return ((b && b.list) || []).filter((a) => !a.enTramite);
}

function destinoDe(ctx, fuente) {
    if (PERSONA_DESTINO && fuente === PERSONA_FUENTE) {
        return PERSONA_DESTINO;
    }
    const b = cuerpo(http.get(`${BASE_URL}/api/v1/traspasos/personas?bodega=${BODEGA_PE}&empresa=${EMPRESA}`, conToken(ctx.tokenAdmin)));
    const p = ((b && b.list) || []).find((x) => x.cedula !== fuente);
    return p ? p.cedula : null;
}

function cuerpoTraspaso(fuente, destino, activos, nota) {
    return {
        empresa: EMPRESA,
        personaFuente: fuente,
        personaDestino: destino,
        observacion: `Prueba de concurrencia k6: ${nota}`,
        articulos: activos.map((a) => ({ articulo: a.articulo, placa: a.placa })),
    };
}

function crearTraspaso(ctx, fuente, destino, activos, nota) {
    const res = http.post(`${BASE_URL}/api/v1/traspasos/crear`,
        JSON.stringify(cuerpoTraspaso(fuente, destino, activos, nota)), conToken(ctx.tokenAdmin, 'POST /traspasos/crear'));
    const o = objeto(res);
    if (!o) {
        fail(`No se pudo crear el traspaso de preparacion: ${mensaje(res)}`);
    }
    return o.id;
}

function rechazar(ctx, id) {
    const res = http.post(`${BASE_URL}/api/v1/traspasos/process/${id}`,
        JSON.stringify({ estado: 'na', observacion: 'Prueba de concurrencia k6' }), conToken(ctx.tokenAdmin));
    if (!objetoOk(res)) {
        console.error(`El traspaso ${id} no se pudo rechazar y queda abierto: ${mensaje(res)}`);
    }
}

function objetoOk(res) {
    const b = cuerpo(res);
    return b !== null && b.code === 0;
}

function detalleTraspaso(ctx, id) {
    return objeto(http.get(`${BASE_URL}/api/v1/traspasos/get/${id}`, conToken(ctx.tokenAdmin)));
}

function idCreado(res) {
    const o = objeto(res);
    return o ? o.id : null;
}

function carrerasTraspasoBasicas(ctx) {
    const libres = activosLibres(ctx, PERSONA_FUENTE, BODEGA_PE);
    const destino = destinoDe(ctx, PERSONA_FUENTE);
    if (libres.length < 2 || !destino) {
        fail(`ESCRIBIR=1 necesita 2 activos libres de ${PERSONA_FUENTE} en ${BODEGA_PE} y un destino: hay ${libres.length} y destino=${destino}`);
    }
    const [a1, a2] = libres;

    // 1. El mismo activo en N traspasos a la vez. Lo cubren tres capas: el
    //    chequeo en Java, pro_valida_activo_libre y el indice
    //    FI_MOVITRAS_UK_ABIERTO. Con N simultaneas las dos primeras pueden
    //    pasar todas; el indice es el que debe cortar.
    const mismo = carrera('crear traspaso con el mismo activo',
        repetir(N, (i) => peticion('POST', '/api/v1/traspasos/crear',
            cuerpoTraspaso(PERSONA_FUENTE, destino, [a1], `mismo activo ${i + 1}`), ctx.tokenAdmin, 'POST /traspasos/crear')),
        1);
    mismo.respuestas.map(idCreado).filter(Boolean).forEach((id) => rechazar(ctx, id));

    // 2. Dos traspasos con los mismos dos activos en orden inverso: A pide
    //    (a1, a2) y B pide (a2, a1). Es el patron clasico de interbloqueo. Oracle
    //    lo resuelve matando una sesion (ORA-00060); lo que importa es que gane
    //    exactamente uno y ningun activo quede en dos tramites.
    const cruzados = carrera('crear traspasos con activos cruzados',
        repetir(N, (i) => peticion('POST', '/api/v1/traspasos/crear',
            cuerpoTraspaso(PERSONA_FUENTE, destino, i % 2 === 0 ? [a1, a2] : [a2, a1], `cruzado ${i + 1}`),
            ctx.tokenAdmin, 'POST /traspasos/crear')),
        1);
    cruzados.respuestas.map(idCreado).filter(Boolean).forEach((id) => rechazar(ctx, id));

    // Despues de rechazar a las ganadoras, ningun activo puede seguir comprometido.
    const despues = activosLibres(ctx, PERSONA_FUENTE, BODEGA_PE).map((a) => `${a.articulo}|${a.placa}`);
    check(null, {
        'activos liberados tras rechazar': () => [a1, a2].every((a) => despues.includes(`${a.articulo}|${a.placa}`)),
    });

    // 3. N rechazos a la vez del mismo tramite: pro_bloquea_tramite serializa
    //    y solo el primero lo encuentra en 'pe'.
    const id = crearTraspaso(ctx, PERSONA_FUENTE, destino, [a1], 'rechazo simultaneo');
    carrera('rechazar el mismo traspaso a la vez',
        repetir(N, (i) => peticion('POST', `/api/v1/traspasos/process/${id}`,
            { estado: 'na', observacion: `rechazo ${i + 1}` }, ctx.tokenAdmin, 'POST /traspasos/process/{id}')),
        1);
    const final = detalleTraspaso(ctx, id);
    check(final, { 'rechazo simultaneo: queda en na': (d) => d !== null && d.estado === 'na' });
}

// ------------------------------------------------------------
//  Traspasos: ciclo completo (TRASPASO_CICLO=1)
// ------------------------------------------------------------
function carrerasTraspasoCiclo(ctx) {
    if (!PERSONA_DESTINO || !BODEGA_DESTINO) {
        fail('TRASPASO_CICLO=1 exige PERSONA_DESTINO y BODEGA_DESTINO (bodega personal del destino) para devolver el activo');
    }
    const libres = activosLibres(ctx, PERSONA_FUENTE, BODEGA_PE);
    if (libres.length === 0) {
        fail(`No hay activos libres de ${PERSONA_FUENTE} en ${BODEGA_PE}`);
    }
    const activo = libres[0];
    console.log(`\n  Ciclo con el activo ${activo.articulo} placa ${activo.placa}`);

    cicloTraspaso(ctx, 'ida', PERSONA_FUENTE, PERSONA_DESTINO, activo, BODEGA_DESTINO);
    cicloTraspaso(ctx, 'vuelta', PERSONA_DESTINO, PERSONA_FUENTE, activo, BODEGA_PE);
}

function cicloTraspaso(ctx, etiqueta, fuente, destino, activo, bodegaLlegada) {
    // Aprobar y rechazar a la vez: la mitad pide 'ap' y la otra 'na'. Gana una
    // y el tramite debe quedar en el estado de la ganadora, en TODAS sus lineas.
    let id = crearTraspaso(ctx, fuente, destino, [activo], `ciclo ${etiqueta}`);
    const mixta = carrera(`${etiqueta}: aprobar y rechazar a la vez`,
        repetir(N, (i) => peticion('POST', `/api/v1/traspasos/process/${id}`,
            { estado: i % 2 === 0 ? 'ap' : 'na', observacion: `proceso ${i + 1}` }, ctx.tokenAdmin, 'POST /traspasos/process/{id}')),
        1);
    const ganadora = mixta.respuestas.findIndex((r) => clasificar(r) === 'ok');
    if (ganadora < 0) {
        fail(`${etiqueta}: ninguna peticion pudo procesar el traspaso ${id}; queda abierto`);
    }
    const estadoGanador = ganadora % 2 === 0 ? 'ap' : 'na';
    const tras = detalleTraspaso(ctx, id);
    check(tras, { [`${etiqueta}: el estado final es el de la ganadora`]: (d) => d !== null && d.estado === estadoGanador });

    if (estadoGanador === 'na') {
        // Gano el rechazo: el activo quedo libre. Se abre otro tramite y se
        // aprueba sin carrera para seguir con las firmas.
        id = crearTraspaso(ctx, fuente, destino, [activo], `ciclo ${etiqueta} (2)`);
        const ap = http.post(`${BASE_URL}/api/v1/traspasos/process/${id}`,
            JSON.stringify({ estado: 'ap', observacion: 'ciclo k6' }), conToken(ctx.tokenAdmin));
        if (!objetoOk(ap)) {
            fail(`No se pudo aprobar el traspaso ${id}: ${mensaje(ap)}`);
        }
    }

    // Firmas: N de la fuente y N del destino, todas a la vez. Deben ganar dos,
    // una por posicion: las firmas no tienen orden, pero cada posicion se
    // firma una sola vez.
    const firmas = repetir(N * 2, (i) => peticion('PUT', `/api/v1/traspasos/sign/${id}`,
        { tipoFirma: i % 2 === 0 ? 'FU' : 'DE', firma: FIRMA_PNG }, ctx.tokenAdmin, 'PUT /traspasos/sign/{id}'));
    const f = carrera(`${etiqueta}: firmas FU y DE a la vez`, firmas, 2);
    const ganFU = f.respuestas.filter((r, i) => i % 2 === 0 && clasificar(r) === 'ok').length;
    check(null, { [`${etiqueta}: gana una firma por posicion`]: () => ganFU === 1 });

    const firmado = detalleTraspaso(ctx, id);
    check(firmado, {
        [`${etiqueta}: dos firmas registradas`]: (d) => d !== null && d.firmas.filter((x) => x.firmada).length === 2,
        [`${etiqueta}: firmar no mueve el estado`]: (d) => d !== null && d.estado === 'ap',
    });

    // Recibir N veces a la vez. Es el punto de no retorno: un segundo
    // documento de inventario seria irreversible.
    carrera(`${etiqueta}: recibir a la vez`,
        repetir(N, () => peticion('PUT', `/api/v1/traspasos/recibir/${id}`, null, ctx.tokenAdmin, 'PUT /traspasos/recibir/{id}')),
        1);
    const recibido = detalleTraspaso(ctx, id);
    check(recibido, { [`${etiqueta}: queda en re`]: (d) => d !== null && d.estado === 're' });

    // El activo debe aparecer UNA vez en la bodega de llegada.
    const llegada = cuerpo(http.get(
        `${BASE_URL}/api/v1/traspasos/activos?persona=${destino}&bodega=${bodegaLlegada}&empresa=${EMPRESA}`,
        conToken(ctx.tokenAdmin)));
    const veces = ((llegada && llegada.list) || [])
        .filter((a) => a.articulo === activo.articulo && a.placa === activo.placa).length;
    check(null, { [`${etiqueta}: el activo llego una sola vez a ${destino}`]: () => veces === 1 });
    sleep(1);
}

// ------------------------------------------------------------
//  Requisiciones: ciclo completo (REQ_CICLO=tipo/numero)
// ------------------------------------------------------------
function carrerasRequisicion(ctx) {
    const [tipo, numero] = REQ_CICLO.split('/');
    const base = `/api/v1/requisiciones/${EMPRESA}/${tipo}/${numero}`;
    const detalle = () => objeto(http.get(BASE_URL + base, conToken(ctx.tokenAdmin)));

    let req = detalle();
    if (!req) {
        fail(`No existe la requisicion ${EMPRESA}/${REQ_CICLO}`);
    }
    if (req.estado === 'rg' || req.estado === 'ae') {
        fail(`La requisicion ${REQ_CICLO} ya esta en '${req.estado}': elegir otra`);
    }
    const linea = req.lineas[0];
    const clave = { articulo: linea.articulo, bodega: linea.bodega, secuencia: linea.secuencia };
    const conCantidad = (c) => ({ lineas: [Object.assign({ cantidad: c }, clave)] });

    // Aprobar la misma linea N veces a la vez: solo la primera la encuentra en
    // 'in'. La aprobada debe ser 1, no N.
    if (linea.estado === 'in') {
        carrera('requisicion: aprobar la misma linea a la vez',
            repetir(N, () => peticion('PUT', `${base}/aprobar`, conCantidad(1), ctx.tokenAdmin, 'PUT /requisiciones/.../aprobar')),
            1);
        req = detalle();
        check(req, { 'requisicion: aprobada = 1': (d) => d !== null && Number(d.lineas[0].aprobada) === 1 });
    }

    // El documento avanza al ritmo de su linea mas atrasada: las demas se
    // aprueban con 1 y se anulan para que no lo frenen.
    apartarOtrasLineas(ctx, base, req);

    // Entregar N veces a la vez con cantidad 1 sobre 1 aprobada. La entrega es
    // acumulativa, asi que solo cabe una: la entregada debe ser 1.
    req = detalle();
    if (req.lineas[0].estado === 'ap') {
        carrera('requisicion: entregar la misma linea a la vez',
            repetir(N, () => peticion('PUT', `${base}/entregar`, conCantidad(1), ctx.tokenAdmin, 'PUT /requisiciones/.../entregar')),
            1);
        req = detalle();
        check(req, {
            'requisicion: entregada = 1': (d) => d !== null && Number(d.lineas[0].entregada) === 1,
            'requisicion: queda en en': (d) => d !== null && d.estado === 'en',
        });
    }

    // Firmas de salida y recibe, N de cada una a la vez: gana una por tipo.
    req = detalle();
    const pendientes = req.firmas.filter((x) => !x.firmada).map((x) => x.tipo);
    if (pendientes.length > 0) {
        const personaDe = (t) => (t === 'SA' ? req.responsableBodega : req.responsableBodegaDestino) || 'k6';
        const pet = repetir(N * pendientes.length, (i) => {
            const t = pendientes[i % pendientes.length];
            return peticion('PUT', `${base}/firmar`, { tipo: t, persona: personaDe(t), firma: FIRMA_PNG },
                ctx.tokenAdmin, 'PUT /requisiciones/.../firmar');
        });
        carrera('requisicion: firmas a la vez', pet, pendientes.length);
        req = detalle();
        check(req, {
            'requisicion: dos firmas': (d) => d !== null && d.firmas.filter((x) => x.firmada).length === 2,
            'requisicion: firmar no mueve el estado': (d) => d !== null && d.estado === 'en',
        });
    }

    // Registrar N veces a la vez. pro_generar_salida bloquea la requisicion y
    // es reentrante: las que llegan tarde la ven en 'rg' y responden "ya fue
    // registrada" SIN tipo de salida. Gana la unica que genero documento.
    carrera('requisicion: registrar a la vez',
        repetir(N, () => peticion('PUT', `${base}/registrar`, null, ctx.tokenAdmin, 'PUT /requisiciones/.../registrar')),
        1,
        (res) => { const o = objeto(res); return o !== null && o.tipoSalida !== null && o.tipoSalida !== undefined; });
    req = detalle();
    check(req, { 'requisicion: queda en rg': (d) => d !== null && d.estado === 'rg' });
}

function apartarOtrasLineas(ctx, base, req) {
    const otras = req.lineas.slice(1).filter((l) => l.estado === 'in' || l.estado === 'ap');
    if (otras.length === 0) {
        return;
    }
    console.warn(`La requisicion tiene ${otras.length} linea(s) mas: se aprueban con 1 y se anulan`);
    const h = conToken(ctx.tokenAdmin);
    for (const l of otras) {
        const clave = { articulo: l.articulo, bodega: l.bodega, secuencia: l.secuencia };
        if (l.estado === 'in') {
            const ap = http.put(`${BASE_URL}${base}/aprobar`, JSON.stringify({ lineas: [Object.assign({ cantidad: 1 }, clave)] }), h);
            if (!objetoOk(ap)) {
                fail(`No se pudo aprobar la linea ${l.articulo} para anularla: ${mensaje(ap)}`);
            }
        }
        const an = http.put(`${BASE_URL}${base}/anular`, JSON.stringify({ lineas: [clave] }), h);
        if (!objetoOk(an)) {
            fail(`No se pudo anular la linea ${l.articulo}: ${mensaje(an)}`);
        }
    }
}

// ------------------------------------------------------------
//  Conteo fisico (flags CONTEO_*)
// ------------------------------------------------------------
//  Estas carreras esperan exactamente una ganadora, como las demas. El conteo
//  no tiene bloqueos ni control de version: si fallan, la prueba esta
//  mostrando un defecto real, no un falso positivo.
// ------------------------------------------------------------
function carrerasConteo(ctx) {
    if (CONTEO_REPORTAR) {
        const pend = cuerpo(http.get(`${BASE_URL}/api/v1/conteo-fisico/pendientes`, conToken(ctx.tokenContador)));
        const art = (pend || []).find((p) => p.idBodega === BODEGA_CONTEO);
        if (!art) {
            fail(`El contador ${CONTADOR_DOC} no tiene articulos pendientes en ${BODEGA_CONTEO}`);
        }
        // Mismo articulo, cantidades distintas: si ganan dos, el valor final
        // depende de quien escribio ultimo.
        carrera(`conteo: reportar el mismo articulo a la vez (conteo ${NUMERO_CONTEO})`,
            repetir(N, (i) => peticion('POST', '/api/v1/conteo-fisico/reportar',
                { bodega: BODEGA_CONTEO, numeroConteo: NUMERO_CONTEO, articulos: [{ idArticulo: art.idArticulo, cantidad: i + 1 }] },
                ctx.tokenContador, 'POST /conteo-fisico/reportar')),
            1);
    }

    if (CONTEO_GENERAR) {
        const body = JSON.parse(CONTEO_GENERAR);
        carrera('conteo: generar el conteo de la misma bodega a la vez',
            repetir(N, () => peticion('POST', '/api/v1/conteo-fisico/registrar', body, ctx.tokenAdmin, 'POST /conteo-fisico/registrar')),
            1);
    }

    if (CONTEO_ASIGNAR) {
        const body = JSON.parse(CONTEO_ASIGNAR);
        carrera('conteo: asignar articulos de la misma bodega a la vez',
            repetir(N, () => peticion('POST', '/api/v1/conteo-fisico/asignar_articulos', body, ctx.tokenAdmin, 'POST /conteo-fisico/asignar_articulos')),
            1);
    }

    if (CONTEO_CERRAR) {
        carrera('conteo: cerrar la misma bodega a la vez',
            repetir(N, () => peticion('POST', '/api/v1/conteo-fisico/cerrar',
                { bodega: BODEGA_CONTEO, empresa: EMPRESA }, ctx.tokenContador, 'POST /conteo-fisico/cerrar')),
            1);
    }
}
