package com.proyectointegrador.service;

import com.proyectointegrador.exception.EnvioCorreoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** H11 - Envío del código de confirmación de cuenta. */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String remitente;

    public EmailService(JavaMailSender mailSender, @Value("${app.mail.from}") String remitente) {
        this.mailSender = mailSender;
        this.remitente = remitente;
    }

    /** H35: enlace de un solo uso para definir una contraseña nueva. */
    public void enviarEnlaceRecuperacion(String destinatario, String nombre, String enlace, long minutosVigencia) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(destinatario);
        mensaje.setSubject("FoundIA | Restablece tu contraseña");
        mensaje.setText("""
                Hola %s,

                Recibimos una solicitud para restablecer la contraseña de tu cuenta de FoundIA.
                Abre este enlace para definir una contraseña nueva:

                %s

                El enlace vence en %d minutos y solo se puede usar una vez.
                Si no lo solicitaste, ignora este correo: tu contraseña no cambiará.

                Equipo FoundIA
                """.formatted(nombre, enlace, minutosVigencia));
        try {
            mailSender.send(mensaje);
        } catch (MailException ex) {
            log.error("No se pudo enviar el enlace de recuperación de contraseña", ex);
            throw new EnvioCorreoException("No pudimos enviar el enlace de recuperación.");
        }
    }

    /** H68: aviso de la bandeja enviado por correo, para quien eligió ese canal. */
    public void enviarAviso(String destinatario, String nombre, String titulo, String texto, String enlace) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(destinatario);
        mensaje.setSubject("FoundIA | " + titulo);
        mensaje.setText("""
                Hola %s,

                %s

                Míralo en FoundIA: %s

                Recibes este correo porque activaste los avisos por correo. Puedes apagarlos en
                Notificaciones > Preferencias; los avisos seguirán en tu bandeja.

                Equipo FoundIA
                """.formatted(nombre, texto, enlace));
        try {
            mailSender.send(mensaje);
        } catch (MailException ex) {
            log.warn("No se pudo enviar un aviso por correo: {}", ex.getClass().getSimpleName());
            throw new EnvioCorreoException("No pudimos enviar el aviso por correo.");
        }
    }

    public void enviarCodigoVerificacion(String destinatario, String nombre, String codigo, long minutosVigencia) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(destinatario);
        mensaje.setSubject("ENCUENTRA+ | Tu código de verificación: " + codigo);
        mensaje.setText("""
                Hola %s,

                Gracias por registrarte en ENCUENTRA+.
                Tu código de verificación es:

                    %s

                El código vence en %d minutos.
                Si no creaste esta cuenta, ignora este correo.

                Equipo ENCUENTRA+
                """.formatted(nombre, codigo, minutosVigencia));
        try {
            mailSender.send(mensaje);
        } catch (MailException ex) {
            log.error("No se pudo enviar el código de verificación a {}", destinatario, ex);
            throw new EnvioCorreoException(
                    "No pudimos enviar el código de verificación. Intenta de nuevo en unos minutos.");
        }
    }
}
