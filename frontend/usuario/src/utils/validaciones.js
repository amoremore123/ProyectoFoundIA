// H11 - Reglas de validación del registro (las mismas que valida el backend)

export const bytesUtf8 = (texto) => new TextEncoder().encode(texto).length;

/*
 * H1 - Límite de la descripción de una publicación.
 * La API usa @Size, que cuenta unidades UTF-16 como String.length de JavaScript.
 * Se conserva el límite de Amore: 16.000 unidades (un emoji ocupa 2).
 * Incluso con 3 bytes por unidad son 48.000 bytes, por debajo de TEXT (65.535).
 */
export const MAX_DESCRIPCION = 16000;
export const MAX_NOMBRE_OBJETO = 150;
export const MAX_UBICACION = 255;
export const largoEnCaracteres = (texto) => texto.length;

export const REGLAS_PASSWORD = [
  { id: 'largo', texto: 'Mínimo 8 caracteres', cumple: (p) => p.length >= 8 },
  { id: 'mayuscula', texto: 'Una letra mayúscula', cumple: (p) => /[A-Z]/.test(p) },
  { id: 'minuscula', texto: 'Una letra minúscula', cumple: (p) => /[a-z]/.test(p) },
  { id: 'numero', texto: 'Un número', cumple: (p) => /\d/.test(p) },
  { id: 'simbolo', texto: 'Un símbolo (ej. ! @ # $)', cumple: (p) => /[^A-Za-z0-9]/.test(p) },
  { id: 'maximoBcrypt', texto: 'Máximo 72 bytes en UTF-8', cumple: (p) => bytesUtf8(p) <= 72 },
];

const SOLO_LETRAS = /^[\p{L} '-]+$/u;
const CORREO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function passwordEsSegura(password) {
  return REGLAS_PASSWORD.every((r) => r.cumple(password));
}

/** Devuelve un objeto { campo: mensaje } solo con los campos que tienen error. */
export function validarRegistro({ nombre, apellido, correo, password, confirmar }) {
  const errores = {};

  if (!nombre.trim()) errores.nombre = 'El nombre es obligatorio.';
  else if (!SOLO_LETRAS.test(nombre.trim())) errores.nombre = 'El nombre solo puede contener letras.';

  if (!apellido.trim()) errores.apellido = 'El apellido es obligatorio.';
  else if (!SOLO_LETRAS.test(apellido.trim())) errores.apellido = 'El apellido solo puede contener letras.';

  if (!correo.trim()) errores.correo = 'El correo es obligatorio.';
  else if (!CORREO.test(correo.trim())) errores.correo = 'Ingresa un correo válido.';

  if (!password) errores.password = 'La contraseña es obligatoria.';
  else if (bytesUtf8(password) > 72) errores.password = 'La contraseña no debe superar los 72 bytes en UTF-8.';
  else if (!passwordEsSegura(password)) errores.password = 'La contraseña no cumple todos los requisitos.';

  if (!confirmar) errores.confirmar = 'Confirma tu contraseña.';
  else if (confirmar !== password) errores.confirmar = 'Las contraseñas no coinciden.';

  return errores;
}

export const MAX_NOMBRE_PERSONA = 100;

/**
 * H13 - Edición del perfil propio (mismas reglas que PUT /api/perfil).
 * La contraseña es opcional: si se escribe una nueva, debe cumplir las reglas
 * del registro, confirmarse e ir acompañada de la contraseña actual.
 */
export function validarPerfil({ nombre, apellido, password, confirmar, passwordActual }) {
  const errores = {};

  if (!nombre.trim()) errores.nombre = 'El nombre es obligatorio.';
  else if (nombre.trim().length > MAX_NOMBRE_PERSONA) errores.nombre = `El nombre no debe superar los ${MAX_NOMBRE_PERSONA} caracteres.`;
  else if (!SOLO_LETRAS.test(nombre.trim())) errores.nombre = 'El nombre solo puede contener letras.';

  if (!apellido.trim()) errores.apellido = 'El apellido es obligatorio.';
  else if (apellido.trim().length > MAX_NOMBRE_PERSONA) errores.apellido = `El apellido no debe superar los ${MAX_NOMBRE_PERSONA} caracteres.`;
  else if (!SOLO_LETRAS.test(apellido.trim())) errores.apellido = 'El apellido solo puede contener letras.';

  const cambiaPassword = Boolean(password || confirmar);
  if (cambiaPassword) {
    if (!password) errores.password = 'Escribe la nueva contraseña.';
    else if (bytesUtf8(password) > 72) errores.password = 'La contraseña no debe superar los 72 bytes en UTF-8.';
    else if (!passwordEsSegura(password)) errores.password = 'La contraseña no cumple todos los requisitos.';

    if (!confirmar) errores.confirmar = 'Confirma la nueva contraseña.';
    else if (confirmar !== password) errores.confirmar = 'Las contraseñas no coinciden.';

    if (!passwordActual) errores.passwordActual = 'Para cambiar la contraseña, escribe tu contraseña actual.';
  }

  return errores;
}

// H35 - Formato del enlace de recuperación (el mismo que acepta la API)
export const TOKEN_RECUPERACION = /^[A-Za-z0-9_-]{16,2048}$/;

export function correoEsValido(correo) {
  return CORREO.test(correo.trim());
}

/** H35 - Nueva contraseña al restablecer: mismas reglas que el registro. */
export function validarNuevaPassword({ password, confirmar }) {
  const errores = {};
  if (!password) errores.password = 'Escribe la nueva contraseña.';
  else if (bytesUtf8(password) > 72) errores.password = 'La contraseña no debe superar los 72 bytes en UTF-8.';
  else if (!passwordEsSegura(password)) errores.password = 'La contraseña no cumple todos los requisitos.';

  if (!confirmar) errores.confirmar = 'Confirma la nueva contraseña.';
  else if (confirmar !== password) errores.confirmar = 'Las contraseñas no coinciden.';
  return errores;
}
