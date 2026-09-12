# Análisis pedagógico de la arquitectura del frontend React

## 1. Alcance y conclusión general

Este documento describe el código que existe actualmente en `Frontend/react-starter`. Para explicar el contrato de seguridad se contrastaron también las clases Spring Boot que implementan los endpoints y las cookies; no se analiza el backend completo ni se propone reemplazar la solución actual.

La aplicación es un frontend pequeño de autenticación construido con React 19, React Router, Vite y Tailwind CSS. Su arquitectura no es una arquitectura por capas estricta como podría ser una aplicación Spring grande. Combina dos formas de organización:

- **Por feature**: `src/auth` agrupa páginas, componentes, estado contextual y servicios relacionados con autenticación.
- **Por responsabilidad técnica compartida**: `src/services` contiene la infraestructura HTTP reutilizable.

El flujo dominante es:

```text
Página o Provider React
        ↓
authService (operación de autenticación)
        ↓
apiRequest (infraestructura HTTP y seguridad del cliente)
        ↓
fetch + cookies administradas por el navegador
        ↓
Backend Spring Boot
```

No todas las operaciones pasan por un custom hook. `useAuth` sirve para acceder al estado/contexto de sesión, pero no es un hook genérico de fetching. Por ejemplo, recuperación de contraseña llama al service directamente desde la página.

Analogía aproximada con Spring:

- Una **página/componente React** mezcla parte de lo que en una aplicación server-side serían controller web, modelo de vista y template. No es una equivalencia exacta porque el componente también conserva estado y reacciona a eventos en el navegador.
- `authService.js` se parece a una **fachada de aplicación o service de cliente**, pero no contiene reglas de negocio sensibles: estas siguen en Spring.
- `apiClient.js` se parece a un **cliente HTTP configurado/interceptor centralizado** (`RestClient`, `WebClient` más filtros), aunque está implementado como funciones.
- `AuthProvider` se parece parcialmente a un **componente con alcance de aplicación que expone estado**, no a un bean singleton tradicional: participa del ciclo de render de React.

## 2. Estructura completa del frontend

```text
Frontend/react-starter/
├── index.html                    punto HTML de montaje
├── package.json                  dependencias y scripts
├── package-lock.json             versiones exactas instalables
├── vite.config.js                build, proxy y tests
├── eslint.config.js              análisis estático
├── README.md                     presentación y comandos
├── public/
│   └── vite.svg                  recurso estático público
└── src/
    ├── main.jsx                  bootstrap de React
    ├── App.jsx                   componente raíz mínimo
    ├── AppRouter.jsx             no existe aquí; está en app/
    ├── index.css                 Tailwind y estilos globales
    ├── assets/
    │   └── react.svg             asset importable, actualmente sin uso
    ├── app/
    │   ├── AppRouter.jsx         tabla declarativa de rutas
    │   ├── ProtectedRoute.jsx    guard de rutas privadas
    │   ├── PublicOnlyRoute.jsx   guard de login
    │   ├── LoadingScreen.jsx     UI de restauración
    │   ├── AppLayout.jsx         shell privado y logout
    │   ├── ProfilePage.jsx       página privada
    │   └── *.test.jsx            pruebas de rutas/layout
    ├── auth/
    │   ├── components/           piezas visuales reutilizables de auth
    │   ├── context/              estado global de autenticación
    │   ├── pages/                login y recuperación de contraseña
    │   └── services/             operaciones remotas de autenticación
    ├── services/                 infraestructura HTTP compartida
    └── test/setup.js             setup común de Vitest
```

No existen actualmente carpetas `hooks`, `utils`, `api` o un store Redux/Zustand. Sí existe un custom hook dentro de `auth/context`, una función auxiliar junto a los componentes y un API client dentro de `services`.

## 3. Capas y responsabilidades reales

### 3.1 Bootstrap y composición raíz

#### `index.html`

- **Responsabilidad**: entrega el documento HTML base, el nodo `<div id="root">` y carga `src/main.jsx` como módulo.
- **Quién lo usa**: Vite y el navegador.
- **Dependencias**: `/src/main.jsx` y el favicon de `public`.
- **Clasificación**: configuración/host HTML.
- **Concepto**: no es un patrón de aplicación; es el punto de entrada web.

#### `src/main.jsx`

- **Responsabilidad**: crea la raíz React y compone los providers globales.
- **Quién lo usa**: `index.html` lo carga directamente.
- **Dependencias**: React, React DOM, `BrowserRouter`, `AuthProvider`, `App` e `index.css`.
- **Clasificación**: bootstrap y composition root.
- **Función principal/orquestadora**: la expresión `createRoot(...).render(...)` arma el entorno completo.
- **Patrón/principio**: **Composition** y **Provider**. La jerarquía importa: `App` puede usar routing y autenticación porque está debajo de ambos providers.

```text
StrictMode
└── BrowserRouter
    └── AuthProvider
        └── App
```

`StrictMode` ayuda a detectar usos inseguros durante desarrollo. No representa una pantalla. `BrowserRouter` provee contexto de navegación; `AuthProvider`, contexto de autenticación.

#### `src/App.jsx`

- **Responsabilidad**: componente raíz que delega todo el routing a `AppRouter`.
- **Quién lo usa**: `main.jsx`.
- **Dependencias**: `AppRouter`.
- **Clasificación**: componente de composición.
- **Observación**: es deliberadamente mínimo. No agrega lógica ni constituye por sí mismo una capa.

### 3.2 Routing y shell de aplicación (`src/app`)

#### `AppRouter.jsx`

- **Responsabilidad**: declara rutas, redirecciones, layouts anidados y guards.
- **Quién lo usa**: `App.jsx`.
- **Dependencias**: React Router; las tres páginas de auth; `ProtectedRoute`, `PublicOnlyRoute`, `AppLayout` y `ProfilePage`.
- **Clasificación**: routing/configuración expresada como componente.
- **Función principal**: `AppRouter` es un componente orquestador; no dibuja una pantalla propia, decide qué árbol renderizar según la URL.

Rutas reales:

