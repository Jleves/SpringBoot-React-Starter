import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { RequestError } from '../auth/components/RequestError.jsx'
import { useAuth } from '../auth/context/useAuth.js'

export function AppLayout() {
  const { user, logout } = useAuth()
  const [error, setError] = useState(null)
  const [loggingOut, setLoggingOut] = useState(false)

  async function handleLogout() {
    setError(null)
    setLoggingOut(true)
    try {
      await logout()
    } catch (requestError) {
      setError(requestError)
    } finally {
      setLoggingOut(false)
    }
  }

  return (
    <div className="min-h-screen bg-slate-100">
      <header className="flex min-h-18 items-start justify-between gap-3 border-b border-slate-200 bg-white px-[clamp(1rem,4vw,3rem)] py-3 md:items-center">
        <NavLink className="text-lg font-extrabold tracking-tight text-slate-900 hover:no-underline" to="/app/profile">Spring Starter</NavLink>
        <div className="flex flex-col items-end gap-2 text-sm text-slate-500 md:flex-row md:items-center md:gap-4">
          <span className="hidden sm:inline">{user?.email}</span>
          <button className="cursor-pointer rounded-xl bg-emerald-50 px-4 py-3 font-bold text-emerald-800 transition hover:-translate-y-px disabled:cursor-wait disabled:opacity-60" disabled={loggingOut} onClick={handleLogout} type="button">
            {loggingOut ? 'Cerrando…' : 'Cerrar sesión'}
          </button>
        </div>
      </header>
      <div className="grid min-h-[calc(100vh-4.5rem)] md:grid-cols-[15rem_1fr]">
        <aside className="flex bg-emerald-950 px-4 py-2 md:flex-col md:py-8" aria-label="Navegación principal">
          <p className="mx-3 mb-3 hidden text-xs font-extrabold tracking-[.12em] text-emerald-200/75 uppercase md:block">Cuenta</p>
          <NavLink className={({ isActive }) => `rounded-xl px-3 py-3 font-semibold text-emerald-100 transition hover:bg-white/10 hover:no-underline ${isActive ? 'bg-white/15 text-white' : ''}`} to="/app/profile">Perfil</NavLink>
        </aside>
        <main className="p-[clamp(1.5rem,4vw,3.5rem)] [&>.grid]:mx-auto [&>.grid]:mb-4 [&>.grid]:max-w-5xl">
          <RequestError error={error} />
          <Outlet />
        </main>
      </div>
    </div>
  )
}
