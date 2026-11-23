package com.proyectointegrador.entity;

/** H68: por dónde sale un tipo de aviso además de la bandeja de Notificaciones. */
public enum CanalAviso {
    PUSH(true, false),
    CORREO(false, true),
    AMBOS(true, true),
    NINGUNO(false, false);

    private final boolean push;
    private final boolean correo;

    CanalAviso(boolean push, boolean correo) {
        this.push = push;
        this.correo = correo;
    }

    public boolean incluyePush() { return push; }

    public boolean incluyeCorreo() { return correo; }
}