| URL | Protección | Elemento |
| --- | --- | --- |
| `/login` | solo anónimos | `LoginPage` |
| `/forgot-password` | pública | `ForgotPasswordPage` |
| `/reset-password` | pública | `ResetPasswordPage` |
| `/app` | autenticados | redirige a `profile` |
| `/app/profile` | autenticados | `AppLayout` + `ProfilePage` |
| `/` | redirección | `/app/profile` |
| cualquier otra | redirección | `/` |

Las rutas anidadas usan `<Outlet />`: equivale a un punto de inserción donde React Router coloca la ruta hija. Es composición, comparable de manera aproximada a un layout/template con un fragmento variable.

#### `ProtectedRoute.jsx`

- **Responsabilidad**: impedir que la UI privada se renderice sin autenticación.
- **Quién lo usa**: `AppRouter`.
- **Dependencias**: `useAuth`, `useLocation`, `Navigate`, `Outlet` y `LoadingScreen`.
- **Clasificación**: componente de control de navegación/seguridad de UI.
- **Función orquestadora**: decide entre loading, redirección y contenido hijo.

Condiciones:

1. `status === 'loading'`: muestra `LoadingScreen` mientras se restaura la sesión.
2. `!isAuthenticated`: redirige a `/login` y guarda `pathname + search` en `state.from`.
3. En otro caso: renderiza el `<Outlet />` privado.

Esto es un **route guard de cliente**, no una barrera de seguridad real. Spring Boot debe seguir autorizando cada endpoint.

#### `PublicOnlyRoute.jsx`

- **Responsabilidad**: evitar que un usuario ya autenticado vuelva al login.
- **Quién lo usa**: `AppRouter`.
- **Dependencias**: `useAuth`, React Router y `LoadingScreen`.
- **Clasificación**: control de navegación/UX.
- **Condiciones**: espera durante `loading`; si está autenticado redirige al perfil; si no, renderiza el login.

#### `LoadingScreen.jsx`

- **Responsabilidad**: mostrar feedback accesible durante la restauración de sesión.
- **Quién lo usa**: ambos route guards.
- **Dependencias**: ninguna interna.
- **Clasificación**: componente presentacional de UI.
- **Patrón**: no hace falta asignarle un patrón; es una pieza visual sin estado.

#### `AppLayout.jsx`

- **Responsabilidad**: shell de la zona privada: encabezado, navegación, usuario, logout, error y contenido anidado.
- **Quién lo usa**: `AppRouter`.
- **Dependencias**: `useState`, React Router, `RequestError` y `useAuth`.
- **Clasificación**: UI, layout y pequeña orquestación de interacción.

`handleLogout` es una **función principal/orquestadora local**: limpia el error, activa el indicador, espera `logout`, captura el error y siempre desactiva el indicador. `try/catch/finally` cumple el mismo papel general que en Java.

El estado `error` y `loggingOut` es local al componente. Si el logout falla, `AuthProvider.logout` no alcanza a borrar al usuario, por lo que el layout conserva la sesión visible y muestra el error. El test documenta explícitamente esa conducta.

#### `ProfilePage.jsx`

- **Responsabilidad**: mostrar el usuario autenticado (`email`, `role`, `id`).
- **Quién lo usa**: `AppRouter` como hija de `AppLayout`.
- **Dependencias**: `useAuth`.
- **Clasificación**: página/UI.
- **Observación**: no consulta el backend al montar; lee el usuario que ya restauró o estableció `AuthProvider`.

Expresiones como `user?.email?.charAt(0)` usan **optional chaining**: si `user` o `email` son `null`/`undefined`, la evaluación devuelve `undefined` en vez de lanzar `NullPointerException`.

### 3.3 Componentes reutilizables de autenticación

#### `AuthCard.jsx`

- **Responsabilidad**: estructura visual común de login y recuperación: panel de marca, tarjeta, títulos, cuerpo y footer.
- **Quién lo usa**: `LoginPage`, `ForgotPasswordPage` y `ResetPasswordPage`.
- **Dependencias**: `Link` de React Router.
- **Clasificación**: componente presentacional/composición.
- **Principio**: reutilización por **Composition**. Recibe `children` en lugar de conocer cada formulario.

`children` es el contenido anidado que el padre coloca entre `<AuthCard>...</AuthCard>`. Las condiciones `eyebrow && ...` y `footer && ...` significan “renderizar solo si el valor existe”.

#### `FormField.jsx`

- **Responsabilidad**: renderizar label, input y error de campo con atributos de accesibilidad consistentes.
- **Quién lo usa**: las tres páginas de autenticación.
- **Dependencias**: ninguna interna.
- **Clasificación**: componente presentacional reutilizable.

La firma `{ label, error, id, ...inputProps }` usa:

- **destructuring** para extraer propiedades por nombre;
- **rest syntax** (`...inputProps`) para reunir las propiedades restantes;
- **spread en JSX** (`{...inputProps}`) para transferirlas al `<input>`.

Así el componente acepta `type`, `value`, `onChange`, `required`, etc., sin enumerar cada atributo.

#### `RequestError.jsx`

- **Responsabilidad**: mostrar el mensaje general y, si existe, el `requestId` de un error.
- **Quién lo usa**: las páginas y `AppLayout`.
- **Dependencias**: ninguna interna.
- **Clasificación**: componente presentacional de errores.
- **Condición principal**: si no hay error retorna `null`, que en React significa “no renderizar nada”.

#### `fieldErrors.js`

- **Responsabilidad**: encontrar el mensaje asociado a un campo dentro de `error.fieldErrors`.
- **Quién lo usa**: las tres páginas de auth.
- **Dependencias**: ninguna.
- **Clasificación**: función auxiliar de presentación/validación remota.
- **Patrón**: ninguno necesario; es simplemente una función auxiliar pura.

La cadena `error?.fieldErrors?.find(...)?.message` combina optional chaining y un callback de búsqueda. El callback `(item) => item.field === field` se ejecuta para cada elemento hasta encontrar uno.

### 3.4 Páginas de autenticación

Las páginas son componentes “contenedor”: coordinan estado local, eventos, navegación y services; luego componen componentes visuales.

#### `LoginPage.jsx`

