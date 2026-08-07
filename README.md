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
