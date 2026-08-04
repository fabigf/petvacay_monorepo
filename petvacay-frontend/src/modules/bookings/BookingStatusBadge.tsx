import type { BookingStatus } from '@/core/api/types'
import { Badge } from '@/shared/components/ui'

type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'info'

/** Traducción de la máquina de estados del dominio a lenguaje de usuario. */
const statusPresentation: Record<BookingStatus, { label: string; tone: Tone }> = {
  BORRADOR: { label: 'Borrador', tone: 'neutral' },
  SOLICITADO: { label: 'Pendiente de respuesta', tone: 'warning' },
  PENDIENTE_PAGO: { label: 'Pendiente de pago', tone: 'warning' },
  CONFIRMADO: { label: 'Confirmada', tone: 'success' },
  EN_PROGRESO: { label: 'En curso', tone: 'info' },
  FINALIZADO: { label: 'Finalizada', tone: 'neutral' },
  CANCELADO: { label: 'Cancelada', tone: 'danger' },
  EN_DISPUTA: { label: 'En disputa', tone: 'danger' },
}

export function BookingStatusBadge({ status }: { status: BookingStatus }) {
  const { label, tone } = statusPresentation[status]
  return <Badge tone={tone}>{label}</Badge>
}
