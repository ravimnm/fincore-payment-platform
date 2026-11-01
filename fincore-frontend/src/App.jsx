import { Navigate, NavLink, Outlet, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth.jsx';
import { WalletProvider } from './wallet.jsx';
import { Avatar, Icon } from './ui.jsx';
import AuthPage from './pages/AuthPage.jsx';
import HomePage from './pages/HomePage.jsx';
import SendPage from './pages/SendPage.jsx';
import AddMoneyPage from './pages/AddMoneyPage.jsx';
import DashboardPage from './pages/DashboardPage.jsx';
import HistoryPage from './pages/HistoryPage.jsx';
import AdminPage from './pages/AdminPage.jsx';

function Shell() {
  const { isAuthed, isAdmin, username, logout } = useAuth();
  if (!isAuthed) return <Navigate to="/login" replace />;
  const tabs = [['/', 'home', 'Home'], ['/send', 'send', 'Send'], ['/add', 'plus', 'Add money'], ['/dashboard', 'chart', 'Dashboard'], ['/history', 'clock', 'History']];
  return (
    <WalletProvider>
      <div className="app">
        <header className="top">
          <Avatar text={username} />
          <div className="grow"><small className="muted">Welcome back</small><b>{username}</b></div>
          {isAdmin && <NavLink to="/admin" className="link">Admin</NavLink>}
          <button className="link" onClick={logout}>Sign out</button>
        </header>
        <main><Outlet /></main>
        <nav className="tabs" aria-label="Main">
          {tabs.map(([to, icon, label]) => (
            <NavLink key={to} to={to} end={to === '/'}><Icon name={icon} /><span>{label}</span></NavLink>
          ))}
        </nav>
      </div>
    </WalletProvider>
  );
}

export default function App() {
  const { isAuthed, isAdmin } = useAuth();
  return (
    <Routes>
      <Route path="/login" element={isAuthed ? <Navigate to="/" replace /> : <AuthPage />} />
      <Route element={<Shell />}>
        <Route index element={<HomePage />} />
        <Route path="send" element={<SendPage />} />
        <Route path="add" element={<AddMoneyPage />} />
        <Route path="dashboard" element={<DashboardPage />} />
        <Route path="history" element={<HistoryPage />} />
        <Route path="admin" element={isAdmin ? <AdminPage /> : <Navigate to="/" replace />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
