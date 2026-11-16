# Interfaz Android con FINDI

Cambios del 10/11/2026, tomando como referencia la propuesta móvil del usuario.
Paleta compartida con la web: marino `#14234B`, azul `#0866F5`, amarillo
`#FFD400` y naranja para notificaciones. Toda la interfaz usa la misma familia
SansSerif de `FoundIATypography`, con jerarquía mediante tamaños y pesos.

## Pantallas

- Bienvenida: personaje FINDI, acceso, creación de cuenta y entrada como invitado.
  La introducción se guarda antes de navegar, manteniendo las reglas existentes.
- Inicio: saludo con el nombre real disponible, notificaciones, perfil, escena
  de FINDI y cuatro accesos: Reportar, Buscar, Mis reportes y Notificaciones.
  Conserva categorías, coincidencias y publicaciones recientes.
- Asistente: saludo, cuatro accesos rápidos, burbujas de conversación, estado de
  búsqueda, resultados reales y reintento de errores. El campo admite 300
  caracteres. El dictado usa la actividad de reconocimiento disponible en el
  dispositivo; el texto se revisa antes de enviarlo. Si no hay servicio, se
  informa y se puede seguir escribiendo.
- Navegación inferior: Inicio, Reportes, FINDI, Buscar y Perfil. Los reportes
  privados siguen pidiendo iniciar sesión. Publicar permanece accesible desde
  la cuadrícula y desde el asistente. Al pulsar FINDI, la barra abre el chat
  sin restaurar una pantalla protegida visitada desde él.

## Arquitectura y API

`AsistenteScreen` observa `AsistenteViewModel`, que consulta
`AsistenteRepository`. `ApiAsistenteRepository` usa el contrato H45 integrado:
`POST /api/asistente/interpretar`, seguido de `GET /api/objetos/buscar` cuando
la intención es buscar. No inventa publicaciones, porcentajes ni recuperaciones.
El servidor decide la fuente local o IA. El asistente no crea ni elimina objetos.

La API se configura con la propiedad existente `foundiaApiUrl`; en el emulador
el valor por defecto es `http://10.0.2.2:8081/`. Debe estar disponible el backend
con H45 para consultar desde el chat. Cómo funciona es una respuesta local.
El dictado y sus permisos dependen del servicio instalado en el dispositivo.
No se implementó síntesis de voz remota como parte del rediseño.

Los recursos `findi_hero.webp` (900 × 952, 195 KB) y `findi_avatar.webp` se reutilizan de la identidad
web y se empaquetan localmente, sin descargarlos al abrir las pantallas.

## Validación

- `:app:assembleDebug`: APK generado correctamente.
- `:app:testDebugUnitTest`: 47 pruebas, sin errores ni fallos.
- `qa/verificar_arquitectura.py`: sin acceso directo a API/DAO desde vistas.
- Compilado con el JDK 21 instalado y el SDK Android 36. La JVM objetivo del
  proyecto permanece en Java 17.

APK de revisión: `output/android/FoundIA-FINDI-debug.apk` en la raíz del repo.
No se creó un commit ni se publicaron cambios remotos.

Revisión visual en el emulador Medium_Phone: bienvenida, inicio, chat y
redirección al acceso al intentar reportar como invitado. Las pruebas de red
del asistente utilizan MockWebServer; el dictado no se grabó en la revisión.
