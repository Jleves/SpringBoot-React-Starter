import { useCallback, useEffect, useMemo, useState } from 'react'
import { ensureCsrfToken } from '../../services/apiClient.js'
import { subscribeToSessionExpired } from '../../services/sessionEvents.js'
import * as authService from '../services/authService.js'
import { AuthContext } from './AuthContext.js'

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [status, setStatus] = useState('loading')

  useEffect(() => subscribeToSessionExpired(() => {
    setUser(null)
    setStatus('anonymous')
  }), [])

  useEffect(() => {
    let active = true

    async function restoreSession() {
      try {
        await ensureCsrfToken()
        const currentUser = await authService.getCurrentUser()
        if (active) {
          setUser(currentUser)
          setStatus('authenticated')
        }
      } catch {
        if (active) {
          setUser(null)
          setStatus('anonymous')
        }
      }
    }

    restoreSession()
    return () => {
      active = false
    }
  }, [])

  const login = useCallback(async (credentials) => {
    const authenticatedUser = await authService.login(credentials)
    setUser(authenticatedUser)
    setStatus('authenticated')
    return authenticatedUser
  }, [])

  const logout = useCallback(async () => {
    await authService.logout()
    setUser(null)
    setStatus('anonymous')
  }, [])

  const value = useMemo(() => ({
    user,
    status,
    isAuthenticated: status === 'authenticated',
    login,
    logout,
  }), [user, status, login, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
