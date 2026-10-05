import Logo from '../components/Logo';

// Pantallas de acceso (Login, Registro, Verificar): panel de marca + formulario
export default function AuthLayout({ children }) {
  return (
    <div className="auth-pantalla">
      <aside className="auth-panel" aria-hidden="true">
        <Logo claro tamano={40} />

        <div className="auth-panel-texto">
          <h2>
            Perdiste algo.
            <br />
            <span>Encuéntralo.</span>
          </h2>
          <p>La plataforma de objetos perdidos y encontrados de tu institución.</p>
        </div>

        <ul className="auth-beneficios">
          <li>
            <span className="auth-beneficio-icono">📢</span>
            Publica en segundos lo que perdiste o encontraste
          </li>
          <li>
            <span className="auth-beneficio-icono">🤝</span>
            Recibe coincidencias sugeridas automáticamente
          </li>
          <li>
            <span className="auth-beneficio-icono">🔒</span>
            Cuenta verificada y acceso seguro
          </li>
        </ul>

        <p className="auth-panel-pie">© 2026 Encuentra+ · Proyecto integrador Tecsup</p>
        <div className="auth-burbuja auth-burbuja-1" />
        <div className="auth-burbuja auth-burbuja-2" />
      </aside>

      <main className="auth-contenido">{children}</main>
    </div>
  );
}