- **Responsabilidad**: capturar credenciales, ejecutar login, mostrar errores y volver a la URL privada solicitada.
- **Quién lo usa**: `AppRouter`.
- **Dependencias**: hooks de React/Router, componentes de auth, `fieldError` y `useAuth`.
- **Clasificación**: UI + estado local + orquestación de caso de uso.

Estados locales: `form`, `error`, `submitting`. `handleSubmit` evita el submit HTML tradicional, invoca `login(form)`, navega y gestiona error/loading.

El destino usa `location.state?.from || '/app/profile'`: intenta volver a la ruta guardada por `ProtectedRoute`; si no existe usa el perfil.

Al actualizar un campo, `{ ...form, email: nuevoValor }` usa el **spread operator** para crear un objeto nuevo copiando el anterior y reemplazando una propiedad. React necesita este estilo inmutable para detectar correctamente el cambio de estado.

#### `ForgotPasswordPage.jsx`

- **Responsabilidad**: pedir un email, solicitar recuperación y mostrar mensaje o error.
- **Quién lo usa**: `AppRouter`.
- **Dependencias**: React, Router, componentes compartidos, `fieldError` y `requestPasswordReset`.
- **Clasificación**: UI + estado local + orquestación.
- **Flujo real**: Página → `authService.requestPasswordReset` → `apiRequest` → Spring.

No usa `useAuth` porque recuperar contraseña no modifica el estado de sesión del frontend.

#### `ResetPasswordPage.jsx`

- **Responsabilidad**: leer el token de la query string, validarlo, capturar una nueva contraseña y ejecutar el cambio.
- **Quién lo usa**: `AppRouter`.
- **Dependencias**: React, Router, componentes compartidos, auxiliar de error y dos funciones de `authService`.
- **Clasificación**: UI + estado local + orquestación de dos operaciones remotas.

`useEffect` valida el token cuando cambia. La variable local `active` y la función de cleanup forman una **closure**: los callbacks de la Promise conservan acceso a esa variable. Al desmontar el componente se vuelve `false`, evitando actualizar estado con una respuesta tardía. No cancela la petición HTTP; evita aplicar su resultado.

Aquí se usa la API de **Promises** con `.then()` y `.catch()` en lugar de `await`. Ambos estilos representan trabajo asíncrono. `active && setTokenStatus(...)` es una condición corta: solo ejecuta el setter si `active` es verdadero.

`handleSubmit` contiene una validación local: si contraseña y confirmación no coinciden, crea un `Error` común y no llama al backend. Las reglas definitivas de contraseña deben seguir validándose en Spring.

### 3.5 Estado global de autenticación (`src/auth/context`)

#### `AuthContext.js`

- **Responsabilidad**: crear el canal Context que transportará el estado y operaciones de autenticación.
- **Quién lo usa**: `AuthProvider`, `useAuth` y los tests que inyectan valores controlados.
- **Dependencias**: `createContext`.
- **Clasificación**: definición de estado/contexto.
- **Analogía**: es más parecido a declarar el tipo/canal de una dependencia compartida que a implementar el servicio. El valor real lo aporta el Provider.

#### `AuthProvider.jsx`

- **Responsabilidad**: ser la fuente de verdad en memoria sobre el usuario y el estado de autenticación; restaurar sesión; ofrecer `login`/`logout`; reaccionar a expiración.
- **Quién lo usa**: `main.jsx` lo monta; todos los consumidores acceden indirectamente mediante `useAuth`.
- **Dependencias**: hooks React, `apiClient.ensureCsrfToken`, `sessionEvents`, todo `authService` y `AuthContext`.
- **Clasificación**: estado de aplicación + orquestación de autenticación.
- **Funciones principales**: `restoreSession`, `login`, `logout`.

Estado modelado:

- `user`: DTO normalizado o `null`.
- `status`: `'loading'`, `'authenticated'` o `'anonymous'`.
- `isAuthenticated`: valor derivado, no otro estado independiente.

Primer `useEffect`: se suscribe a expiración. El callback pone `user = null` y `status = anonymous`. Como `subscribeToSessionExpired` devuelve una función, React la ejecuta como cleanup al desmontar.

Segundo `useEffect`: restaura la sesión al montar. Primero asegura CSRF, luego consulta `/me`. Si ambos funcionan, autentica la UI; ante cualquier error, queda anónima. La variable `active` evita actualizaciones luego del desmontaje.

`login` y `logout` se envuelven en `useCallback`, que conserva la identidad de las funciones entre renders mientras no cambien sus dependencias. `useMemo` conserva el objeto `value` mientras sus entradas no cambien. Esto evita notificar a consumidores por una identidad nueva sin cambios; no equivale a cachear datos del backend.

La importación `import * as authService` reúne los exports del módulo en un objeto namespace. Se parece a invocar métodos estáticos por nombre, aunque los exports siguen siendo funciones de módulo.

#### `useAuth.js`

- **Responsabilidad**: dar acceso cómodo y seguro a `AuthContext`.
- **Quién lo usa**: páginas, layouts y route guards.
- **Dependencias**: `useContext` y `AuthContext`.
- **Clasificación**: custom hook.
- **Patrón**: **Custom Hook** como fachada de acceso al contexto.

El prefijo `use` informa a React y al linter que se aplican las reglas de hooks. Si no hay Provider, lanza un error explicativo inmediatamente. Sin este hook, cada consumidor repetiría `useContext(AuthContext)` y podría olvidar validar el resultado.

### 3.6 Service de aplicación (`src/auth/services`)

#### `authService.js`

- **Responsabilidad**: expresar operaciones remotas del dominio de autenticación y adaptar el DTO de usuario.
- **Quién lo usa**: `AuthProvider`, `ForgotPasswordPage` y `ResetPasswordPage`.
- **Dependencias**: `apiRequest` y `ENDPOINTS`.
- **Clasificación**: Service Layer del frontend/adaptador del contrato de auth.

Funciones públicas:

