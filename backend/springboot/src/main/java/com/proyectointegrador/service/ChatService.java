package com.proyectointegrador.service;

import com.proyectointegrador.dto.ChatDtos.ConversacionResponse;
import com.proyectointegrador.dto.ChatDtos.IniciarRequest;
import com.proyectointegrador.dto.ChatDtos.MensajeResponse;
import com.proyectointegrador.dto.ChatDtos.MensajesResponse;
import com.proyectointegrador.dto.ChatDtos.Participante;
import com.proyectointegrador.entity.BloqueoChat;
import com.proyectointegrador.entity.Conversacion;
import com.proyectointegrador.entity.EstadoContacto;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.LecturaChat;
import com.proyectointegrador.entity.MensajeChat;
import com.proyectointegrador.entity.ReporteMensaje;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.TipoConversacion;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.exception.ChatNoDisponibleException;
import com.proyectointegrador.exception.LimiteAsistenteException;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.exception.SolicitudInvalidaException;
import com.proyectointegrador.repository.BloqueoChatRepository;
import com.proyectointegrador.repository.ContactoRepository;
import com.proyectointegrador.repository.ConversacionRepository;
import com.proyectointegrador.repository.LecturaChatRepository;
import com.proyectointegrador.repository.MensajeChatRepository;
import com.proyectointegrador.repository.ReclamoRepository;
import com.proyectointegrador.repository.ReporteMensajeRepository;
import com.proyectointegrador.repository.UsuarioRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * H55 a H63: conversaciones entre quien perdió y quien encontró, y entre quien reclama y la biblioteca.
 * Solo participan las personas de la conversación (y el personal de la biblioteca en las suyas).
 */
@Service
public class ChatService {

    public static final int MAXIMO_POR_MINUTO = 20;

    private final ConversacionRepository conversaciones;
    private final MensajeChatRepository mensajes;
    private final LecturaChatRepository lecturas;
    private final BloqueoChatRepository bloqueos;
    private final ReporteMensajeRepository reportes;
    private final ContactoRepository contactos;
    private final ReclamoRepository reclamos;
    private final UsuarioRepository usuarios;
    private final NotificacionService notificaciones;
    private final AlmacenamientoImagenService imagenes;

    public ChatService(ConversacionRepository conversaciones, MensajeChatRepository mensajes,
                       LecturaChatRepository lecturas, BloqueoChatRepository bloqueos,
                       ReporteMensajeRepository reportes, ContactoRepository contactos,
                       ReclamoRepository reclamos, UsuarioRepository usuarios,
                       NotificacionService notificaciones, AlmacenamientoImagenService imagenes) {
        this.conversaciones = conversaciones;
        this.mensajes = mensajes;
        this.lecturas = lecturas;
        this.bloqueos = bloqueos;
        this.reportes = reportes;
        this.contactos = contactos;
        this.reclamos = reclamos;
        this.usuarios = usuarios;
        this.notificaciones = notificaciones;
        this.imagenes = imagenes;
    }

    private static boolean esPersonal(Usuario u) {
        return u.getRol() == Rol.BIBLIOTECA || u.getRol() == Rol.ADMIN;
    }

