import http from "k6/http";
import { check } from "k6";

export const options = {
    stages: [
        { duration: "30s", target: 100 },
        { duration: "30s", target: 350 },
        { duration: "30s", target: 700 },
        { duration: "30s", target: 1000 },
        { duration: "30s", target: 1000 },
        { duration: "30s", target: 0 },
    ],

    thresholds: {
        http_req_failed: ["rate<0.01"],
    },
};

const BASE_URL = "http://localhost:8081";

/*
 * ============================================================
 * 25 LOAD-TEST USERS
 * ============================================================
 *
 * Add your actual JWT and wallet ID for each user.
 *
 * IMPORTANT:
 * user JWT -> that user's wallet
 *
 * Example:
 *
 * loaduser1: {
 *     token: "JWT_FOR_LOADUSER1",
 *     walletId: 5
 * }
 *
 * ============================================================
 */
const USERS = [
  { username: "loaduser1",  token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjEiLCJyb2xlcyI6WyJST0xFX1VTRVIiXSwiaWF0IjoxNzkzNTMxMDI4LCJleHAiOjE3OTM2MTc0Mjh9.3ueJFn9_NCqncyk25jWP4ZiRkUfr0hekGVqHCCikLbo", walletId: 5 },
  { username: "loaduser2",  token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjIiLCJyb2xlcyI6WyJST0xFX1VTRVIiXSwiaWF0IjoxNzkzNTMxMDI5LCJleHAiOjE3OTM2MTc0Mjl9.bo9aawetnqUouBm-sl_Ke_NQSVRWMfV2-eTW995QUrA", walletId: 6 },
  { username: "loaduser3",  token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjMiLCJyb2xlcyI6WyJST0xFX1VTRVIiXSwiaWF0IjoxNzkzNTMxMDI5LCJleHAiOjE3OTM2MTc0Mjl9.p4PvVoULo8_-IUgTWpqruPTnTwzJOlMSTGNiJrM0K8g", walletId: 7 },
  { username: "loaduser4",  token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjQiLCJyb2xlcyI6WyJST0xFX1VTRVIiXSwiaWF0IjoxNzkzNTMxMDI5LCJleHAiOjE3OTM2MTc0Mjl9.m-3JhbWaI3E-E1V-qYnSNxniJ-y1tcwNilAcbFyD-Z8", walletId: 8 },
  { username: "loaduser5",  token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjUiLCJyb2xlcyI6WyJST0xFX1VTRVIiXSwiaWF0IjoxNzkzNTMxMDI5LCJleHAiOjE3OTM2MTc0Mjl9._F00nSw8pZNhTAsT53K3PbRtg_HEKysDKr6lXdE3iG4", walletId: 9 },
  { username: "loaduser6",  token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjYiLCJyb2xlcyI6WyJST0xFX1VTRVIiXSwiaWF0IjoxNzkzNTMxMDI5LCJleHAiOjE3OTM2MTc0Mjl9.hyDOzZG7yCXgwi8TtK1eYLu59QzZZPSJsrSr4EKQp68", walletId: 10 },
  { username: "loaduser7",  token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjciLCJyb2xlcyI6WyJST0xFX1VTRVIiXSwiaWF0IjoxNzkzNTMxMDI5LCJleHAiOjE3OTM2MTc0Mjl9._hq_GYW_AfuAKT8GN7JXUayzYtBVyMVtiuoStmf2JcE", walletId: 11 },
  { username: "loaduser8",  token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjgiLCJyb2xlcyI6WyJST0xFX1VTRVIiXSwiaWF0IjoxNzkzNTMxMDMwLCJleHAiOjE3OTM2MTc0MzB9.4COLWwwPZUK93Q0OACctyLkwK7Qps_0S_8g88xp6yi4", walletId: 12 },
  { username: "loaduser9",  token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjkiLCJyb2xlcyI6WyJST0xFX1VTRVIiXSwiaWF0IjoxNzkzNTMxMDMwLCJleHAiOjE3OTM2MTc0MzB9.qNluwgsizXCuTFX2ShlMAYIiiHRZI1zmp4yLSCDWTRU", walletId: 13 },
  { username: "loaduser10", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjEwIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc5MzUzMTAzMCwiZXhwIjoxNzkzNjE3NDMwfQ.Q9XG0XndeboggshedfI1xuFf-YnExYTOuNc5hF-qPbI", walletId: 14 },
  { username: "loaduser11", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjExIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc5MzUzMTAzMCwiZXhwIjoxNzkzNjE3NDMwfQ.4coWlwcubTruutDSIwD3XMgGKDUcAG_u9J84vS0BQJA", walletId: 15 },
  { username: "loaduser12", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjEyIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc5MzUzMTAzMCwiZXhwIjoxNzkzNjE3NDMwfQ.zeL1Gm-wCxod9449CKLWw27xAvjrhxs7QID1aZVyyLw", walletId: 16 },
  { username: "loaduser13", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjEzIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc5MzUzMTAzMCwiZXhwIjoxNzkzNjE3NDMwfQ.8S5JxlxA_3YDLpaf80aos8PPhEBaQ7VAjatCiPZxmms", walletId: 17 },
  { username: "loaduser14", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjE0Iiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc5MzUzMTAzMCwiZXhwIjoxNzkzNjE3NDMwfQ.W4SBVgDfE5mODZCrUuJ-N-BCLrMx567MgdbSMr9wU3U", walletId: 18 },
  { username: "loaduser15", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjE1Iiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc5MzUzMTAzMCwiZXhwIjoxNzkzNjE3NDMwfQ.wMBJoW5vwaf7gHqOSFXX5K3ig9pMAXvzfLxxeHEHm90", walletId: 19 },
  { username: "loaduser16", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjE2Iiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc5MzUzMTAzMCwiZXhwIjoxNzkzNjE3NDMwfQ.EK8OFt5AnjMwYep3-lnt3fCD5nfqgUD_JsD4K1Hh9Cw", walletId: 20 },
  { username: "loaduser17", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjE3Iiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc5MzUzMTAzMSwiZXhwIjoxNzkzNjE3NDMxfQ.7vsjum45bwXbu5FJxol_1omXaCY2X_jQviGz8C2aBMI", walletId: 21 },
  { username: "loaduser18", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjE4Iiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc5MzUzMTAzMSwiZXhwIjoxNzkzNjE3NDMxfQ.eNbjTU1wX1wnjK1pqJ47PkGIaKUypq5Uyrg6SZT6fuE", walletId: 22 },
  { username: "loaduser19", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjE5Iiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc5MzUzMTAzMSwiZXhwIjoxNzkzNjE3NDMxfQ.p5wXU6zC9Ana7a2PgsTRyWzGVj5lhRiQHf9i2FzX-us", walletId: 23 },
  { username: "loaduser20", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjIwIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc5MzUzMTAzMSwiZXhwIjoxNzkzNjE3NDMxfQ.oxWgXhXlRcSnTabiMGGnpqLuu5SUDrIKf0Bc-85kK-8", walletId: 24 },
  { username: "loaduser21", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjIxIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc5MzUzMTAzMSwiZXhwIjoxNzkzNjE3NDMxfQ.F1DUDoQu5nhFY3kiYRAgHQirViHiKBdAC2mdPXycAwo", walletId: 25 },
  { username: "loaduser22", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjIyIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc5MzUzMTAzMSwiZXhwIjoxNzkzNjE3NDMxfQ.MdvY6QGXh0uItbJIzEuaj4rwm2p3XVshuWMnXiRFqw8", walletId: 26 },
  { username: "loaduser23", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjIzIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc5MzUzMTAzMSwiZXhwIjoxNzkzNjE3NDMxfQ.MYDo2ubWEqHkFApA2AhIS_P_7zLxyba7oC85uRVpwhI", walletId: 27 },
  { username: "loaduser24", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjI0Iiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc5MzUzMTAzMSwiZXhwIjoxNzkzNjE3NDMxfQ.CxD0tj17MMh2qNJLJDHSVmao78TgyZXh5JZFbSnX5xE", walletId: 28 },
  { username: "loaduser25", token: "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJsb2FkdXNlcjI1Iiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTc5MzUzMTAzMSwiZXhwIjoxNzkzNjE3NDMxfQ.Fmu17DB4ftaTMzJrd5ODgFLpEFwiHq0DuNCwia9Ygmw", walletId: 29 },
];
/*
 * ============================================================
 * SELECT AUTHENTICATED USER
 * ============================================================
 *
 * Each VU is assigned one of the 25 users.
 *
 * VU 1  -> user 1
 * VU 2  -> user 2
 * ...
 * VU 25 -> user 25
 * VU 26 -> user 1
 * ...
 */


/*
 * ============================================================
 * PAYMENT REQUEST
 * ============================================================
 */

export default function () {

    const senderIndex = (__VU - 1) % USERS.length;
    const sender = USERS[senderIndex];

    const receiverIndex =
        (senderIndex + 1 + (__ITER % (USERS.length - 1)))
        % USERS.length;

    const receiver = USERS[receiverIndex];

    const idempotencyKey =
        `k6-${sender.username}-${__VU}-${__ITER}`;

    const payload = JSON.stringify({
        senderWalletId: sender.walletId,
        receiverWalletId: receiver.walletId,
        amount: 1.00,
        currency: "INR",
        idempotencyKey: idempotencyKey,
        description: "k6 25-user concurrency test",
    });

    const params = {
        headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${sender.token}`,
        },
    };

    const response = http.post(
        `${BASE_URL}/payments`,
        payload,
        params
    );

    check(response, {
        "status is 201": (r) => r.status === 201,
    });

    if (response.status !== 201) {
        console.log(
            `VU=${__VU} ` +
            `ITER=${__ITER} ` +
            `USER=${sender.username} ` +
            `SENDER_WALLET=${sender.walletId} ` +
            `RECEIVER_WALLET=${receiver.walletId} ` +
            `STATUS=${response.status} ` +
            `BODY=${response.body}`
        );
    }
}