/**
 * Contratos de API compartidos con el backend (especificaciones.md).
 * El tipado estricto es la primera línea de defensa cuando el backend evolucione
 * hacia microservicios en Fase 4.
 */

export type Species = 'DOG' | 'CAT' | 'RABBIT' | 'BIRD' | 'OTHER'
export type Sociability = 'SOCIABLE' | 'SELECTIVE' | 'NEEDS_TO_BE_ALONE'
export type HomeType = 'APARTMENT' | 'HOUSE_GARDEN' | 'RURAL_HOUSE'
export type ServiceType = 'NIGHT_ACCOMMODATION' | 'DAY_CARE' | 'WALKING'
export type Role = 'ROLE_CLIENT' | 'ROLE_SITTER' | 'ROLE_ADMIN'

export type VerificationStatus =
  | 'PENDIENTE_VERIFICACION'
  | 'VERIFICADO'
  | 'RECHAZADO'

/** Estados del ciclo de vida de la reserva (negocio.md 4.2). */
export type BookingStatus =
  | 'BORRADOR'
  | 'SOLICITADO'
  | 'PENDIENTE_PAGO'
  | 'CONFIRMADO'
  | 'EN_PROGRESO'
  | 'FINALIZADO'
  | 'CANCELADO'
  | 'EN_DISPUTA'

export interface SitterSearchResult {
  sitterId: string
  firstName: string
  rating: number
  reviewCount: number
  distanceKm: number
  pricePerNight: number
  currency: string
  homeType: HomeType
  photoUrl?: string
  acceptedSpecies: Species[]
  offeredServices: ServiceType[]
}

export interface GeocodeResult {
  lat: number
  lng: number
  displayName: string
}

export interface SearchFilters {
  lat: number
  lng: number
  radiusKm?: number
  startDate?: string
  endDate?: string
  petType?: Species
  petCount?: number
}

export interface CreateBookingRequest {
  sitterId: string
  petIds: string[]
  startDate: string
  endDate: string
  serviceType: ServiceType
}

export interface CreateBookingResponse {
  bookingId: string
  status: BookingStatus
  finalAmountToPay: number
}

export interface BookingDetail {
  bookingId: string
  clientId: string
  sitterId: string
  petIds: string[]
  startDate: string
  endDate: string
  nights: number
  serviceType: ServiceType
  status: BookingStatus
  currency: string
  pricePerNight: number
  basePrice: number
  clientServiceFee: number
  finalAmountToPay: number
  sitterPayout: number
  platformRevenue: number
  requestedAt?: string
  confirmedAt?: string
  closedAt?: string
  scheduledPayoutAt?: string
  cancelledBy?: 'CLIENT' | 'SITTER' | 'SYSTEM'
  refundToClient?: number
  disputeReason?: string
}

export interface Pet {
  petId: string
  ownerId: string
  name: string
  species: Species
  breed?: string
  ageYears: number
  weightKg: number
  medicalNeeds?: string
  dietaryRestrictions?: string
  sociability: Sociability
  specialMedicalNeeds: boolean
}

export interface UserProfile {
  userId: string
  email: string
  firstName: string
  lastName: string
  roles: Role[]
  active: boolean
}

/**
 * Tarifa por servicio: el cuidador puede ofrecer solo alguno de los tres
 * (negocio.md sección 2), así que las claves ausentes significan "no lo ofrece".
 */
export type ServicePrices = Partial<Record<ServiceType, number>>

export interface SitterProfile {
  sitterId: string
  /**
   * Solo llega en la vista pública (`GET /sitters/{id}/profile`); las respuestas
   * a operaciones sobre el propio perfil lo omiten.
   */
  firstName?: string
  description?: string
  homeType: HomeType
  latitude: number
  longitude: number
  servicePrices: ServicePrices
  currency: string
  maxSimultaneousPets: number
  acceptedSpecies: Species[]
  maxPetWeightKg?: number
  photoUrl?: string
  verificationStatus: VerificationStatus
  searchable: boolean
  rejectionReason?: string
  verifiedAt?: string
}

export type ReviewAuthorRole = 'CLIENT' | 'SITTER'

export interface Review {
  reviewId: string
  bookingId: string
  authorId: string
  subjectId: string
  authorRole: ReviewAuthorRole
  rating: number
  comment?: string
  createdAt: string
}

/** Formato RFC 7807 devuelto por el backend (especificaciones.md 4). */
export interface ProblemDetails {
  type?: string
  title: string
  status: number
  detail?: string
  instance?: string
  code?: string
  invalid_params?: Array<{ name: string; reason: string }>
}
