import { useId } from 'react';

// Logo de ENCUENTRA+: marca (lupa) + texto. `claro` = versión para fondos oscuros.
export function LogoMarca({ tamano = 34 }) {
  const id = `logo-grad-${useId().replace(/:/g, '')}`;
  return (
    <svg width={tamano} height={tamano} viewBox="0 0 40 40" aria-hidden="true" className="logo-marca">
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#1d4ed8" />
          <stop offset="1" stopColor="#0ea5e9" />
        </linearGradient>
      </defs>
      <rect width="40" height="40" rx="11" fill={`url(#${id})`} />
      <circle cx="18" cy="18" r="8" fill="none" stroke="#fff" strokeWidth="3.2" />
      <line x1="24" y1="24" x2="30.5" y2="30.5" stroke="#fff" strokeWidth="3.2" strokeLinecap="round" />
      <path d="M29 8.5v6M26 11.5h6" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" />
    </svg>
  );
}

export default function Logo({ claro = false, tamano = 34 }) {
  return (
    <span className={`logo-completo${claro ? ' logo-claro' : ''}`}>
      <LogoMarca tamano={tamano} />
      <span className="logo-texto">
        ENCUENTRA<span className="logo-mas">+</span>
      </span>
    </span>
  );
}
