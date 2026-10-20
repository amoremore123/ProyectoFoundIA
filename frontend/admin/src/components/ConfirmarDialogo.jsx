import { useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';

// Modal de confirmación accesible: el foco empieza en "Cancelar" (la opción segura),
// Escape o clic fuera cancelan, y Tab no sale del diálogo mientras está abierto.
// Se dibuja en <body> (portal) para que ninguna animación de la página lo recorte ni lo tape.
export default function ConfirmarDialogo({
  abierto, titulo, mensaje, textoConfirmar = 'Confirmar', peligro = false, procesando = false,
  onConfirmar, onCancelar,
}) {
  const cancelar = useRef(null);
  const caja = useRef(null);

  useEffect(() => {
    if (!abierto) return undefined;
    const anterior = document.activeElement;
    cancelar.current?.focus();
    return () => anterior?.focus?.();
  }, [abierto]);

  if (!abierto) return null;

  const teclas = (e) => {
    if (e.key === 'Escape' && !procesando) {
      e.stopPropagation();
      onCancelar();
    }
    if (e.key === 'Tab') {
      const botones = [...caja.current.querySelectorAll('button:not([disabled])')];
      if (botones.length === 0) return;
      const primero = botones[0];
      const ultimo = botones[botones.length - 1];
      if (e.shiftKey && document.activeElement === primero) { e.preventDefault(); ultimo.focus(); }
      else if (!e.shiftKey && document.activeElement === ultimo) { e.preventDefault(); primero.focus(); }
    }
  };

  return createPortal(
    <div className="dialogo-fondo" onMouseDown={(e) => { if (e.target === e.currentTarget && !procesando) onCancelar(); }}>
      <div
        ref={caja}
        className="dialogo tarjeta"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="dialogo-titulo"
        aria-describedby="dialogo-mensaje"
        onKeyDown={teclas}
      >
        <h2 id="dialogo-titulo" className="dialogo-titulo">{titulo}</h2>
        <p id="dialogo-mensaje" className="dialogo-mensaje">{mensaje}</p>
        <div className="dialogo-botones">
          <button ref={cancelar} type="button" className="btn btn-secundario" onClick={onCancelar} disabled={procesando}>
            Cancelar
          </button>
          <button
            type="button"
            className={`btn ${peligro ? 'btn-peligro' : 'btn-primario'}`}
            onClick={onConfirmar}
            disabled={procesando}
          >
            {procesando ? 'Procesando...' : textoConfirmar}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  );
}
