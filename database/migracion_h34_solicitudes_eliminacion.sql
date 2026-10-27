-- H34: trámite propio. No elimina ni actualiza cuentas o antecedentes.
CREATE TABLE IF NOT EXISTS solicitudes_eliminacion (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha_solicitud DATETIME(6) NOT NULL,
    CONSTRAINT uk_solicitud_eliminacion_usuario UNIQUE (usuario_id),
    CONSTRAINT fk_solicitud_eliminacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
