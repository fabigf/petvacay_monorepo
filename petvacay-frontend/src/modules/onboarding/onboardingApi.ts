import apiClient from '@/core/api/apiClient'
import type { HomeType, ServicePrices, SitterProfile, Species } from '@/core/api/types'

export interface CreateSitterProfileRequest {
  description: string
  homeType: HomeType
  latitude: number
  longitude: number
  servicePrices: ServicePrices
  maxSimultaneousPets: number
  acceptedSpecies: Species[]
  maxPetWeightKg?: number
  photoUrl?: string
}

export async function createSitterProfile(
  request: CreateSitterProfileRequest,
): Promise<SitterProfile> {
  const { data } = await apiClient.post<SitterProfile>('/sitters/profile', request)
  return data
}

/**
 * Actualiza tarifas, capacidad, restricciones o foto de un perfil ya creado.
 * El backend lo tenía implementado desde la Fase 4 (`PUT /sitters/profile`)
 * pero el frontend nunca lo había expuesto.
 */
export async function updateSitterProfile(
  request: CreateSitterProfileRequest,
): Promise<SitterProfile> {
  const { data } = await apiClient.put<SitterProfile>('/sitters/profile', request)
  return data
}

export async function getSitterProfile(sitterId: string): Promise<SitterProfile> {
  const { data } = await apiClient.get<SitterProfile>(`/sitters/${sitterId}/profile`)
  return data
}

/**
 * Envía la referencia del documento de identidad ya almacenado.
 * El fichero nunca viaja por esta API ni se guarda en la base de datos: el
 * procesado es asíncrono y la verificación final la hace un administrador.
 */
export async function submitIdentityDocument(documentReference: string): Promise<SitterProfile> {
  const { data } = await apiClient.post<SitterProfile>('/sitters/kyc/document', {
    documentReference,
  })
  return data
}
