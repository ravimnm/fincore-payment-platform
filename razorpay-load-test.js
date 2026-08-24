import http from "k6/http";
import { check } from "k6";

export const options = {
    scenarios: {
        razorpay_orders: {
            executor: "constant-arrival-rate",

            rate: 2,
            timeUnit: "1s",

            duration: "8m20s",

            preAllocatedVUs: 5,
            maxVUs: 10,
        },
    },

    thresholds: {
        http_req_failed: ["rate<0.01"],
        http_req_duration: ["p(95)<5000"],
    },
};

const BASE_URL = "http://localhost:8080";

const USERS = [
  { username: "loaduser1",  token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjEiLCJyb2xlcyI6WyJST0xFX1VTRVIiXSwiaWF0IjoxNzg3NTYzMDgzLCJleHAiOjE3ODc2NDk0ODN9.AE4OgC0dNq3KaeyoCCKaay4k7bB4hLuOPfmLxqpxyaA", walletId: 5 },
  { username: "loaduser2",  token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjIiLCJyb2xlcyI6WyJST0xFX1VTRVIiXSwiaWF0IjoxNzg3NTYzMDgzLCJleHAiOjE3ODc2NDk0ODN9.RuZWN8cFEkcaSMqv_2k2i8TJQDh06HG5Ie2ycC9LZHo", walletId: 6 },
  { username: "loaduser3",  token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjMiLCJyb2xlcyI6WyJST0xFX1VTRVIiXSwiaWF0IjoxNzg3NTYzMDgzLCJleHAiOjE3ODc2NDk0ODN9.2u_RL7vJnd1i4nKv7_qcgUnt_uS84ZnhyVi8lIb9TI8", walletId: 7 },
  { username: "loaduser4",  token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjQiLCJyb2xlcyI6WyJST0xFX1VTRVIiXSwiaWF0IjoxNzg3NTYzMDgzLCJleHAiOjE3ODc2NDk0ODN9.gJSwBigu86NYWGEJzMKhP0HuXlBCgbKbk0rGFcCMwxc", walletId: 8 },
  { username: "loaduser5",  token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjUiLCJyb2xlcyI6WyJST0xFX1VTRVIiXSwiaWF0IjoxNzg3NTYzMDg0LCJleHAiOjE3ODc2NDk0ODR9.yo8dJT6ZGo3iFybByM35uwc5IIL0tIaKNn2wgbylPHo", walletId: 9 },
  { username: "loaduser6",  token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjYiLCJyb2xlcyI6WyJST0xFX1VTRVIiXSwiaWF0IjoxNzg3NTYzMDg0LCJleHAiOjE3ODc2NDk0ODR9.pH7tWp3jCDU2lLw5dZaJrJiduK7u9MDAra-OwPsj8po", walletId: 10 },
  { username: "loaduser7",  token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjciLCJyb2xlcyI6WyJST0xFX1VTRVIiXSwiaWF0IjoxNzg3NTYzMDg0LCJleHAiOjE3ODc2NDk0ODR9.b5VWT0Bb_QxG4YZnpJH1s983d-md4wbOhkyZboMl17A", walletId: 11 },
  { username: "loaduser8",  token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjgiLCJyb2xlcyI6WyJST0xFX1VTRVIiXSwiaWF0IjoxNzg3NTYzMDg0LCJleHAiOjE3ODc2NDk0ODR9.rLJU3kKJxZ2gve9ka7rEwcK7zhc_n_eTfYV9kVXIlHo", walletId: 12 },
  { username: "loaduser9",  token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjkiLCJyb2xlcyI6WyJST0xFX1VTRVIiXSwiaWF0IjoxNzg3NTYzMDg0LCJleHAiOjE3ODc2NDk0ODR9._PHnBA7QlgfbH1Eu3Z9Xg8yroLYkONj6OdtDwXVZ1BQ", walletId: 13 },
  { username: "loaduser10", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjEwIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc4NzU2MzA4NCwiZXhwIjoxNzg3NjQ5NDg0fQ.6sxtiEecn7qUN3kssBnAwJgLM8iQb5RMLKQUW50FIN8", walletId: 14 },
  { username: "loaduser11", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjExIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc4NzU2MzA4NCwiZXhwIjoxNzg3NjQ5NDg0fQ.lzbN9N2LWolo0lhzTAj7ZjG0sJ1VUZdHvdcXPV63ZAA", walletId: 15 },
  { username: "loaduser12", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjEyIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc4NzU2MzA4NCwiZXhwIjoxNzg3NjQ5NDg0fQ.tQmjnkrCFJIjqVR1Gknye_uRjbAPyoEJbNcngegLpUo", walletId: 16 },
  { username: "loaduser13", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjEzIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc4NzU2MzA4NSwiZXhwIjoxNzg3NjQ5NDg1fQ.r4hcCLnQPZ0FCE4u6PxRL7-tiQeVJIV7XoyFEvs7rAc", walletId: 17 },
  { username: "loaduser14", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjE0Iiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc4NzU2MzA4NSwiZXhwIjoxNzg3NjQ5NDg1fQ.ZCVo7T4NH_aJzNHEcGPCL_LZZYD2eSV8jMP0O_ug5yA", walletId: 18 },
  { username: "loaduser15", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjE1Iiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc4NzU2MzA4NSwiZXhwIjoxNzg3NjQ5NDg1fQ.GaC__hLmEhPAJ2Zj7b2isgQ20DDJPPUjY2lh-V9QsdI", walletId: 19 },
  { username: "loaduser16", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjE2Iiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc4NzU2MzA4NSwiZXhwIjoxNzg3NjQ5NDg1fQ.HfQVZDwyYSBL_R6vTQhbV6g7EMvRvRPJJUwyw_-ukyM", walletId: 20 },
  { username: "loaduser17", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjE3Iiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc4NzU2MzA4NSwiZXhwIjoxNzg3NjQ5NDg1fQ.WW0aZboc2omF5z02h3BKiJknNA2A-A4T62lT2gb7Mf0", walletId: 21 },
  { username: "loaduser18", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjE4Iiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc4NzU2MzA4NSwiZXhwIjoxNzg3NjQ5NDg1fQ.su8hriXmbayfG8-08nghdUdcePOUCpfQ2HBAZFIi96Y", walletId: 22 },
  { username: "loaduser19", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjE5Iiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc4NzU2MzA4NiwiZXhwIjoxNzg3NjQ5NDg2fQ.plAZGJmc1TSrKe7FUIviwOEvFMz67ckjhfMSEGJsmkY", walletId: 23 },
  { username: "loaduser20", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjIwIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc4NzU2MzA4NiwiZXhwIjoxNzg3NjQ5NDg2fQ.IMBzjlba8YemMQjUVjaZUScMtC8GWSpusqAPLwRfu7g", walletId: 24 },
  { username: "loaduser21", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjIxIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc4NzU2MzA4NiwiZXhwIjoxNzg3NjQ5NDg2fQ.CnADOJyXaHF6Czm_3TGFMgp5ci04rdTv89BL3jlXzB4", walletId: 25 },
  { username: "loaduser22", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjIyIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc4NzU2MzA4NiwiZXhwIjoxNzg3NjQ5NDg2fQ.pVyrR2EYec_fZni1odtCHX2Jv2X4Dxdh2POE2BgnN1Q", walletId: 26 },
  { username: "loaduser23", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjIzIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc4NzU2MzA4NiwiZXhwIjoxNzg3NjQ5NDg2fQ.K4aBcTWmJrVVi6pUXc9J5sobxTPLQYAU9sm57uPOyZM", walletId: 27 },
  { username: "loaduser24", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjI0Iiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc4NzU2MzA4NiwiZXhwIjoxNzg3NjQ5NDg2fQ.voqZvonW0ubpRfp14kuzjkBe0xl6mpjvkKmk1NzAImo", walletId: 28 },
  { username: "loaduser25", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjI1Iiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc4NzU2MzA4NywiZXhwIjoxNzg3NjQ5NDg3fQ.EdBvM0X8fVCpkjRS_ri0O2fcI5UNUVfMA1Etd1IB0es", walletId: 29 },
];
export default function () {

    const userIndex = (__VU - 1) % USERS.length;
    const user = USERS[userIndex];

    /*
     * Every request gets a unique idempotency key.
     *
     * This allows us to test creation of independent
     * Razorpay orders rather than repeatedly retrieving
     * the same order.
     */
    const idempotencyKey =
        `k6-rzp-${user.username}-${__VU}-${__ITER}-${Date.now()}`;

    const payload = JSON.stringify({
        walletId: user.walletId,
        amount: 100.00,
        currency: "INR",
        idempotencyKey: idempotencyKey,
        description: "FinCore Razorpay Test Mode Load Test",
    });

    const params = {
        headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${user.token}`,
        },
        tags: {
            endpoint: "razorpay_create_order",
        },
    };

    const response = http.post(
        `${BASE_URL}/gateway/razorpay/orders`,
        payload,
        params
    );

    const success = check(response, {
        "status is 201": (r) => r.status === 201,

        "gateway is RAZORPAY": (r) => {
            if (r.status !== 201) return false;

            try {
                return JSON.parse(r.body).gateway === "RAZORPAY";
            } catch {
                return false;
            }
        },

        "orderId exists": (r) => {
            if (r.status !== 201) return false;

            try {
                const body = JSON.parse(r.body);
                return body.orderId &&
                       body.orderId.startsWith("order_");
            } catch {
                return false;
            }
        },

        "amount is correct": (r) => {
            if (r.status !== 201) return false;

            try {
                const body = JSON.parse(r.body);
                return body.amountInPaise === 10000;
            } catch {
                return false;
            }
        },
    });

    if (!success) {
        console.log(
            `VU=${__VU} ` +
            `ITER=${__ITER} ` +
            `USER=${user.username} ` +
            `WALLET=${user.walletId} ` +
            `STATUS=${response.status} ` +
            `BODY=${response.body}`
        );
    }
}