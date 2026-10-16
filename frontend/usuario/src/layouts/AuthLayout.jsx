import Logo from '../components/Logo';
import Icono from '../components/Icono';
import { useTema } from '../context/TemaContext';

function BotonTema() {
  const { tema, alternarTema } = useTema();
  const oscuro = tema === 'oscuro';
  return (
    <button
      type="button"
      className="icono-btn auth-tema"
      onClick={alternarTema}
      aria-label={oscuro ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
      title={oscuro ? 'Modo claro' : 'Modo oscuro'}
    >
      <Icono nombre={oscuro ? 'sol' : 'luna'} tamano={20} />
    </button>
  );
}

// Pantallas de acceso (Login, Registro, Verificar): panel de marca + formulario
export default function AuthLayout({ children }) {
  return (
    <div className="auth-pantalla">
      <BotonTema />
      <aside className="auth-panel" aria-hidden="true">
        <div className="auth-panel-marca">
          <Logo claro tamano={44} />
          <span className="auth-panel-sello">Tecsup</span>
        </div>

        <div className="auth-panel-texto">
          <span className="auth-panel-etiqueta">Objetos perdidos · Campus Tecsup</span>
          <h2>
            Perdiste algo.
            <br />
            <span>Encuéntralo.</span>
          </h2>
          <p>La plataforma de objetos perdidos y encontrados de la comunidad Tecsup.</p>
        </div>

        <ul className="auth-beneficios">
          <li>
            <span className="auth-beneficio-icono"><Icono nombre="megafono" tamano={18} /></span>
            Publica en segundos lo que perdiste o encontraste
          </li>
          <li>
            <span className="auth-beneficio-icono"><Icono nombre="chispa" tamano={18} /></span>
            Recibe coincidencias sugeridas automáticamente
          </li>
          <li>
            <span className="auth-beneficio-icono"><Icono nombre="escudo" tamano={18} /></span>
            Cuenta verificada y acceso seguro
          </li>
        </ul>

        <p className="auth-panel-pie">© 2026 FoundIA · Proyecto integrador Tecsup</p>
      </aside>

      <main className="auth-contenido">{children}</main>
    </div>
  );
}
