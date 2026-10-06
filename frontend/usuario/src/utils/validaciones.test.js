import { describe, expect, it } from 'vitest';
import { passwordEsSegura, validarRegistro } from './validaciones';

const casos = [
  ['ASCII, 72 bytes', `Aa1!${'a'.repeat(68)}`, true],
  ['ASCII, 73 bytes', `Aa1!${'a'.repeat(69)}`, false],
  ['Acentos, 72 bytes', `Aa1!${'ñ'.repeat(34)}`, true],
  ['Acentos, 73 bytes', `Aa1!${'ñ'.repeat(34)}x`, false],
  ['Emoji, 72 bytes', `Aa1!${'🙂'.repeat(17)}`, true],
  ['Emoji, 76 bytes', `Aa1!${'🙂'.repeat(18)}`, false],
];

describe('H11 - Contraseñas y límite UTF-8 de BCrypt', () => {
  it.each(casos)('%s', (_, password, permitido) => {
    expect(passwordEsSegura(password)).toBe(permitido);
    const errores = validarRegistro({
      nombre: 'Ana', apellido: 'Prueba', correo: 'ana@prueba.invalid', password, confirmar: password,
    });
    if (permitido) expect(errores).toEqual({});
    else expect(errores.password).toContain('72 bytes');
  });

  it('mantiene las reglas de complejidad y confirmación', () => {
    expect(passwordEsSegura('sinNumero!')).toBe(false);
    expect(validarRegistro({
      nombre: 'Ana', apellido: 'Prueba', correo: 'ana@prueba.invalid',
      password: 'Segura123!', confirmar: 'Diferente123!',
    }).confirmar).toBe('Las contraseñas no coinciden.');
  });
});
