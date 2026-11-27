# H38 - Servicio programado de respaldo lógico de MySQL.
# Se construye desde la raíz del repositorio para copiar scripts/respaldo.sh.
FROM mysql:8.0

RUN microdnf install -y gzip findutils tar \
    && microdnf clean all

COPY scripts/respaldo.sh /usr/local/bin/respaldo.sh
# H70: para atender las restauraciones pedidas desde el panel
COPY scripts/restaurar_version.sh scripts/restaurar_respaldo.sh scripts/restaurar_fotos.sh /usr/local/bin/
# El checkout de Windows puede traer CRLF; el shebang necesita LF.
RUN sed -i 's/\r$//' /usr/local/bin/respaldo.sh /usr/local/bin/restaurar_version.sh \
        /usr/local/bin/restaurar_respaldo.sh /usr/local/bin/restaurar_fotos.sh \
    && chmod 755 /usr/local/bin/respaldo.sh /usr/local/bin/restaurar_version.sh \
        /usr/local/bin/restaurar_respaldo.sh /usr/local/bin/restaurar_fotos.sh

ENTRYPOINT ["/usr/local/bin/respaldo.sh"]
