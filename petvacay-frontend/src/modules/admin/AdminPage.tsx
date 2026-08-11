import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import {
  listBookingsByStatus,
  listIncidents,
  listPendingSitters,
  listUsers,
  reprojectReputations,
  resolveDispute,
} from '@/modules/admin/adminApi'
import { UsersSection } from '@/modules/admin/UsersSection'
import { SittersSection } from '@/modules/admin/SittersSection'
import { IncidentsSection } from '@/modules/admin/IncidentsSection'
import type { BookingDetail, DisputeResolution, UserProfile } from '@/core/api/types'
import { Badge, Button, Card, EmptyState, Field, Input, Select, Skeleton } from '@/shared/components/ui'
import { PageHeader } from '@/shared/components/PageHeader'
import { formatDateRange, formatMoney } from '@/shared/format'
import { useProblemToast } from '@/shared/hooks/useProblemToast'

const resolutionLabels: Record<DisputeResolution, string> = {
  FAVOR_CLIENT: 'A favor del cliente (reembolso íntegro)',
  FAVOR_SITTER: 'A favor del cuidador (liquidación normal)',
  SPLIT: 'Reparto intermedio',
}

type Tab = 'sitters' | 'clients' | 'disputes' | 'incidents' | 'users'

const tabLabels: Record<Tab, string> = {
  sitters: 'Cuidadores',
  clients: 'Clientes',
  disputes: 'Disputas',
  incidents: 'Incidencias',
  users: 'Todas las cuentas',
}

/**
 * Panel de administración (negocio.md 2.3).
 *
 * <p>Reúne las competencias del administrador: verificar cuidadores, resolver
 * disputas, consultar el registro de incidencias y gestionar cuentas. Hasta que
 * existió esta pantalla, todas ellas solo se podían ejercer con `curl` y el
 * token del administrador.
 *
 * <p>Va por pestañas y no en una sola columna porque son cuatro trabajos
 * distintos: quien entra a resolver una disputa no quiere desplazarse por el
 * listado de usuarios para llegar.
 *
 * <p>La ruta está protegida por `ROLE_ADMIN` en `AppRouter`, pero eso es solo
 * experiencia de usuario: cada endpoint vuelve a exigir el rol en el servidor.
 */
