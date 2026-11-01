
const BASE = (import.meta.env.VITE_API_URL || '/api')
  .replace(/\/+$/, '');

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

let getToken = () => null;
let onUnauthorized = () => {};

export function bindAuth(tokenFn, unauthorizedFn) {
  getToken = tokenFn;
  onUnauthorized = unauthorizedFn;
}

async function req(path, { method = 'GET', body } = {}) {
  const token = getToken();

  let res;

  const url = `${BASE}${path.startsWith('/') ? path : `/${path}`}`;

  try {
    res = await fetch(url, {
      method,
      headers: {
        'X-Requested-With': 'XMLHttpRequest',
        ...(body !== undefined
          ? { 'Content-Type': 'application/json' }
          : {}),
        ...(token
          ? { Authorization: `Bearer ${token}` }
          : {}),
      },
      body: body !== undefined
        ? JSON.stringify(body)
        : undefined,
    });
  } catch {
    throw new ApiError(
      'Cannot reach the FinCore server. Check that the backend is running on port 8081.',
      0
    );
  }

  const text = await res.text();

  let data = text;

  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    // Response is plain text
  }

  if (!res.ok) {

    if (res.status === 401 && token) {
      onUnauthorized();
    }

    const msg =
      (data && data.message) ||
      (typeof data === 'string' && data) ||
      (res.status === 403
        ? 'You do not have access to this resource.'
        : `Request failed (${res.status})`);

    throw new ApiError(msg, res.status);
  }

  return data;
}

const post = (path, body) =>
  req(path, { method: 'POST', body });

export const api = {

  // Authentication
  login: (b) => post('/auth/login', b),
  register: (b) => post('/auth/register', b),

  // Automatically retrieve authenticated user's wallet
  myWallet: () => req('/wallet/me'),

  // Find people to pay by user ID, name, username or mobile number
  searchUsers: (q) =>
    req(`/users/search?q=${encodeURIComponent(q)}`),

  // Wallet operations
  fundDev: (id, amount) =>
    post(`/wallet/${id}/fund?amount=${encodeURIComponent(amount)}`),

  // Admin
  adminWallet: (id) => req(`/admin/wallets/${id}`),

  freeze: (id) => post(`/admin/wallets/${id}/freeze`),

  activate: (id) => post(`/admin/wallets/${id}/activate`),

  // Payments
  pay: (b) => post('/payments', b),

  myPayments: () => req('/payments/me'),

  payment: (id) => req(`/payments/${id}`),

  paymentByKey: (key) =>
    req(`/payments/idempotency/${encodeURIComponent(key)}`),

  // Razorpay
  createOrder: (b) =>
    post('/gateway/razorpay/orders', b),

  verifyOrder: (b) =>
    post('/gateway/razorpay/verify', b),
};
