import { Suspense, lazy, useEffect, useRef, useState } from 'react'
import { geocodeAddress, suggestAddresses } from '@/modules/search/searchApi'
import type { GeocodeResult } from '@/core/api/types'
import { Field, Input, Skeleton } from '@/shared/components/ui'

/**
 * Carga perezosa: Leaflet (~150 KB) solo hace falta cuando alguien pulsa
 * "Ajustar manualmente", y quien nunca llega a editar su perfil de cuidador —
 * la inmensa mayoría de las visitas a la app — no debería pagar ese peso en su
 * primera carga.
 */
const LocationPinMap = lazy(() =>
  import('./LocationPinMap').then((module) => ({ default: module.LocationPinMap })),
)

/**
 * Espacios repetidos o al borde no aportan nada a la búsqueda y solo hacen que
 * un texto por lo demás correcto no encuentre nada — la causa más frecuente de
 * "depende de cómo se escriba no lo pilla". Nominatim además tiende a fallar
 * más si el texto trae puntuación suelta al final (una coma colgando, etc.).
 */
function normalizeQuery(text: string): string {
  return text.trim().replace(/\s+/g, ' ').replace(/,\s*$/, '')
}

/**
 * Espera tras la última pulsación antes de consultar, en milisegundos.
 *
 * <p>Nominatim limita a una petición por segundo EN TOTAL (no por usuario) y su
 * política de uso prohíbe expresamente las consultas automatizadas en bucle:
 * lanzar una por tecla nos dejaría fuera de servicio a todos. Medio segundo es
 * suficiente para que solo se consulte cuando alguien deja de escribir.
 */
const DEBOUNCE_MS = 500

/** Con menos caracteres todo se parece a todo; el backend aplica el mismo mínimo. */
const MIN_QUERY_LENGTH = 3

/** Domicilio completo del cuidador: lo que se busca y lo que se escribe a mano. */
export interface AddressValue {
  address: string
  streetNumber: string
  floorDoor: string
  latitude: number
  longitude: number
}

/**
 * Selector del domicilio del cuidador con autocompletado de OpenStreetMap.
 *
 * <p>Sustituye a los dos campos de latitud y longitud que había antes, que
 * obligaban a buscar las coordenadas por tu cuenta en otra herramienta y no se
 * podían verificar de un vistazo: nadie sabe si `40.4168, -3.7038` es su casa.
 *
 * <p>Se ofrecen varias sugerencias en lugar de resolver directamente la primera
 * coincidencia porque aquí la dirección no es un filtro que se pueda afinar
 * luego: es dónde va a vivir un animal, y "Calle Mayor" existe en decenas de
 * pueblos. Quien elige tiene que ver cuál está escogiendo.
 */
