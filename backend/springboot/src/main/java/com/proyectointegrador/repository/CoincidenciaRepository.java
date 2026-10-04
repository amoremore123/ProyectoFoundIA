package com.proyectointegrador.repository;

import com.proyectointegrador.entity.Coincidencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoincidenciaRepository extends JpaRepository<Coincidencia, Long> {

    boolean existsByObjetoPerdidoIdAndObjetoEncontradoId(Long objetoPerdidoId, Long objetoEncontradoId);

    List<Coincidencia> findByObjetoPerdidoId(Long objetoPerdidoId);

    List<Coincidencia> findByObjetoEncontradoId(Long objetoEncontradoId);
}
