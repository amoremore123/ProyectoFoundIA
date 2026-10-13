// Logo de FoundIA (imágenes en /public).
// `claro`  = versión con texto blanco, para fondos oscuros o con degradado.
// `tamano` = alto del logo en píxeles (el ancho se ajusta solo).
export default function Logo({ claro = false, tamano = 36 }) {
  return (
    <img
      src={claro ? '/logo-claro.png' : '/logo.png'}
      alt="FoundIA"
      className="logo-img"
      style={{ height: tamano }}
    />
  );
}
