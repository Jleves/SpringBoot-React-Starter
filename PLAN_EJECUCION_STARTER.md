# Plan por etapas: plantilla estable Spring Boot + React con cookies

## Resumen

Construir una plantilla clonable con Spring Boot 4.1/Java 21, React/Vite y MySQL. La autenticación utilizará access JWT y refresh token en cookies `HttpOnly`, protección CSRF y sesiones multidispositivo.

La implementación partirá del código copiado, pero se reescribirán los componentes de seguridad, persistencia de sesiones, configuración y errores. No se migrará lógica específica de rifas.

## Etapas de implementación

### Estrategia de commits y reversión

- Cada etapa termina en un commit de cierre independiente y funcional; no se mezclan cambios pertenecientes a la etapa siguiente.
- Antes de crear el commit deben cumplirse su criterio de salida y las pruebas disponibles hasta ese momento.
- Los mensajes siguen Conventional Commits y se ejecutan desde la raíz del repositorio.
- Si una etapa necesita varios commits de trabajo, estos deben ser pequeños y coherentes; el commit propuesto aquí actúa como punto de control final de la etapa.
- Para volver atrás se debe preferir `git revert <commit>` sobre reescribir el historial compartido.

### Etapa 0 — Baseline ejecutable

- Reparar o regenerar Maven Wrapper y fijar Maven 3.9.16.
- Mantener una estructura raíz `Backend/spring-starter` y crear `Frontend/react-starter`.
- Corregir perfiles y propiedades heredadas de Rifas.
- Activar explícitamente el perfil `test` en pruebas.
- Confirmar que backend compila y que React ejecuta lint/build.
- Mantener Spring Boot 4.1.0 y Java 21, validando compatibilidad de dependencias.

**Criterio de salida:** backend y frontend construyen desde cero con comandos documentados y sin variables productivas.

**Commit de cierre propuesto:** `chore(project): establish executable backend and frontend baseline`

Incluye exclusivamente la reparación del toolchain, la limpieza mínima de perfiles heredados, la estructura inicial de ambos proyectos y sus comandos de build verificados.

### Etapa 1 — Modelo, migraciones y configuración

- Reorganizar paquetes por capacidad: `auth`, `user`, `email`, `security`, `logging` y `shared`.
- Usar roles fijos `SUPER_ADMIN`, `ADMIN` y `USER`; eliminar permisos CRUD genéricos.
- Separar la entidad `User` del principal de Spring Security.
- Normalizar emails con `trim` y minúsculas antes de buscar o guardar.
- Crear migraciones Flyway para:
  - `users`;
  - `auth_sessions`;
  - `password_reset_tokens`.
- Modelar sesiones multidispositivo con UUID público, hash del secreto de refresh, expiración, revocación, última actividad y control optimista de concurrencia.
- Almacenar únicamente hashes SHA-256 de refresh y reset tokens.
- Configurar Hibernate con `ddl-auto=validate` y `open-in-view=false`.
- Conservar una única clase raíz `AppProperties`, enlazada con `@ConfigurationProperties(prefix = "app")` y validada con `@Validated`.
- Mantener dentro de `AppProperties` únicamente grupos anidados para:
  - JWT: secreto, duración del access token y duración del refresh token;
  - mail: host, puerto, username, password y URL base del frontend para enlaces;
  - bootstrap: habilitación, email y contraseña inicial del `SUPER_ADMIN`.
- Usar tipos apropiados —especialmente `Duration` para `15m` y `3h`— en lugar de mantener propiedades separadas en minutos y horas.
- Eliminar los `@Value` dispersos para propiedades propias e inyectar `AppProperties` mediante constructor.
- Configurar `JavaMailSender` desde `AppProperties.Mail`, evitando duplicar las mismas variables bajo `spring.mail.*`; TLS, encoding y timeouts estables quedarán en la configuración interna de mail.
- Mantener datasource, JPA, Flyway, logging y Actuator fuera de `AppProperties`, utilizando las propiedades y la autoconfiguración estándar de Spring Boot.
- Validar el bootstrap condicionalmente: si está habilitado, email y contraseña son obligatorios; si está deshabilitado, pueden omitirse.
- Implementar bootstrap opcional e idempotente de `SUPER_ADMIN` mediante variables de entorno; eliminar `seed_users.json`.
- Usar MySQL como base oficial y Testcontainers MySQL en integraciones.

