package com.proyectointegrador.controller;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.sql.DriverManager;

/** Solo bases QA nuevas, marcadas por el ejecutor; nunca la conexión normal de la aplicación. */
final class MySqlQaProperties {
    private MySqlQaProperties() {
    }

    static void registrar(DynamicPropertyRegistry properties) {
        String puerto = requerida("FOUNDIA_QA_MYSQL_PORT");
        String base = requerida("FOUNDIA_QA_MYSQL_DATABASE");
        String password = requerida("FOUNDIA_QA_MYSQL_PASSWORD");
        String token = requerida("FOUNDIA_QA_MYSQL_TOKEN");
        int numeroPuerto = Integer.parseInt(puerto);
        if (numeroPuerto <= 1024 || numeroPuerto > 65535 || numeroPuerto == 3306
                || !base.matches("foundia_s2_qa_[a-f0-9]{12}")) {
            throw new IllegalStateException("Se requiere una base QA nueva y un puerto aislado");
        }
        String url = "jdbc:mysql://127.0.0.1:" + puerto + "/" + base
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
        try (var conexion = DriverManager.getConnection(url, "foundia_qa", password);
             var sentencia = conexion.createStatement();
             var resultado = sentencia.executeQuery("select token from foundia_qa_guard")) {
            if (!resultado.next() || !token.equals(resultado.getString(1)) || resultado.next()) {
                throw new IllegalStateException("La base no pertenece a esta ejecución QA");
            }
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException("No se pudo verificar la base exclusiva de QA", ex);
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add("spring.datasource.username", () -> "foundia_qa");
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
    }

    private static String requerida(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta " + nombre + "; no se usa la base por defecto");
        }
        return valor;
    }
}
