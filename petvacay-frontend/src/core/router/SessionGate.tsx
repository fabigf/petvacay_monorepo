import { useEffect, useState, type ReactNode } from 'react'
import { restoreSession } from '@/core/api/apiClient'

/**
 * Restaura la sesión antes de renderizar la aplicación.
 *
 * <p>Como el token de acceso vive solo en memoria, tras recargar la página no
 * existe hasta que se canjea la cookie de refresco. Sin esta espera, una ruta
 * protegida vería `isAuthenticated() === false` durante un instante y expulsaría
 * al usuario a `/login` aunque su sesión fuera perfectamente válida.
 */
export function SessionGate({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false)

  useEffect(() => {
    void restoreSession().finally(() => setReady(true))
  }, [])

  if (!ready) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <span
          aria-label="Cargando"
          className="size-8 animate-spin rounded-full border-4 border-brand-200 border-t-brand-600"
        />
      </div>
    )
  }

  return <>{children}</>
}
