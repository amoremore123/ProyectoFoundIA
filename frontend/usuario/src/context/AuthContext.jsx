import { createContext, useContext, useState } from 'react';
import * as api from '../services/api';

const AuthContext = createContext(null);

function leerUsuario() {
  try {
    const crudo = localStorage.getItem('usuario');
    return crudo ? JSON.parse(crudo) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem('token'));
  const [user, setUser] = useState(leerUsuario);

  const guardarSesion = (data) => {
    localStorage.setItem('token', data.token);
    localStorage.setItem('usuario', JSON.stringify(data.usuario));
    setToken(data.token);
    setUser(data.usuario);
  };

  const login = async (correo, password) => {
    const data = await api.login(correo, password);
    guardarSesion(data);
    return data;
  };

  // H11: el registro ya no inicia sesión; primero hay que verificar el correo
  const registrar = async (datos) => {
    const data = await api.register(datos);
    return data;
  };

  // H11: al verificar el código, el backend devuelve el JWT
  const verificar = async (correo, codigo) => {
    const data = await api.verificarCuenta(correo, codigo);
    guardarSesion(data);
    return data;
  };

  const actualizarUsuario = (usuario) => {
    localStorage.setItem('usuario', JSON.stringify(usuario));
    setUser(usuario);
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('usuario');
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, token, login, registrar, verificar, logout, actualizarUsuario }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
