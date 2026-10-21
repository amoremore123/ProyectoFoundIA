-- H20: persistir reclamos por publicación y cuenta; idempotencia por pareja.
-- Ejecutar una vez en bases existentes (la tabla puede estar ya creada).
CREATE TABLE IF NOT EXISTS reclamos (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    objeto_id      BIGINT      NOT NULL,
    usuario_id     BIGINT      NOT NULL,
    estado         VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha_reclamo  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_reclamos_objeto_usuario (objeto_id, usuario_id),
    CONSTRAINT fk_reclamos_objeto FOREIGN KEY (objeto_id)
        REFERENCES objetos (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_reclamos_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_reclamos_estado CHECK (estado IN ('PENDIENTE', 'ACEPTADO', 'RECHAZADO'))
) ENGINE = InnoDB;
