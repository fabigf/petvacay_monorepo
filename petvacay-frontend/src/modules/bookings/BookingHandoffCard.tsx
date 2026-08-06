import type { BookingHandoff } from '@/core/api/types'
import { Card, Skeleton } from '@/shared/components/ui'

/** «HH:mm:ss» (como serializa java.time.LocalTime) → «HH:mm». */
function formatTime(time: string): string {
  return time.slice(0, 5)
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long' })
}

/**
 * Dirección exacta del cuidador y franja de entrega/recogida (negocio.md 4.2 y
 * 5). Solo existe una vez el pago está confirmado: antes de eso `handoff` es
 * `null` y la tarjeta ni se muestra, porque el backend rechaza la petición.
 *
 * <p>La entrega y la recogida comparten la misma franja horaria fija —
 * 09:00 a 20:00 — porque la plataforma todavía no modela el horario real de
 * cada cuidador; ver el javadoc de `HandoffWindow` en el backend.
 */
export function BookingHandoffCard({
  handoff,
  loading,
}: {
  handoff: BookingHandoff | null
  loading: boolean
}) {
  if (!loading && !handoff) return null

  return (
    <Card>
      <h2 className="mb-3 font-semibold text-slate-800">Entrega y recogida</h2>

      {loading && <Skeleton className="h-20 w-full" />}

      {handoff && (
        <div className="flex flex-col gap-3">
          <div className="rounded-lg border border-sand-200 bg-sand-50 p-3">
            {handoff.sitterAddress ? (
              <p className="text-sm text-slate-700">
                <span aria-hidden>📍 </span>
                {handoff.sitterAddress}
              </p>
            ) : (
              <p className="text-sm text-slate-500">
                El cuidador todavía no ha completado su dirección exacta. Contacta con él para acordar el
                punto de entrega.
              </p>
            )}
            {handoff.sitterLatitude !== 0 || handoff.sitterLongitude !== 0 ? (
              <div className="mt-1 flex flex-wrap items-center gap-3 text-xs">
                <a
                  className="text-brand-700 underline hover:text-brand-800"
                  href={`https://www.openstreetmap.org/?mlat=${handoff.sitterLatitude}&mlon=${handoff.sitterLongitude}#map=17/${handoff.sitterLatitude}/${handoff.sitterLongitude}`}
                  target="_blank"
                  rel="noreferrer"
                >
                  Ver en el mapa
                </a>
                <a
                  className="text-brand-700 underline hover:text-brand-800"
                  href={`https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=${handoff.sitterLatitude},${handoff.sitterLongitude}`}
                  target="_blank"
                  rel="noreferrer"
                >
                  Ver a pie de calle
                </a>
              </div>
            ) : null}
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <div className="rounded-lg border border-sand-200 p-3">
              <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Entrega</p>
              <p className="mt-0.5 text-sm capitalize text-slate-800">{formatDate(handoff.dropOffDate)}</p>
              <p className="text-sm text-slate-600">
                de {formatTime(handoff.windowStart)} a {formatTime(handoff.windowEnd)}
              </p>
            </div>
            <div className="rounded-lg border border-sand-200 p-3">
              <p className="text-xs font-medium uppercase tracking-wide text-slate-500">Recogida</p>
              <p className="mt-0.5 text-sm capitalize text-slate-800">{formatDate(handoff.pickUpDate)}</p>
              <p className="text-sm text-slate-600">
                de {formatTime(handoff.windowStart)} a {formatTime(handoff.windowEnd)}
              </p>
            </div>
          </div>
        </div>
      )}
    </Card>
  )
}
