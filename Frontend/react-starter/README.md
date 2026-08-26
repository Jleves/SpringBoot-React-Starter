# React Starter

Shell de autenticación construido con React, Vite y Tailwind CSS para el backend Spring Starter.

La sesión utiliza cookies `HttpOnly`; el frontend no almacena tokens. El cliente HTTP incluye cookies,
envía `XSRF-TOKEN` como `X-XSRF-TOKEN` en mutaciones y coordina un único refresh cuando varias
peticiones reciben `401` al mismo tiempo.

En desarrollo, Vite publica `/api` mediante proxy hacia `http://localhost:8080`.

Tailwind se integra mediante `@tailwindcss/vite`; los componentes usan utilidades directamente y
`src/index.css` conserva únicamente el import de Tailwind y los estilos globales del documento.

## Comandos

```bash
npm install
npm run dev
npm run lint
npm test
npm run build
```

Requiere Node.js 22 y npm 10.
