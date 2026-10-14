// ============================================================
//  PASO 2 - Carga con concurrencia realista.
//
//  Simula a la vez a los tres perfiles que usan el backend, cada uno en su
//  propio escenario, corriendo EN PARALELO:
//
//    contadores       la app de conteo: pendientes, bodegas, asignados,
//                     login y refresh. Suben hasta CONTADORES usuarios.
//    administradores  bandejas y detalles de traspasos y requisiciones,
//                     activos, personas y firmas. Suben hasta ADMINS.
//    publico          la app validando el QR contra /health/info a un ritmo
//                     fijo (PUBLICO_RPS por segundo), llegue o no respuesta.
//    escritores       OPCIONAL (-e ESCRIBIR=1). Crean un traspaso y lo
//                     rechazan, en bucle, mientras los demas leen. Mide la
//                     contencion de bloqueos y del pool entre lectura y
//                     escritura. Cada tramite queda en 'na', sin movimiento
//                     de inventario, pero deja su documento REQUSUMI.
//
//  Que valida:
//    - p95 de cada endpoint por debajo del umbral calibrado en el baseline
//      (resultados/baseline.json) o de UMBRAL_P95 si se da.
//    - Menos de 1 % de respuestas invalidas.
//    - Ningun error tecnico ni de pool agotado por encima de ERRORES_MAX.
//
//  Ejecutar (desde la carpeta stress-tests):
//    k6 run -e ADMIN_USER=... -e ADMIN_PASS=... 02-carga.js
//    k6 run -e ADMIN_USER=... -e ADMIN_PASS=... -e CONTADORES=50 -e ESCRIBIR=1 02-carga.js
// ============================================================
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import {
    BASE_URL, EMPRESA, BODEGA_PE, PERSONA_FUENTE,
    ENDPOINTS, endpointsDisponibles, ejecutar, elegirPonderado, prepararContexto, pensar,
    conToken, cuerpo, registrar, mensaje,
} from './lib/sigapp.js';

const CONTADORES = parseInt(__ENV.CONTADORES || '30', 10);
const ADMINS = parseInt(__ENV.ADMINS || '10', 10);
const PUBLICO_RPS = parseInt(__ENV.PUBLICO_RPS || '2', 10);
const RAMPA = __ENV.RAMPA || '1m';
const MESETA = __ENV.MESETA || '3m';
const ESCRIBIR = __ENV.ESCRIBIR === '1';
const ESCRITORES = parseInt(__ENV.ESCRITORES || '2', 10);
const MAX_CICLOS = parseInt(__ENV.MAX_CICLOS || '10', 10);
const ERRORES_MAX = parseInt(__ENV.ERRORES_MAX || '0', 10);

// ------------------------------------------------------------
//  Umbrales por endpoint: del baseline si existe
// ------------------------------------------------------------
let baseline = null;
try {
    baseline = JSON.parse(open('./resultados/baseline.json'));
} catch (e) {
    baseline = null;
}
const UMBRAL_FIJO = __ENV.UMBRAL_P95 ? parseInt(__ENV.UMBRAL_P95, 10) : null;
const UMBRAL_DEFECTO = 2000;

function umbralDe(nombre) {
    if (UMBRAL_FIJO) {
        return UMBRAL_FIJO;
    }
    if (baseline && baseline.umbrales && baseline.umbrales[nombre]) {
        return baseline.umbrales[nombre];
    }
    return UMBRAL_DEFECTO;
}

const thresholds = {
    respuestas_validas: ['rate>0.99'],
    errores_tecnicos: [`count<=${ERRORES_MAX}`],
    pool_agotado: [`count<=${ERRORES_MAX}`],
    http_req_failed: ['rate<0.05'],
};
for (const e of ENDPOINTS) {
    thresholds[`http_req_duration{endpoint:${e.nombre}}`] = [`p(95)<${umbralDe(e.nombre)}`];
}
if (ESCRIBIR) {
    thresholds['http_req_duration{endpoint:POST /traspasos/crear}'] = [`p(95)<${UMBRAL_FIJO || 5000}`];
    thresholds['http_req_duration{endpoint:POST /traspasos/process/{id}}'] = [`p(95)<${UMBRAL_FIJO || 5000}`];
}

