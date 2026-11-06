package com.proyectointegrador.security;

import com.proyectointegrador.entity.EstadoUsuario;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("H12 - Token JWT")
class JwtServiceTest {

    private static final String SECRETO = "secreto-de-pruebas-con-al-menos-32-caracteres!!";

    private final UserPrincipal ana = new UserPrincipal(1L, "Ana", "ana@foundia.dev", "hash", Rol.USUARIO);
    private final UserPrincipal luis = new UserPrincipal(2L, "Luis", "luis@foundia.dev", "hash", Rol.USUARIO);

    @Test
    @DisplayName("El token generado contiene el correo y es válido para su dueño")
    void tokenValido() {
        JwtService jwt = new JwtService(SECRETO, 60_000);
        String token = jwt.generateToken(ana);

        assertThat(jwt.extractUsername(token)).isEqualTo("ana@foundia.dev");
        assertThat(jwt.isTokenValid(token, ana)).isTrue();
        assertThat(jwt.isTokenValid(token, luis)).isFalse();
    }

    @Test
    @DisplayName("Un token firmado con otro secreto no es válido")
    void otroSecreto() {
        String token = new JwtService(SECRETO, 60_000).generateToken(ana);
        JwtService otro = new JwtService("otro-secreto-distinto-con-mas-de-32-caracteres", 60_000);

        assertThat(otro.isTokenValid(token, ana)).isFalse();
    }

    @Test
    @DisplayName("Un token expirado no es válido")
    void tokenExpirado() {
        JwtService jwt = new JwtService(SECRETO, -1_000);
        String token = jwt.generateToken(ana);

        assertThat(jwt.isTokenValid(token, ana)).isFalse();
    }

    @Test
    @DisplayName("Un token anterior no habilita una cuenta que ahora está suspendida")
    void tokenDeCuentaSuspendida() {
        JwtService jwt = new JwtService(SECRETO, 60_000);
        String token = jwt.generateToken(ana);
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setNombre("Ana");
        usuario.setCorreo("ana@foundia.dev");
        usuario.setRol(Rol.USUARIO);
        usuario.setVerificado(true);
        usuario.setEstado(EstadoUsuario.SUSPENDIDO);

        assertThat(jwt.isTokenValid(token, UserPrincipal.from(usuario))).isFalse();
    }

    private Claims claims(String token) {
        return Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8)))
                .build().parseClaimsJws(token).getBody();
    }

    private UserPrincipal credencialActual(String hash, Date cambio) {
        Usuario usuario = new Usuario();
        usuario.setId(1L); usuario.setNombre("Ana"); usuario.setCorreo(ana.getCorreo());
        usuario.setPassword(hash); usuario.setRol(Rol.USUARIO); usuario.setVerificado(true);
        usuario.setPasswordActualizadoEn(cambio == null ? null : LocalDateTime.ofInstant(cambio.toInstant(), ZoneOffset.UTC));
        return UserPrincipal.from(usuario);
    }

    private String legado(Date emitido, Object sello, boolean incluirSello) {
        var builder = Jwts.builder().setSubject(ana.getUsername()).setIssuedAt(emitido)
                .setExpiration(new Date(System.currentTimeMillis() + 60_000));
        if (incluirSello) builder.claim("cred", sello);
        return builder.signWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
    }

    @Test
    @DisplayName("H35: el JWT anterior se invalida aunque el cambio tenga el mismo segundo")
    void invalidaCredencialAnteriorEnElMismoSegundo() {
        JwtService jwt = new JwtService(SECRETO, 60_000);
        String anterior = jwt.generateToken(ana);
        Date mismoSegundo = claims(anterior).getIssuedAt();
        assertThat(jwt.isTokenValid(anterior, credencialActual("hash-nuevo", mismoSegundo))).isFalse();
    }

    @Test
    @DisplayName("H35: el login nuevo sirve inmediatamente y una segunda clave vuelve a revocarlo")
    void loginNuevoNoDependeDeEsperasNiDeLaPrecisionTemporal() {
        JwtService jwt = new JwtService(SECRETO, 60_000);
        Date cambio = claims(jwt.generateToken(ana)).getIssuedAt();
        UserPrincipal actual = credencialActual("hash-nuevo", cambio);
        String nuevo = jwt.generateToken(actual);
        assertThat(jwt.isTokenValid(nuevo, actual)).isTrue();
        assertThat(jwt.isTokenValid(nuevo, credencialActual("otro-hash", cambio))).isFalse();
    }

    @Test
    @DisplayName("H35: el sello coincide con el vector Django y no contiene el hash almacenado")
    void selloCompartidoEntreBackendsSinExponerCredenciales() {
        Claims payload = claims(new JwtService(SECRETO, 60_000).generateToken(ana));
        assertThat(payload.get("cred")).isEqualTo("b5fbc51c55bd84fa2b8d75e4fad7f42da3abe1fadf0e1ca7a72b6829aa41f295");
        assertThat(payload.values()).doesNotContain(ana.getPassword());
    }

    @Test
    @DisplayName("H35: el formato legado solo se conserva antes del primer restablecimiento")
    void conservaLegadosSoloSiNoSeRecuperoLaPassword() {
        JwtService jwt = new JwtService(SECRETO, 60_000);
        Date emitido = new Date();
        String anterior = legado(emitido, null, false);
        assertThat(jwt.isTokenValid(anterior, ana)).isTrue();
        assertThat(jwt.isTokenValid(anterior, credencialActual("hash", claims(anterior).getIssuedAt()))).isFalse();
    }

    @Test
    @DisplayName("H35: un sello inválido no se acepta como un JWT legado")
    void selloInvalidoNoTieneModoCompatible() {
        JwtService jwt = new JwtService(SECRETO, 60_000);
        assertThat(jwt.isTokenValid(legado(new Date(), "a".repeat(64), true), ana)).isFalse();
        assertThat(jwt.isTokenValid(legado(new Date(), true, true), ana)).isFalse();
        Object sello = claims(jwt.generateToken(ana)).get("cred");
        assertThat(jwt.isTokenValid(legado(null, sello, true), ana)).isFalse();
    }

    @Test
    @DisplayName("H35: un sello explícitamente null tampoco se trata como JWT legado")
    void selloNullNoTieneModoCompatible() {
        String token = Jwts.builder().setPayload("{\"sub\":\"ana@foundia.dev\",\"exp\":"
                + (System.currentTimeMillis() / 1000 + 60) + ",\"cred\":null}")
                .signWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
        assertThat(new JwtService(SECRETO, 60_000).isTokenValid(token, ana)).isFalse();
    }

    @Test
    @DisplayName("H35: cambiar el hash también revoca el sello aunque no se registre otra fecha")
    void cambioDeHashSinFechaRevocaLaSesion() {
        JwtService jwt = new JwtService(SECRETO, 60_000);
        String token = jwt.generateToken(ana);
        assertThat(jwt.isTokenValid(token, credencialActual("otro-hash", null))).isFalse();
    }
}
