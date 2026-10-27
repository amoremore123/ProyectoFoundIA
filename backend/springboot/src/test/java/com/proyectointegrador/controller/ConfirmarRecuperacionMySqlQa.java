package com.proyectointegrador.controller;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Ejecución explícita en una base nueva y marcada, nunca contra la base compartida. */
class ConfirmarRecuperacionMySqlQa extends ConfirmarRecuperacionIntegrationTest {
    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry properties) {
        MySqlQaProperties.registrar(properties);
    }
}
