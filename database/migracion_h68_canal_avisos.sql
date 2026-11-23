-- H68: elegir el canal de los avisos (push y correo) y registrar el envío por correo.
-- Idempotente. Requiere migracion_h57_dispositivos.sql. Hacer un respaldo antes de aplicarla. No borra datos.
USE objetos_perdidos_db;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'preferencias_notificacion' AND column_name = 'canal_push');
SET @sql := IF(@existe = 0, 'ALTER TABLE preferencias_notificacion ADD COLUMN canal_push BOOLEAN NOT NULL DEFAULT TRUE',
               'SELECT ''preferencias_notificacion.canal_push ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'preferencias_notificacion' AND column_name = 'canal_correo');
SET @sql := IF(@existe = 0, 'ALTER TABLE preferencias_notificacion ADD COLUMN canal_correo BOOLEAN NOT NULL DEFAULT FALSE',
               'SELECT ''preferencias_notificacion.canal_correo ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'notificaciones' AND column_name = 'correo_enviado_en');
SET @sql := IF(@existe = 0, 'ALTER TABLE notificaciones ADD COLUMN correo_enviado_en DATETIME NULL',
               'SELECT ''notificaciones.correo_enviado_en ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'notificaciones' AND column_name = 'correo_resultado');
SET @sql := IF(@existe = 0, 'ALTER TABLE notificaciones ADD COLUMN correo_resultado VARCHAR(10) NULL',
               'SELECT ''notificaciones.correo_resultado ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'notificaciones' AND index_name = 'idx_notificaciones_correo');
SET @sql := IF(@existe = 0, 'CREATE INDEX idx_notificaciones_correo ON notificaciones (correo_enviado_en, fecha_creacion)',
               'SELECT ''idx_notificaciones_correo ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;
