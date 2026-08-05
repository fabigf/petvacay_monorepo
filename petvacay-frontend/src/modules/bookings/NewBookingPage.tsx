import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { createBooking } from './bookingsApi'
import { listPets } from '@/modules/pets/petsApi'
import { getSitterProfile } from '@/modules/onboarding/onboardingApi'
import { listReviewsForSitter } from '@/modules/reviews/reviewsApi'
import type { Pet, Review, ServiceType, SitterProfile } from '@/core/api/types'
import { Button, Card, EmptyState, Field, Input, Select, Skeleton } from '@/shared/components/ui'
import { StarRating } from '@/shared/components/StarRating'
import { useProblemToast } from '@/shared/hooks/useProblemToast'

const serviceTypeLabels: Record<ServiceType, string> = {
  NIGHT_ACCOMMODATION: 'Alojamiento nocturno',
  DAY_CARE: 'Guardería de día',
  WALKING: 'Paseos por horas',
}

function todayPlus(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return date.toISOString().slice(0, 10)
}

function nightsBetween(start: string, end: string): number {
  const diff = new Date(end).getTime() - new Date(start).getTime()
  return Math.max(0, Math.round(diff / 86_400_000))
}

/**
 * Formulario de solicitud de reserva.
 *
 * El importe que se muestra es una estimación local orientativa: el precio
 * vinculante siempre lo calcula el dominio en el backend y llega en la respuesta
 * 201 (`finalAmountToPay`), nunca se envía desde el cliente.
 */
