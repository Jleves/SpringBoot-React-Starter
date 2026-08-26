import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { AuthContext } from '../auth/context/AuthContext.js'
import { AppLayout } from './AppLayout.jsx'

describe('AppLayout', () => {
  it('conserva la sesión visible cuando falla el logout', async () => {
    const user = userEvent.setup()
    const logout = vi.fn().mockRejectedValue(Object.assign(new Error('No se pudo cerrar la sesión.'), {
      requestId: 'req-logout',
    }))

    render(
      <AuthContext.Provider value={{ user: { email: 'user@example.com' }, logout }}>
        <MemoryRouter initialEntries={['/app/profile']}>
          <Routes>
            <Route path="/app" element={<AppLayout />}>
              <Route path="profile" element={<p>Contenido privado</p>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    )

    await user.click(screen.getByRole('button', { name: 'Cerrar sesión' }))

    expect(logout).toHaveBeenCalledOnce()
    expect(screen.getByText('user@example.com')).toBeInTheDocument()
    expect(screen.getByText('Contenido privado')).toBeInTheDocument()
    expect(screen.getByText('No se pudo cerrar la sesión.')).toBeInTheDocument()
    expect(screen.getByText('Referencia: req-logout')).toBeInTheDocument()
  })
})
