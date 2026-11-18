package com.proyectointegrador.repository;

import com.proyectointegrador.entity.ReporteMensaje;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReporteMensajeRepository extends JpaRepository<ReporteMensaje, Long> {

    boolean existsByMensajeIdAndUsuarioId(Long mensajeId, Long usuarioId);
}
