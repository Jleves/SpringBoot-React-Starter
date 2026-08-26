export class ApiClientError extends Error {
  constructor({ status = 0, data = null, fallbackMessage = 'No se pudo completar la solicitud.' } = {}) {
    super(data?.message || fallbackMessage)
    this.name = 'ApiClientError'
    this.status = status
    this.code = data?.code || null
    this.fieldErrors = data?.fieldErrors || []
    this.requestId = data?.requestId || null
    this.data = data
  }
}
