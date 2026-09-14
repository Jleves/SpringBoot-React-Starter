import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/useAuth.js'
import { FormField } from './FormField.jsx'
import { RequestError } from './RequestError.jsx'
import { fieldError } from './fieldErrors.js'
import { publishSessionExpired } from '../../services/sessionEvents.js'

const emptyForm = { currentPassword: '', newPassword: '', confirmation: '' }

export function ChangePasswordForm() {
  const { changePassword } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState(emptyForm)
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const inFlight = useRef(false)

  async function submit(event) {
    event.preventDefault()
    if (inFlight.current || uncertain) return
    setError(null)
    if (form.newPassword !== form.confirmation) {
      setError({ message: 'Las contraseñas nuevas no coinciden.', fieldErrors: [{ field: 'confirmation', message: 'Repetí la misma contraseña.' }] })
      return
    }
    if (form.currentPassword === form.newPassword || new TextEncoder().encode(form.newPassword).length > 72) {
      setError({ message: 'La contraseña nueva debe ser diferente de la actual y no superar 72 bytes UTF-8.' })
      return
    }
    inFlight.current = true
    setSubmitting(true)
    try {
      await changePassword(form)
      setForm(emptyForm)
      navigate('/login', { replace: true, state: { passwordChanged: true } })
    } catch (requestError) {
      if (!requestError.status || requestError.status >= 500) {
        setUncertain(true)
        setForm(emptyForm)
        setError({ message: 'No pudimos confirmar el resultado. No repitas el cambio automáticamente; intentá ingresar con la contraseña nueva o recuperá el acceso.' })
      } else {
        setError(requestError)
      }
    } finally {
      inFlight.current = false
      setSubmitting(false)
    }
  }

  return (
    <section className="mt-8 rounded-2xl border border-slate-200 bg-white p-6 sm:p-8">
      <h2 className="text-xl font-bold text-slate-900">Cambiar contraseña</h2>
      <p className="mt-2 text-slate-600">Deberás volver a ingresar. Las demás sesiones dejarán de poder renovarse; sus accesos actuales pueden seguir vigentes hasta vencer.</p>
      <form className="mt-5 grid gap-5" onSubmit={submit}>
        <RequestError error={error} />
        <fieldset disabled={submitting || uncertain} className="grid gap-5">
          <FormField id="current-password" label="Contraseña actual" type="password" autoComplete="current-password" required maxLength={72} value={form.currentPassword} error={fieldError(error, 'currentPassword')} onChange={(e) => setForm({ ...form, currentPassword: e.target.value })} />
          <FormField id="new-password" label="Contraseña nueva" type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={form.newPassword} error={fieldError(error, 'newPassword')} onChange={(e) => setForm({ ...form, newPassword: e.target.value })} />
          <FormField id="confirm-password" label="Confirmar contraseña nueva" type="password" autoComplete="new-password" required minLength={8} maxLength={72} value={form.confirmation} error={fieldError(error, 'confirmation')} onChange={(e) => setForm({ ...form, confirmation: e.target.value })} />
          <button className="rounded-xl bg-emerald-700 px-4 py-3 font-bold text-white disabled:opacity-60" type="submit">{submitting ? 'Actualizando…' : 'Actualizar contraseña'}</button>
        </fieldset>
        {uncertain && <div className="flex gap-4"><Link to="/forgot-password">Recuperar acceso</Link><button type="button" onClick={() => { publishSessionExpired(); navigate('/login', { replace: true }) }}>Volver al login</button></div>}
      </form>
    </section>
  )
}
