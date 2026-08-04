/**
 * Estrellas de valoración. Con `onChange` es un selector interactivo (para
 * dejar una reseña); sin él, es solo la representación visual de un valor ya
 * fijo (para mostrar la media de un cuidador).
 */
export function StarRating({
  value,
  onChange,
  size = 'md',
}: {
  value: number
  onChange?: (value: number) => void
  size?: 'sm' | 'md' | 'lg'
}) {
  const sizeClass = size === 'sm' ? 'text-sm' : size === 'lg' ? 'text-2xl' : 'text-lg'
  const rounded = Math.round(value)

  return (
    <span className={`inline-flex gap-0.5 ${sizeClass}`}>
      {[1, 2, 3, 4, 5].map((star) => (
        <button
          key={star}
          type="button"
          disabled={!onChange}
          onClick={() => onChange?.(star)}
          className={`leading-none ${onChange ? 'cursor-pointer' : 'cursor-default'} ${
            star <= rounded ? 'text-amber-500' : 'text-slate-300'
          }`}
          aria-label={`${star} estrella${star === 1 ? '' : 's'}`}
        >
          ★
        </button>
      ))}
    </span>
  )
}
