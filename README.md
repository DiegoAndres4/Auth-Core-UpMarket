# AuthCore (UPMarket)

Servicio de autenticación: registro, login y emisión de JWT. Arquitectura hexagonal (Java 21, Spring Boot, Gradle).

## Contrato
| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/auth/register` | público | `{username, password}` (username ≤ 100, password 6–72). 201 / 409 si existe |
| POST | `/api/auth/login` | público | devuelve `{token}` o 401 |
| GET | `/api/auth/users` | ADMIN | lista usuarios |
| POST | `/api/auth/users/{id}/roles` | ADMIN | agrega un rol |
| DELETE | `/api/auth/users/me` | autenticado | borra las credenciales del usuario del token (idempotente, 204) |

JWT: HMAC-SHA (HS512 con una clave de 64 bytes), claims `sub` (username), `uid`, `roles`. Los demás servicios lo validan localmente con la misma clave (`JWT_SECRET`).

## Configuración (variables de entorno)
- `JWT_SECRET` **obligatoria**, mínimo 32 bytes (el servicio no arranca sin ella).
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
- `AUTHCORE_ADMIN_PASSWORD`: contraseña inicial de `admin`. **Obligatoria** si el usuario `admin` todavía no existe: el servicio falla al arrancar si falta (ya no se generan contraseñas aleatorias ni se escriben en el log).
- `AUTHCORE_JWT_EXPIRATION_MS` (por defecto 3600000), `SPRING_JPA_HIBERNATE_DDL_AUTO` (por defecto `update`).
- `FLYWAY_ENABLED` (por defecto `false`): en producción se activa y se usa `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`; las migraciones viven en `src/main/resources/db/migration`.
- `GET /actuator/health` es público y se usa como healthcheck en Docker.

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

Para trabajar con UPMarket completo, usa el `docker-compose.dev.yml` de ese repositorio. El puerto `8081` es compartido, así que levanta solo uno a la vez (MySQL ya no choca: este entorno usa `3308`).

## Pruebas
```bash
./gradlew clean test        # unitarias; la prueba de contexto Spring se omite sin RUN_DB_TESTS
RUN_DB_TESTS=true SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3308/authcoredb \
  SPRING_DATASOURCE_USERNAME=authcore SPRING_DATASOURCE_PASSWORD=authcore-dev \
  ./gradlew cleanTest test  # incluye AuthcoreServiceApplicationTests contra MySQL
```

## Pendiente conocido
Sin refresh de token ni revocación; sin cambio de contraseña.