**Criterio de salida:** esquema reproducible desde cero, sin `ddl-auto=update` ni secretos persistidos en claro.

Configuración propia inicial:

```properties
app.security.jwt.secret=${JWT_SECRET}
app.security.jwt.access-expiration=${JWT_ACCESS_EXPIRATION:15m}
app.security.jwt.refresh-expiration=${JWT_REFRESH_EXPIRATION:3h}

app.mail.host=${EMAIL_HOST}
app.mail.port=${EMAIL_PORT:587}
app.mail.username=${EMAIL_USERNAME}
app.mail.password=${EMAIL_PASSWORD}
app.mail.frontend-base-url=${FRONTEND_BASE_URL:http://localhost:5173}

app.init.superadmin.enabled=${INIT_SUPERADMIN_ENABLED:false}
app.init.superadmin.email=${INIT_SUPERADMIN_EMAIL:}
app.init.superadmin.password=${INIT_SUPERADMIN_PASSWORD:}
```

**Commit de cierre propuesto:** `refactor(core): establish persistence and configuration foundation`

Incluye la organización modular, roles, separación entre usuario y principal de seguridad, migraciones, entidades, repositorios, normalización de email, `AppProperties`, configuración de mail, bootstrap y pruebas de persistencia de esta etapa.

### Etapa 2 — Contratos HTTP, errores y seguridad base

- Estandarizar errores con:
  - `timestamp`;
  - `status`;
  - `code`;
  - `message`;
  - `path`;
  - `requestId`;
  - errores de campo cuando corresponda.
- Unificar errores MVC y Spring Security mediante `AuthenticationEntryPoint` y `AccessDeniedHandler`.
- Eliminar `printStackTrace`, logs de tokens y prefijos de secretos.
- Añadir `requestId` al MDC y al header `X-Request-Id`.
- Registrar eventos de seguridad por identificador de usuario/sesión, sin contraseñas, cookies ni tokens.
- Corregir inyección por constructor y nombres de paquetes/clases.

**Criterio de salida:** todos los errores JSON comparten contrato y ningún secreto aparece en logs.

**Commit de cierre propuesto:** `refactor(api): unify error handling and security logging`

Incluye el contrato de errores, entry points de seguridad, correlación por `requestId`, sanitización de logs e inyección por constructor, sin incorporar todavía autenticación por cookies.

### Etapa 3 — Autenticación mediante cookies

Implementar estos endpoints:

- `GET /api/auth/csrf`: genera la cookie CSRF.
- `POST /api/auth/login`: valida credenciales, crea sesión y devuelve únicamente el usuario.
- `POST /api/auth/refresh`: rota el refresh token y renueva el access token.
- `POST /api/auth/logout`: revoca la sesión actual y elimina cookies.
- `GET /api/auth/me`: devuelve el usuario autenticado.
- `POST /api/auth/forgot-password`.
- `GET /api/auth/reset-password/validate?token=...`.
- `POST /api/auth/reset-password`.

Política de cookies:

- `ACCESS_TOKEN`: JWT, `HttpOnly`, host-only, `Path=/`, `SameSite=Lax`, duración predeterminada de 15 minutos.
- `REFRESH_TOKEN`: valor `sessionId.secret`, `HttpOnly`, host-only, `Path=/api/auth`, `SameSite=Lax`, duración predeterminada de 3 horas.
- `XSRF-TOKEN`: no `HttpOnly`, `Path=/`, `SameSite=Lax`; React la envía como `X-XSRF-TOKEN`.
- `Secure=true` en producción y `false` exclusivamente en desarrollo local.
- No establecer `Domain`, porque frontend y API compartirán sitio.
- No devolver access ni refresh tokens en JSON ni aceptar Bearer como mecanismo alternativo.
- Centralizar la creación, lectura y eliminación de `ACCESS_TOKEN` y `REFRESH_TOKEN` en un componente HTTP dedicado, por ejemplo `AuthCookieService`; `JWTUtil` seguirá generando el access JWT y `AuthSessionService` seguirá generando y rotando el valor `sessionId.secret`, sin depender de APIs HTTP.
- Generar `XSRF-TOKEN` mediante `CookieCsrfTokenRepository`, no mediante un generador propio, y aplicar CSRF a todas las operaciones mutantes, incluidos login, refresh, logout y reset de contraseña.
- Mantener los nombres de cookies como constantes del contrato y obtener `Secure` desde configuración tipada, con valor verdadero por defecto y falso solamente en desarrollo local y tests.