| Función | Endpoint | Método | Consumidor |
| --- | --- | --- | --- |
| `login(credentials)` | `AUTH.LOGIN` | POST | `AuthProvider.login` |
| `logout()` | `AUTH.LOGOUT` | POST | `AuthProvider.logout` |
| `getCurrentUser()` | `AUTH.ME` | GET | restauración en `AuthProvider` |
| `requestPasswordReset(email)` | `AUTH.FORGOT_PASSWORD` | POST | `ForgotPasswordPage` |
| `validateResetToken(token)` | endpoint dinámico | GET | `ResetPasswordPage` |
| `resetPassword({...})` | `AUTH.RESET_PASSWORD` | POST | `ResetPasswordPage` |

`normalizeUser` es una **función auxiliar privada del módulo**. Acepta `role` o el nombre legado `rol`, elimina `rol` y `username`, y devuelve una forma uniforme. Usa spread para copiar el objeto antes de borrarle campos, por lo que no muta el objeto recibido.

`login`, además de ser una operación, adapta `response?.user`. Las operaciones públicas de auth (`login`, `logout`, recuperación y reset) configuran `retryAuth: false`: un `401` en ellas debe llegar al llamador, no intentar renovar una sesión que quizá no corresponde.

Este service no almacena tokens ni estado React. El test verifica expresamente que login no escribe en `localStorage` ni `sessionStorage`.

### 3.7 Infraestructura HTTP compartida (`src/services`)

#### `apiClient.js`

- **Responsabilidad**: centralizar `fetch`, serialización, parsing, envío de cookies, CSRF, refresh, reintentos y errores de red.
- **Quién lo usa**: `authService`; `AuthProvider` usa directamente `ensureCsrfToken`; tests prueban sus garantías.
- **Dependencias**: `ApiClientError`, `ENDPOINTS`, `sessionEvents` y APIs del navegador (`fetch`, `document.cookie`, `Headers`, `FormData`).
- **Clasificación**: infraestructura HTTP y coordinación de seguridad del cliente.
- **Función principal**: `apiRequest`.
- **Auxiliares**: `readCookie`, `parseResponse`, `shouldAttemptRefresh`, `refreshSession`, `prepareBody`.
- **Función pública secundaria**: `ensureCsrfToken`.
- **Utilidad exclusiva de tests**: `__resetApiClientForTests`.

`MUTATING_METHODS` es un **Set**: una colección de valores únicos con consulta eficiente mediante `.has()`. Contiene POST/PUT/PATCH/DELETE. `NO_REFRESH_ENDPOINTS` es un array porque se recorre con `.some()` y cada elemento requiere comparaciones más complejas.

`csrfRequest` y `refreshRequest` son variables privadas del módulo. Los módulos ES se evalúan una vez y sus bindings se comparten entre importadores, por lo que funcionan como estado singleton del módulo. Guardan Promises en curso para que requests concurrentes compartan una única llamada.

`apiRequest`:

1. Aplica defaults mediante destructuring de `options`.
2. Normaliza el método y crea headers.
3. Para métodos mutantes asegura CSRF y envía `X-XSRF-TOKEN`.
4. Ejecuta `fetch` con `credentials: 'include'`, haciendo que el navegador incluya cookies válidas para esa URL.
5. Convierte objetos a JSON, pero conserva string/FormData.
6. Propaga `AbortError`; convierte otros fallos de red a `ApiClientError` status 0.
7. Interpreta 204, blob, texto o JSON.
8. Si `response.ok`, devuelve el body interpretado.
9. Si recibe `403 + CSRF_TOKEN_INVALID`, fuerza un CSRF nuevo y reintenta una vez.
10. Si recibe `401`, el endpoint admite refresh y no fue reintentado, renueva la sesión y reintenta una vez.
11. En los demás casos lanza `ApiClientError`.

Las opciones internas `_csrfRetried` y `_authRetried` son marcas de control. En el reintento `{ ...options, _authRetried: true }` copia opciones originales y sobrescribe la marca. Evitan recursión infinita.

`async/await` es sintaxis sobre Promises: una función `async` siempre devuelve una Promise; `await` suspende esa función hasta que la Promise se resuelve o lanza su rechazo como excepción. No bloquea el thread del navegador como `Thread.sleep`.

#### `ApiClientError.js`

- **Responsabilidad**: adaptar el contrato de error HTTP a una excepción uniforme.
- **Quién lo usa**: `apiClient` lo crea; páginas y componentes consumen sus propiedades.
- **Dependencias**: clase nativa `Error`.
- **Clasificación**: objeto/clase de infraestructura.
- **Patrón**: adaptación del error remoto, sin necesidad de llamarlo un patrón GoF completo.

Expone `status`, `code`, `fieldErrors`, `requestId` y `data`, además de `message`. `data?.message` usa optional chaining. Un error de red tiene status 0; un error HTTP conserva el status de Spring.

#### `endpoints.js`

- **Responsabilidad**: centralizar rutas de autenticación y construir la URL dinámica de validación.
- **Quién lo usa**: `authService` y `apiClient`.
- **Dependencias**: `URLSearchParams` del navegador.
- **Clasificación**: configuración de infraestructura.

`Object.freeze` evita modificar los objetos accidentalmente. Se aplica en ambos niveles porque el freeze es superficial. `VALIDATE_RESET_TOKEN` es una función, no una constante URL, porque necesita codificar el token recibido.

#### `sessionEvents.js`

- **Responsabilidad**: desacoplar la detección técnica de refresh fallido de la actualización de estado React.
- **Quién lo usa**: `apiClient` publica; `AuthProvider` se suscribe.
- **Dependencias**: ninguna.
- **Clasificación**: infraestructura de eventos en memoria.
- **Patrón**: **Observer / Pub-Sub** simple.

`listeners` es un Set privado de callbacks. `subscribeToSessionExpired(listener)` registra una función y devuelve otra función que la elimina. Esa función retornada es una **closure**: recuerda qué `listener` debe borrar. `publishSessionExpired()` itera y ejecuta cada callback.

### 3.8 Estilos, configuración y tests

#### `index.css`

- Importa Tailwind, define la fuente temática y estilos base mínimos.
- Es configuración visual global, no lógica de aplicación.
- El resto del estilo se expresa mediante clases utility en JSX.

#### `vite.config.js`

