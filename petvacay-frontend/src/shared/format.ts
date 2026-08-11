/**
 * Formato de fechas e importes para toda la interfaz.
 *
 * <p>Existe porque el backend habla en ISO (`2026-08-13`) y en céntimos exactos,
 * y hasta ahora cada pantalla resolvía la traducción por su cuenta: algunas
 * pintaban la fecha ISO tal cual —lo que en español se lee como una cadena
 * técnica, no como un día— y el importe se componía con `toFixed(2)` más un
 * símbolo de euro suelto en dieciocho sitios distintos.
 *
 * <p>Centralizarlo no es solo estética: `Intl` aplica el separador decimal
 * español (coma) y el espacio correcto antes del símbolo, que es lo que hace que
 * un precio parezca un precio.
 */

/**
 * Una fecha ISO (`2026-08-13`) sin hora.
 *
 * <p>Se parte a mano en lugar de `new Date(iso)`: esa forma interpreta la cadena
 * como UTC y, en husos horarios negativos, muestra el día anterior al que el
 * backend envió. Es el mismo motivo por el que el calendario de disponibilidad
 * evita `toISOString()`.
 */
function parseISODate(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number)
  return new Date(year, month - 1, day)
}

/** `2026-08-13` → `13 de agosto de 2026` */
export function formatDate(iso: string): string {
  return parseISODate(iso).toLocaleDateString('es-ES', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

/** `2026-08-13` → `13 ago 2026`, para tablas y listados donde el espacio manda. */
export function formatDateShort(iso: string): string {
  return parseISODate(iso).toLocaleDateString('es-ES', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

/** `2026-08-13` → `jueves, 13 de agosto`, cuando el día de la semana importa. */
export function formatDateWithWeekday(iso: string): string {
  return parseISODate(iso).toLocaleDateString('es-ES', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  })
}

/**
 * Un rango de estancia. Omite lo que se repite: dentro del mismo mes, este solo
 * se nombra una vez (`13 – 20 de agosto de 2026`), que es como se escribe en
 * español y ocupa la mitad.
 */
export function formatDateRange(startISO: string, endISO: string): string {
  const start = parseISODate(startISO)
  const end = parseISODate(endISO)

  const sameYear = start.getFullYear() === end.getFullYear()
  const sameMonth = sameYear && start.getMonth() === end.getMonth()

  if (sameMonth) {
    const month = end.toLocaleDateString('es-ES', { month: 'long', year: 'numeric' })
    return `${start.getDate()} – ${end.getDate()} de ${month}`
  }
  if (sameYear) {
    const from = start.toLocaleDateString('es-ES', { day: 'numeric', month: 'long' })
    const to = end.toLocaleDateString('es-ES', { day: 'numeric', month: 'long', year: 'numeric' })
    return `${from} – ${to}`
  }
  return `${formatDate(startISO)} – ${formatDate(endISO)}`
}

/** Marca de tiempo completa (`Instant` del backend) para historiales. */
export function formatDateTime(instant: string): string {
  return new Date(instant).toLocaleString('es-ES', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** `09:00:00` (java.time.LocalTime) → `09:00` */
export function formatTime(time: string): string {
  return time.slice(0, 5)
}

/**
 * `183.75` → `183,75 €`.
 *
 * <p>Con la coma decimal y el espacio antes del símbolo que corresponden al
 * español, en lugar del `183.75 €` que produce `toFixed(2)`.
 */
export function formatMoney(amount: number, currency = 'EUR'): string {
  return new Intl.NumberFormat('es-ES', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount)
}

/** `7` → `7 noches`; `1` → `1 noche`. */
export function formatNights(nights: number): string {
  return `${nights} ${nights === 1 ? 'noche' : 'noches'}`
}

/** `2` → `2 mascotas`; `1` → `1 mascota`. */
export function formatPets(count: number): string {
  return `${count} ${count === 1 ? 'mascota' : 'mascotas'}`
}
