package com.proyectointegrador.entity;

public enum EstadoUsuario {
    ACTIVO,
    SUSPENDIDO,
    /** H34: cuenta anonimizada a pedido de la persona (Ley 29733); no se puede volver a usar. */
    ELIMINADO
}
