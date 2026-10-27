-- H23. Incremental, sin modificar antecedentes. Aplicar solo al destino autorizado.
SET NAMES utf8mb4;
SET @existe = (SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='notificaciones' AND COLUMN_NAME='enlace');
SET @sql = IF(@existe=0, 'ALTER TABLE notificaciones ADD COLUMN enlace VARCHAR(500) NULL', 'SELECT ''enlace ya existe'' AS aviso');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
CREATE TABLE IF NOT EXISTS avisos_coincidencia (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 objeto_perdido_id BIGINT NOT NULL, objeto_encontrado_id BIGINT NOT NULL,
 notificacion_id BIGINT NULL,
 CONSTRAINT uq_aviso_pareja UNIQUE (objeto_perdido_id, objeto_encontrado_id),
 CONSTRAINT fk_aviso_perdido FOREIGN KEY (objeto_perdido_id) REFERENCES objetos(id),
 CONSTRAINT fk_aviso_encontrado FOREIGN KEY (objeto_encontrado_id) REFERENCES objetos(id),
 CONSTRAINT fk_aviso_notificacion FOREIGN KEY (notificacion_id) REFERENCES notificaciones(id)
) ENGINE=InnoDB;
