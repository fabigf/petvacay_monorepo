import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { closeIncident, listIncidents } from '@/modules/admin/adminApi'
import { Pagination } from '@/modules/admin/Pagination'
import type {
  Incident,
  IncidentStatus,
  IncidentType,
  PageResponse,
  UserProfile,
} from '@/core/api/types'
import { Badge, Button, Card, EmptyState, Field, Input, Select, Skeleton } from '@/shared/components/ui'

const typeLabels: Record<IncidentType, string> = {
  DISPUTA: 'Disputa',
  ALERTA_MEDICA: 'Alerta médica',
}

/**
 * Registro de incidencias: qué ha pasado en cada reserva y a quién afecta.
 *
 * <p>Los nombres de cliente y cuidador se resuelven contra el listado de
 * usuarios que ya carga el panel, en lugar de pedirle a booking-service que los
 * incluya: ese servicio no conoce las cuentas — viven en identity-service — y
 * hacerle preguntar por cada fila sería una llamada por incidencia.
 */
export function IncidentsSection({
  users,
  onAction,
}: {
  users: UserProfile[]
  onAction: (action: () => Promise<unknown>, message: string) => Promise<void>
}) {
  const [result, setResult] = useState<PageResponse<Incident> | null>(null)
  const [status, setStatus] = useState<IncidentStatus | 'TODAS'>('ABIERTA')
  const [type, setType] = useState<IncidentType | 'TODOS'>('TODOS')
  const [page, setPage] = useState(0)

  const refresh = useCallback(() => {
    listIncidents({
      status: status === 'TODAS' ? undefined : status,
      type: type === 'TODOS' ? undefined : type,
      page,
    })
      .then(setResult)
      .catch(() => setResult(null))
  }, [status, type, page])

  useEffect(refresh, [refresh])

  // Cambiar un filtro con la página 5 abierta dejaría un listado vacío sin
  // explicación: cualquier filtro nuevo vuelve al principio.
  const changeFilter = (apply: () => void) => {
    apply()
    setPage(0)
  }

  const nameOf = (userId: string) => {
    const user = users.find((candidate) => candidate.userId === userId)
    // Sin nombre se enseña el identificador: es feo, pero es peor una fila vacía
    // que no dice a quién afecta la incidencia.
    return user ? `${user.firstName} ${user.lastName}` : userId
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Estado" htmlFor="incidentStatus">
          <Select
            id="incidentStatus"
            value={status}
            onChange={(event) =>
              changeFilter(() => setStatus(event.target.value as IncidentStatus | 'TODAS'))
            }
          >
            <option value="ABIERTA">Por atender</option>
            <option value="RESUELTA">Atendidas</option>
            <option value="TODAS">Todas (histórico)</option>
          </Select>
        </Field>
        <Field label="Tipo" htmlFor="incidentType">
          <Select
            id="incidentType"
            value={type}
            onChange={(event) =>
              changeFilter(() => setType(event.target.value as IncidentType | 'TODOS'))
            }
          >
            <option value="TODOS">Todos</option>
            <option value="DISPUTA">Disputas</option>
            <option value="ALERTA_MEDICA">Alertas médicas</option>
          </Select>
        </Field>
      </div>

      {result === null && <Skeleton className="h-24 w-full" />}
      {result?.content.length === 0 && (
        <EmptyState
          title="Sin incidencias"
          description="No hay ninguna incidencia registrada con estos filtros."
        />
      )}

      {result?.content.map((incident) => (
        <IncidentCard
          key={incident.incidentId}
          incident={incident}
          clientName={nameOf(incident.clientId)}
          sitterName={nameOf(incident.sitterId)}
          onClose={async (notes) => {
            await onAction(() => closeIncident(incident.incidentId, notes), 'Incidencia atendida')
            refresh()
          }}
        />
      ))}

      {result && (
        <Pagination
          page={result.page}
          totalPages={result.totalPages}
          totalElements={result.totalElements}
          onChange={setPage}
        />
      )}
    </div>
  )
}

function IncidentCard({
  incident,
  clientName,
  sitterName,
  onClose,
}: {
  incident: Incident
  clientName: string
  sitterName: string
  onClose: (notes: string) => Promise<void>
}) {
  const [notes, setNotes] = useState('')
  const isOpen = incident.status === 'ABIERTA'

  return (
    <Card className="flex flex-col gap-2">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-semibold text-slate-800">{typeLabels[incident.type]}</span>
            <Badge tone={isOpen ? 'danger' : 'success'}>
              {isOpen ? 'Por atender' : 'Atendida'}
            </Badge>
            <span className="text-xs text-slate-400">
              {new Date(incident.openedAt).toLocaleString('es-ES')}
            </span>
          </div>
          <p className="mt-1 text-sm text-slate-600">
            Cliente: <span className="font-medium text-slate-800">{clientName}</span> · Cuidador:{' '}
            <span className="font-medium text-slate-800">{sitterName}</span>
          </p>
          <p className="text-xs text-slate-400">
            Reserva:{' '}
            <Link to={`/bookings/${incident.bookingId}`} className="underline hover:text-brand-700">
              {incident.bookingId}
            </Link>
          </p>
        </div>
      </div>

      {incident.description && <p className="text-sm text-slate-700">{incident.description}</p>}

      {incident.resolutionNotes && (
        <p className="text-sm text-slate-600">
          <span className="font-medium">Resolución:</span> {incident.resolutionNotes}
        </p>
      )}

      {/* Las disputas se cierran solas al dictarse su resolución en la pestaña de
          incidencias económicas; aquí solo se cierran a mano las alertas médicas,
          que no tienen ninguna transición de reserva que las cierre. */}
      {isOpen && incident.type === 'ALERTA_MEDICA' && (
        <div className="flex flex-wrap items-end gap-2">
          <div className="min-w-56 flex-1">
            <Field label="Cómo se ha atendido" htmlFor={`notes-${incident.incidentId}`}>
              <Input
                id={`notes-${incident.incidentId}`}
                value={notes}
                onChange={(event) => setNotes(event.target.value)}
                placeholder="Se coordinó veterinario de urgencia..."
              />
            </Field>
          </div>
          <Button disabled={notes.trim().length === 0} onClick={() => void onClose(notes)}>
            Marcar como atendida
          </Button>
        </div>
      )}

      {isOpen && incident.type === 'DISPUTA' && (
        <p className="text-xs text-slate-500">
          Se cerrará automáticamente al resolver la disputa desde «Disputas».
        </p>
      )}
    </Card>
  )
}
