# AuthCore (UPMarket)

Servicio de autenticación: registro, login y emisión de JWT. Arquitectura hexagonal (Java 21, Spring Boot, Gradle).

## Contrato
| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/auth/register` | público | `{username, password}` (username ≤ 100, password 6–72). 201 / 409 si existe |
| POST | `/api/auth/login` | público | devuelve `{token}` o 401 |
| GET | `/api/auth/users` | ADMIN | lista usuarios |
| POST | `/api/auth/users/{id}/roles` | ADMIN | agrega un rol |

JWT: HMAC-SHA (HS512 con una clave de 64 bytes), claims `sub` (username), `uid`, `roles`. Los demás servicios lo validan localmente con la misma clave (`JWT_SECRET`).

## Configuración (variables de entorno)
- `JWT_SECRET` **obligatoria**, mínimo 32 bytes (el servicio no arranca sin ella).
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
- `AUTHCORE_ADMIN_PASSWORD`: contraseña inicial de `admin`. Si no se define, se genera una aleatoria y se imprime una vez en el log.
- `AUTHCORE_JWT_EXPIRATION_MS` (por defecto 3600000), `SPRING_JPA_HIBERNATE_DDL_AUTO` (por defecto `update`).

## Desarrollo con Docker
```bash
docker compose -f docker-compose.dev.yml up --build --watch
```
No necesita `.env`. Al guardar un archivo de `src/main`, el servicio se recompila y reinicia solo.

- API: `http://localhost:8081`. Usuario inicial: `admin` / `admin-dev-123`.
- MySQL: `localhost:3307`, base `authcoredb`, usuario `authcore` / `authcore-dev`.
- Depurador remoto: puerto `5005`.
- Borrar la base y empezar de cero: `docker compose -f docker-compose.dev.yml down -v`.

Para trabajar con UPMarket completo, usa el `docker-compose.dev.yml` de ese repositorio. Los dos entornos comparten puertos, así que levanta solo uno a la vez.

## Pendiente conocido
Sin refresh de token ni revocación; sin migraciones (Flyway); sin cambio de contraseña ni borrado de usuarios.
