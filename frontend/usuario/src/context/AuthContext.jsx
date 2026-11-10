import { createContext, useContext, useEffect, useState } from 'react';
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

  // H12: si otra pestaña inicia o cierra sesión, esta se actualiza sin recargar
  useEffect(() => {
    const alCambiarStorage = (e) => {
      if (e.key !== null && e.key !== 'token' && e.key !== 'usuario') return;
      setToken(localStorage.getItem('token'));
      setUser(leerUsuario());
    };
    window.addEventListener('storage', alCambiarStorage);
    return () => window.removeEventListener('storage', alCambiarStorage);
  }, []);

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

  const borrarSesionLocal = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('usuario');
    setToken(null);
    setUser(null);
  };

  // H37: la sesión local se cierra al instante y el token se revoca en el servidor.
  // Si el servidor no responde, la sesión de este navegador igual queda cerrada.
  const logout = () => {
    const actual = localStorage.getItem('token');
    borrarSesionLocal();
    if (actual) api.cerrarSesionServidor(actual).catch(() => {});
  };

  // H37: cierra la sesión en todos los dispositivos de la cuenta. Primero confirma con el
  // servidor; si falla, se informa y la sesión sigue abierta para poder reintentar.
  const logoutTodos = async () => {
    const actual = localStorage.getItem('token');
    if (actual) await api.cerrarSesionServidor(actual, { todos: true });
    borrarSesionLocal();
  };

  return (
    <AuthContext.Provider value={{ user, token, login, registrar, verificar, logout, logoutTodos, actualizarUsuario }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
