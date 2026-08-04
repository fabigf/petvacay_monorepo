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
import { leaveReview, listReviewsForBooking, listReviewsForSitter } from '@/modules/reviews/reviewsApi'
import { getSitterProfile } from '@/modules/onboarding/onboardingApi'
import type { BookingDetail, HomeType, Review, ServiceType, SitterProfile } from '@/core/api/types'
import { Button, Card, Field, Input, ProcessingNotice, Skeleton } from '@/shared/components/ui'
import { StarRating } from '@/shared/components/StarRating'
import { useAuthStore } from '@/core/security/authStore'
import { useProblemToast } from '@/shared/hooks/useProblemToast'

const serviceTypeLabels: Record<ServiceType, string> = {
  NIGHT_ACCOMMODATION: 'Alojamiento nocturno',
  DAY_CARE: 'Guardería de día',
  WALKING: 'Paseos por horas',
}

const homeTypeLabels: Record<HomeType, string> = {
  APARTMENT: 'Apartamento',
  HOUSE_GARDEN: 'Casa con jardín',
  RURAL_HOUSE: 'Casa rural',
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4 py-1.5 text-sm">
      <span className="text-slate-500">{label}</span>
      <span className="font-medium text-slate-800">{value}</span>
    </div>
  )
}

function SectionTitle({ children }: { children: string }) {
  return <h2 className="mb-3 font-semibold text-slate-800">{children}</h2>
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
  // el buscador vía el proyector de Kafka: ese efecto no es instantáneo.
  const [justConfirmedPayment, setJustConfirmedPayment] = useState(false)
  // Secreto del PaymentIntent: al obtenerlo se muestra el formulario de tarjeta.
  const [clientSecret, setClientSecret] = useState<string | null>(null)

  // Ficha del cuidador: vive en identity-service, no en la reserva, así que es una
  // segunda llamada. Es informativa — si falla, el detalle sigue siendo usable.
  const [sitter, setSitter] = useState<SitterProfile | null>(null)
  const [sitterRating, setSitterRating] = useState<{ average: number; count: number } | null>(null)

  const [reviews, setReviews] = useState<Review[]>([])
  const [reviewRating, setReviewRating] = useState(5)
  const [reviewComment, setReviewComment] = useState('')
  const [submittingReview, setSubmittingReview] = useState(false)

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
      const detail = await getBooking(bookingId)
      setBooking(detail)
      if (detail.status === 'FINALIZADO') {
        setReviews(await listReviewsForBooking(bookingId))
      }
    } catch (error) {
      handleProblem(error)
    } finally {
      setLoading(false)
    }
  }, [bookingId, handleProblem])

  const handleLeaveReview = async (event: React.FormEvent) => {
    event.preventDefault()
    setSubmittingReview(true)
    try {
      const review = await leaveReview(bookingId, {
        rating: reviewRating,
        comment: reviewComment.trim() || undefined,
      })
      setReviews((current) => [...current, review])
      toast.success('Reseña enviada')
    } catch (error) {
      handleProblem(error)
    } finally {
      setSubmittingReview(false)
    }
  }

  useEffect(() => {
    void refresh()
  }, [refresh])

  useEffect(() => {
    const sitterId = booking?.sitterId
    if (!sitterId) return
    getSitterProfile(sitterId).then(setSitter).catch(() => setSitter(null))
    listReviewsForSitter(sitterId)
      .then((all) => {
        // Solo cuentan las del cliente hacia el cuidador: son las que forman su
        // reputación pública, igual que en el buscador.
        const fromClients = all.filter((r) => r.authorRole === 'CLIENT')
        setSitterRating(
          fromClients.length === 0
            ? { average: 0, count: 0 }
            : {
                average: fromClients.reduce((sum, r) => sum + r.rating, 0) / fromClients.length,
                count: fromClients.length,
              },
        )
      })
      .catch(() => setSitterRating(null))
  }, [booking?.sitterId])

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
      // produce en el buscador llega por Kafka y no es instantáneo.
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
    <div className="mx-auto flex max-w-5xl flex-col gap-6">
      {/* Fuera de la rejilla de dos columnas a propósito: si viviera dentro,
          la primera tarjeta de cada columna ("Tu cuidador" a la izquierda,
          "Desglose" a la derecha) quedaría desalineada entre sí. */}
      <div>
        <div className="mb-2 flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-bold text-slate-800">
            {serviceTypeLabels[booking.serviceType]}
          </h1>
          <BookingStatusBadge status={booking.status} />
        </div>
        <p className="text-sm text-slate-500">
          Id de reserva:{' '}
          <span className="font-mono text-xs text-slate-600">{booking.bookingId}</span>
        </p>
      </div>

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_340px]">
      <div className="flex min-w-0 flex-col gap-6">
        {justConfirmedPayment && (
          <ProcessingNotice>
            Pago confirmado. La disponibilidad del cuidador en el buscador puede tardar unos
            segundos en actualizarse mientras se procesa en segundo plano.
          </ProcessingNotice>
        )}

        {/* Quien reserva necesita saber con quién ha reservado; al cuidador esta
            ficha no le aporta nada porque es la suya propia. */}
        {isClient && sitter && (
          <Card>
            <SectionTitle>Tu cuidador</SectionTitle>
            <div className="flex items-start gap-4">
              {sitter.photoUrl ? (
                <img
                  src={sitter.photoUrl}
                  alt={sitter.firstName ?? 'Cuidador'}
                  className="size-16 shrink-0 rounded-full object-cover"
                />
              ) : (
                <div className="flex size-16 shrink-0 items-center justify-center rounded-full bg-sand-100 text-xl font-semibold text-slate-500">
                  {(sitter.firstName ?? '?').charAt(0).toUpperCase()}
                </div>
              )}

              <div className="min-w-0 flex-1">
                <p className="font-semibold text-slate-800">
                  {sitter.firstName ?? 'Cuidador'}
                </p>
                <p className="text-sm text-slate-500">{homeTypeLabels[sitter.homeType]}</p>

                {sitterRating && sitterRating.count > 0 ? (
                  <div className="mt-1 flex items-center gap-1.5">
                    <StarRating value={sitterRating.average} size="sm" />
                    <span className="text-xs text-slate-500">
                      {sitterRating.average.toFixed(1)} ({sitterRating.count}{' '}
                      {sitterRating.count === 1 ? 'reseña' : 'reseñas'})
                    </span>
                  </div>
                ) : (
                  <p className="mt-1 text-xs text-slate-400">Sin reseñas todavía</p>
                )}

                {sitter.description && (
                  <p className="mt-2 text-sm text-slate-600">{sitter.description}</p>
                )}
              </div>
            </div>
          </Card>
        )}

        <Card>
          <SectionTitle>Servicio</SectionTitle>
          <Row label="Tipo" value={serviceTypeLabels[booking.serviceType]} />
          <Row label="Entrada" value={booking.startDate} />
          <Row label="Salida" value={booking.endDate} />
          <Row label="Noches" value={String(booking.nights)} />
          <Row label="Mascotas" value={String(booking.petIds.length)} />
          {booking.scheduledPayoutAt && (
            <Row
              label="Liberación de fondos"
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

        {/* Acciones disponibles según el estado actual de la máquina de estados */}
        <Card>
          <SectionTitle>Acciones</SectionTitle>
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

        </Card>

        {/* Abrir una incidencia es una acción excepcional y de consecuencias serias
            (congela el pago al cuidador): va en su propio bloque, no mezclada entre
            los botones del día a día. */}
        {isClient && (booking.status === 'CONFIRMADO' || booking.status === 'EN_PROGRESO') && (
          <Card>
            <SectionTitle>¿Ha ocurrido algo?</SectionTitle>
            <form
              className="flex flex-col gap-3"
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
                hint="Dispones de 24 horas tras el fin del servicio. Se detendrá el pago al cuidador."
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
          </Card>
        )}

        {booking.status === 'FINALIZADO' && (
          <Card>
            <SectionTitle>Reseñas</SectionTitle>
            {reviews.length > 0 && (
              <div className="mb-4 flex flex-col gap-3">
                {reviews.map((review) => (
                  <div key={review.reviewId} className="rounded-lg border border-sand-200 p-3">
                    <div className="mb-1 flex items-center justify-between">
                      <span className="text-xs font-medium text-slate-500">
                        {review.authorRole === 'CLIENT' ? 'Del cliente' : 'Del cuidador'}
                      </span>
                      <StarRating value={review.rating} size="sm" />
                    </div>
                    {review.comment && <p className="text-sm text-slate-700">{review.comment}</p>}
                  </div>
                ))}
              </div>
            )}

            {reviews.some((r) => r.authorId === userId) ? (
              <p className="text-sm text-slate-500">Ya has dejado tu valoración de esta reserva.</p>
            ) : (
              <form onSubmit={(e) => void handleLeaveReview(e)} className="flex flex-col gap-3">
                <Field label="Tu valoración" htmlFor="reviewRating">
                  <StarRating value={reviewRating} onChange={setReviewRating} size="lg" />
                </Field>
                <Field label="Comentario (opcional)" htmlFor="reviewComment">
                  <Input
                    id="reviewComment"
                    placeholder={
                      isClient ? 'Cuenta cómo fue tu experiencia con el cuidador' : 'Cuenta cómo fue el servicio'
                    }
                    value={reviewComment}
                    onChange={(e) => setReviewComment(e.target.value)}
                  />
                </Field>
                <Button type="submit" loading={submittingReview}>
                  Enviar reseña
                </Button>
              </form>
            )}
          </Card>
        )}
      </div>

      {/* Desglose económico: transparencia sobre el modelo de comisión doble.
          Se queda fijo al desplazar porque es la referencia que se consulta
          mientras se leen las acciones y las reseñas de la columna izquierda. */}
      <Card className="h-fit lg:sticky lg:top-6">
        <SectionTitle>Desglose</SectionTitle>
        <Row
          label={`Servicio (${booking.pricePerNight.toFixed(2)} ${currency} / noche)`}
          value={`${booking.basePrice.toFixed(2)} ${currency}`}
        />
        <Row
          label="Tasa de gestión (5%)"
          value={`${booking.clientServiceFee.toFixed(2)} ${currency}`}
        />
        <div className="my-2 border-t border-sand-200" />
        <Row label="Total" value={`${booking.finalAmountToPay.toFixed(2)} ${currency}`} />

        {!isClient && (
          <>
            <div className="my-2 border-t border-sand-200" />
            <Row
              label="Tu liquidación (85%)"
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
    </div>
  )
}
