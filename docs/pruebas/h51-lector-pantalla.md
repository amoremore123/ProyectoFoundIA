# H51 — Prueba con lector de pantalla real y texto ampliado del teléfono

Responsable: Yajaira · Rama: `frontend/web` · Duración: unos 20 minutos.

axe, Lighthouse y la medición manual de contraste ya están en
[`accesibilidad-h51.md`](accesibilidad-h51.md). Esta prueba cubre lo que no se puede medir
con herramientas: **si una persona que no ve la pantalla puede completar las tareas** solo
escuchando al lector.

## Antes de empezar

- Levantar la web y la API (Docker + `npm run dev -- --host` en `frontend/usuario`).
- Usar una cuenta de prueba de `database/seed.sql` (no tu cuenta personal).
- Elegir **un** lector (con uno basta; si hay tiempo, los dos):

| Lector | Dónde | Cómo activarlo | Gestos básicos |
| --- | --- | --- | --- |
| **TalkBack** (recomendado) | Celular Android + Chrome, en `http://<IP de la laptop>:5173` | Ajustes › Accesibilidad › TalkBack › Activar. Atajo: mantener **las dos teclas de volumen** 3 s | Deslizar → siguiente · Deslizar ← anterior · **Doble toque** = activar · Dos dedos = desplazar |
| **NVDA** | Laptop + Chrome, en `http://localhost:5173` | Instalarlo desde nvaccess.org (gratis). `Ctrl + Alt + N` lo inicia | `Tab` siguiente control · `H` siguiente título · `D` siguiente región · `Enter` activar · `Insert + Q` salir |

**Regla de la prueba:** con TalkBack, no mirar la pantalla mientras se hace cada tarea (bajar el
brillo al mínimo o voltear el celular). Solo se vale lo que se escucha.

## Tareas

Marca ✅ si se completa solo escuchando, ⚠️ si se completa pero algo se lee raro o confuso, y
❌ si no se puede completar. En «Qué se escuchó» anota la frase exacta cuando algo falle.

| # | Tarea | Qué debería pasar | Resultado | Qué se escuchó / problema |
| --- | --- | --- | --- | --- |
| 1 | Abrir la portada y saltar al contenido | Lo primero que se lee es «Saltar al contenido»; al activarlo, el foco va al contenido principal | | |
| 2 | Recorrer la portada por títulos | Los títulos se leen en orden y tienen sentido sin ver la página | | |
| 3 | Iniciar sesión y equivocarse a propósito | Cada campo dice su nombre («Correo electrónico», «Contraseña»); el error se anuncia solo, sin buscarlo | | |
| 4 | Buscar «mochila» en Buscar | Al buscar se anuncia cuántos resultados hay; cada tarjeta dice nombre, tipo y lugar | | |
| 5 | Abrir un objeto encontrado | Se lee la foto (texto alternativo), el nombre y los datos; los botones dicen qué hacen | | |
| 6 | Reclamar un objeto en custodia (o ver el estado de tu reclamo) | El formulario de verificación tiene etiqueta; tras enviar se anuncia el estado; el QR de retiro se lee como «QR de retiro de …» | | |
| 7 | Publicar un objeto (sin enviarlo) | Cada campo dice su nombre; los botones de foto/cámara se entienden; «Preparando la foto…» y los errores se anuncian | | |
| 8 | Hablar con FINDI | El botón para abrirlo se encuentra; las respuestas nuevas se leen solas; las tarjetas y sus acciones se entienden; «Cerrar asistente» cierra | | |
| 9 | Notificaciones | El enlace dice cuántas hay sin leer; los filtros dicen si están activos; «marcar todas» confirma | | |
| 10 | Perfil › Invitar a un amigo | «Crear enlace de invitación» funciona; al copiar se anuncia «Enlace copiado…» | | |
| 11 | Escanear una etiqueta de custodia (o abrir su enlace) | Se lee «Objeto en custodia en la biblioteca», el código y el nombre del objeto | | |

## Texto ampliado del teléfono (sin lector)

Celular › Ajustes › Pantalla › **Tamaño de fuente al máximo** (y, si existe, Zoom de pantalla
al máximo). Abrir la web y revisar:

| Pantalla | ¿Se puede leer todo? | ¿Algo se corta o se sale de la pantalla? | ¿Se puede usar sin desplazarse de lado? |
| --- | --- | --- | --- |
| Portada | | | |
| Buscar | | | |
| Detalle de un objeto | | | |
| Publicar | | | |
| Chat de FINDI abierto | | | |

## Resultado

**16/11/2026 — TalkBack en celular Android con Chrome:** Yajaira hizo el recorrido escuchando
al lector y la web se pudo usar sin problemas; no se registraron fallos que corregir. No se
anotó el detalle tarea por tarea.

Plantilla para repetir la prueba:

- Lector usado y versión: ______ · Celular / navegador: ______ · Fecha: ______
- Tareas completadas solo escuchando: __ / 11
- Problemas encontrados (cada uno con tarea, frase escuchada y qué se esperaba):
  1.
- Capturas o grabación: guardarlas en `docs/pruebas/sprint3-evidencias/` sin datos personales.

Si aparecen problemas, se corrigen en `frontend/web` con un commit por arreglo
(`H51: …`) y se repite solo la tarea afectada.

## Pre-revisión automática del 16/11/2026

Antes de la prueba manual se pasó axe (misma configuración de `?auditar=1`: 375 × 812, texto al
130 %) por las pantallas agregadas después de la última auditoría:

| Pantalla | Claro | Oscuro | Scroll horizontal |
| --- | --- | --- | --- |
| Perfil con «Invitar a un amigo» (H40) | 0 fallos | 0 fallos | No |
| Detalle con reclamo aceptado y QR de retiro (H42/H44) | 0 fallos | 0 fallos | No |
| Página de la etiqueta de custodia `/custodia/verificar` (H41) | 0 fallos | 0 fallos | No |

En oscuro, axe deja 4 contrastes como «incompletos» en cada pantalla (superficies translúcidas),
igual que en la auditoría anterior; no son fallos, pero tampoco se pueden dar por aprobados sin
medirlos a mano.
