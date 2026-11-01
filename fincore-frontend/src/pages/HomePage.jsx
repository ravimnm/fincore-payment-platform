import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useWallet } from '../wallet.jsx';
import { ActivityList, Icon } from '../ui.jsx';
import { groups, inr, last4 } from '../lib.js';

export default function HomePage() {
  const { wallet, error, loading, refresh, activity } = useWallet();
  const [show, setShow] = useState(true);
  const [copied, setCopied] = useState(false);

  if (loading && !wallet) return <div className="empty">Loading your FinCore wallet…</div>;

  if (!wallet) {
    return (
      <>
        <h1>My Wallet</h1>
        <div className="panel">
          <p className="err" role="alert">{error || 'Unable to load your wallet.'}</p>
          <button className="btn full" onClick={refresh}>Retry</button>
        </div>
      </>
    );
  }

  const actions = [['/send', 'send', 'Send money'], ['/add', 'plus', 'Add money'], ['/history', 'clock', 'History']];

  function copy() {
    navigator.clipboard?.writeText(wallet.walletNumber).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  }

  return (
    <>
      {error && <p className="err" role="alert">{error}</p>}
      <section className="card">
        <div className="card-top">
          <span>Wallet balance</span>
          <button className="icon-btn" onClick={() => setShow(!show)} aria-label={show ? 'Hide balance' : 'Show balance'}>
            <Icon name={show ? 'eye' : 'eyeoff'} />
          </button>
        </div>
        <div className="balance">{show ? inr(wallet.balance, wallet.currency) : '₹ ••••••'}</div>
        <div className="card-bot">
          <span>{groups(`••••••••${last4(wallet.walletNumber)}`)}</span>
          {wallet.status !== 'ACTIVE' && <b className="pill frozen">{wallet.status}</b>}
        </div>
      </section>

      <section className="panel">
        <h2>Your wallet number</h2>
        <div className="between">
          <strong>{groups(wallet.walletNumber)}</strong>
          <button className="link" onClick={copy}>{copied ? 'Copied' : 'Copy'}</button>
        </div>
        <p className="muted">People can also find you by your name, username or mobile number.</p>
      </section>

      <div className="actions">
        {actions.map(([to, icon, label]) => (
          <Link key={to} to={to} className="action"><span className="circle"><Icon name={icon} /></span>{label}</Link>
        ))}
        <button className="action" onClick={refresh}><span className="circle"><Icon name="refresh" /></span>Refresh</button>
      </div>

      <div className="between"><h2>Recent activity</h2><Link to="/history" className="link">See all</Link></div>
      {activity.length === 0
        ? <p className="empty">No activity yet. Add money or send some to get started.</p>
        : <ActivityList items={activity.slice(0, 4)} />}
    </>
  );
}
