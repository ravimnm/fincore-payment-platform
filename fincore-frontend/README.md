# FinCore frontend (React + Vite)

```bash
cp .env.example .env     # add your Razorpay *key id*
npm install
npm run dev              # http://localhost:5173  (proxies /api -> http://localhost:8080)
```
Start the Spring Boot backend first. Set `VITE_BACKEND_URL` if it isn't on :8080.
