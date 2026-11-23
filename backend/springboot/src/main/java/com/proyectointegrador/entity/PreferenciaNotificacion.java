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
    @Column(nullable=false) private boolean recordatorios=true;
    /** H68: por dónde sale cada tipo de aviso además de la bandeja. */
    @Enumerated(EnumType.STRING) @Column(name="canal_coincidencias", nullable=false, length=8)
    private CanalAviso canalCoincidencias=CanalAviso.PUSH;
    @Enumerated(EnumType.STRING) @Column(name="canal_contactos", nullable=false, length=8)
    private CanalAviso canalContactos=CanalAviso.PUSH;
    @Enumerated(EnumType.STRING) @Column(name="canal_reclamos", nullable=false, length=8)
    private CanalAviso canalReclamos=CanalAviso.PUSH;
    @Enumerated(EnumType.STRING) @Column(name="canal_recordatorios", nullable=false, length=8)
    private CanalAviso canalRecordatorios=CanalAviso.PUSH;
    @Enumerated(EnumType.STRING) @Column(name="canal_mensajes", nullable=false, length=8)
    private CanalAviso canalMensajes=CanalAviso.PUSH;
    @Enumerated(EnumType.STRING) @Column(name="canal_sistema", nullable=false, length=8)
    private CanalAviso canalSistema=CanalAviso.PUSH;
    @Column(name="fecha_actualizacion", nullable=false) private LocalDateTime fechaActualizacion;
}
