// Lleva a una sección de la página por su id. Al llegar desde otra página, la portada sigue
// cargando imágenes y cifras y la sección se mueve; por eso se corrige la posición un par de
// veces durante el primer segundo, solo si quedó lejos del borde superior.
const MARGEN_SUPERIOR = 96; // igual que scroll-margin-top de las secciones
const INTENTOS_MS = [0, 400, 1000];

export function desplazarASeccion(id) {
  INTENTOS_MS.forEach((ms, i) => {
    setTimeout(() => {
      const seccion = document.getElementById(id);
      if (!seccion) return;
      const distancia = Math.abs(seccion.getBoundingClientRect().top - MARGEN_SUPERIOR);
      if (i === 0 || distancia > 40) {
        seccion.scrollIntoView({ behavior: i === 0 ? 'smooth' : 'auto', block: 'start' });
      }
    }, ms);
  });
}
