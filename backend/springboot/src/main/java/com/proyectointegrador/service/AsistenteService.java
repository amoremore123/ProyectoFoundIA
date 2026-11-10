package com.proyectointegrador.service;

import com.proyectointegrador.dto.AsistenteResponse;
import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.repository.CategoriaRepository;
import com.proyectointegrador.repository.ObjetoRepository;
import com.proyectointegrador.repository.ReclamoRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * H45: interpreta el mensaje del asistente y, si hay sesión, añade un resumen
 * de las publicaciones, reclamos y QR del propio usuario.
 *
 * El resumen son solo cifras: no se devuelven nombres, correos, teléfonos ni
 * tokens de QR, y nada de eso se envía al proveedor de IA.
 */
@Service
public class AsistenteService {

    private final AsistenteMotorLocal motorLocal;
    private final ClaudeAsistenteClient clienteIa;
    private final AsistenteIaValidator validador;
    private final CategoriaRepository categoriaRepository;
    private final ObjetoRepository objetoRepository;
    private final ReclamoRepository reclamoRepository;
    private final InterpretadorIaCompatible compatible;

    @Autowired
    public AsistenteService(AsistenteMotorLocal motorLocal,
                            ClaudeAsistenteClient clienteIa,
                            AsistenteIaValidator validador,
                            CategoriaRepository categoriaRepository,
                            ObjetoRepository objetoRepository,
                            ReclamoRepository reclamoRepository,
                            InterpretadorIaCompatible compatible) {
        this.motorLocal = motorLocal;
        this.clienteIa = clienteIa;
        this.validador = validador;
        this.categoriaRepository = categoriaRepository;
        this.objetoRepository = objetoRepository;
        this.reclamoRepository = reclamoRepository;
        this.compatible = compatible;
    }

    @Transactional(readOnly = true)
    public AsistenteResponse interpretar(String mensaje, Long usuarioId) {
        List<Categoria> categorias = categoriaRepository.findAllByOrderByNombreAsc().stream()
                .filter(c -> Boolean.TRUE.equals(c.getEstado()))
                .toList();

        Optional<InterpretacionAsistente> deIa = compatible.intentarInterpretar(mensaje, categorias);
        String fuente = deIa.isPresent() ? "ia" : "local";
        if (deIa.isEmpty()) {
            deIa = clienteIa.interpretar(mensaje, categorias)
                    .flatMap(cruda -> validador.validar(cruda, categorias));
            if (deIa.isPresent()) fuente = "claude";
        }
        InterpretacionAsistente interpretacion = deIa
                .orElseGet(() -> motorLocal.interpretar(mensaje, categorias));

        AsistenteResponse.DatosPropios datosPropios = usuarioId == null
                ? null : resumenPropio(usuarioId);

        return new AsistenteResponse(
                interpretacion.intencion(),
                interpretacion.tipoBuscado(),
                interpretacion.categoriaId() == null
                        ? null
                        : new AsistenteResponse.CategoriaAsistente(
                                interpretacion.categoriaId(), interpretacion.categoriaNombre()),
                interpretacion.ubicacion(),
                interpretacion.palabras(),
                interpretacion.colores(),
                fuente,
                datosPropios == null ? null : resumenTexto(datosPropios),
                datosPropios);
    }

    private AsistenteResponse.DatosPropios resumenPropio(Long usuarioId) {
        long totalPublicaciones = objetoRepository.countByUsuarioId(usuarioId);
        long activas = objetoRepository.countByUsuarioIdAndEstado(usuarioId, EstadoObjeto.ACTIVO);
        long enCustodia = objetoRepository.countByUsuarioIdAndEstado(usuarioId, EstadoObjeto.EN_CUSTODIA);
        long recuperadas = objetoRepository.countByUsuarioIdAndEstado(usuarioId, EstadoObjeto.RECUPERADO);

        long totalReclamos = reclamoRepository.countByUsuarioId(usuarioId);
        long pendientes = reclamoRepository.countByUsuarioIdAndEstado(usuarioId, "PENDIENTE");
        long aceptados = reclamoRepository.countByUsuarioIdAndEstado(usuarioId, "ACEPTADO");
        long retirados = reclamoRepository.countByUsuarioIdAndEstado(usuarioId, "RETIRADO");
        long rechazados = reclamoRepository.countByUsuarioIdAndEstado(usuarioId, "RECHAZADO");
        long qrVigentes = reclamoRepository.countQrRetiroVigentes(
                usuarioId, LocalDateTime.now(ZoneOffset.UTC));

        return new AsistenteResponse.DatosPropios(
                new AsistenteResponse.Publicaciones(totalPublicaciones, activas, enCustodia, recuperadas),
                new AsistenteResponse.Reclamos(totalReclamos, pendientes, aceptados, retirados, rechazados),
                qrVigentes);
    }

    private String resumenTexto(AsistenteResponse.DatosPropios datos) {
        StringBuilder texto = new StringBuilder();
        AsistenteResponse.Publicaciones publicaciones = datos.publicaciones();
        if (publicaciones.total() == 0) {
            texto.append("No tienes publicaciones registradas");
        } else {
            texto.append("Tienes ").append(publicaciones.total())
                    .append(publicaciones.total() == 1 ? " publicación" : " publicaciones")
                    .append(": ").append(publicaciones.activas())
                    .append(publicaciones.activas() == 1 ? " activa" : " activas").append(", ")
                    .append(publicaciones.enCustodia()).append(" en custodia y ")
                    .append(publicaciones.recuperadas()).append(" recuperada")
                    .append(publicaciones.recuperadas() == 1 ? "" : "s");
        }
        texto.append(". ");

        AsistenteResponse.Reclamos reclamos = datos.reclamos();
        if (reclamos.total() == 0) {
            texto.append("No tienes reclamos registrados");
        } else {
            texto.append("Tienes ").append(reclamos.total())
                    .append(reclamos.total() == 1 ? " reclamo" : " reclamos")
                    .append(": ").append(reclamos.pendientes()).append(" pendiente")
                    .append(reclamos.pendientes() == 1 ? "" : "s").append(", ")
                    .append(reclamos.aceptados()).append(" aceptado")
                    .append(reclamos.aceptados() == 1 ? "" : "s").append(" y ")
                    .append(reclamos.retirados()).append(" retirado")
                    .append(reclamos.retirados() == 1 ? "" : "s");
        }
        texto.append(".");

        if (datos.qrRetiroVigentes() > 0) {
            texto.append(" Tienes ").append(datos.qrRetiroVigentes())
                    .append(datos.qrRetiroVigentes() == 1
                            ? " QR de retiro vigente." : " QR de retiro vigentes.");
        }
        return texto.toString();
    }
}
