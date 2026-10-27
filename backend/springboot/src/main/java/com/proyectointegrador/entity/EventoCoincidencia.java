package com.proyectointegrador.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Entity @Table(name="eventos_coincidencia") @Getter @Setter @NoArgsConstructor
public class EventoCoincidencia {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="objeto_id",nullable=false) private Objeto objeto;
    @Column(nullable=false,length=20) private String estado="PENDIENTE";
    @Column(nullable=false) private int intentos=0;
    @Column(length=100) private String detalle;
    @Column(name="fecha_creacion",nullable=false) private LocalDateTime fechaCreacion;
    @PrePersist void preparar(){fechaCreacion=LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);}
}
