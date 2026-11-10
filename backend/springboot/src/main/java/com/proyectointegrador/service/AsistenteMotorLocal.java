package com.proyectointegrador.service;

import com.proyectointegrador.entity.Categoria;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * H45: motor local de interpretación en español. Es el respaldo cuando el
 * proveedor de IA no está configurado o devuelve una respuesta inválida.
 *
 * Porta las reglas del motor del frontend (frontend/usuario/src/utils/asistente.js)
 * para que ambos produzcan la misma forma de interpretación.
 */
@Component
public class AsistenteMotorLocal {

    private static final Set<String> STOP = new HashSet<>(Arrays.asList(
            ("a al algo alguien alguna alguno ante con contra de del desde donde e el ella ellos en entre "
                    + "era es esa ese eso esta este esto estoy fue ha hay he la las le lo los me mi mis muy no "
                    + "nos o para pero por que se si sin sobre su sus te tengo tiene tu tus un una uno unos unas "
                    + "y ya yo hoy ayer manana tarde noche dia creo quiza tal vez cual cuando como porfa porfavor "
                    + "favor gracias hola buenas buenos dias por ahi aqui alli alla puedes podrias ayudame ayuda "
                    + "ayudar quiero queria necesito saber ver hace rato semana pasada").split(" ")));

    private static final List<String> VERBOS_PERDI = List.of(
            "perdi", "perdido", "perdida", "extravie", "extraviado", "extraviada", "olvide",
            "olvidado", "deje", "cayo", "robaron", "busco", "buscando", "buscar", "encuentro", "falta");

    private static final List<String> VERBOS_ENCONTRE = List.of(
            "encontre", "halle", "recogi", "vi", "encontrado", "encontrada");

    private static final List<String> SALUDOS = List.of("hola", "buenas", "buenos", "hey", "saludos", "que tal");
    private static final List<String> GRACIAS = List.of("gracias", "genial", "perfecto", "excelente", "listo");

    private static final List<List<String>> SINONIMOS = List.of(
            List.of("celular", "telefono", "movil", "smartphone", "iphone", "samsung", "xiaomi", "motorola", "cel"),
            List.of("laptop", "computadora", "portatil", "notebook", "pc", "macbook", "lenovo", "hp"),
            List.of("mochila", "bolso", "morral", "maleta"),
            List.of("documento", "dni", "carne", "carnet", "cedula", "tarjeta", "credencial", "pasaporte", "fotocheck"),
            List.of("llave", "llavero"),
            List.of("gafa", "lente", "anteojo"),
            List.of("audifono", "auricular", "airpod", "headset", "audifon"),
            List.of("billetera", "cartera", "monedero"),
            List.of("casaca", "chaqueta", "polera", "chompa", "sueter", "abrigo", "chaleco"),
            List.of("botella", "tomatodo", "termo", "tomatodos"),
            List.of("reloj", "smartwatch"),
            List.of("cuaderno", "libreta", "libro", "apunte"),
            List.of("cargador", "cable", "adaptador"),
            List.of("calculadora", "casio"),
            List.of("paraguas", "sombrilla"));

    private static final Set<String> LUGARES = Set.of(
            "aula", "salon", "laboratorio", "lab", "biblioteca", "comedor", "cafeteria", "entrada",
            "puerta", "pabellon", "patio", "estacionamiento", "auditorio", "bano", "banos", "gimnasio",
            "losa", "taller", "pasillo", "escalera", "oficina", "secretaria", "kiosko", "cancha",
            "jardin", "piso", "edificio", "sotano", "paradero", "bus");

    private static final Pattern UBICACION = Pattern.compile(
            "\\b(?:en|cerca de|cerca del|por|dentro de|dentro del|afuera de)\\s+"
                    + "(?:el |la |los |las |un |una |mi )?([a-z0-9ñ ]{2,40}?)"
                    + "(?=$| ayer| hoy| esta | este | cuando| mientras| y | que | el dia| la semana| durante)");

    private static final Pattern PUBLICAR = Pattern.compile(
            "\\b(como|donde)\\b.*\\b(publico|publicar|reporto|reportar|subo|subir)\\b");
    private static final Pattern QUIERO_PUBLICAR = Pattern.compile("\\bquiero (publicar|reportar)\\b");
    private static final Pattern AYUDA = Pattern.compile(
            "\\b(que puedes|que haces|como funciona|ayuda|quien eres)\\b");

