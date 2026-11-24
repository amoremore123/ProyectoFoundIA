-- Extra: etiqueta QR preventiva. Cada persona registra sus pertenencias y pega un QR; quien la
-- encuentra escanea, ve solo el nombre del objeto y avisa al dueño por el chat, sin ver sus datos.
-- Idempotente: se puede ejecutar varias veces. Hacer un respaldo antes de aplicarla. No borra datos.
-- Requiere migracion_h55_chat.sql. No usar docker compose down -v ni reimportar script.sql o seed.sql.
USE objetos_perdidos_db;

CREATE TABLE IF NOT EXISTS pertenencias (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    usuario_id   BIGINT       NOT NULL,
    nombre       VARCHAR(80)  NOT NULL,
    descripcion  VARCHAR(200) NULL,
    codigo       VARCHAR(32)  NOT NULL,
    activa       BOOLEAN      NOT NULL DEFAULT TRUE,
    creada_en    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_pertenencias_codigo UNIQUE (codigo),
    CONSTRAINT fk_pertenencias_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    INDEX idx_pertenencias_usuario (usuario_id, activa)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Las conversaciones que nacen de una etiqueta no tienen publicación: objeto_id pasa a ser opcional
ALTER TABLE conversaciones MODIFY objeto_id BIGINT NULL;

SET @existe := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'conversaciones' AND COLUMN_NAME = 'pertenencia_id');
SET @sql := IF(@existe = 0,
    'ALTER TABLE conversaciones ADD COLUMN pertenencia_id BIGINT NULL AFTER reclamo_id,
       ADD CONSTRAINT fk_conversaciones_pertenencia FOREIGN KEY (pertenencia_id) REFERENCES pertenencias (id) ON DELETE SET NULL',
    'SELECT 1');
PREPARE paso FROM @sql;
EXECUTE paso;
DEALLOCATE PREPARE paso;