const duracionTotal = `${segundos(RAMPA) * 2 + segundos(MESETA)}s`;

const scenarios = {
    contadores: {
        executor: 'ramping-vus',
        exec: 'contador',
        startVUs: 0,
        stages: [
            { duration: RAMPA, target: CONTADORES },
            { duration: MESETA, target: CONTADORES },
            { duration: RAMPA, target: 0 },
        ],
        gracefulRampDown: '30s',
    },
    administradores: {
        executor: 'ramping-vus',
        exec: 'administrador',
        startVUs: 0,
        stages: [
            { duration: RAMPA, target: ADMINS },
            { duration: MESETA, target: ADMINS },
            { duration: RAMPA, target: 0 },
        ],
        gracefulRampDown: '30s',
    },
    publico: {
        executor: 'constant-arrival-rate',
        exec: 'publico',
        rate: PUBLICO_RPS,
        timeUnit: '1s',
        duration: duracionTotal,
        preAllocatedVUs: Math.max(2, PUBLICO_RPS * 2),
        maxVUs: Math.max(10, PUBLICO_RPS * 10),
    },
};

// Un escenario de VUs con objetivo cero no tiene nada que correr y k6 lo
// rechaza: se quita en vez de exigir siempre los dos perfiles.
if (CONTADORES <= 0) {
    delete scenarios.contadores;
}
if (ADMINS <= 0) {
    delete scenarios.administradores;
}

if (ESCRIBIR) {
    scenarios.escritores = {
        executor: 'constant-vus',
        exec: 'escritor',
        vus: ESCRITORES,
        // Empiezan cuando la rampa ya cargo la lectura, para medir la contencion
        // en la meseta y no en el arranque.
        startTime: RAMPA,
        duration: MESETA,
        gracefulStop: '1m',
    };
}

export const options = { scenarios, thresholds };

const ciclosEscritura = new Counter('ciclos_escritura');

// ------------------------------------------------------------
export function setup() {
    // Las escrituras sacan el usuario del token: exigen un administrador real.
    const ctx = prepararContexto(ESCRIBIR);

    if (ESCRIBIR) {
        ctx.escritura = datosEscritura(ctx.tokenAdmin);
    }

    const contador = endpointsDisponibles(ctx, 'contador').map((e) => e.nombre);
    const admin = endpointsDisponibles(ctx, 'admin').map((e) => e.nombre);
    console.log(`Contadores: ${CONTADORES} VUs sobre ${contador.length} endpoints`);
    console.log(`Administradores: ${ADMINS} VUs sobre ${admin.length} endpoints`);
    console.log(`Umbrales: ${baseline ? 'del baseline ' + baseline.fecha : (UMBRAL_FIJO ? 'fijo ' + UMBRAL_FIJO + ' ms' : 'por defecto ' + UMBRAL_DEFECTO + ' ms (no hay baseline)')}`);
    return ctx;
}

// ------------------------------------------------------------
//  Escenarios
// ------------------------------------------------------------
export function contador(ctx) {
    const e = elegirPonderado(endpointsDisponibles(ctx, 'contador'));
    ejecutar(e, ctx);
    sleep(pensar(1, 3));
}

export function administrador(ctx) {
    const lista = endpointsDisponibles(ctx, 'admin');
    if (lista.length === 0) {
        // Sin acceso al modulo administrativo este escenario no tiene que hacer.
        sleep(5);
        return;
    }
    ejecutar(elegirPonderado(lista), ctx);
    sleep(pensar(1, 4));
}

export function publico(ctx) {
    const e = elegirPonderado(endpointsDisponibles(ctx, 'publico'));
    ejecutar(e, ctx);
}

/**
 * Crea un traspaso con un activo propio de este VU y lo rechaza. Cada VU usa
 * un activo distinto: si compartieran, el segundo chocaria con el primero y se
 * mediria la regla de "activo comprometido", que es asunto de 04-concurrencia.
 */
