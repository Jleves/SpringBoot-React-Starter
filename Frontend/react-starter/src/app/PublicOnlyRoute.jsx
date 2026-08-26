import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/context/useAuth.js'
import { LoadingScreen } from './LoadingScreen.jsx'

export function PublicOnlyRoute() {
  const { status, isAuthenticated } = useAuth()
  if (status === 'loading') return <LoadingScreen />
  return isAuthenticated ? <Navigate to="/app/profile" replace /> : <Outlet />
}
