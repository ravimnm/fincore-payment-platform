import { dayLabel, fmtDate, inr, initials } from './lib.js';

const P = {
  home: 'M3 11l9-8 9 8M5 10v10h14V10',
  send: 'M5 19L19 5M9 5h10v10',
  plus: 'M12 5v14M5 12h14',
  clock: 'M12 7v5l3 2M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
  eye: 'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12zM12 15a3 3 0 100-6 3 3 0 000 6z',
  eyeoff: 'M3 3l18 18M10.6 6.1A9.7 9.7 0 0112 6c6 0 10 6 10 6a17 17 0 01-3.2 3.7M6.6 6.6A16 16 0 002 12s4 6 10 6c1.4 0 2.7-.3 3.9-.8',
  shield: 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6l8-3z',
  back: 'M15 5l-7 7 7 7',
  chart: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  refresh: 'M20 12a8 8 0 11-2.3-5.7M20 4v5h-5',
};
export const Icon = ({ name, size = 22 }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d={P[name]} />
  </svg>
);

export const Avatar = ({ text, tone }) => <span className={`avatar ${tone || ''}`}>{initials(text)}</span>;

export function ActivityRow({ a }) {
  const out = a.type === 'sent';
  const bad = a.status === 'FAILED';
  return (
    <li className="act">
      <Avatar text={out ? 'To' : '+'} tone={out ? '' : 'green'} />
      <div className="act-main">
        <b>{a.title}</b>
        <small>{a.sub ? `${a.sub} · ` : ''}{fmtDate(a.at)}</small>
      </div>
      <div className="act-amt">
        <span className={bad ? 'muted' : out ? 'debit' : 'credit'}>{out ? '−' : '+'}{inr(a.amount, a.currency)}</span>
        {bad && <small className="debit">Failed</small>}
      </div>
    </li>
  );
}

export function ActivityList({ items }) {
  const groups = [];
  items.forEach((a) => {
    const label = dayLabel(a.at);
    const g = groups[groups.length - 1];
    g && g.label === label ? g.items.push(a) : groups.push({ label, items: [a] });
  });
  return groups.map((g) => (
    <section key={g.label}>
      <h3 className="day">{g.label}</h3>
      <ul className="acts">{g.items.map((a) => <ActivityRow key={a.id} a={a} />)}</ul>
    </section>
  ));
}

export function Success({ amount, title, lines, children }) {
  return (
    <div className="success">
      <svg className="tick" viewBox="0 0 52 52" width="88" height="88" aria-hidden="true">
        <circle cx="26" cy="26" r="24" fill="#0E8F6B" />
        <path d="M15 27l8 8 14-16" fill="none" stroke="#fff" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <h1>{inr(amount)}</h1>
      <p className="muted" role="status">{title}</p>
      <dl className="receipt">{lines.map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}</dl>
      {children}
    </div>
  );
}

export function AmountInput({ value, onChange, chips = [100, 500, 1000], autoFocus }) {
  return (
    <>
      <div className="amount">
        <span>₹</span>
        <input aria-label="Amount in rupees" autoFocus={autoFocus} inputMode="decimal" placeholder="0" value={value}
          onChange={(e) => /^\d*\.?\d{0,2}$/.test(e.target.value) && onChange(e.target.value)} />
      </div>
      <div className="chips">
        {chips.map((c) => <button type="button" key={c} className="chip" onClick={() => onChange(String((Number(value) || 0) + c))}>+ ₹{c}</button>)}
      </div>
    </>
  );
}
