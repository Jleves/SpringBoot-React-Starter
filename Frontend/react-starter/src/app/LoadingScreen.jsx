export function LoadingScreen() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 bg-slate-50 text-slate-500" role="status" aria-live="polite">
      <span className="size-8 animate-spin rounded-full border-3 border-slate-200 border-t-emerald-700" aria-hidden="true" />
      <p>Restaurando tu sesión…</p>
    </main>
  )
}
