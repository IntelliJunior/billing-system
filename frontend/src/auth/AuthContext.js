import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { getMe, login as loginRequest, logout as logoutRequest } from '../api/api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(() => {
    return getMe()
      .then((res) => {
        setUser(res.data);
        return res.data;
      })
      .catch(() => {
        setUser(null);
        return null;
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const login = async (username, password) => {
    await loginRequest(username, password);
    return refresh();
  };

  const logout = async () => {
    try {
      await logoutRequest();
    } catch (e) {
      // ignore: we clear the local state either way
    }
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, refresh }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
