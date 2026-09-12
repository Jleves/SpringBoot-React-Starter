import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AuthContext } from '../auth/context/AuthContext.js'
import { AppLayout } from '../app/AppLayout.jsx'
import { ProtectedRoute } from '../app/ProtectedRoute.jsx'

describe('Acceso al alta administrativa', () => {
  it.each(['SUPER_ADMIN', 'ADMIN', 'USER', null])('protege la URL y navegación para %s', (role) => {
    render(
      <AuthContext.Provider value={{ user: role ? { role } : null, status: role ? 'authenticated' : 'anonymous', isAuthenticated: Boolean(role) }}>
        <MemoryRouter initialEntries={['/app/admin/users/new']}>
          <Routes>
            <Route element={<ProtectedRoute />}>
              <Route path="/app" element={<AppLayout />}>
                <Route path="profile" element={<p>Perfil privado</p>} />
                <Route element={<ProtectedRoute requiredRole="SUPER_ADMIN" />}>
                  <Route path="admin/users/new" element={<p>Formulario de alta</p>} />
                </Route>
              </Route>
            </Route>
            <Route path="/login" element={<p>Ingresar</p>} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    )
    if (role === 'SUPER_ADMIN') {
      expect(screen.getByText('Formulario de alta')).toBeInTheDocument()
      expect(screen.getByRole('link', { name: 'Crear usuario' })).toBeInTheDocument()
    } else {
      expect(screen.queryByText('Formulario de alta')).not.toBeInTheDocument()
      expect(screen.queryByRole('link', { name: 'Crear usuario' })).not.toBeInTheDocument()
      expect(screen.getByText(role ? 'Perfil privado' : 'Ingresar')).toBeInTheDocument()
    }
  })
})
