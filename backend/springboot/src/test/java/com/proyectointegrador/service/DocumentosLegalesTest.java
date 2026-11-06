package com.proyectointegrador.service;
import com.proyectointegrador.dto.RegisterRequest;
import com.proyectointegrador.exception.SolicitudInvalidaException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.*;
class DocumentosLegalesTest {
    @Test void exigeAceptacionExpresaYVersionPresentada() {
        for (Boolean acepta : new Boolean[]{null,false})
            assertThatThrownBy(() -> DocumentosLegales.validar(new RegisterRequest("Ana","QA","ana@qa.invalid","Segura123!",acepta,DocumentosLegales.VERSION)))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> DocumentosLegales.validar(new RegisterRequest("Ana","QA","ana@qa.invalid","Segura123!",true,"anterior")))
            .isInstanceOf(SolicitudInvalidaException.class);
        assertThatCode(() -> DocumentosLegales.validar(new RegisterRequest("Ana","QA","ana@qa.invalid","Segura123!",true,DocumentosLegales.VERSION)))
            .doesNotThrowAnyException();
    }

    @Test void publicaLasDosFuentesOficialesSinAtribuirAprobacionDelProyecto() {
        var docs = DocumentosLegales.documentos();
        assertThat(docs.version()).isEqualTo("foundia-tecsup-2026-11-05-v2");
        assertThat(docs.estado()).isEqualTo("FUENTES_OFICIALES_TECSUP_CON_AVISO_FOUNDIA");
        assertThat(docs.aviso()).contains("no autoriza publicidad", "ni acredita aprobación institucional");
        assertThat(docs.fuentes()).extracting(DocumentosLegales.Fuente::url).containsExactly(
            "https://www.tecsup.edu.pe/terminos-y-condiciones/",
            "https://www.tecsup.edu.pe/wp-content/uploads/2024/08/politica-tratamiento-personales-tecsup.pdf");
        for (var fuente : docs.fuentes()) {
            assertThat(fuente.consultadoEl()).isEqualTo("2026-11-05");
            assertThat(fuente.sha256Original()).matches("[a-f0-9]{64}");
            assertThat(fuente.sha256Texto()).matches("[a-f0-9]{64}");
        }
        assertThatThrownBy(() -> docs.fuentes().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void transcripcionesCompletasYMetadatosFormanParteDeLaHuellaAceptada() throws Exception {
        var docs = DocumentosLegales.documentos();
        assertThat(docs.terminos()).contains("Autorizo a TECSUP", "Artículo 6", "no otorga las autorizaciones de publicidad");
        assertThat(docs.privacidad()).contains("VIII.", "No cumplir con las disposiciones", "datospersonales@tecsup.edu.pe",
            "no remite automáticamente solicitudes ni datos a ese correo");
        for (var fuente : docs.fuentes()) {
            String documento = fuente.id().equals("terminos-tecsup") ? docs.terminos() : docs.privacidad();
            assertThat(documento).contains(fuente.url(), fuente.consultadoEl(), fuente.sha256Original(), fuente.sha256Texto());
            try (var stream = new ClassPathResource("legal/" + docs.version() + "/fuentes/" + fuente.id() + ".txt").getInputStream()) {
                assertThat(documento).contains(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest((docs.terminos() + "\n" + docs.privacidad()).getBytes(StandardCharsets.UTF_8)));
        assertThat(docs.sha256()).isEqualTo(hash);
    }

    @Test void conservaLaVersionAnteriorPeroNoLaAceptaParaUnRegistroNuevo() throws Exception {
        try (var stream = new ClassPathResource("legal/proyecto-2026-10-26-v1/terminos.md").getInputStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).contains("proyecto-2026-10-26-v1");
        }
        assertThatThrownBy(() -> DocumentosLegales.validar(new RegisterRequest("Ana", "QA", "ana@qa.invalid", "Segura123!",
            true, "proyecto-2026-10-26-v1"))).isInstanceOf(SolicitudInvalidaException.class);
    }
}
