import { useState } from 'react';
import { exportarReporte, guardarArchivo, mensajeError, nombreExportacion } from '../services/api';
import Icono from './Icono';

const FORMATOS = [
  { formato: 'xlsx', texto: 'Excel' },
  { formato: 'pdf', texto: 'PDF' },
];

// H32: exporta el mismo reporte del tablero con el rango aplicado.
// "rango" es el filtro ya aplicado ({ desde, hasta }); "vacio" avisa que el periodo no tiene publicaciones.
export default function ExportarReporte({ rango, vacio = false, deshabilitado = false }) {
  const [enCurso, setEnCurso] = useState('');
  const [error, setError] = useState('');
  const [listo, setListo] = useState('');

  const exportar = async (formato) => {
    setEnCurso(formato);
    setError('');
    setListo('');
    try {
      const archivo = await exportarReporte(rango, formato);
      const nombre = nombreExportacion(rango, formato);
      guardarArchivo(archivo, nombre);
      setListo(`Se descargó ${nombre}.`);
    } catch (e) {
      setError(mensajeError(e, 'No se pudo generar el archivo'));
    } finally {
      setEnCurso('');
    }
  };

  return (
    <div className="exportar" role="group" aria-label="Exportar reporte">
      <span className="campo-label">Exportar reporte</span>
      <div className="acciones-fila">
        {FORMATOS.map(({ formato, texto }) => (
          <button key={formato} type="button" className="btn btn-fantasma btn-mini"
            onClick={() => exportar(formato)} disabled={deshabilitado || Boolean(enCurso)}>
            <Icono nombre="descargar" tamano={16} /> {enCurso === formato ? 'Generando...' : texto}
          </button>
        ))}
      </div>
      {vacio && !error && <p className="nota-tabla">El periodo no tiene publicaciones: el archivo saldrá con ceros.</p>}
      {listo && <p className="nota-tabla" role="status">{listo}</p>}
      {error && <p className="campo-error" role="alert">{error}</p>}
    </div>
  );
}
