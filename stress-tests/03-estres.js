// ============================================================
//  PASO 3 - Estres: hasta donde aguanta.
//
//  A diferencia de 02-carga, aqui no se simula un dia normal: se empuja el
//  ritmo de peticiones por encima de lo esperado para encontrar el PUNTO DE
//  QUIEBRE y ver como se degrada. Usa un ejecutor de llegadas
//  (ramping-arrival-rate): las peticiones siguen llegando aunque el backend
//  tarde, igual que en la vida real, en vez de esperar a que responda.
//
//  Dos perfiles (-e PERFIL=...):
//    escalera  (defecto) sube a MAX_RPS en ESCALONES iguales y sostiene cada
//              uno. Dice en que escalon se rompe.
//    pico      carga base, salto brusco a MAX_RPS, y vuelta a la base. Dice si
//              el backend se RECUPERA despues del pico o queda tocado (pool
//              envenenado, hilos colgados, conexiones Oracle sin liberar).
//
//  La mezcla es la de todos los perfiles juntos, ponderada por uso real.
//  Solo lectura: no escribe nada.
//
//  Se aborta solo si mas del 20 % de las respuestas son invalidas durante 30 s:
//  a partir de ahi ya se sabe donde quebro y seguir solo castiga la BD de dev.
//
//  Ejecutar (desde la carpeta stress-tests):
//    k6 run -e ADMIN_USER=... -e ADMIN_PASS=... -e MAX_RPS=80 03-estres.js
//    k6 run -e ADMIN_USER=... -e ADMIN_PASS=... -e PERFIL=pico -e MAX_RPS=100 03-estres.js
// ============================================================
import { Trend } from 'k6/metrics';
import exec from 'k6/execution';
import { endpointsDisponibles, ejecutar, elegirPonderado, prepararContexto } from './lib/sigapp.js';

const PERFIL = __ENV.PERFIL || 'escalera';
const MAX_RPS = parseInt(__ENV.MAX_RPS || '60', 10);
const ESCALONES = parseInt(__ENV.ESCALONES || '6', 10);
const SUBIDA_S = parseInt(__ENV.SUBIDA_S || '30', 10);
const SOSTENER_S = parseInt(__ENV.SOSTENER_S || '60', 10);
// Lo que se considera "aguanta" en cada etapa
const P95_ACEPTABLE = parseInt(__ENV.P95_ACEPTABLE || '3000', 10);
const VALIDAS_ACEPTABLE = parseFloat(__ENV.VALIDAS_ACEPTABLE || '0.99');

// ------------------------------------------------------------
//  Etapas: cada una sube en 'subida' segundos a 'rps' y lo sostiene
// ------------------------------------------------------------
function construirEtapas() {
    if (PERFIL === 'pico') {
        const base = Math.max(1, Math.round(MAX_RPS / 10));
        return [
            { nombre: 'base', rps: base, subida: 20, sostener: 60 },
            { nombre: 'pico', rps: MAX_RPS, subida: 10, sostener: 60 },
            { nombre: 'recuperacion', rps: base, subida: 10, sostener: 120 },
        ];
    }
    const etapas = [];
    for (let i = 1; i <= ESCALONES; i++) {
        etapas.push({
            nombre: `escalon-${i}`,
            rps: Math.max(1, Math.round((MAX_RPS * i) / ESCALONES)),
            subida: SUBIDA_S,
            sostener: SOSTENER_S,
        });
    }
    return etapas;
}

const ETAPAS = construirEtapas();

const stages = [];
for (const e of ETAPAS) {
    stages.push({ duration: `${e.subida}s`, target: e.rps });
    stages.push({ duration: `${e.sostener}s`, target: e.rps });
}
stages.push({ duration: '10s', target: 0 });

// k6 solo calcula una submetrica si hay un threshold sobre ella: los de
// 'p(95)>=0' y 'rate>=0' no validan nada, existen para que el resumen tenga
// los numeros de cada etapa.
const thresholds = {
    respuestas_validas: [{ threshold: 'rate>0.8', abortOnFail: true, delayAbortEval: '30s' }],
};
for (const e of ETAPAS) {
    thresholds[`http_req_duration{etapa:${e.nombre}}`] = ['p(95)>=0'];
    thresholds[`respuestas_validas{etapa:${e.nombre}}`] = ['rate>=0'];
    thresholds[`pool_agotado{etapa:${e.nombre}}`] = ['count>=0'];
    thresholds[`http_reqs{etapa:${e.nombre}}`] = ['count>=0'];
}