export function AdminPage() {
  const handleProblem = useProblemToast()
  const [tab, setTab] = useState<Tab>('sitters')
  const [pendingSitters, setPendingSitters] = useState(0)
  const [disputes, setDisputes] = useState<BookingDetail[] | null>(null)
  const [users, setUsers] = useState<UserProfile[]>([])
  const [openIncidents, setOpenIncidents] = useState(0)

  const refresh = useCallback(() => {
    listPendingSitters()
      .then((sitters) => setPendingSitters(sitters.length))
      .catch(() => setPendingSitters(0))
    listBookingsByStatus('EN_DISPUTA').then(setDisputes).catch(() => setDisputes([]))
    // El listado de cuentas se carga una vez y se comparte: la pestaña de
    // incidencias lo necesita solo para traducir identificadores a nombres.
    // Se pide una página grande a propósito — es un mapa de apoyo, no un listado.
    listUsers({ size: 500 }).then((result) => setUsers(result.content)).catch(() => setUsers([]))
    listIncidents({ status: 'ABIERTA' })
      .then((result) => setOpenIncidents(result.totalElements))
      .catch(() => setOpenIncidents(0))
  }, [])

  useEffect(refresh, [refresh])

  const run = async (action: () => Promise<unknown>, successMessage: string) => {
    try {
      await action()
      toast.success(successMessage)
      refresh()
    } catch (error) {
      handleProblem(error)
    }
  }

  /** Cuenta de "cosas por atender" de cada pestaña; 0 no pinta nada. */
  const badgeFor = (candidate: Tab) => {
    if (candidate === 'sitters') return pendingSitters
    if (candidate === 'disputes') return disputes?.length ?? 0
    if (candidate === 'incidents') return openIncidents
    return 0
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Administración"
        subtitle="Verificación de cuidadores, resolución de disputas, registro de incidencias y cuentas."
      />

      <nav className="flex flex-wrap gap-1 border-b border-sand-200">
        {(Object.keys(tabLabels) as Tab[]).map((candidate) => (
          <button
            key={candidate}
            type="button"
            onClick={() => setTab(candidate)}
            className={`-mb-px flex items-center gap-2 border-b-2 px-4 py-2.5 text-sm font-medium transition ${
              tab === candidate
                ? 'border-brand-600 text-brand-700'
                : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            {tabLabels[candidate]}
            {badgeFor(candidate) > 0 && (
              <Badge tone={candidate === 'sitters' ? 'warning' : 'danger'}>{badgeFor(candidate)}</Badge>
            )}
          </button>
        ))}
      </nav>

      {tab === 'sitters' && <SittersSection onAction={run} />}
      {tab === 'clients' && (
        <UsersSection
          role="ROLE_CLIENT"
          emptyDescription="Ningún cliente coincide con estos filtros."
          onAction={run}
        />
      )}
      {tab === 'users' && (
        <UsersSection
          emptyDescription="Ninguna cuenta coincide con estos filtros."
          onAction={run}
        />
      )}
      {tab === 'incidents' && <IncidentsSection users={users} onAction={run} />}

      <section className={`flex-col gap-3 ${tab === 'disputes' ? 'flex' : 'hidden'}`}>
        {disputes === null && <Skeleton className="h-24 w-full" />}
        {disputes?.length === 0 && (
          <EmptyState
            title="Sin disputas"
            description="Ninguna reserva está en disputa ahora mismo."
          />
        )}
        {disputes?.map((booking) => (
          <DisputeCard
            key={booking.bookingId}
            booking={booking}
            onResolve={(request) =>
              run(() => resolveDispute(booking.bookingId, request), 'Disputa resuelta')
            }
          />
        ))}

        {/* Herramienta de reparación, no de uso diario: la proyección del
            buscador puede quedarse desincronizada de las reseñas reales y esto
            la reconstruye. Republica valores absolutos, así que pulsarla de más
            no infla la nota de nadie. */}
        <Card className="mt-4">
          <h3 className="font-semibold text-slate-800">Reconstruir reputaciones</h3>
          <p className="mt-1 text-sm text-slate-600">
            Recalcula en el buscador la nota media de todos los cuidadores a partir de las reseñas
            reales. Úsalo si el buscador muestra valoraciones que no cuadran.
          </p>
          <Button
            variant="secondary"
            className="mt-3"
            onClick={() =>
              void run(async () => {
                const sitters = await reprojectReputations()
                return sitters
              }, 'Reputaciones reconstruidas')
            }
          >
            Reconstruir
          </Button>
        </Card>
      </section>
    </div>
  )
}

function DisputeCard({
  booking,
  onResolve,
}: {
  booking: BookingDetail
  onResolve: (request: {
    resolution: DisputeResolution
    refundToClient?: number
    notes: string
  }) => Promise<void>
}) {
  const [resolution, setResolution] = useState<DisputeResolution>('FAVOR_CLIENT')
  const [refund, setRefund] = useState('')
  const [notes, setNotes] = useState('')

  const isSplit = resolution === 'SPLIT'
  const canSubmit = notes.trim().length > 0 && (!isSplit || refund.trim().length > 0)

  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-semibold text-slate-800">
            Reserva del {formatDateRange(booking.startDate, booking.endDate)}
          </p>
          <p className="text-xs text-slate-400">Id de reserva: {booking.bookingId}</p>
          <p className="mt-1 text-sm text-slate-600">
            Importe en custodia:{' '}
            <span className="font-semibold tabular-nums text-slate-800">
              {formatMoney(booking.finalAmountToPay, booking.currency)}
            </span>
          </p>
          {booking.disputeReason && (
            <p className="mt-1 text-sm text-slate-700">Motivo: {booking.disputeReason}</p>
          )}
        </div>
        <Badge tone="danger">En disputa</Badge>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Resolución" htmlFor={`resolution-${booking.bookingId}`}>
          <Select
            id={`resolution-${booking.bookingId}`}
            value={resolution}
            onChange={(event) => setResolution(event.target.value as DisputeResolution)}
          >
            {Object.entries(resolutionLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </Field>

        {isSplit && (
          <Field
            label="Reembolso al cliente"
            htmlFor={`refund-${booking.bookingId}`}
            hint={`Entre 0 y ${booking.finalAmountToPay.toFixed(2)} ${booking.currency}`}
          >
            <Input
              id={`refund-${booking.bookingId}`}
              type="number"
              min={0}
              max={booking.finalAmountToPay}
              step="0.01"
              value={refund}
              onChange={(event) => setRefund(event.target.value)}
            />
          </Field>
        )}
      </div>

      <Field
        label="Motivo de la resolución"
        htmlFor={`notes-${booking.bookingId}`}
        hint="Queda registrado junto a la reserva; ambas partes reciben el aviso."
      >
        <Input
          id={`notes-${booking.bookingId}`}
          value={notes}
          onChange={(event) => setNotes(event.target.value)}
          placeholder="Informe veterinario aportado por el cliente..."
        />
      </Field>

      <div>
        <Button
          disabled={!canSubmit}
          onClick={() =>
            void onResolve({
              resolution,
              refundToClient: isSplit ? Number(refund) : undefined,
              notes,
            })
          }
        >
          Resolver incidencia
        </Button>
      </div>
    </Card>
  )
}
