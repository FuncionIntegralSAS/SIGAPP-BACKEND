import http from 'k6/http';
import { check, sleep } from 'k6';

// Configuración de la prueba (Escenarios de carga)
export const options = {
    stages: [
        { duration: '30s', target: 10 }, // Fase de calentamiento: sube a 10 usuarios en 30s
        { duration: '1m', target: 50 },  // Fase de carga: sube a 50 usuarios en 1 minuto
        { duration: '1m', target: 50 },  // Mantiene 50 usuarios por 1 minuto
        { duration: '30s', target: 0 },  // Fase de enfriamiento: baja a 0 usuarios
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'], // El 95% de las peticiones debe tardar menos de 500ms
        http_req_failed: ['rate<0.01'],   // El ratio de fallos debe ser menor al 1%
    },
};

const BASE_URL = 'http://localhost:8080'; // Ajusta el puerto si es distinto

export default function () {
    const url = `${BASE_URL}/login/contador`;
    
    // Payload extraído de LoginContadorRequest
    const payload = JSON.stringify({
        documento: '123456789',   // Reemplaza con un documento válido de prueba
        codigoTemporal: '12345' // Reemplaza con un código temporal válido 
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const res = http.post(url, payload, params);

    // Verificaciones
    check(res, {
        'status es 200': (r) => r.status === 200,
        // Puedes agregar más validaciones, por ejemplo, que la respuesta tenga un token
        // 'tiene token': (r) => r.json().hasOwnProperty('token')
    });

    // Pausa simulando el tiempo que le toma a un usuario pensar antes de la siguiente acción
    sleep(1);
}
