import apiClient from '@/core/api/apiClient'

export interface PaymentIntentResponse {
  paymentIntentId: string
  /** Secreto con el que Stripe Elements monta el formulario de tarjeta. */
  clientSecret: string
  amount: number
  currency: string
}

/**
 * Pide al backend que prepare el cobro (especificaciones.md sección 3, paso 1).
 *
 * <p>El importe viaja para que el backend lo contraste, pero es el servidor
 * quien manda: `PaymentApplicationService` lo valida contra el que calculó el
 * dominio de reservas. Enviar 1 € en lugar de 183,75 € no abarata nada.
 */
export async function createPaymentIntent(
  bookingId: string,
  amount: number,
  currency: string,
): Promise<PaymentIntentResponse> {
  const { data } = await apiClient.post<PaymentIntentResponse>(
    `/payments/bookings/${bookingId}/intent`,
    { amount, currency },
  )
  return data
}
