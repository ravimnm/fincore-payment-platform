import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '30s', target: 100 },
        { duration: '30s', target: 350 },
        { duration: '30s', target: 700 },
        { duration: '30s', target: 1000 },
        { duration: '30s', target: 1000 },
        { duration: '30s', target: 0 },
    ],

    thresholds: {
        http_req_failed: ['rate<0.01'],
    },
};

const BASE_URL = 'http://localhost:8080';

export default function () {

    const token = __ENV.K6_TOKEN;

    if (!token) {
        throw new Error('K6_TOKEN environment variable is not set');
    }

    const idempotencyKey =
        `k6-${__VU}-${__ITER}-${Date.now()}`;

    const payload = JSON.stringify({
        senderWalletId: 4,
        receiverWalletId: 3,
        amount: 1.00,
        currency: 'INR',
        idempotencyKey: idempotencyKey,
        description: 'k6 load test payment'
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
        },
    };

    const response = http.post(
        `${BASE_URL}/payments`,
        payload,
        params
    );

    check(response, {
        'status is 201': (r) => r.status === 201,
    });

    if (response.status !== 201) {
        console.log(
            `VU=${__VU} ITER=${__ITER} STATUS=${response.status} BODY=${response.body}`
        );
    }

    sleep(0.1);
}