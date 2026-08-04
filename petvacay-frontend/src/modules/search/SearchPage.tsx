import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { geocodeAddress, searchSitters } from './searchApi'
import { SitterCard } from './SitterCard'
import type { SearchFilters, Species, SitterSearchResult } from '@/core/api/types'
import { Button, Card, EmptyState, Field, Input, Select, Skeleton } from '@/shared/components/ui'
import { useProblemToast } from '@/shared/hooks/useProblemToast'

/**
 * Centro de Madrid: punto de partida SOLO si la geolocalización del navegador
 * no está disponible, el usuario la deniega, o tarda demasiado. No es más que
 * un fallback razonable, no el criterio principal — ver `locateUser` abajo.
 */
const DEFAULT_LOCATION = { lat: 40.4168, lng: -3.7038 }
const DEFAULT_LOCATION_LABEL = 'Madrid (ubicación por defecto)'

const speciesLabels: Record<Species, string> = {
  DOG: 'Perro',
  CAT: 'Gato',
  RABBIT: 'Conejo',
  BIRD: 'Ave',
  OTHER: 'Otro',
}

function todayPlus(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() + days)
  return date.toISOString().slice(0, 10)
}

export function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const handleProblem = useProblemToast()

  const [filters, setFilters] = useState<SearchFilters>({
    lat: DEFAULT_LOCATION.lat,
    lng: DEFAULT_LOCATION.lng,
    radiusKm: 25,
    startDate: searchParams.get('startDate') ?? todayPlus(7),
    endDate: searchParams.get('endDate') ?? todayPlus(14),
    petType: (searchParams.get('petType') as Species) || 'DOG',
    petCount: 1,
  })

  const [results, setResults] = useState<SitterSearchResult[]>([])
  const [loading, setLoading] = useState(true)
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null)

  const [locationLabel, setLocationLabel] = useState(DEFAULT_LOCATION_LABEL)
  const [locating, setLocating] = useState(false)
  const [addressQuery, setAddressQuery] = useState('')
  const [addressError, setAddressError] = useState<string | null>(null)

  const runSearch = useCallback(
    async (activeFilters: SearchFilters) => {
      setLoading(true)
      try {
        setResults(await searchSitters(activeFilters))
        setLastUpdatedAt(new Date())
      } catch (error) {
        handleProblem(error)
        setResults([])
      } finally {
        setLoading(false)
      }
    },
    [handleProblem],
  )

  /**
   * Pide la ubicación real al navegador. Se llama sola al montar (mejor
   * esfuerzo, en silencio) y también desde el botón "Usar mi ubicación" si el
   * usuario quiere reintentarlo tras denegar el permiso la primera vez.
   *
   * No bloquea el primer resultado: la búsqueda inicial ya se lanzó con
   * Madrid por defecto, así que si el navegador tarda o el usuario deniega el
   * permiso, el usuario nunca se queda mirando una pantalla vacía esperando.
   *
   * Deliberadamente SIN `useCallback`: necesita cerrar sobre el `filters`
   * actual en el momento del clic (fechas, radio... ya elegidos por el
   * usuario), no sobre el de cuando se monto el componente. No hace falta
   * memoizarla — no es dependencia de ningun otro hook.
   */
  const locateUser = () => {
    if (!navigator.geolocation) return

    setLocating(true)
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const next = {
          ...filters,
          lat: position.coords.latitude,
          lng: position.coords.longitude,
        }
        setFilters(next)
        setLocationLabel('Tu ubicación actual')
        setLocating(false)
        void runSearch(next)
      },
      () => {
        // Permiso denegado, sin soporte, o tiempo agotado: no es un error que
        // mostrar, es exactamente el escenario para el que existe el fallback.
        setLocating(false)
      },
      { timeout: 6000, maximumAge: 5 * 60 * 1000 },
    )
  }

  useEffect(() => {
    void runSearch(filters)
    locateUser()
    // Solo en el montaje: después la búsqueda la dispara el formulario.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    setSearchParams({
      startDate: filters.startDate ?? '',
      endDate: filters.endDate ?? '',
      petType: filters.petType ?? '',
    })
    void runSearch(filters)
  }

  const handleAddressSearch = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!addressQuery.trim()) return

    setLocating(true)
    setAddressError(null)
    try {
      const result = await geocodeAddress(addressQuery.trim())
      if (!result) {
        setAddressError('No se ha encontrado esa dirección o código postal.')
        return
      }
      const next = { ...filters, lat: result.lat, lng: result.lng }
      setFilters(next)
      setLocationLabel(result.displayName)
      void runSearch(next)
    } catch (error) {
      handleProblem(error)
    } finally {
      setLocating(false)
    }
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[300px_1fr]">
      <Card className="h-fit">
        <h2 className="mb-4 text-lg font-semibold text-slate-800">Filtros</h2>

        {/* Fuera del <form> de filtros a propósito: son dos envíos distintos
            (geocodificar una dirección no es lo mismo que aplicar los
            filtros) y HTML no permite anidar formularios. */}
        <div className="mb-4 flex flex-col gap-2 border-b border-sand-200 pb-4">
          <p className="text-xs text-slate-500">
            Buscando cerca de: <span className="font-medium text-slate-700">{locationLabel}</span>
          </p>
          <form onSubmit={(e) => void handleAddressSearch(e)} className="flex gap-2">
            <Input
              aria-label="Dirección o código postal"
              placeholder="Dirección o código postal"
              value={addressQuery}
              onChange={(e) => setAddressQuery(e.target.value)}
            />
            <Button type="submit" variant="secondary" loading={locating}>
              Buscar
            </Button>
          </form>
          {addressError && (
            <p role="alert" className="text-xs font-medium text-red-600">
              {addressError}
            </p>
          )}
          <button
            type="button"
            onClick={locateUser}
            disabled={locating}
            className="self-start text-xs font-medium text-brand-600 hover:underline disabled:opacity-50"
          >
            Usar mi ubicación actual
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <Field label="Entrada" htmlFor="startDate">
            <Input
              id="startDate"
              type="date"
              value={filters.startDate}
              onChange={(e) => setFilters({ ...filters, startDate: e.target.value })}
            />
          </Field>

          <Field label="Salida" htmlFor="endDate">
            <Input
              id="endDate"
              type="date"
              value={filters.endDate}
              onChange={(e) => setFilters({ ...filters, endDate: e.target.value })}
            />
          </Field>

          <Field label="Tipo de mascota" htmlFor="petType">
            <Select
              id="petType"
              value={filters.petType}
              onChange={(e) => setFilters({ ...filters, petType: e.target.value as Species })}
            >
              {Object.entries(speciesLabels).map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </Select>
          </Field>

          <Field
            label="Número de mascotas"
            htmlFor="petCount"
            hint="Se descartan los cuidadores sin capacidad libre esas noches."
          >
            <Input
              id="petCount"
              type="number"
              min={1}
              max={10}
              value={filters.petCount}
              onChange={(e) => setFilters({ ...filters, petCount: Number(e.target.value) })}
            />
          </Field>

          <Field label="Radio de búsqueda (km)" htmlFor="radiusKm">
            <Input
              id="radiusKm"
              type="number"
              min={1}
              max={200}
              value={filters.radiusKm}
              onChange={(e) => setFilters({ ...filters, radiusKm: Number(e.target.value) })}
            />
          </Field>

          <Button type="submit" loading={loading}>
            Buscar
          </Button>
        </form>
      </Card>

      <section>
        <div className="mb-1 flex items-baseline justify-between">
          <h1 className="text-2xl font-bold text-slate-800">Cuidadores disponibles</h1>
          {!loading && (
            <p className="text-sm text-slate-500">
              {results.length} {results.length === 1 ? 'resultado' : 'resultados'}
            </p>
          )}
        </div>

        {/* La disponibilidad se sirve desde un modelo de lectura que se actualiza de
            forma asíncrona (Fase 3): un cambio reciente puede tardar unos segundos
            en reflejarse aquí, así que se comunica la frescura del dato y se ofrece
            un refresco manual en vez de fingir que es instantáneo. */}
        <div className="mb-4 flex items-center gap-2 text-xs text-slate-400">
          {lastUpdatedAt && (
            <span>Actualizado a las {lastUpdatedAt.toLocaleTimeString('es-ES')}</span>
          )}
          <button
            type="button"
            onClick={() => void runSearch(filters)}
            disabled={loading}
            className="font-medium text-brand-600 hover:underline disabled:opacity-50"
          >
            Actualizar disponibilidad
          </button>
        </div>

        {loading ? (
          <div className="grid gap-4 sm:grid-cols-2">
            {[0, 1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-40" />
            ))}
          </div>
        ) : results.length === 0 ? (
          <EmptyState
            title="No hay cuidadores para esos filtros"
            description="Prueba a ampliar el radio de búsqueda o a cambiar las fechas."
          />
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            {results.map((sitter) => (
              <SitterCard key={sitter.sitterId} sitter={sitter} />
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
