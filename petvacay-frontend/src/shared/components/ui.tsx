import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from 'react'

/** Primitivas de UI reutilizables. Sustituibles por Shadcn/ui sin tocar los módulos. */

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'

const buttonStyles: Record<ButtonVariant, string> = {
  primary: 'bg-brand-600 text-white hover:bg-brand-700 focus-visible:outline-brand-600',
  secondary: 'bg-white text-brand-700 border border-brand-200 hover:bg-brand-50 focus-visible:outline-brand-600',
  ghost: 'bg-transparent text-slate-600 hover:bg-slate-100 focus-visible:outline-slate-500',
  danger: 'bg-red-600 text-white hover:bg-red-700 focus-visible:outline-red-600',
}

/**
 * Anillo de foco común a todos los botones.
 *
 * <p>Antes solo lo llevaba la variante primaria, así que al navegar con el
 * teclado los botones «secundario», «fantasma» y «peligro» no daban ninguna
 * señal de dónde estaba el foco. Se usa `focus-visible` y no `focus` para que
 * el anillo aparezca al tabular pero no al hacer clic con el ratón.
 */
const focusRing =
  'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  loading?: boolean
}

export function Button({
  variant = 'primary',
  loading = false,
  disabled,
  children,
  className = '',
  ...props
}: ButtonProps) {
  return (
    <button
      {...props}
      disabled={disabled || loading}
      className={`inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold transition
        disabled:cursor-not-allowed disabled:opacity-50 ${focusRing} ${buttonStyles[variant]} ${className}`}
    >
      {loading && (
        <span
          aria-hidden
          className="size-4 animate-spin rounded-full border-2 border-current border-t-transparent"
        />
      )}
      {children}
    </button>
  )
}

export function Card({
  children,
  className = '',
  interactive = false,
}: {
  children: ReactNode
  className?: string
  /**
   * La tarjeta entera lleva a algún sitio. Añade respuesta al puntero para que
   * se note que es pulsable: sin ella, una tarjeta clicable es indistinguible
   * de un bloque de texto.
   */
  interactive?: boolean
}) {
  const interactiveStyles = interactive
    ? 'transition hover:border-brand-300 hover:shadow-md'
    : ''
  return (
    <div
      className={`rounded-xl border border-sand-200 bg-white p-5 shadow-sm ${interactiveStyles} ${className}`}
    >
      {children}
    </div>
  )
}

/**
 * Dato etiquetado: el patrón «rótulo pequeño arriba, valor destacado debajo»
 * que repiten el detalle de reserva, el panel y la ficha del cuidador.
 */
export function DataPoint({
  label,
  value,
  align = 'left',
}: {
  label: ReactNode
  value: ReactNode
  align?: 'left' | 'right'
}) {
  return (
    <div className={align === 'right' ? 'text-right' : ''}>
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <div className="mt-0.5 text-sm text-slate-800">{value}</div>
    </div>
  )
}

interface FieldProps {
  label: string
  htmlFor: string
  error?: string
  hint?: string
  children: ReactNode
}

export function Field({ label, htmlFor, error, hint, children }: FieldProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={htmlFor} className="text-sm font-medium text-slate-700">
        {label}
      </label>
      {children}
      {hint && !error && <p className="text-xs text-slate-500">{hint}</p>}
      {/* Mensaje por campo alimentado por invalid_params del backend */}
      {error && (
        <p role="alert" className="text-xs font-medium text-red-600">
          {error}
        </p>
      )}
    </div>
  )
}

const controlClass =
  'w-full rounded-lg border border-sand-200 bg-white px-3 py-2 text-sm text-slate-800 outline-none transition focus:border-brand-400 focus:ring-2 focus:ring-brand-100 aria-[invalid=true]:border-red-400'

export function Input({ className = '', ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={`${controlClass} ${className}`} />
}

export function Select({ className = '', ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} className={`${controlClass} ${className}`} />
}

export function Badge({
  children,
  tone = 'neutral',
  className = '',
}: {
  children: ReactNode
  tone?: 'neutral' | 'success' | 'warning' | 'danger' | 'info'
  className?: string
}) {
  const tones = {
    neutral: 'bg-slate-100 text-slate-700',
    success: 'bg-emerald-100 text-emerald-800',
    warning: 'bg-amber-100 text-amber-800',
    danger: 'bg-red-100 text-red-800',
    info: 'bg-sky-100 text-sky-800',
  }
  return (
    <span
      className={`inline-flex items-center justify-center text-center rounded-full px-2.5 py-1 text-xs font-semibold ${tones[tone]} ${className}`}
    >
      {children}
    </span>
  )
}

/**
 * Hueco sin contenido.
 *
 * <p>Admite un icono y una acción porque un vacío casi nunca es un callejón sin
 * salida: casi siempre hay algo que la persona puede hacer para llenarlo, y
 * ofrecérselo ahí ahorra tener que buscarlo en el menú.
 */
export function EmptyState({
  title,
  description,
  icon,
  action,
}: {
  title: string
  description?: string
  icon?: ReactNode
  action?: ReactNode
}) {
  return (
    <div className="rounded-xl border border-dashed border-sand-300 bg-white/60 p-10 text-center">
      {icon && (
        <div aria-hidden className="mb-3 text-3xl">
          {icon}
        </div>
      )}
      <p className="font-semibold text-slate-700">{title}</p>
      {description && (
        <p className="mx-auto mt-1 max-w-sm text-sm text-slate-500">{description}</p>
      )}
      {action && <div className="mt-5 flex justify-center gap-2">{action}</div>}
    </div>
  )
}

/**
 * Placeholder de carga. Es una pieza central a partir de Fase 3: con Kafka de por
 * medio el sistema es eventualmente consistente y la UI debe comunicar
 * "procesando" en lugar de asumir respuesta inmediata.
 */
export function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`animate-pulse rounded-lg bg-sand-100 ${className}`} />
}

/**
 * Aviso de consistencia eventual (fases.md Fase 3): informa de que una acción se
 * confirmó en el modelo de escritura pero sus efectos derivados (proyecciones que
 * viajan por Kafka, como el bloqueo de calendario del buscador) pueden tardar unos
 * segundos en reflejarse. No es un error ni un estado de carga bloqueante: la
 * acción ya tuvo éxito, esto solo gestiona la expectativa del usuario.
 */
export function ProcessingNotice({ children }: { children: ReactNode }) {
  return (
    <div className="flex items-start gap-2.5 rounded-lg border border-sky-200 bg-sky-50 px-3.5 py-3 text-sm text-sky-800">
      <span
        aria-hidden
        className="mt-0.5 size-3.5 shrink-0 animate-spin rounded-full border-2 border-sky-400 border-t-transparent"
      />
      <p>{children}</p>
    </div>
  )
}
