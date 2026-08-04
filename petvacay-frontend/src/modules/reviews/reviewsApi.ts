import apiClient from '@/core/api/apiClient'
import type { Review } from '@/core/api/types'

export interface LeaveReviewRequest {
  rating: number
  comment?: string
}

export async function leaveReview(bookingId: string, request: LeaveReviewRequest): Promise<Review> {
  const { data } = await apiClient.post<Review>(`/bookings/${bookingId}/reviews`, request)
  return data
}

/** Las (hasta dos) resenas de una reserva concreta; solo la ven sus participantes. */
export async function listReviewsForBooking(bookingId: string): Promise<Review[]> {
  const { data } = await apiClient.get<Review[]>(`/bookings/${bookingId}/reviews`)
  return data
}

/** Resenas publicas de un cuidador. Sin autenticacion: cualquiera puede verlas antes de reservar. */
export async function listReviewsForSitter(sitterId: string): Promise<Review[]> {
  const { data } = await apiClient.get<Review[]>(`/reviews/sitters/${sitterId}`)
  return data
}
