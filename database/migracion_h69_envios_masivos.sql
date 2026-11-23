-- H69: notificaciones masivas con su autor, y cada aviso ligado a su envío.
-- Idempotente. Hacer un respaldo antes de aplicarla. No borra datos.
USE objetos_perdidos_db;

CREATE TABLE IF NOT EXISTS envios_masivos (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    titulo              VARCHAR(150)  NOT NULL,
    mensaje             VARCHAR(1000) NOT NULL,
    enlace              VARCHAR(255)  NULL,
    destinatarios       VARCHAR(12)   NOT NULL,
    total_destinatarios INT           NOT NULL,
    autor_id            BIGINT        NOT NULL,
    enviado_en          DATETIME(6)   NOT NULL,
    CONSTRAINT fk_envio_masivo_autor FOREIGN KEY (autor_id) REFERENCES usuarios (id),
    CONSTRAINT chk_envio_masivo_destinatarios CHECK (destinatarios IN ('TODOS', 'USUARIOS', 'BIBLIOTECA')),
    INDEX idx_envios_masivos_enviado (enviado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'notificaciones' AND column_name = 'envio_masivo_id');
SET @sql := IF(@existe = 0, 'ALTER TABLE notificaciones ADD COLUMN envio_masivo_id BIGINT NULL',
               'SELECT ''notificaciones.envio_masivo_id ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = 'notificaciones'
                  AND constraint_name = 'fk_notificacion_envio_masivo');
SET @sql := IF(@existe = 0, 'ALTER TABLE notificaciones ADD CONSTRAINT fk_notificacion_envio_masivo FOREIGN KEY (envio_masivo_id) REFERENCES envios_masivos (id)',
               'SELECT ''fk_notificacion_envio_masivo ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;
