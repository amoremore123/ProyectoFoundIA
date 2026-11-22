package com.proyectointegrador.repository;

import com.proyectointegrador.entity.MensajeChat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MensajeChatRepository extends JpaRepository<MensajeChat, Long> {

    List<MensajeChat> findTop50ByConversacionIdAndIdGreaterThanOrderByIdAsc(Long conversacionId, Long despuesDe);

    Optional<MensajeChat> findTopByConversacionIdOrderByIdDesc(Long conversacionId);

    long countByAutorIdAndEnviadoEnAfter(Long autorId, LocalDateTime desde);

    long countByConversacionIdAndIdGreaterThanAndAutorIdNot(Long conversacionId, Long despuesDe, Long autorId);

    Optional<MensajeChat> findByIdAndConversacionId(Long id, Long conversacionId);
}
