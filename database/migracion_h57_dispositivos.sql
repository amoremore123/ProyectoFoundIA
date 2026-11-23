-- H57: dispositivos para el push con Firebase y marca de envío en cada aviso.
-- Idempotente. Hacer un respaldo antes de aplicarla. No borra datos.
USE objetos_perdidos_db;

CREATE TABLE IF NOT EXISTS dispositivos (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    usuario_id       BIGINT       NOT NULL,
    token            VARCHAR(512) NOT NULL,
    plataforma       VARCHAR(10)  NOT NULL,
    sesion_jti       VARCHAR(64)  NULL,
    fecha_registro   DATETIME     NOT NULL,
    fecha_ultimo_uso DATETIME     NOT NULL,
    CONSTRAINT uk_dispositivo_token UNIQUE (token),
    CONSTRAINT fk_dispositivo_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT chk_dispositivo_plataforma CHECK (plataforma IN ('ANDROID')),
    INDEX idx_dispositivos_usuario (usuario_id, fecha_ultimo_uso),
    INDEX idx_dispositivos_sesion (sesion_jti)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'notificaciones' AND column_name = 'push_enviado_en');
SET @sql := IF(@existe = 0, 'ALTER TABLE notificaciones ADD COLUMN push_enviado_en DATETIME NULL',
               'SELECT ''notificaciones.push_enviado_en ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'notificaciones' AND index_name = 'idx_notificaciones_push');
SET @sql := IF(@existe = 0, 'CREATE INDEX idx_notificaciones_push ON notificaciones (push_enviado_en, fecha_creacion)',
               'SELECT ''idx_notificaciones_push ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;
