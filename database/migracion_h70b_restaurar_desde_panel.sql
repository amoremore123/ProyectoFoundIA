-- H70: restaurar una versión anterior desde el panel. El panel deja la restauración SOLICITADA y el servicio
-- de respaldo la atiende. Idempotente. Requiere migracion_h70_restauraciones.sql. No borra datos.
USE objetos_perdidos_db;

SET @tiene := (SELECT COUNT(*) FROM information_schema.check_constraints
               WHERE constraint_schema = DATABASE() AND constraint_name = 'chk_restauracion_estado'
                 AND check_clause LIKE '%SOLICITADA%');
SET @existe := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = 'restauraciones' AND constraint_name = 'chk_restauracion_estado');
SET @sql := IF(@existe = 1 AND @tiene = 0, 'ALTER TABLE restauraciones DROP CHECK chk_restauracion_estado',
               'SELECT ''chk_restauracion_estado no se cambia''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = 'restauraciones' AND constraint_name = 'chk_restauracion_estado');
SET @sql := IF(@existe = 0,
    'ALTER TABLE restauraciones ADD CONSTRAINT chk_restauracion_estado CHECK (estado IN (''SOLICITADA'', ''EN_PROCESO'', ''COMPLETADA'', ''FALLIDA''))',
    'SELECT ''chk_restauracion_estado ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;
