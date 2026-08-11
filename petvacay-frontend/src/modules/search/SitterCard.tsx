import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import type { HomeType, ServiceType, SitterSearchResult, Species } from '@/core/api/types'
import { Button, Card } from '@/shared/components/ui'
import { StarRating } from '@/shared/components/StarRating'
import { formatMoney } from '@/shared/format'
import { SitterReviewsModal } from '@/modules/reviews/SitterReviewsModal'

const homeTypeLabels: Record<HomeType, string> = {
  APARTMENT: 'Apartamento',
  HOUSE_GARDEN: 'Casa con jardín',
  RURAL_HOUSE: 'Casa rural',
}

const serviceTypeLabels: Record<ServiceType, string> = {
  NIGHT_ACCOMMODATION: 'Alojamiento',
  DAY_CARE: 'Guardería',
  WALKING: 'Paseos',
}

const speciesLabels: Record<Species, string> = {
  DOG: 'Perros',
  CAT: 'Gatos',
  RABBIT: 'Conejos',
  BIRD: 'Aves',
  OTHER: 'Otros',
}

function Tag({ children, tone = 'neutral' }: { children: string; tone?: 'neutral' | 'brand' }) {
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-xs font-medium ${
        tone === 'brand' ? 'bg-brand-50 text-brand-700' : 'bg-sand-100 text-slate-600'
      }`}
    >
      {children}
    </span>
  )
}

export function SitterCard({ sitter }: { sitter: SitterSearchResult }) {
  const navigate = useNavigate()
  const [showReviews, setShowReviews] = useState(false)

  return (
    <Card interactive className="flex h-full flex-col gap-3">
      <div className="flex items-start gap-3">
        {sitter.photoUrl ? (
          <img
            src={sitter.photoUrl}
            alt={sitter.firstName}
            className="size-14 shrink-0 rounded-full object-cover"
          />
        ) : (
          <div className="flex size-14 shrink-0 items-center justify-center rounded-full bg-sand-100 text-lg font-semibold text-slate-500">
            {sitter.firstName.charAt(0).toUpperCase()}
          </div>
        )}

        <div className="flex-1">
          <h3 className="text-base font-semibold text-slate-800">{sitter.firstName}</h3>
          <p className="text-sm text-slate-500">
            {homeTypeLabels[sitter.homeType]} · a {sitter.distanceKm} km
          </p>
          {/* La media y el recuento resumen la reputación, pero no dicen POR QUÉ:
              el enlace abre los comentarios sin obligar a entrar antes en el
              formulario de reserva, que era el único sitio donde se leían. */}
          {sitter.reviewCount > 0 ? (
            <div className="mt-0.5 flex flex-wrap items-center gap-1.5">
              <StarRating value={sitter.rating} size="sm" />
              <span className="text-xs text-slate-500">
                {sitter.rating.toFixed(1)} ({sitter.reviewCount})
              </span>
              <button
                type="button"
                onClick={() => setShowReviews(true)}
                className="text-xs font-medium text-brand-700 underline underline-offset-2 hover:text-brand-800"
              >
                Ver reseñas
              </button>
            </div>
          ) : (
            <p className="mt-0.5 text-xs text-slate-400">Sin reseñas todavía</p>
          )}
        </div>
      </div>

      {/* Servicios en tono de marca (lo que se reserva) y especies en tono
          neutro (lo que se acepta): dos categorías distintas, dos estilos
          distintos, para que se distingan de un vistazo sin leer cada tag. */}
      <div className="flex flex-wrap gap-1.5">
        {sitter.offeredServices.map((type) => (
          <Tag key={type} tone="brand">
            {serviceTypeLabels[type]}
          </Tag>
        ))}
        {sitter.acceptedSpecies.map((species) => (
          <Tag key={species}>{speciesLabels[species]}</Tag>
        ))}
        {/* Capacidad simultánea: condiciona si puede aceptar la reserva de quien
            viaja con más de una mascota, así que merece verse antes de pulsar
            "Reservar" y no al recibir un 409 por falta de plazas. */}
        <Tag>
          {sitter.maxSimultaneousPets === 1
            ? 'Una mascota a la vez'
            : `Hasta ${sitter.maxSimultaneousPets} mascotas a la vez`}
        </Tag>
      </div>

      {/* Separador antes del precio: cierra la ficha y evita que la tarifa se
          lea como una etiqueta más de la fila de arriba. */}
      <div className="mt-auto flex items-end justify-between gap-3 border-t border-sand-100 pt-3">
        <div>
          {/* Puede ofrecer alojamiento, guardería o paseos, cada uno con su
              propia tarifa: esto es la más barata de su catálogo, no "la"
              tarifa — por eso "desde" en vez de fijar una unidad ("/ noche"). */}
          <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Desde</p>
          <p className="text-xl font-bold tabular-nums text-slate-900">
            {formatMoney(sitter.pricePerNight, sitter.currency)}
          </p>
        </div>
        <Button onClick={() => navigate(`/bookings/new/${sitter.sitterId}`)}>Reservar</Button>
      </div>

      {showReviews && (
        <SitterReviewsModal
          sitterId={sitter.sitterId}
          sitterName={sitter.firstName}
          onClose={() => setShowReviews(false)}
        />
      )}
    </Card>
  )
}
