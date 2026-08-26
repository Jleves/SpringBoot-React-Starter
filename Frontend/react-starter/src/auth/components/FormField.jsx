export function FormField({ label, error, id, ...inputProps }) {
  return (
    <label className="grid gap-2 text-sm font-semibold text-slate-700" htmlFor={id}>
      <span>{label}</span>
      <input className="w-full rounded-xl border border-slate-300 bg-white px-3.5 py-3 text-slate-900 transition focus:border-emerald-700 focus:ring-4 focus:ring-emerald-700/10 focus:outline-none aria-invalid:border-red-700" id={id} aria-invalid={Boolean(error)} aria-describedby={error ? `${id}-error` : undefined} {...inputProps} />
      {error && <small className="font-medium text-red-700" id={`${id}-error`}>{error}</small>}
    </label>
  )
}
