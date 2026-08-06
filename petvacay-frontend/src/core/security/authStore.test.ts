import { beforeEach, describe, expect, it } from 'vitest'
import type { UserProfile } from '@/core/api/types'
import { useAuthStore } from './authStore'

/**
 * Fija la decisión de seguridad más importante del cliente: el token de acceso
 * **no se persiste**.
 *
 * <p>Es una garantía silenciosa — si alguien añadiera `token` a `partialize`
 * para "arreglar" que la sesión se pierde al refrescar, todo seguiría
 * funcionando mejor en apariencia mientras el token queda legible para
 * cualquier XSS. Nada más en la aplicación fallaría al hacerlo.
 */
describe('authStore', () => {
  const profile: UserProfile = {
    userId: 'user-1',
    email: 'ana@petvacay.test',
    firstName: 'Ana',
    lastName: 'García',
    roles: ['ROLE_CLIENT', 'ROLE_SITTER'],
    active: true,
  }

  const session = {
    token: 'jwt-secreto',
    profile,
    sitterVerified: true,
    expiresAt: Date.now() + 900_000,
  }

  beforeEach(() => {
    localStorage.clear()
    useAuthStore.setState({
      token: null,
      expiresAt: null,
      userId: null,
      profile: null,
      sitterVerified: false,
      bootstrapped: false,
    })
  })

  it('el token de acceso nunca llega a localStorage', () => {
    useAuthStore.getState().login(session)

    const persisted = localStorage.getItem('petvacay-auth') ?? ''
    expect(persisted).not.toContain('jwt-secreto')
    expect(persisted).not.toContain('token')
    // El perfil sí, para pintar la interfaz sin parpadeo.
    expect(persisted).toContain('user-1')
  })

  it('tampoco persiste la caducidad del token', () => {
    useAuthStore.getState().login(session)

    expect(localStorage.getItem('petvacay-auth') ?? '').not.toContain('expiresAt')
  })

  it('login deja la sesión completa en memoria', () => {
    useAuthStore.getState().login(session)
    const state = useAuthStore.getState()

    expect(state.token).toBe('jwt-secreto')
    expect(state.userId).toBe('user-1')
    expect(state.sitterVerified).toBe(true)
    expect(state.isAuthenticated()).toBe(true)
    // Entrar cuenta como arranque resuelto: la interfaz ya puede pintarse.
    expect(state.bootstrapped).toBe(true)
  })

  it('logout borra token, perfil y verificación', () => {
    useAuthStore.getState().login(session)
    useAuthStore.getState().logout()
    const state = useAuthStore.getState()

    expect(state.token).toBeNull()
    expect(state.userId).toBeNull()
    expect(state.profile).toBeNull()
    expect(state.sitterVerified).toBe(false)
    expect(state.isAuthenticated()).toBe(false)
    // Sigue "arrancado": si no, la app volvería a la pantalla de carga.
    expect(state.bootstrapped).toBe(true)
  })

  it('no se considera autenticado con perfil pero sin token', () => {
    // Es el estado exacto tras recargar la página: el perfil se rehidrata de
    // localStorage y el token todavía no se ha canjeado por la cookie.
    useAuthStore.setState({ profile, userId: 'user-1', token: null })

    expect(useAuthStore.getState().isAuthenticated()).toBe(false)
  })

  it('hasRole responde por los roles del perfil', () => {
    useAuthStore.getState().login(session)
    const { hasRole } = useAuthStore.getState()

    expect(hasRole('ROLE_CLIENT')).toBe(true)
    expect(hasRole('ROLE_SITTER')).toBe(true)
    expect(hasRole('ROLE_ADMIN')).toBe(false)
  })

  it('hasRole es falso sin sesión, sin reventar', () => {
    expect(useAuthStore.getState().hasRole('ROLE_ADMIN')).toBe(false)
  })
})
