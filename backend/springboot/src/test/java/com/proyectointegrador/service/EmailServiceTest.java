package com.proyectointegrador.service;

import com.proyectointegrador.exception.EnvioCorreoException;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Correos con el logo de FoundIA y el pie «Equipo FoundIA»")
class EmailServiceTest {

    private JavaMailSender sender;
    private EmailService email;

    @BeforeEach
    void preparar() {
        sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
        email = new EmailService(sender, "no-reply@foundia.dev");
    }

    @Test
    void elCodigoVaEnHtmlConLogoPieYVersionEnTexto() throws Exception {
        email.enviarCodigoVerificacion("ana@foundia.dev", "Ana", "924588", 15);

        Correo correo = enviado();
        assertThat(correo.asunto).isEqualTo("FoundIA | Tu código de verificación: 924588");
        assertThat(correo.html).contains("cid:" + EmailService.CID_LOGO, "Equipo FoundIA", "924588", "Hola <strong>Ana</strong>");
        assertThat(correo.texto).contains("924588", "Equipo FoundIA").doesNotContain("<");
        assertThat(correo.logoAdjunto).isTrue();
    }

    @Test
    void laRecuperacionYElAvisoLlevanBotonConElEnlace() throws Exception {
        email.enviarEnlaceRecuperacion("ana@foundia.dev", "Ana", "https://foundia.test/restablecer?t=abc&x=1", 30);
        Correo recuperacion = enviado();
        assertThat(recuperacion.html).contains("Restablecer contraseña", "href=\"https://foundia.test/restablecer?t=abc&amp;x=1\"",
                "Equipo FoundIA", "cid:" + EmailService.CID_LOGO);

        email.enviarAviso("ana@foundia.dev", "Ana", "Nueva coincidencia", "Encontraron algo parecido a tu mochila.",
                "https://foundia.test/coincidencias/3-4");
        Correo aviso = ultimo();
        assertThat(aviso.asunto).isEqualTo("FoundIA | Nueva coincidencia");
        assertThat(aviso.html).contains("Ver en FoundIA", "Encontraron algo parecido a tu mochila.", "Equipo FoundIA");
        assertThat(aviso.logoAdjunto).isTrue();
    }

    @Test
    void losTextosDeLosUsuariosNoInyectanHtml() throws Exception {
        email.enviarAviso("ana@foundia.dev", "<b>Ana</b>", "Aviso", "<script>alert(1)</script>", "https://foundia.test/x");

        Correo correo = enviado();
        assertThat(correo.html).doesNotContain("<script>", "<b>Ana</b>")
                .contains("&lt;script&gt;", "&lt;b&gt;Ana&lt;/b&gt;");
    }

    @Test
    void siElServidorDeCorreoFallaSeAvisaConLaExcepcionDeSiempre() {
        doThrow(new MailSendException("sin SMTP")).when(sender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> email.enviarCodigoVerificacion("ana@foundia.dev", "Ana", "123456", 15))
                .isInstanceOf(EnvioCorreoException.class);
    }

    private Correo enviado() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
        return leer(captor.getAllValues().get(0));
    }

    private Correo ultimo() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
        List<MimeMessage> todos = captor.getAllValues();
        return leer(todos.get(todos.size() - 1));
    }

    private Correo leer(MimeMessage mensaje) throws Exception {
        mensaje.saveChanges();
        Correo correo = new Correo();
        correo.asunto = mensaje.getSubject();
        recorrer(mensaje, correo);
        return correo;
    }

    private void recorrer(Part parte, Correo correo) throws Exception {
        if (parte.isMimeType("text/html")) {
            correo.html = (String) parte.getContent();
        } else if (parte.isMimeType("text/plain")) {
            correo.texto = (String) parte.getContent();
        } else if (parte.isMimeType("image/png")) {
            String[] id = parte.getHeader("Content-ID");
            correo.logoAdjunto = id != null && id[0].contains(EmailService.CID_LOGO);
        } else if (parte.isMimeType("multipart/*")) {
            Multipart partes = (Multipart) parte.getContent();
            for (int i = 0; i < partes.getCount(); i++) {
                BodyPart hija = partes.getBodyPart(i);
                recorrer(hija, correo);
            }
        }
    }

    private static class Correo {
        String asunto;
        String html = "";
        String texto = "";
        boolean logoAdjunto;
    }
}
