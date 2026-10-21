import axios from 'axios';

export const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8000';

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

// H15: con 401 (sesión vencida) o 403 (la cuenta dejó de ser ADMIN o fue suspendida)
// se borra la sesión y se sale del panel, para que no queden datos administrativos a la vista.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    if ((status === 401 || status === 403) && localStorage.getItem('admin_token')) {
      localStorage.removeItem('admin_token');
      localStorage.removeItem('admin_usuario');
      window.location.href = `/login?motivo=${status === 403 ? 'sin-permiso' : 'sesion-vencida'}`;
    }
    return Promise.reject(error);
  }
);

// Django responde los errores en "detail"; axios trae sus mensajes en inglés.
export function mensajeError(error, fallback = 'Ocurrió un error inesperado') {
  const data = error?.response?.data;
  const servidor = data?.detail || data?.mensaje || data?.message || (typeof data === 'string' ? data : null);
  if (servidor) return servidor;
  if (error?.response) return `${fallback} (error ${error.response.status}).`;
  if (error?.code === 'ECONNABORTED') return `${fallback}: el servidor tardó demasiado en responder.`;
  if (error?.isAxiosError || error?.message === 'Network Error') {
    return `${fallback}: no hay conexión con el servidor. Revisa tu conexión e inténtalo de nuevo.`;
  }
  return error?.message || fallback;
}

// Las listas de Django vienen paginadas de 10 en 10: se recorren todas las páginas.
async function obtenerTodas(ruta) {
  const todos = [];
  let siguiente = ruta;
  for (let pagina = 0; siguiente && pagina < 200; pagina += 1) {
    const { data } = await api.get(siguiente);
    if (Array.isArray(data)) return data;
    todos.push(...(data?.results || []));
    siguiente = data?.next || null;
  }
  return todos;
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

// Listas paginadas DRF {count, results, next}: se devuelven todos los registros.
export const getUsuarios = async () => obtenerTodas('/api/admin/usuarios/');

export const patchUsuario = async (id, payload) => {
  const { data } = await api.patch(`/api/admin/usuarios/${id}/`, payload);
  return data;
};

export const getPublicaciones = async () => obtenerTodas('/api/admin/publicaciones/');

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
