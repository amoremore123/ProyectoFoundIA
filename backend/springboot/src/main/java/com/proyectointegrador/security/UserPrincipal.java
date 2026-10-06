package com.proyectointegrador.security;

import com.proyectointegrador.entity.EstadoUsuario;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String nombre;
    private final String correo;
    private final String password;
    private final Rol rol;
    private final boolean habilitado;

    public UserPrincipal(Long id, String nombre, String correo, String password, Rol rol) {
        this(id, nombre, correo, password, rol, true);
    }

    private UserPrincipal(Long id, String nombre, String correo, String password, Rol rol, boolean habilitado) {
        this.id = id;
        this.nombre = nombre;
        this.correo = correo;
        this.password = password;
        this.rol = rol;
        this.habilitado = habilitado;
    }

    public static UserPrincipal from(Usuario usuario) {
        return new UserPrincipal(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getCorreo(),
                usuario.getPassword(),
                usuario.getRol(),
                usuario.getEstado() == EstadoUsuario.ACTIVO && usuario.isVerificado()
        );
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public Rol getRol() {
        return rol;
    }

    public String getCorreo() {
        return correo;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String authority = rol == Rol.ADMIN ? "ROLE_ADMIN" : "ROLE_USER";
        return List.of(new SimpleGrantedAuthority(authority));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return correo;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return habilitado;
    }
}
