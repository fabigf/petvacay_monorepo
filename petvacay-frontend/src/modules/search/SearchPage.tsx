import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { searchSitters } from './searchApi'
import { SitterCard } from './SitterCard'
import type { SearchFilters, Species, SitterSearchResult } from '@/core/api/types'
import { Button, Card, EmptyState, Field, Input, Select, Skeleton } from '@/shared/components/ui'
import { useProblemToast } from '@/shared/hooks/useProblemToast'

/** Centro de Madrid: punto de partida hasta que exista geolocalizacion real. */
const DEFAULT_LOCATION = { lat: 40.4168, lng: -3.7038 }

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

  useEffect(() => {
    void runSearch(filters)
    // Solo en el montaje: despues la busqueda la dispara el formulario.
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

  return (
    <div className="grid gap-6 lg:grid-cols-[300px_1fr]">
      <Card className="h-fit">
        <h2 className="mb-4 text-lg font-semibold text-slate-800">Filtros</h2>
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
            forma asincrona (Fase 3): un cambio reciente puede tardar unos segundos
            en reflejarse aqui, asi que se comunica la frescura del dato y se ofrece
            un refresco manual en vez de fingir que es instantaneo. */}
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
