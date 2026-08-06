import type { BookingPet, Sociability, Species } from '@/core/api/types'
import { Badge, Card, Skeleton } from '@/shared/components/ui'

const speciesLabels: Record<Species, string> = {
  DOG: 'Perro',
  CAT: 'Gato',
  RABBIT: 'Conejo',
  BIRD: 'Ave',
  OTHER: 'Otra especie',
}

const sociabilityLabels: Record<Sociability, string> = {
  SOCIABLE: 'Sociable',
  SELECTIVE: 'Selectiva con otros animales',
  NEEDS_TO_BE_ALONE: 'Necesita estar sola',
}

/**
 * Fichas de las mascotas de la reserva.
 *
 * <p>Para el cuidador es la información con la que decide si acepta y cómo se
 * prepara: negocio.md 2.1 hace la ficha obligatoria justo porque «el cuidador
 * toma decisiones a partir de estos datos». Hasta ahora la reserva solo llevaba
 * los identificadores, así que aceptaba a ciegas.
 */
export function BookingPetsCard({ pets }: { pets: BookingPet[] | null }) {
  return (
    <Card>
      <h2 className="mb-3 font-semibold text-slate-800">
        {pets && pets.length === 1 ? 'La mascota' : 'Las mascotas'}
      </h2>

      {pets === null && <Skeleton className="h-24 w-full" />}
      {pets?.length === 0 && (
        <p className="text-sm text-slate-500">No se han podido cargar las fichas.</p>
      )}

      <div className="flex flex-col gap-3">
        {pets?.map((pet) => (
          <div key={pet.petId} className="rounded-lg border border-sand-200 p-3">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-semibold text-slate-800">{pet.name}</span>
              <Badge>{speciesLabels[pet.species]}</Badge>
              {/* Se destaca porque cambia lo que el cuidador tiene que hacer a
                  diario, no es un dato más de la ficha. */}
              {pet.specialMedicalNeeds && <Badge tone="warning">Necesidades especiales</Badge>}
            </div>

            <p className="mt-1 text-sm text-slate-600">
              {pet.breed ? `${pet.breed} · ` : ''}
              {pet.ageYears} {pet.ageYears === 1 ? 'año' : 'años'} · {pet.weightKg} kg ·{' '}
              {sociabilityLabels[pet.sociability]}
            </p>

            {pet.medicalNeeds && (
              <p className="mt-1.5 text-sm text-slate-700">
                <span className="font-medium">Salud:</span> {pet.medicalNeeds}
              </p>
            )}
            {pet.dietaryRestrictions && (
              <p className="mt-0.5 text-sm text-slate-700">
                <span className="font-medium">Dieta:</span> {pet.dietaryRestrictions}
              </p>
            )}
          </div>
        ))}
      </div>
    </Card>
  )
}
