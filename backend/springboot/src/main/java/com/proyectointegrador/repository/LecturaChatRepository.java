package com.proyectointegrador.repository;

import com.proyectointegrador.entity.LecturaChat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LecturaChatRepository extends JpaRepository<LecturaChat, Long> {

    Optional<LecturaChat> findByConversacionIdAndUsuarioId(Long conversacionId, Long usuarioId);
}
