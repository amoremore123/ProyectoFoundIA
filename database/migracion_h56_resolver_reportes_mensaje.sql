-- H56: resolución auditada de los reportes de mensajes del chat desde el panel.
-- Idempotente. Requiere migracion_h55_chat.sql. Hacer un respaldo antes de aplicarla.
-- Solo agrega columnas; las filas existentes quedan PENDIENTE y sin resolución.
USE objetos_perdidos_db;

SET @t := 'reportes_mensaje';

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = @t AND column_name = 'accion');
SET @sql := IF(@existe = 0, 'ALTER TABLE reportes_mensaje ADD COLUMN accion VARCHAR(20) NULL',
               'SELECT ''reportes_mensaje.accion ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = @t AND column_name = 'resolucion');
SET @sql := IF(@existe = 0, 'ALTER TABLE reportes_mensaje ADD COLUMN resolucion VARCHAR(1000) NULL',
               'SELECT ''reportes_mensaje.resolucion ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = @t AND column_name = 'resuelto_por_id');
SET @sql := IF(@existe = 0, 'ALTER TABLE reportes_mensaje ADD COLUMN resuelto_por_id BIGINT NULL',
               'SELECT ''reportes_mensaje.resuelto_por_id ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = @t AND column_name = 'fecha_resolucion');
SET @sql := IF(@existe = 0, 'ALTER TABLE reportes_mensaje ADD COLUMN fecha_resolucion DATETIME NULL',
               'SELECT ''reportes_mensaje.fecha_resolucion ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = @t AND constraint_name = 'fk_reportes_mensaje_resuelto_por');
SET @sql := IF(@existe = 0,
    'ALTER TABLE reportes_mensaje ADD CONSTRAINT fk_reportes_mensaje_resuelto_por FOREIGN KEY (resuelto_por_id) REFERENCES usuarios (id)',
    'SELECT ''fk_reportes_mensaje_resuelto_por ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;
