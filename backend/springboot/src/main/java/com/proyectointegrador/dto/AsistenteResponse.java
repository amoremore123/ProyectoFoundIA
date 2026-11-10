package com.proyectointegrador.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * H45: interpretación que consume el asistente web/Android.
 * Los campos extra (fuente, resumenPropio, datosPropios) no cambian el contrato
 * que ya usa el frontend: intencion, tipoBuscado, categoria.id, ubicacion,
 * palabras y colores.
 *
 * H47: "tarjetas" y "acciones" salen de un catálogo cerrado (AsistenteCatalogo) y los arma el
 * servidor con datos validados; el modelo de IA no los genera. Una acción nunca se ejecuta sola.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsistenteResponse(
        String intencion,
        String tipoBuscado,
        CategoriaAsistente categoria,
        String ubicacion,
        List<String> palabras,
        List<String> colores,
        String fuente,
        String resumenPropio,
        DatosPropios datosPropios,
        List<Tarjeta> tarjetas,
        List<Accion> acciones
) {

    /** Contrato anterior a H47: sin tarjetas ni acciones. */
    public AsistenteResponse(String intencion, String tipoBuscado, CategoriaAsistente categoria, String ubicacion,
                             List<String> palabras, List<String> colores, String fuente, String resumenPropio,
                             DatosPropios datosPropios) {
        this(intencion, tipoBuscado, categoria, ubicacion, palabras, colores, fuente, resumenPropio, datosPropios,
                null, null);
    }

    public record CategoriaAsistente(Long id, String nombre) {
    }

    /** Solo cifras: nunca nombres, correos, teléfonos ni tokens de QR. */
    public record DatosPropios(
            Publicaciones publicaciones,
            Reclamos reclamos,
            long qrRetiroVigentes
    ) {
    }

    public record Publicaciones(long total, long activas, long enCustodia, long recuperadas) {
    }

    public record Reclamos(long total, long pendientes, long aceptados, long retirados, long rechazados) {
    }

    /**
     * H47: componente que el cliente ya sabe dibujar. "datos" solo lleva valores públicos o cifras.
     * "enlace" es siempre una ruta interna de la web.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tarjeta(String tipo, String titulo, Map<String, Object> datos, String enlace) {
    }

    /**
     * H47: acción propuesta. El cliente muestra "confirmacion" y solo si la persona acepta abre "destino"
     * con "datos" prellenados. requiereConfirmacion siempre es true.
     */
    public record Accion(String tipo, String texto, String confirmacion, String destino, Map<String, Object> datos,
                         boolean requiereConfirmacion) {
    }
}
