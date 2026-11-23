-- H68: canal de cada tipo de aviso (PUSH, CORREO, AMBOS o NINGUNO) y registro del envío por correo.
-- Idempotente. Requiere migracion_h57_dispositivos.sql. Hacer un respaldo antes de aplicarla.
-- Si la base tiene la primera versión de H68 (canal_push y canal_correo), copia lo guardado a los
-- canales por tipo y después quita esas dos columnas.
USE objetos_perdidos_db;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'preferencias_notificacion' AND column_name = 'canal_coincidencias');
SET @sql := IF(@existe = 0, 'ALTER TABLE preferencias_notificacion ADD COLUMN canal_coincidencias VARCHAR(8) NOT NULL DEFAULT ''PUSH''',
               'SELECT ''preferencias_notificacion.canal_coincidencias ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = 'preferencias_notificacion' AND constraint_name = 'chk_preferencia_canal_coincidencias');
SET @sql := IF(@existe = 0, 'ALTER TABLE preferencias_notificacion ADD CONSTRAINT chk_preferencia_canal_coincidencias CHECK (canal_coincidencias IN (''PUSH'', ''CORREO'', ''AMBOS'', ''NINGUNO''))',
               'SELECT ''chk_preferencia_canal_coincidencias ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'preferencias_notificacion' AND column_name = 'canal_contactos');
SET @sql := IF(@existe = 0, 'ALTER TABLE preferencias_notificacion ADD COLUMN canal_contactos VARCHAR(8) NOT NULL DEFAULT ''PUSH''',
               'SELECT ''preferencias_notificacion.canal_contactos ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = 'preferencias_notificacion' AND constraint_name = 'chk_preferencia_canal_contactos');
SET @sql := IF(@existe = 0, 'ALTER TABLE preferencias_notificacion ADD CONSTRAINT chk_preferencia_canal_contactos CHECK (canal_contactos IN (''PUSH'', ''CORREO'', ''AMBOS'', ''NINGUNO''))',
               'SELECT ''chk_preferencia_canal_contactos ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'preferencias_notificacion' AND column_name = 'canal_reclamos');
SET @sql := IF(@existe = 0, 'ALTER TABLE preferencias_notificacion ADD COLUMN canal_reclamos VARCHAR(8) NOT NULL DEFAULT ''PUSH''',
               'SELECT ''preferencias_notificacion.canal_reclamos ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = 'preferencias_notificacion' AND constraint_name = 'chk_preferencia_canal_reclamos');
SET @sql := IF(@existe = 0, 'ALTER TABLE preferencias_notificacion ADD CONSTRAINT chk_preferencia_canal_reclamos CHECK (canal_reclamos IN (''PUSH'', ''CORREO'', ''AMBOS'', ''NINGUNO''))',
               'SELECT ''chk_preferencia_canal_reclamos ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'preferencias_notificacion' AND column_name = 'canal_recordatorios');
SET @sql := IF(@existe = 0, 'ALTER TABLE preferencias_notificacion ADD COLUMN canal_recordatorios VARCHAR(8) NOT NULL DEFAULT ''PUSH''',
               'SELECT ''preferencias_notificacion.canal_recordatorios ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = 'preferencias_notificacion' AND constraint_name = 'chk_preferencia_canal_recordatorios');
SET @sql := IF(@existe = 0, 'ALTER TABLE preferencias_notificacion ADD CONSTRAINT chk_preferencia_canal_recordatorios CHECK (canal_recordatorios IN (''PUSH'', ''CORREO'', ''AMBOS'', ''NINGUNO''))',
               'SELECT ''chk_preferencia_canal_recordatorios ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'preferencias_notificacion' AND column_name = 'canal_mensajes');
SET @sql := IF(@existe = 0, 'ALTER TABLE preferencias_notificacion ADD COLUMN canal_mensajes VARCHAR(8) NOT NULL DEFAULT ''PUSH''',
               'SELECT ''preferencias_notificacion.canal_mensajes ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = 'preferencias_notificacion' AND constraint_name = 'chk_preferencia_canal_mensajes');
SET @sql := IF(@existe = 0, 'ALTER TABLE preferencias_notificacion ADD CONSTRAINT chk_preferencia_canal_mensajes CHECK (canal_mensajes IN (''PUSH'', ''CORREO'', ''AMBOS'', ''NINGUNO''))',
               'SELECT ''chk_preferencia_canal_mensajes ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'preferencias_notificacion' AND column_name = 'canal_sistema');
SET @sql := IF(@existe = 0, 'ALTER TABLE preferencias_notificacion ADD COLUMN canal_sistema VARCHAR(8) NOT NULL DEFAULT ''PUSH''',
               'SELECT ''preferencias_notificacion.canal_sistema ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_schema = DATABASE() AND table_name = 'preferencias_notificacion' AND constraint_name = 'chk_preferencia_canal_sistema');
SET @sql := IF(@existe = 0, 'ALTER TABLE preferencias_notificacion ADD CONSTRAINT chk_preferencia_canal_sistema CHECK (canal_sistema IN (''PUSH'', ''CORREO'', ''AMBOS'', ''NINGUNO''))',
               'SELECT ''chk_preferencia_canal_sistema ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;


-- Primera versión de H68: se copia lo guardado y se quitan las columnas viejas.
SET @vieja := (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = 'preferencias_notificacion'
                 AND column_name IN ('canal_push', 'canal_correo'));
SET @sql := IF(@vieja = 2, 'UPDATE preferencias_notificacion SET canal_coincidencias = CASE WHEN canal_push AND canal_correo THEN ''AMBOS'' WHEN canal_push THEN ''PUSH'' WHEN canal_correo THEN ''CORREO'' ELSE ''NINGUNO'' END, canal_contactos = CASE WHEN canal_push AND canal_correo THEN ''AMBOS'' WHEN canal_push THEN ''PUSH'' WHEN canal_correo THEN ''CORREO'' ELSE ''NINGUNO'' END, canal_reclamos = CASE WHEN canal_push AND canal_correo THEN ''AMBOS'' WHEN canal_push THEN ''PUSH'' WHEN canal_correo THEN ''CORREO'' ELSE ''NINGUNO'' END, canal_recordatorios = CASE WHEN canal_push AND canal_correo THEN ''AMBOS'' WHEN canal_push THEN ''PUSH'' WHEN canal_correo THEN ''CORREO'' ELSE ''NINGUNO'' END, canal_mensajes = CASE WHEN canal_push AND canal_correo THEN ''AMBOS'' WHEN canal_push THEN ''PUSH'' WHEN canal_correo THEN ''CORREO'' ELSE ''NINGUNO'' END, canal_sistema = CASE WHEN canal_push AND canal_correo THEN ''AMBOS'' WHEN canal_push THEN ''PUSH'' WHEN canal_correo THEN ''CORREO'' ELSE ''NINGUNO'' END',
               'SELECT ''sin columnas de la primera versión de H68''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;
SET @sql := IF(@vieja = 2, 'ALTER TABLE preferencias_notificacion DROP COLUMN canal_push, DROP COLUMN canal_correo',
               'SELECT ''nada que quitar''');
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
