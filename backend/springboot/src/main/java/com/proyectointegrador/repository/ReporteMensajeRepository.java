package com.proyectointegrador.repository;

import com.proyectointegrador.entity.ReporteMensaje;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReporteMensajeRepository extends JpaRepository<ReporteMensaje, Long> {

    boolean existsByMensajeIdAndUsuarioId(Long mensajeId, Long usuarioId);

    /** H62: el administrador puede ver la foto de un mensaje reportado para moderarlo. */
    boolean existsByMensajeId(Long mensajeId);
}
