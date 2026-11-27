-- H34: eliminación real de la cuenta (Ley 29733). La cuenta se anonimiza (estado ELIMINADO), la
-- solicitud registra quién la atendió o el motivo del rechazo, y las fotos a borrar se anotan para Spring.
-- Idempotente. Hacer un respaldo antes de aplicarla. No borra datos.
USE objetos_perdidos_db;

-- usuarios.estado admite ELIMINADO
SET @tiene := (SELECT COUNT(*) FROM information_schema.check_constraints
               WHERE constraint_schema = DATABASE() AND constraint_name = 'chk_usuarios_estado'
                 AND check_clause LIKE '%ELIMINADO%');
SET @existe := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = 'usuarios' AND constraint_name = 'chk_usuarios_estado');
SET @sql := IF(@existe = 1 AND @tiene = 0, 'ALTER TABLE usuarios DROP CHECK chk_usuarios_estado',
               'SELECT ''chk_usuarios_estado no se cambia''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;
SET @existe := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = 'usuarios' AND constraint_name = 'chk_usuarios_estado');
SET @sql := IF(@existe = 0, 'ALTER TABLE usuarios ADD CONSTRAINT chk_usuarios_estado CHECK (estado IN (''ACTIVO'', ''SUSPENDIDO'', ''ELIMINADO''))', 'SELECT ''chk_usuarios_estado ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

-- La solicitud registra quién la atendió, cuándo, o por qué se rechazó
SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'solicitudes_eliminacion' AND column_name = 'atendida_por_id');
SET @sql := IF(@existe = 0, 'ALTER TABLE solicitudes_eliminacion ADD COLUMN atendida_por_id BIGINT NULL', 'SELECT ''solicitudes_eliminacion.atendida_por_id ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;
SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'solicitudes_eliminacion' AND column_name = 'fecha_atencion');
SET @sql := IF(@existe = 0, 'ALTER TABLE solicitudes_eliminacion ADD COLUMN fecha_atencion DATETIME(6) NULL', 'SELECT ''solicitudes_eliminacion.fecha_atencion ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;
SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'solicitudes_eliminacion' AND column_name = 'motivo_rechazo');
SET @sql := IF(@existe = 0, 'ALTER TABLE solicitudes_eliminacion ADD COLUMN motivo_rechazo VARCHAR(500) NULL', 'SELECT ''solicitudes_eliminacion.motivo_rechazo ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;
SET @existe := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = 'solicitudes_eliminacion' AND constraint_name = 'fk_solicitud_eliminacion_atendida_por');
SET @sql := IF(@existe = 0, 'ALTER TABLE solicitudes_eliminacion ADD CONSTRAINT fk_solicitud_eliminacion_atendida_por FOREIGN KEY (atendida_por_id) REFERENCES usuarios (id)', 'SELECT ''fk_solicitud_eliminacion_atendida_por ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;
SET @existe := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = 'solicitudes_eliminacion' AND constraint_name = 'chk_solicitud_eliminacion_estado');
SET @sql := IF(@existe = 0, 'ALTER TABLE solicitudes_eliminacion ADD CONSTRAINT chk_solicitud_eliminacion_estado CHECK (estado IN (''PENDIENTE'', ''ATENDIDA'', ''RECHAZADA''))', 'SELECT ''chk_solicitud_eliminacion_estado ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

-- Fotos que Spring debe borrar del volumen (Django no tiene acceso a él)
CREATE TABLE IF NOT EXISTS archivos_por_borrar (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ruta       VARCHAR(255) NOT NULL,
    anotado_en DATETIME(6)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