- Registra plugins React y Tailwind.
- En desarrollo redirige `/api` a `http://localhost:8080`; así el frontend usa URLs relativas y Vite actúa como reverse proxy de desarrollo.
- Configura Vitest con `jsdom`, setup compartido y limpieza de mocks.
- Es configuración de tooling e integración, no runtime de producción por sí misma.

#### `eslint.config.js`

- Configura reglas JavaScript, reglas de hooks y React Refresh.
- Los errores del linter ayudan a respetar reglas de hooks y detectar variables sin uso.
- Es calidad estática/tooling.

#### `package.json` y `package-lock.json`

- `package.json` declara scripts y dependencias directas.
- `"type": "module"` hace que `.js` use módulos ES (`import`/`export`). Cada archivo declara explícitamente sus dependencias, comparable de forma general a imports de Java, aunque no hay clases/packages Java.
- El lock fija el grafo exacto de paquetes.

#### Tests

- `src/test/setup.js`: agrega matchers DOM y limpia el DOM después de cada test.
- `apiClient.test.js`: verifica cookies/CSRF/JSON, refresh único concurrente, retry CSRF único y exclusión de login.
- `authService.test.js`: verifica adaptación del usuario y ausencia de almacenamiento web de tokens.
- `ProtectedRoute.test.jsx`: verifica sus tres estados.
- `AppLayout.test.jsx`: verifica que un logout fallido no borra visualmente la sesión.

Los tests son soporte de calidad y documentación ejecutable; no forman parte de las capas runtime.

`public/vite.svg` y `src/assets/react.svg` son recursos del template. El primero se usa como favicon; el segundo no tiene importaciones actuales.

## 4. Tipos de unidades del código

| Tipo | Ejemplos reales | Cómo reconocerlo |
| --- | --- | --- |
| Orquestadoras | `apiRequest`, `restoreSession`, handlers de submit, `AppRouter` | coordinan varios pasos/dependencias y condiciones |
| Auxiliares | `readCookie`, `parseResponse`, `prepareBody`, `normalizeUser`, `fieldError` | hacen una transformación o consulta acotada |
| Componentes | `AuthCard`, `LoginPage`, `ProtectedRoute` | funciones con nombre en mayúscula que retornan JSX |
| Custom hook | `useAuth` | función `use...` que llama a hooks React |
| Services | funciones exportadas de `authService` | representan operaciones remotas del feature |
| Infraestructura | `apiRequest`, `ApiClientError`, eventos | ocultan protocolo, navegador y detalles transversales |
| Configuración | `ENDPOINTS`, Vite, ESLint, package | valores y reglas que conectan/compilan el sistema |

Una función puede ser componente y orquestadora a la vez. “Componente” describe su papel en React; “orquestadora” describe cómo coordina trabajo.

## 5. Patrones y principios realmente presentes

### 5.1 Service Layer / fachada de autenticación

- **Problema resuelto**: las páginas no deberían repetir URLs, métodos y forma de los DTO.
- **Archivos**: `authService.js`, consumidores en `AuthProvider` y pages.
- **Información**: credenciales/email/token bajan al service; usuario normalizado o resultado/error vuelve al consumidor.
- **Sin abstracción**: cada página llamaría `apiRequest` con rutas y contratos concretos, aumentando duplicación y acoplamiento HTTP.
- **Caso concreto**: `LoginPage → AuthProvider.login → authService.login → apiRequest`.

Puede considerarse una **fachada pequeña** porque presenta operaciones significativas (`login`, `logout`) sobre la infraestructura. No es un Facade complejo ni un service Spring equivalente: no concentra el negocio de autenticación.

### 5.2 API Client centralizado como adaptación de infraestructura

- **Problema resuelto**: `fetch` por sí solo no agrega cookies/CSRF, no falla automáticamente por 4xx/5xx ni conoce refresh.
- **Archivos**: `apiClient.js`, `ApiClientError.js`, `endpoints.js` y `sessionEvents.js`.
- **Información**: recibe endpoint/opciones; prepara request; recibe `Response`; retorna datos o error normalizado.
- **Sin abstracción**: todos los services repetirían `credentials`, headers, parsing y retries, con riesgo de diferencias de seguridad.
- **Caso concreto**: `requestPasswordReset` entrega un objeto; `prepareBody` lo serializa y CSRF se agrega automáticamente por ser POST.

Es razonable verlo como **Adapter** en sentido arquitectónico: adapta la API de bajo nivel `fetch` al contrato esperado por la aplicación. No implementa una interfaz formal como sería común en Java.

### 5.3 Provider / Context

- **Problema resuelto**: routing, layout y páginas necesitan el mismo estado de sesión sin pasar props por cada nivel.
- **Archivos**: `AuthContext.js`, `AuthProvider.jsx`, `useAuth.js`, `main.jsx` y consumidores.
- **Información**: el Provider publica `user`, `status`, `isAuthenticated`, `login`, `logout`; consumidores leen el valor.
- **Sin abstracción**: habría prop drilling o múltiples estados de sesión inconsistentes.
- **Caso concreto**: login cambia el Provider; `PublicOnlyRoute` observa `isAuthenticated`; `ProfilePage` obtiene el mismo usuario.

### 5.4 Custom Hook

- **Problema resuelto**: acceso repetido y potencialmente inseguro a Context.
- **Archivos**: `useAuth.js`, `AuthContext.js` y consumidores.
- **Información**: `useContext` obtiene el valor más cercano; el hook lo valida y retorna.
- **Sin abstracción**: cada componente repetiría dos líneas y el control del Provider.
- **Caso concreto**: `ProtectedRoute` obtiene `status/isAuthenticated` sin importar directamente `AuthContext`.

### 5.5 Observer / Pub-Sub

- **Problema resuelto**: infraestructura HTTP debe anunciar que el refresh es irrecuperable sin importar React ni mutar su estado directamente.
- **Archivos**: `sessionEvents.js`, publicador en `apiClient.js`, suscriptor en `AuthProvider.jsx`.
- **Información**: refresh rechazado → evento sin payload → callbacks → estado anónimo.
- **Sin abstracción**: `apiClient` tendría que depender de React/AuthContext, o cada petición manejar manualmente la expiración.
- **Caso concreto**: `/me` da 401, refresh falla, `publishSessionExpired`, Provider limpia sesión.

