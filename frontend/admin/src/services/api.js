import axios from 'axios';

export const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8081';

const api = axios.create({
  baseURL: API_URL,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    if (status === 401 && localStorage.getItem('admin_token')) {
      localStorage.removeItem('admin_token');
      localStorage.removeItem('admin_usuario');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export function mensajeError(error, fallback = 'Ocurrió un error inesperado') {
  return (
    error?.response?.data?.mensaje ||
    error?.response?.data?.message ||
    (typeof error?.response?.data === 'string' ? error.response.data : null) ||
    error?.message ||
    fallback
  );
}

export function statusDe(error) {
  return error?.response?.status;
}

export function erroresDeValidacion(error) {
  const data = error?.response?.data;
  if (!data || typeof data !== 'object') return null;
  const salida = {};
  Object.entries(data).forEach(([campo, valor]) => {
    if (campo === 'detail' || campo === 'mensaje' || campo === 'message') return;
    salida[campo] = Array.isArray(valor) ? valor.join(' ') : String(valor);
  });
  return Object.keys(salida).length ? salida : null;
}

// Auth
export const loginAdmin = async (correo, password) => {
  const { data } = await api.post('/api/admin/auth/login/', { correo, password });
  return data;
};

// Dashboard
export const getDashboard = async () => {
  const { data } = await api.get('/api/admin/dashboard/');
  return data;
};

// Listas paginadas DRF {count, results}
export const getUsuarios = async () => {
  const { data } = await api.get('/api/admin/usuarios/');
  return data;
};

export const patchUsuario = async (id, payload) => {
  const { data } = await api.patch(`/api/admin/usuarios/${id}/`, payload);
  return data;
};

export const getPublicaciones = async () => {
  const { data } = await api.get('/api/admin/publicaciones/');
  return data;
};

export const patchPublicacion = async (id, payload) => {
  const { data } = await api.patch(`/api/admin/publicaciones/${id}/`, payload);
  return data;
};

export const getReportes = async () => {
  const { data } = await api.get('/api/admin/reportes/');
  return data;
};

export const patchReporte = async (id, payload) => {
  const { data } = await api.patch(`/api/admin/reportes/${id}/`, payload);
  return data;
};

export const getCategorias = async () => {
  const { data } = await api.get('/api/admin/categorias/');
  return data;
};

export const crearCategoria = async (payload) => {
  const { data } = await api.post('/api/admin/categorias/', payload);
  return data;
};

export const patchCategoria = async (id, payload) => {
  const { data } = await api.patch(`/api/admin/categorias/${id}/`, payload);
  return data;
};

export default api;
