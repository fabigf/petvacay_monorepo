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
  /** Mascotas que puede atender a la vez; el buscador ya lo usaba para filtrar. */
  maxSimultaneousPets: number
}

export interface GeocodeResult {
  lat: number
  lng: number
  displayName: string
  /**
   * Número de portal, solo cuando OpenStreetMap tiene ese resultado mapeado a
   * ese nivel de detalle; si no, viene ausente y las coordenadas son las de la
   * calle. Es el dato fiable para saber si un domicilio quedó geocodificado a
   * nivel de portal — comparar contra `displayName` en texto libre no lo es.
   */
  houseNumber?: string
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

/**
 * Plazas libres de un cuidador en una noche concreta.
 *
 * Lo calcula booking-service con la misma cuenta que valida la reserva, no el
 * modelo de lectura del buscador: así lo que se muestra antes de reservar es
 * exactamente lo que se va a aplicar al confirmarla.
 */
export interface DailyAvailability {
  date: string
  capacity: number
  occupied: number
  free: number
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
  disputeResolution?: DisputeResolution
  disputeResolutionNotes?: string
}

/**
 * Dirección exacta del cuidador y franja de entrega/recogida de una reserva ya
 * pagada (negocio.md 4.2 y 5). Antes de la confirmación del pago el backend
 * rechaza la petición: por eso vive en su propio recurso y no en el detalle.
 */
export interface BookingHandoff {
  sitterAddress: string
  sitterLatitude: number
  sitterLongitude: number
  dropOffDate: string
  pickUpDate: string
  /** Formato HH:mm:ss, tal y como lo serializa java.time.LocalTime. */
  windowStart: string
  windowEnd: string
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

/**
 * Ficha de una mascota tal y como la ve una de las partes de la reserva.
 *
 * Es más escueta que `Pet` a propósito: no lleva `ownerId` porque quien la
 * consulta ya sabe de quién es la reserva, y llega desde booking-service, no
 * desde el listado de mascotas propias.
 */
export interface BookingPet {
  petId: string
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
  /** Dirección legible de la que salieron las coordenadas; los perfiles antiguos no la tienen. */
  address?: string
  /** El geocodificador no conoce el portal ni el timbre: los aporta el cuidador. */
  streetNumber?: string
  floorDoor?: string
  /** Las tres partes ya concatenadas por el backend, para mostrarlas sin recomponer. */
  fullAddress?: string
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

/** SYSTEM es la reseña negativa automática por cancelar el cuidador (negocio.md 4.3). */
export type ReviewAuthorRole = 'CLIENT' | 'SITTER' | 'SYSTEM'

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

/** Tipos de aviso de la campana; el texto ya viene redactado del backend. */
export type NotificationType =
  | 'BOOKING_REQUESTED'
  | 'BOOKING_ACCEPTED'
  | 'BOOKING_REJECTED'
  | 'BOOKING_CONFIRMED'
  | 'BOOKING_CANCELLED'
  | 'BOOKING_FINISHED'
  | 'DISPUTE_OPENED'
  | 'DISPUTE_RESOLVED'
  | 'PET_EMERGENCY'
  | 'SITTER_PENDING_REVIEW'
  | 'SITTER_APPROVED'
  | 'SITTER_REJECTED'

export interface Notification {
  notificationId: string
  type: NotificationType
  title: string
  body?: string
  bookingId?: string
  createdAt: string
  unread: boolean
}

/** Resolución manual de una disputa por el equipo de administración (negocio.md 4.4). */
export type DisputeResolution = 'FAVOR_CLIENT' | 'FAVOR_SITTER' | 'SPLIT'

/** Tramo de una lista larga; `totalPages` evita recalcularlo en cada vista. */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/**
 * Ficha completa de un cuidador para el back-office. Lleva datos que la vista
 * pública de un perfil no expone —correo, documento de identidad, si la cuenta
 * sigue activa— porque son justo los que hacen falta para verificar un KYC.
 */
export interface AdminSitter {
  sitterId: string
  firstName?: string
  lastName?: string
  email?: string
  birthDate?: string
  accountActive: boolean
  registeredAt?: string
  description?: string
  homeType: HomeType
  /** Dirección legible; los perfiles anteriores al selector solo tienen coordenadas. */
  address?: string
  streetNumber?: string
  floorDoor?: string
  fullAddress?: string
  latitude: number
  longitude: number
  servicePrices: ServicePrices
  currency: string
  maxSimultaneousPets: number
  acceptedSpecies: Species[]
  maxPetWeightKg?: number
  photoUrl?: string
  verificationStatus: VerificationStatus
  identityDocumentReference?: string
  searchable: boolean
  rejectionReason?: string
  verifiedAt?: string
}

export type IncidentType = 'DISPUTA' | 'ALERTA_MEDICA'
export type IncidentStatus = 'ABIERTA' | 'RESUELTA'

/** Registro de lo que ha pasado en una reserva y a quién afecta (negocio.md 2.3). */
export interface Incident {
  incidentId: string
  bookingId: string
  clientId: string
  sitterId: string
  type: IncidentType
  status: IncidentStatus
  description?: string
  resolutionNotes?: string
  openedAt: string
  resolvedAt?: string
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
