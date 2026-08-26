export function RequestError({ error }) {
  if (!error) return null
  return (
    <div className="grid gap-1 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm leading-relaxed text-red-700" role="alert">
      <span>{error.message}</span>
      {error.requestId && <small className="opacity-80">Referencia: {error.requestId}</small>}
    </div>
  )
}