Comportamiento de sesión:

- Cada login crea una sesión independiente.
- Refresh rota el secreto dentro de la misma sesión y se ejecuta transaccionalmente.
- Si se reutiliza un secreto anterior o no coincide su hash, se revoca esa sesión.
- Dos refresh concurrentes no pueden producir dos tokens válidos.
- Derivar el estado de la sesión desde `revokedAt` y `expiresAt`, sin persistir un enum redundante: una sesión está revocada cuando `revokedAt` no es nulo, expirada cuando superó `expiresAt` y activa únicamente cuando no está revocada ni expirada.
- Garantizar que la revocación provocada por reutilización, hash incorrecto o conflicto concurrente se confirme aunque el refresh termine con error; el rollback de la rotación no puede restaurar la sesión. Resolverlo mediante un límite transaccional independiente —si se utiliza `REQUIRES_NEW`, debe ejecutarse desde otro bean administrado por Spring— o mediante una coordinación equivalente que confirme `revokedAt` antes de lanzar el error público.
- Responder con un error público genérico para refresh inválido, expirado o reutilizado; registrar el motivo específico solamente en logs de seguridad por `sessionId`, nunca mediante el token o su secreto.
- Logout valida el refresh presentado, revoca solamente la sesión actual y elimina las cookies. Debe ser idempotente: si falta la cookie, es inválida o la sesión ya estaba revocada, igualmente elimina las cookies y devuelve `204 No Content`.
- Restablecer la contraseña revoca todas las sesiones del usuario.
- La actualización de contraseña, el consumo del reset token y la revocación global deben participar de un único resultado atómico; cualquier fallo revierte las tres operaciones.
- El access JWT puede continuar válido hasta 15 minutos si fue robado, límite documentado del diseño stateless.
- Incorporar códigos uniformes para refresh inválido y CSRF inválido dentro de `ApiError`, sin exponer si un secreto fue reutilizado.

Pruebas backend mínimas de la etapa:

- Verificar nombres, paths, `HttpOnly`, `SameSite`, `Secure`, `Max-Age` y eliminación de cookies por perfil.
- Verificar que login y refresh no exponen tokens en el body y que Bearer deja de aceptarse.
- Verificar CSRF ausente, inválido y válido en operaciones mutantes.
- Verificar sesiones independientes, rotación, expiración, reutilización, refresh concurrente y logout selectivo e idempotente.
- Verificar contra la base que `revokedAt` permanece persistido después de responder el error de reutilización o concurrencia.
- Verificar que el reset de contraseña consume el token y revoca todas las sesiones en la misma transacción.

La arquitectura, los flujos y las pruebas manuales complementarias se detallan en `GUIA_ETAPA_3_COOKIES_Y_SESIONES.md`.

**Criterio de salida:** login, navegación autenticada, refresh y logout funcionan sin exponer tokens a JavaScript y las mutaciones sin CSRF reciben `403`.

**Commit de cierre propuesto:** `feat(auth): implement cookie-based authentication sessions`

Incluye endpoints de sesión, cookies, CSRF, filtro de access JWT, rotación y revocación de refresh tokens, concurrencia y pruebas backend del ciclo de autenticación.

### Etapa 4 — Usuarios, recuperación y email

- Implementar `POST /api/admin/users` para alta administrativa.
- Aceptar email, contraseña inicial y rol; solamente `SUPER_ADMIN` puede crear otros `SUPER_ADMIN`.
- Exponer un `UserResponse` mínimo con `id`, `email`, `role`, `enabled`, `createdAt` y `updatedAt`.
- Mantener cuentas habilitables/deshabilitables desde el modelo, aunque la v1 solo incluya el alta administrativa.
- Hacer reset de contraseña atómico, con TTL único y consumo de un solo uso.
- Mantener respuesta anti-enumeración en forgot-password.
- Separar el puerto de envío de email de SMTP/Thymeleaf.
- Si SMTP falla, conservar una respuesta pública no enumerativa, registrar un error operacional sin token y permitir solicitar un nuevo reset.
- No incluir registro público ni verificación de email en la v1.

