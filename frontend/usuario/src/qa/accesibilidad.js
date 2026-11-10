// Auditoría reproducible en un navegador real: ?auditar=1.
// Iniciar sesión normalmente. El informe solo contiene nodos y reglas axe.
import axe from 'axe-core';

document.documentElement.style.fontSize = '130%';
// Esperar la entrada de las pantallas y del tema evita medir colores a mitad
// de una transición de opacidad. No se deshabilitan reglas ni elementos.
const boton = document.createElement('button');
boton.textContent = 'Ejecutar auditoría H51';
boton.id = 'qa-auditar';
boton.style.cssText = 'position:fixed;left:8px;bottom:8px;z-index:99999;background:#fff;color:#111;padding:8px;font:14px sans-serif';
boton.addEventListener('click', async () => {
  boton.disabled = true;
  const anterior = document.getElementById('qa-informe');
  anterior?.remove();
  await document.fonts.ready;
  await new Promise(resolve => setTimeout(resolve, 1200));
  const r = await axe.run({ exclude: [['#qa-auditar']] }, {
    runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice'] },
  });
  const informe = {
    ruta: location.pathname, tema: document.documentElement.dataset.tema,
    ancho: innerWidth, texto: getComputedStyle(document.documentElement).fontSize,
    axe: axe.version, violaciones: r.violations.map(v => ({
      id: v.id, impacto: v.impact, nodos: v.nodes.map(n => ({ target: n.target, resumen: n.failureSummary })),
    })), incompletas: r.incomplete.map(v => ({ id: v.id, nodos: v.nodes.map(n => n.target) })),
    desbordamiento: document.documentElement.scrollWidth > innerWidth,
  };
  const salida = document.createElement('pre');
  salida.id = 'qa-informe';
  salida.style.cssText = 'position:relative;white-space:pre-wrap;background:white;color:black;padding:16px';
  salida.textContent = JSON.stringify(informe, null, 2);
  document.body.append(salida);
  boton.disabled = false;
});
document.body.append(boton);
