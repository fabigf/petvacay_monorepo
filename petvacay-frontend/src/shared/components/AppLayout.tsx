import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Toaster } from 'sonner'
import { logout as logoutRequest } from '@/modules/auth/authApi'
import { useAuthStore } from '@/core/security/authStore'
import { NotificationBell } from '@/modules/notifications/NotificationBell'
import { AppFooter } from '@/shared/components/AppFooter'
import { Button } from '@/shared/components/ui'

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-lg px-3 py-2 text-sm font-medium transition ${
    isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100'
  }`

/** Mismo enlace en el menú desplegable del móvil: ocupa toda la fila. */
const mobileNavLinkClass = ({ isActive }: { isActive: boolean }) =>
  `block rounded-lg px-3 py-2.5 text-sm font-medium transition ${
    isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-700 hover:bg-slate-100'
  }`

export function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const profile = useAuthStore((state) => state.profile)
  const logout = useAuthStore((state) => state.logout)
  const isSitter = profile?.roles.includes('ROLE_SITTER') ?? false
  const isAdmin = profile?.roles.includes('ROLE_ADMIN') ?? false

  const [menuOpen, setMenuOpen] = useState(false)

  // Al cambiar de página: cerrar el menú y volver arriba. Sin lo segundo, ir de
  // una lista larga a otra pantalla la abre por la mitad, que es lo que hace que
  // una aplicación de una sola página se note "rara" frente a una web normal.
  useEffect(() => {
    setMenuOpen(false)
    window.scrollTo({ top: 0 })
  }, [location.pathname])

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

  const links = (
    <>
      <NavLink to="/search" className={menuOpen ? mobileNavLinkClass : navLinkClass}>
        Buscar cuidador
      </NavLink>
      {profile && (
        <>
          <NavLink to="/dashboard" className={menuOpen ? mobileNavLinkClass : navLinkClass}>
            Mis reservas
          </NavLink>
          <NavLink to="/pets" className={menuOpen ? mobileNavLinkClass : navLinkClass}>
            Mis mascotas
          </NavLink>
        </>
      )}
      {isSitter && (
        <NavLink to="/dashboard/sitter" className={menuOpen ? mobileNavLinkClass : navLinkClass}>
          Mi perfil de cuidador
        </NavLink>
      )}
      {isAdmin && (
        <NavLink to="/admin" className={menuOpen ? mobileNavLinkClass : navLinkClass}>
          Administración
        </NavLink>
      )}
    </>
  )

  return (
    <div className="flex min-h-screen flex-col">
      {/* Primer elemento enfocable de la página: quien navega con teclado puede
          saltarse la barra entera en lugar de tabular por ella en cada pantalla. */}
      <a
        href="#contenido"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-brand-600 focus:px-4 focus:py-2 focus:text-sm focus:font-semibold focus:text-white"
      >
        Saltar al contenido
      </a>

      <header className="sticky top-0 z-20 border-b border-sand-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
          <Link to="/" className="shrink-0 text-lg font-bold tracking-tight text-brand-700">
            🐾 PetVacay
          </Link>

          <nav aria-label="Principal" className="hidden items-center gap-1 lg:flex">
            {links}
          </nav>

          <div className="flex items-center gap-2">
            {profile ? (
              <>
                <NotificationBell />
                <span className="hidden text-sm text-slate-600 xl:inline">
                  Hola, {profile.firstName}
                </span>
                <Button
                  variant="ghost"
                  className="hidden lg:inline-flex"
                  onClick={() => void handleLogout()}
                >
                  Salir
                </Button>
              </>
            ) : (
              <>
                <Button
                  variant="ghost"
                  className="hidden lg:inline-flex"
                  onClick={() => navigate('/login')}
                >
                  Entrar
                </Button>
                <Button className="hidden lg:inline-flex" onClick={() => navigate('/register')}>
                  Crear cuenta
                </Button>
              </>
            )}

            <button
              type="button"
              onClick={() => setMenuOpen((open) => !open)}
              aria-expanded={menuOpen}
              aria-controls="menu-movil"
              aria-label={menuOpen ? 'Cerrar menú' : 'Abrir menú'}
              className="inline-flex size-10 items-center justify-center rounded-lg text-slate-600 transition hover:bg-slate-100 lg:hidden"
            >
              <span aria-hidden className="text-xl leading-none">
                {menuOpen ? '✕' : '☰'}
              </span>
            </button>
          </div>
        </div>

        {menuOpen && (
          <div id="menu-movil" className="border-t border-sand-200 bg-white lg:hidden">
            <nav aria-label="Principal (móvil)" className="mx-auto max-w-6xl px-4 py-3">
              <div className="flex flex-col gap-1">{links}</div>

              <div className="mt-3 flex flex-col gap-2 border-t border-sand-100 pt-3">
                {profile ? (
                  <>
                    <p className="px-3 text-sm text-slate-500">Hola, {profile.firstName}</p>
                    <Button variant="secondary" onClick={() => void handleLogout()}>
                      Salir
                    </Button>
                  </>
                ) : (
                  <>
                    <Button variant="secondary" onClick={() => navigate('/login')}>
                      Entrar
                    </Button>
                    <Button onClick={() => navigate('/register')}>Crear cuenta</Button>
                  </>
                )}
              </div>
            </nav>
          </div>
        )}
      </header>

      <main id="contenido" className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <Outlet />
      </main>

      <AppFooter />

      {/* Toasts de errores de negocio (409) según especificaciones.md 4 */}
      <Toaster richColors position="top-right" />
    </div>
  )
}
