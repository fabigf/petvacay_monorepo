import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { acceptBooking, listBookings, rejectBooking } from './bookingsApi'
import { BookingStatusBadge } from './BookingStatusBadge'
import type { BookingDetail, ServiceType } from '@/core/api/types'
import { Button, Card, EmptyState, Skeleton } from '@/shared/components/ui'
import { PageHeader } from '@/shared/components/PageHeader'
import { formatDateRange, formatMoney, formatNights, formatPets } from '@/shared/format'
import { useAuthStore } from '@/core/security/authStore'
import { useProblemToast } from '@/shared/hooks/useProblemToast'

const serviceTypeLabels: Record<ServiceType, string> = {
  NIGHT_ACCOMMODATION: 'Alojamiento nocturno',
  DAY_CARE: 'Guardería de día',
  WALKING: 'Paseos por horas',
}

type Perspective = 'client' | 'sitter'

/** Historial de reservas, con la doble perspectiva cliente / cuidador. */
export function DashboardPage() {
  const handleProblem = useProblemToast()
  const isSitter = useAuthStore((state) => state.hasRole('ROLE_SITTER'))

  // Todo cuidador es tambien cliente (no existe el rol "solo cuidador"), pero
  // si alguien tiene el rol de cuidador lo mas probable es que entre aqui a
  // gestionar sus solicitudes, no a mirar sus propias reservas como cliente.
  const [perspective, setPerspective] = useState<Perspective>(isSitter ? 'sitter' : 'client')
  const [bookings, setBookings] = useState<BookingDetail[]>([])
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(
    async (role: Perspective) => {
      setLoading(true)
      try {
        setBookings(await listBookings(role))
      } catch (error) {
        handleProblem(error)
      } finally {
        setLoading(false)
      }
    },
    [handleProblem],
  )

  useEffect(() => {
    void refresh(perspective)
  }, [perspective, refresh])

  const respond = async (bookingId: string, action: 'accept' | 'reject') => {
    try {
      await (action === 'accept' ? acceptBooking(bookingId) : rejectBooking(bookingId))
      toast.success(action === 'accept' ? 'Solicitud aceptada' : 'Solicitud rechazada')
      await refresh(perspective)
    } catch (error) {
      handleProblem(error)
    }
  }

  return (
    <div>
      <PageHeader
        title="Mis reservas"
        subtitle={
          perspective === 'client'
            ? 'Las estancias que has solicitado como cliente.'
            : 'Las solicitudes que recibes como cuidador.'
        }
        actions={
          isSitter && (
            // Selector de perspectiva: es un grupo de opciones excluyentes, así
            // que se anuncia como tal y no como dos botones sueltos.
            <div role="group" aria-label="Ver reservas como" className="flex rounded-lg border border-sand-200 bg-white p-1">
              {(['client', 'sitter'] as const).map((role) => (
                <button
                  key={role}
                  type="button"
                  aria-pressed={perspective === role}
                  onClick={() => setPerspective(role)}
                  className={`rounded-md px-3 py-1.5 text-sm font-medium transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-600 ${
                    perspective === role
                      ? 'bg-brand-600 text-white'
                      : 'text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  {role === 'client' ? 'Como cliente' : 'Como cuidador'}
                </button>
              ))}
            </div>
          )
        }
      />

      {loading ? (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
        </div>
      ) : bookings.length === 0 ? (
        <EmptyState
          icon="🐾"
          title="Aún no tienes reservas"
          description={
            perspective === 'client'
              ? 'Busca un cuidador verificado cerca de ti y envía tu primera solicitud.'
              : 'Cuando un cliente te envíe una solicitud aparecerá aquí para que la aceptes o la rechaces.'
          }
          action={
            perspective === 'client' ? (
              <Link to="/search">
                <Button>Buscar cuidador</Button>
              </Link>
            ) : (
              <Link to="/dashboard/sitter">
                <Button variant="secondary">Revisar mi perfil</Button>
              </Link>
            )
          }
        />
      ) : (
        <div className="flex flex-col gap-3">
          {bookings.map((booking) => (
            <Card
              key={booking.bookingId}
              interactive
              className="flex flex-wrap items-center gap-x-6 gap-y-4"
            >
              <div className="min-w-56 flex-1">
                <div className="mb-1.5 flex flex-wrap items-center gap-2">
                  <BookingStatusBadge status={booking.status} />
                  <span className="text-sm font-semibold text-slate-800">
                    {serviceTypeLabels[booking.serviceType]}
                  </span>
                </div>
                <p className="text-sm text-slate-700">
                  {formatDateRange(booking.startDate, booking.endDate)}
                </p>
                <p className="mt-0.5 text-sm text-slate-500">
                  {formatNights(booking.nights)} · {formatPets(booking.petIds.length)}
                </p>
              </div>

              {/* Ancho fijo para que la columna de importes quede alineada entre
                  tarjetas en lugar de bailar segun la longitud de cada cifra. */}
              <div className="w-32 shrink-0 text-right">
                <p className="text-xs font-medium uppercase tracking-wide text-slate-500">
                  {perspective === 'sitter' ? 'Recibirás' : 'Total'}
                </p>
                <p className="text-lg font-bold tabular-nums text-slate-900">
                  {formatMoney(
                    perspective === 'sitter' ? booking.sitterPayout : booking.finalAmountToPay,
                    booking.currency,
                  )}
                </p>
              </div>

              <div className="flex shrink-0 gap-2">
                {perspective === 'sitter' && booking.status === 'SOLICITADO' && (
                  <>
                    <Button onClick={() => void respond(booking.bookingId, 'accept')}>
                      Aceptar
                    </Button>
                    <Button
                      variant="secondary"
                      onClick={() => void respond(booking.bookingId, 'reject')}
                    >
                      Rechazar
                    </Button>
                  </>
                )}
                <Link to={`/bookings/${booking.bookingId}`}>
                  <Button variant="secondary">Ver detalle</Button>
                </Link>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
