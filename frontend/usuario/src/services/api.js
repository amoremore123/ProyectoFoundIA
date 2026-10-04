import axios from 'axios';

export const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8081';

const api = axios.create({
  baseURL: API_URL,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    if (status === 401 && localStorage.getItem('token')) {
      localStorage.removeItem('token');
      localStorage.removeItem('usuario');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export function mensajeError(error, fallback = 'Ocurrió un error inesperado') {
  return error?.response?.data?.mensaje || error?.response?.data?.message || error?.message || fallback;
}

export function statusDe(error) {
  return error?.response?.status;
}

export function fotoUrl(foto) {
  if (!foto) return '';
  const url = foto.url || foto;
  if (!url) return '';
  if (/^https?:\/\//.test(url)) return url;
  return `${API_URL}${url.startsWith('/') ? '' : '/'}${url}`;
}

// Auth
export const login = async (correo, password) => {
  const { data } = await api.post('/api/auth/login', { correo, password });
  return data;
};

export const register = async (datos) => {
  const { data } = await api.post('/api/auth/register', datos);
  return data;
};

// Categorías
export const listarCategorias = async () => {
  const { data } = await api.get('/api/categorias');
  return data;
};

// Objetos
export const listarObjetos = async (params = {}) => {
  const { data } = await api.get('/api/objetos', { params });
  return data;
};

export const buscarObjetos = async (params = {}) => {
  const { data } = await api.get('/api/objetos/buscar', { params });
  return data;
};

export const getObjeto = async (id) => {
  const { data } = await api.get(`/api/objetos/${id}`);
  return data;
};

export const crearObjeto = async (payload) => {
  const { data } = await api.post('/api/objetos', payload);
  return data;
};

export const eliminarObjeto = async (id) => {
  await api.delete(`/api/objetos/${id}`);
};

export const getCoincidencias = async (id) => {
  const { data } = await api.get(`/api/objetos/${id}/coincidencias`);
  return data;
};

// Perfil
export const getPerfil = async () => {
  const { data } = await api.get('/api/perfil');
  return data;
};

export const actualizarPerfil = async (payload) => {
  const { data } = await api.put('/api/perfil', payload);
  return data;
};

export const getMisObjetos = async () => {
  const { data } = await api.get('/api/perfil/objetos');
  return data;
};

// Notificaciones
export const getNotificaciones = async () => {
  const { data } = await api.get('/api/notificaciones');
  return data;
};

export const marcarNotificacionLeida = async (id) => {
  const { data } = await api.put(`/api/notificaciones/${id}/leida`);
  return data;
};

export default api;
