-- H37: cierre de sesión individual y en todos los dispositivos.
-- Idempotente: se puede ejecutar sobre una base del Sprint 2 sin borrar datos.
USE objetos_perdidos_db;

-- 1. Versión de sesión del usuario (solo si no existe).
SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE usuarios ADD COLUMN sesion_version INT NOT NULL DEFAULT 0', 'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuarios'
      AND COLUMN_NAME = 'sesion_version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. Tokens revocados por cierre de sesión individual.
CREATE TABLE IF NOT EXISTS tokens_revocados (
    jti         VARCHAR(64) NOT NULL PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL,
    revocado_en DATETIME(6) NOT NULL,
    expira_en   DATETIME(6) NOT NULL,
    CONSTRAINT fk_token_revocado_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    INDEX idx_tokens_revocados_expira (expira_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
