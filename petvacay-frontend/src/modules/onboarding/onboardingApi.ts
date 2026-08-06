import apiClient from '@/core/api/apiClient'
import type { HomeType, ServicePrices, SitterProfile, Species } from '@/core/api/types'

export interface CreateSitterProfileRequest {
  description: string
  homeType: HomeType
  /** Dirección legible; las coordenadas salen de geocodificarla. */
  address: string
  /** Obligatorio: el geocodificador no sabe en qué portal vives. */
  streetNumber: string
  /** Obligatorio solo en apartamento; en casa o finca no existe. */
  floorDoor: string
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

/**
 * Vista pública del perfil de otro cuidador (buscador, formulario de reserva,
 * "tu cuidador" en el detalle). NO incluye la dirección: el backend la omite a
 * propósito — se revela solo al cliente de una reserva ya pagada, a través de
 * `getBookingHandoff` en `bookingsApi.ts`. Ver el javadoc de
 * `IdentityDtos.SitterProfileResponse` en el backend.
 */
export async function getSitterProfile(sitterId: string): Promise<SitterProfile> {
  const { data } = await apiClient.get<SitterProfile>(`/sitters/${sitterId}/profile`)
  return data
}

/**
 * El propio perfil del cuidador autenticado, con la dirección completa para
 * poder revisarla o corregirla al editar.
 */
export async function getMySitterProfile(): Promise<SitterProfile> {
  const { data } = await apiClient.get<SitterProfile>('/sitters/profile')
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
