import { ApiClientError } from './ApiClientError.js'
import { ENDPOINTS } from './endpoints.js'
import { publishSessionExpired } from './sessionEvents.js'

const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])
const NO_REFRESH_ENDPOINTS = [
  ENDPOINTS.AUTH.CSRF,
  ENDPOINTS.AUTH.LOGIN,
  ENDPOINTS.AUTH.REFRESH,
  ENDPOINTS.AUTH.LOGOUT,
  ENDPOINTS.AUTH.FORGOT_PASSWORD,
  ENDPOINTS.AUTH.RESET_PASSWORD,
]

let csrfRequest = null
let refreshRequest = null

function readCookie(name) {
  const prefix = `${encodeURIComponent(name)}=`
  const cookie = document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(prefix))

  if (!cookie) return null

  try {
    return decodeURIComponent(cookie.slice(prefix.length))
  } catch {
    return cookie.slice(prefix.length)
  }
}

async function parseResponse(response, responseType) {
  if (response.status === 204) return null

  if (responseType === 'blob') return response.blob()
  if (responseType === 'text') return response.text()

  const contentType = response.headers.get('content-type') || ''
  if (responseType === 'json' || contentType.includes('application/json')) {
    try {
      return await response.json()
    } catch {
      return null
    }
  }

  try {
    return await response.text()
  } catch {
    return null
  }
}

export async function ensureCsrfToken({ force = false } = {}) {
  const currentToken = readCookie('XSRF-TOKEN')
  if (currentToken && !force) return currentToken
  if (csrfRequest) return csrfRequest

  if (force) {
    document.cookie = 'XSRF-TOKEN=; Path=/; Max-Age=0; SameSite=Lax'
  }

  csrfRequest = fetch(ENDPOINTS.AUTH.CSRF, { credentials: 'include' })
    .then(async (response) => {
      if (!response.ok) {
        const data = await parseResponse(response, 'auto')
        throw new ApiClientError({ status: response.status, data })
      }
      return readCookie('XSRF-TOKEN')
    })
    .finally(() => {
      csrfRequest = null
    })

  return csrfRequest
}

function shouldAttemptRefresh(endpoint) {
  return !NO_REFRESH_ENDPOINTS.some(
    (excluded) => endpoint === excluded || endpoint.startsWith(`${excluded}?`) || endpoint.startsWith(`${excluded}/`),
  )
}

async function refreshSession() {
  if (!refreshRequest) {
    refreshRequest = apiRequest(ENDPOINTS.AUTH.REFRESH, {
      method: 'POST',
      retryAuth: false,
    })
      .catch((error) => {
        publishSessionExpired()
        throw error
      })
      .finally(() => {
        refreshRequest = null
      })
  }

  return refreshRequest
}

function prepareBody(body, headers) {
  if (body == null || body instanceof FormData || typeof body === 'string') return body
  if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  return JSON.stringify(body)
}

export async function apiRequest(endpoint, options = {}) {
  const {
    method = 'GET',
    headers: customHeaders,
    body,
    responseType = 'auto',
    retryAuth = true,
    signal,
    _csrfRetried = false,
    _authRetried = false,
  } = options
  const normalizedMethod = method.toUpperCase()
  const headers = new Headers(customHeaders)

  if (MUTATING_METHODS.has(normalizedMethod)) {
    const csrfToken = await ensureCsrfToken()
    if (csrfToken) headers.set('X-XSRF-TOKEN', csrfToken)
  }

  let response
  try {
    response = await fetch(endpoint, {
      method: normalizedMethod,
      credentials: 'include',
      headers,
      body: prepareBody(body, headers),
      signal,
    })
  } catch (cause) {
    if (cause?.name === 'AbortError') throw cause
    throw new ApiClientError({
      fallbackMessage: 'No se pudo conectar con el servidor.',
      data: { message: 'No se pudo conectar con el servidor.' },
    })
  }

  const data = await parseResponse(response, responseType)

  if (response.ok) return data

  const error = new ApiClientError({ status: response.status, data })

  if (response.status === 403 && error.code === 'CSRF_TOKEN_INVALID' && !_csrfRetried) {
    await ensureCsrfToken({ force: true })
    return apiRequest(endpoint, { ...options, _csrfRetried: true })
  }

  if (
    response.status === 401 &&
    retryAuth &&
    !_authRetried &&
    shouldAttemptRefresh(endpoint)
  ) {
    await refreshSession()
    return apiRequest(endpoint, { ...options, _authRetried: true })
  }

  throw error
}

export function __resetApiClientForTests() {
  csrfRequest = null
  refreshRequest = null
}
