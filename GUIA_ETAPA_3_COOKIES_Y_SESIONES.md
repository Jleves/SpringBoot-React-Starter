# Guía de la etapa 3: cookies, sesiones y revocación

## Responsabilidades principales

La cookie no genera el token: transporta un valor generado previamente. Conviene separar claramente las responsabilidades:

```text
JWTUtil ─────────────── genera el access JWT
AuthSessionService ─── genera sessionId.secret y persiste su hash
AuthCookieService ──── convierte esos valores en cookies HTTP
AuthController ─────── coordina el flujo y agrega Set-Cookie a la respuesta
```

`JWTUtil` no debería conocer cookies ni HTTP. De la misma manera, `AuthSessionService` debería ocuparse de la sesión y del refresh token, pero no de escribir headers HTTP.

## Generación y centralización de cookies

Se recomienda crear un servicio independiente, por ejemplo `AuthCookieService`, responsable de:

- Crear la cookie `ACCESS_TOKEN`.
- Crear la cookie `REFRESH_TOKEN`.
- Leer sus valores desde una request.
- Crear cookies vencidas para eliminarlas.
- Aplicar consistentemente `HttpOnly`, `Secure`, `SameSite`, `Path` y `Max-Age`.

Las cookies tendrán este contrato:

| Cookie | Contenido | Path | HttpOnly |
|---|---|---:|---:|
| `ACCESS_TOKEN` | JWT generado por `JWTUtil` | `/` | Sí |
| `REFRESH_TOKEN` | `sessionId.secret` generado por `AuthSessionService` | `/api/auth` | Sí |
| `XSRF-TOKEN` | Token generado por Spring Security | `/` | No |

No se configura `Domain`, por lo que serán cookies host-only.

El atributo `Secure` debe obtenerse de configuración:

```properties
app.security.cookies.secure=${COOKIE_SECURE:true}
```

- Producción: `true`.
- Desarrollo HTTP local y tests: `false`.

Los nombres pueden quedar como constantes, en lugar de configuración, porque forman parte del contrato entre backend y frontend.

## Flujo de login

```text
POST /api/auth/login
        │
        ├─ AuthenticationManager valida credenciales
        ├─ AuthSessionService crea sesión y refresh secret
        ├─ JWTUtil genera access JWT
        ├─ AuthCookieService crea las dos cookies
        └─ AuthController devuelve solamente el usuario
```

El `AuthResponse` actual expone ambos tokens. En la etapa 3 debe reemplazarse por un contrato sin credenciales:

```java
public record LoginResponse(UserDTO user) {
}
```

La respuesta HTTP contendrá headers similares a:

```http
Set-Cookie: ACCESS_TOKEN=jwt; HttpOnly; Path=/; SameSite=Lax
Set-Cookie: REFRESH_TOKEN=sessionId.secret; HttpOnly; Path=/api/auth; SameSite=Lax
```

El body contendrá únicamente información pública:

```json
{
  "user": {
    "id": 1,
    "email": "admin@example.com",
    "rol": "SUPER_ADMIN"
  }
}
```

Los access y refresh tokens no deben aparecer en el JSON.

## Estado de una sesión

No hace falta persistir un enum de estado. `AuthSession` ya contiene la información necesaria:

```java
private Instant expiresAt;
private Instant revokedAt;
```

El estado se deriva de esos campos:

```text
revokedAt != null                  → REVOKED
revokedAt == null y expiró         → EXPIRED
revokedAt == null y no expiró      → ACTIVE
```

Un enum persistido duplicaría información y podría quedar inconsistente con las fechas.

El secreto tampoco tiene un estado propio. En la base se almacena solamente su hash:

```java
private String refreshTokenHash;
```

La sesión completa es la que se encuentra activa, expirada o revocada.

## Logout y revocación de la sesión actual

`POST /api/auth/logout` debe:

1. Leer `REFRESH_TOKEN`.
2. Separar `sessionId.secret`.
3. Buscar la sesión por `sessionId`.
4. Comparar el hash del secreto en tiempo constante.
5. Establecer `revokedAt = Instant.now()`.
6. Eliminar las cookies `ACCESS_TOKEN` y `REFRESH_TOKEN`.
7. Opcionalmente eliminar `XSRF-TOKEN`, obligando a obtener uno nuevo antes del próximo login.
8. Responder `204 No Content`.

No se elimina la fila ni se borra el hash. `revokedAt` conserva trazabilidad y garantiza que la sesión no vuelva a utilizarse.

La operación debe ser idempotente. Si falta la cookie, está vencida o la sesión ya fue revocada, igualmente se eliminan las cookies del navegador y se responde `204`.

La revocación pertenece al `AuthSessionService` existente, mediante operaciones como:

```java
void revoke(String refreshToken);
void revokeAllForUser(Long userId);
```

No es necesario crear otro servicio dedicado solamente a revocar sesiones.

## Rotación y reutilización del refresh token

En cada refresh:

1. Se recibe `REFRESH_TOKEN` desde la cookie.
2. Se valida `sessionId.secret`.
3. Se genera un secreto nuevo.
4. Se reemplaza `refreshTokenHash`.
5. Se actualizan `lastUsedAt` y la expiración.
6. Se envían nuevas cookies de access y refresh.

