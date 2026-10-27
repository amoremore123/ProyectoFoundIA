# Rediseño de FoundIA Android

Entrega del 27 de octubre de 2026. Rama exclusiva: `mobile/android`. No se modificaron backend, web, MySQL ni ramas de otros integrantes. Los commits de este rediseño permanecen locales hasta recibir autorización de publicación.

## Referencia e identidad

El adjunto de esta tarea contiene texto; no incluye las dos capturas mencionadas. Se revisaron las pantallas reales de acceso e inicio de la web local, sus estilos y recursos. Se reutilizan los logos originales `logo.png`, `logo-claro.png`, `favicon.png` y `imagenes/tecsup-campus.png` del repositorio. La fotografía del campus forma parte del banner; nunca se usa como reemplazo de fotos de publicaciones.

Se creó una ilustración vectorial original en Compose (mochila, ficha, lupa y conexión), con variantes para Encuentra, Publica y Conecta. La identidad móvil combina azul profundo, azul eléctrico, celeste y acentos violetas; superficies, textos, bordes y mensajes tienen paletas claras y oscuras propias. Las imágenes están en recursos sin escalado por densidad.

## Experiencia implementada

- Bienvenida con logo, identificación Tecsup, ilustración y las dos acciones de entrada.
- Tres pasos introductorios con transiciones, indicadores, Siguiente, Omitir y Comenzar. La preferencia se escribe en almacenamiento local antes de navegar. Se conserva al reabrir; una sesión iniciada evita la introducción.
- Acceso con cabecera ilustrada, superficie independiente, campos con iconos, visibilidad de contraseña, tema, recuperación, registro y verificación; conserva JWT y errores reales. El formulario permite desplazamiento con el teclado.
- Inicio con saludo de perfil real, insignia de avisos reales, avatar de iniciales, campus, acciones según tipo, categorías del servidor, catálogo y posibles coincidencias solo cuando la cuenta tiene resultados. La consulta inicial de coincidencias se limita a tres publicaciones perdidas activas para evitar una petición por cada publicación de toda la cuenta; Mis publicaciones permite consultar las demás.
- Navegación con cinco destinos, iconos vectoriales, Publicar destacado, estado seleccionado, transiciones y restauración por pestaña.
- Búsqueda con texto, tipo, categoría y limpieza de filtros. La categoría se aplica en el ViewModel sobre el conjunto completo recibido de la consulta real; no se agregaron filtros ficticios. Estados vacíos, carga, errores y copia local explícita.
- Detalle con fotos reales (galería si hay varias), estado, categoría, datos, descripción, compartir, reporte y sugerencias del servidor. Si no existe foto se dice expresamente; no se muestran fotos de ejemplo como publicaciones.
- Publicación en seis pasos: tipo, información, foto opcional, lugar, calendario y revisión. Las validaciones se aplican por paso y antes de enviar; el servidor conserva sus validaciones finales. Confirmación visual después de la respuesta real.
- Selección de foto desde el proveedor del sistema, vista previa, validación fuera del hilo principal (JPEG/PNG, 5 MiB, 40 MP) y multipart al endpoint existente. Si se crea el objeto pero falla la foto, se conserva el ID durante ese formulario y el reintento no vuelve a crear el objeto. Se puede abrir la publicación sin la foto pendiente. No hay cola de operaciones sin conexión.
- Comparación con dos fotografías, porcentaje y criterios reales, solicitud de contacto, descarte y recuperación con confirmación. Los puntos de los criterios se muestran como puntos; no se convierten en porcentajes inventados.
- Avisos con tarjetas por tipo, fecha/hora, estado leído, acciones y contador que cambia después de la respuesta del servidor.
- Perfil con iniciales, datos, agrupaciones para actividad, edición, seguridad, avisos, privacidad, apariencia, eliminación y cierre de sesión. Edición y contraseña usan PUT /api/perfil; el correo y el rol no se alteran desde el cliente.
- Pantallas complementarias de verificación, recuperación, preferencias, reporte y eliminación utilizan la misma identidad y mantienen sus acciones existentes.

## Arquitectura y datos

