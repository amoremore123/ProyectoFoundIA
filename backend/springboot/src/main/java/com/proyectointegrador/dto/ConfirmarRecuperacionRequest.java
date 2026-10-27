package com.proyectointegrador.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record ConfirmarRecuperacionRequest(
        @NotNull(message = "Debes confirmar que recuperaste el objeto")
        @AssertTrue(message = "Debes confirmar que recuperaste el objeto")
        Boolean confirmarRecuperacion) {
}
