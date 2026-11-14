import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  CLAVE_CONSENTIMIENTO, CLAVE_TEMA, EVENTO_CONSENTIMIENTO, OPCIONES, guardarConsentimiento, leerConsentimiento,
} from '../utils/almacenamiento';
import { CLAVE_BORRADOR } from '../utils/borradorPublicar';

// Política de cookies y almacenamiento local: pública, se lee sin iniciar sesión.
// Lo que dice esta página debe coincidir con lo que la web guarda (ver utils/almacenamiento.js).
const GUARDADO = [
  { nombre: 'token', para: 'Mantener tu sesión iniciada en este navegador.', tipo: 'Esencial', dura: 'Hasta que cierres sesión o venza.' },
  { nombre: 'usuario', para: 'Mostrar tu nombre e iniciales mientras tienes la sesión abierta.', tipo: 'Esencial', dura: 'Hasta que cierres sesión.' },
  { nombre: CLAVE_CONSENTIMIENTO, para: 'Recordar la opción que elegiste en este aviso.', tipo: 'Esencial', dura: 'Hasta que la cambies o borres los datos del navegador.' },
  { nombre: CLAVE_BORRADOR, para: 'Recuperar lo que escribiste en Publicar si el navegador recarga la página (por ejemplo, al volver de la cámara).', tipo: 'Esencial', dura: 'Solo en esa pestaña; se borra al publicar o al cerrarla.' },
  { nombre: CLAVE_TEMA, para: 'Recordar si prefieres el modo claro u oscuro.', tipo: 'Preferencia', dura: 'Solo si eliges «Recordar mis preferencias».' },
];

const TERCEROS = [
  { quien: 'Google Fonts', cuando: 'Al abrir la web, para cargar la tipografía.', recibe: 'Tu dirección IP y datos técnicos del navegador.' },
  { quien: 'OpenStreetMap (Nominatim)', cuando: 'Solo si pulsas «Usar mi ubicación».', recibe: 'Las coordenadas para obtener el nombre del lugar.' },
  { quien: 'Reconocimiento de voz del navegador', cuando: 'Solo si usas el micrófono con FINDI y das permiso.', recibe: 'El audio que dictas, procesado por el proveedor de tu navegador.' },
  { quien: 'Groq (inteligencia artificial), a través de nuestro servidor', cuando: 'Solo si escribes a FINDI, pides una sugerencia al publicar o buscas con una foto.', recibe: 'El texto (sin correos ni teléfonos) o la foto reducida del objeto. No se guarda.' },
  { quien: 'ElevenLabs, a través de nuestro servidor', cuando: 'Solo si activas la lectura en voz alta de FINDI.', recibe: 'El texto de la respuesta de FINDI, sin tus datos de cuenta.' },
];

export default function Cookies() {
  const [eleccion, setEleccion] = useState(leerConsentimiento);

  useEffect(() => {
    const alCambiar = () => setEleccion(leerConsentimiento());
    window.addEventListener(EVENTO_CONSENTIMIENTO, alCambiar);
    return () => window.removeEventListener(EVENTO_CONSENTIMIENTO, alCambiar);
  }, []);

  const textoEleccion = eleccion === OPCIONES.TODO
    ? 'Recordamos tus preferencias.'
    : eleccion === OPCIONES.ESENCIAL
      ? 'Solo guardamos lo esencial.'
      : 'Todavía no elegiste una opción.';

  return (
    <div className="pagina legal-pagina cookies-pagina">
      <div className="seccion-header">
        <h1 className="pagina-titulo">Cookies y almacenamiento</h1>
      </div>

      <div className="legal-pagina-texto">
        <p>
          FoundIA <strong>no usa cookies propias</strong>, ni de publicidad ni de seguimiento, y no usa
          herramientas de analítica. Para funcionar guarda unos pocos datos en el almacenamiento de tu
          navegador; no salen de tu dispositivo salvo el token, que se envía a nuestro servidor para
          identificar tu sesión.
        </p>

        <h2>Qué guardamos en tu navegador</h2>
        <div className="cookies-tabla-envoltura">
          {/* Roles explícitos: en pantallas angostas las filas se apilan con CSS y no deben perder la semántica de tabla */}
          <table className="cookies-tabla" role="table" aria-label="Datos guardados en tu navegador">
            <thead role="rowgroup">
              <tr role="row"><th scope="col" role="columnheader">Dato</th><th scope="col" role="columnheader">Para qué</th><th scope="col" role="columnheader">Tipo</th><th scope="col" role="columnheader">Cuánto dura</th></tr>
            </thead>
            <tbody role="rowgroup">
              {GUARDADO.map((d) => (
                <tr key={d.nombre} role="row">
                  <th scope="row" role="rowheader"><code>{d.nombre}</code></th><td role="cell" data-etiqueta="Para qué">{d.para}</td><td role="cell" data-etiqueta="Tipo">{d.tipo}</td><td role="cell" data-etiqueta="Cuánto dura">{d.dura}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <h2>Servicios externos</h2>
        <ul className="cookies-terceros">
          {TERCEROS.map((t) => (
            <li key={t.quien}><strong>{t.quien}.</strong> {t.cuando} Recibe: {t.recibe}</li>
          ))}
        </ul>

        <h2>Tu elección</h2>
        <p role="status">{textoEleccion}</p>
        <p>
          Lo esencial no se puede desactivar porque sin ello no podrías iniciar sesión. Si eliges
          «Solo lo esencial», borramos tu preferencia de tema y la web usará la de tu sistema.
        </p>
        <div className="aviso-cookies-acciones">
          <button
            type="button"
            className="btn btn-secundario"
            aria-pressed={eleccion === OPCIONES.ESENCIAL}
            onClick={() => guardarConsentimiento(OPCIONES.ESENCIAL)}
          >
            Solo lo esencial
          </button>
          <button
            type="button"
            className="btn btn-primario"
            aria-pressed={eleccion === OPCIONES.TODO}
            onClick={() => guardarConsentimiento(OPCIONES.TODO)}
          >
            Recordar mis preferencias
          </button>
        </div>
        <p className="nota-suave">
          Texto del proyecto, sujeto a revisión institucional. Para borrar todo, cierra sesión o elimina los
          datos del sitio desde la configuración de tu navegador.
        </p>
      </div>

      <nav className="legal-pagina-enlaces" aria-label="Documentos legales">
        <Link to="/privacidad">Ver Política de privacidad</Link>
        <Link to="/terminos">Ver Términos de uso</Link>
        <Link to="/">Volver al inicio</Link>
      </nav>
    </div>
  );
}
