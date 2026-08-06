import { useCallback, useEffect, useState } from 'react'
import {
  approveSitter,
  deactivateUser,
  listSitters,
  reactivateUser,
  rejectSitter,
} from '@/modules/admin/adminApi'
import { SitterDossierModal } from '@/modules/admin/SitterDossierModal'
import { Pagination } from '@/modules/admin/Pagination'
import type { AdminSitter, PageResponse, VerificationStatus } from '@/core/api/types'
import { Badge, Button, Card, EmptyState, Field, Input, Select, Skeleton } from '@/shared/components/ui'

const statusLabels: Record<VerificationStatus, string> = {
  PENDIENTE_VERIFICACION: 'Pendiente',
  VERIFICADO: 'Verificado',
  RECHAZADO: 'Rechazado',
}

const statusTones: Record<VerificationStatus, 'warning' | 'success' | 'danger'> = {
  PENDIENTE_VERIFICACION: 'warning',
  VERIFICADO: 'success',
  RECHAZADO: 'danger',
}

/**
 * Listado de cuidadores del back-office: buscar, filtrar por estado de KYC, ver
 * la ficha completa, aprobar o rechazar la verificación y expulsar o readmitir.
 *
 * <p>Arranca filtrado por «Pendiente» porque es el trabajo que genera cola: lo
 * primero que un administrador quiere ver al entrar es qué tiene por revisar.
 */
export function SittersSection({
  onAction,
}: {
  onAction: (action: () => Promise<unknown>, message: string) => Promise<void>
}) {
  const [result, setResult] = useState<PageResponse<AdminSitter> | null>(null)
  const [status, setStatus] = useState<VerificationStatus | 'TODOS'>('PENDIENTE_VERIFICACION')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [detailFor, setDetailFor] = useState<string | null>(null)

  const refresh = useCallback(() => {
    listSitters({
      status: status === 'TODOS' ? undefined : status,
      query: query.trim() || undefined,
      page,
    })
      .then(setResult)
      .catch(() => setResult(null))
  }, [status, query, page])

  useEffect(refresh, [refresh])

  // Cambiar un filtro con la página 5 abierta dejaría un listado vacío sin
  // explicación: cualquier filtro nuevo vuelve al principio.
  const changeFilter = (apply: () => void) => {
    apply()
    setPage(0)
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Estado de verificación" htmlFor="sitterStatus">
          <Select
            id="sitterStatus"
            value={status}
            onChange={(event) =>
              changeFilter(() => setStatus(event.target.value as VerificationStatus | 'TODOS'))
            }
          >
            <option value="PENDIENTE_VERIFICACION">Pendientes de verificar</option>
            <option value="VERIFICADO">Verificados</option>
            <option value="RECHAZADO">Rechazados</option>
            <option value="TODOS">Todos</option>
          </Select>
        </Field>
        <Field label="Buscar" htmlFor="sitterQuery" hint="Por nombre, apellidos o correo.">
          <Input
            id="sitterQuery"
            value={query}
            onChange={(event) => changeFilter(() => setQuery(event.target.value))}
            placeholder="ana@..., García..."
          />
        </Field>
      </div>

      {result === null && <Skeleton className="h-24 w-full" />}
      {result?.content.length === 0 && (
        <EmptyState
          title="Sin resultados"
          description="Ningún cuidador coincide con estos filtros."
        />
      )}

      {result?.content.map((sitter) => (
        <SitterRow
          key={sitter.sitterId}
          sitter={sitter}
          onDetails={() => setDetailFor(sitter.sitterId)}
          onApprove={() => onAction(() => approveSitter(sitter.sitterId), 'Cuidador verificado').then(refresh)}
          onReject={(reason) =>
            onAction(() => rejectSitter(sitter.sitterId, reason), 'Verificación rechazada').then(refresh)
          }
          onToggleAccount={() =>
            onAction(
              () =>
                sitter.accountActive
                  ? deactivateUser(sitter.sitterId)
                  : reactivateUser(sitter.sitterId),
              sitter.accountActive ? 'Cuenta expulsada' : 'Cuenta readmitida',
            ).then(refresh)
          }
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

      {detailFor && (
        <SitterDossierModal sitterId={detailFor} onClose={() => setDetailFor(null)} />
      )}
    </div>
  )
}

function SitterRow({
  sitter,
  onDetails,
  onApprove,
  onReject,
  onToggleAccount,
}: {
  sitter: AdminSitter
  onDetails: () => void
  onApprove: () => Promise<void>
  onReject: (reason: string) => Promise<void>
  onToggleAccount: () => Promise<void>
}) {
  const [reason, setReason] = useState('')
  const [rejecting, setRejecting] = useState(false)
  const isPending = sitter.verificationStatus === 'PENDIENTE_VERIFICACION'

  return (
    <Card className="flex flex-col gap-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          {sitter.photoUrl ? (
            <img
              src={sitter.photoUrl}
              alt={sitter.firstName ?? 'Cuidador'}
              className="size-12 shrink-0 rounded-full object-cover"
            />
          ) : (
            <div className="flex size-12 shrink-0 items-center justify-center rounded-full bg-sand-100 font-semibold text-slate-400">
              {(sitter.firstName ?? '?').charAt(0).toUpperCase()}
            </div>
          )}
          <div>
            <p className="font-semibold text-slate-800">
              {sitter.firstName} {sitter.lastName}
            </p>
            <p className="text-sm text-slate-500">{sitter.email}</p>
            <div className="mt-1 flex flex-wrap gap-1.5">
              <Badge tone={statusTones[sitter.verificationStatus]}>
                {statusLabels[sitter.verificationStatus]}
              </Badge>
              {!sitter.accountActive && <Badge tone="danger">Expulsada</Badge>}
              {sitter.identityDocumentReference && <Badge tone="info">Documento subido</Badge>}
            </div>
          </div>
        </div>

        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={onDetails}>
            Ver detalles
          </Button>
          {isPending && <Button onClick={() => void onApprove()}>Aprobar</Button>}
          {isPending && (
            <Button variant="secondary" onClick={() => setRejecting((current) => !current)}>
              Rechazar
            </Button>
          )}
          <Button variant={sitter.accountActive ? 'danger' : 'primary'} onClick={() => void onToggleAccount()}>
            {sitter.accountActive ? 'Expulsar' : 'Readmitir'}
          </Button>
        </div>
      </div>

      {/* El motivo solo aparece al pulsar "Rechazar": es obligatorio, pero tenerlo
          siempre visible sugiere que rechazar es la acción por defecto. */}
      {rejecting && (
        <div className="flex flex-wrap items-end gap-2 border-t border-sand-100 pt-3">
          <div className="min-w-56 flex-1">
            <Field label="Motivo del rechazo" htmlFor={`reject-${sitter.sitterId}`}>
              <Input
                id={`reject-${sitter.sitterId}`}
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                placeholder="Documento ilegible, datos que no coinciden..."
              />
            </Field>
          </div>
          <Button
            variant="danger"
            disabled={reason.trim().length === 0}
            onClick={() => void onReject(reason)}
          >
            Confirmar rechazo
          </Button>
        </div>
      )}
    </Card>
  )
}
