-- H53: recordatorios de objetos perdidos sin resolver.
-- Idempotente: se puede ejecutar sobre una base del Sprint 2 sin borrar datos.
-- Agrega la preferencia `recordatorios` y la tabla de deduplicación mensual.
USE objetos_perdidos_db;

-- 1. Preferencia propia para activar o desactivar los recordatorios.
SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE preferencias_notificacion ADD COLUMN recordatorios BOOLEAN NOT NULL DEFAULT TRUE',
    'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'preferencias_notificacion'
      AND COLUMN_NAME = 'recordatorios');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. Un solo recordatorio por objeto y periodo mensual (yyyy-MM en UTC).
CREATE TABLE IF NOT EXISTS recordatorios_objeto (
    id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    objeto_id       BIGINT      NOT NULL,
    periodo         CHAR(7)     NOT NULL,
    notificacion_id BIGINT      NULL,
    fecha_creacion  DATETIME(6) NOT NULL,
    CONSTRAINT uk_recordatorio_objeto_periodo UNIQUE (objeto_id, periodo),
    CONSTRAINT fk_recordatorio_objeto FOREIGN KEY (objeto_id) REFERENCES objetos(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_recordatorio_notificacion FOREIGN KEY (notificacion_id) REFERENCES notificaciones(id)
        ON DELETE SET NULL,
    INDEX idx_recordatorios_periodo (periodo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
