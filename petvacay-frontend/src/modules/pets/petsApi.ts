import apiClient from '@/core/api/apiClient'
import type { Pet, Sociability, Species } from '@/core/api/types'

export interface AddPetRequest {
  name: string
  species: Species
  breed?: string
  ageYears: number
  weightKg: number
  medicalNeeds?: string
  dietaryRestrictions?: string
  sociability: Sociability
}

export async function listPets(): Promise<Pet[]> {
  const { data } = await apiClient.get<Pet[]>('/pets')
  return data
}

export async function addPet(request: AddPetRequest): Promise<Pet> {
  const { data } = await apiClient.post<Pet>('/pets', request)
  return data
}

export async function removePet(petId: string): Promise<void> {
  await apiClient.delete(`/pets/${petId}`)
}
