package com.proyectointegrador.controller;
import org.springframework.test.context.*;
class AvisosCoincidenciaMySqlQa extends AvisosCoincidenciaIntegrationTest {
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry p) { MySqlQaProperties.registrar(p); }
}
