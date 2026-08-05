import apiClient from '@/core/api/apiClient'
import type {
  BookingDetail,
  BookingStatus,
  DisputeResolution,
  SitterProfile,
  UserProfile,
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

/** Expulsión fulminante de una cuenta (negocio.md 2.3). */
export async function deactivateUser(userId: string): Promise<UserProfile> {
  const { data } = await apiClient.post<UserProfile>(`/admin/users/${userId}/deactivate`)
  return data
}
