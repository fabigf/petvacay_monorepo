import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import {
  createSitterProfile,
  getMySitterProfile,
  submitIdentityDocument,
  updateSitterProfile,
  type CreateSitterProfileRequest,
} from './onboardingApi'
import { AddressPicker } from './AddressPicker'
import type {
  HomeType,
  ServicePrices,
  ServiceType,
  SitterProfile,
  Species,
  VerificationStatus,
} from '@/core/api/types'
import { Badge, Button, Card, Field, Input, Select, Skeleton } from '@/shared/components/ui'
import { useAuthStore } from '@/core/security/authStore'
import { useProblemToast } from '@/shared/hooks/useProblemToast'

const homeTypeLabels: Record<HomeType, string> = {
  APARTMENT: 'Apartamento',
  HOUSE_GARDEN: 'Casa con jardín',
  RURAL_HOUSE: 'Casa rural',
}

const speciesLabels: Record<Species, string> = {
  DOG: 'Perros',
  CAT: 'Gatos',
  RABBIT: 'Conejos',
  BIRD: 'Aves',
  OTHER: 'Otros',
}

const serviceTypeLabels: Record<ServiceType, string> = {
  NIGHT_ACCOMMODATION: 'Alojamiento nocturno',
  DAY_CARE: 'Guardería de día',
  WALKING: 'Paseos por horas',
}

const verificationPresentation: Record<
  VerificationStatus,
  { label: string; tone: 'warning' | 'success' | 'danger'; help: string }
> = {
  PENDIENTE_VERIFICACION: {
    label: 'Pendiente de verificación',
    tone: 'warning',
    help: 'Tu perfil no aparece todavía en el buscador ni puede recibir solicitudes. Un administrador revisará tu documentación.',
  },
  VERIFICADO: {
    label: 'Verificado',
    tone: 'success',
    help: 'Tu perfil ya está indexado en el buscador y puedes recibir solicitudes de reserva.',
  },
  RECHAZADO: {
    label: 'Rechazado',
    tone: 'danger',
    help: 'La verificación no ha sido superada. Revisa el motivo y vuelve a subir tu documento.',
  },
}

const emptyForm: CreateSitterProfileRequest = {
  description: '',
  homeType: 'APARTMENT',
  // Sin dirección todavía: el centro de Madrid es solo el punto de partida del
  // mapa hasta que el cuidador busca la suya.
  address: '',
  streetNumber: '',
  floorDoor: '',
  latitude: 40.4168,
  longitude: -3.7038,
  servicePrices: { NIGHT_ACCOMMODATION: 20 },
  maxSimultaneousPets: 2,
  acceptedSpecies: ['DOG'],
  maxPetWeightKg: undefined,
  photoUrl: '',
}

function profileToForm(profile: SitterProfile): CreateSitterProfileRequest {
  return {
    description: profile.description ?? '',
    homeType: profile.homeType,
    address: profile.address ?? '',
    streetNumber: profile.streetNumber ?? '',
    floorDoor: profile.floorDoor ?? '',
    latitude: profile.latitude,
    longitude: profile.longitude,
    servicePrices: profile.servicePrices,
    maxSimultaneousPets: profile.maxSimultaneousPets,
    acceptedSpecies: profile.acceptedSpecies,
    maxPetWeightKg: profile.maxPetWeightKg,
    photoUrl: profile.photoUrl ?? '',
  }
}

/**
 * Formulario de perfil de cuidador, compartido entre "crear" y "editar": los
 * campos son exactamente los mismos, solo cambia qué endpoint dispara el envío
 * (eso lo decide quien lo usa, via `onSubmit`).
 */
