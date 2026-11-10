package com.proyectointegrador.service;

import java.util.Set;

/**
 * H47 - Catálogo cerrado de lo que FINDI puede devolver además del texto.
 *
 * La web y Android solo dibujan componentes ya construidos para estos tipos; cualquier otro
 * tipo se ignora. Las acciones nunca se ejecutan en el servidor: el cliente pide confirmación
 * y solo entonces abre la pantalla con los datos prellenados (la persona aún puede cambiarlos).
 */
public final class AsistenteCatalogo {

    private AsistenteCatalogo() {
    }

    /** Tarjeta de una publicación visible (activa o en custodia). */
    public static final String TARJETA_OBJETO = "OBJETO";
    /** Estado de los reclamos propios (solo cifras). */
    public static final String TARJETA_RECLAMOS = "RECLAMOS";
    /** QR de retiro vigentes de la cuenta (solo cifra; el token nunca se envía). */
    public static final String TARJETA_QR = "QR_RETIRO";

    /** Abre Publicar con tipo, categoría y ubicación prellenados. */
    public static final String ACCION_PRELLENAR_PUBLICAR = "PRELLENAR_PUBLICAR";
    /** Abre el detalle de un objeto encontrado con el diálogo de reclamo listo para confirmar. */
    public static final String ACCION_ABRIR_RECLAMO = "ABRIR_RECLAMO";

    public static final Set<String> TARJETAS = Set.of(TARJETA_OBJETO, TARJETA_RECLAMOS, TARJETA_QR);
    public static final Set<String> ACCIONES = Set.of(ACCION_PRELLENAR_PUBLICAR, ACCION_ABRIR_RECLAMO);
}
