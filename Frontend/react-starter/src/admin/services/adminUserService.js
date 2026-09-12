import { apiRequest } from '../../services/apiClient.js'
import { ENDPOINTS } from '../../services/endpoints.js'

export function createUser(form) {
  return apiRequest(ENDPOINTS.ADMIN.USERS, { method: 'POST', body: form })
}
