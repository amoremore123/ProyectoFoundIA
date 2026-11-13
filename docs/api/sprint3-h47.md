# H47 — Asistente con tarjetas y acciones (UI generativa controlada)

> Estado: API (Spring) y web implementadas y probadas. La capa Android (Amore) consume este contrato.

`POST /api/asistente/interpretar` añade dos campos **opcionales** a su respuesta. El resto del
contrato de H45 no cambia; si no hay nada que mostrar, los campos no aparecen.

Las tarjetas y acciones las arma el **servidor** con datos de la base y salen de un catálogo cerrado
(`AsistenteCatalogo`); el modelo de IA no las genera. **Ninguna acción se ejecuta sola.**

## Catálogo de tarjetas (`tarjetas`)

| `tipo` | Cuándo | `datos` | `enlace` |
| --- | --- | --- | --- |
| `OBJETO` | el mensaje nombra una publicación visible («#12», «publicación 12»; activa o en custodia) | `id`, `tipo`, `estado`, `ubicacion`, `categoria` (sin datos de quien publicó) | `/objeto/{id}` |
| `RECLAMOS` | con sesión, si se pregunta por reclamos, retiro o QR | `pendientes`, `aceptados`, `retirados`, `rechazados` | `/mis-publicaciones` |
| `QR_RETIRO` | con sesión, si hay QR de retiro vigentes | `vigentes` (el token nunca se envía) | `/mis-publicaciones` |

## Catálogo de acciones (`acciones`)

| `tipo` | Cuándo | `datos` | `destino` |
| --- | --- | --- | --- |
| `PRELLENAR_PUBLICAR` | intención `publicar` | `tipo` (PERDIDO/ENCONTRADO), `categoriaId`, `ubicacion` (solo los que se entendieron) | `/publicar` |
| `ABRIR_RECLAMO` | con sesión, objeto `ENCONTRADO` activo o en custodia que no es tuyo | `objetoId` | `/objeto/{id}` |

Cada acción trae `texto` (botón), `confirmacion` (pregunta que se muestra) y `requiereConfirmacion: true`.

```json
"acciones": [{
  "tipo": "PRELLENAR_PUBLICAR",
  "texto": "Abrir Publicar con estos datos",
  "confirmacion": "¿Abrir el formulario de Publicar con los datos que entendí? Podrás revisarlos y cambiarlos antes de publicar.",
  "destino": "/publicar",
  "datos": { "tipo": "PERDIDO", "categoriaId": 3, "ubicacion": "biblioteca" },
  "requiereConfirmacion": true
}]
```

## Reglas para los clientes (web y Android)

1. Dibujar solo tipos del catálogo; ignorar cualquier otro tipo o un destino distinto del esperado.
2. Pedir confirmación antes de abrir el destino. Cancelar no hace nada.
3. Al confirmar solo se **abre** la pantalla con los datos prellenados; publicar o reclamar sigue
   necesitando la confirmación propia de esa pantalla.

## Pruebas

- Spring: `AsistenteTarjetasTest` (5): prellenado con confirmación, tarjeta de objeto sin datos del autor, reclamo solo para objetos encontrados ajenos, ocultos e inexistentes no se muestran, tarjetas de reclamos y QR solo con sesión y como cifras.
- Web: `ChatTarjetas.test.jsx` (4): catálogo cerrado y prellenado validado, confirmación y Cancelar, tarjeta de objeto y reclamo, tarjetas de reclamos y QR.
