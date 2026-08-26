import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { AuthCard } from '../components/AuthCard.jsx'
import { FormField } from '../components/FormField.jsx'
import { RequestError } from '../components/RequestError.jsx'
import { fieldError } from '../components/fieldErrors.js'
import { resetPassword, validateResetToken } from '../services/authService.js'

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') || ''
  const [tokenStatus, setTokenStatus] = useState('validating')
  const [form, setForm] = useState({ newPassword: '', confirmation: '' })
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let active = true
    if (!token) {
      setTokenStatus('invalid')
      return undefined
    }
    validateResetToken(token)
      .then(() => active && setTokenStatus('valid'))
      .catch(() => active && setTokenStatus('invalid'))
    return () => { active = false }
  }, [token])

  async function handleSubmit(event) {
    event.preventDefault()
    if (form.newPassword !== form.confirmation) {
      setError(new Error('Las contraseñas no coinciden.'))
      return
    }
    setError(null)
    setSubmitting(true)
    try {
      await resetPassword({ token, newPassword: form.newPassword })
      setSuccess(true)
    } catch (requestError) {
      setError(requestError)
    } finally {
      setSubmitting(false)
    }
  }

  let content = <div className="py-8 text-center text-slate-500" role="status">Validando el enlace…</div>
  if (tokenStatus === 'invalid') content = <div className="mt-6 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700" role="alert">El enlace es inválido o venció. Solicitá uno nuevo.</div>
  if (success) content = <div className="mt-6 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700" role="status">Tu contraseña fue actualizada. Ya podés ingresar.</div>
  if (tokenStatus === 'valid' && !success) content = (
    <form className="mt-7 grid gap-5" onSubmit={handleSubmit}>
      <RequestError error={error} />
      <FormField id="newPassword" label="Nueva contraseña" type="password" minLength="8" maxLength="72" autoComplete="new-password" required value={form.newPassword} error={fieldError(error, 'newPassword')} onChange={(event) => setForm({ ...form, newPassword: event.target.value })} />
      <FormField id="confirmation" label="Repetir contraseña" type="password" minLength="8" maxLength="72" autoComplete="new-password" required value={form.confirmation} onChange={(event) => setForm({ ...form, confirmation: event.target.value })} />
      <button className="cursor-pointer rounded-xl bg-emerald-700 px-4 py-3 font-bold text-white transition hover:-translate-y-px hover:bg-emerald-800 disabled:cursor-wait disabled:opacity-60" disabled={submitting} type="submit">{submitting ? 'Guardando…' : 'Cambiar contraseña'}</button>
    </form>
  )

  return <AuthCard title="Nueva contraseña" description="Elegí una contraseña de entre 8 y 72 caracteres." footer={<Link to="/login">Volver al ingreso</Link>}>{content}</AuthCard>
}
