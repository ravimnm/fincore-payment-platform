import { useState } from 'react';
import { api } from '../api.js';
import { groups, inr } from '../lib.js';

export default function AdminPage() {
  const [id, setId] = useState('');
  const [wallet, setWallet] = useState(null);
  const [msg, setMsg] = useState({ error: '', ok: '' });

  const run = (fn, ok) => async (e) => {
    e?.preventDefault();
    try { const w = await fn(); if (w) setWallet(w); setMsg({ error: '', ok: ok || '' }); }
    catch (err) { setMsg({ error: err.message, ok: '' }); }
  };
  const load = () => api.adminWallet(id);
  const change = (action, verb) => run(async () => { await api[action](wallet.id); return api.adminWallet(wallet.id); }, `Wallet ${wallet?.id} ${verb}.`);

  return (
    <>
      <h1>Wallet administration</h1>
      <form className="row" onSubmit={run(load)}>
        <label className="grow">Wallet ID<input required inputMode="numeric" pattern="\d+" value={id} onChange={(e) => setId(e.target.value)} /></label>
        <button className="btn ghost">Look up</button>
      </form>
      {msg.error && <p className="err" role="alert">{msg.error}</p>}
      {msg.ok && <p className="ok" role="status">{msg.ok}</p>}
      {wallet && (
        <section className="panel">
          <h2>{groups(wallet.walletNumber)}</h2>
          <p className="muted">Wallet {wallet.id} · owner user {wallet.userId} · {inr(wallet.balance, wallet.currency)}</p>
          <p>Status: <span className={`pill ${wallet.status.toLowerCase()}`}>{wallet.status}</span></p>
          <div className="row">
            <button className="btn" disabled={wallet.status !== 'ACTIVE'} onClick={change('freeze', 'frozen')}>Freeze wallet</button>
            <button className="btn ghost" disabled={wallet.status !== 'FROZEN'} onClick={change('activate', 'reactivated')}>Reactivate wallet</button>
          </div>
        </section>
      )}
    </>
  );
}
