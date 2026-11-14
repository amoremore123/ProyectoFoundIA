import { beforeEach } from 'vitest';

// Cada prueba empieza sin datos de otra: el borrador de Publicar (H52) vive en sessionStorage
beforeEach(() => {
  sessionStorage.clear();
});
