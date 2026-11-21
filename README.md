# AuthCore (UPMarket)

Servicio de autenticación: registro con verificación de correo, login, emisión de JWT y administración de usuarios. Arquitectura hexagonal (Java 21, Spring Boot, Gradle).

## Contrato
| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/auth/register` | público | `{username, password}` (username ≤ 100, password 6–72). 201 `{id, username, emailVerified:false}` **sin token**; 409 si existe |
| POST | `/api/auth/login` | público | 200 `{token}`; contraseña incorrecta 401; 403 `{code:"EMAIL_NO_VERIFICADO"}`; 403 `{code:"CUENTA_BLOQUEADA"}` |
| POST | `/api/auth/verify-email` | público | `{token}` → 200 `{verified:true}`; 400 `{code:"TOKEN_INVALIDO"}` o `{code:"TOKEN_EXPIRADO"}` |
| POST | `/api/auth/resend-verification` | público | `{username}` → **siempre** 202 con el mismo cuerpo (no revela si la cuenta existe) |
| GET | `/api/auth/users` | ADMIN | lista usuarios activos con `id, username, roles, emailVerified, blocked` |
| GET | `/api/auth/users/deleted` | ADMIN | lista las cuentas eliminadas (archivo), de la más reciente a la más antigua |
| POST | `/api/auth/users/{id}/roles` | ADMIN | agrega un rol |
| PATCH | `/api/auth/users/{id}/blocked` | ADMIN | `{blocked: true\|false}` → 200 con el usuario actualizado |
| DELETE | `/api/auth/users/me` | autenticado | archiva el registro en `deleted_users`, borra el usuario y sus tokens de verificación (idempotente, 204) |

### Verificación de correo
- Al registrarse, la cuenta queda con `emailVerified=false` y **no** se emite JWT.
- Se genera un token de 32 bytes aleatorios (Base64 URL-safe), se guarda **solo su hash SHA-256** con vencimiento a 24 horas y se envía el enlace `${APP_PUBLIC_URL}/#/verificar?token=<token>`.
- `verify-email` marca la cuenta como verificada y el token como usado. Un token usado, inválido o inexistente responde `TOKEN_INVALIDO`; uno vencido responde `TOKEN_EXPIRADO`.
- `resend-verification` responde 202 siempre. Solo envía un correo nuevo si la cuenta existe, sigue sin verificar y el último token tiene al menos 60 segundos; en ese caso invalida los tokens anteriores.
- El usuario `admin` inicial se crea ya verificado (no tiene correo).

### Bloqueo de usuarios
- `PATCH /api/auth/users/{id}/blocked` solo lo puede usar un ADMIN.
- Un admin no puede bloquear su propia cuenta: 409 `{code:"NO_PUEDES_BLOQUEARTE"}`.
- Un usuario con rol ADMIN no puede ser bloqueado: 409 `{code:"NO_SE_BLOQUEA_ADMIN"}`.
- Bloquear impide **nuevos** logins (403 `CUENTA_BLOQUEADA`); no borra datos.

### Baja de cuentas
- `DELETE /api/auth/users/me` archiva una instantánea de la cuenta en `deleted_users` (`original_user_id`, `username`, `roles`, `email_verified`, `blocked`, `created_at`, `deleted_at`, `deleted_by`, `reason`) y, en la misma transacción, borra el usuario activo y sus tokens de verificación.
- **No se archiva el hash de la contraseña**: una cuenta cerrada no conserva credenciales.
- El `username`/email queda libre: un registro nuevo con el mismo correo crea una cuenta nueva (id nuevo, `emailVerified=false`) y no modifica el registro archivado. Debe verificarse el correo como cualquier registro nuevo.
- El login de una cuenta eliminada responde igual que el de un usuario inexistente: 401.
- `GET /api/auth/users/deleted` (ADMIN) devuelve el archivo de solo lectura, de la cuenta más reciente a la más antigua. `GET /api/auth/users` sigue listando solo las cuentas activas.
- `created_at` de la cuenta original se guarda como `NULL` porque el esquema de `users` no lo registraba.

JWT: HMAC-SHA (HS512 con una clave de 64 bytes), claims `sub` (username), `uid`, `roles`. Los demás servicios lo validan localmente con la misma clave (`JWT_SECRET`).

