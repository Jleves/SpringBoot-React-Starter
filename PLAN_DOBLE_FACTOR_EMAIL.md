# Verificación en dos pasos por correo

## Objetivo y alcance

Diseño para una etapa posterior al cambio de contraseña del perfil. Este documento especifica trabajo pendiente: el doble factor todavía no está implementado.

Cada usuario (`USER`, `ADMIN`, `SUPER_ADMIN`) podrá activar o desactivar la verificación en dos pasos para su propia cuenta. Si está activa, conocer la contraseña no será suficiente para crear una sesión: deberá presentar un código recibido en el correo registrado. No habrá excepciones por rol.

Se reutilizarán Spring Security, MySQL, SMTP, las cookies y CSRF existentes. No se necesita un proveedor externo de identidad ni Redis para esta versión. El SMTP deberá ser operativo y confiable en producción; la recepción de los códigos pasa a formar parte de la disponibilidad del login.

El correo agrega una barrera, pero depende de la seguridad de la casilla y no resiste phishing. Si el mismo correo permite recuperar la contraseña y recibir el código, comprometerlo permite comprometer ambos pasos. No equivale a la independencia de un autenticador TOTP o a las garantías de una passkey. Referencia: [OWASP Multifactor Authentication](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html).

**Decisiones acordadas:** opción desactivada por defecto; código en cada login nuevo cuando esté activada; sin dispositivos de confianza; sin códigos de respaldo ni recuperación administrativa. Quien pierda el correo deberá recuperarlo con su proveedor. Cambiar o recuperar la contraseña no desactiva la verificación en dos pasos.

## Flujo de login

El navegador obtiene primero CSRF con `GET /api/auth/csrf`. Todas las operaciones POST conservan cookie CSRF y header `X-XSRF-TOKEN`. Las contraseñas y códigos viajan en el cuerpo JSON, siempre sobre HTTPS en producción, nunca en URLs.

```mermaid
sequenceDiagram
    actor U as Usuario
    participant R as React / navegador
    participant S as Spring Security
    participant A as AuthService
    participant M as Servicio de desafíos
    participant DB as MySQL
    participant E as SMTP / correo
    U->>R: Email y contraseña
    R->>S: POST /api/auth/login + CSRF
    S->>A: Validar credenciales
    A->>DB: Leer cuenta y configuración bajo bloqueo
    alt Credenciales incorrectas o cuenta deshabilitada
        A-->>R: 401, sin sesión nueva
    else Credenciales correctas y doble paso desactivado
        A->>DB: Crear auth_session
        A-->>R: 200 AUTHENTICATED + user
        Note over A,R: Set-Cookie ACCESS_TOKEN y REFRESH_TOKEN
        R-->>U: Perfil autenticado
    else Credenciales correctas y doble paso activado
        A->>M: Crear desafío LOGIN
        M->>DB: Guardar secreto protegido, expiración e intentos
        Note over M,DB: Confirmar transacción antes de enviar correo
        M->>E: Enviar código al correo registrado
        E-->>M: Aceptado por SMTP
        M->>DB: Marcar generación enviada
        M-->>R: 202 MFA_REQUIRED + expiración
        Note over M,R: Solo cookie MFA_CHALLENGE, sin sesión nueva
        R-->>U: Pantalla para introducir código
        U->>R: Código recibido
        R->>S: POST /api/auth/mfa/login/verify + código + CSRF + cookie de desafío
        S->>M: Verificar desafío y código
        M->>DB: Bloquear, verificar estado y consumir una sola vez
        alt Código válido, cuenta y versiones vigentes
            M->>DB: Crear auth_session en la misma transacción
            M-->>R: 200 AUTHENTICATED + user
            Note over M,R: Cookies ACCESS_TOKEN y REFRESH_TOKEN; borrar MFA_CHALLENGE
            R-->>U: Perfil autenticado
        else Código inválido, expirado o agotado
            M-->>R: Error ApiError, sin crear sesión
            R-->>U: Corregir código o reiniciar login según el error
        end
    end
```

`202` indica que falta completar el desafío, no que el usuario ya esté autenticado. La aceptación SMTP no garantiza llegada a la bandeja. Si SMTP falla, responder `503` y no emitir credenciales; nunca permitir continuar solo con la contraseña como alternativa.

El refresh de una sesión ya autenticada mantiene su funcionamiento sin enviar un código en cada renovación. Cada sesión existente antes de activar/desactivar se revocará para impedir su renovación posterior.

## Activación desde el perfil

La interfaz mostrará el estado confirmado por el servidor y un botón **Activar verificación por correo**. No será un interruptor que modifique el estado inmediatamente.

