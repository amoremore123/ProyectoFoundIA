import { useCallback, useEffect, useRef, useState } from 'react';
import ConfirmarDialogo from './ConfirmarDialogo';
import Icono from './Icono';
import { getSolicitudEliminacion, mensajeError, solicitarEliminacion } from '../services/api';

// La API devuelve la fecha en UTC sin zona ("2026-10-27T15:04:05"): se interpreta como UTC
export function fechaSolicitudLocal(valor) {
  if (!valor) return '';
  const texto = String(valor);
  const fecha = new Date(/[zZ]|[+-]\d\d:\d\d$/.test(texto) ? texto : `${texto}Z`);
  if (Number.isNaN(fecha.getTime())) return texto;
  return fecha.toLocaleString('es-PE', { dateStyle: 'long', timeStyle: 'short' });
}

// H34 - Solicitar la eliminación de la cuenta.
// Es un trámite: queda PENDIENTE de revisión y no borra la cuenta ni los datos.
export default function SolicitudEliminacion() {
  const [estado, setEstado] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState('');
  const [errorEnvio, setErrorEnvio] = useState('');
  const [aviso, setAviso] = useState('');
  const [confirmando, setConfirmando] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const peticion = useRef(null);

  const cargar = useCallback(async () => {
    peticion.current?.abort();
    const control = new AbortController();
    peticion.current = control;
    setCargando(true);
    setErrorCarga('');
    try {
      const data = await getSolicitudEliminacion({ signal: control.signal });
      if (control.signal.aborted) return;
      setEstado(data);
    } catch (e) {
      if (control.signal.aborted) return;
      setEstado(null);
      setErrorCarga(mensajeError(e, 'No se pudo consultar el estado de tu solicitud'));
    } finally {
      if (!control.signal.aborted) setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargar();
    return () => peticion.current?.abort();
  }, [cargar]);

  const enviar = async () => {
    setEnviando(true);
    setErrorEnvio('');
    try {
      const data = await solicitarEliminacion();
      setConfirmando(false);
      setAviso(data?.repetido
        ? 'Ya tenías una solicitud pendiente. No se creó otra.'
        : 'Tu solicitud quedó registrada. Tu cuenta sigue activa mientras se revisa.');
      // Se vuelve a consultar para mostrar el estado real guardado
      await cargar();
    } catch (e) {
      setConfirmando(false);
      setErrorEnvio(mensajeError(e, 'No se pudo registrar la solicitud'));
    } finally {
      setEnviando(false);
    }
  };

  const pendiente = estado?.estado === 'PENDIENTE';

  return (
    <section className="tarjeta eliminacion" aria-labelledby="titulo-eliminacion">
      <h2 id="titulo-eliminacion" className="eliminacion-titulo">
        <Icono nombre="escudo" tamano={18} /> Privacidad: eliminar mi cuenta
      </h2>

      {cargando && <p className="nota-suave" role="status">Consultando el estado de tu solicitud...</p>}

      {!cargando && errorCarga && (
        <div className="banner-error" role="alert">
          <Icono nombre="alerta" tamano={18} /> {errorCarga}
          <button type="button" className="btn-enlace" onClick={cargar}>Reintentar</button>
        </div>
      )}

      {!cargando && estado && (
        <>
          {aviso && <p role="status" className="aviso-accion aviso-ok">{aviso}</p>}

          {pendiente ? (
            <div className="eliminacion-estado">
              <span className="eliminacion-chip">Pendiente de revisión</span>
              {estado.fechaSolicitud && (
                <p className="nota-suave">Solicitada el {fechaSolicitudLocal(estado.fechaSolicitud)}.</p>
              )}
            </div>
          ) : (
            <p className="eliminacion-texto">
              Puedes pedir que se elimine tu cuenta de FoundIA. La solicitud será revisada antes de
              cualquier cambio.
            </p>
          )}

          {estado.informacion && <p className="nota-suave">{estado.informacion}</p>}

          {errorEnvio && <p role="alert" className="campo-error">{errorEnvio}</p>}

          {!pendiente && (
            <button
              type="button"
              className="btn btn-peligro btn-bloque"
              onClick={() => { setErrorEnvio(''); setAviso(''); setConfirmando(true); }}
              disabled={enviando}
            >
              Solicitar eliminación de mi cuenta
            </button>
          )}
        </>
      )}

      <ConfirmarDialogo
        abierto={confirmando}
        titulo="¿Solicitar la eliminación de tu cuenta?"
        mensaje="Se registrará una solicitud pendiente de revisión. Tu cuenta, tus publicaciones y tus datos no se borran en este momento y podrás seguir usando FoundIA."
        textoConfirmar="Sí, enviar solicitud"
        peligro
        procesando={enviando}
        onConfirmar={enviar}
        onCancelar={() => setConfirmando(false)}
      />
    </section>
  );
}
