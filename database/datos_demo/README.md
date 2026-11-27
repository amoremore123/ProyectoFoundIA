# Datos de demostración del QA

Scripts para dejar la base de QA lista para mostrar la web y la app. **Solo para la base de QA local** (Docker del
proyecto `foundia-amore-s3`); no los uses sobre datos reales. Antes de borrar, saca un respaldo:

```bash
docker exec foundia-amore-s3-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --no-tablespaces objetos_perdidos_db' > respaldo-antes.sql
```

| Script | Qué hace |
| --- | --- |
| `limpiar_datos_prueba.py` | Elige por **nombre** las publicaciones de prueba (las que dicen «prueba», «QA», «H44», «H52» o terminan en un código raro) y los usuarios `@qa.invalid`, y las borra con todo lo que depende de ellas (custodias, reclamos, chats, avisos, fotos). **Simula por defecto**; con `--aplicar` borra, dentro de una transacción |
| `cargar_datos_demo.py` | Crea por la API real dos recorridos con sentido: una mochila en custodia con reclamo aceptado, QR sin usar y chat con la biblioteca; y una casaca perdida/encontrada que coinciden, con chat entre dos personas. La contraseña de las cuentas de prueba va en `FOUNDIA_QA_PASSWORD` |

Las categorías de prueba se desactivan con `database/datos_m1_desactivar_categorias_qa.sql` (de Leonel, rama `dev/leonel`).
El nombre de las cuentas de prueba se cambió con `UPDATE usuarios` en cada base; no es parte del seed.
