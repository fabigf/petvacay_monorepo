import { useEffect, useState } from 'react'
import { getSitterDossier } from '@/modules/admin/adminApi'
import type { AdminSitter, HomeType, ServiceType, Species, VerificationStatus } from '@/core/api/types'
import { Badge, Button, Skeleton } from '@/shared/components/ui'

const homeTypeLabels: Record<HomeType, string> = {
  APARTMENT: 'Apartamento',
  HOUSE_GARDEN: 'Casa con jardín',
  RURAL_HOUSE: 'Casa rural',
}

const serviceTypeLabels: Record<ServiceType, string> = {
  NIGHT_ACCOMMODATION: 'Alojamiento nocturno',
  DAY_CARE: 'Guardería de día',
  WALKING: 'Paseos por horas',
}

const speciesLabels: Record<Species, string> = {
  DOG: 'Perros',
  CAT: 'Gatos',
  RABBIT: 'Conejos',
  BIRD: 'Aves',
  OTHER: 'Otros',
}

const statusLabels: Record<VerificationStatus, string> = {
  PENDIENTE_VERIFICACION: 'Pendiente de verificar',
  VERIFICADO: 'Verificado',
  RECHAZADO: 'Rechazado',
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4 border-b border-sand-100 py-1.5 text-sm last:border-0">
      <span className="text-slate-500">{label}</span>
      <span className="text-right font-medium text-slate-800">{value}</span>
    </div>
  )
}

/**
 * Ficha completa de un cuidador para el equipo de administración.
 *
 * <p>Reúne en una sola vista lo que hay que contrastar para verificar un KYC: el
 * documento subido, la persona real que hay detrás de la cuenta y el perfil que
 * quiere publicar. Antes esa información estaba repartida y el panel solo
 * mostraba el nombre y la descripción, que no bastan para decidir nada.
 */
export function SitterDossierModal({
  sitterId,
  onClose,
}: {
  sitterId: string
  onClose: () => void
}) {
  const [sitter, setSitter] = useState<AdminSitter | null>(null)

  useEffect(() => {
    let cancelled = false
    getSitterDossier(sitterId)
      .then((dossier) => {
        if (!cancelled) setSitter(dossier)
      })
      .catch(() => {
        if (!cancelled) onClose()
      })
    return () => {
      cancelled = true
    }
  }, [sitterId, onClose])

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/40 p-4 sm:items-center"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Ficha del cuidador"
        className="flex max-h-[85vh] w-full max-w-2xl flex-col overflow-hidden rounded-xl bg-white shadow-xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-sand-200 px-5 py-4">
          <h2 className="text-base font-semibold text-slate-800">Ficha del cuidador</h2>
          <Button variant="ghost" onClick={onClose}>
            Cerrar
          </Button>
        </div>

        <div className="overflow-y-auto px-5 py-4">
          {sitter === null ? (
            <div className="flex flex-col gap-3">
              <Skeleton className="h-20 w-full" />
              <Skeleton className="h-40 w-full" />
            </div>
          ) : (
            <div className="flex flex-col gap-5">
              <div className="flex items-start gap-4">
                {sitter.photoUrl ? (
                  <img
                    src={sitter.photoUrl}
                    alt={sitter.firstName ?? 'Cuidador'}
                    className="size-20 shrink-0 rounded-full object-cover"
                  />
                ) : (
                  <div className="flex size-20 shrink-0 items-center justify-center rounded-full bg-sand-100 text-2xl font-semibold text-slate-400">
                    {(sitter.firstName ?? '?').charAt(0).toUpperCase()}
                  </div>
                )}
                <div className="flex-1">
                  <p className="text-lg font-semibold text-slate-800">
                    {sitter.firstName} {sitter.lastName}
                  </p>
                  <p className="text-sm text-slate-600">{sitter.email}</p>
                  <div className="mt-1.5 flex flex-wrap gap-1.5">
                    <Badge
                      tone={
                        sitter.verificationStatus === 'VERIFICADO'
                          ? 'success'
                          : sitter.verificationStatus === 'RECHAZADO'
                            ? 'danger'
                            : 'warning'
                      }
                    >
                      {statusLabels[sitter.verificationStatus]}
                    </Badge>
                    {!sitter.accountActive && <Badge tone="danger">Cuenta expulsada</Badge>}
                    {sitter.searchable && <Badge tone="info">Visible en el buscador</Badge>}
                  </div>
                </div>
              </div>

              <section>
                <h3 className="mb-1 text-sm font-semibold text-slate-700">Verificación de identidad</h3>
                {sitter.identityDocumentReference ? (
                  <Row label="Documento subido" value={sitter.identityDocumentReference} />
                ) : (
                  <p className="text-sm text-slate-500">
                    Todavía no ha subido ningún documento de identidad.
                  </p>
                )}
                {sitter.birthDate && (
                  <Row
                    label="Fecha de nacimiento"
                    value={new Date(sitter.birthDate).toLocaleDateString('es-ES')}
                  />
                )}
                {sitter.registeredAt && (
                  <Row
                    label="Registrado el"
                    value={new Date(sitter.registeredAt).toLocaleDateString('es-ES')}
                  />
                )}
                {sitter.verifiedAt && (
                  <Row
                    label="Verificado el"
                    value={new Date(sitter.verifiedAt).toLocaleDateString('es-ES')}
                  />
                )}
                {sitter.rejectionReason && (
                  <Row label="Motivo del rechazo" value={sitter.rejectionReason} />
                )}
              </section>

              <section>
                <h3 className="mb-1 text-sm font-semibold text-slate-700">Alojamiento y ubicación</h3>
                <Row label="Tipo de vivienda" value={homeTypeLabels[sitter.homeType]} />
                {sitter.fullAddress && <Row label="Dirección" value={sitter.fullAddress} />}
                <Row
                  label="Coordenadas"
                  value={`${sitter.latitude.toFixed(5)}, ${sitter.longitude.toFixed(5)}`}
                />
                {/* Enlace al mapa en vez de incrustarlo: verificar dónde vive
                    alguien se hace mirando el sitio de verdad, y así el panel no
                    depende de ninguna clave de API de mapas. */}
                <a
                  className="mt-1 inline-block text-sm text-brand-700 underline hover:text-brand-800"
                  href={`https://www.openstreetmap.org/?mlat=${sitter.latitude}&mlon=${sitter.longitude}#map=16/${sitter.latitude}/${sitter.longitude}`}
                  target="_blank"
                  rel="noreferrer"
                >
                  Ver ubicación en el mapa
                </a>
              </section>

              <section>
                <h3 className="mb-1 text-sm font-semibold text-slate-700">Servicios y capacidad</h3>
                {Object.entries(sitter.servicePrices).map(([type, price]) => (
                  <Row
                    key={type}
                    label={serviceTypeLabels[type as ServiceType]}
                    value={`${Number(price).toFixed(2)} ${sitter.currency === 'EUR' ? '€' : sitter.currency}`}
                  />
                ))}
                <Row
                  label="Mascotas simultáneas"
                  value={String(sitter.maxSimultaneousPets)}
                />
                <Row
                  label="Especies aceptadas"
                  value={sitter.acceptedSpecies.map((species) => speciesLabels[species]).join(', ')}
                />
                {sitter.maxPetWeightKg != null && (
                  <Row label="Peso máximo" value={`${sitter.maxPetWeightKg} kg`} />
                )}
              </section>

              {sitter.description && (
                <section>
                  <h3 className="mb-1 text-sm font-semibold text-slate-700">Descripción</h3>
                  <p className="text-sm text-slate-700">{sitter.description}</p>
                </section>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
