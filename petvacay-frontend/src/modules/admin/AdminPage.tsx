import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import {
  approveSitter,
  deactivateUser,
  listBookingsByStatus,
  listPendingSitters,
  rejectSitter,
  resolveDispute,
} from '@/modules/admin/adminApi'
import type { BookingDetail, DisputeResolution, SitterProfile } from '@/core/api/types'
import { Badge, Button, Card, EmptyState, Field, Input, Select, Skeleton } from '@/shared/components/ui'
import { useProblemToast } from '@/shared/hooks/useProblemToast'

const resolutionLabels: Record<DisputeResolution, string> = {
  FAVOR_CLIENT: 'A favor del cliente (reembolso íntegro)',
  FAVOR_SITTER: 'A favor del cuidador (liquidación normal)',
  SPLIT: 'Reparto intermedio',
}

/**
 * Panel de administración (negocio.md 2.3).
 *
 * <p>Reúne las tres competencias del administrador que ya tienen backend:
 * verificar cuidadores, resolver incidencias y expulsar cuentas. Hasta ahora
 * todas ellas solo se podían ejercer con `curl` y el token del administrador.
 *
 * <p>La ruta está protegida por `ROLE_ADMIN` en `AppRouter`, pero eso es solo
 * experiencia de usuario: cada endpoint vuelve a exigir el rol en el servidor.
 */
export function AdminPage() {
  const handleProblem = useProblemToast()
  const [pendingSitters, setPendingSitters] = useState<SitterProfile[] | null>(null)
  const [disputes, setDisputes] = useState<BookingDetail[] | null>(null)

  const refresh = useCallback(() => {
    listPendingSitters().then(setPendingSitters).catch(() => setPendingSitters([]))
    listBookingsByStatus('EN_DISPUTA').then(setDisputes).catch(() => setDisputes([]))
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

  return (
    <div className="flex flex-col gap-8">
      <header>
        <h1 className="text-2xl font-bold text-slate-800">Administración</h1>
        <p className="mt-1 text-sm text-slate-500">
          Verificación de cuidadores, incidencias abiertas y expulsión de cuentas.
        </p>
      </header>

      <section className="flex flex-col gap-3">
        <h2 className="text-lg font-semibold text-slate-800">
          Cuidadores pendientes de verificar
          {pendingSitters && pendingSitters.length > 0 && (
            <Badge tone="warning" className="ml-2">
              {pendingSitters.length}
            </Badge>
          )}
        </h2>

        {pendingSitters === null && <Skeleton className="h-24 w-full" />}
        {pendingSitters?.length === 0 && (
          <EmptyState title="Nada pendiente" description="No hay cuidadores esperando verificación." />
        )}
        {pendingSitters?.map((sitter) => (
          <PendingSitterCard
            key={sitter.sitterId}
            sitter={sitter}
            onApprove={() => run(() => approveSitter(sitter.sitterId), 'Cuidador verificado')}
            onReject={(reason) =>
              run(() => rejectSitter(sitter.sitterId, reason), 'Cuidador rechazado')
            }
            onDeactivate={() =>
              run(() => deactivateUser(sitter.sitterId), 'Cuenta desactivada')
            }
          />
        ))}
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="text-lg font-semibold text-slate-800">
          Incidencias por resolver
          {disputes && disputes.length > 0 && (
            <Badge tone="danger" className="ml-2">
              {disputes.length}
            </Badge>
          )}
        </h2>

        {disputes === null && <Skeleton className="h-24 w-full" />}
        {disputes?.length === 0 && (
          <EmptyState
            title="Sin incidencias"
            description="Ninguna reserva está en disputa ahora mismo."
          />
        )}
        {disputes?.map((booking) => (
          <DisputeCard
            key={booking.bookingId}
            booking={booking}
            onResolve={(request) =>
              run(() => resolveDispute(booking.bookingId, request), 'Incidencia resuelta')
            }
          />
        ))}
      </section>
    </div>
  )
}

function PendingSitterCard({
  sitter,
  onApprove,
  onReject,
  onDeactivate,
}: {
  sitter: SitterProfile
  onApprove: () => Promise<void>
  onReject: (reason: string) => Promise<void>
  onDeactivate: () => Promise<void>
}) {
  const [reason, setReason] = useState('')

  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-semibold text-slate-800">{sitter.firstName ?? 'Cuidador'}</p>
          <p className="text-xs text-slate-400">{sitter.sitterId}</p>
          {sitter.description && <p className="mt-1 text-sm text-slate-600">{sitter.description}</p>}
        </div>
        <Badge tone="warning">Pendiente</Badge>
      </div>

      <div className="flex flex-wrap items-end gap-2">
        <div className="min-w-56 flex-1">
          <Field label="Motivo del rechazo" htmlFor={`reason-${sitter.sitterId}`}>
            <Input
              id={`reason-${sitter.sitterId}`}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              placeholder="Documento ilegible, datos que no coinciden..."
            />
          </Field>
        </div>
        <Button onClick={() => void onApprove()}>Aprobar</Button>
        <Button
          variant="secondary"
          disabled={reason.trim().length === 0}
          onClick={() => void onReject(reason)}
        >
          Rechazar
        </Button>
        {/* Expulsar no es lo mismo que rechazar el KYC: rechazar deja la cuenta
            viva para reintentar el proceso, expulsar la cierra por completo. */}
        <Button variant="danger" onClick={() => void onDeactivate()}>
          Expulsar cuenta
        </Button>
      </div>
    </Card>
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
            Reserva del {booking.startDate} al {booking.endDate}
          </p>
          <p className="text-xs text-slate-400">Id de reserva: {booking.bookingId}</p>
          <p className="mt-1 text-sm text-slate-600">
            Importe en custodia: {booking.finalAmountToPay.toFixed(2)} {booking.currency}
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