export function NewBookingPage() {
  const { sitterId = '' } = useParams()
  const navigate = useNavigate()
  const handleProblem = useProblemToast()

  const [pets, setPets] = useState<Pet[]>([])
  const [loadingPets, setLoadingPets] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const [sitter, setSitter] = useState<SitterProfile | null>(null)
  const [reviews, setReviews] = useState<Review[]>([])

  const [selectedPets, setSelectedPets] = useState<string[]>([])
  const [startDate, setStartDate] = useState(todayPlus(7))
  const [endDate, setEndDate] = useState(todayPlus(14))
  const [serviceType, setServiceType] = useState<ServiceType>('NIGHT_ACCOMMODATION')

  useEffect(() => {
    listPets()
      .then(setPets)
      .catch(handleProblem)
      .finally(() => setLoadingPets(false))
  }, [handleProblem])

  // Mejor esfuerzo: si el cuidador o sus reseñas no cargan, el formulario de
  // reserva sigue siendo utilizable — no es un requisito para poder reservar.
  useEffect(() => {
    if (!sitterId) return
    getSitterProfile(sitterId).then(setSitter).catch(() => setSitter(null))
    listReviewsForSitter(sitterId).then(setReviews).catch(() => setReviews([]))
  }, [sitterId])

  // El servicio por defecto tiene que ser uno que el cuidador ofrezca de
  // verdad: no todos ofrecen los tres (negocio.md sección 2).
  useEffect(() => {
    if (!sitter) return
    const offered = Object.keys(sitter.servicePrices) as ServiceType[]
    if (offered.length > 0 && !offered.includes(serviceType)) {
      setServiceType(offered[0])
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sitter])

  const averageRating = useMemo(() => {
    if (reviews.length === 0) return 0
    return reviews.reduce((sum, r) => sum + r.rating, 0) / reviews.length
  }, [reviews])

  const nights = useMemo(() => nightsBetween(startDate, endDate), [startDate, endDate])
  const offeredServices = (sitter ? Object.keys(sitter.servicePrices) : Object.keys(serviceTypeLabels)) as ServiceType[]
  const selectedServicePrice = sitter?.servicePrices[serviceType]

  const togglePet = (petId: string) => {
    setSelectedPets((current) =>
      current.includes(petId) ? current.filter((id) => id !== petId) : [...current, petId],
    )
  }

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setFieldErrors({})
    try {
      const booking = await createBooking({
        sitterId,
        petIds: selectedPets,
        startDate,
        endDate,
        serviceType,
      })
      toast.success('Solicitud enviada', {
        description: `El cuidador tiene 24 horas para responder. Importe: ${booking.finalAmountToPay.toFixed(2)} €`,
      })
      navigate(`/bookings/${booking.bookingId}`)
    } catch (error) {
      setFieldErrors(handleProblem(error).fieldErrors)
    } finally {
      setSubmitting(false)
    }
  }

  if (loadingPets) {
    return <Skeleton className="h-96" />
  }

  if (pets.length === 0) {
    return (
      <EmptyState
        title="Necesitas registrar una mascota antes de reservar"
        description="Ve a 'Mis mascotas' y completa su ficha técnica."
      />
    )
  }

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-1 text-2xl font-bold text-slate-800">Solicitar reserva</h1>
      <p className="mb-6 text-sm text-slate-500">
        El cuidador debe aceptar la propuesta antes de que se realice ningún cobro.
      </p>

      {sitter && (
        <Card className="mb-6">
          <div className="flex items-start gap-4">
            {sitter.photoUrl ? (
              <img
                src={sitter.photoUrl}
                alt="Cuidador"
                className="size-16 shrink-0 rounded-full object-cover"
              />
            ) : (
              <div className="size-16 shrink-0 rounded-full bg-sand-100" />
            )}
            <div className="flex-1">
              {reviews.length > 0 ? (
                <div className="flex items-center gap-1.5">
                  <StarRating value={averageRating} size="sm" />
                  <span className="text-xs text-slate-500">
                    {averageRating.toFixed(1)} ({reviews.length} {reviews.length === 1 ? 'reseña' : 'reseñas'})
                  </span>
                </div>
              ) : (
                <p className="text-xs text-slate-400">Sin reseñas todavía</p>
              )}
              {sitter.description && <p className="mt-1 text-sm text-slate-600">{sitter.description}</p>}
            </div>
          </div>

          {reviews.length > 0 && (
            <div className="mt-4 flex flex-col gap-2 border-t border-sand-200 pt-4">
              {reviews
                .filter((r) => r.authorRole !== 'SITTER')
                .slice(0, 5)
                .map((review) => (
                  <div key={review.reviewId} className="text-sm">
                    <StarRating value={review.rating} size="sm" />
                    {review.comment && <p className="mt-0.5 text-slate-600">{review.comment}</p>}
                  </div>
                ))}
            </div>
          )}
        </Card>
      )}

      <Card>
        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-sm font-medium text-slate-700">Mascotas</legend>
            {fieldErrors.petIds && (
              <p role="alert" className="text-xs font-medium text-red-600">
                {fieldErrors.petIds}
              </p>
            )}
            <div className="grid gap-2 sm:grid-cols-2">
              {pets.map((pet) => (
                <label
                  key={pet.petId}
                  className={`flex cursor-pointer items-center gap-3 rounded-lg border p-3 transition ${
                    selectedPets.includes(pet.petId)
                      ? 'border-brand-400 bg-brand-50'
                      : 'border-sand-200 bg-white hover:bg-sand-50'
                  }`}
                >
                  <input
                    type="checkbox"
                    className="size-4 accent-brand-600"
                    checked={selectedPets.includes(pet.petId)}
                    onChange={() => togglePet(pet.petId)}
                  />
                  <span className="text-sm">
                    <span className="font-medium text-slate-800">{pet.name}</span>
                    <span className="block text-xs text-slate-500">{pet.weightKg} kg</span>
                  </span>
                </label>
              ))}
            </div>
          </fieldset>

          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Entrada" htmlFor="startDate" error={fieldErrors.startDate}>
              <Input
                id="startDate"
                type="date"
                required
                value={startDate}
                aria-invalid={Boolean(fieldErrors.startDate)}
                onChange={(e) => setStartDate(e.target.value)}
              />
            </Field>
            <Field label="Salida" htmlFor="endDate" error={fieldErrors.endDate}>
              <Input
                id="endDate"
                type="date"
                required
                value={endDate}
                aria-invalid={Boolean(fieldErrors.endDate)}
                onChange={(e) => setEndDate(e.target.value)}
              />
            </Field>
          </div>

          <Field
            label="Tipo de servicio"
            htmlFor="serviceType"
            error={fieldErrors.serviceType}
            hint={
              offeredServices.length < Object.keys(serviceTypeLabels).length
                ? 'Solo se muestran los servicios que ofrece este cuidador.'
                : undefined
            }
          >
            <Select
              id="serviceType"
              value={serviceType}
              onChange={(e) => setServiceType(e.target.value as ServiceType)}
            >
              {offeredServices.map((value) => (
                <option key={value} value={value}>
                  {serviceTypeLabels[value]}
                  {sitter ? ` — ${sitter.servicePrices[value]?.toFixed(2)} €` : ''}
                </option>
              ))}
            </Select>
          </Field>

          <div className="rounded-lg bg-sand-100 p-4 text-sm text-slate-700">
            <p>
              <strong>{nights}</strong> {nights === 1 ? 'noche' : 'noches'} ·{' '}
              <strong>{selectedPets.length}</strong>{' '}
              {selectedPets.length === 1 ? 'mascota' : 'mascotas'}
              {selectedServicePrice !== undefined && (
                <>
                  {' '}
                  · <strong>{(selectedServicePrice * Math.max(nights, 1)).toFixed(2)} €</strong> estimado
                </>
              )}
            </p>
            <p className="mt-1 text-xs text-slate-500">
              Al importe del servicio se añade una tasa de gestión del 5%. El importe definitivo lo
              calcula el servidor al enviar la solicitud.
            </p>
          </div>

          <Button type="submit" loading={submitting} disabled={selectedPets.length === 0}>
            Enviar solicitud
          </Button>
        </form>
      </Card>
    </div>
  )
}
