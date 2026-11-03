-- H35: recuperación de contraseña con enlace de un solo uso.
-- Solo se guarda el hash SHA-256 del token, nunca el token en claro.
-- Re-ejecutable. Revisar respaldo y base destino antes de aplicar;
-- no ejecutar script.sql ni seed.sql sobre bases existentes.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS tokens_recuperacion_password (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL,
    token_hash  CHAR(64)    NOT NULL,
    creado_en   DATETIME(6) NOT NULL,
    expira_en   DATETIME(6) NOT NULL,
    usado_en    DATETIME(6) NULL COMMENT 'Uso o invalidación del enlace en UTC',
    CONSTRAINT uk_token_recuperacion_hash UNIQUE (token_hash),
    CONSTRAINT fk_token_recuperacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    INDEX idx_token_recuperacion_usuario (usuario_id, creado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Fecha del último cambio de contraseña por recuperación: los JWT emitidos antes dejan de valer.
SET @existe = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='usuarios' AND COLUMN_NAME='password_actualizado_en');
SET @sql = IF(@existe=0, 'ALTER TABLE usuarios ADD COLUMN password_actualizado_en DATETIME NULL', 'SELECT ''password_actualizado_en ya existe'' AS aviso');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
