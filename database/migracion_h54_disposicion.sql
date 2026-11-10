-- H54: disposición final de objetos no reclamados (donado o descartado) con acta.
-- Idempotente: se puede ejecutar varias veces sobre una base con H41/H43 sin borrar datos.
-- Antes de aplicarla, respalda la base (ver database/README.md). Nunca uses `docker compose down -v`.
USE objetos_perdidos_db;

-- 1) La custodia admite el estado DISPUESTO (el objeto no se borra: queda OCULTO).
SET @existe_check := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'custodias'
      AND CONSTRAINT_NAME = 'chk_custodias_estado'
);
SET @sql := IF(@existe_check > 0,
    'ALTER TABLE custodias DROP CHECK chk_custodias_estado',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE custodias
    ADD CONSTRAINT chk_custodias_estado
    CHECK (estado IN ('EN_CUSTODIA', 'RETIRADO', 'DISPUESTO'));

-- 2) Acta de disposición: una por custodia, con responsable y fecha que pone el servidor.
CREATE TABLE IF NOT EXISTS disposiciones (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    custodia_id      BIGINT        NOT NULL,
    codigo_acta      VARCHAR(30)   NOT NULL,
    destino          VARCHAR(20)   NOT NULL,
    motivo           VARCHAR(1000) NOT NULL,
    plazo_dias       INT           NOT NULL,
    dias_en_custodia INT           NOT NULL,
    responsable_id   BIGINT        NOT NULL,
    fecha_disposicion DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_disposiciones_custodia (custodia_id),
    UNIQUE KEY uq_disposiciones_acta (codigo_acta),
    CONSTRAINT fk_disposiciones_custodia FOREIGN KEY (custodia_id)
        REFERENCES custodias (id) ON DELETE RESTRICT,
    CONSTRAINT fk_disposiciones_responsable FOREIGN KEY (responsable_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT chk_disposiciones_destino CHECK (destino IN ('DONADO', 'DESCARTADO')),
    INDEX idx_disposiciones_fecha (fecha_disposicion)
) ENGINE = InnoDB;
