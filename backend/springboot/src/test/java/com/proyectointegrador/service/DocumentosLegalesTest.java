package com.proyectointegrador.service;
import com.proyectointegrador.dto.RegisterRequest;
import com.proyectointegrador.exception.SolicitudInvalidaException;
import org.junit.jupiter.api.Test;
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
}