### 5.6 Composition

- **Problema resuelto**: reutilizar estructura sin herencia.
- **Archivos**: `main.jsx`, `AppRouter`, `AppLayout`, `AuthCard`, `FormField`.
- **Información**: padres aportan children/props y componentes hijos renderizan partes variables.
- **Sin abstracción**: se duplicaría el layout de auth y el campo accesible.
- **Caso concreto**: las tres páginas colocan formularios distintos dentro del mismo `AuthCard`.

React favorece composición sobre herencia. La analogía con Java no es extender una clase base, sino ensamblar objetos/componentes pequeños.

### 5.7 Single Responsibility, con límites pragmáticos

Hay separación clara entre rutas, estado de auth, operaciones remotas, HTTP y UI reutilizable. No es SRP absoluto: las páginas manejan UI, estado y coordinación, algo normal en una aplicación de este tamaño. `apiClient` concentra varias tareas, pero todas pertenecen a una única preocupación amplia: el protocolo HTTP seguro del frontend.

### 5.8 Dependency Inversion: solo parcialmente

No existe inversión de dependencias formal: `authService` importa directamente la implementación `apiRequest`, y `AuthProvider` importa `authService`. No hay interfaces ni inyección runtime. Los tests sustituyen módulos con `vi.mock`, lo cual da aislamiento, pero no convierte el diseño en Dependency Inversion completo.

En este tamaño, la dependencia directa mediante módulos es una decisión válida. Afirmar que existe Clean Architecture o ports-and-adapters completa sería forzar el análisis.

## 6. Flujo real hacia Spring Boot

La cadena propuesta “Componente → Hook → Service → ApiClient → Backend” existe solo para algunas acciones:

```text
LoginPage (componente)
  → useAuth (hook de acceso)
  → AuthProvider.login (estado/orquestación)
  → authService.login (service)
  → apiRequest (API client)
  → Spring Boot
```

Pero no es una regla universal:

```text
ForgotPasswordPage
  → authService.requestPasswordReset
  → apiRequest
  → Spring Boot
```

```text
ResetPasswordPage
  → authService.validateResetToken/resetPassword
  → apiRequest
  → Spring Boot
```

```text
AuthProvider al montar
  ├→ ensureCsrfToken → Spring Boot
  └→ authService.getCurrentUser → apiRequest → Spring Boot
```

Por lo tanto, `useAuth` no es una capa obligatoria entre UI y services. Es el acceso al estado global de sesión. Las operaciones que afectan ese estado pasan por el Provider; las operaciones públicas independientes se llaman desde sus páginas.

En desarrollo, `apiRequest('/api/...')` llega primero al servidor Vite, cuyo proxy reenvía `/api` a Spring en `localhost:8080`. En producción, el repositorio frontend no muestra aquí la infraestructura de publicación; las URLs relativas presuponen que `/api` será accesible en el mismo origen lógico o mediante un proxy equivalente.

## 7. Autenticación y seguridad, extremo a extremo

### 7.1 Distribución de responsabilidades

| Frontend | Navegador | Backend Spring |
| --- | --- | --- |
| pide CSRF, copia su valor al header, incluye credenciales, coordina refresh/retry y estado visual | almacena cookies, aplica Path/HttpOnly/Secure/SameSite y las adjunta según reglas | valida credenciales, genera/valida JWT, crea/rota/revoca sesiones, configura cookies, valida CSRF y autoriza endpoints |

El frontend nunca debe ser la autoridad de seguridad. Ocultar una ruta protege la experiencia, no los datos.

### 7.2 Cookies reales

Spring define:

- `ACCESS_TOKEN`: cookie `HttpOnly`, path `/`, contiene el JWT de acceso.
- `REFRESH_TOKEN`: cookie `HttpOnly`, path `/api/auth`, contiene el token de refresh asociado a una sesión persistida y rotada.
- `XSRF-TOKEN`: cookie no HttpOnly, path `/`, para que JavaScript pueda leerla y reflejarla en `X-XSRF-TOKEN`.

Las cookies de autenticación usan `SameSite=Lax`; `Secure` depende de configuración. `HttpOnly` significa que `document.cookie` no puede leer ACCESS/REFRESH, mitigando robo directo por JavaScript inyectado. No impide que el navegador las envíe.

El frontend no escribe ni almacena ACCESS/REFRESH en localStorage. Solo el backend los establece mediante `Set-Cookie` y el navegador los administra.

### 7.3 Inicio/restauración de la aplicación

1. `AuthProvider` comienza con `status = loading`.
2. Los guards muestran `LoadingScreen`, evitando decidir demasiado pronto.
3. `ensureCsrfToken()` lee `XSRF-TOKEN`; si falta llama `GET /api/auth/csrf`.
4. Spring fuerza la creación del token y responde 204 con cookie CSRF.
5. `getCurrentUser()` llama `GET /api/auth/me`.
6. El navegador adjunta `ACCESS_TOKEN` si existe.
7. Spring valida JWT y responde usuario; el Provider marca `authenticated`.
8. Si no se puede restaurar, el Provider marca `anonymous`.

### 7.4 Login

1. `LoginPage.handleSubmit` entrega `{email, password}` a `useAuth().login`.
2. `AuthProvider.login` llama `authService.login`.
3. El service llama POST `/api/auth/login`, con `retryAuth: false`.
4. Como POST es mutante, `apiClient` asegura CSRF y envía `X-XSRF-TOKEN`.
5. `credentials: include` permite recibir/enviar cookies.
6. Spring valida CSRF y credenciales, crea una sesión, genera access/refresh y responde ambos como cookies HttpOnly; el JSON solo incluye el usuario.
7. `authService` normaliza el usuario.
8. `AuthProvider` actualiza estado; `LoginPage` navega al destino.

### 7.5 Uso del ACCESS_TOKEN

El frontend no lo lee ni crea un header `Authorization`. En cada request, el navegador lo manda como cookie. `JwtRequestFilter` de Spring lee `ACCESS_TOKEN`, valida el JWT y coloca la autenticación en `SecurityContextHolder`.

