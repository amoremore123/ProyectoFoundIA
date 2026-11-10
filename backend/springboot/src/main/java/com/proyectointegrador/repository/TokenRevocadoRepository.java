package com.proyectointegrador.repository;

import com.proyectointegrador.entity.TokenRevocado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface TokenRevocadoRepository extends JpaRepository<TokenRevocado, String> {

    boolean existsByJti(String jti);

    void deleteByExpiraEnBefore(LocalDateTime limite);
}
