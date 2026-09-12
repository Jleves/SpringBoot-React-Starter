import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { __resetApiClientForTests } from '../../services/apiClient.js'
import { createUser } from './adminUserService.js'

beforeEach(() => {
  __resetApiClientForTests()
  document.cookie = 'XSRF-TOKEN=admin-csrf; Path=/'
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; Path=/; Max-Age=0'
})

it('envía el alta por el cliente HTTP con cookies y CSRF y devuelve el usuario público', async () => {
  const created = { id: 4, email: 'new@example.com', role: 'USER', enabled: true }
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(created), {
    status: 201, headers: { 'Content-Type': 'application/json' },
  }))
  vi.stubGlobal('fetch', fetchMock)
  const form = { email: created.email, password: 'initial-password', role: 'USER' }
  expect(await createUser(form)).toEqual(created)
  const [url, options] = fetchMock.mock.calls[0]
  expect(url).toBe('/api/admin/users')
  expect(options.method).toBe('POST')
  expect(options.credentials).toBe('include')
  expect(options.headers.get('X-XSRF-TOKEN')).toBe('admin-csrf')
  expect(JSON.parse(options.body)).toEqual(form)
})