Si expiró, Spring responde `401` con código `AUTH_TOKEN_EXPIRED`; si es inválido, `401 AUTH_TOKEN_INVALID`. El cliente actual decide refresh por status 401, no diferencia ambos códigos.

### 7.6 Refresh automático y concurrencia

Para un endpoint protegido que responde 401:

1. `apiRequest` verifica `retryAuth`, `_authRetried` y `shouldAttemptRefresh`.
2. `refreshSession` llama POST `/api/auth/refresh` con `retryAuth: false`.
3. Ese POST también lleva CSRF y la cookie `REFRESH_TOKEN` (su path permite `/api/auth/refresh`).
4. Spring valida el refresh, rota la sesión/token y devuelve nuevas cookies ACCESS/REFRESH.
5. `apiRequest` repite el request original con `_authRetried: true`.

Si varias peticiones fallan juntas, `refreshRequest` contiene una Promise compartida. Solo la primera crea el refresh; las demás esperan el mismo resultado. Esto es crucial porque el backend rota el refresh token: varios refresh paralelos podrían competir y hacer que uno use un token ya invalidado.

Si el refresh falla, se publica expiración de sesión y se relanza el error. No se intenta refresh para CSRF/login/refresh/logout/forgot/reset, evitando bucles y comportamientos conceptualmente incorrectos.

### 7.7 CSRF y 403

Como la autenticación viaja automáticamente en cookies, un sitio malicioso podría intentar provocar requests desde otro origen. El token CSRF exige un dato que dicho sitio no puede leer normalmente.

Para POST/PUT/PATCH/DELETE:

1. frontend lee `XSRF-TOKEN`;
2. lo copia a `X-XSRF-TOKEN`;
3. Spring compara cookie/header mediante `CookieCsrfTokenRepository`;
4. si falta o no coincide, responde `403 CSRF_TOKEN_INVALID`.

Ante ese error exacto, `apiClient` borra su cookie CSRF, solicita otra y reintenta una sola vez. `_csrfRetried` evita un loop. Otros 403, como `ACCESS_DENIED`, no se reintentan: llegan a la UI como `ApiClientError`.

Un 401 significa “no autenticado/token no válido” en este contrato; un 403 significa “autenticado sin permiso” o CSRF inválido. El código distingue el caso CSRF por `error.code`.

### 7.8 Logout

1. `AppLayout.handleLogout` llama al `logout` del Provider.
2. `authService.logout` hace POST con `retryAuth: false` y CSRF.
3. Spring intenta revocar la sesión asociada al refresh y responde cookies ACCESS/REFRESH/XSRF expiradas.
4. Solo si la llamada se resuelve, el Provider borra usuario y marca `anonymous`.
5. Si falla, `AppLayout` muestra error y conserva el estado actual; esta conducta está testeada.

### 7.9 Expiración de sesión y eventos

```text
Request protegido → 401 → refresh
                           ├─ éxito → retry original
                           └─ fallo → publishSessionExpired()
                                      ↓
                              listener de AuthProvider
                                      ↓
                             user=null, anonymous
                                      ↓
                       ProtectedRoute redirige a /login
```

El evento no transporta el error. Su finalidad es sincronizar estado, mientras la Promise conserva y propaga el error a quien inició la operación.

### 7.10 `ApiClientError`

Spring devuelve errores estructurados con `message`, `code`, `fieldErrors` y `requestId`. El cliente los adapta:

- `RequestError` muestra `message` y `requestId`.
- `fieldError` busca validaciones por campo.
- lógica de infraestructura inspecciona `code`, por ejemplo `CSRF_TOKEN_INVALID`.
- `status` permite distinguir HTTP; status 0 representa falta de respuesta de red.

Esta clase no “maneja” el error por sí misma: solo le da forma uniforme. La decisión sigue perteneciendo a `apiClient`, Provider o página según el nivel.

## 8. Construcciones JavaScript observadas en contexto

| Construcción | Ejemplo | Significado aquí |
| --- | --- | --- |
| Módulos | `import` / `export` | dependencias explícitas entre archivos; el módulo conserva estado compartido |
| Destructuring | `const { user, logout } = useAuth()` | extrae propiedades por nombre |
| Spread | `{ ...form, email: value }` | crea una copia con una propiedad reemplazada |
| Rest | `{ label, ...inputProps }` | agrupa props no extraídas |
| Optional chaining | `user?.email` | evita error si un eslabón es null/undefined |
| Callback | `onClick={handleLogout}` | entrega una función para ejecutar ante un evento |
| Promise | retorno de `fetch`/función `async` | resultado futuro exitoso o rechazado |
| async/await | `await authService.login(...)` | espera lógicamente una Promise sin bloquear el navegador |
| Closure | cleanup con `active`; unsubscribe | una función conserva variables del ámbito donde nació |
| Set | mutating methods/listeners | colección sin duplicados con `has/add/delete` |
| Eventos | `publishSessionExpired` | comunicación desacoplada mediante callbacks |

No hay `Map` en el código actual. Tampoco hay clases de componentes; los componentes son funciones.

## 9. Evaluación arquitectónica proporcionada al tamaño actual

### Importante

- **La restauración convierte cualquier error en estado anónimo sin conservar causa visible**. En `AuthProvider.restoreSession`, un fallo de red, un error del endpoint CSRF y una ausencia real de sesión terminan igual. Esto puede hacer que una caída del backend parezca un logout y lleve al login. Es una característica real con impacto de UX/diagnóstico; no compromete por sí sola la seguridad.
- **Los guards son solo seguridad de interfaz**. Sería un problema grave si se confiaran como autorización. En este repositorio Spring sí protege `.anyRequest().authenticated()` y roles administrativos, por lo que la división actual es correcta; se señala para evitar interpretar `ProtectedRoute` como equivalente a Spring Security.

### Mejora futura