```mermaid
sequenceDiagram
    actor U as Usuario autenticado
    participant R as Perfil React
    participant A as API
    participant DB as MySQL
    participant E as Correo registrado
    U->>R: Activar e ingresar contraseña actual
    R->>A: POST /api/auth/mfa/enable/start + currentPassword + cookies + CSRF
    A->>DB: Verificar cuenta, contraseña y estado desactivado
    A->>DB: Crear desafío ENABLE ligado al usuario y sesión
    A->>E: Enviar código de activación después del commit
    A-->>R: 202 + cookie MFA_CHALLENGE + vencimiento
    Note over R,DB: La opción permanece desactivada
    U->>R: Ingresar código
    R->>A: POST /api/auth/mfa/enable/confirm + code + cookies + CSRF
    A->>DB: Verificar identidad, sesión, propósito y código
    A->>DB: Consumir desafío, activar, subir versión de seguridad y revocar refresh
    A-->>R: 204 y eliminación de cookies
    R-->>U: Activación confirmada; volver a ingresar
    A->>E: Notificación informativa posterior al commit
    Note over U,A: El siguiente login exigirá contraseña y código
```

El usuario prueba acceso al correo antes de activar. Cancelar, dejar vencer o fallar el código conserva el estado anterior. En una operación exitosa, guardar fecha de activación y limpiar los desafíos pendientes anteriores. La contraseña se comprueba al iniciar y esa prueba solo vale para ese desafío durante sus cinco minutos; si cambia la contraseña, el desafío deja de servir.

## Desactivación desde el perfil

