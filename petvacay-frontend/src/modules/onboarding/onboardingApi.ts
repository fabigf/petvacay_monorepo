import apiClient from '@/core/api/apiClient'
import type { HomeType, SitterProfile, Species } from '@/core/api/types'

export interface CreateSitterProfileRequest {
  description: string
  homeType: HomeType
  latitude: number
  longitude: number
  pricePerNight: number
  maxSimultaneousPets: number
  acceptedSpecies: Species[]
  maxPetWeightKg?: number
}

export async function createSitterProfile(
  request: CreateSitterProfileRequest,
): Promise<SitterProfile> {
  const { data } = await apiClient.post<SitterProfile>('/sitters/profile', request)
  return data
}

export async function getSitterProfile(sitterId: string): Promise<SitterProfile> {
  const { data } = await apiClient.get<SitterProfile>(`/sitters/${sitterId}/profile`)
  return data
}

/**
 * Envia la referencia del documento de identidad ya almacenado.
 * El fichero nunca viaja por esta API ni se guarda en la base de datos: el
 * procesado es asincrono y la verificacion final la hace un administrador.
 */
export async function submitIdentityDocument(documentReference: string): Promise<SitterProfile> {
  const { data } = await apiClient.post<SitterProfile>('/sitters/kyc/document', {
    documentReference,
  })
  return data
}
