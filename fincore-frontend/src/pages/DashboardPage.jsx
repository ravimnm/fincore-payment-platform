import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useWallet } from '../wallet.jsx';
import { inr } from '../lib.js';

const DAY = 864e5;
const sod = (d) => { const x = new Date(d); x.setHours(0, 0, 0, 0); return x.getTime(); };

export default function DashboardPage() {
  const { walletId, wallet, activity } = useWallet();
  const [days, setDays] = useState(7);

  const d = useMemo(() => {
    const from = sod(Date.now()) - (days - 1) * DAY;
    const buckets = Array.from({ length: days }, (_, i) => ({ t: from + i * DAY, in: 0, out: 0 }));
    const byTo = {};
    let inn = 0, out = 0, count = 0;
    activity.forEach((a) => {
      const b = buckets[Math.round((sod(a.at) - from) / DAY)];
      if (a.status === 'FAILED' || !b) return;
      const v = Number(a.amount);
      count++;
      if (a.type === 'sent') { b.out += v; out += v; byTo[a.to] = (byTo[a.to] || 0) + v; } else { b.in += v; inn += v; }
    });
    const top = Object.entries(byTo).sort((x, y) => y[1] - x[1]).slice(0, 4);
    return { buckets, inn, out, count, top, max: Math.max(1, ...buckets.flatMap((b) => [b.in, b.out])) };
  }, [activity, days]);

  if (!walletId) return <p className="empty">Your wallet is loading. Open the Home tab if this doesn’t go away.</p>;
  const net = d.inn - d.out;
  const label = (t, i) => days === 7 ? new Date(t).toLocaleDateString('en-IN', { weekday: 'short' }).slice(0, 2)
    : (i % 5 === 0 ? new Date(t).getDate() : '');

  return (
    <>
      <div className="between">
        <h1>Dashboard</h1>
        <div className="seg" role="group" aria-label="Time range">
          {[7, 30].map((n) => <button key={n} aria-pressed={days === n} onClick={() => setDays(n)}>{n} days</button>)}
        </div>
      </div>
      <p className="muted">Balance {wallet ? inr(wallet.balance, wallet.currency) : '…'}</p>

      <div className="tiles">
        <div className="tile"><small>Money in</small><b className="credit">{inr(d.inn)}</b></div>
        <div className="tile"><small>Money out</small><b className="debit">{inr(d.out)}</b></div>
        <div className="tile"><small>Net change</small><b>{net < 0 ? '−' : '+'}{inr(Math.abs(net))}</b></div>
        <div className="tile"><small>Payments</small><b>{d.count}</b></div>
      </div>

      {d.count === 0 ? (
        <p className="empty">No activity in the last {days} days. <Link to="/add">Add money</Link> or <Link to="/send">send some</Link> to see your chart.</p>
      ) : (
        <>
          <h2>Daily activity</h2>
          <div className="panel chart-wrap">
            <div className="chart" role="img" aria-label={`Money in and out per day, last ${days} days`}>
              {d.buckets.map((b, i) => (
                <div className="col" key={b.t} title={`${new Date(b.t).toDateString()}: in ${inr(b.in)}, out ${inr(b.out)}`}>
                  <div className="bars">
                    <i className="bar in" style={{ height: `${(b.in / d.max) * 100}%` }} />
                    <i className="bar out" style={{ height: `${(b.out / d.max) * 100}%` }} />
                  </div>
                  <span>{label(b.t, i)}</span>
                </div>
              ))}
            </div>
            <p className="legend"><i className="dot in" />Money in <i className="dot out" />Money out</p>
          </div>

          <h2>Where your money went</h2>
          {d.top.length === 0 ? <p className="muted">You haven’t sent money in this period.</p> : (
            <ul className="acts">
              {d.top.map(([id, v]) => (
                <li key={id} className="top-row">
                  <div className="between"><b>Wallet {id}</b><span className="debit">{inr(v)}</span></div>
                  <div className="meter"><i style={{ width: `${(v / d.top[0][1]) * 100}%` }} /></div>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </>
  );
}
