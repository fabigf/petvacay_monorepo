import apiClient from '@/core/api/apiClient'
import type { UserProfile } from '@/core/api/types'

export interface RegisterRequest {
  email: string
  password: string
  firstName: string
  lastName: string
  birthDate: string
  asSitter: boolean
}

export interface LoginRequest {
  email: string
  password: string
}

/**
 * Respuesta de `POST /auth/login`: token de ACCESO + perfil.
 * El token de refresco no viene aquí: viaja en una cookie HttpOnly.
 */
export interface LoginResponse {
  accessToken: string
  tokenType: string
  /** Vida del token en segundos. */
  expiresIn: number
  user: UserProfile
  sitterVerified: boolean
}

export async function register(request: RegisterRequest): Promise<UserProfile> {
  const { data } = await apiClient.post<UserProfile>('/auth/register', request)
  return data
}

export async function login(request: LoginRequest): Promise<LoginResponse> {
  const { data } = await apiClient.post<LoginResponse>('/auth/login', request)
  return data
}

/**
 * Cierra la sesión en el servidor, revocando la familia de tokens de refresco.
 *
 * Sin esta llamada, borrar el estado local dejaría la cookie viva y cualquiera
 * con acceso al navegador podría restaurar la sesión.
 */
export async function logout(): Promise<void> {
  await apiClient.post('/auth/logout')
}
