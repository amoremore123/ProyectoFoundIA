package com.proyectointegrador.repository;

import com.proyectointegrador.entity.Pertenencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PertenenciaRepository extends JpaRepository<Pertenencia, Long> {

    List<Pertenencia> findByUsuarioIdAndActivaTrueOrderByIdDesc(Long usuarioId);

    long countByUsuarioIdAndActivaTrue(Long usuarioId);

    Optional<Pertenencia> findByCodigo(String codigo);
}