function SitterProfileForm({
  form,
  setForm,
  fieldErrors,
  saving,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  form: CreateSitterProfileRequest
  setForm: (form: CreateSitterProfileRequest) => void
  fieldErrors: Record<string, string>
  saving: boolean
  submitLabel: string
  onSubmit: (event: React.FormEvent) => void
  onCancel?: () => void
}) {
  const toggleSpecies = (species: Species) => {
    setForm({
      ...form,
      acceptedSpecies: form.acceptedSpecies.includes(species)
        ? form.acceptedSpecies.filter((s) => s !== species)
        : [...form.acceptedSpecies, species],
    })
  }

  const toggleService = (type: ServiceType, defaultPrice: number) => {
    const next: ServicePrices = { ...form.servicePrices }
    if (type in next) {
      delete next[type]
    } else {
      next[type] = defaultPrice
    }
    setForm({ ...form, servicePrices: next })
  }

  const setServicePrice = (type: ServiceType, price: number) => {
    setForm({ ...form, servicePrices: { ...form.servicePrices, [type]: price } })
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4">
      <Field label="Foto de perfil (URL)" htmlFor="photoUrl" hint="Se muestra en tus tarjetas del buscador.">
        <Input
          id="photoUrl"
          type="url"
          placeholder="https://..."
          value={form.photoUrl ?? ''}
          onChange={(e) => setForm({ ...form, photoUrl: e.target.value })}
        />
      </Field>
      {form.photoUrl && (
        <img
          src={form.photoUrl}
          alt="Vista previa"
          className="size-16 rounded-full border border-sand-200 object-cover"
        />
      )}

      <Field label="Descripción" htmlFor="description" error={fieldErrors.description}>
        <Input
          id="description"
          placeholder="Cuenta tu experiencia y cómo es tu casa"
          value={form.description}
          onChange={(e) => setForm({ ...form, description: e.target.value })}
        />
      </Field>

      <Field label="Tipo de vivienda" htmlFor="homeType" error={fieldErrors.homeType}>
        <Select
          id="homeType"
          value={form.homeType}
          onChange={(e) => setForm({ ...form, homeType: e.target.value as HomeType })}
        >
          {Object.entries(homeTypeLabels).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
      </Field>

      <AddressPicker
        address={form.address}
        streetNumber={form.streetNumber}
        floorDoor={form.floorDoor}
        latitude={form.latitude}
        longitude={form.longitude}
        requiresFloor={form.homeType === 'APARTMENT'}
        error={fieldErrors.address}
        onChange={(location) => setForm({ ...form, ...location })}
      />

      <fieldset>
        <legend className="mb-2 text-sm font-medium text-slate-700">
          Servicios que ofreces y su tarifa
        </legend>
        {fieldErrors.servicePrices && (
          <p role="alert" className="mb-2 text-xs font-medium text-red-600">
            {fieldErrors.servicePrices}
          </p>
        )}
        <div className="flex flex-col gap-2">
          {(Object.entries(serviceTypeLabels) as [ServiceType, string][]).map(([type, label]) => {
            const offered = type in form.servicePrices
            return (
              <label
                key={type}
                className={`flex items-center gap-3 rounded-lg border p-3 transition ${
                  offered ? 'border-brand-400 bg-brand-50' : 'border-sand-200 bg-white hover:bg-sand-50'
                }`}
              >
                <input
                  type="checkbox"
                  className="size-4 accent-brand-600"
                  checked={offered}
                  onChange={() => toggleService(type, 20)}
                />
                <span className="flex-1 text-sm font-medium text-slate-700">{label}</span>
                {offered && (
                  <Input
                    type="number"
                    min={1}
                    step={0.5}
                    aria-label={`Tarifa de ${label}`}
                    className="w-28"
                    value={form.servicePrices[type] ?? ''}
                    onChange={(e) => setServicePrice(type, Number(e.target.value))}
                  />
                )}
                {offered && <span className="text-sm text-slate-500">€</span>}
              </label>
            )
          })}
        </div>
      </fieldset>

      <Field
        label="Mascotas simultáneas"
        htmlFor="maxSimultaneousPets"
        error={fieldErrors.maxSimultaneousPets}
      >
        <Input
          id="maxSimultaneousPets"
          type="number"
          min={1}
          value={form.maxSimultaneousPets}
          onChange={(e) => setForm({ ...form, maxSimultaneousPets: Number(e.target.value) })}
        />
      </Field>

      <Field
        label="Peso máximo admitido (kg)"
        htmlFor="maxPetWeightKg"
        hint="Déjalo vacío si no pones límite."
        error={fieldErrors.maxPetWeightKg}
      >
        <Input
          id="maxPetWeightKg"
          type="number"
          min={1}
          value={form.maxPetWeightKg ?? ''}
          onChange={(e) =>
            setForm({
              ...form,
              maxPetWeightKg: e.target.value ? Number(e.target.value) : undefined,
            })
          }
        />
      </Field>

      <fieldset>
        <legend className="mb-2 text-sm font-medium text-slate-700">Animales que aceptas</legend>
        <div className="flex flex-wrap gap-2">
          {Object.entries(speciesLabels).map(([value, label]) => {
            const species = value as Species
            const selected = form.acceptedSpecies.includes(species)
            return (
              <button
                key={value}
                type="button"
                onClick={() => toggleSpecies(species)}
                className={`rounded-full border px-3 py-1.5 text-sm font-medium transition ${
                  selected
                    ? 'border-brand-400 bg-brand-50 text-brand-700'
                    : 'border-sand-200 bg-white text-slate-600 hover:bg-sand-50'
                }`}
              >
                {label}
              </button>
            )
          })}
        </div>
      </fieldset>

      <div className="flex gap-2">
        <Button type="submit" loading={saving}>
          {submitLabel}
        </Button>
        {onCancel && (
          <Button type="button" variant="secondary" onClick={onCancel}>
            Cancelar
          </Button>
        )}
      </div>
    </form>
  )
}

/** Onboarding y KYC del cuidador (negocio.md sección 4.1). */
export function SitterOnboardingPage() {
  const sitterId = useAuthStore((state) => state.userId)
  const handleProblem = useProblemToast()

  const [profile, setProfile] = useState<SitterProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState<CreateSitterProfileRequest>(emptyForm)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [documentReference, setDocumentReference] = useState('')

  const refresh = useCallback(async () => {
    if (!sitterId) return
    try {
      setProfile(await getMySitterProfile())
    } catch {
      // 404 esperado mientras el cuidador no haya creado su perfil todavía.
      setProfile(null)
    } finally {
      setLoading(false)
    }
  }, [sitterId])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const handleCreate = async (event: React.FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setFieldErrors({})
    try {
      setProfile(await createSitterProfile(form))
      toast.success('Perfil creado. Ahora sube tu documento de identidad.')
    } catch (error) {
      setFieldErrors(handleProblem(error).fieldErrors)
    } finally {
      setSaving(false)
    }
  }

  const handleUpdate = async (event: React.FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setFieldErrors({})
    try {
      setProfile(await updateSitterProfile(form))
      setEditing(false)
      toast.success('Perfil actualizado')
    } catch (error) {
      setFieldErrors(handleProblem(error).fieldErrors)
    } finally {
      setSaving(false)
    }
  }

  const startEditing = () => {
    if (!profile) return
    setForm(profileToForm(profile))
    setFieldErrors({})
    setEditing(true)
  }

  const handleSubmitDocument = async (event: React.FormEvent) => {
    event.preventDefault()
    setSaving(true)
    try {
      setProfile(await submitIdentityDocument(documentReference))
      toast.success('Documento enviado', {
        description: 'Un administrador revisará tu identidad en las próximas horas.',
      })
    } catch (error) {
      handleProblem(error)
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <Skeleton className="h-96" />

  if (!profile) {
    return (
      <div className="mx-auto max-w-2xl">
        <h1 className="mb-1 text-2xl font-bold text-slate-800">Crea tu perfil de cuidador</h1>
        <p className="mb-6 text-sm text-slate-500">
          Define tus tarifas, tu capacidad y qué animales aceptas. Podrás cambiarlo cuando quieras.
        </p>

        <Card>
          <SitterProfileForm
            form={form}
            setForm={setForm}
            fieldErrors={fieldErrors}
            saving={saving}
            submitLabel="Crear perfil"
            onSubmit={handleCreate}
          />
        </Card>
      </div>
    )
  }

  if (editing) {
    return (
      <div className="mx-auto max-w-2xl">
        <h1 className="mb-6 text-2xl font-bold text-slate-800">Editar perfil de cuidador</h1>
        <Card>
          <SitterProfileForm
            form={form}
            setForm={setForm}
            fieldErrors={fieldErrors}
            saving={saving}
            submitLabel="Guardar cambios"
            onSubmit={handleUpdate}
            onCancel={() => setEditing(false)}
          />
        </Card>
      </div>
    )
  }

  const status = verificationPresentation[profile.verificationStatus]

  return (
    <div className="mx-auto grid max-w-4xl gap-6 md:grid-cols-[1fr_320px]">
      <div className="flex flex-col gap-6">
        <div>
          <div className="mb-2 flex items-center gap-3">
            <h1 className="text-2xl font-bold text-slate-800">Panel de cuidador</h1>
            <Badge tone={status.tone}>{status.label}</Badge>
          </div>
          <p className="text-sm text-slate-600">{status.help}</p>
          {profile.rejectionReason && (
            <p className="mt-2 text-sm text-red-600">Motivo: {profile.rejectionReason}</p>
          )}
        </div>

        {profile.verificationStatus !== 'VERIFICADO' && (
          <Card>
            <h2 className="mb-1 font-semibold text-slate-800">Verificación de identidad</h2>
            <p className="mb-4 text-sm text-slate-500">
              Sube una imagen nítida de tu DNI, NIE o Pasaporte a tu almacenamiento y pega aquí su
              referencia. El fichero no pasa por esta API.
            </p>
            <form onSubmit={handleSubmitDocument} className="flex flex-col gap-3">
              <Field label="Referencia del documento" htmlFor="documentReference">
                <Input
                  id="documentReference"
                  required
                  placeholder="s3://petvacay-kyc/mi-dni.jpg"
                  value={documentReference}
                  onChange={(e) => setDocumentReference(e.target.value)}
                />
              </Field>
              <Button type="submit" loading={saving}>
                Enviar documento
              </Button>
            </form>
          </Card>
        )}
      </div>

      <Card className="h-fit">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="font-semibold text-slate-800">Tu oferta</h2>
          <Button variant="secondary" onClick={startEditing}>
            Editar
          </Button>
        </div>

        {profile.photoUrl && (
          <img
            src={profile.photoUrl}
            alt={undefined}
            className="mb-3 size-16 rounded-full border border-sand-200 object-cover"
          />
        )}

        <div className="flex flex-col gap-1.5 text-sm">
          <div className="text-slate-600">
            Servicios:
            <ul className="mt-1 flex flex-col gap-0.5">
              {(Object.entries(profile.servicePrices) as [ServiceType, number][]).map(([type, price]) => (
                <li key={type} className="flex justify-between">
                  <span>{serviceTypeLabels[type]}</span>
                  <span className="font-semibold text-slate-800">{price.toFixed(2)} €</span>
                </li>
              ))}
            </ul>
          </div>
          <p className="text-slate-600">
            Capacidad:{' '}
            <span className="font-semibold text-slate-800">
              {profile.maxSimultaneousPets} mascotas simultáneas
            </span>
          </p>
          <p className="text-slate-600">Vivienda: {homeTypeLabels[profile.homeType]}</p>
          <p className="text-slate-600">
            Acepta: {profile.acceptedSpecies.map((s) => speciesLabels[s]).join(', ')}
          </p>
          {profile.maxPetWeightKg && (
            <p className="text-slate-600">Peso máximo: {profile.maxPetWeightKg} kg</p>
          )}
        </div>

        <div className="mt-4 border-t border-sand-200 pt-4 text-xs text-slate-500">
          De cada reserva PetVacay retiene un 15% en concepto de comisión. Recibirás el 85% de tu
          tarifa 24 horas después de finalizar el servicio.
        </div>
      </Card>
    </div>
  )
}
