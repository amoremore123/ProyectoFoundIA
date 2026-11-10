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
// H33: términos y política de privacidad vigentes (público, sin sesión)
export const getDocumentosLegales = async ({ signal } = {}) => {
  const { data } = await api.get('/api/legal/documentos', { signal });
  return data;
};

export const verificarCuenta = async (correo, codigo) => {
  const { data } = await api.post('/api/auth/verificar', { correo, codigo });
  return data;
};

export const reenviarCodigo = async (correo) => {
  const { data } = await api.post('/api/auth/reenviar-codigo', { correo });
  return data;
};

// H35: recuperar contraseña (respuesta igual exista o no el correo)
export const solicitarRecuperacion = async (correo) => {
  const { data } = await api.post('/api/auth/recuperar-password', { correo });
  return data;
};

export const restablecerPassword = async (token, password) => {
  const { data } = await api.post('/api/auth/restablecer-password', { token, password });
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

// H45: interpretación y resumen propio opcional. Usa el token sin interceptores,
// de modo que un error del asistente no cierre la sesión de toda la web.
export const interpretarConIA = async (mensaje, { signal } = {}) => {
  const token = localStorage.getItem('token');
  const { data } = await axios.post(`${API_URL}/api/asistente/interpretar`, { mensaje }, {
    signal, timeout: 20000,
    ...(token ? { headers: { Authorization: `Bearer ${token}` } } : {}),
  });
  return data;
};

// H49: sugerencia de categoría y descripción al publicar. Usa la instancia con sesión; no guarda nada.
export const sugerirPublicacion = async ({ nombre, descripcion, tipo }) => {
  const { data } = await api.post('/api/asistente/sugerir-publicacion', { nombre, descripcion, tipo }, { timeout: 20000 });
  return data;
};

// Fase 6: audio MP3 de una respuesta de FINDI (ElevenLabs desde el backend; la clave nunca llega aquí).
// Si falla (503 sin configurar, 429 límite), quien lo usa lee con la voz del navegador.
export const obtenerVozFindi = async (texto, { signal } = {}) => {
  const { data } = await axios.post(`${API_URL}/api/asistente/voz`, { texto }, { signal, timeout: 15000, responseType: 'blob' });
  return data;
};

// Perfil
export const getPerfil = async () => {
  const { data } = await api.get('/api/perfil');
  return data;
};

// H34: solicitud de eliminación de la cuenta (no ejecuta un borrado)
export const getSolicitudEliminacion = async ({ signal } = {}) => {
  const { data } = await api.get('/api/perfil/solicitud-eliminacion', { signal });
  return data;
};

export const solicitarEliminacion = async () => {
  const { data } = await api.post('/api/perfil/solicitud-eliminacion', { confirmar: true });
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

// H21: descartar una coincidencia propia (no cambia los objetos)
export const descartarCoincidencia = async (id) => {
  const { data } = await api.post(`/api/coincidencias/${encodeURIComponent(id)}/descartar`);
  return data;
};

// H22: confirmar que recuperaste tu objeto perdido con esta coincidencia
export const confirmarRecuperacion = async (id) => {
  const { data } = await api.post(`/api/coincidencias/${encodeURIComponent(id)}/confirmar`, { confirmarRecuperacion: true });
  return data;
};

// H27: denunciar una publicación (el denunciante sale del token)
export const reportarPublicacion = async (objetoId, { motivo, descripcion }) => {
  const { data } = await api.post(`/api/objetos/${encodeURIComponent(objetoId)}/reportes`, { motivo, ...(descripcion ? { descripcion } : {}) });
  return data;
};

// Notificaciones
export const getNotificaciones = async () => {
  const { data } = await api.get('/api/notificaciones');
  return data;
};

// H24: preferencias de avisos de la cuenta del token
export const getPreferenciasNotificaciones = async ({ signal } = {}) => {
  const { data } = await api.get('/api/notificaciones/preferencias', { signal });
  return data;
};

// H53: "recordatorios" es opcional en el contrato; si no llega, el servidor conserva el valor guardado
export const guardarPreferenciasNotificaciones = async ({ coincidencias, contactos, reclamos, recordatorios }) => {
  const cuerpo = { coincidencias, contactos, reclamos };
  if (typeof recordatorios === 'boolean') cuerpo.recordatorios = recordatorios;
  const { data } = await api.put('/api/notificaciones/preferencias', cuerpo);
  return data;
};

// H37: cierre seguro. Se envía el token explícitamente porque la sesión local ya pudo borrarse.
export const cerrarSesionServidor = async (token, { todos = false } = {}) => {
  const { data } = await axios.post(
    `${API_URL}/api/auth/${todos ? 'logout-all' : 'logout'}`,
    null,
    { headers: { Authorization: `Bearer ${token}` }, timeout: 8000 },
  );
  return data;
};

// H25: historial propio paginado con filtro TODAS | NO_LEIDAS | LEIDAS
export const getHistorialNotificaciones = async ({ estado = 'TODAS', pagina = 0, tamano = 20 } = {}) => {
  const { data } = await api.get('/api/notificaciones/historial', { params: { estado, pagina, tamano } });
  return data;
};

// H25: marca como leídos todos los avisos de la cuenta del token
export const marcarTodasNotificacionesLeidas = async () => {
  const { data } = await api.put('/api/notificaciones/leidas');
  return data;
};

export const marcarNotificacionLeida = async (id) => {
  const { data } = await api.put(`/api/notificaciones/${id}/leida`);
  return data;
};

export default api;
