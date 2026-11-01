import { useState } from 'react';
import { api } from '../api.js';
import { useAuth } from '../auth.jsx';

const EMPTY = { firstName: '', lastName: '', username: '', email: '', phone: '', password: '' };

export default function AuthPage() {
  const { login } = useAuth();
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState(EMPTY);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    setBusy(true); setError(''); setNotice('');
    try {
      if (mode === 'login') {
        await login(form.username, form.password);
      } else {
        await api.register(form);
        // Your wallet is created automatically; sign straight in.
        await login(form.username, form.password);
      }
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }

  const reg = mode === 'register';
  return (
    <div className="auth">
      <section className="auth-pitch">
        <h1>FinCore</h1>
        <p>Hold money in a wallet, add funds with Razorpay, and send it to anyone by name, username or mobile number.</p>
      </section>
      <form className="panel auth-form" onSubmit={submit}>
        <h2>{reg ? 'Create your account' : 'Sign in'}</h2>
        {notice && <p className="ok">{notice}</p>}
        {reg && (
          <div className="row">
            <label>First name<input required maxLength={50} value={form.firstName} onChange={set('firstName')} /></label>
            <label>Last name<input required maxLength={50} value={form.lastName} onChange={set('lastName')} /></label>
          </div>
        )}
        <label>Username<input required maxLength={50} autoComplete="username" value={form.username} onChange={set('username')} /></label>
        {reg && (
          <>
            <label>Email<input type="email" required maxLength={100} value={form.email} onChange={set('email')} /></label>
            <label>Phone<input type="tel" required maxLength={15} value={form.phone} onChange={set('phone')} /></label>
          </>
        )}
        <label>Password
          <input type="password" required minLength={reg ? 8 : 1} maxLength={100}
            autoComplete={reg ? 'new-password' : 'current-password'} value={form.password} onChange={set('password')} />
          {reg && <small>At least 8 characters.</small>}
        </label>
        {error && <p className="err" role="alert">{error}</p>}
        <button className="btn" disabled={busy}>{busy ? 'Please wait…' : reg ? 'Create account' : 'Sign in'}</button>
        <button type="button" className="link" onClick={() => { setMode(reg ? 'login' : 'register'); setError(''); setNotice(''); }}>
          {reg ? 'I already have an account' : 'Create an account'}
        </button>
      </form>
    </div>
  );
}
