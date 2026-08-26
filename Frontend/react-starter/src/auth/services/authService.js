import { apiRequest } from '../../services/apiClient.js'
import { ENDPOINTS } from '../../services/endpoints.js'

function normalizeUser(user) {
  if (!user) return null
  const normalized = { ...user, role: user.role || user.rol }
  delete normalized.rol
  delete normalized.username
  return normalized
}

export async function login(credentials) {
  const response = await apiRequest(ENDPOINTS.AUTH.LOGIN, {
    method: 'POST',
    body: credentials,
    retryAuth: false,
  })
  return normalizeUser(response?.user)
}

export async function logout() {
  return apiRequest(ENDPOINTS.AUTH.LOGOUT, {
    method: 'POST',
    retryAuth: false,
  })
}

export async function getCurrentUser() {
  const user = await apiRequest(ENDPOINTS.AUTH.ME)
  return normalizeUser(user)
}

export async function requestPasswordReset(email) {
  return apiRequest(ENDPOINTS.AUTH.FORGOT_PASSWORD, {
    method: 'POST',
    body: { email },
    retryAuth: false,
  })
}

export async function validateResetToken(token) {
  return apiRequest(ENDPOINTS.AUTH.VALIDATE_RESET_TOKEN(token), {
    retryAuth: false,
  })
}

export async function resetPassword({ token, newPassword }) {
  return apiRequest(ENDPOINTS.AUTH.RESET_PASSWORD, {
    method: 'POST',
    body: { token, newPassword },
    retryAuth: false,
  })
}
