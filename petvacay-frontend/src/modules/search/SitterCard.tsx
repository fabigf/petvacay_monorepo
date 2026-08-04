import { useNavigate } from 'react-router-dom'
import type { HomeType, SitterSearchResult } from '@/core/api/types'
import { Badge, Button, Card } from '@/shared/components/ui'

const homeTypeLabels: Record<HomeType, string> = {
  APARTMENT: 'Apartamento',
  HOUSE_GARDEN: 'Casa con jardín',
  RURAL_HOUSE: 'Casa rural',
}

export function SitterCard({ sitter }: { sitter: SitterSearchResult }) {
  const navigate = useNavigate()

  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-base font-semibold text-slate-800">{sitter.firstName}</h3>
          <p className="text-sm text-slate-500">
            {homeTypeLabels[sitter.homeType]} · a {sitter.distanceKm} km
          </p>
        </div>
        <Badge tone={sitter.rating > 0 ? 'success' : 'neutral'}>
          {sitter.rating > 0 ? `★ ${sitter.rating.toFixed(2)}` : 'Sin reseñas'}
        </Badge>
      </div>

      <div className="flex items-end justify-between gap-3">
        <p className="text-sm text-slate-600">
          <span className="text-xl font-bold text-slate-800">
            {sitter.pricePerNight.toFixed(2)} {sitter.currency === 'EUR' ? '€' : sitter.currency}
          </span>
          <span className="ml-1">/ noche</span>
        </p>
        <Button onClick={() => navigate(`/bookings/new/${sitter.sitterId}`)}>Reservar</Button>
      </div>
    </Card>
  )
}
