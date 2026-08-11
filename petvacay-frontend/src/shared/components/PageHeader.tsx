import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

/**
 * Cabecera común de las pantallas internas.
 *
 * <p>Cada página venía resolviendo su título con su propia combinación de
 * clases, y las diferencias se notaban al navegar: el mismo nivel de jerarquía
 * aparecía con distinto tamaño y distinto espacio inferior según la pantalla.
 * Aquí se decide una vez.
 *
 * <p>`actions` va a la derecha en pantallas anchas y debajo en móvil, en lugar
 * de encogerse hasta romperse.
 */
export function PageHeader({
  title,
  subtitle,
  actions,
  backTo,
  backLabel = 'Volver',
}: {
  title: ReactNode
  subtitle?: ReactNode
  actions?: ReactNode
  /** Ruta del enlace de vuelta. Se omite en las pantallas de primer nivel. */
  backTo?: string
  backLabel?: string
}) {
  return (
    <header className="mb-6">
      {backTo && (
        <Link
          to={backTo}
          className="mb-2 inline-flex items-center gap-1 text-sm font-medium text-slate-500 transition hover:text-brand-700"
        >
          <span aria-hidden>←</span> {backLabel}
        </Link>
      )}

      <div className="flex flex-wrap items-start justify-between gap-x-6 gap-y-3">
        <div className="min-w-0">
          <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">{title}</h1>
          {subtitle && <div className="mt-1.5 text-sm text-slate-600">{subtitle}</div>}
        </div>
        {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
      </div>
    </header>
  )
}

/**
 * Título de una sección dentro de una página, un escalón por debajo de
 * {@link PageHeader}.
 */
export function SectionHeader({
  title,
  description,
  actions,
}: {
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
}) {
  return (
    <div className="mb-3 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
      <div className="min-w-0">
        <h2 className="font-semibold text-slate-800">{title}</h2>
        {description && <p className="mt-0.5 text-sm text-slate-500">{description}</p>}
      </div>
      {actions && <div className="shrink-0">{actions}</div>}
    </div>
  )
}
