import apiClient from '@/core/api/apiClient'
import type {
  BookingDetail,
  CreateBookingRequest,
  CreateBookingResponse,
} from '@/core/api/types'

/** Lado de escritura del CQRS: comandos contra el Booking Service. */

export async function createBooking(
  request: CreateBookingRequest,
): Promise<CreateBookingResponse> {
  const { data } = await apiClient.post<CreateBookingResponse>('/bookings', request)
  return data
}

export async function getBooking(bookingId: string): Promise<BookingDetail> {
  const { data } = await apiClient.get<BookingDetail>(`/bookings/${bookingId}`)
  return data
}

export async function listBookings(role: 'client' | 'sitter'): Promise<BookingDetail[]> {
  const { data } = await apiClient.get<BookingDetail[]>('/bookings', { params: { role } })
  return data
}

export async function acceptBooking(bookingId: string): Promise<BookingDetail> {
  const { data } = await apiClient.post<BookingDetail>(`/bookings/${bookingId}/accept`)
  return data
}

export async function rejectBooking(bookingId: string): Promise<BookingDetail> {
  const { data } = await apiClient.post<BookingDetail>(`/bookings/${bookingId}/reject`)
  return data
}

/**
 * Confirma el pago de la reserva.
 *
 * Con Stripe configurado, el cobro real lo hace el navegador contra Stripe y el
 * resultado llega por webhook; esta llamada marca la transición de estado de la
 * reserva. En el entorno simulado (sin claves de Stripe) es el único paso.
 */
export async function confirmPayment(bookingId: string): Promise<BookingDetail> {
  const { data } = await apiClient.post<BookingDetail>(`/bookings/${bookingId}/confirm-payment`)
  return data
}

/**
 * Cancela la reserva.
 *
 * No recibe el rol del que cancela: lo deduce el servidor a partir del token.
 * Hasta la Fase 5 viajaba como parámetro, lo que permitía a un cliente
 * declararse cuidador y cobrar un reembolso íntegro que no le correspondía.
 */
export async function cancelBooking(bookingId: string): Promise<BookingDetail> {
  const { data } = await apiClient.post<BookingDetail>(`/bookings/${bookingId}/cancel`)
  return data
}

export async function openDispute(bookingId: string, reason: string): Promise<BookingDetail> {
  const { data } = await apiClient.post<BookingDetail>(`/bookings/${bookingId}/disputes`, {
    reason,
  })
  return data
}
