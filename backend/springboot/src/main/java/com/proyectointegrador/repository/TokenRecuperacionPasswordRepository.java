package com.proyectointegrador.repository;

import com.proyectointegrador.entity.TokenRecuperacionPassword;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TokenRecuperacionPasswordRepository extends JpaRepository<TokenRecuperacionPassword, Long> {

    /** Bloquea la fila para que dos usos simultáneos del mismo enlace no cambien la contraseña dos veces. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TokenRecuperacionPassword t where t.tokenHash = :hash")
    Optional<TokenRecuperacionPassword> findByTokenHashForUpdate(@Param("hash") String hash);

    List<TokenRecuperacionPassword> findByUsuarioIdAndUsadoEnIsNull(Long usuarioId);

    Optional<TokenRecuperacionPassword> findFirstByUsuarioIdOrderByCreadoEnDesc(Long usuarioId);
}
