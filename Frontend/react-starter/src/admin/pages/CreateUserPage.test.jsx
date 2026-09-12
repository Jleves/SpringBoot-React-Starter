import { act, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CreateUserPage } from './CreateUserPage.jsx'
import { createUser } from '../services/adminUserService.js'

vi.mock('../services/adminUserService.js', () => ({ createUser: vi.fn() }))

async function fillForm() {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Email'), 'New@example.com')
  await user.type(screen.getByLabelText('Contraseña inicial'), 'initial-password')
  return user
}

describe('CreateUserPage', () => {
  beforeEach(() => vi.resetAllMocks())

  it('ofrece los tres roles, crea la cuenta y limpia la contraseña', async () => {
    createUser.mockResolvedValue({ email: 'new@example.com', role: 'ADMIN' })
    render(<CreateUserPage />)
    expect(screen.getAllByRole('option').map((option) => option.value)).toEqual(['USER', 'ADMIN', 'SUPER_ADMIN'])
    const user = await fillForm()
    await user.selectOptions(screen.getByLabelText('Rol'), 'ADMIN')
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))
    expect(createUser).toHaveBeenCalledWith({ email: 'new@example.com', password: 'initial-password', role: 'ADMIN' })
    expect(await screen.findByRole('status')).toHaveTextContent('new@example.com')
    expect(screen.getByLabelText('Contraseña inicial')).toHaveValue('')
  })

  it('muestra los errores de campo devueltos por el backend', async () => {
    createUser.mockRejectedValue(Object.assign(new Error('Datos inválidos'), {
      status: 400, fieldErrors: [{ field: 'password', message: 'Contraseña inválida' }],
    }))
    render(<CreateUserPage />)
    const user = await fillForm()
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))
    expect(await screen.findByText('Contraseña inválida')).toBeInTheDocument()
    expect(screen.getByLabelText(/^Contraseña inicial/)).toHaveAttribute('aria-invalid', 'true')
  })

  it('evita solicitudes simultáneas', async () => {
    let resolve
    createUser.mockReturnValue(new Promise((done) => { resolve = done }))
    render(<CreateUserPage />)
    const user = await fillForm()
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))
    expect(screen.getByRole('button', { name: 'Creando…' })).toBeDisabled()
    fireEvent.submit(screen.getByLabelText('Email').closest('form'))
    expect(createUser).toHaveBeenCalledOnce()
    await act(async () => resolve({ email: 'new@example.com', role: 'USER' }))
  })

  it.each([
    [409, 'Conflicto', 'Ya existe una cuenta con ese email.'],
    [400, 'Datos inválidos', 'Datos inválidos'],
    [403, 'No tenés permisos', 'No tenés permisos'],
    [401, 'La sesión venció', 'La sesión venció'],
    [0, 'No se pudo conectar con el servidor.', 'No se pudo conectar con el servidor.'],
  ])('muestra el error %s sin confirmar un alta', async (status, message, expected) => {
    createUser.mockRejectedValue(Object.assign(new Error(message), { status }))
    render(<CreateUserPage />)
    const user = await fillForm()
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(expected)
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Crear cuenta' })).toBeEnabled()
  })
})