**Criterio de salida:** bootstrap, alta administrativa, login, perfil y recuperación están cubiertos por pruebas de autorización y persistencia.

**Commit de cierre propuesto:** `feat(users): add administrative provisioning and password recovery`

Incluye alta administrativa, modelo público de usuario, estado de cuenta, recuperación/restablecimiento transaccional, revocación de sesiones y envío de email desacoplado.

### Etapa 5 — Shell React

- Crear React/Vite sin componentes de negocio de Rifas.
- Incluir login, forgot/reset password, layout autenticado, perfil básico, rutas protegidas y logout.
- Configurar el proxy de Vite `/api -> http://localhost:8080` para conservar un mismo sitio también en desarrollo.
- Usar `credentials: "include"` en el cliente HTTP.
- Inicializar CSRF antes del primer `POST`.
- Leer `XSRF-TOKEN` y enviar `X-XSRF-TOKEN` en métodos mutantes.
- Restaurar sesión mediante `/api/auth/me`; no utilizar `localStorage` ni `sessionStorage` para autenticación.
- Ante `401`, ejecutar un único refresh compartido entre solicitudes concurrentes y reintentar cada petición como máximo una vez.
- No intentar refresh para login, refresh, logout o errores definitivos.
- Mantener la sesión visible hasta confirmar logout; si falla la revocación, informar al usuario en vez de simular un cierre seguro.

**Criterio de salida:** recargar la página conserva la sesión, un access expirado se renueva transparentemente y un refresh inválido dirige al login.

**Commit de cierre propuesto:** `feat(frontend): add cookie-based authentication shell`

Incluye únicamente el shell React, cliente HTTP con cookies y CSRF, estado de autenticación, refresh single-flight, pantallas y rutas protegidas; no incluye componentes de negocio.

### Etapa 6 — Alta administrativa integrada con React

Completar el flujo de alta administrativa desde el frontend, adaptando el backend incorporado en la etapa 4 al nuevo contrato de autorización.

**Contrato de autorización:** únicamente el superusuario (`SUPER_ADMIN`) puede realizar altas administrativas y crear cuentas con rol `USER`, `ADMIN` o `SUPER_ADMIN`. `ADMIN` y `USER` no pueden crear ninguna cuenta, ni desde la interfaz ni mediante llamadas directas a la API. Esta regla reemplaza los permisos de alta implementados en la etapa 4.

**Estado de implementación (12/09/2026):**

- Backend implementado: `AdminUserController` expone `POST /api/admin/users`, recibe `email`, `password` y `role`, valida el request y responde `201 Created` con `UserResponse`, sin contraseña ni hash.
- `AdminUserServiceImpl` normaliza el email, codifica la contraseña y persiste el usuario habilitado dentro de una transacción. La contraseña inicial se valida entre 8 y 72 caracteres.
- La autorización HTTP, el controlador y el servicio restringen el alta a `SUPER_ADMIN`. `ADMIN` y `USER` no pueden crear cuentas de ningún rol. El endpoint conserva autenticación y CSRF.
- `AdminUserProvisioningIntegrationTest` verifica los tres roles de destino, login y perfil de las cuentas creadas, rechazos sin persistencia para `ADMIN` y `USER`, validaciones, normalización, hash, ausencia de contraseña en la respuesta, duplicados, autenticación y CSRF. `AdminUserServiceTest` verifica el rechazo al invocar directamente el servicio sin pasar por HTTP.
- Frontend implementado: pantalla `/app/admin/users/new`, navegación y ruta exclusivas de `SUPER_ADMIN`, formulario de email/contraseña/rol, cliente HTTP con cookies y CSRF, confirmación pública, limpieza de contraseña y manejo de errores y envíos simultáneos.
- Verificación ejecutada: 7 pruebas backend de alta y permisos aprobadas con Java 21 y perfil `test` (H2); 22 pruebas frontend aprobadas; lint y build de React aprobados. La prueba backend verifica alta, login y perfil mediante HTTP simulado (MockMvc); las pruebas frontend verifican formulario, roles y transporte HTTP con respuestas simuladas. No se realizó una prueba completa en navegador contra MySQL.

