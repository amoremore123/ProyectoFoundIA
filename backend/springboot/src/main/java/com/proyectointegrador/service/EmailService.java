package com.proyectointegrador.service;

import com.proyectointegrador.exception.EnvioCorreoException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;

/**
 * Correos de FoundIA: código de verificación (H11), enlace de recuperación (H35) y avisos (H68).
 * Cada correo va en HTML con el logo de FoundIA y el pie «Equipo FoundIA», y lleva también su versión
 * en texto para los programas que no muestran HTML. El logo viaja dentro del correo (cid:logo-foundia),
 * así se ve aunque el servidor de FoundIA no esté al alcance de quien lo abre.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    static final String CID_LOGO = "logo-foundia";
    private static final ClassPathResource LOGO = new ClassPathResource("correo/logo-foundia.png");
    private static final String MARINO = "#12355B";
    private static final String AZUL = "#1F6FEB";

    private final JavaMailSender mailSender;
    private final String remitente;

    public EmailService(JavaMailSender mailSender, @Value("${app.mail.from}") String remitente) {
        this.mailSender = mailSender;
        this.remitente = remitente;
    }

    /** H35: enlace de un solo uso para definir una contraseña nueva. */
    public void enviarEnlaceRecuperacion(String destinatario, String nombre, String enlace, long minutosVigencia) {
        String texto = """
                Hola %s,

                Recibimos una solicitud para restablecer la contraseña de tu cuenta de FoundIA.
                Abre este enlace para definir una contraseña nueva:

                %s

                El enlace vence en %d minutos y solo se puede usar una vez.
                Si no lo solicitaste, ignora este correo: tu contraseña no cambiará.

                Equipo FoundIA
                """.formatted(nombre, enlace, minutosVigencia);
        String cuerpo = saludo(nombre)
                + parrafo("Recibimos una solicitud para restablecer la contraseña de tu cuenta de FoundIA.")
                + boton("Restablecer contraseña", enlace)
                + nota("El enlace vence en " + minutosVigencia + " minutos y solo se puede usar una vez. "
                        + "Si no lo solicitaste, ignora este correo: tu contraseña no cambiará.");
        try {
            enviar(destinatario, "FoundIA | Restablece tu contraseña", texto, cuerpo);
        } catch (MailException | MessagingException ex) {
            log.error("No se pudo enviar el enlace de recuperación de contraseña", ex);
            throw new EnvioCorreoException("No pudimos enviar el enlace de recuperación.");
        }
    }

    /** H68: aviso de la bandeja enviado por correo, para quien eligió ese canal. */
    public void enviarAviso(String destinatario, String nombre, String titulo, String texto, String enlace) {
        String plano = """
                Hola %s,

                %s

                Míralo en FoundIA: %s

                Recibes este correo porque activaste los avisos por correo. Puedes apagarlos en
                Notificaciones > Preferencias; los avisos seguirán en tu bandeja.

                Equipo FoundIA
                """.formatted(nombre, texto, enlace);
        String cuerpo = saludo(nombre)
                + parrafo(texto)
                + boton("Ver en FoundIA", enlace)
                + nota("Recibes este correo porque activaste los avisos por correo. Puedes apagarlos en "
                        + "Notificaciones > Preferencias; los avisos seguirán en tu bandeja.");
        try {
            enviar(destinatario, "FoundIA | " + titulo, plano, cuerpo);
        } catch (MailException | MessagingException ex) {
            log.warn("No se pudo enviar un aviso por correo: {}", ex.getClass().getSimpleName());
            throw new EnvioCorreoException("No pudimos enviar el aviso por correo.");
        }
    }

    public void enviarCodigoVerificacion(String destinatario, String nombre, String codigo, long minutosVigencia) {
        String texto = """
                Hola %s,

                Gracias por registrarte en FoundIA.
                Tu código de verificación es:

                    %s

                El código vence en %d minutos.
                Si no creaste esta cuenta, ignora este correo.

                Equipo FoundIA
                """.formatted(nombre, codigo, minutosVigencia);
        String cuerpo = saludo(nombre)
                + parrafo("Gracias por registrarte en FoundIA. Tu código de verificación es:")
                + """
                <div style="margin:18px 0;padding:14px;text-align:center;background:#EAF2FF;border:1px solid #B5D4F4;border-radius:8px;">\
                <span style="font-size:30px;font-weight:bold;letter-spacing:8px;color:%s;font-family:Arial,Helvetica,sans-serif;">%s</span>\
                </div>""".formatted(MARINO, e(codigo))
                + nota("El código vence en " + minutosVigencia + " minutos. Si no creaste esta cuenta, ignora este correo.");
        try {
            enviar(destinatario, "FoundIA | Tu código de verificación: " + codigo, texto, cuerpo);
        } catch (MailException | MessagingException ex) {
            log.error("No se pudo enviar el código de verificación a {}", destinatario, ex);
            throw new EnvioCorreoException(
                    "No pudimos enviar el código de verificación. Intenta de nuevo en unos minutos.");
        }
    }

    private void enviar(String destinatario, String asunto, String texto, String cuerpoHtml) throws MessagingException {
        MimeMessage mensaje = mailSender.createMimeMessage();
        MimeMessageHelper ayuda = new MimeMessageHelper(mensaje, true, StandardCharsets.UTF_8.name());
        ayuda.setFrom(remitente);
        ayuda.setTo(destinatario);
        ayuda.setSubject(asunto);
        ayuda.setText(texto, plantilla(cuerpoHtml));
        ayuda.addInline(CID_LOGO, LOGO, "image/png");
        mailSender.send(mensaje);
    }

    /** Tablas y estilos en línea: es lo que respetan Gmail y Outlook. */
    static String plantilla(String cuerpo) {
        return """
                <!DOCTYPE html>
                <html lang="es"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;padding:0;background:#F2F4F7;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#F2F4F7;padding:24px 12px;">
                <tr><td align="center">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#FFFFFF;border-radius:10px;overflow:hidden;border:1px solid #D0D5DD;">
                <tr><td style="padding:22px 28px;border-bottom:4px solid %s;">
                <img src="cid:%s" width="180" alt="FoundIA" style="display:block;width:180px;max-width:100%%;height:auto;border:0;">
                </td></tr>
                <tr><td style="padding:26px 28px;font-family:Arial,Helvetica,sans-serif;font-size:15px;line-height:1.6;color:#1D2939;">
                %s
                </td></tr>
                <tr><td style="padding:16px 28px;background:%s;font-family:Arial,Helvetica,sans-serif;font-size:13px;color:#FFFFFF;text-align:center;">
                Equipo FoundIA
                </td></tr>
                </table>
                </td></tr>
                </table>
                </body></html>
                """.formatted(MARINO, CID_LOGO, cuerpo, MARINO);
    }

    private static String saludo(String nombre) {
        return "<p style=\"margin:0 0 14px;\">Hola <strong>" + e(nombre) + "</strong>,</p>";
    }

    private static String parrafo(String texto) {
        return "<p style=\"margin:0 0 14px;\">" + e(texto).replace("\n", "<br>") + "</p>";
    }

    private static String nota(String texto) {
        return "<p style=\"margin:18px 0 0;font-size:13px;color:#667085;\">" + e(texto) + "</p>";
    }

    private static String boton(String etiqueta, String enlace) {
        return """
                <table role="presentation" cellpadding="0" cellspacing="0" style="margin:20px 0;"><tr>\
                <td style="background:%s;border-radius:8px;">\
                <a href="%s" style="display:inline-block;padding:12px 24px;font-family:Arial,Helvetica,sans-serif;font-size:15px;font-weight:bold;color:#FFFFFF;text-decoration:none;">%s</a>\
                </td></tr></table>\
                <p style="margin:0 0 6px;font-size:12px;color:#667085;">Si el botón no funciona, copia este enlace en tu navegador:<br>\
                <a href="%s" style="color:%s;word-break:break-all;">%s</a></p>""".formatted(
                AZUL, e(enlace), e(etiqueta), e(enlace), AZUL, e(enlace));
    }

    /** Nombres, títulos y textos vienen de los usuarios: se escapan para que no inyecten HTML. */
    private static String e(String texto) {
        return texto == null ? "" : HtmlUtils.htmlEscape(texto, StandardCharsets.UTF_8.name());
    }
}