Desactivar debilita la protección de la cuenta: debe exigir sesión válida, contraseña actual y un código nuevo enviado al correo. Ni una cookie por sí sola ni un código usado para login autorizan la desactivación. [OWASP recomienda reautenticación y notificación al cambiar factores](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html#changing-mfa-factors).

```mermaid
sequenceDiagram
    actor U as Usuario autenticado
    participant R as Perfil React
    participant A as API
    participant DB as MySQL
    participant E as Correo registrado
    U->>R: Desactivar e ingresar contraseña actual
    R->>A: POST /api/auth/mfa/disable/start + currentPassword + cookies + CSRF
    A->>DB: Verificar cuenta, contraseña y estado activado
    A->>DB: Crear desafío DISABLE ligado al usuario y sesión
    A->>E: Enviar código de desactivación después del commit
    A-->>R: 202 + cookie MFA_CHALLENGE + vencimiento
    Note over R,DB: La opción permanece activada
    U->>R: Ingresar código
    R->>A: POST /api/auth/mfa/disable/confirm + code + cookies + CSRF
    A->>DB: Verificar propósito, sesión y código
    A->>DB: Consumir desafío, desactivar, subir versión y revocar refresh
    A-->>R: 204 y eliminación de cookies
    R-->>U: Desactivación confirmada; volver a ingresar
    A->>E: Notificación informativa posterior al commit
    Note over U,A: El siguiente login exigirá solo la contraseña
```

Si no recibe el código, puede reenviar respetando los límites. Si perdió el correo, deberá recuperarlo primero. No agregar endpoints de emergencia que permitan a administradores desactivar esta opción de otro usuario.

## Contratos HTTP propuestos

Los nombres de rutas y estados siguientes constituyen el contrato de implementación. Mantener el contrato uniforme `ApiError`.

| Endpoint | Autorización | Entrada | Éxito |
|---|---|---|---|
| `POST /api/auth/login` | Público + CSRF | `email`, `password` | `200 {status: "AUTHENTICATED", user}` o `202 {status: "MFA_REQUIRED", expiresAt, resendAvailableAt, maskedEmail}` |
| `POST /api/auth/mfa/login/verify` | Desafío LOGIN + CSRF | `code` como string | `200 {status: "AUTHENTICATED", user}` + cookies de sesión |
| `POST /api/auth/mfa/login/resend` | Desafío LOGIN + CSRF | Sin cuerpo | `202 {expiresAt, resendAvailableAt}` |
| `GET /api/auth/mfa/status` | Usuario autenticado | Sin cuerpo | `200 {enabled, enabledAt, maskedEmail}` |
| `POST /api/auth/mfa/enable/start` | Usuario autenticado + CSRF | `currentPassword` | `202 {expiresAt, resendAvailableAt}` |
| `POST /api/auth/mfa/enable/confirm` | Usuario y sesión originales + desafío ENABLE + CSRF | `code` | `204`, borrar cookies |
| `POST /api/auth/mfa/disable/start` | Usuario autenticado + CSRF | `currentPassword` | `202 {expiresAt, resendAvailableAt}` |
| `POST /api/auth/mfa/disable/confirm` | Usuario y sesión originales + desafío DISABLE + CSRF | `code` | `204`, borrar cookies |
| `POST /api/auth/mfa/settings/resend` | Usuario y sesión originales + desafío ENABLE o DISABLE + CSRF | Sin cuerpo | `202 {expiresAt, resendAvailableAt}` |
| `POST /api/auth/mfa/cancel` | Cookie de desafío + CSRF | Sin cuerpo | `204` idempotente, invalidar desafío y borrar cookie |

No aceptar email, usuario o propósito elegidos por el cliente en confirmación/reenvío. El destinatario es siempre el correo registrado de la cuenta asociada al desafío. No incluir códigos ni credenciales en las respuestas JSON. `maskedEmail` solo se devuelve después de verificar contraseña o sesión.

Errores: `400 MFA_CODE_INVALID`, `400 MFA_CHALLENGE_INVALID` para desafío ausente, vencido, consumido o desactualizado; `409 MFA_STATE_CONFLICT` para un cambio incompatible de configuración; `429 MFA_RATE_LIMITED` con `Retry-After`; `503 MFA_DELIVERY_UNAVAILABLE` si no se pudo enviar. Contraseña actual incorrecta en configuración: `400` y error de campo; reservar `401` para credenciales de login o sesión inválidas. CSRF inválido continúa siendo `403 CSRF_TOKEN_INVALID`.

Reenviar no requiere reenviar la contraseña. Un desafío vencido o sin intentos obliga a empezar de nuevo; empezar de nuevo no reinicia el límite agregado por cuenta. No reintentar automáticamente confirmaciones ni envíos ante fallos de red. Si la respuesta de una confirmación se pierde, consultar `/me` (login) o `/mfa/status` si aún hay sesión; de no poder consultar, pedir un nuevo login. Nunca mostrar un estado modificado sin confirmación del servidor.

## Persistencia y controles

Agregar mediante Flyway `email_mfa_enabled` (false), `email_mfa_enabled_at` y `security_version` (0) en `users`, y una tabla `auth_challenges` con:

- ID aleatorio, usuario, propósito cerrado (`LOGIN`, `ENABLE`, `DISABLE`), versión de seguridad capturada y, para configuración, ID de la sesión de origen.
- Hash SHA-256 de un secreto aleatorio de desafío de 32 bytes. El secreto en claro se conserva únicamente en una cookie temporal del navegador.
- HMAC-SHA-256 del código, usando una clave independiente de la clave JWT. Incluir ID, propósito y generación del desafío en el mensaje del HMAC; comparación en tiempo constante. No usar SHA-256 simple para los seis dígitos.
- Creación, expiración, consumo, intentos fallidos, cantidad de reenvíos, último envío y generación del código. Estado de entrega (`PENDING`, `SENT`, `FAILED`) independiente de vigencia/consumo.

Cookie `MFA_CHALLENGE`: valor `id.secret`, `HttpOnly`, host-only, `Path=/api/auth/mfa`, `SameSite=Lax`, `Secure=true` en producción, `Max-Age` limitado por la expiración absoluta del desafío. No autoriza `/me`, refresh ni otros recursos privados. Centralizarla en `AuthCookieService`. No guardar el desafío en localStorage/sessionStorage.

**Valores iniciales:** código aleatorio de seis dígitos, incluyendo ceros iniciales; cinco minutos de vigencia absoluta; cinco verificaciones fallidas como máximo por desafío; reenvío cada 60 segundos, con tres reenvíos como máximo. Reenvío invalida el código anterior, conserva intentos y no extiende la expiración absoluta.

Persistir límites agregados por cuenta en MySQL: máximo cinco emisiones de código y diez verificaciones fallidas por ventana de 15 minutos, compartidos entre propósitos/desafíos. La verificación de contraseña para activar/desactivar comparte un límite de cinco fallos en 15 minutos con las operaciones sensibles del perfil. Añadir limitación de login por cuenta e IP en la capa de aplicación/proxy con almacenamiento compartido; no confiar exclusivamente en la IP ni en la memoria de un proceso. Los umbrales deben quedar en configuración tipada de `AppProperties`, con validación positiva.

Solo puede haber un desafío vigente por usuario y propósito. Un inicio nuevo invalida el anterior de ese propósito; una sola cookie en un navegador implica que iniciar otro flujo reemplaza su desafío local. No enviar mensajes automáticamente al cargar la pantalla. Logout y cancelación deben eliminar la cookie e invalidar el desafío local, sin reiniciar límites agregados; agregar un `POST /api/auth/mfa/cancel` con CSRF que solo invalide el desafío acreditado por esa cookie y responda `204` idempotente. Permitido sin sesión normal para poder cancelar LOGIN; el servicio no acepta un ID arbitrario de usuario.

## Servicios y transacciones

- `AuthService`: separar verificación de contraseña de emisión de sesión. Leer `email_mfa_enabled` bajo el bloqueo de usuario utilizado en cambio de contraseña/login. Si requiere código, crear únicamente el desafío. Revalidar cuenta habilitada, configuración y versión al confirmar; nunca confiar solo en el estado capturado al primer paso.
- `EmailMfaService`: inicio, confirmación y reenvío de LOGIN. Consumir desafío y crear `auth_session` en la misma transacción. Una confirmación concurrente debe producir a lo sumo una sesión.
- `MfaSettingsService`: estado, activación y desactivación propias. Verificar contraseña al iniciar y validar sesión activa original en confirmación; obtener el ID de sesión del `sid` del JWT validado, nunca del cuerpo enviado por React. Consumir desafío, actualizar configuración/versión, invalidar otros desafíos y revocar refresh en una transacción.
- `ChallengeService` y repositorios: generación criptográfica, HMAC, límites, comprobación de propósito y expiración, consumo y limpieza periódica de registros expirados. Aplicar bloqueos en orden consistente: usuario, contadores y desafío.
- `MfaEmailSender`: puerto específico con adaptador SMTP/Thymeleaf que reutiliza `JavaMailSender`; plantillas distintas para LOGIN, ENABLE y DISABLE. Clave HMAC suministrada como secreto de entorno, nunca en Git. Invalidar desafíos al rotar la clave.

Para la primera versión, enviar el código sincrónicamente **fuera de la transacción de base**: guardar y confirmar la generación como PENDING, enviar mediante SMTP con timeout y luego marcar SENT en una transacción breve. El código en claro vive solo en memoria durante el envío. Verificar que la generación siga vigente antes de marcar SENT. Un error marca FAILED y no entrega sesión; verificar códigos exige SENT. Si el proceso cae en medio, el reenvío controlado reemplaza el código y permite recuperar el flujo. No mantener bloqueos de MySQL durante SMTP ni afirmar entrega a bandeja por una aceptación SMTP.

Las notificaciones de activación/desactivación se envían después del commit. Un fallo de notificación no revierte un cambio ya confirmado: registrar un error operacional sin código, contraseña o cookie.

Incrementar `security_version` e invalidar desafíos cuando cambie/resetee la contraseña o cambie la configuración MFA. Login pendiente con una contraseña ya reemplazada no puede completarse. La versión de esta propuesta invalida desafíos; **no introduce invalidación inmediata de los access JWT existentes**. Al activar/desactivar, revocar todos los refresh y eliminar cookies locales. Los access JWT anteriores conservan su vigencia residual, hasta 15 minutos por defecto. Si se desea eliminar ese límite, será un cambio adicional en la validación de todos los JWT.

Un login MFA iniciado desde un navegador con cookies previas debe limpiar esas cookies al pasar a MFA_REQUIRED y revocar el refresh presentado si es válido. La identidad pendiente nunca reemplaza un principal autenticado ni obtiene acceso por una cookie de desafío. La revocación no elimina la vigencia residual de copias de access JWT ya emitidos.

## Cambios concretos en Spring Security

`DaoAuthenticationProvider`, BCrypt y `AuthenticationManager` continúan verificando el primer paso. `SessionCreationPolicy.STATELESS` se conserva: no se crea `HttpSession`; persistir desafíos en MySQL es compatible con esta política.

Agregar estas autorizaciones exactas antes de `.anyRequest().authenticated()`:

```java
.requestMatchers(HttpMethod.POST,
    "/api/auth/mfa/login/verify",
    "/api/auth/mfa/login/resend",
    "/api/auth/mfa/cancel").permitAll()
.requestMatchers("/api/auth/mfa/**").authenticated()
```

`permitAll` no elimina CSRF ni la verificación del desafío en el servicio. No usar un `permitAll` para todo `/api/auth/mfa/**`, ni deshabilitar CSRF para recibir los códigos.

En `JwtRequestFilter.shouldNotFilter`, omitir únicamente las dos rutas públicas de LOGIN y la cancelación, como ya ocurre con `/login`, para que un access JWT vencido no bloquee la autenticación pendiente. Las rutas de configuración siguen pasando por el filtro. No interpretar `MFA_CHALLENGE` como JWT de acceso ni crear un `Authentication` con ella.

No es necesario un segundo filtro de autenticación para esta versión: el servicio solo emite access/refresh al terminar ambos pasos. Mantener la política de roles del alta administrativa. Si React consulta `Retry-After` en un despliegue cross-origin, agregar ese header a `exposedHeaders` de CORS, sin ampliar los orígenes permitidos.

## Cambios en React

- `authService.login` debe devolver un resultado discriminado, sin asumir que toda respuesta exitosa contiene `user`. `AuthProvider` tendrá un estado `mfa-required`, distinto de `authenticated`; no se habilitan rutas privadas con él.
- Pantalla de código con `inputMode="numeric"`, `autocomplete="one-time-code"`, seis dígitos como string, pegado permitido, expiración visible y botón de reenvío con espera. El reloj es informativo: el servidor controla los límites.
- Al recargar la pantalla de código, reiniciar el login si se perdió el estado React; no persistir metadatos de autenticación en almacenamiento web. Al reiniciar, los límites de cuenta se conservan.
- Excluir verificación/reenvío de LOGIN del refresh automático, como `/login`; mantener cookies y CSRF. Los endpoints de configuración pueden renovar una sesión vencida antes de actuar, pero un refresh inválido exige login nuevo y no cambia la opción.
- En perfil, consultar estado al entrar. Ofrecer los flujos de activación/desactivación con contraseña actual, código y posibilidad de cancelar. Mostrar estado confirmado y avisar antes de confirmar que se cerrará la sesión local y se revocará la renovación en otros dispositivos.
- Tras activar/desactivar con éxito, limpiar el estado React sin depender de un segundo logout y dirigir al login con el mensaje correspondiente. Limpiar contraseñas/códigos al completar o abandonar el formulario. No mostrar botones administrativos para cambiar el MFA de otras personas.

## Pruebas y criterio de cierre

Desplegar primero la migración aditiva con la opción desactivada por defecto y configurar el secreto HMAC y SMTP; backend y frontend deben actualizarse coordinadamente por el nuevo resultado intermedio de login. No activar cuentas hasta verificar entrega de correo y los flujos completos en el entorno. Un rollback que elimine la comprobación MFA mientras haya cuentas con la opción activada no es aceptable: mantener esas cuentas sin login hasta restaurar una versión compatible.

1. Opción desactivada: login conserva resultado y cookies. Activada: primer paso no crea sesión, y `/me`/refresh rechazan la cookie de desafío sola.
2. Código correcto, incorrecto, cero inicial, expirado, reutilizado, propósito equivocado, secreto de desafío incorrecto y confirmaciones concurrentes; a lo sumo una sesión por desafío.
3. Reenvío invalida código anterior, conserva ventana/intentos, respeta espera y límites globales aunque se creen nuevos desafíos o cambie la instancia del backend.
4. Fallos, timeout y aceptación SMTP; caída entre persistencia/envío/confirmación; nunca acceso sin código ni secretos en logs o errores.
5. Activar/desactivar exige contraseña y código propios, verifica la sesión original y rechaza manipulación de usuario/propósito. Un fallo o cancelación conserva el estado anterior.
6. Cambio/reset de contraseña, deshabilitación de cuenta y cambios de MFA invalidan desafíos obsoletos. Recuperación no desactiva MFA. Pruebas de carreras con login, refresh y configuración.
7. CSRF y cookies por perfil, atomicidad/rollback, migraciones en MySQL y ausencia de bypass por roles o rutas públicas.
8. React: estado pendiente sin acceso privado, reenvíos, recarga, códigos inválidos sin refresh, resultado de red incierto, configuración y regreso al login.
9. Navegador con backend real, MySQL y SMTP de pruebas: login con opción activa/inactiva, activar, cerrar sesión, verificar código, desactivar y comprobar siguiente login.

**Cierre:** pruebas backend/frontend, lint, build y flujos integrados aprobados; documentación de SMTP y secreto HMAC; notificaciones comprobadas; sin tokens/códigos en body de respuesta, logs o almacenamiento web. Implementar en una etapa independiente, conservando las pruebas de cambio de contraseña de la etapa 7.

Complejidad estimada: media-alta. Los cambios de `SecurityConfig` son pequeños; el esfuerzo principal está en los desafíos transaccionales, la entrega de correo, el estado intermedio de React y la prevención de bypass/reutilización. No incluye un servicio de identidad externo, MFA por TOTP/passkeys ni gestión de dispositivos de confianza.
