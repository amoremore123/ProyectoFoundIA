package com.proyectointegrador.controller;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class CategoriasPublicacionMySqlQa extends CategoriasPublicacionIntegrationTest {
    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry propiedades) {
        MySqlQaProperties.registrar(propiedades);
    }
}
