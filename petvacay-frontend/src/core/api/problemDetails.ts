import { AxiosError } from 'axios'
import type { ProblemDetails } from './types'

/**
 * Normaliza cualquier fallo de Axios al formato RFC 7807, de modo que la UI
 * tenga siempre un `title`, un `detail` y un `code` con los que decidir
 * (especificaciones.md sección 4).
 */
export function toProblemDetails(error: unknown): ProblemDetails {
  if (error instanceof AxiosError) {
    const data = error.response?.data as ProblemDetails | undefined
    if (data && typeof data === 'object' && 'title' in data) {
      return data
    }
    if (error.code === 'ECONNABORTED' || !error.response) {
      return {
        title: 'Sin conexión con el servidor',
        status: 0,
        detail:
          'No hemos podido contactar con PetVacay. Comprueba tu conexión e inténtalo de nuevo.',
        code: 'ERR_NETWORK',
      }
    }
    return {
      title: 'Error inesperado',
      status: error.response.status,
      detail: error.message,
      code: 'ERR_UNKNOWN',
    }
  }

  return {
    title: 'Error inesperado',
    status: 0,
    detail: error instanceof Error ? error.message : String(error),
    code: 'ERR_UNKNOWN',
  }
}

/**
 * Extrae los errores de campo de un 400 para pintarlos junto a cada input.
 * Devuelve un mapa `nombreDeCampo -> motivo`.
 */
export function toFieldErrors(problem: ProblemDetails): Record<string, string> {
  if (!problem.invalid_params) return {}
  return Object.fromEntries(
    problem.invalid_params.map((param) => [param.name, param.reason]),
  )
}
