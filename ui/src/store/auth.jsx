import React, { createContext, useContext, useMemo, useState } from 'react';
import { loginRequest } from '../api/auth.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem('akcp_token'));
  const [username, setUsername] = useState(() => localStorage.getItem('akcp_username'));

  const login = async (nextUsername, password) => {
    const response = await loginRequest(nextUsername, password);
    localStorage.setItem('akcp_token', response.token);
    localStorage.setItem('akcp_username', nextUsername);
    setToken(response.token);
    setUsername(nextUsername);
  };

  const logout = () => {
    localStorage.removeItem('akcp_token');
    localStorage.removeItem('akcp_username');
    setToken(null);
    setUsername(null);
  };

  const value = useMemo(() => ({ token, username, login, logout }), [token, username]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