    public InterpretacionAsistente interpretar(String mensaje, List<Categoria> categorias) {
        String texto = AsistenteLexico.normalizar(mensaje);
        List<String> tokens = texto.isBlank() ? List.of() : List.of(texto.split(" "));
        Set<String> conjunto = new HashSet<>(tokens);

        String intencion = "buscar";
        if (PUBLICAR.matcher(texto).find() || QUIERO_PUBLICAR.matcher(texto).find()) {
            intencion = "publicar";
        } else if (AYUDA.matcher(texto).find() && tokens.size() <= 6) {
            intencion = "ayuda";
        }

        String tipoBuscado = "";
        if (tiene(texto, conjunto, VERBOS_ENCONTRE) && !tiene(texto, conjunto, List.of("perdi", "extravie", "olvide"))) {
            tipoBuscado = "PERDIDO";
        } else if (tiene(texto, conjunto, VERBOS_PERDI)) {
            tipoBuscado = "ENCONTRADO";
        }

        String ubicacion = extraerUbicacion(texto);
        Set<String> tokensUbicacion = new HashSet<>(ubicacion.isBlank() ? List.of() : List.of(ubicacion.split(" ")));

        Set<String> ignorar = new HashSet<>(STOP);
        ignorar.addAll(VERBOS_PERDI);
        ignorar.addAll(VERBOS_ENCONTRE);
        ignorar.addAll(SALUDOS);
        ignorar.addAll(GRACIAS);
        ignorar.addAll(List.of("objeto", "cosa", "cosas", "perdidos", "encontrados", "campus", "tecsup"));

        Set<String> palabras = new LinkedHashSet<>();
        for (String token : tokens) {
            if (token.length() > 1 && !ignorar.contains(token) && !tokensUbicacion.contains(token)) {
                palabras.add(AsistenteLexico.raiz(token));
            }
        }

        List<String> colores = palabras.stream().filter(AsistenteLexico::esColor).toList();

        Long categoriaId = null;
        String categoriaNombre = null;
        for (Categoria categoria : categorias) {
            if (!Boolean.TRUE.equals(categoria.getEstado())) {
                continue;
            }
            String descripcion = categoria.getDescripcion() == null ? "" : categoria.getDescripcion();
            Set<String> vocabulario = new HashSet<>();
            for (String parte : AsistenteLexico.normalizar(categoria.getNombre() + " " + descripcion).split(" ")) {
                if (!parte.isBlank()) {
                    vocabulario.add(AsistenteLexico.raiz(parte));
                }
            }
            boolean coincide = palabras.stream()
                    .anyMatch(palabra -> expandir(palabra).stream().anyMatch(vocabulario::contains));
            if (coincide) {
                categoriaId = categoria.getId();
                categoriaNombre = categoria.getNombre();
                break;
            }
        }

        if ("buscar".equals(intencion) && palabras.isEmpty() && categoriaId == null && ubicacion.isBlank()) {
            if (tiene(texto, conjunto, GRACIAS)) {
                intencion = "gracias";
            } else if (tiene(texto, conjunto, SALUDOS)) {
                intencion = "saludo";
            } else {
                intencion = "vacio";
            }
        }

        return new InterpretacionAsistente(
                intencion, tipoBuscado, categoriaId, categoriaNombre, ubicacion,
                new ArrayList<>(palabras), colores);
    }

    private boolean tiene(String texto, Set<String> tokens, List<String> lista) {
        for (String valor : lista) {
            if (valor.contains(" ")) {
                if (texto.contains(valor)) {
                    return true;
                }
            } else if (tokens.contains(valor)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> expandir(String palabra) {
        String raiz = AsistenteLexico.raiz(palabra);
        Set<String> resultado = new LinkedHashSet<>();
        resultado.add(raiz);
        for (List<String> grupo : SINONIMOS) {
            if (grupo.contains(raiz) || grupo.contains(palabra)) {
                resultado.addAll(grupo);
                break;
            }
        }
        return resultado;
    }

    private String extraerUbicacion(String texto) {
        Matcher matcher = UBICACION.matcher(texto);
        if (!matcher.find()) {
            return "";
        }
        String candidato = matcher.group(1).trim();
        for (String token : candidato.split(" ")) {
            if (LUGARES.contains(AsistenteLexico.raiz(token)) || LUGARES.contains(token)
                    || token.matches("\\d{2,4}[a-z]?")) {
                return candidato;
            }
        }
        return "";
    }
}
