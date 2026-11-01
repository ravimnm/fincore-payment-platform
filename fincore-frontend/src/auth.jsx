import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, bindAuth } from './api.js';
import { store } from './lib.js';

const KEY = 'fincore.auth';
const AuthCtx = createContext(null);
export const useAuth = () => useContext(AuthCtx);

function expired(token) {
  try {
    const { exp } = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return exp * 1000 < Date.now();
  } catch { return true; }
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(() => {
    const s = store.get(KEY);
    return s && s.token && !expired(s.token) ? s : null;
  });

  const logout = useCallback(() => { store.del(KEY); setSession(null); }, []);

  useEffect(() => { bindAuth(() => session?.token, logout); }, [session, logout]);

  const login = useCallback(async (username, password) => {
    const res = await api.login({ username, password });
    const s = { token: res.token, username: res.username, roles: res.roles || [] };
    store.set(KEY, s);
    bindAuth(() => s.token, logout); // bind immediately so the next request is authenticated
    setSession(s);
  }, [logout]);

  const value = useMemo(() => ({
    ...session,
    isAuthed: !!session,
    isAdmin: !!session?.roles?.some((r) => r === 'ROLE_ADMIN' || r === 'ROLE_SUPER_ADMIN'),
    login, logout,
  }), [session, login, logout]);

  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}
