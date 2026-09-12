import { useRef, useState } from 'react'
import { FormField } from '../../auth/components/FormField.jsx'
import { RequestError } from '../../auth/components/RequestError.jsx'
import { fieldError } from '../../auth/components/fieldErrors.js'
import { createUser } from '../services/adminUserService.js'

const emptyForm = { email: '', password: '', role: 'USER' }

export function CreateUserPage() {
  const [form, setForm] = useState(emptyForm)
  const [error, setError] = useState(null)
  const [created, setCreated] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const inFlight = useRef(false)

  async function handleSubmit(event) {
    event.preventDefault()
    if (inFlight.current) return
    inFlight.current = true
    setSubmitting(true)
    setError(null)
    setCreated(null)
    try {
      const user = await createUser({ ...form, email: form.email.trim().toLowerCase() })
      setCreated(user)
      setForm(emptyForm)
    } catch (requestError) {
      setError(requestError.status === 409
        ? { ...requestError, message: 'Ya existe una cuenta con ese email.' }
        : requestError)
    } finally {
      inFlight.current = false
      setSubmitting(false)
    }
  }

  const roleError = fieldError(error, 'role')
  return (
    <section className="mx-auto max-w-2xl rounded-2xl border border-slate-200 bg-white p-6 sm:p-8">
      <h1 className="text-2xl font-bold text-slate-900">Crear usuario</h1>
      <p className="mt-2 text-slate-600">Ingresá el email, la contraseña inicial y el rol de la nueva cuenta.</p>
      {created && <div className="mt-5 rounded-xl bg-emerald-50 p-4 text-emerald-900" role="status">Cuenta creada: {created.email}. Rol: {created.role}.</div>}
      <form className="mt-6 grid gap-5" onSubmit={handleSubmit}>
        <RequestError error={error} />
        <fieldset disabled={submitting} className="grid gap-5">
          <FormField id="new-email" label="Email" type="email" autoComplete="off" required value={form.email} error={fieldError(error, 'email')} onChange={(event) => setForm({ ...form, email: event.target.value })} />
          <FormField id="new-password" label="Contraseña inicial" type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={form.password} error={fieldError(error, 'password')} onChange={(event) => setForm({ ...form, password: event.target.value })} />
          <label className="grid gap-2 text-sm font-semibold text-slate-700" htmlFor="new-role">
            Rol
            <select id="new-role" className="rounded-xl border border-slate-300 bg-white px-3.5 py-3" required value={form.role} aria-invalid={Boolean(roleError)} aria-describedby={roleError ? 'new-role-error' : undefined} onChange={(event) => setForm({ ...form, role: event.target.value })}>
              <option value="USER">USER</option>
              <option value="ADMIN">ADMIN</option>
              <option value="SUPER_ADMIN">SUPER_ADMIN</option>
            </select>
            {roleError && <small id="new-role-error" className="text-red-700">{roleError}</small>}
          </label>
          <button className="cursor-pointer rounded-xl bg-emerald-700 px-4 py-3 font-bold text-white hover:bg-emerald-800 disabled:cursor-wait disabled:opacity-60" disabled={submitting} type="submit">{submitting ? 'Creando…' : 'Crear cuenta'}</button>
        </fieldset>
      </form>
    </section>
  )
}
