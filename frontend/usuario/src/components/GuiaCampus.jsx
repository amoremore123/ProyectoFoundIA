import { Link } from 'react-router-dom';
import { useAsistente } from '../context/AsistenteContext';
import Icono from './Icono';

const preguntas = [
  ['¿Necesito una cuenta para buscar?', 'Puedes consultar el historial y buscar objetos sin iniciar sesión. Para publicar, necesitas una cuenta verificada.'],
  ['¿Qué hago si perdí algo?', 'Revisa las publicaciones de objetos encontrados. Puedes buscar por nombre, categoría o ubicación. Si no aparece, publica tu objeto como perdido con la fecha y el último lugar donde lo viste.'],
  ['¿Qué hago si encontré un objeto?', 'Publica el objeto como encontrado e indica dónde y cuándo lo encontraste. Describe sus características sin mostrar datos personales de su dueño.'],
  ['¿La fotografía siempre corresponde al objeto?', 'Las fotos subidas por quien publica tienen prioridad. Cuando no hay una foto disponible, podemos mostrar una fotografía de referencia identificada como tal. Revisa siempre la descripción.'],
  ['¿Dónde consulto mis publicaciones?', 'Entra a tu cuenta y abre “Mis publicaciones”. Para consultar las publicaciones de toda la comunidad, utiliza el historial.'],
];

export default function GuiaCampus() {
  const { abrir } = useAsistente();
  return <>
    <section className="guia-campus" aria-labelledby="guia-titulo" id="como-funciona">
      <div className="inicio-seccion-cabecera"><span className="visual-eyebrow">POR DÓNDE EMPEZAR</span><h2 id="guia-titulo">De vuelta a tus manos, paso a paso</h2><p>Entre clases y talleres, perder algo puede pasar. Así puedes empezar a buscarlo.</p></div>
      <ol className="guia-pasos">
        <li><span className="guia-numero">01</span><h3>Revisa lo que encontraron</h3><p>Compara la descripción, la fecha y el lugar con lo que recuerdas de tu objeto.</p><Link to="/buscar?tipo=ENCONTRADO">Ver objetos encontrados <Icono nombre="flechaDer" tamano={14} className="icono-enlace" /></Link></li>
        <li><span className="guia-numero">02</span><h3>Cuéntanos qué perdiste</h3><p>Escribe como se lo contarías a un compañero. El asistente te ayuda a buscar publicaciones relacionadas.</p><button type="button" onClick={() => abrir()}>Abrir el asistente <Icono nombre="flechaDer" tamano={14} className="icono-enlace" /></button></li>
        <li><span className="guia-numero">03</span><h3>Deja tu publicación</h3><p>Si todavía no aparece, comparte sus características y dónde lo viste por última vez.</p><Link to="/publicar">Publicar un objeto perdido <Icono nombre="flechaDer" tamano={14} className="icono-enlace" /></Link></li>
      </ol>
    </section>

    <section className="consejos-campus" aria-labelledby="consejos-titulo">
      <div className="consejos-intro"><span className="visual-eyebrow">UNA BUENA DESCRIPCIÓN AYUDA</span><h2 id="consejos-titulo">Los detalles hacen la diferencia</h2><p>Una publicación clara ayuda a que otra persona reconozca lo que estás buscando.</p><Link to="/publicar" className="btn btn-secundario">Crear mi publicación</Link></div>
      <dl className="consejos-lista">
        <div><dt>Cómo es</dt><dd>Incluye el color, la marca y algún detalle particular: una funda, una etiqueta o un llavero.</dd></div>
        <div><dt>Dónde y cuándo</dt><dd>“Biblioteca, segundo piso, el martes por la tarde” ayuda más que “en el campus”.</dd></div>
        <div><dt>Una foto clara</dt><dd>Usa una foto del objeto si la tienes. Evita mostrar números de documentos o datos personales.</dd></div>
        <div><dt>Antes de una entrega</dt><dd>Comprueba un detalle que solo el dueño conozca y acuerda la entrega en un lugar público.</dd></div>
      </dl>
    </section>

    <section className="inicio-faq" aria-labelledby="faq-titulo" id="preguntas-frecuentes">
      <div className="inicio-seccion-cabecera"><span className="visual-eyebrow">RESOLVEMOS TUS DUDAS</span><h2 id="faq-titulo">Antes de seguir buscando</h2><p>Lo esencial para usar FoundIA en tu día a día en Tecsup.</p><Link to="/mis-publicaciones">Ir a mis publicaciones <Icono nombre="flechaDer" tamano={14} className="icono-enlace" /></Link></div>
      <div className="faq-lista">{preguntas.map(([pregunta, respuesta]) => <details className="faq-item" key={pregunta}><summary>{pregunta}</summary><p>{respuesta}</p></details>)}</div>
    </section>
  </>;
}
