import apiClient from '@/core/api/apiClient'
import type { GeocodeResult, SearchFilters, SitterSearchResult } from '@/core/api/types'

/**
 * Lado de lectura del CQRS. Estas consultas viajan al modelo optimizado
 * (PostgreSQL en Fase 1, MongoDB desde Fase 2) y deben responder en < 50 ms.
 */
export async function searchSitters(
  filters: SearchFilters,
): Promise<SitterSearchResult[]> {
  const { data } = await apiClient.get<SitterSearchResult[]>('/search/sitters', {
    params: filters,
  })
  return data
}

/**
 * Traduce una dirección o código postal a coordenadas (backend: Nominatim,
 * ver NominatimGeocodingAdapter). Devuelve `null` en vez de lanzar cuando no
 * se encuentra nada — no es un error de red, es "esa dirección no existe".
 */
/**
 * Candidatos para un texto a medio escribir. Devuelve lista vacía en vez de
 * fallar cuando no hay coincidencias: un autocompletado se consulta en cada
 * pulsación y "todavía no hay nada" es el caso normal, no un error.
 */
export async function suggestAddresses(query: string): Promise<GeocodeResult[]> {
  try {
    const { data } = await apiClient.get<GeocodeResult[]>('/search/geocode/suggest', {
      params: { q: query },
    })
    return data
  } catch {
    return []
  }
}

export async function geocodeAddress(query: string): Promise<GeocodeResult | null> {
  try {
    const { data } = await apiClient.get<GeocodeResult>('/search/geocode', {
      params: { q: query },
    })
    return data
  } catch (error) {
    if (error && typeof error === 'object' && 'response' in error) {
      const response = (error as { response?: { status?: number } }).response
      if (response?.status === 404) {
        return null
      }
    }
    throw error
  }
}
