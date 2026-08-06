import { Button } from '@/shared/components/ui'

/**
 * Paginador mínimo: anterior, siguiente y en qué punto estás.
 *
 * <p>No pinta un botón por página a propósito. El back-office se recorre
 * buscando y filtrando, no saltando a la página 7; los números sueltos ocupan
 * sitio y no responden a la pregunta que sí importa, que es cuántos resultados
 * hay en total.
 */
export function Pagination({
  page,
  totalPages,
  totalElements,
  onChange,
}: {
  page: number
  totalPages: number
  totalElements: number
  onChange: (page: number) => void
}) {
  if (totalElements === 0) return null

  return (
    <div className="flex items-center justify-between gap-3 pt-1">
      <span className="text-xs text-slate-500">
        Página {page + 1} de {Math.max(totalPages, 1)} · {totalElements}{' '}
        {totalElements === 1 ? 'resultado' : 'resultados'}
      </span>
      <div className="flex gap-2">
        <Button variant="secondary" disabled={page === 0} onClick={() => onChange(page - 1)}>
          Anterior
        </Button>
        <Button
          variant="secondary"
          disabled={page + 1 >= totalPages}
          onClick={() => onChange(page + 1)}
        >
          Siguiente
        </Button>
      </div>
    </div>
  )
}
