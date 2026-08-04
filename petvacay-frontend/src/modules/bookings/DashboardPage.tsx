import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { acceptBooking, listBookings, rejectBooking } from './bookingsApi'
import { BookingStatusBadge } from './BookingStatusBadge'
import type { BookingDetail } from '@/core/api/types'
import { Button, Card, EmptyState, Skeleton } from '@/shared/components/ui'
import { useAuthStore } from '@/core/security/authStore'
import { useProblemToast } from '@/shared/hooks/useProblemToast'

type Perspective = 'client' | 'sitter'

/** Historial de reservas, con la doble perspectiva cliente / cuidador. */
export function DashboardPage() {
  const handleProblem = useProblemToast()
  const isSitter = useAuthStore((state) => state.hasRole('ROLE_SITTER'))

  const [perspective, setPerspective] = useState<Perspective>('client')
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
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold text-slate-800">Mis reservas</h1>

        {isSitter && (
          <div className="flex rounded-lg border border-sand-200 bg-white p-1">
            {(['client', 'sitter'] as const).map((role) => (
              <button
                key={role}
                onClick={() => setPerspective(role)}
                className={`rounded-md px-3 py-1.5 text-sm font-medium transition ${
                  perspective === role
                    ? 'bg-brand-600 text-white'
                    : 'text-slate-600 hover:bg-slate-100'
                }`}
              >
                {role === 'client' ? 'Como cliente' : 'Como cuidador'}
              </button>
            ))}
          </div>
        )}
      </div>

      {loading ? (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
        </div>
      ) : bookings.length === 0 ? (
        <EmptyState
          title="Aún no tienes reservas"
          description={
            perspective === 'client'
              ? 'Busca un cuidador y envía tu primera solicitud.'
              : 'Cuando un cliente te envíe una solicitud aparecerá aquí.'
          }
        />
      ) : (
        <div className="flex flex-col gap-3">
          {bookings.map((booking) => (
            <Card key={booking.bookingId} className="flex flex-wrap items-center gap-4">
              <div className="min-w-52 flex-1">
                <div className="mb-1 flex items-center gap-2">
                  <BookingStatusBadge status={booking.status} />
                  <span className="text-xs text-slate-400">
                    #{booking.bookingId.slice(0, 8)}
                  </span>
                </div>
                <p className="text-sm text-slate-700">
                  {booking.startDate} → {booking.endDate} · {booking.nights}{' '}
                  {booking.nights === 1 ? 'noche' : 'noches'} · {booking.petIds.length}{' '}
                  {booking.petIds.length === 1 ? 'mascota' : 'mascotas'}
                </p>
              </div>

              <div className="text-right">
                <p className="text-xs text-slate-500">
                  {perspective === 'sitter' ? 'Recibirás' : 'Total'}
                </p>
                <p className="text-lg font-bold text-slate-800">
                  {(perspective === 'sitter'
                    ? booking.sitterPayout
                    : booking.finalAmountToPay
                  ).toFixed(2)}{' '}
                  €
                </p>
              </div>

              <div className="flex gap-2">
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