export function AddressPicker({
  address,
  streetNumber,
  floorDoor,
  latitude,
  longitude,
  requiresFloor,
  onChange,
  error,
}: {
  address: string
  streetNumber: string
  floorDoor: string
  latitude: number
  longitude: number
  /** En apartamento hay que saber a qué timbre llamar; en casa o finca, no. */
  requiresFloor: boolean
  onChange: (location: Partial<AddressValue>) => void
  error?: string
}) {
  const [draft, setDraft] = useState(address)
  const [suggestions, setSuggestions] = useState<GeocodeResult[]>([])
  const [searching, setSearching] = useState(false)
  const [open, setOpen] = useState(false)
  const [showMap, setShowMap] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  // Se considera "situado" cuando el texto escrito coincide con la dirección ya
  // confirmada: si se edita después, deja de estarlo hasta elegir otra.
  const located = Boolean(address) && draft === address

  useEffect(() => {
    if (draft.trim().length < MIN_QUERY_LENGTH || draft === address) {
      setSuggestions([])
      return
    }

    // `stale` corta la respuesta de una consulta que ya no interesa: al seguir
    // escribiendo se lanza otra, y sin esto la lenta podría llegar después de la
    // rápida y pisar sugerencias más recientes con otras más antiguas.
    let stale = false
    setSearching(true)

    const timer = window.setTimeout(() => {
      suggestAddresses(normalizeQuery(draft))
        .then((results) => {
          if (stale) return
          setSuggestions(results)
          setOpen(results.length > 0)
        })
        .finally(() => {
          if (!stale) setSearching(false)
        })
    }, DEBOUNCE_MS)

    return () => {
      stale = true
      window.clearTimeout(timer)
    }
  }, [draft, address])

  /**
   * `null` = todavía no se sabe; `true` = las coordenadas apuntan al portal
   * exacto; `false` = OpenStreetMap no tiene mapeado ese número y se mantienen
   * las coordenadas de la calle.
   */
  const [numberMatched, setNumberMatched] = useState<boolean | null>(null)

  /**
   * OpenStreetMap SÍ puede geocodificar por número de portal — tiene un campo
   * `housenumber` — pero solo si ese número está mapeado para esa calle, y la
   * cobertura en España es desigual (bien en muchos cascos urbanos, con huecos
   * en urbanizaciones o calles nuevas). Por eso, al fijar la calle, esto vuelve
   * a consultar añadiendo el número: si Nominatim lo encuentra, afina las
   * coordenadas al portal; si no, se quedan las de la calle — mejor una
   * ubicación aproximada que ninguna, y nunca bloquea el formulario.
   */
  useEffect(() => {
    if (!located || streetNumber.trim().length === 0) {
      setNumberMatched(null)
      return
    }

    let stale = false
    // Solo el nombre de la vía, sin barrio/ciudad/país: Nominatim interpreta
    // mejor "10 Calle Mayor" que un número intercalado en la dirección
    // completa que ya devolvió.
    const streetName = normalizeQuery(address.split(',')[0])
    const trimmedNumber = streetNumber.trim()

    const timer = window.setTimeout(() => {
      geocodeAddress(`${trimmedNumber} ${streetName}`)
        .then((result) => {
          if (stale) return
          // Comparación exacta contra el campo estructurado que devuelve el
          // backend (Nominatim con `addressdetails=1`), no una búsqueda de
          // texto sobre `displayName`: esa era la versión anterior de esta
          // comprobación y podía dar falsos positivos o negativos según cómo
          // Nominatim redactara la frase.
          if (result?.houseNumber === trimmedNumber) {
            setNumberMatched(true)
            onChange({ latitude: result.lat, longitude: result.lng })
          } else {
            setNumberMatched(false)
          }
        })
        .catch(() => {
          if (!stale) setNumberMatched(false)
        })
    }, DEBOUNCE_MS)

    return () => {
      stale = true
      window.clearTimeout(timer)
    }
    // Solo cuando cambian el número o la calle elegida: `onChange` no debe
    // disparar otra ronda, o el ajuste de coordenadas se re-consultaría a sí
    // mismo en bucle.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [streetNumber, located, address])

  // Cerrar al pulsar fuera: es un desplegable, no un diálogo.
  useEffect(() => {
    if (!open) return
    const onClickOutside = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [open])

  const choose = (suggestion: GeocodeResult) => {
    onChange({
      address: suggestion.displayName,
      latitude: suggestion.lat,
      longitude: suggestion.lng,
    })
    setDraft(suggestion.displayName)
    setSuggestions([])
    setOpen(false)
  }

  /**
   * Vía de escape cuando la búsqueda no encuentra nada, sea cual sea el
   * motivo (número no mapeado, urbanización sin nombre oficial, una errata que
   * no se detecta). En vez de dejar al cuidador atascado reformulando el texto,
   * se da por buena la dirección tal y como la ha escrito y se abre el mapa
   * para que sitúe el punto a mano — la dirección en sí sigue siendo la que
   * escribió, solo las coordenadas quedan por fijar.
   */
  const useTypedAddressManually = () => {
    const confirmed = normalizeQuery(draft)
    setDraft(confirmed)
    onChange({ address: confirmed })
    setShowMap(true)
  }

  return (
    <div ref={containerRef} className="flex flex-col gap-2">
      <Field
        label="Dirección"
        htmlFor="address"
        error={error}
        hint="Empieza a escribir y elige tu dirección. Los clientes solo ven la distancia, nunca tu dirección exacta."
      >
        {/* La lista cuelga del propio campo (`top-full`) en vez de a una altura
            fija: así no se descoloca cuando el campo muestra un error encima. */}
        <div className="relative">
          <Input
            id="address"
            value={draft}
            autoComplete="off"
            placeholder="Calle Mayor 1, Madrid"
            role="combobox"
            aria-expanded={open}
            aria-autocomplete="list"
            onChange={(event) => setDraft(event.target.value)}
            onFocus={() => setOpen(suggestions.length > 0)}
            onKeyDown={(event) => {
              // Enter no debe enviar el formulario entero, que aún está a medias:
              // el resto de campos vienen después de la dirección.
              if (event.key === 'Enter') event.preventDefault()
              if (event.key === 'Escape') setOpen(false)
            }}
          />

          {open && suggestions.length > 0 && (
            <ul
              role="listbox"
              className="absolute inset-x-0 top-full z-20 mt-1 max-h-64 overflow-y-auto rounded-lg border border-sand-200 bg-white shadow-lg"
            >
              {suggestions.map((suggestion) => (
                <li key={`${suggestion.lat},${suggestion.lng}`}>
                  <button
                    type="button"
                    role="option"
                    aria-selected={false}
                    onClick={() => choose(suggestion)}
                    className="block w-full border-b border-sand-100 px-3 py-2 text-left text-sm text-slate-700 transition last:border-0 hover:bg-brand-50"
                  >
                    <span aria-hidden className="mr-1.5">
                      📍
                    </span>
                    {suggestion.displayName}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </Field>

      {searching && <p className="text-xs text-slate-400">Buscando direcciones…</p>}

      {!searching && !located && draft.trim().length >= MIN_QUERY_LENGTH && suggestions.length === 0 && (
        <div className="flex flex-col gap-1.5">
          <p className="text-xs text-slate-500">
            No encontramos esa dirección. Prueba a añadir la ciudad o el código postal, o sitúa el
            punto tú mismo en el mapa.
          </p>
          <button
            type="button"
            onClick={useTypedAddressManually}
            className="self-start text-xs font-medium text-brand-700 underline underline-offset-2 hover:text-brand-800"
          >
            Usar «{draft.trim()}» tal cual y situar el punto a mano
          </button>
        </div>
      )}

      {located && (
        <div className="rounded-lg border border-sand-200 bg-sand-50 px-3 py-2 text-sm">
          <p className="text-slate-700">
            <span aria-hidden>📍 </span>
            {address}
          </p>
          {numberMatched === true && (
            <p className="mt-0.5 text-xs text-emerald-700">
              ✓ Ubicado a nivel de portal ({streetNumber}).
            </p>
          )}
          {numberMatched === false && (
            <p className="mt-0.5 text-xs text-amber-700">
              OpenStreetMap no tiene mapeado el número {streetNumber} en esta calle: la ubicación
              queda a nivel de calle, orientativa. La dirección escrita sigue siendo correcta.
            </p>
          )}
          {/* Los enlaces confirman el punto mirando el sitio real (mapa
              completo, Street View); el botón de abajo, en cambio, deja
              CORREGIRLO sin salir del formulario cuando el geocodificador se
              ha quedado corto. */}
          <div className="mt-1 flex flex-wrap items-center gap-3 text-xs">
            <a
              className="text-brand-700 underline hover:text-brand-800"
              href={`https://www.openstreetmap.org/?mlat=${latitude}&mlon=${longitude}#map=17/${latitude}/${longitude}`}
              target="_blank"
              rel="noreferrer"
            >
              Ver en el mapa
            </a>
            <a
              className="text-brand-700 underline hover:text-brand-800"
              href={`https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=${latitude},${longitude}`}
              target="_blank"
              rel="noreferrer"
            >
              Ver a pie de calle
            </a>
            <button
              type="button"
              onClick={() => setShowMap((current) => !current)}
              className="text-brand-700 underline hover:text-brand-800"
            >
              {showMap ? 'Ocultar ajuste manual' : 'Ajustar manualmente'}
            </button>
          </div>

          {showMap && (
            <div className="mt-2">
              <Suspense fallback={<Skeleton className="h-64 w-full" />}>
                <LocationPinMap latitude={latitude} longitude={longitude} onDragEnd={onChange} />
              </Suspense>
            </div>
          )}
        </div>
      )}

      {!located && draft.trim().length < MIN_QUERY_LENGTH && (
        <p className="text-xs text-slate-500">
          Sitúa tu domicilio en el mapa: es lo que permite que los clientes cercanos te encuentren.
        </p>
      )}

      {/* El número y la planta se escriben a mano porque el geocodificador no los
          conoce: OpenStreetMap sabe dónde está una calle, pero el portal y el
          timbre no están mapeados en ningún sitio — solo los sabe quien vive
          allí. Sin ellos, quien lleva su mascota llega y no sabe dónde llamar. */}
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Número" htmlFor="streetNumber" hint="Obligatorio.">
          <Input
            id="streetNumber"
            required
            value={streetNumber}
            placeholder="10"
            onChange={(event) => onChange({ streetNumber: event.target.value })}
          />
        </Field>
        <Field
          label={requiresFloor ? 'Planta y puerta' : 'Planta y puerta (opcional)'}
          htmlFor="floorDoor"
          hint={
            requiresFloor
              ? 'Obligatorio en un apartamento: es a qué timbre hay que llamar.'
              : 'Déjalo vacío si es una vivienda unifamiliar.'
          }
        >
          <Input
            id="floorDoor"
            required={requiresFloor}
            value={floorDoor}
            placeholder="3º B"
            onChange={(event) => onChange({ floorDoor: event.target.value })}
          />
        </Field>
      </div>
    </div>
  )
}
