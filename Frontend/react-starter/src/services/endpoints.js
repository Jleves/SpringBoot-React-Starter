const authBase = '/api/auth'

export const ENDPOINTS = Object.freeze({
  AUTH: Object.freeze({
    CSRF: `${authBase}/csrf`,
    LOGIN: `${authBase}/login`,
    REFRESH: `${authBase}/refresh`,
    LOGOUT: `${authBase}/logout`,
    ME: `${authBase}/me`,
    FORGOT_PASSWORD: `${authBase}/forgot-password`,
    RESET_PASSWORD: `${authBase}/reset-password`,
    VALIDATE_RESET_TOKEN: (token) =>
      `${authBase}/reset-password/validate?${new URLSearchParams({ token })}`,
  }),
})
