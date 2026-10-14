// ============================================================
//  Carga sobre la autenticacion.
//
//  Es lo primero que hace cada usuario al abrir la app, y a primera hora del
//  dia de conteo entran todos casi a la vez. Mide el login del contador
//  (FI_COFIUSCO), la renovacion del token y, si hay credenciales, el login de
//  administrador (USUABADA + ROLEFORM).
//
//  Ninguno escribe: el login del contador no consume el codigo temporal.
//
//  Ejecutar (desde la carpeta stress-tests):
//    k6 run -e CONTADOR_DOC=666 -e CONTADOR_COD=846166 login-contador.js
//    k6 run -e ADMIN_USER=... -e ADMIN_PASS=... -e USUARIOS=80 login-contador.js
// ============================================================
import http from 'k6/http';
import { check, sleep } from 'k6';
import {
    BASE_URL, ADMIN_USER, ADMIN_PASS, CONTADOR_DOC, CONTADOR_COD,
    cuerpo, sinToken, conToken, registrar, respuestasValidas, loginContador, pensar,
} from './lib/sigapp.js';

const USUARIOS = parseInt(__ENV.USUARIOS || '50', 10);
const UMBRAL_P95 = parseInt(__ENV.UMBRAL_P95 || '1500', 10);

export const options = {
    stages: [
        { duration: '30s', target: Math.ceil(USUARIOS / 5) }, // calentamiento
        { duration: '1m', target: USUARIOS },                 // llegada masiva
        { duration: '1m', target: USUARIOS },                 // meseta
        { duration: '30s', target: 0 },                       // salida
    ],
    thresholds: {
        'http_req_duration{endpoint:POST /auth/login/contador}': [`p(95)<${UMBRAL_P95}`],
        'http_req_duration{endpoint:POST /auth/refresh}': [`p(95)<${UMBRAL_P95}`],
        'http_req_duration{endpoint:POST /auth/login}': [`p(95)<${UMBRAL_P95}`],
        respuestas_validas: ['rate>0.99'],
        errores_tecnicos: ['count==0'],
    },
};

export function setup() {
    // Valida las credenciales una vez antes de cargar: si estan mal, todas las
    // peticiones darian 401 y el reporte no significaria nada.
    loginContador();
}

export default function () {
    const login = http.post(`${BASE_URL}/api/v1/auth/login/contador`,
        JSON.stringify({ documento: CONTADOR_DOC, codigoTemporal: CONTADOR_COD }),
        sinToken('POST /auth/login/contador'));
    registrar(login);
    const b = cuerpo(login);
    const ok = check(login, {
        'login contador: HTTP 200': (r) => r.status === 200,
        'login contador: trae token y refresh': () => b !== null && !!b.token && !!b.refreshToken,
    });
    respuestasValidas.add(ok);

    sleep(pensar(1, 3));

    // La app renueva el token sin volver a pedir el codigo.
    if (ok) {
        const refresh = http.post(`${BASE_URL}/api/v1/auth/refresh`, null, conToken(b.refreshToken, 'POST /auth/refresh'));
        registrar(refresh);
        const r = cuerpo(refresh);
        respuestasValidas.add(check(refresh, {
            'refresh: HTTP 200': (x) => x.status === 200,
            'refresh: trae token': () => r !== null && !!r.token,
        }));
    }

    // Uno de cada cinco usuarios es administrador.
    if (ADMIN_USER && ADMIN_PASS && Math.random() < 0.2) {
        const admin = http.post(`${BASE_URL}/api/v1/auth/login`,
            JSON.stringify({ username: ADMIN_USER, password: ADMIN_PASS }), sinToken('POST /auth/login'));
        registrar(admin);
        const a = cuerpo(admin);
        respuestasValidas.add(check(admin, {
            'login admin: HTTP 200': (x) => x.status === 200,
            'login admin: trae token': () => a !== null && !!a.token,
        }));
    }

    sleep(pensar(1, 3));
}
