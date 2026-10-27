package com.proyectointegrador.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "solicitudes_eliminacion")
@Getter @Setter @NoArgsConstructor
public class SolicitudEliminacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(optional = false) @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;
    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE";
    @Column(name = "fecha_solicitud", nullable = false, updatable = false)
    private Instant fechaSolicitud;
}
