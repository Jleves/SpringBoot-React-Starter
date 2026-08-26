const listeners = new Set()

export function subscribeToSessionExpired(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function publishSessionExpired() {
  listeners.forEach((listener) => listener())
}
