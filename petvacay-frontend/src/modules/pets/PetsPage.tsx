import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { addPet, listPets, removePet, type AddPetRequest } from './petsApi'
import type { Pet, Sociability, Species } from '@/core/api/types'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Field,
  Input,
  Select,
  Skeleton,
} from '@/shared/components/ui'
import { useProblemToast } from '@/shared/hooks/useProblemToast'

const speciesLabels: Record<Species, string> = {
  DOG: 'Perro',
  CAT: 'Gato',
  RABBIT: 'Conejo',
  BIRD: 'Ave',
  OTHER: 'Otro',
}

const sociabilityLabels: Record<Sociability, string> = {
  SOCIABLE: 'Sociable',
  SELECTIVE: 'Selectivo',
  NEEDS_TO_BE_ALONE: 'Necesita estar solo',
}

const emptyForm: AddPetRequest = {
  name: '',
  species: 'DOG',
  breed: '',
  ageYears: 1,
  weightKg: 5,
  medicalNeeds: '',
  dietaryRestrictions: '',
  sociability: 'SOCIABLE',
}

/**
 * Ficha técnica de las mascotas. Es obligatoria antes de reservar: el cuidador
 * decide en función de estos datos (negocio.md sección 2.1).
 */
export function PetsPage() {
  const handleProblem = useProblemToast()
  const [pets, setPets] = useState<Pet[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState<AddPetRequest>(emptyForm)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const refresh = async () => {
    setLoading(true)
    try {
      setPets(await listPets())
    } catch (error) {
      handleProblem(error)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void refresh()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setFieldErrors({})
    try {
      await addPet(form)
      toast.success(`${form.name} añadida a tu perfil`)
      setForm(emptyForm)
      await refresh()
    } catch (error) {
      setFieldErrors(handleProblem(error).fieldErrors)
    } finally {
      setSaving(false)
    }
  }

  const handleRemove = async (pet: Pet) => {
    try {
      await removePet(pet.petId)
      toast.success(`${pet.name} eliminada`)
      await refresh()
    } catch (error) {
      handleProblem(error)
    }
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
      <section>
        <h1 className="mb-4 text-2xl font-bold text-slate-800">Mis mascotas</h1>

        {loading ? (
          <div className="grid gap-4 sm:grid-cols-2">
            <Skeleton className="h-36" />
            <Skeleton className="h-36" />
          </div>
        ) : pets.length === 0 ? (
          <EmptyState
            title="Todavía no has registrado ninguna mascota"
            description="Necesitas al menos una ficha completa para poder reservar."
          />
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            {pets.map((pet) => (
              <Card key={pet.petId} className="flex flex-col gap-2">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <h3 className="font-semibold text-slate-800">{pet.name}</h3>
                    <p className="text-sm text-slate-500">
                      {speciesLabels[pet.species]}
                      {pet.breed ? ` · ${pet.breed}` : ''} · {pet.ageYears} años · {pet.weightKg} kg
                    </p>
                  </div>
                  {pet.specialMedicalNeeds && (
                    <Badge tone="warning" className="shrink-0">
                      Cuidados médicos
                    </Badge>
                  )}
                </div>

                <p className="text-sm text-slate-600">
                  Sociabilidad: {sociabilityLabels[pet.sociability]}
                </p>
                {pet.medicalNeeds && (
                  <p className="text-sm text-slate-600">Medicación: {pet.medicalNeeds}</p>
                )}
                {pet.dietaryRestrictions && (
                  <p className="text-sm text-slate-600">Dieta: {pet.dietaryRestrictions}</p>
                )}

                <div className="mt-1">
                  <Button variant="ghost" onClick={() => void handleRemove(pet)}>
                    Eliminar
                  </Button>
                </div>
              </Card>
            ))}
          </div>
        )}
      </section>

      <Card className="h-fit">
        <h2 className="mb-4 text-lg font-semibold text-slate-800">Añadir mascota</h2>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <Field label="Nombre" htmlFor="name" error={fieldErrors.name}>
            <Input
              id="name"
              required
              value={form.name}
              aria-invalid={Boolean(fieldErrors.name)}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
            />
          </Field>

          <Field label="Especie" htmlFor="species" error={fieldErrors.species}>
            <Select
              id="species"
              value={form.species}
              onChange={(e) => setForm({ ...form, species: e.target.value as Species })}
            >
              {Object.entries(speciesLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </Field>

          <Field label="Raza" htmlFor="breed" error={fieldErrors.breed}>
            <Input
              id="breed"
              value={form.breed}
              onChange={(e) => setForm({ ...form, breed: e.target.value })}
            />
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field label="Edad (años)" htmlFor="ageYears" error={fieldErrors.ageYears}>
              <Input
                id="ageYears"
                type="number"
                min={0}
                value={form.ageYears}
                onChange={(e) => setForm({ ...form, ageYears: Number(e.target.value) })}
              />
            </Field>
            <Field label="Peso (kg)" htmlFor="weightKg" error={fieldErrors.weightKg}>
              <Input
                id="weightKg"
                type="number"
                min={0.1}
                step={0.1}
                value={form.weightKg}
                onChange={(e) => setForm({ ...form, weightKg: Number(e.target.value) })}
              />
            </Field>
          </div>

          <Field
            label="Necesidades médicas"
            htmlFor="medicalNeeds"
            hint="Medicación, alergias o tratamientos en curso."
            error={fieldErrors.medicalNeeds}
          >
            <Input
              id="medicalNeeds"
              value={form.medicalNeeds}
              onChange={(e) => setForm({ ...form, medicalNeeds: e.target.value })}
            />
          </Field>

          <Field
            label="Restricciones alimentarias"
            htmlFor="dietaryRestrictions"
            error={fieldErrors.dietaryRestrictions}
          >
            <Input
              id="dietaryRestrictions"
              value={form.dietaryRestrictions}
              onChange={(e) => setForm({ ...form, dietaryRestrictions: e.target.value })}
            />
          </Field>

          <Field label="Sociabilidad" htmlFor="sociability" error={fieldErrors.sociability}>
            <Select
              id="sociability"
              value={form.sociability}
              onChange={(e) => setForm({ ...form, sociability: e.target.value as Sociability })}
            >
              {Object.entries(sociabilityLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </Field>

          <Button type="submit" loading={saving}>
            Guardar mascota
          </Button>
        </form>
      </Card>
    </div>
  )
}
