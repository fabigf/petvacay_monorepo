import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { createBooking } from './bookingsApi'
import { listPets } from '@/modules/pets/petsApi'
import type { Pet, ServiceType } from '@/core/api/types'
import { Button, Card, EmptyState, Field, Input, Select, Skeleton } from '@/shared/components/ui'
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
 * El importe que se muestra es una estimacion local orientativa: el precio
 * vinculante siempre lo calcula el dominio en el backend y llega en la respuesta
 * 201 (`finalAmountToPay`), nunca se envia desde el cliente.
 */
export function NewBookingPage() {
  const { sitterId = '' } = useParams()
  const navigate = useNavigate()
  const handleProblem = useProblemToast()

  const [pets, setPets] = useState<Pet[]>([])
  const [loadingPets, setLoadingPets] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

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

  const nights = useMemo(() => nightsBetween(startDate, endDate), [startDate, endDate])

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
        description="Ve a 'Mis mascotas' y completa su ficha tecnica."
      />
    )
  }

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-1 text-2xl font-bold text-slate-800">Solicitar reserva</h1>
      <p className="mb-6 text-sm text-slate-500">
        El cuidador debe aceptar la propuesta antes de que se realice ningún cobro.
      </p>

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

          <Field label="Tipo de servicio" htmlFor="serviceType" error={fieldErrors.serviceType}>
            <Select
              id="serviceType"
              value={serviceType}
              onChange={(e) => setServiceType(e.target.value as ServiceType)}
            >
              {Object.entries(serviceTypeLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </Field>

          <div className="rounded-lg bg-sand-100 p-4 text-sm text-slate-700">
            <p>
              <strong>{nights}</strong> {nights === 1 ? 'noche' : 'noches'} ·{' '}
              <strong>{selectedPets.length}</strong>{' '}
              {selectedPets.length === 1 ? 'mascota' : 'mascotas'}
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
