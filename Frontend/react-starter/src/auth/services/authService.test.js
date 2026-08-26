import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '../../services/apiClient.js'
import { login } from './authService.js'

vi.mock('../../services/apiClient.js', () => ({ apiRequest: vi.fn() }))

describe('authService', () => {
  beforeEach(() => localStorage.clear())

  it('normaliza el usuario sin guardar credenciales en almacenamiento web', async () => {
    apiRequest.mockResolvedValue({
      user: { id: 7, username: 'user@example.com', email: 'user@example.com', rol: 'USER' },
    })

    const user = await login({ email: 'user@example.com', password: 'secret123' })

    expect(user).toEqual({ id: 7, email: 'user@example.com', role: 'USER' })
    expect(localStorage).toHaveLength(0)
    expect(sessionStorage).toHaveLength(0)
  })
})
