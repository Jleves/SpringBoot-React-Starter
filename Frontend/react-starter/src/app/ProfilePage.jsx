import { useAuth } from '../auth/context/useAuth.js'
import { ChangePasswordForm } from '../auth/components/ChangePasswordForm.jsx'

export function ProfilePage() {
  const { user } = useAuth()
  return (
    <section className="mx-auto max-w-5xl">
      <div>
        <p className="mb-3 text-xs font-extrabold tracking-[.12em] text-emerald-700 uppercase">Sesión activa</p>
        <h1 className="mb-2 text-[clamp(2rem,4vw,3.2rem)] font-bold tracking-[-.05em] text-slate-900">Tu perfil</h1>
        <p className="leading-relaxed text-slate-500">Estos datos llegan desde el backend y no se guardan en el navegador.</p>
      </div>
      <div className="mt-8 grid items-start gap-8 rounded-2xl border border-slate-200 bg-white p-[clamp(1.5rem,4vw,2.5rem)] shadow-[0_12px_35px_rgba(23,43,38,.06)] sm:grid-cols-[auto_1fr] sm:items-center">
        <div className="flex size-20 items-center justify-center rounded-2xl bg-emerald-100 text-3xl font-extrabold text-emerald-800" aria-hidden="true">{user?.email?.charAt(0).toUpperCase()}</div>
        <dl className="m-0">
          <div className="grid gap-1 border-b border-slate-100 py-3 sm:grid-cols-[9rem_1fr] sm:gap-4"><dt className="text-slate-500">Email</dt><dd className="m-0 [overflow-wrap:anywhere] font-semibold text-slate-900">{user?.email}</dd></div>
          <div className="grid gap-1 border-b border-slate-100 py-3 sm:grid-cols-[9rem_1fr] sm:gap-4"><dt className="text-slate-500">Rol</dt><dd className="m-0 font-semibold text-slate-900"><span className="inline-block rounded-full bg-emerald-50 px-2.5 py-1 text-xs text-emerald-800">{user?.role}</span></dd></div>
          <div className="grid gap-1 py-3 sm:grid-cols-[9rem_1fr] sm:gap-4"><dt className="text-slate-500">Identificador</dt><dd className="m-0 font-semibold text-slate-900">{user?.id}</dd></div>
        </dl>
      </div>
      <ChangePasswordForm />
    </section>
  )
}
