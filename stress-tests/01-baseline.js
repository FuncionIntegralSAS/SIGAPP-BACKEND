// ============================================================
//  PASO 1 - Baseline. Corre ESTO antes que nada.
//
//  1 solo usuario, sin concurrencia. Mide el piso real de cada endpoint:
//  cuanto tarda una peticion cuando NADA compite por el pool de conexiones.
//
//  De aqui salen los umbrales del script de carga. Poner p(95)<500 a ojo no
//  sirve: la BD de dev esta en Phoenix y solo el viaje de red ya puede
//  gastarse buena parte de ese presupuesto.
//
//  Ejecutar:
//    k6 run stress-tests/01-baseline.js
// ============================================================
import { sleep } from 'k6';
import { Trend } from 'k6/metrics';
import {
    loginContador,
    endpointsLectura,
    checkLectura,
    METRICAS_LECTURA,
} from './lib/sigapp.js';

export const options = {
    vus: 1,
    duration: '30s',
    // Sin thresholds a proposito: esta corrida NO valida nada, solo mide.
    // Es la que produce los numeros para calibrar la prueba de carga.
};

// Una metrica por endpoint. Un p95 global mezclando los cinco no dice cual es
// el lento, que es justo lo que un baseline tiene que responder.
const latencias = {};
for (const metrica of METRICAS_LECTURA) {
    latencias[metrica] = new Trend(`lat_${metrica}`, true);
}

export function setup() {
    return { token: loginContador() };
}

export default function (data) {
    for (const endpoint of endpointsLectura(data.token)) {
        const res = endpoint.ejecutar();

        checkLectura(res, endpoint.nombre);
        latencias[endpoint.metrica].add(res.timings.duration);

        sleep(0.5);
    }
}

export function handleSummary(data) {
    const ms = (n) => `${Math.round(n)}`.padStart(6);

    const filas = endpointsParaReporte()
        .map((e) => {
            const m = data.metrics[`lat_${e.metrica}`];
            if (!m) return null;
            // Las metricas Trend de k6 no exponen 'count', solo estadisticos.
            return {
                nombre: e.nombre,
                p95: m.values['p(95)'],
                avg: m.values.avg,
                med: m.values.med,
                max: m.values.max,
            };
        })
        .filter(Boolean)
        .sort((a, b) => b.p95 - a.p95);

    const tabla = filas
        .map((f) => `  ${ms(f.p95)}  ${ms(f.avg)}  ${ms(f.med)}  ${ms(f.max)}   ${f.nombre}`)
        .join('\n');

    const p95Global = data.metrics.http_req_duration.values['p(95)'];
    const sugerido = Math.ceil((p95Global * 3) / 100) * 100;

    const tasaChecks = data.metrics.checks ? data.metrics.checks.values.rate * 100 : 100;
    const tasaFallos = data.metrics.http_req_failed.values.rate * 100;

    const resumen = `
==========================================================================
 BASELINE - 1 usuario, sin concurrencia (piso de latencia)
==========================================================================
     p95     avg     med     max   endpoint
${tabla}

  --- global ---
  p95 ${Math.round(p95Global)} ms | avg ${Math.round(data.metrics.http_req_duration.values.avg)} ms | ${data.metrics.http_reqs.values.count} peticiones
  checks OK: ${tasaChecks.toFixed(1)}%   peticiones fallidas: ${tasaFallos.toFixed(1)}%

  Umbral sugerido para la prueba de carga: p(95) < ${sugerido} ms
  (el triple del baseline: margen razonable al pasar de 1 a 50 usuarios)
==========================================================================
`;

    return { stdout: resumen };
}

// Solo para nombrar las filas del reporte: no ejecuta peticiones, por eso el
// token vacio es suficiente.
function endpointsParaReporte() {
    return endpointsLectura('');
}