El secreto anterior deja de ser válido automáticamente porque su hash fue reemplazado.

Si alguien reutiliza el secreto anterior, la sesión completa debe marcarse como revocada. Debe cuidarse el límite transaccional: si se establece `revokedAt` y luego se lanza una excepción normal dentro del mismo `@Transactional`, Spring puede hacer rollback y deshacer la revocación. La implementación debe confirmar esa revocación antes de devolver el error, utilizando una transacción independiente o una excepción configurada para no provocar rollback.

La columna `@Version` de `AuthSession` debe controlar los refresh concurrentes. Sólo una transacción puede actualizar la versión. La segunda debe tratarse como reutilización o conflicto y provocar la revocación de esa sesión.

## Cambio de contraseña y revocación global

El cambio debe ejecutarse dentro de una única transacción:

```text
Validar reset token
        ↓
Actualizar passwordHash
        ↓
Marcar reset token como utilizado
        ↓
AuthSessionService.revokeAllForUser(userId)
        ↓
Commit único
```

Si cualquier operación falla, se revierte el cambio completo.

El repositorio de sesiones necesitará una operación equivalente a:

```sql
UPDATE auth_sessions
SET revoked_at = :now
WHERE user_id = :userId
  AND revoked_at IS NULL;
```

Esto invalida todos los refresh tokens del usuario. Los access JWT emitidos previamente pueden continuar funcionando hasta 15 minutos porque son stateless. Este es un límite explícito del diseño.

## Protección CSRF

Para `XSRF-TOKEN` se debe utilizar `CookieCsrfTokenRepository` de Spring Security, no un generador propio:

```java
CookieCsrfTokenRepository.withHttpOnlyFalse()
```

`GET /api/auth/csrf` fuerza la generación del token y su cookie. Luego el cliente envía el valor en el header:

```http
X-XSRF-TOKEN: valor-de-la-cookie
```

La configuración debería distinguir los errores CSRF de otros errores de autorización. Se pueden incorporar códigos como:

```java
CSRF_TOKEN_INVALID
AUTH_REFRESH_TOKEN_INVALID
```

No se recomienda exponer un código público diferente para un refresh token reutilizado. Ese detalle no ayuda al cliente y proporciona información adicional a un atacante. El evento concreto sí puede registrarse internamente usando el `sessionId`, nunca el token.

## Pruebas unitarias

### `AuthCookieServiceTest`

- Nombre y valor correctos.
- Atributo `HttpOnly`.
- `SameSite=Lax`.
- `Secure` según configuración.
- Paths de access y refresh diferentes.
- `Max-Age` según cada duración.
- Cookies de eliminación con `Max-Age=0`.

### `AuthSessionServiceTest`

- Sólo persiste el hash del secreto.
- Crea sesiones independientes.
- La rotación cambia el hash.
- El secreto anterior deja de funcionar.
- Una sesión expirada no rota.
- Una sesión revocada no rota.
- Logout establece `revokedAt`.
- Logout repetido es idempotente.
- La reutilización revoca la sesión.
- La revocación global afecta solamente al usuario indicado.

### `PasswordResetServiceTest`

- Cambia el hash de la contraseña.
- Consume el reset token.
- Revoca todas las sesiones.
- Un fallo provoca rollback completo.

## Pruebas de integración

Con `MockMvc` y una base real mediante Testcontainers:

- Login correcto genera ambas cookies y no devuelve tokens en JSON.
- Login incorrecto no genera cookies.
- `/api/auth/me` funciona con `ACCESS_TOKEN`.
- El header Bearer deja de aceptarse.
- Refresh rota ambas cookies.
- Reutilizar el refresh anterior revoca la sesión.
- Dos refresh concurrentes no generan dos secretos válidos.
- Logout revoca solamente la sesión actual.
- Dos dispositivos mantienen sesiones independientes.
- Reset de contraseña revoca todas las sesiones.
- Las mutaciones sin CSRF reciben `403`.
- Un CSRF válido permite login, refresh, logout y reset.
- El atributo `Secure` cambia correctamente según el perfil.

## Pruebas manuales con Postman

Postman permite recibir, almacenar e inspeccionar cookies `HttpOnly`.

1. Ejecutar `GET /api/auth/csrf`.
2. Abrir la sección **Cookies** y verificar `XSRF-TOKEN`.
3. Copiar su valor al header:

   ```http
   X-XSRF-TOKEN: valor
   ```

4. Ejecutar `POST /api/auth/login`.
5. Verificar que Postman recibió `ACCESS_TOKEN` y `REFRESH_TOKEN`.
6. Ejecutar `GET /api/auth/me`; Postman enviará las cookies automáticamente.
7. Ejecutar refresh y confirmar que cambiaron las dos cookies de autenticación.
8. Ejecutar logout y confirmar que quedaron eliminadas.

En desarrollo mediante `http://localhost`, `Secure` debe ser `false`. Si permanece en `true`, Postman puede recibir la cookie, pero no la enviará sobre HTTP.
