// ============================================================
//  PASO 1 - Baseline. Corre ESTO antes que nada.
//
//  1 solo usuario, sin concurrencia. Mide el piso real de CADA endpoint de
//  lectura de los tres modulos: cuanto tarda una peticion cuando NADA compite
//  por el pool de conexiones.
//
//  De aqui salen los umbrales de la prueba de carga: deja el archivo
//  resultados/baseline.json, que 02-carga.js lee para fijar un p95 por
//  endpoint. Poner p(95)<500 a ojo no sirve: la BD de dev esta en Phoenix y
//  solo el viaje de red ya puede gastarse buena parte de ese presupuesto.
//
//  Ejecutar (desde la carpeta stress-tests):
//    k6 run -e ADMIN_USER=... -e ADMIN_PASS=... 01-baseline.js
// ============================================================
import { sleep } from 'k6';
import { ENDPOINTS, endpointsDisponibles, ejecutar, prepararContexto } from './lib/sigapp.js';

// Una submetrica por endpoint: un p95 global mezclando todos no dice cual es
// el lento, que es justo lo que un baseline tiene que responder. k6 solo
// calcula una submetrica si tiene un threshold, y 'p(95)>=0' no valida nada:
// existe para que el resumen traiga el numero. Esta corrida no valida
// rendimiento, solo mide.
const thresholds = {};
for (const e of ENDPOINTS) {
    thresholds[`http_req_duration{endpoint:${e.nombre}}`] = ['p(95)>=0'];
}

export const options = {
    vus: 1,
    duration: __ENV.DURACION || '1m',
    thresholds,
};

export function setup() {
    return prepararContexto();
}

export default function (ctx) {
    for (const e of endpointsDisponibles(ctx)) {
        ejecutar(e, ctx);
        sleep(0.3);
    }
}

export function handleSummary(data) {
    const ms = (n) => `${Math.round(n)}`.padStart(6);

    const filas = ENDPOINTS
        .map((e) => {
            const m = data.metrics[`http_req_duration{endpoint:${e.nombre}}`];
            if (!m || !m.values || !m.values.max) {
                return null;
            }
            return {
                nombre: e.nombre,
                p95: m.values['p(95)'],
                avg: m.values.avg,
                med: m.values.med,
                max: m.values.max,
            };
        })
        .filter((f) => f !== null && !Number.isNaN(f.p95))
        .sort((a, b) => b.p95 - a.p95);

    // Umbral por endpoint para la prueba de carga: el triple del p95 aislado,
    // redondeado a la centena y nunca por debajo de 300 ms.
    const umbrales = {};
    for (const f of filas) {
        umbrales[f.nombre] = Math.max(300, Math.ceil((f.p95 * 3) / 100) * 100);
    }

    const tabla = filas
        .map((f) => `  ${ms(f.p95)}  ${ms(f.avg)}  ${ms(f.med)}  ${ms(f.max)}  ${ms(umbrales[f.nombre])}   ${f.nombre}`)
        .join('\n');

    const omitidos = ENDPOINTS.filter((e) => !filas.find((f) => f.nombre === e.nombre)).map((e) => e.nombre);

    const p95Global = data.metrics.http_req_duration.values['p(95)'];
    const tasaChecks = data.metrics.checks ? data.metrics.checks.values.rate * 100 : 100;
    const tecnicos = data.metrics.errores_tecnicos ? data.metrics.errores_tecnicos.values.count : 0;

    const resumen = `
==========================================================================
 BASELINE - 1 usuario, sin concurrencia (piso de latencia)
==========================================================================
     p95     avg     med     max  umbral   endpoint
${tabla}

  --- global ---
  p95 ${Math.round(p95Global)} ms | ${data.metrics.http_reqs.values.count} peticiones
  checks OK: ${tasaChecks.toFixed(1)}%   errores tecnicos: ${tecnicos}
${omitidos.length ? `\n  Sin medir (faltan datos o credenciales): ${omitidos.join(', ')}\n` : ''}
  'umbral' = p95 x 3: lo que 02-carga.js exigira a cada endpoint con
  concurrencia. Se guarda en resultados/baseline.json.
==========================================================================
`;

    return {
        stdout: resumen,
        'resultados/baseline.json': JSON.stringify({
            fecha: new Date().toISOString(),
            p95Global,
            umbrales,
        }, null, 2),
    };
}
