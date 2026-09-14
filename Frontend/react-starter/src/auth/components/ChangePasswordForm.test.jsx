import { act, fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { AuthContext } from '../context/AuthContext.js'
import { ChangePasswordForm } from './ChangePasswordForm.jsx'

function setup(changePassword = vi.fn().mockResolvedValue(null)) {
  render(<AuthContext.Provider value={{ changePassword }}><MemoryRouter><Routes>
    <Route path="/" element={<ChangePasswordForm />} />
    <Route path="/login" element={<p>Ingresar nuevamente</p>} />
  </Routes></MemoryRouter></AuthContext.Provider>)
  return changePassword
}
async function fill(confirmation = 'changed-password') {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Contraseña actual'), 'original-password')
  await user.type(screen.getByLabelText('Contraseña nueva'), 'changed-password')
  await user.type(screen.getByLabelText('Confirmar contraseña nueva'), confirmation)
  return user
}

describe('Cambio de contraseña', () => {
  it('confirma la nueva contraseña antes de enviar', async () => {
    const change = setup(); const user = await fill('different-password')
    await user.click(screen.getByRole('button', { name: 'Actualizar contraseña' }))
    expect(screen.getByRole('alert')).toHaveTextContent('no coinciden')
    expect(change).not.toHaveBeenCalled()
  })
  it('regresa al login únicamente después del éxito y evita doble envío', async () => {
    let resolve
    const change = setup(vi.fn().mockReturnValue(new Promise((done) => { resolve = done })))
    const user = await fill()
    await user.click(screen.getByRole('button', { name: 'Actualizar contraseña' }))
    fireEvent.submit(screen.getByLabelText('Contraseña actual').closest('form'))
    expect(change).toHaveBeenCalledOnce()
    expect(screen.queryByText('Ingresar nuevamente')).not.toBeInTheDocument()
    await act(async () => resolve())
    expect(screen.getByText('Ingresar nuevamente')).toBeInTheDocument()
  })
  it.each([400, 401, 403, 429])('muestra errores %s sin simular éxito', async (status) => {
    setup(vi.fn().mockRejectedValue({ status, message: 'Solicitud rechazada', fieldErrors: [{ field: 'currentPassword', message: 'Revisá la contraseña actual' }] }))
    const user = await fill(); await user.click(screen.getByRole('button', { name: 'Actualizar contraseña' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Solicitud rechazada')
    expect(screen.getByText('Revisá la contraseña actual')).toBeInTheDocument()
    expect(screen.queryByText('Ingresar nuevamente')).not.toBeInTheDocument()
  })
  it('informa un resultado incierto ante fallo de red y no reenvía', async () => {
    const change = setup(vi.fn().mockRejectedValue({ status: 0 }))
    const user = await fill(); await user.click(screen.getByRole('button', { name: 'Actualizar contraseña' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos confirmar')
    expect(screen.getByLabelText('Contraseña actual')).toHaveValue('')
    expect(screen.getByRole('button', { name: 'Actualizar contraseña' })).toBeDisabled()
    expect(change).toHaveBeenCalledOnce()
  })
})
