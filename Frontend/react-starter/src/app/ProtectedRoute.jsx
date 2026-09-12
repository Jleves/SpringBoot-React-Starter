import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/context/useAuth.js'
import { LoadingScreen } from './LoadingScreen.jsx'

export function ProtectedRoute({ requiredRole }) {
  const { status, isAuthenticated, user } = useAuth()
  const location = useLocation()

  if (status === 'loading') return <LoadingScreen />
  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />
  }
  if (requiredRole && user?.role !== requiredRole) return <Navigate to="/app/profile" replace />
  return <Outlet />
}
