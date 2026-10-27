-- H24. Defaults habilitados, sin borrar ni reescribir el historial.
CREATE TABLE IF NOT EXISTS preferencias_notificacion (
 usuario_id BIGINT NOT NULL PRIMARY KEY,
 coincidencias BOOLEAN NOT NULL DEFAULT TRUE, contactos BOOLEAN NOT NULL DEFAULT TRUE,
 reclamos BOOLEAN NOT NULL DEFAULT TRUE, fecha_actualizacion DATETIME NOT NULL,
 CONSTRAINT fk_preferencia_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
) ENGINE=InnoDB;
