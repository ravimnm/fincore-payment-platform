import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { useWallet } from '../wallet.jsx';
import { AmountInput, Success } from '../ui.jsx';
import { fmtDate, inr, newKey } from '../lib.js';

const RZP_KEY = import.meta.env.VITE_RAZORPAY_KEY_ID;

export default function AddMoneyPage() {
  const { walletId, wallet, refresh, addActivity } = useWallet();
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [done, setDone] = useState(null);
  if (!walletId) return <p className="empty">Your wallet is loading. Open the Home tab if this doesn’t go away.</p>;

  const amt = Number(amount);
  const ok = wallet?.status === 'ACTIVE' && amt >= 1;
  const finish = (ref, title) => {
    addActivity({ id: `g${ref}`, type: 'added', amount: amt, currency: 'INR', title, sub: ref, at: new Date().toISOString(), status: 'SUCCESS' });
    setDone({ amount: amt, ref, title }); refresh();
  };

  async function viaRazorpay() {
    if (!RZP_KEY) return setError('Set VITE_RAZORPAY_KEY_ID in .env and restart the dev server.');
    if (!window.Razorpay) return setError('Razorpay checkout failed to load. Check your connection.');
    setBusy(true); setError('');
    try {
      const order = await api.createOrder({ walletId, amount: amt, currency: 'INR', idempotencyKey: newKey('rzp'), description: 'FinCore wallet funding' });
      const res = await new Promise((resolve, reject) => {
        const rzp = new window.Razorpay({
          key: RZP_KEY, name: 'FinCore', description: 'Wallet funding', order_id: order.orderId, amount: order.amountInPaise, currency: order.currency,
          handler: (r) => api.verifyOrder({ razorpayOrderId: r.razorpay_order_id, razorpayPaymentId: r.razorpay_payment_id,
            razorpaySignature: r.razorpay_signature }).then(resolve, reject),
          modal: { ondismiss: () => reject(new Error('Checkout was closed before the payment finished.')) },
        });
        rzp.on('payment.failed', (r) => reject(new Error(r.error?.description || 'Payment failed.')));
        rzp.open();
      });
      finish(res.gatewayPaymentId, 'Added via Razorpay');
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  async function devCredit() {
    setBusy(true); setError('');
    try { await api.fundDev(walletId, amt); finish(newKey('test').slice(0, 13), 'Test credit'); }
    catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  if (done) return (
    <Success amount={done.amount} title="Added to your wallet" lines={[['Source', done.title], ['Reference', done.ref], ['Date', fmtDate(new Date())]]}>
      <Link to="/" className="btn full">Done</Link>
    </Success>
  );

  return (
    <>
      <h1>Add money</h1>
      <p className="muted">To wallet {walletId}{wallet ? ` · balance ${inr(wallet.balance, wallet.currency)}` : ''}</p>
      <AmountInput autoFocus value={amount} onChange={setAmount} chips={[100, 500, 1000, 2000]} />
      {wallet && wallet.status !== 'ACTIVE' && <p className="err">This wallet is {wallet.status.toLowerCase()} and can’t receive funds.</p>}
      {error && <p className="err" role="alert">{error}</p>}
      <button className="btn full" disabled={busy || !ok} onClick={viaRazorpay}>{busy ? 'Working…' : `Add ${amt > 0 ? inr(amt) : 'money'}`}</button>
      {import.meta.env.DEV && <button className="btn ghost full" disabled={busy || !ok} onClick={devCredit}>Test credit (dev only)</button>}
      <p className="secure"><span aria-hidden="true">🔒</span> Payments are processed by Razorpay and verified on our server before your balance changes.</p>
    </>
  );
}