**Alcance de esta etapa:**

- Restringir el alta a `SUPER_ADMIN` en el controlador y el servicio backend, y ajustar las reglas HTTP de seguridad que correspondan. Mantener autenticación y CSRF; rechazar con `403` los intentos de alta de `ADMIN` y `USER`, independientemente del rol solicitado.
- Actualizar y ejecutar las pruebas backend antes de integrar la pantalla: verificar que `SUPER_ADMIN` puede crear los tres roles y que `ADMIN` y `USER` no pueden crear ninguno, sin persistir cuentas tras un rechazo. Sustituir las expectativas anteriores que permitían altas a `ADMIN` y completar las validaciones de entrada pendientes.
- Agregar el endpoint y un servicio frontend para `POST /api/admin/users`, reutilizando el cliente HTTP con cookies, CSRF y manejo uniforme de errores.
- Incorporar una pantalla de alta dentro del layout autenticado, con acceso de navegación visible únicamente para `SUPER_ADMIN` y una ruta protegida por ese rol, incluso al ingresar la URL directamente. El backend mantiene la autorización definitiva.
- Crear un formulario con email, contraseña inicial y rol, validaciones coherentes con el contrato backend y mensajes por campo.
- Ofrecer al superusuario los roles `USER`, `ADMIN` y `SUPER_ADMIN` en el selector del formulario.
- Mostrar estado de envío, evitar envíos duplicados y presentar confirmación con los datos públicos del usuario creado; limpiar la contraseña tras el alta exitosa, sin persistirla en almacenamiento web ni registrarla en logs.
- Manejar email duplicado (`409`), validaciones (`400`), falta de permisos (`403`), sesión vencida y errores de red, conservando mensajes comprensibles y sin mostrar una creación exitosa si la petición falla.
- Verificar el flujo integrado: crear una cuenta desde React, cerrar la sesión administrativa e iniciar sesión con la cuenta nueva, comprobando su perfil y rol.
- Incorporar pruebas frontend para acceso por rol, opciones del selector, envío exitoso y manejo de errores; ejecutar lint, tests y build, junto con las pruebas backend afectadas.
- Mantener el alcance en el alta administrativa: listado, edición, eliminación y habilitación/deshabilitación desde la interfaz quedan fuera de esta etapa.

**Criterio de salida:** únicamente `SUPER_ADMIN` puede crear cuentas desde React y la API, con cualquiera de los tres roles; `ADMIN` y `USER` no pueden acceder al alta ni crear cuentas mediante peticiones directas. La cuenta creada puede iniciar sesión y los errores de validación, duplicados y autorización se muestran correctamente. Pruebas backend y frontend relevantes, lint y build aprobados.

**Commit de cierre propuesto:** `feat(admin): add administrative user creation flow`

Incluye la pantalla, navegación y protección exclusiva para `SUPER_ADMIN`, integración con el endpoint existente y pruebas del flujo de alta. El cambio de autorización backend y sus pruebas se realizan antes en un commit separado y explícito.

### Etapa 7 — Cambio de contraseña desde el perfil

Permitir que cualquier usuario autenticado (`USER`, `ADMIN` o `SUPER_ADMIN`) cambie su propia contraseña desde el perfil, sin solicitar un enlace por correo.

**Estado de cierre (13/09/2026):** implementada y verificada, sin crear commit por indicación del usuario. Pasaron 28 pruebas backend de cambio/recuperación (incluidas 11 contra MySQL 8.4), 30 pruebas frontend, lint, build de React y empaquetado del backend. También se comprobó en Edge con backend y MySQL aislados: dos sesiones, cambio desde el perfil, confirmación en login, rechazo de contraseña anterior, acceso con la nueva, rechazo del refresh de la segunda sesión y vigencia residual del access anterior. El proxy temporal de la prueba presentó el origen permitido por el backend; no se modificó CORS productivo.

**Implementación y reutilización:**

