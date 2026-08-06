import { useEffect, useRef, useState } from 'react'
import { getAvailability } from './bookingsApi'
import type { DailyAvailability } from '@/core/api/types'
import { Button } from '@/shared/components/ui'

/**
 * Selector de fechas con los días sin sitio ya deshabilitados en el propio
 * calendario, en vez de un `<input type="date">` que deja elegir cualquier
 * fecha y solo avisa después.
 *
 * <p>Un `<input type="date">` nativo no tiene forma de deshabilitar días
 * concretos — como mucho un rango con `min`/`max` — así que "las fechas sin
 * hueco no deben aparecer seleccionables" obliga a un calendario propio.
 *
 * <p>Se muestran dos meses a la vez (como Airbnb o Booking) y no uno con
 * navegación libre: así, cuando alguien elige la entrada y luego la salida,
 * las noches intermedias están siempre visibles y ya cargadas — nunca hay que
 * validar un tramo del que todavía no se tienen datos.
 */
export function AvailabilityCalendar({
  sitterId,
  petCount,
  startDate,
  endDate,
  onSelect,
  onInvalidSelection,
  error,
}: {
  sitterId: string
  /** Mascotas actualmente marcadas; cuántas plazas necesita cada noche. */
  petCount: number
  startDate: string
  endDate: string
  /** `end` llega vacío mientras solo hay entrada elegida y falta la salida. */
  onSelect: (start: string, end: string) => void
  /** El rango elegido ha dejado de caber (se marcaron más mascotas). */
  onInvalidSelection: () => void
  error?: string
}) {
  const today = startOfDay(new Date())
  const firstSelectable = addDays(today, 1)

  const [anchor, setAnchor] = useState(() => startOfMonth(firstSelectable))
  const [availability, setAvailability] = useState<Map<string, DailyAvailability>>(new Map())
  const fetchedMonths = useRef<Set<string>>(new Set())

  // Al cambiar de cuidador se tira toda la cache: los datos de otro cuidador
  // no sirven de nada aquí.
  useEffect(() => {
    fetchedMonths.current = new Set()
    setAvailability(new Map())
    setAnchor(startOfMonth(firstSelectable))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sitterId])

  const rightMonth = addMonths(anchor, 1)

  useEffect(() => {
    if (!sitterId) return
    ;[anchor, rightMonth].forEach((month) => {
      const key = monthKey(month)
      if (fetchedMonths.current.has(key)) return
      fetchedMonths.current.add(key)

      const from = isoDate(month)
      const to = isoDate(addMonths(month, 1))
      getAvailability(sitterId, from, to)
        .then((days) => {
          setAvailability((current) => {
            const next = new Map(current)
            days.forEach((day) => next.set(day.date, day))
            return next
          })
        })
        .catch(() => {
          // Sin datos, esos días quedan en estado "cargando" y no
          // seleccionables: es lo prudente cuando no se puede confirmar hueco.
          fetchedMonths.current.delete(key)
        })
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sitterId, anchor])

  /**
   * Si el rango ya elegido deja de caber al marcar más mascotas, se limpia la
   * selección en vez de dejar en pie una reserva que el servidor rechazaría.
   */
  useEffect(() => {
    if (!startDate || !endDate) return
    if (rangeKnownNotToFit(availability, startDate, endDate, petCount)) {
      onInvalidSelection()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [petCount, availability])

  const canGoBack = isAfter(anchor, startOfMonth(firstSelectable))

  const handleDayClick = (dateISO: string) => {
    if (!startDate || endDate) {
      // Sin seleccion previa, o seleccion ya completa: este clic arranca una
      // nueva.
      onSelect(dateISO, '')
      return
    }
    if (dateISO <= startDate) {
      onSelect(dateISO, '')
      return
    }
    if (hasBlockedNight(availability, startDate, dateISO, petCount)) {
      // El tramo no cabe entero: en vez de rechazar el clic sin más, se toma
      // como el inicio de una selección nueva — es lo que se espera al pulsar
      // un día que no es la continuación de lo anterior.
      onSelect(dateISO, '')
      return
    }
    onSelect(startDate, dateISO)
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between">
        <Button
          type="button"
          variant="ghost"
          disabled={!canGoBack}
          onClick={() => setAnchor((current) => addMonths(current, -1))}
          aria-label="Mes anterior"
        >
          ←
        </Button>
        <Button
          type="button"
          variant="ghost"
          onClick={() => setAnchor((current) => addMonths(current, 1))}
          aria-label="Mes siguiente"
        >
          →
        </Button>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <MonthGrid
          month={anchor}
          firstSelectable={firstSelectable}
          availability={availability}
          petCount={petCount}
          startDate={startDate}
          endDate={endDate}
          onDayClick={handleDayClick}
        />
        <MonthGrid
          month={rightMonth}
          firstSelectable={firstSelectable}
          availability={availability}
          petCount={petCount}
          startDate={startDate}
          endDate={endDate}
          onDayClick={handleDayClick}
        />
      </div>

      {error && (
        <p role="alert" className="text-xs font-medium text-red-600">
          {error}
        </p>
      )}

      <div className="flex flex-wrap gap-3 text-xs text-slate-500">
        <Legend swatchClassName="bg-white border border-sand-300" label="Disponible" />
        <Legend swatchClassName="bg-red-100" label="Sin plazas para las mascotas elegidas" />
        <Legend swatchClassName="bg-brand-600" label="Seleccionado" />
      </div>
    </div>
  )
}

function Legend({ swatchClassName, label }: { swatchClassName: string; label: string }) {
  return (
    <span className="flex items-center gap-1.5">
      <span aria-hidden className={`size-3 rounded ${swatchClassName}`} />
      {label}
    </span>
  )
}

function MonthGrid({
  month,
  firstSelectable,
  availability,
  petCount,
  startDate,
  endDate,
  onDayClick,
}: {
  month: Date
  firstSelectable: Date
  availability: Map<string, DailyAvailability>
  petCount: number
  startDate: string
  endDate: string
  onDayClick: (dateISO: string) => void
}) {
  const label = month.toLocaleDateString('es-ES', { month: 'long', year: 'numeric' })
  const daysInMonth = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate()
  // Lunes = 0 .. domingo = 6, para que la semana empiece igual que en un
  // calendario de pared español en vez del domingo que usa JS por defecto.
  const leadingBlanks = (new Date(month.getFullYear(), month.getMonth(), 1).getDay() + 6) % 7

  const cells: Array<{ day: number; dateISO: string } | null> = []
  for (let i = 0; i < leadingBlanks; i++) cells.push(null)
  for (let day = 1; day <= daysInMonth; day++) {
    cells.push({ day, dateISO: isoDate(new Date(month.getFullYear(), month.getMonth(), day)) })
  }

  return (
    <div>
      <p className="mb-2 text-center text-sm font-semibold capitalize text-slate-700">{label}</p>
      <div className="grid grid-cols-7 gap-1 text-center text-xs">
        {WEEKDAY_LABELS.map((weekday) => (
          <span key={weekday} className="py-1 font-medium text-slate-400">
            {weekday}
          </span>
        ))}
        {cells.map((cell, index) => {
          if (!cell) return <span key={`blank-${index}`} />

          const { day, dateISO } = cell
          const isPast = dateISO < isoDate(firstSelectable)
          const daily = availability.get(dateISO)
          const isBlocked = !isPast && daily !== undefined && daily.free < petCount
          const isLoading = !isPast && daily === undefined
          const isStart = dateISO === startDate
          const isEnd = dateISO === endDate
          const isInRange = Boolean(startDate && endDate && dateISO > startDate && dateISO < endDate)
          const isSelectable = !isPast && !isBlocked && !isLoading

          return (
            <button
              key={dateISO}
              type="button"
              disabled={!isSelectable}
              onClick={() => onDayClick(dateISO)}
              aria-pressed={isStart || isEnd}
              aria-label={`${day} de ${label}${isBlocked ? ', sin plazas para las mascotas elegidas' : ''}`}
              className={dayCellClassName({ isPast, isBlocked, isLoading, isStart, isEnd, isInRange })}
            >
              {day}
            </button>
          )
        })}
      </div>
    </div>
  )
}

function dayCellClassName({
  isPast,
  isBlocked,
  isLoading,
  isStart,
  isEnd,
  isInRange,
}: {
  isPast: boolean
  isBlocked: boolean
  isLoading: boolean
  isStart: boolean
  isEnd: boolean
  isInRange: boolean
}): string {
  const base = 'aspect-square rounded-md text-sm transition'

  if (isStart || isEnd) return `${base} bg-brand-600 font-semibold text-white`
  if (isInRange) return `${base} bg-brand-100 text-brand-800`
  if (isPast) return `${base} text-slate-300 cursor-not-allowed`
  if (isBlocked) return `${base} bg-red-100 text-red-400 line-through cursor-not-allowed`
  if (isLoading) return `${base} animate-pulse bg-sand-100 text-transparent cursor-wait`
  return `${base} border border-sand-200 bg-white text-slate-700 hover:border-brand-400 hover:bg-brand-50`
}

const WEEKDAY_LABELS = ['L', 'M', 'X', 'J', 'V', 'S', 'D']

/**
 * ¿Hay alguna noche entre `start` (incluida) y `end` (excluida) sin sitio
 * para `petCount` mascotas? Una noche sin datos todavía se trata como
 * bloqueada: es la opción prudente frente a confirmar una fecha sin poder
 * verificarla.
 */
function hasBlockedNight(
  availability: Map<string, DailyAvailability>,
  start: string,
  end: string,
  petCount: number,
): boolean {
  for (let cursor = fromISO(start); isoDate(cursor) < end; cursor = addDays(cursor, 1)) {
    const daily = availability.get(isoDate(cursor))
    if (!daily || daily.free < petCount) return true
  }
  return false
}

/**
 * ¿Consta que el rango NO cabe? Distinto de {@link hasBlockedNight}: aquí una
 * noche todavía sin datos no cuenta como bloqueada.
 *
 * <p>La diferencia importa según para qué se pregunte. Para *aceptar* una
 * selección, la duda debe impedirla — no se confirma una fecha que no se ha
 * podido verificar. Para *deshacer* una selección que la persona ya había
 * hecho, la duda no basta: hacen falta datos que digan que no cabe. Si no se
 * distinguiera, la selección se borraría sola cada vez que el componente se
 * montara con fechas ya elegidas y la disponibilidad aún viajando.
 */
function rangeKnownNotToFit(
  availability: Map<string, DailyAvailability>,
  start: string,
  end: string,
  petCount: number,
): boolean {
  for (let cursor = fromISO(start); isoDate(cursor) < end; cursor = addDays(cursor, 1)) {
    const daily = availability.get(isoDate(cursor))
    if (daily && daily.free < petCount) return true
  }
  return false
}

// ---------------------------------------------------------------- fechas
//
// Todo en hora LOCAL, nunca `Date.toISOString()`: ese método convierte a UTC
// y en husos horarios negativos puede desplazar el día mostrado un dígito
// respecto al que la persona seleccionó.

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

function startOfMonth(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days)
}

function addMonths(date: Date, months: number): Date {
  return new Date(date.getFullYear(), date.getMonth() + months, 1)
}

function isAfter(a: Date, b: Date): boolean {
  return a.getTime() > b.getTime()
}

function isoDate(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

function fromISO(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

function monthKey(date: Date): string {
  return `${date.getFullYear()}-${date.getMonth()}`
}
