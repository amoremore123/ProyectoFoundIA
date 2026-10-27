# H21 — descarte: casos de aceptación de la API

Contrato: [endpoints](../api/endpoints.md). No acredita interfaces web/Android.

`DescartarCoincidenciaIntegrationTest` comprueba:

- Solo el titular del perdido descarta; anónimo, hallador, tercero y ADMIN ajeno no pueden.
- Clave perdido-encontrado o ID de pareja existente; claves inválidas no escriben datos.
- La pareja válida pasa a DESCARTADA sin cambiar objetos u otras parejas.
- Repetir conserva la fila, la puntuación histórica y la fecha; no duplica nada.
- No reaparece por GET en ningún sentido, ni por clave o ID; consultar no escribe.
- Objetos retirados, recuperados, misma cuenta, otra categoría o baja similitud se rechazan.
- Una aceptada no se descarta y una descartada no permite iniciar contacto.
- Dos descartes simultáneos y la carrera contacto/descarte conservan una sola pareja.

Las escrituras bloquean ambos objetos por ID ascendente y luego la pareja.
Se conservan contactos previos como antecedentes, sin exponer datos privados.

```powershell
# Desde backend/springboot; usa H2 temporal
mvn test "-Dtest=DescartarCoincidenciaIntegrationTest"
```

`DescartarCoincidenciaMySqlQa` reutiliza los casos en un servidor QA nuevo con
guarda, puerto no estándar y usuario restringido. Sin las variables de QA falla
de forma segura y no recurre a la base normal. Ver `scripts/pruebas-api-leonel.py`.

Los resultados de esta revisión se registran en la entrega para Amore. No usar
logs o cifras de revisiones anteriores como evidencia de la rama actual.
Nunca reimportar script.sql/seed.sql ni reiniciar volúmenes para reproducir pruebas.
