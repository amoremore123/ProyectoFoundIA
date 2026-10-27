package com.proyectointegrador.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "avisos_coincidencia", uniqueConstraints = @UniqueConstraint(
        name = "uq_aviso_pareja", columnNames = {"objeto_perdido_id", "objeto_encontrado_id"}))
@Getter
@Setter
@NoArgsConstructor
public class AvisoCoincidencia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "objeto_perdido_id", nullable = false)
    private Objeto objetoPerdido;
    @ManyToOne(optional = false) @JoinColumn(name = "objeto_encontrado_id", nullable = false)
    private Objeto objetoEncontrado;
    @ManyToOne @JoinColumn(name = "notificacion_id")
    private Notificacion notificacion;
}
