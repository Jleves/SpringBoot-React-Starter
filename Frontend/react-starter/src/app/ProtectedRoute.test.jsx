import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthContext } from '../auth/context/AuthContext.js'
import { ProtectedRoute } from './ProtectedRoute.jsx'

function renderRoute(status) {
  return render(
    <AuthContext.Provider value={{ status, isAuthenticated: status === 'authenticated' }}>
      <MemoryRouter initialEntries={['/app/profile?tab=security']}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/app/profile" element={<p>Perfil privado</p>} />
          </Route>
          <Route path="/login" element={<p>Ingreso público</p>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

describe('ProtectedRoute', () => {
  it('espera mientras se restaura la sesión', () => {
    renderRoute('loading')
    expect(screen.getByText('Restaurando tu sesión…')).toBeInTheDocument()
  })

  it('permite entrar con sesión autenticada', () => {
    renderRoute('authenticated')
    expect(screen.getByText('Perfil privado')).toBeInTheDocument()
  })

  it('redirige al login cuando no hay sesión', () => {
    renderRoute('anonymous')
    expect(screen.getByText('Ingreso público')).toBeInTheDocument()
  })
})
