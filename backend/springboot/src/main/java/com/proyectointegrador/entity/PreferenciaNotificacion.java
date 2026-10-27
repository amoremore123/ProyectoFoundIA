package com.proyectointegrador.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="preferencias_notificacion")
@Getter @Setter @NoArgsConstructor
public class PreferenciaNotificacion {
    @Id @Column(name="usuario_id") private Long usuarioId;
    @Column(nullable=false) private boolean coincidencias=true;
    @Column(nullable=false) private boolean contactos=true;
    @Column(nullable=false) private boolean reclamos=true;
    @Column(name="fecha_actualizacion", nullable=false) private LocalDateTime fechaActualizacion;
}