export const options = {
    scenarios: {
        estres: {
            executor: 'ramping-arrival-rate',
            startRate: 1,
            timeUnit: '1s',
            // Si el backend se frena, k6 necesita mas VUs para sostener el ritmo.
            // Cuando se acaban, lo reporta como 'dropped_iterations': tambien es
            // una señal de saturacion.
            preAllocatedVUs: Math.max(10, MAX_RPS),
            maxVUs: Math.max(50, MAX_RPS * 6),
            stages,
        },
    },
    thresholds,
};

const latenciaEtapa = new Trend('latencia_etapa', true);

export function setup() {
    const ctx = prepararContexto();
    console.log(`Perfil ${PERFIL}: ${ETAPAS.map((e) => `${e.nombre}=${e.rps}rps`).join(', ')}`);
    return ctx;
}

export default function (ctx) {
    const etapa = etapaActual();
    // Etiqueta TODAS las metricas de esta iteracion, incluidas las propias.
    exec.vu.metrics.tags.etapa = etapa;

    const e = elegirPonderado(endpointsDisponibles(ctx));
    const res = ejecutar(e, ctx);
    latenciaEtapa.add(res.timings.duration);
}

function etapaActual() {
    const t = exec.instance.currentTestRunDuration / 1000;
    let fin = 0;
    for (const e of ETAPAS) {
        fin += e.subida + e.sostener;
        if (t < fin) {
            return e.nombre;
        }
    }
    return ETAPAS[ETAPAS.length - 1].nombre;
}

export function handleSummary(data) {
    const valor = (m, k) => (data.metrics[m] && data.metrics[m].values ? data.metrics[m].values[k] : undefined);

    let quiebre = null;
    const filas = ETAPAS.map((e) => {
        const p95 = valor(`http_req_duration{etapa:${e.nombre}}`, 'p(95)');
        const validas = valor(`respuestas_validas{etapa:${e.nombre}}`, 'rate');
        const pool = valor(`pool_agotado{etapa:${e.nombre}}`, 'count') || 0;
        const reqs = valor(`http_reqs{etapa:${e.nombre}}`, 'count') || 0;

        const corrio = reqs > 0;
        const aguanta = corrio && p95 < P95_ACEPTABLE && validas >= VALIDAS_ACEPTABLE;
        if (corrio && !aguanta && quiebre === null) {
            quiebre = e;
        }
        return `  ${e.nombre.padEnd(14)} ${String(e.rps).padStart(5)}  ${String(reqs).padStart(7)}  ${corrio ? String(Math.round(p95)).padStart(7) : '      -'}  ${corrio ? (validas * 100).toFixed(1).padStart(6) + '%' : '      -'}  ${String(pool).padStart(5)}   ${corrio ? (aguanta ? 'aguanta' : 'DEGRADADO') : 'no llego'}`;
    });

    const descartadas = valor('dropped_iterations', 'count') || 0;
    let conclusion;
    if (quiebre === null) {
        conclusion = `Aguanto todas las etapas hasta ${MAX_RPS} peticiones/s. Subir MAX_RPS para encontrar el limite.`;
    } else if (PERFIL === 'pico' && quiebre.nombre === 'recuperacion') {
        conclusion = 'NO SE RECUPERO despues del pico: revisar pool de conexiones, hilos y sesiones Oracle.';
    } else {
        conclusion = `Punto de quiebre: ${quiebre.nombre} (${quiebre.rps} peticiones/s).`;
    }

    const resumen = `
==========================================================================
 ESTRES - perfil ${PERFIL}
==========================================================================
  etapa            rps   peticiones  p95 ms  validas  pool   resultado
${filas.join('\n')}

  Iteraciones descartadas por falta de VUs: ${descartadas}
  Criterio: p95 < ${P95_ACEPTABLE} ms y respuestas validas >= ${(VALIDAS_ACEPTABLE * 100).toFixed(0)} %

  ${conclusion}
==========================================================================
`;
    return {
        stdout: resumen,
        [`resultados/estres-${PERFIL}.json`]: JSON.stringify(data, null, 2),
    };
}
