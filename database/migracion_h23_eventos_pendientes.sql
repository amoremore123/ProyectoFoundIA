-- H23: publicación + evento durable en una transacción; avisos posteriores con bloqueo ordenado.
-- Sin backfill de objetos históricos y sin ejecutar procesos al migrar.
CREATE TABLE IF NOT EXISTS eventos_coincidencia (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, objeto_id BIGINT NOT NULL,
 estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE', intentos INT NOT NULL DEFAULT 0,
 detalle VARCHAR(100) NULL, fecha_creacion DATETIME NOT NULL,
 KEY idx_evento_estado_id(estado,id),
 CONSTRAINT fk_evento_objeto FOREIGN KEY(objeto_id) REFERENCES objetos(id)
) ENGINE=InnoDB;
