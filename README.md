# Spring Starter

Plantilla clonable con backend Spring Boot y frontend React/Vite. La implementación se desarrolla por etapas según [PLAN_EJECUCION_STARTER.md](PLAN_EJECUCION_STARTER.md).

## Requisitos

- Java 21.
- Node.js 22 y npm 10.

Maven 3.9.16 se obtiene automáticamente mediante Maven Wrapper.

## Backend

Desde `Backend/spring-starter`:

```powershell
$env:JAVA_HOME='C:\ruta\al\jdk-21'
.\mvnw.cmd test
.\mvnw.cmd package
```

En Linux o macOS:

```bash
export JAVA_HOME=/ruta/al/jdk-21
./mvnw test
./mvnw package
```

El test de contexto activa el perfil `test` y utiliza una base H2 en memoria, por lo que no requiere variables de producción.

## Frontend

Desde `Frontend/react-starter`:

```powershell
npm.cmd install
npm.cmd run lint
npm.cmd run build
```

En Linux o macOS puede utilizarse `npm` en lugar de `npm.cmd`.

## Alta administrativa

Solo una cuenta con rol `SUPER_ADMIN` puede dar de alta usuarios. Al iniciar sesión con ese rol aparece **Crear usuario** en la navegación, en `/app/admin/users/new`.

El formulario permite ingresar email, contraseña inicial (8 a 72 caracteres) y rol (`USER`, `ADMIN` o `SUPER_ADMIN`). Tras confirmar el alta, muestra el email y rol creados y limpia la contraseña. Los roles `ADMIN` y `USER` no tienen acceso a la pantalla y la API rechaza sus intentos con `403`.

Para verificar el flujo manualmente, crear una cuenta desde esa pantalla, cerrar sesión e ingresar con las credenciales nuevas; el perfil debe mostrar el email y rol elegidos. Un email duplicado debe mostrar un error sin confirmar otra creación.

Pruebas específicas del backend (desde `Backend/spring-starter`, con Java 21):

```powershell
.\mvnw.cmd '-Dtest=AdminUserProvisioningIntegrationTest,AdminUserServiceTest' test
```

Pruebas del frontend (desde `Frontend/react-starter`):

```powershell
npm.cmd test
npm.cmd run lint
npm.cmd run build
```

## Cambio de contraseña desde el perfil

Todos los roles pueden cambiar su propia contraseña en **Perfil → Cambiar contraseña**, indicando contraseña actual, nueva y confirmación. No se envía correo. La política es de al menos 8 caracteres y un máximo de 72 bytes UTF-8 para la contraseña nueva, sin recorte silencioso.

Tras confirmar el cambio, se solicita un nuevo login, se revocan los refresh de todas las sesiones y se invalidan los enlaces de recuperación pendientes. Los access JWT ya emitidos conservan su vigencia hasta vencer (15 minutos por defecto); no se promete invalidación inmediata en otros dispositivos.

El endpoint `POST /api/auth/change-password` exige autenticación y CSRF y recibe `currentPassword` y `newPassword`. Una contraseña actual incorrecta produce `400` con error de campo. Después de cinco verificaciones fallidas por cuenta dentro de una ventana de 15 minutos, devuelve `429` y `Retry-After`. Los contadores se comparten mediante MySQL. Flyway aplica automáticamente la migración V2 al arrancar; no se requiere modificar tablas manualmente.

Verificación del backend, con Docker activo para ejecutar también MySQL:

```powershell
.\mvnw.cmd '-Dtest=PasswordChangeIntegrationTest,PasswordChangeMySqlIntegrationTest,PasswordResetAtomicityIntegrationTest,PasswordRecoveryIntegrationTest' test
```

Para comprobarlo manualmente, abrir dos sesiones del mismo usuario en navegadores o perfiles independientes, cambiar la contraseña en uno y volver a ingresar con la nueva. La contraseña anterior debe fallar y el refresh de la otra sesión debe ser rechazado. Si se pierde la respuesta del cambio, la interfaz informa que el resultado no pudo confirmarse y permite volver al login o recuperar acceso, sin reenviar automáticamente.

El diseño de verificación en dos pasos por correo está en [PLAN_DOBLE_FACTOR_EMAIL.md](PLAN_DOBLE_FACTOR_EMAIL.md); todavía no está implementado.