Compose observa StateFlow con ciclo de vida. ViewModels manejan formularios, validación, carga y acciones. Repositorios coordinan API, Room y preferencias. No se añadieron llamadas de red ni DAO en las vistas. Room conserva su esquema v2 y migración 1→2; esta entrega no altera sus tablas.

Las únicas preferencias nuevas son introducción y tema; no contienen información privada y se leen/escriben en IO mediante un repositorio. La sesión sigue cifrada con Android Keystore. Los datos de perfil y avisos se mantienen en memoria y se descartan al cerrar sesión. El contador de notificaciones usa respuestas reales, sin incrementos simulados.

Las animaciones usan Compose y su escala de duración del sistema; no hay temporizadores decorativos infinitos. El contenido es desplazable para pantallas pequeñas y fuentes ampliadas; las etiquetas de acciones pueden crecer, y las tarjetas no fijan una altura para los textos.

## API utilizada

Se conservan las rutas existentes. Se conectaron desde Android las rutas que ya estaban en el servidor:

- POST `/api/objetos/{id}/fotos`: parte multipart `archivo`.
- PUT `/api/perfil`: nombre, apellido y contraseña opcional con la actual.
- POST `/api/contactos`: IDs del objeto perdido y encontrado.

No se añadieron endpoints, ni cambios al backend. H35 (recuperar/restablecer contraseña) conserva su cliente y sus mensajes de error; la disponibilidad depende de que la API del equipo implemente ese contrato. No se presenta una respuesta local como recuperación remota exitosa.

## Ejecutar y revisar

Abrir `movil/android` en Android Studio, seleccionar JDK 17 para Gradle, sincronizar y ejecutar. La versión de entrega es 1.2 (código 3). El APK normal utiliza API `http://10.0.2.2:8081/` y web `http://10.0.2.2:5173/` en el emulador. En un teléfono físico hay que compilar con los valores de `foundiaApiUrl` y `foundiaWebUrl` correspondientes a la PC o al servidor accesible por el teléfono.

El emulador QA y la API de verificación usan puertos separados (5560 / 18083 / MySQL 13370). Las capturas y resultados están en `qa/redisenio/`. Las fotografías del campus cargadas por la prueba de integración son archivos sintéticos **solo en esa base QA** y están identificadas en la descripción de prueba; no se agregaron al servidor compartido ni como fallback del producto.

El informe local general `REPORTE_MOVIL_FOUNDIA.md` sigue excluido del control de versiones.

## Validación y capturas

- 35 pruebas unitarias: 0 fallos. Incluyen persistencia de preferencias, consentimiento, errores HTTP, sesión, publicación parcial, validación por paso y contador de avisos.
- 6 pruebas Android: 0 fallos. Cuatro de Room, una de publicación/edición/eliminación/recuperación y una de fotografía/perfil/contacto contra API + MySQL QA reales.
- Recorrido de bienvenida, tres pasos, cierre y reapertura, acceso JWT real, perfil, tema oscuro persistente, búsqueda, detalle, comparación y avisos.
- Prueba visual adicional a 320 dp con fuente al 130%. En pantallas menores a 360 dp con fuentes ampliadas se muestra la etiqueta de la pestaña seleccionada; los cinco iconos mantienen descripciones accesibles. Se corrigió el salto de línea de Publicar y el desplazamiento hacia los errores al intentar avanzar en un formulario incompleto.
- Compilación de depuración y lint. Los resultados finales, versión, direcciones y hash del APK se registran en `qa/redisenio/resumen.json`.

| Pantalla | Evidencia |
| --- | --- |
| Bienvenida | [Captura](../qa/redisenio/01-bienvenida.png) |
| Acceso | [Captura](../qa/redisenio/04-acceso-claro.png) |
| Inicio claro | [Captura](../qa/redisenio/05-inicio-con-sesion.png) |
| Inicio oscuro | [Captura](../qa/redisenio/08-inicio-oscuro.png) |
| Perfil | [Captura](../qa/redisenio/06-perfil-claro.png) |
| Comparación | [Captura](../qa/redisenio/12-comparacion-real.png) |
| Pantalla pequeña | [Captura](../qa/redisenio/15-publicar-320dp.png) |

Las capturas son del emulador con cuentas sintéticas del seed de QA. La entrega normal no crea usuarios, publicaciones, avisos ni porcentajes de ejemplo.
