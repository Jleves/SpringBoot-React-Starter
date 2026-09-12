import { Navigate, Route, Routes } from 'react-router-dom'
import { CreateUserPage } from '../admin/pages/CreateUserPage.jsx'
import { ForgotPasswordPage } from '../auth/pages/ForgotPasswordPage.jsx'
import { LoginPage } from '../auth/pages/LoginPage.jsx'
import { ResetPasswordPage } from '../auth/pages/ResetPasswordPage.jsx'
import { AppLayout } from './AppLayout.jsx'
import { ProfilePage } from './ProfilePage.jsx'
import { ProtectedRoute } from './ProtectedRoute.jsx'
import { PublicOnlyRoute } from './PublicOnlyRoute.jsx'

export function AppRouter() {
  return (
    <Routes>
      <Route element={<PublicOnlyRoute />}>
        <Route path="/login" element={<LoginPage />} />
      </Route>
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />

      <Route element={<ProtectedRoute />}>
        <Route path="/app" element={<AppLayout />}>
          <Route index element={<Navigate to="profile" replace />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route element={<ProtectedRoute requiredRole="SUPER_ADMIN" />}>
            <Route path="admin/users/new" element={<CreateUserPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="/" element={<Navigate to="/app/profile" replace />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
