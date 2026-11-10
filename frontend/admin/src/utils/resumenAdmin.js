// Cálculos del dashboard a partir de las listas reales de Django (objetos, denuncias y usuarios).
// No inventan datos: si una lista viene vacía, el resultado lo refleja (ceros o null).

const MESES = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Set', 'Oct', 'Nov', 'Dic'];

// Igual que el panel de métricas de Django: los ocultos y eliminados no se cuentan.
export const esVisible = (objeto) => !['OCULTO', 'ELIMINADO'].includes(objeto?.estado);

const fecha = (valor) => {
  if (!valor) return null;
  const d = new Date(valor);
  return Number.isNaN(d.getTime()) ? null : d;
};

// Objetos publicados por mes (UTC) en los últimos `meses`, incluido el mes actual.
export function porMes(objetos, { hoy = new Date(), meses = 6 } = {}) {
  const serie = [];
  for (let i = meses - 1; i >= 0; i -= 1) {
    const d = new Date(Date.UTC(hoy.getUTCFullYear(), hoy.getUTCMonth() - i, 1));
    serie.push({
      clave: `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`,
      etiqueta: MESES[d.getUTCMonth()],
      total: 0,
      perdidos: 0,
      encontrados: 0,
    });
  }
  const indice = new Map(serie.map((m, i) => [m.clave, i]));
  objetos.filter(esVisible).forEach((objeto) => {
    const d = fecha(objeto.fecha_publicacion);
    if (!d) return;
    const i = indice.get(`${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`);
    if (i === undefined) return;
    serie[i].total += 1;
    if (objeto.tipo === 'PERDIDO') serie[i].perdidos += 1;
    if (objeto.tipo === 'ENCONTRADO') serie[i].encontrados += 1;
  });
  return serie;
}

// Compara el último mes de la serie con el anterior. Sin datos del mes anterior no hay porcentaje.
export function variacionMensual(serie) {
  const actual = serie.at(-1)?.total ?? 0;
  const anterior = serie.at(-2)?.total ?? 0;
  const porcentaje = anterior > 0 ? Math.round(((actual - anterior) / anterior) * 100) : null;
  return { actual, anterior, porcentaje };
}

// Porcentaje de objetos perdidos que se recuperaron. Sin perdidos no hay porcentaje.
export function porcentajeRecuperacion(perdidos, recuperados) {
  if (!perdidos) return null;
  return Math.round((recuperados / perdidos) * 100);
}

// Los `n` elementos más nuevos según un campo de fecha.
export function recientes(lista, campo, n = 5) {
  return [...lista]
    .filter((elemento) => fecha(elemento[campo]))
    .sort((a, b) => fecha(b[campo]) - fecha(a[campo]))
    .slice(0, n);
}

// Mezcla nuevos objetos, recuperaciones, denuncias y registros de usuarios, del más nuevo al más antiguo.
export function actividadReciente({ objetos = [], reportes = [], usuarios = [] }, n = 6) {
  const nombres = new Map(objetos.map((o) => [o.id, o.nombre]));
  const eventos = [
    ...objetos.filter(esVisible).map((o) => ({
      clave: `objeto-${o.id}`,
      tipo: o.tipo === 'ENCONTRADO' ? 'encontrado' : 'perdido',
      titulo: o.tipo === 'ENCONTRADO' ? 'Objeto encontrado publicado' : 'Objeto perdido reportado',
      detalle: [o.nombre, o.publicado_por].filter(Boolean).join(' · '),
      fecha: fecha(o.fecha_publicacion),
    })),
    ...objetos.filter((o) => o.estado === 'RECUPERADO').map((o) => ({
      clave: `recuperado-${o.id}`,
      tipo: 'recuperado',
      titulo: 'Objeto marcado como recuperado',
      detalle: o.nombre,
      fecha: fecha(o.fecha_actualizacion),
    })),
    ...reportes.map((r) => ({
      clave: `denuncia-${r.id}`,
      tipo: 'denuncia',
      titulo: r.estado === 'PENDIENTE' ? 'Nueva denuncia por revisar' : 'Denuncia revisada',
      detalle: [nombres.get(r.objeto), r.motivo].filter(Boolean).join(' · '),
      fecha: fecha(r.estado === 'PENDIENTE' ? r.fecha_reporte : r.fecha_resolucion || r.fecha_reporte),
    })),
    ...usuarios.map((u) => ({
      clave: `usuario-${u.id}`,
      tipo: 'usuario',
      titulo: 'Nuevo usuario registrado',
      detalle: `${u.nombre || ''} ${u.apellido || ''}`.trim(),
      fecha: fecha(u.fecha_registro),
    })),
  ];
  return eventos.filter((e) => e.fecha).sort((a, b) => b.fecha - a.fecha).slice(0, n);
}

// "Hace 10 min", "Hace 3 h", "Hace 2 días"; después de una semana, la fecha.
export function haceCuanto(valor, ahora = new Date()) {
  const d = fecha(valor);
  if (!d) return '';
  const minutos = Math.max(0, Math.round((ahora - d) / 60000));
  if (minutos < 1) return 'Ahora';
  if (minutos < 60) return `Hace ${minutos} min`;
  const horas = Math.round(minutos / 60);
  if (horas < 24) return `Hace ${horas} h`;
  const dias = Math.round(horas / 24);
  if (dias < 7) return `Hace ${dias} ${dias === 1 ? 'día' : 'días'}`;
  return d.toLocaleDateString('es-PE', { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'UTC' });
}