    private Usuario usuario(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    @Transactional
    public ConversacionResponse iniciar(IniciarRequest req, Long usuarioId) {
        boolean porContacto = req.contactoId() != null;
        boolean porReclamo = req.reclamoId() != null;
        if (porContacto == porReclamo) {
            throw new SolicitudInvalidaException("Indica contactoId o reclamoId, pero solo uno.");
        }
        Usuario yo = usuario(usuarioId);
        if (porContacto) {
            var contacto = contactos.findById(req.contactoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Contacto no encontrado"));
            Long emisor = contacto.getUsuarioEmisor().getId();
            Long receptor = contacto.getUsuarioReceptor().getId();
            if (!usuarioId.equals(emisor) && !usuarioId.equals(receptor)) {
                throw new AccessDeniedException("Esta conversación no es tuya");
            }
            var existente = conversaciones.findByContactoId(contacto.getId());
            if (existente.isPresent()) return respuesta(existente.get(), yo);
            if (contacto.getEstado() == EstadoContacto.RECHAZADO || contacto.getEstado() == EstadoContacto.CERRADO) {
                throw new ChatNoDisponibleException("El contacto ya no está activo");
            }
            Conversacion c = new Conversacion();
            c.setTipo(TipoConversacion.ENTRE_USUARIOS);
            c.setObjeto(contacto.getCoincidencia().getObjetoEncontrado());
            c.setContacto(contacto);
            c.setUsuarioA(contacto.getUsuarioEmisor());
            c.setUsuarioB(contacto.getUsuarioReceptor());
            return respuesta(conversaciones.saveAndFlush(c), yo);
        }
        var reclamo = reclamos.findById(req.reclamoId())
                .orElseThrow(() -> new ResourceNotFoundException("Reclamo no encontrado"));
        if (!reclamo.getUsuario().getId().equals(usuarioId)) {
            throw new AccessDeniedException("Esta conversación no es tuya");
        }
        var existente = conversaciones.findByReclamoId(reclamo.getId());
        if (existente.isPresent()) return respuesta(existente.get(), yo);
        Conversacion c = new Conversacion();
        c.setTipo(TipoConversacion.CON_BIBLIOTECA);
        c.setObjeto(reclamo.getObjeto());
        c.setReclamo(reclamo);
        c.setUsuarioA(reclamo.getUsuario());
        return respuesta(conversaciones.saveAndFlush(c), yo);
    }

    /** Lista las conversaciones propias; el personal de la biblioteca ve además la bandeja de reclamos. */
    @Transactional(readOnly = true)
    public List<ConversacionResponse> listar(Long usuarioId) {
        Usuario yo = usuario(usuarioId);
        List<Conversacion> lista = esPersonal(yo)
                ? conversaciones.findTop100ByTipoOrderByIdDesc(TipoConversacion.CON_BIBLIOTECA)
                : conversaciones.delUsuario(usuarioId);
        return lista.stream().map(c -> respuesta(c, yo)).toList();
    }

    @Transactional(readOnly = true)
    public ConversacionResponse obtener(Long id, Long usuarioId) {
        Usuario yo = usuario(usuarioId);
        return respuesta(propia(id, yo), yo);
    }

    @Transactional(readOnly = true)
    public MensajesResponse leer(Long id, Long usuarioId, long despuesDe) {
        if (despuesDe < 0) throw new SolicitudInvalidaException("El cursor no puede ser negativo.");
        Usuario yo = usuario(usuarioId);
        Conversacion c = propia(id, yo);
        var lista = mensajes.findTop50ByConversacionIdAndIdGreaterThanOrderByIdAsc(id, despuesDe)
                .stream().map(this::mensaje).toList();
        long ultimo = lista.isEmpty() ? despuesDe : lista.get(lista.size() - 1).id();
        return new MensajesResponse(lista, ultimo, soloLectura(c), bloqueada(c, yo));
    }

    @Transactional
    public MensajeResponse enviar(Long id, Long usuarioId, String texto, MultipartFile foto) {
        Usuario yo = usuario(usuarioId);
        Conversacion c = propia(id, yo);
        if (soloLectura(c)) {
            throw new ChatNoDisponibleException("La conversación ya terminó y está en solo lectura");
        }
        if (bloqueada(c, yo)) {
            throw new ChatNoDisponibleException("No puedes escribir en esta conversación porque hay un bloqueo");
        }
        String limpio = texto == null ? null : texto.trim();
        boolean hayFoto = foto != null && !foto.isEmpty();
        if ((limpio == null || limpio.isEmpty()) && !hayFoto) {
            throw new SolicitudInvalidaException("Escribe un mensaje o adjunta una foto.");
        }
        if (limpio != null && limpio.length() > 1000) {
            throw new SolicitudInvalidaException("El mensaje no puede pasar de 1000 caracteres.");
        }
        long recientes = mensajes.countByAutorIdAndEnviadoEnAfter(usuarioId,
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        if (recientes >= MAXIMO_POR_MINUTO) {
            throw new LimiteAsistenteException("Estás enviando muchos mensajes seguidos. Espera un momento.", 30);
        }
        MensajeChat m = new MensajeChat();
        m.setConversacion(c);
        m.setAutor(yo);
        m.setTexto(limpio == null || limpio.isEmpty() ? null : limpio);
        if (hayFoto) m.setImagenUrl(imagenes.guardar(foto).url());
        MensajeChat guardado = mensajes.saveAndFlush(m);
        marcarLeido(c.getId(), usuarioId, guardado.getId());
        for (Usuario destino : destinatarios(c, yo)) {
            notificaciones.notificarMensajeChat(destino, c.getId());
        }
        return mensaje(guardado);
    }

    @Transactional
    public void marcarLeida(Long id, Long usuarioId) {
        Usuario yo = usuario(usuarioId);
        Conversacion c = propia(id, yo);
        long ultimo = mensajes.findTopByConversacionIdOrderByIdDesc(c.getId()).map(MensajeChat::getId).orElse(0L);
        marcarLeido(c.getId(), usuarioId, ultimo);
    }

    @Transactional
    public ConversacionResponse bloquear(Long id, Long usuarioId) {
        Usuario yo = usuario(usuarioId);
        Conversacion c = propia(id, yo);
        Usuario otro = otro(c, yo);
        if (otro == null || esPersonal(otro) || esPersonal(yo)) {
            throw new SolicitudInvalidaException("Solo se puede bloquear a otra persona usuaria.");
        }
        if (!bloqueos.existsByBloqueadorIdAndBloqueadoId(usuarioId, otro.getId())) {
            BloqueoChat b = new BloqueoChat();
            b.setBloqueadorId(usuarioId);
            b.setBloqueadoId(otro.getId());
            bloqueos.saveAndFlush(b);
        }
        return respuesta(c, yo);
    }

    @Transactional
    public ConversacionResponse desbloquear(Long id, Long usuarioId) {
        Usuario yo = usuario(usuarioId);
        Conversacion c = propia(id, yo);
        Usuario otro = otro(c, yo);
        if (otro != null) {
            bloqueos.findByBloqueadorIdAndBloqueadoId(usuarioId, otro.getId()).ifPresent(bloqueos::delete);
        }
        return respuesta(c, yo);
    }

    /** H56: se denuncia un mensaje ajeno; repetir la denuncia no crea otra. */
    @Transactional
    public void reportar(Long id, Long mensajeId, Long usuarioId, String motivo) {
        Usuario yo = usuario(usuarioId);
        Conversacion c = propia(id, yo);
        MensajeChat m = mensajes.findByIdAndConversacionId(mensajeId, c.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Mensaje no encontrado"));
        if (m.getAutor().getId().equals(usuarioId)) {
            throw new SolicitudInvalidaException("No puedes reportar tus propios mensajes.");
        }
        if (reportes.existsByMensajeIdAndUsuarioId(m.getId(), usuarioId)) return;
        ReporteMensaje r = new ReporteMensaje();
        r.setMensajeId(m.getId());
        r.setUsuarioId(usuarioId);
        r.setMotivo(motivo.trim());
        reportes.saveAndFlush(r);
    }

    // --- reglas ---

    private Conversacion propia(Long id, Usuario yo) {
        Conversacion c = conversaciones.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversación no encontrada"));
        boolean participante = c.getUsuarioA().getId().equals(yo.getId())
                || (c.getUsuarioB() != null && c.getUsuarioB().getId().equals(yo.getId()));
        boolean personal = c.getTipo() == TipoConversacion.CON_BIBLIOTECA && esPersonal(yo);
        if (!participante && !personal) {
            // Se responde igual que si no existiera para no revelar conversaciones ajenas.
            throw new ResourceNotFoundException("Conversación no encontrada");
        }
        return c;
    }

    /** H61: cuando el objeto ya no está en juego o el reclamo se cerró, solo se puede leer. */
    private boolean soloLectura(Conversacion c) {
        if (c.getEstado() != null && c.getEstado().name().equals("SOLO_LECTURA")) return true;
        if (terminado(c.getObjeto().getEstado())) return true;
        if (c.getReclamo() != null) {
            // Estados reales del reclamo: PENDIENTE, ACEPTADO, RECHAZADO y RETIRADO
            String r = c.getReclamo().getEstado();
            return "RECHAZADO".equals(r) || "RETIRADO".equals(r);
        }
        if (c.getContacto() == null) return false;
        if (c.getContacto().getEstado() == EstadoContacto.RECHAZADO
                || c.getContacto().getEstado() == EstadoContacto.CERRADO) return true;
        // La conversación apunta al objeto encontrado, pero al confirmar la recuperación (H22)
        // el que pasa a RECUPERADO es el perdido: también termina la conversación.
        return terminado(c.getContacto().getCoincidencia().getObjetoPerdido().getEstado());
    }

    private static boolean terminado(EstadoObjeto e) {
        return e == EstadoObjeto.RECUPERADO || e == EstadoObjeto.ELIMINADO || e == EstadoObjeto.OCULTO;
    }

    private boolean bloqueada(Conversacion c, Usuario yo) {
        Usuario otro = otro(c, yo);
        if (otro == null) return false;
        return bloqueos.existsByBloqueadorIdAndBloqueadoId(yo.getId(), otro.getId())
                || bloqueos.existsByBloqueadorIdAndBloqueadoId(otro.getId(), yo.getId());
    }

    /** La otra persona de una conversación entre usuarios; en la biblioteca, nadie en concreto. */
    private Usuario otro(Conversacion c, Usuario yo) {
        if (c.getUsuarioB() == null) return c.getUsuarioA().getId().equals(yo.getId()) ? null : c.getUsuarioA();
        return c.getUsuarioA().getId().equals(yo.getId()) ? c.getUsuarioB() : c.getUsuarioA();
    }

    private List<Usuario> destinatarios(Conversacion c, Usuario autor) {
        if (c.getTipo() == TipoConversacion.ENTRE_USUARIOS) return List.of(otro(c, autor));
        // Con la biblioteca: si escribe el personal avisa a quien reclamó; si escribe la persona, lo ve la bandeja.
        return esPersonal(autor) && !c.getUsuarioA().getId().equals(autor.getId())
                ? List.of(c.getUsuarioA()) : List.of();
    }

    private void marcarLeido(Long conversacionId, Long usuarioId, long hasta) {
        LecturaChat l = lecturas.findByConversacionIdAndUsuarioId(conversacionId, usuarioId).orElseGet(() -> {
            LecturaChat nueva = new LecturaChat();
            nueva.setConversacionId(conversacionId);
            nueva.setUsuarioId(usuarioId);
            return nueva;
        });
        if (hasta > l.getUltimoMensajeId()) l.setUltimoMensajeId(hasta);
        lecturas.save(l);
    }

    // --- respuestas ---

    private MensajeResponse mensaje(MensajeChat m) {
        return new MensajeResponse(m.getId(), m.getAutor().getId(), m.getAutor().getNombre(), m.getTexto(),
                m.getImagenUrl(), m.getEnviadoEn());
    }

    private ConversacionResponse respuesta(Conversacion c, Usuario yo) {
        Usuario otro = otro(c, yo);
        Participante participante = c.getTipo() == TipoConversacion.CON_BIBLIOTECA && !esPersonal(yo)
                ? new Participante(null, "Biblioteca")
                : otro == null ? null : new Participante(otro.getId(), otro.getNombre());
        long leido = lecturas.findByConversacionIdAndUsuarioId(c.getId(), yo.getId())
                .map(LecturaChat::getUltimoMensajeId).orElse(0L);
        long sinLeer = mensajes.countByConversacionIdAndIdGreaterThanAndAutorIdNot(c.getId(), leido, yo.getId());
        MensajeResponse ultimo = mensajes.findTopByConversacionIdOrderByIdDesc(c.getId()).map(this::mensaje).orElse(null);
        boolean lectura = soloLectura(c);
        return new ConversacionResponse(c.getId(), c.getTipo().name(), c.getObjeto().getId(), c.getObjeto().getNombre(),
                lectura ? "SOLO_LECTURA" : "ABIERTA", lectura, bloqueada(c, yo), participante, ultimo, sinLeer);
    }
}
