import { useCallback, useEffect, useState } from 'react'
import { deactivateUser, listUsers, reactivateUser, updateUser } from '@/modules/admin/adminApi'
import { Pagination } from '@/modules/admin/Pagination'
import type { PageResponse, Role, UserProfile } from '@/core/api/types'
import {
  Badge,
  Button,
  Card,
  EmptyState,
  Field,
  Input,
  Select,
  Skeleton,
} from '@/shared/components/ui'

const roleLabels: Record<Role, string> = {
  ROLE_CLIENT: 'Cliente',
  ROLE_SITTER: 'Cuidador',
  ROLE_ADMIN: 'Administración',
}

/**
 * Gestión de cuentas: buscar, corregir datos de contacto y expulsar o readmitir.
 *
 * <p>La expulsión es un borrado lógico y por eso es reversible: las reservas
 * pasadas de esa persona, su dinero y sus reseñas siguen existiendo, así que
 * borrarla de verdad dejaría huérfano medio historial de la plataforma.
 */
export function UsersSection({
  role,
  emptyDescription,
  onAction,
}: {
  /** Rol al que se acota el listado; sin él se muestran todas las cuentas. */
  role?: Role
  emptyDescription: string
  onAction: (action: () => Promise<unknown>, message: string) => Promise<void>
}) {
  const [result, setResult] = useState<PageResponse<UserProfile> | null>(null)
  const [query, setQuery] = useState('')
  const [active, setActive] = useState<'TODAS' | 'ACTIVAS' | 'EXPULSADAS'>('TODAS')
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<string | null>(null)

  const refresh = useCallback(() => {
    listUsers({
      query: query.trim() || undefined,
      role,
      active: active === 'TODAS' ? undefined : active === 'ACTIVAS',
      page,
    })
      .then(setResult)
      .catch(() => setResult(null))
  }, [query, role, active, page])

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
        <Field label="Buscar" htmlFor="userQuery" hint="Por nombre, apellidos o correo electrónico.">
          <Input
            id="userQuery"
            value={query}
            onChange={(event) => changeFilter(() => setQuery(event.target.value))}
            placeholder="ana@..., García..."
          />
        </Field>
        <Field label="Estado de la cuenta" htmlFor="userActive">
          <Select
            id="userActive"
            value={active}
            onChange={(event) =>
              changeFilter(() => setActive(event.target.value as 'TODAS' | 'ACTIVAS' | 'EXPULSADAS'))
            }
          >
            <option value="TODAS">Todas</option>
            <option value="ACTIVAS">Activas</option>
            <option value="EXPULSADAS">Expulsadas</option>
          </Select>
        </Field>
      </div>

      {result === null && <Skeleton className="h-24 w-full" />}
      {result?.content.length === 0 && (
        <EmptyState title="Sin resultados" description={emptyDescription} />
      )}

      {result?.content.map((user) =>
        editing === user.userId ? (
          <EditUserCard
            key={user.userId}
            user={user}
            onCancel={() => setEditing(null)}
            onSave={async (request) => {
              await onAction(() => updateUser(user.userId, request), 'Datos actualizados')
              setEditing(null)
              refresh()
            }}
          />
        ) : (
          <Card key={user.userId} className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="font-semibold text-slate-800">
                {user.firstName} {user.lastName}
              </p>
              <p className="text-sm text-slate-500">{user.email}</p>
              <div className="mt-1 flex flex-wrap gap-1.5">
                {user.roles.map((role) => (
                  <Badge key={role} tone={role === 'ROLE_ADMIN' ? 'info' : 'neutral'}>
                    {roleLabels[role]}
                  </Badge>
                ))}
                {!user.active && <Badge tone="danger">Expulsada</Badge>}
              </div>
            </div>

            <div className="flex flex-wrap gap-2">
              <Button variant="secondary" onClick={() => setEditing(user.userId)}>
                Editar
              </Button>
              {user.active ? (
                <Button
                  variant="danger"
                  onClick={() =>
                    void onAction(() => deactivateUser(user.userId), 'Cuenta expulsada').then(refresh)
                  }
                >
                  Expulsar
                </Button>
              ) : (
                <Button
                  onClick={() =>
                    void onAction(() => reactivateUser(user.userId), 'Cuenta readmitida').then(refresh)
                  }
                >
                  Readmitir
                </Button>
              )}
            </div>
          </Card>
        ),
      )}

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

function EditUserCard({
  user,
  onSave,
  onCancel,
}: {
  user: UserProfile
  onSave: (request: { email: string; firstName: string; lastName: string }) => Promise<void>
  onCancel: () => void
}) {
  const [email, setEmail] = useState(user.email)
  const [firstName, setFirstName] = useState(user.firstName)
  const [lastName, setLastName] = useState(user.lastName)

  return (
    <Card className="flex flex-col gap-3">
      <div className="grid gap-3 sm:grid-cols-3">
        <Field label="Nombre" htmlFor={`first-${user.userId}`}>
          <Input
            id={`first-${user.userId}`}
            value={firstName}
            onChange={(event) => setFirstName(event.target.value)}
          />
        </Field>
        <Field label="Apellidos" htmlFor={`last-${user.userId}`}>
          <Input
            id={`last-${user.userId}`}
            value={lastName}
            onChange={(event) => setLastName(event.target.value)}
          />
        </Field>
        <Field
          label="Correo electrónico"
          htmlFor={`email-${user.userId}`}
          hint="Es la credencial con la que inicia sesión."
        >
          <Input
            id={`email-${user.userId}`}
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </Field>
      </div>

      <div className="flex gap-2">
        <Button onClick={() => void onSave({ email, firstName, lastName })}>Guardar</Button>
        <Button variant="ghost" onClick={onCancel}>
          Cancelar
        </Button>
      </div>
    </Card>
  )
}
