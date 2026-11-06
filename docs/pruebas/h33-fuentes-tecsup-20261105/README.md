# H33.1 — Verificación de fuentes oficiales

El `resumen.json` de esta carpeta registra solo las comprobaciones ejecutadas
sobre la integración nueva. No altera los resultados ni las huellas del cierre
anterior, ni afirma haber probado el respaldo compartido o un emulador aquí.

- `fuentes-comprobadas.json`: URLs oficiales, originales conservados, dos
  transcripciones y verificación de las siete páginas del PDF.
- `h33-http-real.json`: API y MySQL QA, rechazo sin consentimiento o con versión
  anterior, alta/versionado/huella correctos y consentimiento antiguo conservado.
- `h33-web-real.json` y `capturas/`: registro y documentos reales en navegador,
  fuentes externas, aceptación inicial desmarcada y verificación de la cuenta.
- `matriz-84-tareas.csv`: mismas descripciones y responsables; H33.1 cambia a
  fuentes oficiales integradas y verificadas, no a una aprobación inventada.
- `huellas.json`: SHA-256, normalizando textos CRLF a LF y sin alterar imágenes.

Los originales y sus huellas están en los recursos versionados del backend.
Los logs, tokens, secretos y SQL QA no se publican. Las pruebas usan solo datos
sintéticos en servicios aislados y nunca la base compartida.
