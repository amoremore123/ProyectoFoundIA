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

// Los mensajes de axios ("Network Error", "timeout of ...") están en inglés: se traducen aquí
// para que la interfaz siempre muestre el error en español y distinto de "Sin resultados".
export function mensajeError(error, fallback = 'Ocurrió un error inesperado') {
  const servidor = error?.response?.data?.mensaje || error?.response?.data?.message;
  if (servidor) return servidor;
  if (error?.response) return `${fallback} (error ${error.response.status}).`;
  if (error?.code === 'ECONNABORTED') return `${fallback}: el servidor tardó demasiado en responder.`;
  if (error?.isAxiosError || error?.message === 'Network Error') {
    return `${fallback}: no hay conexión con el servidor. Revisa tu conexión e inténtalo de nuevo.`;
  }
  return error?.message || fallback;
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

// H11: verificación de correo
export const verificarCuenta = async (correo, codigo) => {
  const { data } = await api.post('/api/auth/verificar', { correo, codigo });
  return data;
};

export const reenviarCodigo = async (correo) => {
  const { data } = await api.post('/api/auth/reenviar-codigo', { correo });
  return data;
};

// Categorías
export const listarCategorias = async ({ signal } = {}) => {
  const { data } = await api.get('/api/categorias', { signal });
  return data;
};

// Objetos
export const listarObjetos = async (params = {}) => {
  const { data } = await api.get('/api/objetos', { params });
  return data;
};

export const buscarObjetos = async (params = {}, { signal } = {}) => {
  const { data } = await api.get('/api/objetos/buscar', { params, signal });
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

// H2: onProgreso(0..100) informa el avance real de la subida para la barra de progreso.
export const subirFotoObjeto = async (id, archivo, onProgreso) => {
  const formulario = new FormData();
  formulario.append('archivo', archivo);
  const { data } = await api.post(`/api/objetos/${id}/fotos`, formulario, {
    onUploadProgress: (e) => {
      if (onProgreso && e.total) onProgreso(Math.round((e.loaded / e.total) * 100));
    },
  });
  return data;
};

export const iniciarContacto = async (objetoPerdidoId, objetoEncontradoId) => {
  const { data } = await api.post('/api/contactos', { objetoPerdidoId, objetoEncontradoId });
  return data;
};

export const reclamarObjeto = async (id) => {
  const { data } = await api.post(`/api/objetos/${id}/reclamos`);
  return data;
};

// H5/H14: solo el autor puede editar; la API valida igual que al publicar.
export const actualizarObjeto = async (id, payload) => {
  const { data } = await api.put(`/api/objetos/${id}`, payload);
  return data;
};

export const eliminarObjeto = async (id) => {
  await api.delete(`/api/objetos/${id}`);
};

export const getCoincidencias = async (id) => {
  const { data } = await api.get(`/api/objetos/${id}/coincidencias`);
  return data;
};

export const getDetalleCoincidencia = async (id, { signal } = {}) => {
  const { data } = await api.get(`/api/coincidencias/${encodeURIComponent(id)}`, { signal });
  return data;
};

// Asistente con IA: el backend interpreta el mensaje con Claude (la API key vive solo en el servidor).
// Es público y opcional: va sin token ni interceptores para que un error nunca cierre la sesión.
export const interpretarConIA = async (mensaje, { signal } = {}) => {
  const { data } = await axios.post(`${API_URL}/api/asistente/interpretar`, { mensaje }, { signal, timeout: 12000 });
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
