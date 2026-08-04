import { useCallback } from 'react'
import { toast } from 'sonner'
import { toProblemDetails, toFieldErrors } from '@/core/api/problemDetails'
import type { ProblemDetails } from '@/core/api/types'

interface HandledProblem {
  problem: ProblemDetails
  fieldErrors: Record<string, string>
}

/**
 * Estrategia de renderizado de errores de especificaciones.md sección 4:
 *
 * - 400: no se lanza toast, se devuelven los errores por campo para pintarlos
 *   en rojo junto a cada input.
 * - 409 y demás errores de negocio: se muestra `detail` en un toast sin romper
 *   el flujo de la pantalla.
 */
export function useProblemToast() {
  return useCallback((error: unknown): HandledProblem => {
    const problem = toProblemDetails(error)
    const fieldErrors = toFieldErrors(problem)

    if (problem.status === 400 && Object.keys(fieldErrors).length > 0) {
      return { problem, fieldErrors }
    }

    toast.error(problem.title, {
      description: problem.detail,
    })

    return { problem, fieldErrors }
  }, [])
}
