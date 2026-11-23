import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Cargando from '../components/Cargando';
import MensajeError from '../components/MensajeError';
import Icono from '../components/Icono';
import AvatarConversacion from '../components/AvatarConversacion';
import { getConversaciones, mensajeError } from '../services/api';
import { horaChat } from '../utils/chat';

// H55: mis conversaciones. Cada una pertenece a un objeto: entre quien perdió y quien encontró,
// o con la biblioteca por un reclamo en custodia. Nunca se muestran correos ni teléfonos.
export default function Mensajes() {
  const [lista, setLista] = useState(null);
  const [error, setError] = useState('');

  const cargar = useCallback(async () => {
    setError('');
    try {
      setLista(await getConversaciones());
    } catch (e) {
      setError(mensajeError(e, 'No se pudieron cargar tus conversaciones'));
    }
  }, []);

  useEffect(() => { cargar(); }, [cargar]);

  if (error) {
    return (
      <div className="pagina pagina-estrecha">
        <h1 className="pagina-titulo">Mensajes</h1>
        <MensajeError mensaje={error} onReintentar={cargar} />
      </div>
    );
  }
  if (!lista) return <Cargando texto="Cargando tus conversaciones..." />;

  return (
    <div className="pagina pagina-estrecha">
      <h1 className="pagina-titulo">Mensajes</h1>
      <p className="nota-suave conv-privacidad">
        <Icono nombre="escudo" tamano={14} /> Coordina la entrega sin compartir tu correo ni tu teléfono.
      </p>
      {lista.length === 0 ? (
        <div className="estado-vacio">
          <p>Todavía no tienes conversaciones.</p>
          <p className="nota-suave">
            Aparecen cuando contactas a quien encontró tu objeto o escribes a la biblioteca desde tu reclamo.
          </p>
        </div>
      ) : (
        <ul className="conv-lista" aria-label="Tus conversaciones">
          {lista.map((c) => {
            const ultimo = c.ultimoMensaje;
            const vistaPrevia = ultimo ? (ultimo.texto || (ultimo.imagenUrl ? 'Foto' : '')) : 'Sin mensajes todavía';
            return (
              <li key={c.id}>
                <Link to={`/mensajes/${c.id}`} className={`conv-lista-item${c.sinLeer > 0 ? ' conv-sin-leer' : ''}`}>
                  <AvatarConversacion conversacion={c} className="conv-lista-avatar" />
                  <span className="conv-lista-cuerpo">
                    <span className="conv-lista-fila">
                      <strong>{c.otro?.nombre || 'Conversación'}</strong>
                      {ultimo && <span className="conv-lista-hora">{horaChat(ultimo.enviadoEn)}</span>}
                    </span>
                    <span className="conv-lista-objeto">{c.objetoNombre}</span>
                    <span className="conv-lista-fila">
                      <span className="conv-lista-previa">{vistaPrevia}</span>
                      {c.sinLeer > 0 && <span className="conv-contador">{c.sinLeer} sin leer</span>}
                      {c.soloLectura && <span className="conv-etiqueta">Terminada</span>}
                    </span>
                  </span>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
