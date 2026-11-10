import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.jsx';
import './index.css';
import './styles/portada.css';
import './styles/objetos.css';
import './styles/campus.css';
import './styles/interaccion.css';
import './styles/rediseno.css';
import './styles/cuenta.css';
import './styles/foundia.css';
import './styles/experiencia.css';
import './styles/hero-findi.css';

// Herramienta H51 optativa, excluida de producción; no contiene credenciales.
if (import.meta.env.DEV && new URLSearchParams(location.search).has('auditar')) {
  import('./qa/accesibilidad.js');
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
