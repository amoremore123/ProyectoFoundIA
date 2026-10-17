import { useTema } from '../context/TemaContext';

// Logo de FoundIA (imágenes en /public).
// `claro`  = fuerza la versión con texto blanco (fondos oscuros o con degradado).
// `tamano` = alto del logo en píxeles (el ancho se ajusta solo).
// En modo oscuro se usa automáticamente la versión clara, porque las
// superficies claras (sidebar, tarjetas, login) pasan a fondo oscuro.
export default function Logo({ claro = false, tamano = 36 }) {
  const { tema } = useTema();
  const usarClaro = claro || tema === 'oscuro';

  return (
    <img
      src={usarClaro ? '/logo-claro.png' : '/logo.png'}
      alt="FoundIA"
      className="logo-img"
      style={{ height: tamano }}
    />
  );
}
