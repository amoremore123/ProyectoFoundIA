// H11 - Reglas de validación del registro (las mismas que valida el backend)

export const bytesUtf8 = (texto) => new TextEncoder().encode(texto).length;

/*
 * H1 - Límite de la descripción de una publicación.
 * La columna es TEXT (65.535 bytes). Se cuentan caracteres reales (un emoji = 1) y cada uno ocupa
 * como máximo 4 bytes en utf8mb4: 2.000 × 4 = 8.000 bytes, siempre por debajo de la capacidad de TEXT.
 * La API debe aplicar el mismo límite antes de guardar (400 con error de campo).
 */
export const MAX_DESCRIPCION = 2000;
export const MAX_NOMBRE_OBJETO = 150;
export const MAX_UBICACION = 255;
export const largoEnCaracteres = (texto) => [...texto].length;

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
