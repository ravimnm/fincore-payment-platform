export const inr = (n, currency = 'INR') =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency }).format(Number(n ?? 0));

export const fmtDate = (s) =>
  s ? new Date(s).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : '—';

export const groups = (n = '') => String(n).replace(/(\d{4})(?=\d)/g, '$1 ');

export const newKey = (prefix) => `${prefix}-${crypto.randomUUID()}`;

export const store = {
  get(key, fallback = null) {
    try { const v = localStorage.getItem(key); return v ? JSON.parse(v) : fallback; } catch { return fallback; }
  },
  set(key, value) { try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* ignore */ } },
  del(key) { try { localStorage.removeItem(key); } catch { /* ignore */ } },
};

export const initials = (s = '') => s.slice(0, 2).toUpperCase();
export const last4 = (n = '') => String(n).slice(-4);

export function dayLabel(iso) {
  const d = new Date(iso), t = new Date(), y = new Date(Date.now() - 864e5);
  const same = (a, b) => a.toDateString() === b.toDateString();
  return same(d, t) ? 'Today' : same(d, y) ? 'Yesterday' : d.toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric' });
}
