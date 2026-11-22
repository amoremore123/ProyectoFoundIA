package com.proyectointegrador.repository;

import com.proyectointegrador.entity.BloqueoChat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BloqueoChatRepository extends JpaRepository<BloqueoChat, Long> {

    boolean existsByBloqueadorIdAndBloqueadoId(Long bloqueadorId, Long bloqueadoId);

    Optional<BloqueoChat> findByBloqueadorIdAndBloqueadoId(Long bloqueadorId, Long bloqueadoId);
}
