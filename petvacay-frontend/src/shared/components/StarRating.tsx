import { useState } from 'react'

/**
 * Estrellas de valoración. Con `onChange` es un selector interactivo (para
 * dejar una reseña); sin él, es solo la representación visual de un valor ya
 * fijo (para mostrar la media de un cuidador).
 *
 * <p>El valor `0` significa "todavía sin puntuar" y pinta las cinco estrellas
 * apagadas: quien deja una reseña tiene que elegir su nota, no confirmar una
 * que ya venía marcada.
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
  // Previsualización al pasar el ratón: sin ella, en un selector de estrellas no
  // hay forma de saber qué se va a marcar antes de hacer clic.
  const [hovered, setHovered] = useState(0)

  const sizeClass = size === 'sm' ? 'text-sm' : size === 'lg' ? 'text-2xl' : 'text-lg'
  const highlighted = hovered || Math.round(value)

  return (
    <span
      className={`inline-flex gap-0.5 ${sizeClass}`}
      onMouseLeave={() => setHovered(0)}
      role={onChange ? 'radiogroup' : undefined}
    >
      {[1, 2, 3, 4, 5].map((star) => (
        <button
          key={star}
          type="button"
          disabled={!onChange}
          onClick={() => onChange?.(star)}
          onMouseEnter={() => onChange && setHovered(star)}
          role={onChange ? 'radio' : undefined}
          aria-checked={onChange ? star === Math.round(value) : undefined}
          className={`leading-none transition-colors ${
            onChange ? 'cursor-pointer' : 'cursor-default'
          } ${star <= highlighted ? 'text-amber-500' : 'text-slate-300'}`}
          aria-label={`${star} estrella${star === 1 ? '' : 's'}`}
        >
          ★
        </button>
      ))}
    </span>
  )
}
