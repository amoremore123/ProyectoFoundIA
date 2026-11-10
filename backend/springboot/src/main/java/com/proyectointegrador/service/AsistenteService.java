package com.proyectointegrador.service;

import com.proyectointegrador.dto.AsistenteResponse;
import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.repository.CategoriaRepository;
import com.proyectointegrador.repository.ObjetoRepository;
import com.proyectointegrador.repository.ReclamoRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

        // H47: tarjetas y acciones del catálogo cerrado, armadas con datos de la base
        List<AsistenteResponse.Tarjeta> tarjetas = new ArrayList<>();
        List<AsistenteResponse.Accion> acciones = new ArrayList<>();
        Optional<Objeto> referido = objetoReferido(mensaje);
        referido.ifPresent(o -> tarjetas.add(tarjetaObjeto(o)));
        if ("publicar".equals(interpretacion.intencion())) {
            acciones.add(prellenarPublicar(interpretacion));
        }
        if (usuarioId != null && referido.isPresent() && reclamable(referido.get(), usuarioId)) {
            acciones.add(abrirReclamo(referido.get()));
        }
        if (datosPropios != null && hablaDeReclamos(mensaje, interpretacion.intencion())) {
            AsistenteResponse.Reclamos r = datosPropios.reclamos();
            if (r.total() > 0) {
                Map<String, Object> datos = new LinkedHashMap<>();
                datos.put("pendientes", r.pendientes());
                datos.put("aceptados", r.aceptados());
                datos.put("retirados", r.retirados());
                datos.put("rechazados", r.rechazados());
                tarjetas.add(new AsistenteResponse.Tarjeta(AsistenteCatalogo.TARJETA_RECLAMOS, "Tus reclamos", datos,
                        "/mis-publicaciones"));
            }
            if (datosPropios.qrRetiroVigentes() > 0) {
                tarjetas.add(new AsistenteResponse.Tarjeta(AsistenteCatalogo.TARJETA_QR, "QR de retiro vigentes",
                        Map.of("vigentes", datosPropios.qrRetiroVigentes()), "/mis-publicaciones"));
            }
        }

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
                datosPropios,
                tarjetas.isEmpty() ? null : List.copyOf(tarjetas),
                acciones.isEmpty() ? null : List.copyOf(acciones));
    }

    // H47: "#12" o "publicación 12" en el mensaje. Solo se muestran publicaciones visibles.
    private static final Pattern REFERENCIA = Pattern.compile("(?:#|publicaci[oó]n\\s+)(\\d{1,9})\\b", Pattern.CASE_INSENSITIVE);

    private Optional<Objeto> objetoReferido(String mensaje) {
        if (mensaje == null) return Optional.empty();
        Matcher m = REFERENCIA.matcher(mensaje);
        if (!m.find()) return Optional.empty();
        return objetoRepository.findById(Long.parseLong(m.group(1)))
                .filter(o -> o.getEstado() == EstadoObjeto.ACTIVO || o.getEstado() == EstadoObjeto.EN_CUSTODIA);
    }

    private static AsistenteResponse.Tarjeta tarjetaObjeto(Objeto o) {
        // Sin datos de quien publicó: solo lo que ya muestra el detalle público
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", o.getId());
        datos.put("tipo", o.getTipo() == null ? null : o.getTipo().name());
        datos.put("estado", o.getEstado() == null ? null : o.getEstado().name());
        datos.put("ubicacion", o.getUbicacion());
        datos.put("categoria", o.getCategoria() == null ? null : o.getCategoria().getNombre());
        datos.values().removeIf(v -> v == null);
        return new AsistenteResponse.Tarjeta(AsistenteCatalogo.TARJETA_OBJETO, o.getNombre(), datos, "/objeto/" + o.getId());
    }

    private static boolean reclamable(Objeto o, Long usuarioId) {
        return o.getTipo() == TipoObjeto.ENCONTRADO
                && (o.getEstado() == EstadoObjeto.ACTIVO || o.getEstado() == EstadoObjeto.EN_CUSTODIA)
                && o.getUsuario() != null && !usuarioId.equals(o.getUsuario().getId());
    }

    private static AsistenteResponse.Accion abrirReclamo(Objeto o) {
        return new AsistenteResponse.Accion(AsistenteCatalogo.ACCION_ABRIR_RECLAMO, "Reclamar este objeto",
                "¿Abrir «" + o.getNombre() + "» para reclamarlo? Antes de enviar el reclamo te pediremos confirmarlo.",
                "/objeto/" + o.getId(), Map.of("objetoId", o.getId()), true);
    }

    private static AsistenteResponse.Accion prellenarPublicar(InterpretacionAsistente i) {
        // Quien busca un objeto ENCONTRADO es porque lo perdió, y al revés
        String tipo = switch (i.tipoBuscado() == null ? "" : i.tipoBuscado()) {
            case "ENCONTRADO" -> "PERDIDO";
            case "PERDIDO" -> "ENCONTRADO";
            default -> null;
        };
        Map<String, Object> datos = new LinkedHashMap<>();
        if (tipo != null) datos.put("tipo", tipo);
        if (i.categoriaId() != null) datos.put("categoriaId", i.categoriaId());
        if (i.ubicacion() != null && !i.ubicacion().isBlank()) datos.put("ubicacion", i.ubicacion());
        return new AsistenteResponse.Accion(AsistenteCatalogo.ACCION_PRELLENAR_PUBLICAR, "Abrir Publicar con estos datos",
                "¿Abrir el formulario de Publicar con los datos que entendí? Podrás revisarlos y cambiarlos antes de publicar.",
                "/publicar", datos, true);
    }

    private static boolean hablaDeReclamos(String mensaje, String intencion) {
        if ("mis_reportes".equals(intencion)) return true;
        String t = AsistenteLexico.normalizar(mensaje == null ? "" : mensaje);
        return t.contains("reclam") || t.contains("retiro") || t.matches(".*\\bqr\\b.*");
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
