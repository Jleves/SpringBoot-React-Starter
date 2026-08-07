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

Comportamiento de sesión:

- Cada login crea una sesión independiente.
- Refresh rota el secreto dentro de la misma sesión y se ejecuta transaccionalmente.
- Si se reutiliza un secreto anterior o no coincide su hash, se revoca esa sesión.
- Dos refresh concurrentes no pueden producir dos tokens válidos.
- Logout revoca solamente la sesión actual.
- Restablecer la contraseña revoca todas las sesiones del usuario.
- El access JWT puede continuar válido hasta 15 minutos si fue robado, límite documentado del diseño stateless.

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

### Etapa 6 — OpenAPI, observabilidad y operación local

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

### Etapa 7 — Verificación y automatización

Backend:

- Unit tests para normalización, JWT, cookies, hash y mapeos.
- Integraciones con Testcontainers MySQL.
- Login correcto/incorrecto y usuario deshabilitado.
- Cookies y atributos por perfil.
- CSRF ausente, inválido y válido.
- Refresh expirado, rotado, reutilizado y concurrente.
- Sesiones independientes y logout selectivo.
- Reset de contraseña y revocación global.
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
