package com.proyectointegrador.service;

import com.proyectointegrador.entity.Categoria;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * H45: valida la respuesta del proveedor de IA antes de usarla.
 *
 * Si la forma no es la esperada se descarta y el servicio usa el motor local.
 * Así una respuesta inventada no puede cambiar la intención ni inventar
 * categorías, tipos o ubicaciones fuera del catálogo real.
 */
@Component
public class AsistenteIaValidator {

    public Optional<InterpretacionAsistente> validar(IaInterpretacion ia, List<Categoria> categorias) {
        if (ia == null) {
            return Optional.empty();
        }

        String intencion = AsistenteLexico.normalizar(ia.intencion());
        if (!AsistenteLexico.INTENCIONES.contains(intencion)) {
            return Optional.empty();
        }

        String tipoBuscado = ia.tipoBuscado() == null
                ? "" : ia.tipoBuscado().trim().toUpperCase(Locale.ROOT);
        if (!AsistenteLexico.TIPOS.contains(tipoBuscado)) {
            return Optional.empty();
        }

        String ubicacion = AsistenteLexico.normalizar(ia.ubicacion());
        if (ubicacion.length() > 80) {
            return Optional.empty();
        }

        List<String> palabras = new ArrayList<>();
        if (ia.palabras() != null) {
            for (String valor : ia.palabras()) {
                if (valor == null) {
                    continue;
                }
                for (String token : AsistenteLexico.normalizar(valor).split(" ")) {
                    if (token.isBlank()) {
                        continue;
                    }
                    if (token.length() > 30) {
                        return Optional.empty();
                    }
                    String raiz = AsistenteLexico.raiz(token);
                    if (!palabras.contains(raiz)) {
                        palabras.add(raiz);
                    }
                }
            }
        }
        if (palabras.size() > 12) {
            return Optional.empty();
        }

        List<String> colores = new ArrayList<>();
        if (ia.colores() != null) {
            for (String valor : ia.colores()) {
                if (valor == null) {
                    continue;
                }
                for (String token : AsistenteLexico.normalizar(valor).split(" ")) {
                    if (token.isBlank()) {
                        continue;
                    }
                    String raiz = AsistenteLexico.raiz(token);
                    if (AsistenteLexico.COLORES.contains(raiz) || AsistenteLexico.COLORES.contains(token)) {
                        if (!colores.contains(raiz)) {
                            colores.add(raiz);
                        }
                    }
                }
            }
        }
        for (String color : colores) {
            if (!palabras.contains(color)) {
                palabras.add(color);
            }
        }
        if (palabras.size() > 12) {
            return Optional.empty();
        }

        Categoria categoria = null;
        if (ia.categoriaId() != null) {
            categoria = categorias.stream()
                    .filter(c -> ia.categoriaId().equals(c.getId()) && Boolean.TRUE.equals(c.getEstado()))
                    .findFirst()
                    .orElse(null);
        }

        String intencionFinal = intencion;
        if ("buscar".equals(intencion) && palabras.isEmpty() && categoria == null && ubicacion.isBlank()) {
            intencionFinal = "vacio";
        }

        return Optional.of(new InterpretacionAsistente(
                intencionFinal,
                tipoBuscado,
                categoria == null ? null : categoria.getId(),
                categoria == null ? null : categoria.getNombre(),
                ubicacion,
                palabras,
                colores));
    }
}
