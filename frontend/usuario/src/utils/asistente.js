// Motor del asistente de búsqueda de FoundIA.
// Interpreta mensajes en lenguaje natural ("perdí mi celular azul en la biblioteca"),
// detecta intención, tipo, categoría, ubicación y palabras clave, y ordena los
// objetos publicados por relevancia. Funciona 100 % en el navegador sobre los datos
// de la API. Si el backend tiene Claude configurado, ChatAsistente le pasa a
// `responder` su interpretación (ver `adaptarInterpretacion`); si no, usa `interpretar`.

export function normalizar(texto = '') {
  return String(texto)
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9ñ\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

// Singular aproximado: "llaves" -> "llave", "celulares" -> "celular", "relojes" -> "reloj"
export function raiz(palabra) {
  if (palabra.length > 5 && /[lrndjzy]es$/.test(palabra)) return palabra.slice(0, -2);
  if (palabra.length > 3 && palabra.endsWith('s')) return palabra.slice(0, -1);
  return palabra;
}

const STOP = new Set(
  ('a al algo alguien alguna alguno ante con contra de del desde donde e el ella ellos en entre era es esa ese eso esta este esto estoy fue ha hay he la las le lo los me mi mis muy no nos o para pero por que se si sin sobre su sus te tengo tiene tu tus un una uno unos unas y ya yo hoy ayer manana tarde noche dia creo quiza tal vez cual cuando como porfa porfavor favor gracias hola buenas buenos dias por ahi aqui alli alla puedes podrias ayudame ayuda ayudar quiero queria necesito saber ver hace rato semana pasada')
    .split(' ')
);

const VERBOS_PERDI = ['perdi', 'perdido', 'perdida', 'extravie', 'extraviado', 'extraviada', 'olvide', 'olvidado', 'deje', 'cayo', 'robaron', 'busco', 'buscando', 'buscar', 'encuentro', 'falta'];
const VERBOS_ENCONTRE = ['encontre', 'halle', 'recogi', 'vi', 'encontrado', 'encontrada'];
const SALUDOS = ['hola', 'buenas', 'buenos', 'hey', 'saludos', 'que tal'];
const GRACIAS = ['gracias', 'genial', 'perfecto', 'excelente', 'listo'];

// Sinónimos frecuentes en el campus (todas las formas en singular y sin tildes)
const SINONIMOS = [
  ['celular', 'telefono', 'movil', 'smartphone', 'iphone', 'samsung', 'xiaomi', 'motorola', 'cel'],
  ['laptop', 'computadora', 'portatil', 'notebook', 'pc', 'macbook', 'lenovo', 'hp'],
  ['mochila', 'bolso', 'morral', 'maleta'],
  ['documento', 'dni', 'carne', 'carnet', 'cedula', 'tarjeta', 'credencial', 'pasaporte', 'fotocheck'],
  ['llave', 'llavero'],
  ['gafa', 'lente', 'anteojo'],
  ['audifono', 'auricular', 'airpod', 'headset', 'audifon'],
  ['billetera', 'cartera', 'monedero'],
  ['casaca', 'chaqueta', 'polera', 'chompa', 'sueter', 'abrigo', 'chaleco'],
  ['botella', 'tomatodo', 'termo', 'tomatodos'],
  ['reloj', 'smartwatch'],
  ['cuaderno', 'libreta', 'libro', 'apunte'],
  ['cargador', 'cable', 'adaptador'],
  ['calculadora', 'casio'],
  ['paraguas', 'sombrilla'],
];

const COLORES = ['negro', 'negra', 'blanco', 'blanca', 'azul', 'rojo', 'roja', 'verde', 'amarillo', 'amarilla', 'gris', 'rosado', 'rosada', 'morado', 'morada', 'marron', 'cafe', 'plateado', 'plateada', 'dorado', 'dorada', 'celeste', 'naranja', 'beige'];

const LUGARES = ['aula', 'salon', 'laboratorio', 'lab', 'biblioteca', 'comedor', 'cafeteria', 'entrada', 'puerta', 'pabellon', 'patio', 'estacionamiento', 'auditorio', 'bano', 'banos', 'gimnasio', 'losa', 'taller', 'pasillo', 'escalera', 'oficina', 'secretaria', 'kiosko', 'cancha', 'jardin', 'piso', 'edificio', 'sotano', 'paradero', 'bus'];

function expandir(palabra) {
  const r = raiz(palabra);
  const grupo = SINONIMOS.find((g) => g.includes(r) || g.includes(palabra));
  return grupo ? Array.from(new Set([r, ...grupo])) : [r];
}

function contienePalabra(textoNorm, palabra) {
  if (!palabra) return false;
  return textoNorm.split(' ').some((t) => {
    const rt = raiz(t);
    return rt === palabra || t === palabra || (palabra.length >= 4 && rt.startsWith(palabra));
  });
}

function extraerUbicacion(texto) {
  const m = texto.match(
    /\b(?:en|cerca de|cerca del|por|dentro de|dentro del|afuera de)\s+(?:el |la |los |las |un |una |mi )?([a-z0-9ñ ]{2,40}?)(?=$| ayer| hoy| esta | este | cuando| mientras| y | que | el dia| la semana| durante)/
  );
  if (!m) return '';
  const candidato = m[1].trim();
  const tokens = candidato.split(' ');
  // Solo es ubicación si menciona un lugar conocido o un número de aula
  if (tokens.some((t) => LUGARES.includes(raiz(t)) || LUGARES.includes(t) || /^\d{2,4}[a-z]?$/.test(t))) {
    return candidato;
  }
  return '';
}

export function interpretar(mensaje, categorias = []) {
  const texto = normalizar(mensaje);
  const tokens = texto.split(' ').filter(Boolean);

  const tiene = (lista) => lista.some((v) => (v.includes(' ') ? texto.includes(v) : tokens.includes(v)));

  let intencion = 'buscar';
  if (/\b(como|donde)\b.*\b(publico|publicar|reporto|reportar|subo|subir)\b/.test(texto) || /\bquiero (publicar|reportar)\b/.test(texto)) {
    intencion = 'publicar';
  } else if (/\b(que puedes|que haces|como funciona|ayuda|quien eres)\b/.test(texto) && tokens.length <= 6) {
    intencion = 'ayuda';
  }

  // Si yo perdí algo, busco entre lo ENCONTRADO; si encontré algo, busco entre lo PERDIDO
  let tipoBuscado = '';
  if (tiene(VERBOS_ENCONTRE) && !tiene(['perdi', 'extravie', 'olvide'])) tipoBuscado = 'PERDIDO';
  else if (tiene(VERBOS_PERDI)) tipoBuscado = 'ENCONTRADO';

  const ubicacion = extraerUbicacion(texto);
  const tokensUbicacion = new Set(ubicacion.split(' ').filter(Boolean));

  const ignorar = new Set([...STOP, ...VERBOS_PERDI, ...VERBOS_ENCONTRE, ...SALUDOS, ...GRACIAS, 'objeto', 'cosa', 'cosas', 'perdidos', 'encontrados', 'campus', 'tecsup']);
  const palabras = Array.from(
    new Set(tokens.filter((t) => t.length > 1 && !ignorar.has(t) && !tokensUbicacion.has(t)).map(raiz))
  );

  const colores = palabras.filter((p) => COLORES.includes(p) || COLORES.includes(`${p}s`));

  let categoria = null;
  for (const c of categorias) {
    if (c.estado === false) continue;
    const vocab = normalizar(`${c.nombre} ${c.descripcion || ''}`).split(' ').map(raiz);
    const coincide = palabras.some((p) => expandir(p).some((s) => vocab.includes(s)));
    if (coincide) {
      categoria = c;
      break;
    }
  }

  if (intencion === 'buscar' && palabras.length === 0 && !categoria && !ubicacion) {
    if (tiene(GRACIAS)) intencion = 'gracias';
    else if (tiene(SALUDOS)) intencion = 'saludo';
    else intencion = 'vacio';
  }

  return { intencion, tipoBuscado, categoria, ubicacion, palabras, colores };
}

const INTENCIONES = ['buscar', 'publicar', 'ayuda', 'saludo', 'gracias', 'vacio'];
const TIPOS = ['', 'PERDIDO', 'ENCONTRADO'];

// Convierte la interpretación del backend (Claude) a la misma forma que `interpretar()`,
// usando las categorías locales. Devuelve null si no tiene la forma esperada,
// para que quien la use vuelva al motor local.
export function adaptarInterpretacion(ia, categorias = []) {
  if (!ia || typeof ia !== 'object' || !INTENCIONES.includes(ia.intencion) || !Array.isArray(ia.palabras)) {
    return null;
  }
  const textos = (lista) => (Array.isArray(lista) ? lista : [])
    .filter((t) => typeof t === 'string')
    .flatMap((t) => normalizar(t).split(' '))
    .filter((t) => t.length > 1)
    .map(raiz);

  const coloresIA = textos(ia.colores);
  const palabras = Array.from(new Set([...textos(ia.palabras), ...coloresIA]));
  const colores = palabras.filter((p) => COLORES.includes(p) || COLORES.includes(`${p}s`) || coloresIA.includes(p));
  const idCategoria = ia.categoria?.id;
  const categoria = idCategoria == null
    ? null
    : categorias.find((c) => c.id === idCategoria && c.estado !== false) || null;
  const ubicacion = typeof ia.ubicacion === 'string' ? normalizar(ia.ubicacion) : '';
  const tipoBuscado = TIPOS.includes(ia.tipoBuscado) ? ia.tipoBuscado : '';

  let intencion = ia.intencion;
  if (intencion === 'buscar' && palabras.length === 0 && !categoria && !ubicacion) intencion = 'vacio';

  return { intencion, tipoBuscado, categoria, ubicacion, palabras, colores };
}

export function puntuar(objeto, consulta) {
  const nombre = normalizar(objeto.nombre);
  const descripcion = normalizar(objeto.descripcion);
  const ubicacion = normalizar(objeto.ubicacion);
  const categoria = normalizar(objeto.categoria?.nombre);

  let puntos = 0;
  let maximo = 0;
  // ¿Coincide el objeto en sí (no solo el color o el lugar)?
  let coincideObjeto = false;

  for (const p of consulta.palabras) {
    const formas = expandir(p);
    const esColor = consulta.colores.includes(p);
    const peso = esColor ? 2 : 3;
    maximo += peso + 2;
    let acierto = 0;
    if (formas.some((f) => contienePalabra(nombre, f))) acierto = peso + 2;
    else if (formas.some((f) => contienePalabra(descripcion, f) || contienePalabra(categoria, f))) acierto = peso;
    puntos += acierto;
    if (acierto && !esColor) coincideObjeto = true;
  }

  if (consulta.categoria) {
    maximo += 3;
    if (objeto.categoria?.id === consulta.categoria.id) {
      puntos += 3;
      coincideObjeto = true;
    }
  }

  if (consulta.ubicacion) {
    const partes = consulta.ubicacion.split(' ').filter((t) => !STOP.has(t));
    maximo += 3;
    if (partes.length && partes.every((t) => contienePalabra(ubicacion, raiz(t)))) puntos += 3;
    else if (partes.some((t) => contienePalabra(ubicacion, raiz(t)))) puntos += 1.5;
  }

  if (consulta.tipoBuscado) {
    maximo += 1;
    if (objeto.tipo === consulta.tipoBuscado) puntos += 1;
  }

  const porcentaje = maximo ? Math.round((puntos / maximo) * 100) : 0;
  return { puntos, porcentaje, coincideObjeto };
}

export function rankear(objetos, consulta, limite = 5) {
  // Si el mensaje nombra un objeto ("celular", "mochila"...), no basta con coincidir en color o lugar
  const nombraObjeto = Boolean(consulta.categoria) || consulta.palabras.some((p) => !consulta.colores.includes(p));
  return objetos
    .filter((o) => !o.estado || o.estado === 'ACTIVO')
    .map((o) => ({ objeto: o, ...puntuar(o, consulta) }))
    .filter((r) => r.puntos > 0 && r.porcentaje >= 25 && (!nombraObjeto || r.coincideObjeto))
    .sort((a, b) => b.puntos - a.puntos || String(b.objeto.fechaObjeto).localeCompare(String(a.objeto.fechaObjeto)))
    .slice(0, limite);
}

const SUGERENCIAS = ['Perdí mi celular en la biblioteca', 'Busco una mochila negra', 'Encontré unas llaves', '¿Cómo publico un objeto?'];

function describirConsulta(c) {
  const partes = [];
  if (c.palabras.length) partes.push(`“${c.palabras.join(' ')}”`);
  if (c.categoria) partes.push(`categoría ${c.categoria.nombre}`);
  if (c.ubicacion) partes.push(`cerca de ${c.ubicacion}`);
  return partes.join(', ');
}

// Construye la respuesta del asistente a partir del mensaje y los datos disponibles.
// Si llega `consulta` (p. ej. la interpretación de Claude) se usa en lugar de `interpretar()`.
export function responder(mensaje, { objetos = [], categorias = [], consulta: dada = null } = {}) {
  const consulta = dada || interpretar(mensaje, categorias);

  if (consulta.intencion === 'saludo') {
    return {
      texto: '¡Hola! Soy el asistente de FoundIA. Cuéntame qué perdiste o encontraste, y si recuerdas dónde, mejor. Por ejemplo: “perdí mi casaca azul en el aula 204”.',
      sugerencias: SUGERENCIAS,
    };
  }
  if (consulta.intencion === 'gracias') {
    return { texto: '¡Con gusto! Si necesitas buscar otra cosa, aquí estoy. Ojalá recuperes lo tuyo pronto.' };
  }
  if (consulta.intencion === 'ayuda') {
    return {
      texto: 'Busco entre todos los objetos publicados en el campus. Entiendo lenguaje natural: dime qué es, de qué color y dónde lo perdiste o encontraste, y te muestro los más parecidos ordenados por coincidencia.',
      sugerencias: SUGERENCIAS,
    };
  }
  if (consulta.intencion === 'publicar') {
    return {
      texto: 'Publicar es rápido: entra a “Publicar”, elige si lo perdiste o lo encontraste, describe el objeto, la categoría, el lugar y la fecha. FoundIA te avisará si aparece una coincidencia.',
      acciones: [{ texto: 'Publicar un objeto', to: '/publicar' }],
    };
  }
  if (consulta.intencion === 'vacio') {
    return {
      texto: 'Necesito un poco más de detalle. ¿Qué objeto es? Puedes decirme el tipo, color, marca o el lugar donde lo viste por última vez.',
      sugerencias: SUGERENCIAS,
    };
  }

  let resultados = rankear(objetos, consulta);
  let notaTipo = '';
  if (consulta.tipoBuscado) {
    const delTipo = resultados.filter((r) => r.objeto.tipo === consulta.tipoBuscado);
    if (delTipo.length) resultados = delTipo;
    else if (resultados.length) {
      notaTipo = consulta.tipoBuscado === 'ENCONTRADO'
        ? ' Nadie lo ha reportado como encontrado aún, pero hay publicaciones parecidas:'
        : ' No hay reportes de pérdida que coincidan exactamente, pero mira estas publicaciones:';
    }
  }

  const resumen = describirConsulta(consulta);
  const paramsBusqueda = new URLSearchParams();
  if (consulta.palabras.length) paramsBusqueda.set('q', consulta.palabras[0]);
  if (consulta.categoria) paramsBusqueda.set('categoriaId', String(consulta.categoria.id));
  const verMas = { texto: 'Ver búsqueda completa', to: `/buscar?${paramsBusqueda.toString()}` };

  if (!resultados.length) {
    const publicar = consulta.tipoBuscado === 'PERDIDO'
      ? 'Publícalo como encontrado para que su dueño lo vea.'
      : 'Te recomiendo publicarlo como perdido: te avisaremos cuando alguien lo encuentre.';
    return {
      texto: `No encontré publicaciones que coincidan con ${resumen || 'tu descripción'}. ${publicar}`,
      acciones: [{ texto: 'Publicar ahora', to: '/publicar' }, verMas],
      consulta,
    };
  }

  const n = resultados.length;
  return {
    texto: notaTipo
      ? `Busqué ${resumen}.${notaTipo}`
      : `Encontré ${n} ${n === 1 ? 'publicación que podría ser tuya' : 'publicaciones que podrían coincidir'} con ${resumen}:`,
    resultados,
    acciones: [verMas],
    consulta,
  };
}

export const SUGERENCIAS_INICIALES = SUGERENCIAS;