- **Cobertura limitada de flujos UI**. Hay tests sólidos focalizados, pero no se observan pruebas de `PublicOnlyRoute`, páginas completas, restauración del Provider ni publicación/desuscripción del evento. Es razonable ampliarlas si crece el proyecto.
- **Contrato de usuario tolera dos nombres (`role`/`rol`)**. `normalizeUser` es una adaptación útil, pero evidencia convivencia de contratos. Mientras exista compatibilidad requerida es válido; a futuro podría consolidarse el contrato entre frontend/backend.
- **El estado de autenticación usa strings libres**. Para este tamaño es claro, pero JavaScript no impide un typo como sí lo haría un enum Java/TypeScript. No justifica por sí solo introducir una librería de estado.
- **`sessionEvents` no aísla excepciones de listeners**. Hoy existe un solo listener controlado; si hubiera varios y uno lanzara, podría interrumpir el recorrido. Es una consideración futura, no un defecto actual relevante.
- **La validación asíncrona de reset ignora respuestas tardías pero no cancela fetch**. El guard `active` evita efectos incorrectos; un `AbortController` sería una optimización futura si esta interacción se vuelve significativa.

### Decisiones perfectamente válidas

- Context + estado local, sin Redux/Zustand, es proporcional al tamaño y al único estado global existente.
- Llamar services directamente desde páginas públicas es correcto: no necesitan modificar sesión global.
- Centralizar seguridad HTTP en `apiClient` reduce duplicación.
- Guardar tokens solo en cookies HttpOnly y no en almacenamiento web es coherente con el diseño de seguridad del backend.
- Compartir una sola Promise de refresh/CSRF es una solución pequeña y eficaz a concurrencia.
- Mantener `App.jsx` mínimo y rutas en `app/AppRouter.jsx` es claro.
- Colocar `fieldErrors.js` junto a componentes es razonable porque hoy solo sirve a presentación de formularios auth.
- No usar interfaces/DI como en Spring evita ceremonia innecesaria en una aplicación de este tamaño.
- Tailwind directamente en componentes es una decisión estilística consistente, aunque produzca JSX largo.

## 10. Mapa general basado en la estructura real

```text
FRONTEND: Frontend/react-starter
│
├── Bootstrap y configuración
│   ├── index.html
│   ├── src/main.jsx
│   ├── src/App.jsx
│   ├── vite.config.js
│   ├── eslint.config.js
│   └── src/index.css
│
├── Routing y shell (`src/app`)
│   ├── AppRouter
│   ├── ProtectedRoute / PublicOnlyRoute
│   ├── LoadingScreen
│   ├── AppLayout
│   └── ProfilePage
│
├── UI de autenticación (`src/auth`)
│   ├── pages: Login / ForgotPassword / ResetPassword
│   └── components: AuthCard / FormField / RequestError / fieldError
│
├── Estado de sesión (`src/auth/context`)
│   ├── AuthContext
│   ├── AuthProvider
│   └── useAuth
│
├── Casos de uso remotos (`src/auth/services`)
│   └── authService
│
├── Comunicación HTTP y seguridad cliente (`src/services`)
│   ├── apiClient
│   ├── ApiClientError
│   ├── endpoints
│   └── sessionEvents
│
├── Navegador
│   ├── fetch
│   ├── cookies HttpOnly ACCESS/REFRESH (opacas para JS)
│   └── cookie legible XSRF-TOKEN
│
└── BACKEND SPRING BOOT
    ├── AuthController
    ├── Spring Security + CSRF
    ├── JwtRequestFilter
    ├── AuthCookieService
    └── servicios de autenticación/sesión/password reset
```

## 11. Mapa de dependencias entre archivos principales

La flecha `A → B` significa “A importa/usa B”. El backend no es una importación JavaScript; se muestra como destino HTTP.

```text
index.html
└→ main.jsx
   ├→ index.css
   ├→ BrowserRouter (librería)
   ├→ AuthProvider.jsx
   │  ├→ AuthContext.js
   │  ├→ authService.js
   │  │  ├→ endpoints.js
   │  │  └→ apiClient.js
   │  │     ├→ endpoints.js
   │  │     ├→ ApiClientError.js
   │  │     ├→ sessionEvents.js (publica)
   │  │     └→ Backend Spring (fetch)
   │  ├→ apiClient.js (ensureCsrfToken directo)
   │  └→ sessionEvents.js (suscribe)
   └→ App.jsx
      └→ AppRouter.jsx
         ├→ PublicOnlyRoute.jsx
         │  ├→ useAuth.js → AuthContext.js
         │  └→ LoadingScreen.jsx
         ├→ ProtectedRoute.jsx
         │  ├→ useAuth.js → AuthContext.js
         │  └→ LoadingScreen.jsx
         ├→ AppLayout.jsx
         │  ├→ useAuth.js → AuthContext.js
         │  └→ RequestError.jsx
         ├→ ProfilePage.jsx
         │  └→ useAuth.js → AuthContext.js
         ├→ LoginPage.jsx
         │  ├→ useAuth.js → AuthContext.js
         │  ├→ AuthCard.jsx
         │  ├→ FormField.jsx
         │  ├→ RequestError.jsx
         │  └→ fieldErrors.js
         ├→ ForgotPasswordPage.jsx
         │  ├→ authService.js
         │  ├→ AuthCard.jsx / FormField.jsx / RequestError.jsx
         │  └→ fieldErrors.js
         └→ ResetPasswordPage.jsx
            ├→ authService.js
            ├→ AuthCard.jsx / FormField.jsx / RequestError.jsx
            └→ fieldErrors.js
```

## 12. Lectura final desde una perspectiva Java/Spring

La separación más importante no es “controller/service/repository” porque el frontend no persiste ni decide la seguridad. La separación real es:

1. **React renderiza y conserva estado de interfaz**.
2. **Context distribuye la sesión en memoria**.
3. **`authService` expresa operaciones del backend en lenguaje del feature**.
4. **`apiClient` implementa las preocupaciones técnicas comunes**.
5. **El navegador custodia y envía cookies**.
6. **Spring es la autoridad de autenticación, autorización, tokens, sesiones y CSRF**.

El código evita dos extremos: no mezcla `fetch` crudo en cada componente, pero tampoco introduce una arquitectura ceremonial para una aplicación pequeña. La pieza más sofisticada es `apiClient`, porque actúa como frontera técnica y coordina dos reintentos seguros —CSRF y refresh— con control de concurrencia. La pieza central para React es `AuthProvider`, que traduce el resultado de esa infraestructura a tres estados simples que routing y UI pueden consumir.
