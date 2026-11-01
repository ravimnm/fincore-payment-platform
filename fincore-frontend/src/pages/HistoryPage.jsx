import { useState } from 'react';
import { useWallet } from '../wallet.jsx';
import { ActivityList } from '../ui.jsx';

const FILTERS = [['all', 'All'], ['sent', 'Sent'], ['received', 'Received'], ['added', 'Added']];

export default function HistoryPage() {
  const { loading, wallet, activity, refresh } = useWallet();
  const [filter, setFilter] = useState('all');

  if (loading && !wallet) return <div className="empty">Loading…</div>;

  const items = filter === 'all' ? activity : activity.filter((a) => a.type === filter);

  return (
    <>
      <div className="between">
        <h1>History</h1>
        <button className="link" onClick={refresh}>Refresh</button>
      </div>
      <div className="seg" role="group" aria-label="Filter">
        {FILTERS.map(([k, label]) => (
          <button key={k} aria-pressed={filter === k} onClick={() => setFilter(k)}>{label}</button>
        ))}
      </div>
      {items.length === 0
        ? <p className="empty">Nothing here yet.</p>
        : <ActivityList items={items} />}
    </>
  );
}
