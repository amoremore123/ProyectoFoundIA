# Datos de demostración (Android)

`cargar_datos_demo.py` crea por la API real dos recorridos con sentido para mostrar la app: una mochila en custodia con
reclamo aceptado, QR sin usar y chat con la biblioteca; y una casaca perdida/encontrada que coinciden, con chat entre dos
personas. **Solo para una base de QA**, nunca contra datos reales.

```bash
FOUNDIA_QA_PASSWORD=<contraseña de las cuentas del seed> \
FOUNDIA_SPRING_URL=http://127.0.0.1:8081 FOUNDIA_DJANGO_URL=http://127.0.0.1:8000 \
python database/datos_demo/cargar_datos_demo.py
```

Si no se definen las URLs usa los puertos normales del proyecto (8081 y 8000). No depende de contenedores ni de carpetas
de ningún equipo.

## Limpiar datos de prueba

Ya no hay script de limpieza aquí: se **retiró** porque repetía el de Yajaira. Se usa `database/limpieza_datos_prueba.sql`
(rama `frontend/web`): muestra qué borraría y solo aplica si se confirma. Para sacar un respaldo antes:
`docker compose exec mysql mysqldump -uroot -p objetos_perdidos_db > respaldo-antes.sql`.

Las categorías de prueba se desactivan con `database/datos_m1_desactivar_categorias_qa.sql` (de Leonel). El script de
demostración del Sprint 3 con fotos es `scripts/cargar_datos_demo.py` (de Leonel); este es complementario.
