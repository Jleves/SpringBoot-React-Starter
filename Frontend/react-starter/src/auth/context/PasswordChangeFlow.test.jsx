import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { expect, it, vi } from 'vitest'
import { AuthProvider } from './AuthProvider.jsx'
import { AppRouter } from '../../app/AppRouter.jsx'
import * as service from '../services/authService.js'

vi.mock('../services/authService.js', () => ({ getCurrentUser: vi.fn(), changePassword: vi.fn(), login: vi.fn(), logout: vi.fn() }))
vi.mock('../../services/apiClient.js', () => ({ ensureCsrfToken: vi.fn().mockResolvedValue('csrf') }))

it('conserva la confirmación al perder el acceso a la ruta protegida tras cambiar la contraseña', async () => {
  service.getCurrentUser.mockResolvedValue({ id: 1, email: 'profile@example.com', role: 'USER' })
  service.changePassword.mockResolvedValue(null)
  render(<MemoryRouter initialEntries={['/app/profile']}><AuthProvider><AppRouter /></AuthProvider></MemoryRouter>)
  const user = userEvent.setup()
  await user.type(await screen.findByLabelText('Contraseña actual'), 'original-password')
  await user.type(screen.getByLabelText('Contraseña nueva'), 'changed-password')
  await user.type(screen.getByLabelText('Confirmar contraseña nueva'), 'changed-password')
  await user.click(screen.getByRole('button', { name: 'Actualizar contraseña' }))
  expect(await screen.findByText('Contraseña actualizada. Ingresá con tu contraseña nueva.')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Ingresar' })).toBeInTheDocument()
  expect(service.logout).not.toHaveBeenCalled()
})