> **Limitación conocida:** los JWT son stateless y duran `AUTHCORE_JWT_EXPIRATION_MS` (por defecto 1 h). Un usuario bloqueado conserva el token ya emitido hasta que vence; el bloqueo solo impide iniciar sesión de nuevo. No hay revocación de tokens.

## Configuración (variables de entorno)
- `JWT_SECRET` **obligatoria**, mínimo 32 bytes (el servicio no arranca sin ella).
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
- `AUTHCORE_ADMIN_PASSWORD`: contraseña inicial de `admin`. **Obligatoria** si el usuario `admin` todavía no existe: el servicio falla al arrancar si falta (ya no se generan contraseñas aleatorias ni se escriben en el log).
- `AUTHCORE_JWT_EXPIRATION_MS` (por defecto 3600000), `SPRING_JPA_HIBERNATE_DDL_AUTO` (por defecto `update`).
- `FLYWAY_ENABLED` (por defecto `false`): en producción se activa y se usa `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`; las migraciones viven en `src/main/resources/db/migration`.
- `GET /actuator/health` es público y se usa como healthcheck en Docker.

### Correo (SMTP de Gmail)
- `APP_PUBLIC_URL` (por defecto `http://localhost`): URL del frontend usada en el enlace de verificación.
- `MAIL_HOST` (por defecto `smtp.gmail.com`), `MAIL_PORT` (por defecto `587`), `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` (por defecto igual a `MAIL_USERNAME`).
- STARTTLS y autenticación obligatorios; timeouts de 10 s.
- Si `MAIL_USERNAME` está vacío, el servicio usa un adaptador de desarrollo que escribe el enlace en el log con `WARN` y avisa al arrancar. **No se envía ningún correo real** en ese modo.
- La contraseña SMTP nunca se registra en el log.

Cómo obtener una contraseña de aplicación de Gmail:
1. Activa la **verificación en dos pasos** en la cuenta de Google.
2. Entra en **Cuenta de Google → Seguridad → Contraseñas de aplicaciones**.
3. Crea una contraseña para “Correo” y úsala en `MAIL_PASSWORD` (no la contraseña normal de la cuenta).
4. Gmail impone un límite de aproximadamente **500 correos por día** para cuentas gratuitas.

Para producción use `deploy/env.ejemplo` como referencia (valores públicos) o `deploy/generar-env.sh`, que crea `.env` con secretos aleatorios y permisos `0600`.

## Desarrollo con Docker
```bash
docker compose -f docker-compose.dev.yml up --build --watch
```
No necesita `.env`. Al guardar un archivo de `src/main`, el servicio se recompila y reinicia solo.

- API: `http://localhost:8081`. Usuario inicial: `admin` / `admin-dev-123`.
- MySQL: `localhost:3308`, base `authcoredb`, usuario `authcore` / `authcore-dev`.
- Depurador remoto: puerto `5005`.
- Borrar la base y empezar de cero: `docker compose -f docker-compose.dev.yml down -v`.
- Sin `MAIL_USERNAME` el correo queda deshabilitado y los enlaces de verificación aparecen en el log del contenedor.

Para trabajar con UPMarket completo, usa el `docker-compose.dev.yml` de ese repositorio. El puerto `8081` es compartido, así que levanta solo uno a la vez (MySQL ya no choca: este entorno usa `3308`).

## Pruebas
```bash
./gradlew clean test        # unitarias + integración con H2; la prueba de contexto MySQL se omite sin RUN_DB_TESTS
RUN_DB_TESTS=true SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3308/authcoredb \
  SPRING_DATASOURCE_USERNAME=authcore SPRING_DATASOURCE_PASSWORD=authcore-dev \
  ./gradlew cleanTest test  # incluye AuthcoreServiceApplicationTests contra MySQL
```
Las pruebas de integración usan H2 en memoria y un adaptador de correo que captura los envíos: nunca hay red ni SMTP real.

## Pendiente conocido
Sin refresh de token ni revocación; sin cambio de contraseña. Un usuario bloqueado conserva su JWT vigente hasta que expira (ver limitación arriba).
