package com.proyectointegrador.service;

import com.proyectointegrador.dto.DispositivoRequest;
import com.proyectointegrador.dto.DispositivoResponse;
import com.proyectointegrador.entity.Dispositivo;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.repository.DispositivoRepository;
import com.proyectointegrador.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * H57: dispositivos que reciben push. Un token pertenece a una sola cuenta: si el teléfono
 * cambia de cuenta, el token pasa a la nueva. Como máximo 10 por cuenta.
 */
@Service
public class DispositivoService {

    public static final int MAXIMO_POR_CUENTA = 10;

    private final DispositivoRepository dispositivos;
    private final UsuarioRepository usuarios;

    public DispositivoService(DispositivoRepository dispositivos, UsuarioRepository usuarios) {
        this.dispositivos = dispositivos;
        this.usuarios = usuarios;
    }

    public record Registro(DispositivoResponse dispositivo, boolean nuevo) {}

    @Transactional
    public Registro registrar(Long usuarioId, DispositivoRequest datos, String sesionJti) {
        var usuario = usuarios.findByIdForUpdate(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);

        var existente = dispositivos.findByTokenForUpdate(datos.token());
        boolean nuevo = existente.isEmpty();
        Dispositivo d = existente.orElseGet(Dispositivo::new);
        if (nuevo) {
            d.setToken(datos.token());
            d.setFechaRegistro(ahora);
        }
        // Mismo token en otra cuenta: el teléfono cambió de cuenta y deja de recibir los avisos de la anterior
        d.setUsuario(usuario);
        d.setPlataforma(datos.plataforma());
        d.setSesionJti(sesionJti);
        d.setFechaUltimoUso(ahora);
        d = dispositivos.saveAndFlush(d);

        List<Dispositivo> propios = dispositivos.findByUsuarioIdOrderByFechaUltimoUsoDescIdDesc(usuarioId);
        if (propios.size() > MAXIMO_POR_CUENTA) {
            dispositivos.deleteAll(propios.subList(MAXIMO_POR_CUENTA, propios.size()));
        }
        return new Registro(DispositivoResponse.from(d), nuevo);
    }

    @Transactional(readOnly = true)
    public List<DispositivoResponse> listar(Long usuarioId) {
        return dispositivos.findByUsuarioIdOrderByFechaUltimoUsoDescIdDesc(usuarioId).stream()
                .map(DispositivoResponse::from).toList();
    }

    @Transactional
    public void eliminar(Long usuarioId, Long id) {
        // De otra cuenta responde igual que si no existiera
        Dispositivo d = dispositivos.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispositivo no encontrado"));
        dispositivos.delete(d);
    }

    /** H37: cerrar una sesión borra los dispositivos que registró esa sesión. */
    @Transactional
    public int borrarDeSesion(String jti) {
        return jti == null || jti.isBlank() ? 0 : dispositivos.deleteBySesionJti(jti);
    }

    /** H37: cerrar en todos los dispositivos borra todos los de la cuenta. */
    @Transactional
    public int borrarDeCuenta(Long usuarioId) {
        return dispositivos.deleteByUsuarioId(usuarioId);
    }
}
