import { Link } from 'react-router-dom'

export function AuthCard({ eyebrow, title, description, children, footer }) {
  return (
    <main className="grid min-h-screen md:grid-cols-[minmax(300px,0.9fr)_minmax(420px,1.1fr)]">
      <section className="relative flex min-h-44 flex-col justify-between overflow-hidden bg-[radial-gradient(circle_at_15%_15%,#328b73_0,transparent_30%),linear-gradient(145deg,#102e27,#176b57)] p-6 text-white md:min-h-screen md:p-[clamp(2rem,5vw,5rem)]" aria-label="Presentación">
        <div className="pointer-events-none absolute top-[30%] -right-68 size-128 rounded-full border border-white/15" />
        <Link className="relative text-lg font-extrabold tracking-tight text-white hover:no-underline" to="/">Spring Starter</Link>
        <div className="relative mt-6 md:mt-12">
          <p className="mb-3 hidden text-xs font-extrabold tracking-[.12em] text-white uppercase sm:block">Base segura y reutilizable</p>
          <h1 className="max-w-[18ch] text-3xl leading-tight font-bold tracking-[-.05em] md:max-w-[11ch] md:text-[clamp(2.3rem,4vw,4.5rem)] md:leading-[1.02]">Tu aplicación empieza con una sesión bien resuelta.</h1>
          <p className="mt-5 hidden max-w-xl leading-7 text-white/75 md:block">React y Spring Boot conectados mediante cookies HttpOnly, protección CSRF y renovación transparente.</p>
        </div>
      </section>
      <section className="flex items-center justify-center bg-slate-50 p-5 md:p-8">
        <div className="w-full max-w-lg rounded-2xl border border-slate-200 bg-white p-6 shadow-[0_22px_60px_rgba(23,43,38,.1)] md:p-12">
          {eyebrow && <p className="mb-3 text-xs font-extrabold tracking-[.12em] text-emerald-700 uppercase">{eyebrow}</p>}
          <h2 className="mb-3 text-3xl font-bold tracking-[-.04em] text-slate-900">{title}</h2>
          {description && <p className="leading-relaxed text-slate-500">{description}</p>}
          {children}
          {footer && <div className="mt-7 border-t border-slate-200 pt-5 text-center text-sm text-slate-500">{footer}</div>}
        </div>
      </section>
    </main>
  )
}
