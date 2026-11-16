USE objetos_perdidos_db;

CREATE TABLE IF NOT EXISTS invitaciones (
    codigo_hash CHAR(64) NOT NULL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    usos INT NOT NULL DEFAULT 0,
    creada_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invitacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
) ENGINE=InnoDB;
