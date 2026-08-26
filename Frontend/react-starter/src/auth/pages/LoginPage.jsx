import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { AuthCard } from '../components/AuthCard.jsx'
import { FormField } from '../components/FormField.jsx'
import { RequestError } from '../components/RequestError.jsx'
import { fieldError } from '../components/fieldErrors.js'
import { useAuth } from '../context/useAuth.js'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  const destination = location.state?.from || '/app/profile'

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(form)
      navigate(destination, { replace: true })
    } catch (requestError) {
      setError(requestError)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthCard
      eyebrow="Bienvenido"
      title="Ingresá a tu cuenta"
      description="Usá las credenciales configuradas para tu entorno."
      footer={<span>¿Olvidaste tu contraseña? <Link to="/forgot-password">Recuperarla</Link></span>}
    >
      <form className="mt-7 grid gap-5" onSubmit={handleSubmit}>
        <RequestError error={error} />
        <FormField id="email" label="Email" type="email" autoComplete="email" required value={form.email} error={fieldError(error, 'email')} onChange={(event) => setForm({ ...form, email: event.target.value })} />
        <FormField id="password" label="Contraseña" type="password" autoComplete="current-password" required value={form.password} error={fieldError(error, 'password')} onChange={(event) => setForm({ ...form, password: event.target.value })} />
        <button className="cursor-pointer rounded-xl bg-emerald-700 px-4 py-3 font-bold text-white transition hover:-translate-y-px hover:bg-emerald-800 disabled:cursor-wait disabled:opacity-60" disabled={submitting} type="submit">
          {submitting ? 'Ingresando…' : 'Ingresar'}
        </button>
      </form>
    </AuthCard>
  )
}
