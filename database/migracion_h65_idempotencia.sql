-- H65: identificador de cliente (Idempotency-Key) para que un reintento no duplique mensajes ni publicaciones.
-- Idempotente. Requiere migracion_h55_chat.sql. Hacer un respaldo antes de aplicarla. No borra datos.
USE objetos_perdidos_db;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'mensajes_chat' AND column_name = 'id_cliente');
SET @sql := IF(@existe = 0, 'ALTER TABLE mensajes_chat ADD COLUMN id_cliente VARCHAR(64) NULL',
               'SELECT ''mensajes_chat.id_cliente ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'mensajes_chat' AND index_name = 'uq_mensajes_chat_cliente');
SET @sql := IF(@existe = 0, 'ALTER TABLE mensajes_chat ADD CONSTRAINT uq_mensajes_chat_cliente UNIQUE (autor_id, id_cliente)',
               'SELECT ''uq_mensajes_chat_cliente ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'objetos' AND column_name = 'id_cliente');
SET @sql := IF(@existe = 0, 'ALTER TABLE objetos ADD COLUMN id_cliente VARCHAR(64) NULL',
               'SELECT ''objetos.id_cliente ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;

SET @existe := (SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'objetos' AND index_name = 'uq_objetos_cliente');
SET @sql := IF(@existe = 0, 'ALTER TABLE objetos ADD CONSTRAINT uq_objetos_cliente UNIQUE (usuario_id, id_cliente)',
               'SELECT ''uq_objetos_cliente ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;