- Existe `POST /api/auth/reset-password`, público y protegido por CSRF, que recibe `token` y `newPassword`. `PasswordResetServiceImpl` valida el token de recuperación, actualiza el hash, consume el token y revoca las sesiones dentro de una transacción. Su contrato requiere demostrar acceso al enlace de recuperación.
- `POST /api/auth/change-password` y el formulario de perfil están implementados. `PasswordChangeService` verifica la contraseña actual y limita los intentos; `PasswordUpdateService` comparte actualización e invalidación con la recuperación por correo.
- Flyway `V2__password_change_attempts.sql` agrega a `users` el contador de fallos y el inicio de ventana. Se admiten cinco verificaciones fallidas por cuenta en 15 minutos; los intentos siguientes reciben `429` con `Retry-After` hasta finalizar la ventana. El contador se persiste en MySQL y no se reinicia cambiando de instancia.
- Login, cambio de contraseña y reset coordinan el acceso mediante bloqueo del usuario. Reset vuelve a comprobar el token bajo bloqueo; la revocación global incrementa las versiones de sesiones para impedir que un refresh concurrente restaure una sesión revocada.
- La política compartida exige al menos ocho caracteres y un máximo de 72 bytes UTF-8, acorde con BCrypt. No se recortan contraseñas. La confirmación de éxito se conserva en el contexto React durante la redirección al login; un resultado incierto de red no provoca reenvío automático.
- Mantener el contrato público de reset. No volver opcional el token, generar uno artificial para el perfil ni permitir elegir entre cookie y token dentro del mismo endpoint.
- Reutilizar `PasswordEncoder`, persistencia de usuario, `AuthSessionService.revokeAllForUser`, eliminación de cookies y el cliente HTTP React. Extraer una operación interna compartida para actualizar la contraseña e invalidar credenciales cuando corresponda, manteniendo separadas las verificaciones de identidad: token de recuperación para reset y contraseña actual para cambio autenticado. La extracción debe conservar la atomicidad de ambos flujos y sus pruebas existentes.

**Contrato implementado:**

- Agregar `POST /api/auth/change-password`, con autenticación y CSRF obligatorios, para todos los roles. Recibir únicamente `currentPassword` y `newPassword`; determinar el usuario desde el principal autenticado, nunca desde un email o identificador enviado por el cliente. Este endpoint no permite cambiar contraseñas de terceros, tampoco a `SUPER_ADMIN`.
- Verificar la contraseña actual con `PasswordEncoder.matches` contra el hash vigente en base de datos y comprobar que la cuenta siga habilitada. Una cookie válida por sí sola no autoriza el cambio.
- Validar la contraseña nueva en el backend, compartir la política con alta y reset y rechazar una contraseña igual a la actual. Revisar los límites reales del encoder antes de fijar validaciones uniformes; no recortar ni transformar contraseñas silenciosamente. La confirmación de contraseña se valida en React para evitar errores de escritura.
- Ante contraseña actual incorrecta, responder `400` con un código específico de `ApiError` y error de campo, sin modificar contraseña, tokens o sesiones. Reservar `401` para falta de autenticación válida y `403` para CSRF o autorización; evitar que un error de contraseña dispare el refresh automático del cliente.
- En una única transacción, guardar el hash nuevo, invalidar los tokens de recuperación pendientes y revocar todas las sesiones de refresh del usuario, incluida la actual. Ante un fallo, revertir todas esas operaciones. Coordinar cambios/reset concurrentes y carreras con login/refresh para impedir que credenciales anteriores creen o reactiven sesiones después del cambio.
- Tras confirmar la transacción, responder `204 No Content` y eliminar `ACCESS_TOKEN`, `REFRESH_TOKEN` y `XSRF-TOKEN` mediante `AuthCookieService`. No emitir una sesión nueva automáticamente. React limpia su estado autenticado y dirige al login con una confirmación para ingresar con la contraseña nueva.
- El cambio no requiere envío de email ni depende de SMTP. Una notificación informativa posterior, sin contraseña ni enlaces de autorización, puede evaluarse como mejora independiente.

**Riesgos y controles:**

