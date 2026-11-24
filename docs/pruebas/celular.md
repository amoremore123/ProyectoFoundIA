# Probar la web en el celular (misma red Wi-Fi)

El navegador del celular solo permite la **cámara en vivo** y el **GPS** en páginas seguras (HTTPS o
`localhost`). Por `http://IP-de-la-laptop:5173` la web carga, pero FINDI y Publicar pasan a elegir una
foto de la galería. Para probar todo se usa el **modo celular**: HTTPS con un certificado local y la API
de Spring por el mismo origen.

## Una sola vez: el certificado local

Desde la raíz del repositorio, con la IP de la laptop en el Wi-Fi (en PowerShell:
`Get-NetIPAddress -AddressFamily IPv4 -InterfaceAlias 'Wi-Fi'`):

```bash
mkdir -p .certs-dev
MSYS_NO_PATHCONV=1 openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
  -keyout .certs-dev/clave.pem -out .certs-dev/certificado.pem -subj "/CN=FoundIA desarrollo" \
  -addext "subjectAltName=IP:TU_IP,IP:127.0.0.1,DNS:localhost"
```

`.certs-dev/` está en `.gitignore`: la clave nunca se sube. Si la laptop cambia de IP, se vuelve a crear
con la IP nueva (se pueden poner varias: `IP:a.b.c.d,IP:e.f.g.h,...`).

Y en `frontend/usuario/.env.celular.local` (también ignorado por git):

```
VITE_API_URL=mismo-origen
```

## Cada vez

1. Docker en marcha: `docker compose up -d mysql mailpit api admin-api`.
2. En `frontend/usuario`: `npm run dev:celular`.
3. En el celular, en el mismo Wi-Fi: `https://TU_IP:5173`.
4. La primera vez el navegador avisa que la conexión no es privada (el certificado es de la laptop):
   **Configuración avanzada → Continuar a TU_IP (no seguro)**. Desde ahí la página cuenta como segura y
   la cámara en vivo y el GPS piden permiso normalmente.

## Cómo funciona

- `vite.config.js`, solo en modo `celular`: abre el servidor a la red (`host: true`), usa el certificado
  de `.certs-dev/` y pasa `/api` y `/uploads` a Spring (`FOUNDIA_SPRING`, por defecto
  `http://localhost:8081`). Quita la cabecera `Origin` porque para Spring la llamada es del mismo origen:
  no hace falta agregar la IP al CORS.
- `npm run dev` sigue igual que siempre (`http://localhost:5173`).
- Para que los enlaces de los correos abran en el celular, el `admin-api` de Docker puede recibir
  `FOUNDIA_WEB_URL=https://TU_IP:5173`.
- El panel (5175) sigue siendo solo para la laptop.

## Problemas frecuentes

| Qué pasa | Por qué | Qué hacer |
| --- | --- | --- |
| El celular no abre la página | Otra red, o la laptop cambió de IP | Revisar la IP y volver a crear el certificado |
| «Falta el certificado del modo celular» al arrancar | No existe `.certs-dev/` | Crear el certificado (arriba) |
| Carga pero no hay objetos | Spring apagado | `docker compose up -d api` |
| Sigue pidiendo foto de la galería | Se abrió por `http://` | Abrir con `https://` |
