-- H59: distinguir los respaldos de la base (BASE) de los de fotos (FOTOS).
-- Idempotente: se puede ejecutar varias veces sobre una base con H38 aplicada.
-- Hacer un respaldo antes de aplicarla. No borra filas ni toca volúmenes.
USE objetos_perdidos_db;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'respaldos' AND column_name = 'tipo');
SET @sql := IF(@existe = 0,
    'ALTER TABLE respaldos ADD COLUMN tipo VARCHAR(10) NOT NULL DEFAULT ''BASE'' AFTER id',
    'SELECT ''respaldos.tipo ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = 'respaldos'
                  AND constraint_name = 'chk_respaldo_tipo');
SET @sql := IF(@existe = 0,
    'ALTER TABLE respaldos ADD CONSTRAINT chk_respaldo_tipo CHECK (tipo IN (''BASE'', ''FOTOS''))',
    'SELECT ''chk_respaldo_tipo ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'respaldos'
                  AND index_name = 'idx_respaldos_tipo_iniciado');
SET @sql := IF(@existe = 0,
    'CREATE INDEX idx_respaldos_tipo_iniciado ON respaldos (tipo, iniciado_en)',
    'SELECT ''idx_respaldos_tipo_iniciado ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;