- Sesión robada o equipo desbloqueado: quien pueda usar una sesión no debe poder tomar control permanente de la cuenta solo con esa sesión. Exigir la contraseña actual reduce ese riesgo; limitar los intentos de verificación por cuenta con espera temporal y respuesta `429`, sin bloquear permanentemente la cuenta. Se permiten cinco verificaciones fallidas por cuenta en una ventana de 15 minutos, con contador persistido en MySQL compartido entre instancias. La reautenticación para cambios sensibles sigue la guía de [OWASP Authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html#change-password-feature).
- CSRF y XSS: conservar HTTPS, cookies `Secure`/`HttpOnly`/`SameSite` y validación de `X-XSRF-TOKEN`. `HttpOnly` impide leer la cookie desde JavaScript, pero un script malicioso dentro del sitio puede enviar peticiones autenticadas e incluso capturar lo escrito en el formulario. Las cookies y la contraseña actual no sustituyen la prevención de XSS. Ver [OWASP Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html#httponly-attribute) y [OWASP CSRF Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html).
- Access JWT anteriores: actualmente `JwtRequestFilter` valida el JWT y carga el usuario, pero no consulta la revocación de la sesión ni una versión de credenciales. Revocar refresh y borrar cookies locales no invalida un access token copiado: puede seguir autorizando peticiones hasta su vencimiento (15 minutos por defecto, según configuración). Esta etapa conserva ese límite del diseño y debe documentarlo sin prometer un cierre inmediato en todos los dispositivos. Si se requiere invalidación inmediata, ampliar primero el contrato con una versión de credenciales o validación de sesión en cada petición, aplicada también al reset y a las carreras con login/refresh.
- Enlaces de recuperación anteriores: invalidarlos en el cambio evita que un enlace pendiente pueda utilizarse posteriormente para reemplazar la contraseña nueva.
- Fuga de secretos y respuestas perdidas: no guardar contraseñas en storage, URLs, logs ni mensajes de error. Deshabilitar envíos simultáneos. Si se pierde la respuesta de red, no afirmar que el cambio falló ni repetirlo automáticamente: informar que el resultado no pudo confirmarse y permitir volver al login o a recuperación.

**Frontend y pruebas:**

- Agregar en el perfil la sección **Cambiar contraseña**, con contraseña actual, nueva y confirmación, usando `autocomplete="current-password"` y `autocomplete="new-password"`, validaciones, errores por campo y estado de envío. Avisar antes de confirmar que deberá volver a ingresar y que las demás sesiones dejarán de poder renovarse.
- Reutilizar el cliente HTTP con cookies y CSRF; permitir únicamente sus reintentos controlados ante rechazo de autenticación/CSRF previo al cambio, sin reintentos automáticos ante errores de red. Limpiar los campos al completar o abandonar el formulario. Actualizar el contexto autenticado solo después de una respuesta exitosa; no depender de un segundo logout para confirmar una operación ya realizada.
- Probar el backend para todos los roles sobre su propia cuenta, imposibilidad de afectar otra cuenta, contraseña actual incorrecta, validación de nueva contraseña, autenticación/CSRF, límite de intentos, rollback, concurrencia, invalidación de reset tokens, revocación de refresh, eliminación de cookies y el límite documentado de los access JWT anteriores. Verificar que el login rechaza la contraseña anterior y acepta la nueva.
- Probar en React confirmación, validaciones, errores por campo, sesión vencida, espera por límite de intentos, errores de red sin falso éxito, prevención de doble envío y regreso al login tras éxito. Ejecutar las pruebas de recuperación existentes para comprobar que la reutilización no altera su contrato.
- Validar el flujo completo desde el perfil contra el backend y MySQL, incluyendo una segunda sesión, y ejecutar las pruebas afectadas, lint y build.

**Criterio de salida:** cualquier usuario autenticado puede cambiar exclusivamente su contraseña presentando la actual, sin email; la contraseña anterior, los refresh y los enlaces de recuperación previos dejan de ser utilizables tras el cambio, se solicita un nuevo login y queda explícita la vigencia residual de los access JWT. Pruebas relevantes, lint, build y flujo integrado aprobados.

**Commit de cierre propuesto:** `feat(auth): add authenticated password change from profile`

Incluye endpoint autenticado, reutilización transaccional de lógica de contraseña, controles de intentos, formulario de perfil, manejo de sesión, documentación y pruebas; no incluye administración de contraseñas ajenas.

El contrato para una futura verificación en dos pasos por correo, incluyendo diagramas y activación/desactivación, está en [PLAN_DOBLE_FACTOR_EMAIL.md](PLAN_DOBLE_FACTOR_EMAIL.md). Esa funcionalidad no forma parte de la implementación de esta etapa.

### Etapa 8 — OpenAPI, observabilidad y operación local

- Incorporar OpenAPI/Swagger compatible con Spring Boot 4.
- Documentar cookies, CSRF, respuestas y códigos de error.
- Habilitar Swagger en desarrollo/test y deshabilitarlo por defecto en producción.
- Exponer públicamente únicamente Actuator health; proteger los demás endpoints operativos.
- Mantener logs estructurados con rotación y correlación por `requestId`.
- Añadir Docker Compose local con MySQL y Mailpit, sin credenciales reales.
- Proporcionar `.env.example`, README de arranque y guía de configuración HTTPS/proxy.

**Criterio de salida:** una persona puede clonar la plantilla, levantar infraestructura local y completar el flujo de autenticación siguiendo únicamente el README.

**Commit de cierre propuesto:** `feat(ops): add API documentation observability and local stack`

Incluye OpenAPI, Actuator, logs operativos, Docker Compose, `.env.example` y documentación de ejecución y despliegue local.

### Etapa 9 — Verificación y automatización

Backend:

- Unit tests para normalización, JWT, cookies, hash y mapeos.
- Integraciones con Testcontainers MySQL.
- Login correcto/incorrecto y usuario deshabilitado.
- Cookies y atributos por perfil.
- CSRF ausente, inválido y válido.
- Refresh expirado, rotado, reutilizado y concurrente.
- Sesiones independientes y logout selectivo.
- Reset de contraseña y revocación global.
- Cambio autenticado de contraseña, reautenticación, límite de intentos e invalidación de credenciales anteriores según el contrato de la etapa 7.
- Matriz de roles y contrato uniforme de errores.

Frontend:

- Tests del contexto de autenticación y rutas protegidas.
- CSRF en mutaciones.
- Refresh automático y single-flight.
- Ausencia de tokens en almacenamiento web.
- Logout exitoso y fallo de red.

CI:

- Maven verify.
- Lint, tests y build de React.
- Testcontainers.
- Comprobación de migraciones desde una base vacía.

**Criterio final:** CI verde, ningún token en body/log/storage, migraciones repetibles y flujos completos validados contra MySQL.

**Commit de cierre propuesto:** `test(ci): enforce authentication quality gates`

Incluye las suites finales de backend y frontend, escenarios con Testcontainers y el pipeline de CI. No debe contener correcciones funcionales pendientes de etapas anteriores; si las hubiera, se corrigen primero en un commit separado y explícito.

## Interfaces públicas principales

- `AppProperties`: punto central tipado para la configuración propia de JWT, mail y bootstrap; se ampliará únicamente cuando aparezca una necesidad concreta.
- `LoginResponse`: contiene solamente `user` y metadatos no sensibles de expiración.
- `UserResponse`: `id`, `email`, `role`, `enabled`, `createdAt`, `updatedAt`.
- `ApiError`: contrato común indicado en la etapa 2.
- Las cookies son el único transporte de credenciales.
- React nunca recibe ni administra access o refresh tokens.
- Los endpoints públicos se limitan a CSRF, login, refresh, recuperación/reset, documentación habilitada por perfil y health.

## Supuestos y límites

- La plantilla será una aplicación clonable, no un auto-configuring Maven Starter.
- Producción publicará React y `/api` bajo el mismo sitio mediante proxy inverso y HTTPS.
- MySQL es la única base soportada oficialmente.
- `AppProperties` no contendrá configuración de datasource, JPA, Flyway, logging ni Actuator.
- `seed_users.json`, `General` y las propiedades específicas de Rifas no formarán parte de la configuración inicial.
- No se incluyen registro público, verificación de email, roles dinámicos, auditoría persistida ni lógica de Rifas.
- OpenAPI, logs y health checks forman parte de la v1 estable.
- La implementación debe avanzar en orden; cada criterio de salida funciona como puerta antes de comenzar la etapa siguiente.
