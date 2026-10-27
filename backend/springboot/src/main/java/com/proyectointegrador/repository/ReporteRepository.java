package com.proyectointegrador.repository;

import com.proyectointegrador.entity.Reporte;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReporteRepository extends JpaRepository<Reporte, Long> {

    Optional<Reporte> findFirstByObjetoIdAndUsuarioIdOrderByIdAsc(Long objetoId, Long usuarioId);

    List<Reporte> findByUsuarioIdOrderByFechaReporteDesc(Long usuarioId);

    List<Reporte> findByObjetoIdOrderByFechaReporteDesc(Long objetoId);
}
