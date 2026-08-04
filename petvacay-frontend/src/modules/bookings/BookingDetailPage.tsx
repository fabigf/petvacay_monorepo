import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { toast } from 'sonner'
import {
  cancelBooking,
  confirmPayment,
  getBooking,
  openDispute,
} from './bookingsApi'
import { BookingStatusBadge } from './BookingStatusBadge'
import { createPaymentIntent } from './paymentsApi'
import { StripePaymentForm } from './StripePaymentForm'
import type { BookingDetail } from '@/core/api/types'
import { Button, Card, Field, Input, ProcessingNotice, Skeleton } from '@/shared/components/ui'
import { useAuthStore } from '@/core/security/authStore'
import { useProblemToast } from '@/shared/hooks/useProblemToast'

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4 py-1.5 text-sm">
      <span className="text-slate-500">{label}</span>
      <span className="font-medium text-slate-800">{value}</span>
    </div>
  )
}

export function BookingDetailPage() {
  const { bookingId = '' } = useParams()
  const handleProblem = useProblemToast()
  const userId = useAuthStore((state) => state.userId)

  const [booking, setBooking] = useState<BookingDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [disputeReason, setDisputeReason] = useState('')
  // El pago dispara PagoConfirmadoEvent, que bloquea el calendario del cuidador en
  // el buscador via el proyector de Kafka: ese efecto no es instantaneo.
  const [justConfirmedPayment, setJustConfirmedPayment] = useState(false)
  // Secreto del PaymentIntent: al obtenerlo se muestra el formulario de tarjeta.
  const [clientSecret, setClientSecret] = useState<string | null>(null)

  /** Paso 1 del cobro: el backend crea el PaymentIntent en Stripe. */
  const startPayment = async () => {
    if (!booking) return
    setBusy(true)
    try {
      const intent = await createPaymentIntent(
        booking.bookingId,
        booking.finalAmountToPay,
        booking.currency,
      )
      setClientSecret(intent.clientSecret)
    } catch (error) {
      handleProblem(error)
    } finally {
      setBusy(false)
    }
  }

  /**
   * Stripe ha aceptado la tarjeta. Se marca la transición de la reserva y se
   * refresca: el estado definitivo lo fija el webhook, así que la pantalla puede
   * tardar un instante en reflejar CONFIRMADO.
   */
  const onPaymentSucceeded = async () => {
    setClientSecret(null)
    await run(() => confirmPayment(booking!.bookingId), 'Pago confirmado')
  }

  const refresh = useCallback(async () => {
    try {
      setBooking(await getBooking(bookingId))
    } catch (error) {
      handleProblem(error)
    } finally {
      setLoading(false)
    }
  }, [bookingId, handleProblem])

  useEffect(() => {
    void refresh()
  }, [refresh])

  useEffect(() => {
    if (!justConfirmedPayment) return
    const timeout = setTimeout(() => setJustConfirmedPayment(false), 8000)
    return () => clearTimeout(timeout)
  }, [justConfirmedPayment])

  const run = async (action: () => Promise<BookingDetail>, successMessage: string) => {
    const statusBeforeAction = booking?.status
    setBusy(true)
    try {
      const updated = await action()
      setBooking(updated)
      toast.success(successMessage)

      // El pago dispara PagoConfirmadoEvent; el bloqueo de calendario que ese evento
      // produce en el buscador llega por Kafka y no es instantaneo.
      if (statusBeforeAction === 'PENDIENTE_PAGO' && updated.status === 'CONFIRMADO') {
        setJustConfirmedPayment(true)
      }
    } catch (error) {
      handleProblem(error)
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <Skeleton className="h-96" />
  if (!booking) return null

  const isClient = booking.clientId === userId
  const currency = booking.currency === 'EUR' ? '€' : booking.currency

  // Una reserva terminada o congelada por una disputa ya no admite cancelación:
  // solo el equipo de administración puede resolverla (negocio.md 4.4).
  const isCancellable = !['FINALIZADO', 'CANCELADO', 'EN_DISPUTA'].includes(booking.status)

  return (
    <div className="mx-auto grid max-w-4xl gap-6 md:grid-cols-[1fr_320px]">
      <div className="flex flex-col gap-6">
        <div>
          <div className="mb-2 flex items-center gap-3">
            <h1 className="text-2xl font-bold text-slate-800">Reserva</h1>
            <BookingStatusBadge status={booking.status} />
          </div>
          <p className="text-sm text-slate-500">#{booking.bookingId}</p>
        </div>

        {justConfirmedPayment && (
          <ProcessingNotice>
            Pago confirmado. La disponibilidad del cuidador en el buscador puede tardar unos
            segundos en actualizarse mientras se procesa en segundo plano.
          </ProcessingNotice>
        )}

        <Card>
          <h2 className="mb-3 font-semibold text-slate-800">Servicio</h2>
          <Row label="Entrada" value={booking.startDate} />
          <Row label="Salida" value={booking.endDate} />
          <Row label="Noches" value={String(booking.nights)} />
          <Row label="Mascotas" value={String(booking.petIds.length)} />
          {booking.scheduledPayoutAt && (
            <Row
              label="Liberacion de fondos"
              value={new Date(booking.scheduledPayoutAt).toLocaleString('es-ES')}
            />
          )}
        </Card>

        {booking.status === 'EN_DISPUTA' && booking.disputeReason && (
          <Card className="border-red-200 bg-red-50">
            <h2 className="mb-1 font-semibold text-red-800">Disputa abierta</h2>
            <p className="text-sm text-red-700">{booking.disputeReason}</p>
            <p className="mt-2 text-xs text-red-600">
              La liberación automática de fondos está detenida hasta que el equipo de
              administración dicte una resolución.
            </p>
          </Card>
        )}

        {/* Acciones disponibles segun el estado actual de la maquina de estados */}
        <Card>
          <h2 className="mb-3 font-semibold text-slate-800">Acciones</h2>
          <div className="flex flex-wrap gap-2">
            {isClient && booking.status === 'PENDIENTE_PAGO' && !clientSecret && (
              <Button loading={busy} onClick={() => void startPayment()}>
                Pagar {booking.finalAmountToPay.toFixed(2)} {currency}
              </Button>
            )}

            {isCancellable && (
                <Button
                  variant="danger"
                  loading={busy}
                  onClick={() =>
                    void run(
                      () => cancelBooking(booking.bookingId),
                      'Reserva cancelada',
                    )
                  }
                >
                  Cancelar reserva
                </Button>
              )}
          </div>

          {clientSecret && (
            <div className="mt-4 border-t border-sand-200 pt-4">
              <h3 className="mb-3 text-sm font-semibold text-slate-700">Datos de pago</h3>
              <StripePaymentForm
                clientSecret={clientSecret}
                amountLabel={`${booking.finalAmountToPay.toFixed(2)} ${currency}`}
                onSuccess={() => void onPaymentSucceeded()}
              />
            </div>
          )}

          {isClient && (booking.status === 'CONFIRMADO' || booking.status === 'EN_PROGRESO') && (
            <form
              className="mt-4 flex flex-col gap-3 border-t border-sand-200 pt-4"
              onSubmit={(e) => {
                e.preventDefault()
                void run(
                  () => openDispute(booking.bookingId, disputeReason),
                  'Disputa registrada',
                )
              }}
            >
              <Field
                label="Abrir una incidencia"
                htmlFor="disputeReason"
                hint="Dispones de 24 horas tras el fin del servicio. Se detendra el pago al cuidador."
              >
                <Input
                  id="disputeReason"
                  required
                  value={disputeReason}
                  placeholder="Describe brevemente lo ocurrido"
                  onChange={(e) => setDisputeReason(e.target.value)}
                />
              </Field>
              <Button type="submit" variant="secondary" loading={busy}>
                Notificar incidencia
              </Button>
            </form>
          )}
        </Card>
      </div>

      {/* Desglose economico: transparencia sobre el modelo de comisión doble */}
      <Card className="h-fit">
        <h2 className="mb-3 font-semibold text-slate-800">Desglose</h2>
        <Row
          label={`Servicio (${booking.pricePerNight.toFixed(2)} ${currency} / noche)`}
          value={`${booking.basePrice.toFixed(2)} ${currency}`}
        />
        <Row
          label="Tasa de gestion (5%)"
          value={`${booking.clientServiceFee.toFixed(2)} ${currency}`}
        />
        <div className="my-2 border-t border-sand-200" />
        <Row label="Total" value={`${booking.finalAmountToPay.toFixed(2)} ${currency}`} />

        {!isClient && (
          <>
            <div className="my-2 border-t border-sand-200" />
            <Row
              label="Tu liquidacion (85%)"
              value={`${booking.sitterPayout.toFixed(2)} ${currency}`}
            />
          </>
        )}

        {booking.refundToClient !== undefined && booking.refundToClient !== null && (
          <>
            <div className="my-2 border-t border-sand-200" />
            <Row
              label="Reembolsado"
              value={`${booking.refundToClient.toFixed(2)} ${currency}`}
            />
          </>
        )}
      </Card>
    </div>
  )
}
