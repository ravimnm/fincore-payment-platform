import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState
} from 'react';

import { api } from './api.js';
import { useAuth } from './auth.jsx';
import { store } from './lib.js';

const WalletCtx = createContext(null);

export const useWallet = () => useContext(WalletCtx);

// Map a server payment row to the shape the UI lists use
const fromPayment = (p) => {
  const sent = p.direction === 'SENT';
  return {
    id: `p${p.id}`,
    type: sent ? 'sent' : 'received',
    to: p.counterpartyWalletId,
    toName: p.counterpartyName,
    amount: p.amount,
    currency: p.currency,
    title: sent
      ? `Paid to ${p.counterpartyName}`
      : `Received from ${p.counterpartyName}`,
    sub: p.description || p.paymentReference,
    at: p.completedAt || p.createdAt,
    status: p.status === 'COMPLETED' ? 'SUCCESS' : p.status,
  };
};

export function WalletProvider({ children }) {

  const { username } = useAuth();

  const [walletId, setWalletId] = useState(null);
  const [wallet, setWallet] = useState(null);
  const [payments, setPayments] = useState([]);
  const [local, setLocal] = useState([]);

  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  // The wallet is created by the server on sign-up (or on first
  // access), so we only ever fetch it here.
  const refresh = useCallback(async () => {

    if (!username) {
      setWallet(null);
      setWalletId(null);
      setPayments([]);
      setLoading(false);
      return;
    }

    setLoading(true);
    setError('');

    try {
      const w = await api.myWallet();
      setWallet(w);
      setWalletId(w.id);

      // History is nice-to-have; don't fail the wallet if it errors
      try {
        setPayments(await api.myPayments());
      } catch { /* keep previous list */ }

    } catch (e) {
      setWallet(null);
      setWalletId(null);
      setError(e.message);
    } finally {
      setLoading(false);
    }

  }, [username]);

  useEffect(() => { refresh(); }, [refresh]);

  // Locally recorded items (money added via Razorpay / test credit)
  useEffect(() => {
    setLocal(
      walletId ? store.get(`fincore.activity.${walletId}`, []) : []
    );
  }, [walletId]);

  const addActivity = useCallback((a) => {

    if (!walletId) return;

    setLocal((prev) => {
      const next = [a, ...prev.filter((x) => x.id !== a.id)].slice(0, 100);
      store.set(`fincore.activity.${walletId}`, next);
      return next;
    });

  }, [walletId]);

  // Server payments win over local copies with the same id
  const activity = useMemo(() => {
    const seen = new Set();
    return [...payments.map(fromPayment), ...local]
      .filter((a) => !seen.has(a.id) && seen.add(a.id))
      .sort((x, y) => new Date(y.at) - new Date(x.at));
  }, [payments, local]);

  return (
    <WalletCtx.Provider
      value={{ walletId, wallet, error, loading, refresh, activity, addActivity }}
    >
      {children}
    </WalletCtx.Provider>
  );
}
