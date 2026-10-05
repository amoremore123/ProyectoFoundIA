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
