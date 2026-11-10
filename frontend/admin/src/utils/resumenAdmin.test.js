import { describe, expect, it } from 'vitest';
import { actividadReciente, haceCuanto, porMes, porcentajeRecuperacion, recientes, variacionMensual } from './resumenAdmin';

const hoy = new Date('2026-11-09T15:00:00Z');
const objetos = [
  { id: 1, nombre: 'Mochila', tipo: 'PERDIDO', estado: 'ACTIVO', fecha_publicacion: '2026-11-08T10:00:00Z', publicado_por: 'Ana Pérez' },
  { id: 2, nombre: 'Llaves', tipo: 'ENCONTRADO', estado: 'ACTIVO', fecha_publicacion: '2026-11-01T00:30:00Z' },
  { id: 3, nombre: 'Celular', tipo: 'PERDIDO', estado: 'RECUPERADO', fecha_publicacion: '2026-10-15T12:00:00Z', fecha_actualizacion: '2026-11-09T14:50:00Z' },
  { id: 4, nombre: 'Oculto', tipo: 'PERDIDO', estado: 'OCULTO', fecha_publicacion: '2026-11-05T12:00:00Z' },
  { id: 5, nombre: 'Muy antiguo', tipo: 'PERDIDO', estado: 'ACTIVO', fecha_publicacion: '2026-01-10T12:00:00Z' },
];

describe('Dashboard admin - objetos por mes', () => {
  it('cuenta los últimos seis meses por fecha de publicación y omite ocultos y eliminados', () => {
    const serie = porMes(objetos, { hoy });
    expect(serie.map((m) => m.etiqueta)).toEqual(['Jun', 'Jul', 'Ago', 'Set', 'Oct', 'Nov']);
    expect(serie.at(-1)).toMatchObject({ clave: '2026-11', total: 2, perdidos: 1, encontrados: 1 });
    expect(serie.at(-2)).toMatchObject({ clave: '2026-10', total: 1, perdidos: 1 });
    expect(serie.reduce((suma, m) => suma + m.total, 0)).toBe(3);
  });

  it('sin objetos devuelve los meses en cero, sin inventar datos', () => {
    expect(porMes([], { hoy }).every((m) => m.total === 0)).toBe(true);
  });

  it('compara el mes actual con el anterior y no calcula porcentaje si el anterior es cero', () => {
    expect(variacionMensual([{ total: 1 }, { total: 2 }])).toEqual({ actual: 2, anterior: 1, porcentaje: 100 });
    expect(variacionMensual([{ total: 0 }, { total: 3 }]).porcentaje).toBeNull();
  });
});

describe('Dashboard admin - recuperación y recientes', () => {
  it('calcula el porcentaje de recuperados sobre los perdidos', () => {
    expect(porcentajeRecuperacion(4, 1)).toBe(25);
    expect(porcentajeRecuperacion(0, 0)).toBeNull();
  });

  it('ordena del más nuevo al más antiguo y limita la cantidad', () => {
    expect(recientes(objetos, 'fecha_publicacion', 2).map((o) => o.id)).toEqual([1, 4]);
  });
});

describe('Dashboard admin - actividad reciente', () => {
  it('mezcla objetos, recuperaciones, denuncias y usuarios por fecha', () => {
    const actividad = actividadReciente({
      objetos,
      reportes: [{ id: 9, objeto: 1, motivo: 'SPAM', estado: 'PENDIENTE', fecha_reporte: '2026-11-09T12:00:00Z' }],
      usuarios: [{ id: 7, nombre: 'Luis', apellido: 'Abad', fecha_registro: '2026-11-09T09:00:00Z' }],
    }, 4);
    expect(actividad.map((e) => e.tipo)).toEqual(['recuperado', 'denuncia', 'usuario', 'perdido']);
    expect(actividad[1].detalle).toBe('Mochila · SPAM');
    expect(actividad[3].detalle).toBe('Mochila · Ana Pérez');
  });

  it('describe el tiempo transcurrido', () => {
    expect(haceCuanto('2026-11-09T14:50:00Z', hoy)).toBe('Hace 10 min');
    expect(haceCuanto('2026-11-09T12:00:00Z', hoy)).toBe('Hace 3 h');
    expect(haceCuanto('2026-11-07T15:00:00Z', hoy)).toBe('Hace 2 días');
    expect(haceCuanto(null, hoy)).toBe('');
  });
});
