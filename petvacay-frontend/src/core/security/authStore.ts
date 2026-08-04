import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { Role, UserProfile } from '@/core/api/types'

interface AuthSession {
  token: string
  profile: UserProfile
  sitterVerified: boolean
  /** Instante de caducidad del token de acceso, en milisegundos epoch. */
  expiresAt: number
}

interface AuthState {
  /** Token de acceso. NO se persiste: vive solo en memoria. */
  token: string | null
  expiresAt: number | null
  userId: string | null
  profile: UserProfile | null
  sitterVerified: boolean
  /** Falso hasta que se ha intentado restaurar la sesión al arrancar la app. */
  bootstrapped: boolean
  login: (session: AuthSession) => void
  logout: () => void
  markBootstrapped: () => void
  hasRole: (role: Role) => boolean
  isAuthenticated: () => boolean
}

/**
 * Estado global de sesión.
 *
 * <p>El token de acceso vive **solo en memoria**. No se guarda en
 * `localStorage` ni en `sessionStorage`, de modo que un XSS no tiene de dónde
 * leerlo: desaparece al cerrar la pestaña. Lo que sí sobrevive a un refresco de
 * página es la cookie de refresco, que el navegador guarda como `HttpOnly` y el
 * JavaScript no puede tocar; `restoreSession()` la canjea al arrancar por un
 * token de acceso nuevo.
 *
 * <p>Sí se persiste el **perfil** (nombre, roles), pero solo para pintar la
 * interfaz sin parpadeo mientras se restaura la sesión. No es una credencial:
 * manipularlo en el navegador solo consigue que la propia interfaz se
 * confunda — cada petición al backend se sigue validando contra la firma del
 * token.
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      token: null,
      expiresAt: null,
      userId: null,
      profile: null,
      sitterVerified: false,
      bootstrapped: false,

      login: (session) =>
        set({
          token: session.token,
          expiresAt: session.expiresAt,
          userId: session.profile.userId,
          profile: session.profile,
          sitterVerified: session.sitterVerified,
          bootstrapped: true,
        }),

      logout: () =>
        set({
          token: null,
          expiresAt: null,
          userId: null,
          profile: null,
          sitterVerified: false,
          bootstrapped: true,
        }),

      markBootstrapped: () => set({ bootstrapped: true }),

      hasRole: (role) => get().profile?.roles.includes(role) ?? false,

      isAuthenticated: () => Boolean(get().token) && Boolean(get().userId),
    }),
    {
      name: 'petvacay-auth',
      // Lista blanca explícita: el token y su caducidad quedan fuera del
      // almacenamiento persistente por diseño, no por descuido.
      partialize: (state) => ({
        userId: state.userId,
        profile: state.profile,
        sitterVerified: state.sitterVerified,
      }),
    },
  ),
)
