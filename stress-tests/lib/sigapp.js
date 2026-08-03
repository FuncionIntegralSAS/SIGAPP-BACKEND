// ============================================================
//  Helpers compartidos por los scripts de carga de SIGAPP.
//  No es un test por si solo: lo importan 01-baseline, 02-carga-lectura
//  y 03-humo-transaccional.
// ============================================================
import http from 'k6/http';
import { check, fail } from 'k6';

// Todo parametrizable por variable de entorno para no tocar los scripts:
//   k6 run -e BASE_URL=http://localhost:8082 -e EMPRESA=01 02-carga-lectura.js
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';
export const EMPRESA = __ENV.EMPRESA || '01';
export const BODEGA = __ENV.BODEGA || 'FMIA';
export const DIVISION = __ENV.DIVISION || 'D001';

const DOCUMENTO = __ENV.DOCUMENTO || '666';
const CODIGO_TEMPORAL = __ENV.CODIGO_TEMPORAL || '846166';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

/**
 * Autentica UNA sola vez y devuelve el token. Se llama desde setup(), cuyo
 * valor de retorno k6 entrega a todos los VUs.
 *
 * Que cada VU haga login en cada iteracion mediria el login contra Oracle en
 * vez del endpoint bajo prueba, y ademas dispararia el consumo del pool.
 */
export function loginContador() {
    const res = http.post(
        `${BASE_URL}/api/v1/auth/login/contador`,
        JSON.stringify({ documento: DOCUMENTO, codigoTemporal: CODIGO_TEMPORAL }),
        { headers: JSON_HEADERS, tags: { name: 'POST /auth/login/contador' } },
    );

    if (res.status !== 200) {
        // fail() aborta la prueba entera: sin token todos los checks siguientes
        // darian 401 y el reporte no significaria nada.
        fail(`Login fallido (HTTP ${res.status}): ${res.body}`);
    }

    const token = res.json('token');
    if (!token) {
        fail(`El login respondio 200 pero sin campo token: ${res.body}`);
    }

    // Sin prefijo "Bearer ": ContadorAuthServiceImpl devuelve el JWT limpio y el
    // esquema viaja aparte en el campo 'type'.
    return token;
}

export function authHeaders(token) {
    return { headers: { Authorization: `Bearer ${token}` } };
}

/**
 * Los endpoints de lectura devuelven 204 cuando no hay datos, y eso NO es un
 * fallo: contarlo como error inflaria http_req_failed y ocultaria los errores
 * reales. Se valida "no fue error del servidor" y se separa el 204 aparte.
 */
export function checkLectura(res, nombre) {
    return check(res, {
        [`${nombre} responde 200 o 204`]: (r) => r.status === 200 || r.status === 204,
        [`${nombre} sin error de servidor`]: (r) => r.status < 500,
    });
}

/**
 * Nombres de metrica de cada endpoint de lectura. Se exporta aparte porque k6
 * exige crear las metricas en el contexto init, antes de que exista el token.
 */
export const METRICAS_LECTURA = [
    'empresas_getall',
    'bodegas_por_empresa',
    'bodegas_conteo_pendientes',
    'articulos_asignados',
    'conteo_pendientes',
];

/** Endpoints de solo lectura. Ninguno escribe en el ERP. */
export function endpointsLectura(token) {
    const auth = authHeaders(token);

    return [
        {
            nombre: 'GET /empresas/getAll',
            metrica: 'empresas_getall',
            ejecutar: () => http.get(`${BASE_URL}/api/v1/empresas/getAll`,
                { ...auth, tags: { name: 'GET /empresas/getAll' } }),
        },
        {
            nombre: 'GET /bodegas/empresa/{empresa}',
            metrica: 'bodegas_por_empresa',
            ejecutar: () => http.get(`${BASE_URL}/api/v1/bodegas/empresa/${EMPRESA}`,
                { ...auth, tags: { name: 'GET /bodegas/empresa' } }),
        },
        {
            nombre: 'GET /bodegas/conteo-pendientes/{empresa}',
            metrica: 'bodegas_conteo_pendientes',
            ejecutar: () => http.get(`${BASE_URL}/api/v1/bodegas/conteo-pendientes/${EMPRESA}`,
                { ...auth, tags: { name: 'GET /bodegas/conteo-pendientes' } }),
        },
        {
            nombre: 'GET /articulos/asignados/{bodega}/{empresa}',
            metrica: 'articulos_asignados',
            ejecutar: () => http.get(`${BASE_URL}/api/v1/articulos/asignados/${BODEGA}/${EMPRESA}`,
                { ...auth, tags: { name: 'GET /articulos/asignados/{bodega}/{empresa}' } }),
        },
        {
            // Requiere el claim idUsuario, que solo trae el token de contador.
            nombre: 'GET /conteo-fisico/pendientes',
            metrica: 'conteo_pendientes',
            ejecutar: () => http.get(`${BASE_URL}/api/v1/conteo-fisico/pendientes`,
                { ...auth, tags: { name: 'GET /conteo-fisico/pendientes' } }),
        },
    ];
}
