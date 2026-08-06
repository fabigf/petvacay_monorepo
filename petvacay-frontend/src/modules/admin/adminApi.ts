import apiClient from '@/core/api/apiClient'
import type {
  AdminSitter,
  BookingDetail,
  BookingStatus,
  DisputeResolution,
  Incident,
  IncidentStatus,
  IncidentType,
  PageResponse,
  Role,
  SitterProfile,
  UserProfile,
  VerificationStatus,
} from '@/core/api/types'

/**
 * Back-office. Todas estas rutas exigen `ROLE_ADMIN` en el backend; el rol del
 * frontend solo decide si se enseña el enlace.
 *
 * Están repartidas entre dos servicios — cuidadores en identity-service,
 * reservas en booking-service — pero el Gateway las publica bajo el mismo
 * prefijo `/admin`, así que desde aquí se ven como una sola API.
 */

export async function listPendingSitters(): Promise<SitterProfile[]> {
  const { data } = await apiClient.get<SitterProfile[]>('/admin/sitters/pending')
  return data
}

export async function approveSitter(sitterId: string): Promise<SitterProfile> {
  const { data } = await apiClient.post<SitterProfile>(`/admin/sitters/${sitterId}/approve`)
  return data
}

export async function rejectSitter(sitterId: string, reason: string): Promise<SitterProfile> {
  const { data } = await apiClient.post<SitterProfile>(`/admin/sitters/${sitterId}/reject`, { reason })
  return data
}

/** Reservas filtradas por estado; por defecto las que están en disputa. */
export async function listBookingsByStatus(status: BookingStatus): Promise<BookingDetail[]> {
  const { data } = await apiClient.get<BookingDetail[]>('/admin/bookings', { params: { status } })
  return data
}

export interface ResolveDisputeRequest {
  resolution: DisputeResolution
  /** Solo se lee en resoluciones intermedias; en el resto lo deduce el servidor. */
  refundToClient?: number
  notes: string
}

export async function resolveDispute(
  bookingId: string,
  request: ResolveDisputeRequest,
): Promise<BookingDetail> {
  const { data } = await apiClient.post<BookingDetail>(
    `/admin/bookings/${bookingId}/resolve-dispute`,
    request,
  )
  return data
}

export interface UserFilters {
  query?: string
  role?: Role
  active?: boolean
  page?: number
  size?: number
}

/** Cuentas paginadas; cada filtro es opcional y se combinan entre sí. */
export async function listUsers(filters: UserFilters = {}): Promise<PageResponse<UserProfile>> {
  const { data } = await apiClient.get<PageResponse<UserProfile>>('/admin/users', {
    params: { page: 0, size: 20, ...filters },
  })
  return data
}

export interface SitterFilters {
  status?: VerificationStatus
  query?: string
  page?: number
  size?: number
}

/**
 * Cuidadores paginados con su ficha completa: perfil, cuenta y estado del KYC.
 * Es lo que alimenta el listado del panel y su vista de detalle.
 */
export async function listSitters(filters: SitterFilters = {}): Promise<PageResponse<AdminSitter>> {
  const { data } = await apiClient.get<PageResponse<AdminSitter>>('/admin/sitters', {
    params: { page: 0, size: 20, ...filters },
  })
  return data
}

export async function getSitterDossier(sitterId: string): Promise<AdminSitter> {
  const { data } = await apiClient.get<AdminSitter>(`/admin/sitters/${sitterId}`)
  return data
}

export interface UpdateUserRequest {
  email: string
  firstName: string
  lastName: string
}

/** Corrige los datos de contacto. Roles y fecha de nacimiento no se tocan aquí. */
export async function updateUser(userId: string, request: UpdateUserRequest): Promise<UserProfile> {
  const { data } = await apiClient.put<UserProfile>(`/admin/users/${userId}`, request)
  return data
}

/**
 * Expulsión de una cuenta (negocio.md 2.3). Es un borrado lógico: la cuenta deja
 * de poder entrar pero conserva su historial, y `reactivateUser` lo deshace.
 */
export async function deactivateUser(userId: string): Promise<UserProfile> {
  const { data } = await apiClient.post<UserProfile>(`/admin/users/${userId}/deactivate`)
  return data
}

export async function reactivateUser(userId: string): Promise<UserProfile> {
  const { data } = await apiClient.post<UserProfile>(`/admin/users/${userId}/reactivate`)
  return data
}

export interface IncidentFilters {
  status?: IncidentStatus
  type?: IncidentType
  page?: number
  size?: number
}

/** Histórico de incidencias paginado: disputas y alertas médicas. */
export async function listIncidents(
  filters: IncidentFilters = {},
): Promise<PageResponse<Incident>> {
  const { data } = await apiClient.get<PageResponse<Incident>>('/admin/incidents', {
    params: { page: 0, size: 20, ...filters },
  })
  return data
}

/**
 * Marca una incidencia como atendida. Las disputas se cierran solas al
 * resolverlas; esto es para las alertas médicas, que no tienen una transición
 * de reserva que las cierre por su cuenta.
 */
export async function closeIncident(incidentId: string, notes: string): Promise<Incident> {
  const { data } = await apiClient.post<Incident>(`/admin/incidents/${incidentId}/close`, { notes })
  return data
}

/**
 * Reconstruye en el buscador la reputación de todos los cuidadores a partir de
 * las reseñas reales. Herramienta de reparación para cuando la proyección se
 * desincroniza; al republicar valores absolutos, repetirla no hace daño.
 */
export async function reprojectReputations(): Promise<number> {
  const { data } = await apiClient.post<{ sitters: number }>('/admin/bookings/reviews/reproject')
  return data.sitters
}
