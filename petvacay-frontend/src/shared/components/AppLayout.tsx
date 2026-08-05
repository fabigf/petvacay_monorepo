import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { Toaster } from 'sonner'
import { logout as logoutRequest } from '@/modules/auth/authApi'
import { useAuthStore } from '@/core/security/authStore'
import { NotificationBell } from '@/modules/notifications/NotificationBell'
import { Button } from '@/shared/components/ui'

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-lg px-3 py-2 text-sm font-medium transition ${
    isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100'
  }`

export function AppLayout() {
  const navigate = useNavigate()
  const profile = useAuthStore((state) => state.profile)
  const logout = useAuthStore((state) => state.logout)
  const isSitter = profile?.roles.includes('ROLE_SITTER') ?? false
  const isAdmin = profile?.roles.includes('ROLE_ADMIN') ?? false

  const handleLogout = async () => {
    // Primero el servidor (revoca la familia de tokens), luego el estado local.
    // Si la llamada falla igualmente se limpia la sesión del navegador: para el
    // usuario "salir" tiene que funcionar siempre.
    try {
      await logoutRequest()
    } finally {
      logout()
      navigate('/')
    }
  }

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-10 border-b border-sand-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
          <Link to="/" className="text-lg font-bold tracking-tight text-brand-700">
            🐾 PetVacay
          </Link>

          <nav className="flex items-center gap-1">
            <NavLink to="/search" className={navLinkClass}>
              Buscar cuidador
            </NavLink>
            {profile && (
              <>
                <NavLink to="/dashboard" className={navLinkClass}>
                  Mis reservas
                </NavLink>
                <NavLink to="/pets" className={navLinkClass}>
                  Mis mascotas
                </NavLink>
              </>
            )}
            {isSitter && (
              <NavLink to="/dashboard/sitter" className={navLinkClass}>
                Mi perfil de cuidador
              </NavLink>
            )}
            {isAdmin && (
              <NavLink to="/admin" className={navLinkClass}>
                Administración
              </NavLink>
            )}
          </nav>

          <div className="flex items-center gap-2">
            {profile ? (
              <>
                <NotificationBell />
                <span className="hidden text-sm text-slate-600 sm:inline">
                  Hola, {profile.firstName}
                </span>
                <Button variant="ghost" onClick={() => void handleLogout()}>
                  Salir
                </Button>
              </>
            ) : (
              <>
                <Button variant="ghost" onClick={() => navigate('/login')}>
                  Entrar
                </Button>
                <Button onClick={() => navigate('/register')}>Crear cuenta</Button>
              </>
            )}
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <Outlet />
      </main>

      <footer className="border-t border-sand-200 bg-white py-6">
        <p className="mx-auto max-w-6xl px-4 text-xs text-slate-500">
          PetVacay · Microservicios con arquitectura hexagonal, CQRS y mensajería por eventos.
        </p>
      </footer>

      {/* Toasts de errores de negocio (409) según especificaciones.md 4 */}
      <Toaster richColors position="top-right" />
    </div>
  )
}
