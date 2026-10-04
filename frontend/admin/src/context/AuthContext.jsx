import { createContext, useContext, useState } from 'react';
import * as api from '../services/api';

const AuthContext = createContext(null);

function leerUsuario() {
  try {
    const crudo = localStorage.getItem('admin_usuario');
    return crudo ? JSON.parse(crudo) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem('admin_token'));
  const [user, setUser] = useState(leerUsuario);

  const login = async (correo, password) => {
    const data = await api.loginAdmin(correo, password);
    localStorage.setItem('admin_token', data.token);
    localStorage.setItem('admin_usuario', JSON.stringify(data.usuario));
    setToken(data.token);
    setUser(data.usuario);
    return data;
  };

  const logout = () => {
    localStorage.removeItem('admin_token');
    localStorage.removeItem('admin_usuario');
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, token, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
