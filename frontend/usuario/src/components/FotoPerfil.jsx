import { useEffect, useRef, useState } from 'react';
import ConfirmarDialogo from './ConfirmarDialogo';
import Icono from './Icono';
import { getFotoPerfil, mensajeError, quitarFotoPerfil, subirFotoPerfil } from '../services/api';
import { reducirFoto } from '../utils/fotos';

// H64: foto de perfil privada. Solo la ve la propia persona (y, cuando exista el chat, quien
// conversa con ella); nunca aparece en las publicaciones. Sin foto se muestran las iniciales.
const TIPOS = ['image/jpeg', 'image/png'];
const MAXIMO_BYTES = 5 * 1024 * 1024;
const LADO_PERFIL = 1024; // una foto de perfil no necesita más: sube más rápido

export function validarFotoPerfil(archivo) {
  if (!archivo) return 'Elige una imagen.';
  if (!TIPOS.includes(archivo.type)) return 'La foto debe ser JPEG o PNG.';
  if (archivo.size > MAXIMO_BYTES) return 'La foto no debe superar los 5 MiB.';
  return '';
}

export default function FotoPerfil({ fotoPerfilUrl, iniciales }) {
  const [url, setUrl] = useState(fotoPerfilUrl || null);
  const [imagen, setImagen] = useState('');
  const [cargandoImagen, setCargandoImagen] = useState(Boolean(fotoPerfilUrl));
  const [subiendo, setSubiendo] = useState(false);
  const [quitando, setQuitando] = useState(false);
  const [confirmarQuitar, setConfirmarQuitar] = useState(false);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const entrada = useRef(null);

  // Descarga la imagen con la sesión y la muestra como blob; se libera al cambiarla o salir
  useEffect(() => {
    if (!url) { setImagen(''); setCargandoImagen(false); return undefined; }
    let vigente = true;
    let objeto = '';
    setCargandoImagen(true);
    getFotoPerfil(url)
      .then((blob) => {
        if (!vigente) return;
        if (blob) { objeto = URL.createObjectURL(blob); setImagen(objeto); } else setImagen('');
      })
      .catch(() => { if (vigente) setImagen(''); })
      .finally(() => { if (vigente) setCargandoImagen(false); });
    return () => { vigente = false; if (objeto) URL.revokeObjectURL(objeto); };
  }, [url]);

  const elegir = async (e) => {
    const original = e.target.files?.[0];
    e.target.value = ''; // permite volver a elegir el mismo archivo
    if (!original) return;
    setError('');
    setAviso('');
    if (!TIPOS.includes(original.type)) { setError('La foto debe ser JPEG o PNG.'); return; }
    setSubiendo(true);
    try {
      const archivo = await reducirFoto(original, LADO_PERFIL);
      const problema = validarFotoPerfil(archivo);
      if (problema) { setError(problema); return; }
      const data = await subirFotoPerfil(archivo);
      setUrl(data?.fotoPerfilUrl || '/api/perfil/foto');
      setAviso('Foto de perfil actualizada.');
    } catch (err) {
      setError(mensajeError(err, 'No se pudo subir la foto. Inténtalo de nuevo.'));
    } finally {
      setSubiendo(false);
    }
  };

  const quitar = async () => {
    setQuitando(true);
    setError('');
    setAviso('');
    try {
      await quitarFotoPerfil();
      setUrl(null);
      setAviso('Quitaste tu foto de perfil.');
    } catch (err) {
      setError(mensajeError(err, 'No se pudo quitar la foto.'));
    } finally {
      setQuitando(false);
      setConfirmarQuitar(false);
    }
  };

  const ocupado = subiendo || quitando;
  return (
    <div className="foto-perfil">
      <span className="perfil-avatar foto-perfil-avatar">
        {imagen
          ? <img src={imagen} alt="Tu foto de perfil" />
          : <span aria-hidden="true">{cargandoImagen ? '' : iniciales}</span>}
      </span>
      <div className="foto-perfil-acciones">
        <input
          ref={entrada} id="foto-perfil-archivo" type="file" accept="image/jpeg,image/png" className="foto-perfil-input"
          onChange={elegir} disabled={ocupado} aria-describedby="foto-perfil-ayuda"
        />
        <button type="button" className="btn-enlace" onClick={() => entrada.current?.click()} disabled={ocupado}>
          <Icono nombre="camara" tamano={14} /> {subiendo ? 'Subiendo foto…' : url ? 'Cambiar foto' : 'Subir foto'}
        </button>
        {url && (
          <button type="button" className="btn-enlace foto-perfil-quitar" onClick={() => setConfirmarQuitar(true)} disabled={ocupado}>
            Quitar foto
          </button>
        )}
      </div>
      <p id="foto-perfil-ayuda" className="nota-suave foto-perfil-ayuda">
        JPEG o PNG, máximo 5 MiB. Solo tú ves tu foto: no aparece en tus publicaciones.
      </p>
      {error && <p className="campo-error" role="alert">{error}</p>}
      {aviso && <p className="nota-suave" role="status">{aviso}</p>}
      <ConfirmarDialogo
        abierto={confirmarQuitar}
        titulo="¿Quitar tu foto de perfil?"
        mensaje="Se borrará la foto y volverán a mostrarse tus iniciales. Puedes subir otra cuando quieras."
        textoConfirmar="Quitar foto"
        peligro
        procesando={quitando}
        onConfirmar={quitar}
        onCancelar={() => setConfirmarQuitar(false)}
      />
    </div>
  );
}
