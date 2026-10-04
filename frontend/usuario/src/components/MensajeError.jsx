export default function MensajeError({ mensaje, onReintentar }) {
  if (!mensaje) return null;
  return (
    <div className="banner-error" role="alert">
      <span>⚠️ {mensaje}</span>
      {onReintentar && (
        <button type="button" className="btn-fantasma btn-reintentar" onClick={onReintentar}>
          Reintentar
        </button>
      )}
    </div>
  );
}
