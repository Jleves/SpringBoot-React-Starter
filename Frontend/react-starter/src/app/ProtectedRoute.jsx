import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/context/useAuth.js'
import { LoadingScreen } from './LoadingScreen.jsx'

export function ProtectedRoute() {
  const { status, isAuthenticated } = useAuth()
  const location = useLocation()

  if (status === 'loading') return <LoadingScreen />
  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />
  }
  return <Outlet />
}
