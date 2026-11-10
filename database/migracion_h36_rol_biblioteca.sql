-- H36: rol BIBLIOTECA y auditoría de cambios de rol.
-- Idempotente: se puede ejecutar sobre una base del Sprint 2 sin borrar datos.
USE objetos_perdidos_db;

ALTER TABLE usuarios MODIFY rol VARCHAR(20) NOT NULL DEFAULT 'USUARIO';

SET @existe_check := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'usuarios'
      AND CONSTRAINT_NAME = 'chk_usuarios_rol'
);
SET @sql := IF(@existe_check > 0,
    'ALTER TABLE usuarios DROP CHECK chk_usuarios_rol',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE usuarios
    ADD CONSTRAINT chk_usuarios_rol
    CHECK (rol IN ('USUARIO', 'BIBLIOTECA', 'ADMIN'));

CREATE TABLE IF NOT EXISTS cambios_rol (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    usuario_id      BIGINT       NOT NULL,
    rol_anterior    VARCHAR(20)  NOT NULL,
    rol_nuevo       VARCHAR(20)  NOT NULL,
    cambiado_por_id BIGINT       NOT NULL,
    motivo          VARCHAR(500) NULL,
    fecha_cambio    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cambio_rol_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT fk_cambio_rol_autor FOREIGN KEY (cambiado_por_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT chk_cambio_rol_anterior
        CHECK (rol_anterior IN ('USUARIO', 'BIBLIOTECA', 'ADMIN')),
    CONSTRAINT chk_cambio_rol_nuevo
        CHECK (rol_nuevo IN ('USUARIO', 'BIBLIOTECA', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
