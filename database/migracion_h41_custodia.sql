-- H41: estado EN_CUSTODIA, código único y recepción con QR firmado.
-- Idempotente: se puede ejecutar sobre una base del Sprint 2 sin borrar datos.
USE objetos_perdidos_db;

ALTER TABLE objetos MODIFY estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO';

SET @existe_check := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'objetos'
      AND CONSTRAINT_NAME = 'chk_objetos_estado'
);
SET @sql := IF(@existe_check > 0,
    'ALTER TABLE objetos DROP CHECK chk_objetos_estado',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE objetos
    ADD CONSTRAINT chk_objetos_estado
    CHECK (estado IN ('ACTIVO', 'EN_CUSTODIA', 'RECUPERADO', 'OCULTO', 'ELIMINADO'));

CREATE TABLE IF NOT EXISTS secuencias_custodia (
    anio   INT NOT NULL PRIMARY KEY,
    ultimo INT NOT NULL DEFAULT 0
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS custodias (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    objeto_id        BIGINT       NOT NULL,
    codigo           VARCHAR(20)  NOT NULL,
    token_qr         VARCHAR(500) NOT NULL,
    recibido_por_id  BIGINT       NOT NULL,
    fecha_recepcion  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observaciones    TEXT         NULL,
    estado           VARCHAR(20)  NOT NULL DEFAULT 'EN_CUSTODIA',
    PRIMARY KEY (id),
    UNIQUE KEY uq_custodias_objeto (objeto_id),
    UNIQUE KEY uq_custodias_codigo (codigo),
    CONSTRAINT fk_custodias_objeto FOREIGN KEY (objeto_id)
        REFERENCES objetos (id) ON DELETE RESTRICT,
    CONSTRAINT fk_custodias_recibido_por FOREIGN KEY (recibido_por_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT chk_custodias_estado CHECK (estado IN ('EN_CUSTODIA', 'RETIRADO')),
    INDEX idx_custodias_estado (estado),
    INDEX idx_custodias_recibido_por (recibido_por_id)
) ENGINE = InnoDB;