export function escritor(ctx) {
    const libres = ctx.escritura.libres;
    const indice = (exec.vu.idInTest - 1) % libres.length;
    const activo = libres[indice];
    const cicloLocal = exec.vu.iterationInScenario;

    if (cicloLocal >= MAX_CICLOS) {
        sleep(5);
        return;
    }

    const crear = http.post(`${BASE_URL}/api/v1/traspasos/crear`, JSON.stringify({
        empresa: EMPRESA,
        personaFuente: PERSONA_FUENTE,
        personaDestino: ctx.escritura.destino,
        observacion: `Prueba de carga k6 VU ${exec.vu.idInTest}: se rechaza en el siguiente paso`,
        articulos: [{ articulo: activo.articulo, placa: activo.placa }],
    }), conToken(ctx.tokenAdmin, 'POST /traspasos/crear'));

    registrar(crear);
    const b = cuerpo(crear);
    const id = b && b.code === 0 && b.object ? b.object.id : null;
    check(crear, { 'escritor: crear traspaso': () => id !== null });
    if (id === null) {
        console.warn(`VU ${exec.vu.idInTest}: no se creo el traspaso: ${mensaje(crear)}`);
        sleep(pensar(2, 4));
        return;
    }

    sleep(pensar(0.5, 1.5));

    const rechazar = () => http.post(`${BASE_URL}/api/v1/traspasos/process/${id}`, JSON.stringify({
        estado: 'na',
        observacion: 'Prueba de carga k6',
    }), conToken(ctx.tokenAdmin, 'POST /traspasos/process/{id}'));

    let rechazo = rechazar();
    registrar(rechazo);
    if (!(cuerpo(rechazo) && cuerpo(rechazo).code === 0)) {
        // Un tramite que se queda en 'pe' deja el activo comprometido y rompe
        // los ciclos siguientes de este VU. Se reintenta una vez.
        sleep(1);
        rechazo = rechazar();
        registrar(rechazo);
    }
    const ok = check(rechazo, { 'escritor: rechazar traspaso': () => cuerpo(rechazo) && cuerpo(rechazo).code === 0 });
    if (!ok) {
        console.error(`Traspaso ${id} quedo en 'pe': rechazarlo a mano. ${mensaje(rechazo)}`);
    }
    ciclosEscritura.add(1);

    sleep(pensar(2, 5));
}

// ------------------------------------------------------------
function datosEscritura(tokenAdmin) {
    const h = conToken(tokenAdmin);
    const activos = cuerpo(http.get(
        `${BASE_URL}/api/v1/traspasos/activos?persona=${PERSONA_FUENTE}&bodega=${BODEGA_PE}&empresa=${EMPRESA}`, h));
    const personas = cuerpo(http.get(
        `${BASE_URL}/api/v1/traspasos/personas?bodega=${BODEGA_PE}&empresa=${EMPRESA}`, h));

    const libres = ((activos && activos.list) || []).filter((a) => !a.enTramite);
    const destino = ((personas && personas.list) || []).find((p) => p.cedula !== PERSONA_FUENTE);

    if (libres.length === 0 || !destino) {
        throw new Error(`ESCRIBIR=1 sin datos: activos libres=${libres.length}, destino=${destino ? destino.cedula : 'ninguno'}`);
    }
    if (libres.length < ESCRITORES) {
        console.warn(`Hay ${libres.length} activos libres para ${ESCRITORES} escritores: algunos compartiran activo y chocaran`);
    }
    return { libres: libres.map((a) => ({ articulo: a.articulo, placa: a.placa })), destino: destino.cedula };
}

function segundos(duracion) {
    const m = /^(\d+)(ms|s|m|h)$/.exec(duracion);
    if (!m) {
        throw new Error(`Duracion invalida: ${duracion}`);
    }
    const n = parseInt(m[1], 10);
    return { ms: n / 1000, s: n, m: n * 60, h: n * 3600 }[m[2]];
}
