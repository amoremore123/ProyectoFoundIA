package com.proyectointegrador.repository;

import com.proyectointegrador.entity.Dispositivo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DispositivoRepository extends JpaRepository<Dispositivo, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Dispositivo d WHERE d.token = :token")
    Optional<Dispositivo> findByTokenForUpdate(@Param("token") String token);

    List<Dispositivo> findByUsuarioIdOrderByFechaUltimoUsoDescIdDesc(Long usuarioId);

    Optional<Dispositivo> findByIdAndUsuarioId(Long id, Long usuarioId);

    @Modifying
    @Query("DELETE FROM Dispositivo d WHERE d.sesionJti = :jti")
    int deleteBySesionJti(@Param("jti") String jti);

    @Modifying
    @Query("DELETE FROM Dispositivo d WHERE d.usuario.id = :usuarioId")
    int deleteByUsuarioId(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("DELETE FROM Dispositivo d WHERE d.token = :token")
    int deleteByToken(@Param("token") String token);
}
