import { useEffect } from 'react';

const SELECTOR_USUARIO = [
  '.pagina > *',
  '.portada > section',
  '.historial-objetos',
  '.explorar-visual',
  '.guia-campus',
  '.consejos-campus',
  '.inicio-faq',
  '.ayuda-campus',
  '.grid-objetos > *',
  '.lista-mis-publicaciones > li',
  '.lista-notificaciones > li',
  '.lista-coincidencias > *',
].join(', ');

// Animación de aparición al hacer scroll, a prueba de fallos.
//
// Regla de oro: NUNCA ocultamos contenido que ya está a la vista. El
// observer solo marca `data-reveal` (oculta) a los bloques que están por
// debajo del pliegue, y les añade `.visible` al entrar en pantalla. Si el
// observer no disparase (p. ej. el doble montaje de StrictMode), no se
// ocultaría nada y el contenido seguiría siendo visible.
export default function useRevelarEnScroll(selector = SELECTOR_USUARIO) {
  useEffect(() => {
    if (typeof window === 'undefined' || !('IntersectionObserver' in window)) return undefined;
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return undefined;

    const contenedor = document.querySelector('.app-shell') || document.querySelector('.admin-shell') || document.body;

    const observador = new IntersectionObserver((entradas) => {
      const alto = window.innerHeight || document.documentElement.clientHeight;
      entradas.forEach((entrada) => {
        const el = entrada.target;
        if (entrada.isIntersecting) {
          el.classList.add('visible');
          observador.unobserve(el);
        } else if (!el.hasAttribute('data-reveal')) {
          // Solo ocultamos lo que está declaradamente por debajo del pliegue.
          if (el.getBoundingClientRect().top >= alto) el.setAttribute('data-reveal', '');
        }
      });
    }, { rootMargin: '0px 0px -6% 0px', threshold: 0.05 });

    const registrar = (nodo) => {
      if (!(nodo instanceof Element) || !nodo.matches(selector)) return;
      observador.observe(nodo);
    };

    const escanear = (raiz) => {
      registrar(raiz);
      raiz.querySelectorAll?.(selector).forEach(registrar);
    };

    escanear(contenedor);

    // Los bloques que cargan después (tarjetas, tablas) también se animan.
    const mutaciones = new MutationObserver((lista) => {
      lista.forEach((mutacion) => {
        mutacion.addedNodes.forEach((nodo) => escanear(nodo));
      });
    });
    mutaciones.observe(contenedor, { childList: true, subtree: true });

    return () => {
      observador.disconnect();
      mutaciones.disconnect();
    };
  }, [selector]);
}

