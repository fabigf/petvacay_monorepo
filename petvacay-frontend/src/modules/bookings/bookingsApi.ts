import apiClient from '@/core/api/apiClient'
import type {
  BookingDetail,
  BookingHandoff,
  BookingPet,
  CreateBookingRequest,
  DailyAvailability,
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

/**
 * Fichas de las mascotas de la reserva. Las ven sus dos partes: el cliente
 * porque son suyas y el cuidador porque se las van a confiar — sin conocer la
 * especie, el peso o las necesidades médicas no puede prepararse (negocio.md 2.1).
 *
 * Es una llamada aparte y no parte del detalle porque las mascotas viven en otro
 * servicio: incrustarlas obligaría a esperar esa consulta en cada lectura de una
 * reserva, incluido el listado, que no las necesita.
 */
export async function listBookingPets(bookingId: string): Promise<BookingPet[]> {
  const { data } = await apiClient.get<BookingPet[]>(`/bookings/${bookingId}/pets`)
  return data
}

/**
 * Plazas libres del cuidador noche a noche en el rango indicado.
 *
 * Permite avisar de que no hay hueco ANTES de enviar la solicitud, en vez de
 * que el cliente rellene el formulario entero y reciba un 409 al confirmarlo.
 */
export async function getAvailability(
  sitterId: string,
  startDate: string,
  endDate: string,
): Promise<DailyAvailability[]> {
  const { data } = await apiClient.get<DailyAvailability[]>('/bookings/availability', {
    params: { sitterId, startDate, endDate },
  })
  return data
}

/**
 * Dirección exacta del cuidador y franja de entrega/recogida (negocio.md 4.2 y
 * 5). El backend la rechaza con 409 (`ERR_HANDOFF_NOT_AVAILABLE`) mientras la
 * reserva no esté pagada; ese caso se deja para que lo gestione quien llama, no
 * se resuelve aquí a `null` en silencio.
 */
export async function getBookingHandoff(bookingId: string): Promise<BookingHandoff> {
  const { data } = await apiClient.get<BookingHandoff>(`/bookings/${bookingId}/handoff`)
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

/**
 * Botón de alerta médica del cuidador (negocio.md 5, `MascotaEnPeligroEvent`).
 *
 * No cambia el estado de la reserva: avisa de inmediato al dueño y al equipo de
 * soporte. Deliberadamente distinto de abrir una incidencia, que sí congela el
 * pago — castigar económicamente al cuidador por dar la alarma sería justo el
 * incentivo contrario al que se busca.
 */
export async function raisePetEmergency(
  bookingId: string,
  description: string,
): Promise<BookingDetail> {
  const { data } = await apiClient.post<BookingDetail>(`/bookings/${bookingId}/emergency`, {
    description,
  })
  return data
}

export async function openDispute(bookingId: string, reason: string): Promise<BookingDetail> {
  const { data } = await apiClient.post<BookingDetail>(`/bookings/${bookingId}/disputes`, {
    reason,
  })
  return data
}
