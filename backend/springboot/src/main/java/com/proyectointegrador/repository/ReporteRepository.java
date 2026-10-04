package com.proyectointegrador.repository;

import com.proyectointegrador.entity.Reporte;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReporteRepository extends JpaRepository<Reporte, Long> {

    List<Reporte> findByUsuarioIdOrderByFechaReporteDesc(Long usuarioId);

    List<Reporte> findByObjetoIdOrderByFechaReporteDesc(Long objetoId);
}
