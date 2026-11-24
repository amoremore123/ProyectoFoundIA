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
  // Fase 5: preguntas por los propios reportes y por cómo funciona FoundIA (también sin IA)
  if (/\bmis (reportes|publicaciones|objetos)\b/.test(texto) || /\b(estado|como va|como van)\b.*\b(reporte|reportes|publicacion|publicaciones)\b/.test(texto)) {
    intencion = 'mis_reportes';
  } else if (/\bcomo (funciona|recupero|puedo recuperar|se recupera)\b/.test(texto) && tokens.length <= 8) {
    intencion = 'como_funciona';
  } else if (/\b(como|donde)\b.*\b(publico|publicar|reporto|reportar|subo|subir)\b/.test(texto) || /\bquiero (publicar|reportar)\b/.test(texto)) {
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

// Igual que AsistenteService.INTENCIONES en el backend (Fase 5)
const INTENCIONES = ['buscar', 'publicar', 'mis_reportes', 'como_funciona', 'ayuda', 'saludo', 'gracias', 'vacio'];
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

  // Una palabra que solo aparece en la descripción («mochila con llavero») no hace que el objeto sea
  // lo que se busca («llaves») si es de otra categoría: solo el nombre cuenta siempre.
  const otraCategoria = Boolean(consulta.categoria) && objeto.categoria?.id !== consulta.categoria.id;
  for (const p of consulta.palabras) {
    const formas = expandir(p);
    const esColor = consulta.colores.includes(p);
    const peso = esColor ? 2 : 3;
    maximo += peso + 2;
    let acierto = 0;
    let enNombre = false;
    if (formas.some((f) => contienePalabra(nombre, f))) { acierto = peso + 2; enNombre = true; }
    else if (formas.some((f) => contienePalabra(descripcion, f) || contienePalabra(categoria, f))) acierto = peso;
    puntos += acierto;
    if (acierto && !esColor && (enNombre || !otraCategoria)) coincideObjeto = true;
  }

  if (consulta.categoria) {
    maximo += 3;
    if (objeto.categoria?.id === consulta.categoria.id) {
      puntos += 3;
      // La categoría sola basta si el mensaje no nombra un objeto («algo de la categoría Celulares»).
      // Si lo nombra («llaves»), otra cosa de la misma categoría (una mochila en «Accesorio») no es
      // ese objeto: tiene que coincidir la palabra.
      if (!consulta.palabras.some((p) => !consulta.colores.includes(p))) coincideObjeto = true;
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
      texto: '¡Hola! 👋 Cuéntame qué perdiste o encontraste, y dónde lo viste por última vez.',
      sugerencias: SUGERENCIAS,
    };
  }
  if (consulta.intencion === 'gracias') {
    return { texto: '¡De nada! 💙 Aquí estaré si necesitas algo más.' };
  }
  if (consulta.intencion === 'ayuda') {
    return {
      texto: 'Te ayudo a buscar, reportar y recuperar objetos en Tecsup. Dime qué es, de qué color y dónde lo viste.',
      sugerencias: SUGERENCIAS,
    };
  }
  if (consulta.intencion === 'como_funciona') {
    return { ...responderAcceso('como-funciona') };
  }
  // "mis_reportes" necesita la sesión y la API: lo resuelve ChatAsistente con GET /api/perfil/objetos
  if (consulta.intencion === 'publicar') {
    return {
      texto: '¡Claro! 📦 En Publicar indicas qué es, dónde y cuándo. Te aviso si aparece una coincidencia.',
      acciones: [{ texto: 'Publicar un objeto', to: '/publicar' }],
    };
  }
  if (consulta.intencion === 'vacio') {
    return {
      texto: 'No te entendí bien 🤔 ¿Qué objeto es y dónde lo viste por última vez?',
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
    // Sin lugar, primero se pide el dato que más ayuda; con lugar, se ofrece registrar el reporte
    const siguiente = !consulta.ubicacion
      ? '¿Dónde lo viste por última vez?'
      : consulta.tipoBuscado === 'PERDIDO'
        ? '¿Lo publicamos como encontrado para que su dueño lo vea?'
        : '¿Quieres registrar un reporte?';
    return {
      texto: `Por ahora no encontré coincidencias${resumen ? ` con ${resumen}` : ''}. ${siguiente}`,
      acciones: [{ texto: 'Publicar ahora', to: '/publicar' }, verMas],
      consulta,
    };
  }

  const n = resultados.length;
  return {
    texto: notaTipo
      ? notaTipo.trim()
      : n === 1 ? '¡Encontré una posible coincidencia! 💙 ¿La revisamos?' : `¡Encontré ${n} posibles coincidencias! 💙 ¿Las revisamos?`,
    resultados,
    acciones: [verMas],
    consulta,
  };
}

export const SUGERENCIAS_INICIALES = SUGERENCIAS;

// Fase 4 - Accesos rápidos de FINDI. Son respuestas fijas: no consultan la IA ni inventan datos.
// No hay acceso a "personal responsable" porque ese flujo no existe (el contacto es entre usuarios).
export const ACCESOS_FINDI = [
  { id: 'buscar', texto: 'Buscar un objeto', icono: 'buscar' },
  { id: 'reportar', texto: 'Reportar un objeto', icono: 'caja' },
  { id: 'mis-reportes', texto: 'Mis reportes', icono: 'lista' },
  { id: 'ayuda', texto: 'Necesito ayuda', icono: 'info' },
];

export function responderAcceso(id, { conSesion = false } = {}) {
  switch (id) {
    case 'buscar':
      return {
        texto: '¡Vamos a buscarlo! 🔎 ¿Qué perdiste y dónde lo viste por última vez?',
        sugerencias: SUGERENCIAS.slice(0, 2),
        enfocar: true,
      };
    case 'reportar':
      return {
        texto: '¡Perfecto! 📦 Indica qué es, dónde y cuándo; si puedes, agrega una foto sin datos personales.',
        acciones: [{ texto: 'Ir a Reportar objeto', to: '/publicar' }],
      };
    case 'mis-reportes':
      return conSesion
        ? { texto: 'En Mis reportes ves el estado de tus publicaciones y puedes editarlas. 📋', acciones: [{ texto: 'Ver mis reportes', to: '/mis-publicaciones' }] }
        : { texto: 'Para ver tus reportes, primero inicia sesión. 🔐', acciones: [{ texto: 'Iniciar sesión', to: '/login' }] };
    case 'como-funciona':
      return {
        texto: 'Son cuatro pasos: reporta, te avisamos si hay coincidencia, verificas con un detalle que solo el dueño conoce y coordinan la entrega.',
        acciones: [{ texto: 'Ver el proceso completo', to: '/#proceso-recuperacion' }],
      };
    case 'ayuda':
      return {
        texto: 'Te ayudo a buscar, reportar y recuperar objetos en Tecsup 💙 Son cuatro pasos: reporta, te avisamos, verificas y coordinan la entrega.',
        acciones: [{ texto: 'Ver el proceso completo', to: '/#proceso-recuperacion' }],
        sugerencias: SUGERENCIAS.slice(0, 2),
      };
    default:
      return null;
  }
}

// H46: palabras del dominio de FoundIA para elegir la mejor alternativa del dictado por voz
export const VOCABULARIO = Array.from(new Set([...SINONIMOS.flat(), ...COLORES, ...LUGARES]));

// Saludo de FINDI al abrir el chat: según la hora y con el nombre si hay sesión.
// FINDI se presenta como «la asistente» de FoundIA (su voz es femenina).
export function saludoFindi(nombre = '', fecha = new Date()) {
  const hora = fecha.getHours();
  const momento = hora < 12 ? 'Buenos días' : hora < 19 ? 'Buenas tardes' : 'Buenas noches';
  const primerNombre = String(nombre || '').trim().split(/\s+/)[0];
  const inicio = primerNombre ? `¡${momento}, ${primerNombre}! 👋` : '¡Hola! 👋';
  return `${inicio} Soy FINDI. ¿Qué necesitas hoy?`;
}
