-- H70: restaurar una versión anterior. Marca los respaldos borrados por la retención y registra
-- cada ensayo y cada restauración aplicada. Idempotente. Requiere migracion_h38_respaldos.sql.
-- Hacer un respaldo antes de aplicarla. No borra datos.
USE objetos_perdidos_db;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'respaldos' AND column_name = 'eliminado_en');
SET @sql := IF(@existe = 0, 'ALTER TABLE respaldos ADD COLUMN eliminado_en DATETIME(6) NULL',
               'SELECT ''respaldos.eliminado_en ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

CREATE TABLE IF NOT EXISTS restauraciones (
    id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version            VARCHAR(20)   NOT NULL,
    modo               VARCHAR(10)   NOT NULL,
    estado             VARCHAR(12)   NOT NULL DEFAULT 'EN_PROCESO',
    respaldo_base      VARCHAR(255)  NOT NULL,
    respaldo_fotos     VARCHAR(255)  NOT NULL,
    respaldo_seguridad VARCHAR(20)   NULL,
    responsable        VARCHAR(120)  NULL,
    iniciado_en        DATETIME(6)   NOT NULL,
    finalizado_en      DATETIME(6)   NULL,
    mensaje            VARCHAR(1000) NULL,
    CONSTRAINT chk_restauracion_modo CHECK (modo IN ('ENSAYO', 'APLICADA')),
    CONSTRAINT chk_restauracion_estado CHECK (estado IN ('EN_PROCESO', 'COMPLETADA', 'FALLIDA')),
    INDEX idx_restauraciones_iniciado (iniciado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
