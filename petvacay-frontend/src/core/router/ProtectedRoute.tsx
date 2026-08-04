import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/core/security/authStore'
import type { Role } from '@/core/api/types'

interface ProtectedRouteProps {
  children: ReactNode
  /** Roles admitidos; si se omite basta con estar autenticado. */
  roles?: Role[]
  /**
   * Exige que el cuidador haya superado el KYC (especificaciones.md 2.2).
   * Sin esto, un cuidador pendiente de verificacion accederia a pantallas que
   * dan por hecho un perfil ya publicado en el buscador.
   */
  requireVerifiedSitter?: boolean
}

/**
 * Wrapper de rutas protegidas (especificaciones.md seccion 2.2).
 *
 * <p>Evalua roles y estado de verificación antes de renderizar la vista y
 * redirige a `/login` conservando el destino original. Desde la Fase 5 los datos
 * que evalua provienen de un JWT firmado, no de un estado local que el usuario
 * pudiera manipular.
 *
 * <p>Aun asi, esto **no es un control de seguridad**: cualquiera puede editar el
 * estado del navegador y forzar el render de una pantalla. Es una decision de
 * experiencia de usuario — no mostrar vistas que no van a funcionar. La
 * autorizacion real la aplica el backend en cada peticion, que es el unico sitio
 * donde puede aplicarse de verdad.
 */
export function ProtectedRoute({ children, roles, requireVerifiedSitter }: ProtectedRouteProps) {
  const location = useLocation()
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated())
  const profile = useAuthStore((state) => state.profile)
  const sitterVerified = useAuthStore((state) => state.sitterVerified)

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }

  if (roles && !roles.some((role) => profile?.roles.includes(role))) {
    return <Navigate to="/dashboard" replace />
  }

  // El cuidador existe y tiene el rol, pero aun no ha superado el KYC: se le deja
  // en su panel de onboarding en lugar de expulsarlo al dashboard de cliente.
  if (requireVerifiedSitter && !sitterVerified) {
    return <Navigate to="/dashboard/sitter" replace />
  }

  return <>{children}</>
}
