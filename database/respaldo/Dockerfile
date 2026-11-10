# H38 - Servicio programado de respaldo lógico de MySQL.
# Se construye desde la raíz del repositorio para copiar scripts/respaldo.sh.
FROM mysql:8.0

RUN microdnf install -y gzip findutils \
    && microdnf clean all

COPY scripts/respaldo.sh /usr/local/bin/respaldo.sh
# El checkout de Windows puede traer CRLF; el shebang necesita LF.
RUN sed -i 's/\r$//' /usr/local/bin/respaldo.sh \
    && chmod 755 /usr/local/bin/respaldo.sh

ENTRYPOINT ["/usr/local/bin/respaldo.sh"]
