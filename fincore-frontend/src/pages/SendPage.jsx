import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { useWallet } from '../wallet.jsx';
import { Avatar, AmountInput, Success } from '../ui.jsx';
import { fmtDate, inr, newKey } from '../lib.js';

export default function SendPage() {
  const { walletId, wallet, refresh, activity, addActivity } = useWallet();
  const [step, setStep] = useState('to');
  const [q, setQ] = useState('');
  const [results, setResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [target, setTarget] = useState(null); // { walletId, displayName, username, maskedPhone }
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState(null);
  const keyRef = useRef(newKey('pay')); // stays the same on retry, so a repeated tap can't pay twice

  // Debounced search by user ID, name, username or mobile number
  useEffect(() => {
    const term = q.trim();
    if (!(term.length >= 3 || /^\d+$/.test(term))) { setResults([]); return undefined; }
    let live = true;
    setSearching(true);
    const t = setTimeout(async () => {
      try {
        const r = await api.searchUsers(term);
        if (live) { setResults(r); setError(''); }
      } catch (e) { if (live) setError(e.message); }
      finally { if (live) setSearching(false); }
    }, 300);
    return () => { live = false; clearTimeout(t); };
  }, [q]);

  if (!walletId) return <p className="empty">Your wallet is loading. Open the Home tab if this doesn’t go away.</p>;

  // Recent people (unique, newest first) from past payments
  const seen = new Set();
  const recents = activity
    .filter((a) => a.type === 'sent' && a.to && a.toName && !seen.has(a.to) && seen.add(a.to))
    .slice(0, 5)
    .map((a) => ({ walletId: a.to, displayName: a.toName }));

  const amt = Number(amount);
  const short = wallet && amt > Number(wallet.balance);

  function choose(p) { setTarget(p); setError(''); setStep('amount'); }

  async function pay() {
    setBusy(true); setError('');
    try {
      const p = await api.pay({ senderWalletId: walletId, receiverWalletId: target.walletId, amount: amt,
        currency: wallet?.currency || 'INR', idempotencyKey: keyRef.current, description: note || undefined });
      addActivity({ id: `p${p.id}`, type: 'sent', to: target.walletId, toName: target.displayName, amount: p.amount,
        currency: p.currency, title: `Paid to ${target.displayName}`, sub: note || p.paymentReference, at: p.createdAt, status: 'SUCCESS' });
      keyRef.current = newKey('pay');
      setDone(p); setStep('done'); refresh();
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  function reset() { setStep('to'); setQ(''); setResults([]); setTarget(null); setAmount(''); setNote(''); setDone(null); setError(''); }

  if (step === 'done') return (
    <Success amount={done.amount} title={`Paid to ${target.displayName}`}
      lines={[['Reference', done.paymentReference], ['Status', done.status], ['Date', fmtDate(done.completedAt || done.createdAt)]]}>
      <button className="btn full" onClick={reset}>Send again</button>
      <Link to="/" className="btn ghost full">Done</Link>
    </Success>
  );

  if (step === 'amount') return (
    <>
      <div className="payee"><Avatar text={target.displayName} />
        <div><b>{target.displayName}</b>
          <small className="muted">{[target.username && `@${target.username}`, target.maskedPhone].filter(Boolean).join(' · ')}</small></div>
        <button className="link" onClick={() => setStep('to')}>Change</button></div>
      <AmountInput autoFocus value={amount} onChange={setAmount} />
      <p className="muted center">Balance {wallet ? inr(wallet.balance, wallet.currency) : '…'}</p>
      {short && <p className="err">Amount is more than your balance. <Link to="/add">Add money</Link></p>}
      <label>Add a note<input maxLength={200} value={note} onChange={(e) => setNote(e.target.value)} placeholder="Rent, dinner, thanks…" /></label>
      {error && <p className="err" role="alert">{error}</p>}
      <button className="btn full" disabled={busy || !(amt >= 0.01) || short} onClick={pay}>{busy ? 'Paying…' : `Pay ${amt > 0 ? inr(amt) : ''}`}</button>
    </>
  );

  const term = q.trim();
  return (
    <>
      <h1>Send money</h1>
      <label>Find a person
        <input autoFocus value={q} onChange={(e) => setQ(e.target.value)} maxLength={50}
          placeholder="Name, username, mobile number or user ID" />
      </label>
      {error && <p className="err" role="alert">{error}</p>}
      {searching && <p className="muted">Searching…</p>}
      {!searching && results.length === 0 && term.length >= 3 && <p className="empty">No matching people found.</p>}
      {results.length > 0 && (
        <ul className="acts">
          {results.map((p) => (
            <li key={p.walletId}>
              <button className="recent" style={{ width: '100%', textAlign: 'left' }} onClick={() => choose(p)}>
                <Avatar text={p.displayName} />
                <span><b>{p.displayName}</b><br /><small className="muted">
                  {[`@${p.username}`, p.maskedPhone, `ID ${p.userId}`].filter(Boolean).join(' · ')}</small></span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {term === '' && recents.length > 0 && (
        <>
          <h2>Recent</h2>
          <div className="recents">
            {recents.map((p) => <button key={p.walletId} className="recent" onClick={() => choose(p)}><Avatar text={p.displayName} />{p.displayName}</button>)}
          </div>
        </>
      )}
    </>
  );
}
