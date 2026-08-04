import apiClient from '@/core/api/apiClient'
import type { SearchFilters, SitterSearchResult } from '@/core/api/types'

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
