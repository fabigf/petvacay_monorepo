import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import {
  createSitterProfile,
  getSitterProfile,
  submitIdentityDocument,
  type CreateSitterProfileRequest,
} from './onboardingApi'
import type { HomeType, SitterProfile, Species, VerificationStatus } from '@/core/api/types'
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

const verificationPresentation: Record<
  VerificationStatus,
  { label: string; tone: 'warning' | 'success' | 'danger'; help: string }
> = {
  PENDIENTE_VERIFICACION: {
    label: 'Pendiente de verificacion',
    tone: 'warning',
    help: 'Tu perfil no aparece todavia en el buscador ni puede recibir solicitudes. Un administrador revisara tu documentacion.',
  },
  VERIFICADO: {
    label: 'Verificado',
    tone: 'success',
    help: 'Tu perfil ya esta indexado en el buscador y puedes recibir solicitudes de reserva.',
  },
  RECHAZADO: {
    label: 'Rechazado',
    tone: 'danger',
    help: 'La verificacion no ha sido superada. Revisa el motivo y vuelve a subir tu documento.',
  },
}

const emptyForm: CreateSitterProfileRequest = {
  description: '',
  homeType: 'APARTMENT',
  latitude: 40.4168,
  longitude: -3.7038,
  pricePerNight: 20,
  maxSimultaneousPets: 2,
  acceptedSpecies: ['DOG'],
  maxPetWeightKg: undefined,
}

/** Onboarding y KYC del cuidador (negocio.md seccion 4.1). */
export function SitterOnboardingPage() {
  const sitterId = useAuthStore((state) => state.userId)
  const handleProblem = useProblemToast()

  const [profile, setProfile] = useState<SitterProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState<CreateSitterProfileRequest>(emptyForm)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [documentReference, setDocumentReference] = useState('')

  const refresh = useCallback(async () => {
    if (!sitterId) return
    try {
      setProfile(await getSitterProfile(sitterId))
    } catch {
      // 404 esperado mientras el cuidador no haya creado su perfil todavia.
      setProfile(null)
    } finally {
      setLoading(false)
    }
  }, [sitterId])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const toggleSpecies = (species: Species) => {
    setForm((current) => ({
      ...current,
      acceptedSpecies: current.acceptedSpecies.includes(species)
        ? current.acceptedSpecies.filter((s) => s !== species)
        : [...current.acceptedSpecies, species],
    }))
  }

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
          <form onSubmit={handleCreate} className="flex flex-col gap-4">
            <Field label="Descripcion" htmlFor="description" error={fieldErrors.description}>
              <Input
                id="description"
                placeholder="Cuenta tu experiencia y como es tu casa"
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

            <div className="grid grid-cols-2 gap-3">
              <Field label="Latitud" htmlFor="latitude" error={fieldErrors.latitude}>
                <Input
                  id="latitude"
                  type="number"
                  step="any"
                  value={form.latitude}
                  onChange={(e) => setForm({ ...form, latitude: Number(e.target.value) })}
                />
              </Field>
              <Field label="Longitud" htmlFor="longitude" error={fieldErrors.longitude}>
                <Input
                  id="longitude"
                  type="number"
                  step="any"
                  value={form.longitude}
                  onChange={(e) => setForm({ ...form, longitude: Number(e.target.value) })}
                />
              </Field>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <Field
                label="Tarifa por noche (€)"
                htmlFor="pricePerNight"
                error={fieldErrors.pricePerNight}
              >
                <Input
                  id="pricePerNight"
                  type="number"
                  min={1}
                  step={0.5}
                  value={form.pricePerNight}
                  aria-invalid={Boolean(fieldErrors.pricePerNight)}
                  onChange={(e) => setForm({ ...form, pricePerNight: Number(e.target.value) })}
                />
              </Field>
              <Field
                label="Mascotas simultaneas"
                htmlFor="maxSimultaneousPets"
                error={fieldErrors.maxSimultaneousPets}
              >
                <Input
                  id="maxSimultaneousPets"
                  type="number"
                  min={1}
                  value={form.maxSimultaneousPets}
                  onChange={(e) =>
                    setForm({ ...form, maxSimultaneousPets: Number(e.target.value) })
                  }
                />
              </Field>
            </div>

            <Field
              label="Peso máximo admitido (kg)"
              htmlFor="maxPetWeightKg"
              hint="Dejalo vacio si no pones limite."
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

            <Button type="submit" loading={saving}>
              Crear perfil
            </Button>
          </form>
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
        <h2 className="mb-3 font-semibold text-slate-800">Tu oferta</h2>
        <div className="flex flex-col gap-1.5 text-sm">
          <p className="text-slate-600">
            Tarifa:{' '}
            <span className="font-semibold text-slate-800">
              {profile.pricePerNight.toFixed(2)} € / noche
            </span>
          </p>
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
