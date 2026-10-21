package com.proyectointegrador.repository;

import com.proyectointegrador.entity.Reclamo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReclamoRepository extends JpaRepository<Reclamo, Long> {
    Optional<Reclamo> findByObjetoIdAndUsuarioId(Long objetoId, Long usuarioId);
}
