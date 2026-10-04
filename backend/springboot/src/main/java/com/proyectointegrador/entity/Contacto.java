package com.proyectointegrador.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "contactos")
@Getter
@Setter
@NoArgsConstructor
public class Contacto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "coincidencia_id", nullable = false)
    private Coincidencia coincidencia;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_emisor_id", nullable = false)
    private Usuario usuarioEmisor;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_receptor_id", nullable = false)
    private Usuario usuarioReceptor;

    @Column(name = "mensaje", nullable = false, columnDefinition = "TEXT")
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoContacto estado = EstadoContacto.PENDIENTE;

    @Column(name = "fecha_contacto", nullable = false, updatable = false)
    private LocalDateTime fechaContacto;

    @PrePersist
    void prePersist() {
        if (fechaContacto == null) {
            fechaContacto = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        }
    }
}
