import { useEffect, useState } from 'react'
import { listReviewsForSitter } from '@/modules/reviews/reviewsApi'
import type { Review } from '@/core/api/types'
import { Button, EmptyState, Skeleton } from '@/shared/components/ui'
import { StarRating } from '@/shared/components/StarRating'

/**
 * Reseñas públicas de un cuidador en una ventana modal.
 *
 * Se lee desde la tarjeta del buscador: hasta ahora la única forma de leer los
 * comentarios era entrar en el formulario de reserva, es decir, había que
 * empezar a reservar para poder decidir si reservabas.
 *
 * Descarta las que el propio cuidador escribió sobre sus clientes: las reseñas
 * son cruzadas y esas no hablan de él. Sí entran las del sistema — la que deja
 * automáticamente una cancelación suya (negocio.md 4.3) — porque son
 * exactamente lo que un cliente necesita saber antes de reservar.
 */
export function SitterReviewsModal({
  sitterId,
  sitterName,
  onClose,
}: {
  sitterId: string
  sitterName: string
  onClose: () => void
}) {
  const [reviews, setReviews] = useState<Review[] | null>(null)

  useEffect(() => {
    let cancelled = false
    listReviewsForSitter(sitterId)
      .then((all) => {
        if (!cancelled) setReviews(all.filter((review) => review.authorRole !== 'SITTER'))
      })
      .catch(() => {
        if (!cancelled) setReviews([])
      })
    return () => {
      cancelled = true
    }
  }, [sitterId])

  // Escape cierra el diálogo: es lo que espera cualquiera que lo abra sin ratón.
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
        aria-label={`Reseñas de ${sitterName}`}
        className="flex max-h-[80vh] w-full max-w-lg flex-col overflow-hidden rounded-xl bg-white shadow-xl"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-sand-200 px-5 py-4">
          <h2 className="text-base font-semibold text-slate-800">Reseñas de {sitterName}</h2>
          <Button variant="ghost" onClick={onClose} aria-label="Cerrar reseñas">
            Cerrar
          </Button>
        </div>

        <div className="flex flex-col gap-4 overflow-y-auto px-5 py-4">
          {reviews === null && (
            <>
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </>
          )}

          {reviews !== null && reviews.length === 0 && (
            <EmptyState
              title="Sin reseñas todavía"
              description="Este cuidador aún no ha recibido valoraciones de sus clientes."
            />
          )}

          {reviews?.map((review) => (
            <div key={review.reviewId} className="border-b border-sand-100 pb-3 last:border-0 last:pb-0">
              <div className="flex items-center gap-2">
                <StarRating value={review.rating} size="sm" />
                {review.authorRole === 'SYSTEM' && (
                  <span className="rounded-full bg-red-100 px-2 py-0.5 text-[11px] font-semibold text-red-800">
                    Automática
                  </span>
                )}
                <span className="text-xs text-slate-400">
                  {new Date(review.createdAt).toLocaleDateString('es-ES')}
                </span>
              </div>
              {review.comment && <p className="mt-1 text-sm text-slate-600">{review.comment}</p>}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
