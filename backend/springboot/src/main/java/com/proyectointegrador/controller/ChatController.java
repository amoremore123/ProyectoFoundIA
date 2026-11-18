package com.proyectointegrador.controller;

import com.proyectointegrador.dto.ChatDtos.ConversacionResponse;
import com.proyectointegrador.dto.ChatDtos.EnviarRequest;
import com.proyectointegrador.dto.ChatDtos.IniciarRequest;
import com.proyectointegrador.dto.ChatDtos.MensajeResponse;
import com.proyectointegrador.dto.ChatDtos.MensajesResponse;
import com.proyectointegrador.dto.ChatDtos.ReportarRequest;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/conversaciones")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping
    public ResponseEntity<ConversacionResponse> iniciar(@RequestBody IniciarRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(chat.iniciar(request, yo()));
    }

    @GetMapping
    public List<ConversacionResponse> listar() {
        return chat.listar(yo());
    }

    @GetMapping("/{id}")
    public ConversacionResponse obtener(@PathVariable Long id) {
        return chat.obtener(id, yo());
    }

    @GetMapping("/{id}/mensajes")
    public MensajesResponse mensajes(@PathVariable Long id, @RequestParam(defaultValue = "0") long despuesDe) {
        return chat.leer(id, yo(), despuesDe);
    }

    @PostMapping(value = "/{id}/mensajes", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MensajeResponse> enviar(@PathVariable Long id, @Valid @RequestBody EnviarRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chat.enviar(id, yo(), request.texto(), null));
    }

    /** H62: mensaje con foto (y texto opcional). */
    @PostMapping(value = "/{id}/mensajes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MensajeResponse> enviarConFoto(@PathVariable Long id,
                                                         @RequestParam(required = false) String texto,
                                                         @RequestPart("foto") MultipartFile foto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chat.enviar(id, yo(), texto, foto));
    }

    @PostMapping("/{id}/leida")
    public ResponseEntity<Void> leida(@PathVariable Long id) {
        chat.marcarLeida(id, yo());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/bloqueo")
    public ConversacionResponse bloquear(@PathVariable Long id) {
        return chat.bloquear(id, yo());
    }

    @DeleteMapping("/{id}/bloqueo")
    public ConversacionResponse desbloquear(@PathVariable Long id) {
        return chat.desbloquear(id, yo());
    }

    @PostMapping("/{id}/mensajes/{mensajeId}/reporte")
    public ResponseEntity<Void> reportar(@PathVariable Long id, @PathVariable Long mensajeId,
                                         @Valid @RequestBody ReportarRequest request) {
        chat.reportar(id, mensajeId, yo(), request.motivo());
        return ResponseEntity.noContent().build();
    }

    private Long yo() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new InvalidCredentialsException("No autenticado");
        }
        return principal.getId();
    }
}
