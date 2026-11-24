import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import Icono from '../components/Icono';
import Cargando from '../components/Cargando';
import { avisarEtiqueta, getEtiquetaPublica, mensajeError, statusDe } from '../services/api';
import { useAuth } from '../context/AuthContext';

// Etiqueta QR preventiva: lo que ve quien escanea. Solo el nombre y la descripción que eligió el
// dueño; para escribirle hay que iniciar sesión (así no llegan mensajes anónimos ni spam).
export default function Etiqueta() {
  const { codigo } = useParams();
  const { user, token } = useAuth() || {};
  const conSesion = Boolean(token && user);
  const navigate = useNavigate();
  const [etiqueta, setEtiqueta] = useState(null);
  const [estado, setEstado] = useState('cargando'); // cargando | lista | invalida | error
  const [mensaje, setMensaje] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState('');

  const cargar = useCallback(async () => {
    setEstado('cargando');
    try {
      setEtiqueta(await getEtiquetaPublica(codigo));
      setEstado('lista');
    } catch (e) {
      setEstado(statusDe(e) === 404 ? 'invalida' : 'error');
    }
  }, [codigo]);
  useEffect(() => { cargar(); }, [cargar]);

  const avisar = async (e) => {
    e.preventDefault();
    setError('');
    if (!mensaje.trim()) { setError('Cuéntale dónde lo encontraste o dónde lo dejarás.'); return; }
    setEnviando(true);
    try {
      const conversacion = await avisarEtiqueta(codigo, mensaje.trim());
      navigate(`/mensajes/${conversacion.id}`);
    } catch (err) {
      setError(mensajeError(err, 'No se pudo enviar el aviso'));
      setEnviando(false);
    }
  };

  if (estado === 'cargando') return <Cargando texto="Leyendo la etiqueta..." />;
  if (estado !== 'lista') {
    return (
      <div className="pagina pagina-estrecha">
        <section className="tarjeta etiqueta-publica" aria-labelledby="etiqueta-titulo">
          <h1 id="etiqueta-titulo">{estado === 'invalida' ? 'Esta etiqueta ya no está activa' : 'No se pudo leer la etiqueta'}</h1>
          <p className="nota-suave">
            {estado === 'invalida'
              ? 'Su dueño la desactivó o el código no es de FoundIA. Si encontraste el objeto, entrégalo en la biblioteca de Tecsup.'
              : 'Revisa tu conexión e inténtalo de nuevo.'}
          </p>
          {estado === 'error' && <button type="button" className="btn btn-secundario" onClick={cargar}>Reintentar</button>}
        </section>
      </div>
    );
  }

  return (
    <div className="pagina pagina-estrecha">
      <section className="tarjeta etiqueta-publica" aria-labelledby="etiqueta-titulo">
        <p className="etiqueta-publica-sello"><Icono nombre="etiqueta" tamano={16} /> Etiqueta FoundIA</p>
        <h1 id="etiqueta-titulo">{etiqueta.nombre}</h1>
        {etiqueta.descripcion && <p className="etiqueta-publica-descripcion">{etiqueta.descripcion}</p>}

        {etiqueta.propia ? (
          <p role="status" className="aviso-accion aviso-ok">
            Esta etiqueta es tuya. Así la verá quien encuentre tu objeto. Puedes desactivarla en tu <Link to="/perfil">perfil</Link>.
          </p>
        ) : (
          <>
            <p>Esto le pertenece a alguien de la comunidad Tecsup. ¡Gracias por ayudar a que vuelva a su dueño!</p>
            {conSesion ? (
              <form className="etiqueta-aviso" onSubmit={avisar} noValidate>
                <div className="campo">
                  <label className="campo-label" htmlFor="etiqueta-mensaje">Mensaje para su dueño</label>
                  <textarea id="etiqueta-mensaje" className="input" rows={3} maxLength={1000} value={mensaje}
                    onChange={(e) => setMensaje(e.target.value)}
                    placeholder="Lo encontré en el laboratorio 3. Lo dejaré en la biblioteca." />
                </div>
                {error && <p role="alert" className="campo-error">{error}</p>}
                <button type="submit" className="btn btn-primario" disabled={enviando}>
                  <Icono nombre="chat" tamano={16} /> {enviando ? 'Enviando...' : 'Avisar a su dueño'}
                </button>
                <p className="nota-suave">Se abrirá un chat de FoundIA con su dueño. Ninguno ve el correo ni el teléfono del otro.</p>
              </form>
            ) : (
              <div className="etiqueta-aviso">
                <Link className="btn btn-primario" to="/login" state={{ volverA: `/etiqueta/${codigo}` }}>Inicia sesión para avisar a su dueño</Link>
                <p className="nota-suave">¿No tienes cuenta en FoundIA? Entrégalo en la biblioteca de Tecsup y ellos lo guardarán.</p>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}
