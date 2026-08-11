import { describe, expect, it } from 'vitest'
import {
  formatDate,
  formatDateRange,
  formatDateShort,
  formatDateWithWeekday,
  formatMoney,
  formatNights,
  formatPets,
  formatTime,
} from './format'

/**
 * Fija el formato con el que la aplicación habla al usuario.
 *
 * <p>Lo importante aquí no es la estética sino el desfase de día: construir la
 * fecha con `new Date('2026-08-13')` la interpreta como medianoche UTC, y en un
 * huso negativo se pinta el 12. Un error de un día en la fecha de entrega de una
 * mascota no es un detalle cosmético.
 */
describe('formato de fechas', () => {
  it('no desplaza el día al formatear una fecha ISO', () => {
    expect(formatDate('2026-08-13')).toContain('13')
    expect(formatDate('2026-08-13')).toContain('agosto')
    expect(formatDate('2026-08-13')).toContain('2026')
  })

  it('respeta el primer día del mes, el caso donde el desfase se nota', () => {
    // Con el error de UTC, el 1 de enero se convierte en 31 de diciembre del
    // año anterior: falla el día, el mes y el año a la vez.
    expect(formatDate('2026-01-01')).toContain('1')
    expect(formatDate('2026-01-01')).toContain('enero')
    expect(formatDate('2026-01-01')).toContain('2026')
  })

  it('formatea en versión corta para listados', () => {
    expect(formatDateShort('2026-08-13')).toMatch(/13.*ago.*2026/)
  })

  it('incluye el día de la semana cuando hace falta', () => {
    // El 13 de agosto de 2026 es jueves.
    expect(formatDateWithWeekday('2026-08-13')).toContain('jueves')
    expect(formatDateWithWeekday('2026-08-13')).toContain('13')
  })

  it('formatea la hora quitando los segundos que envía el backend', () => {
    expect(formatTime('09:00:00')).toBe('09:00')
    expect(formatTime('20:00:00')).toBe('20:00')
  })
})

describe('formatDateRange', () => {
  it('no repite el mes cuando el rango cae dentro de uno solo', () => {
    const rango = formatDateRange('2026-08-13', '2026-08-20')

    expect(rango).toContain('13')
    expect(rango).toContain('20')
    expect(rango).toContain('agosto')
    // "agosto" una sola vez, no "13 de agosto – 20 de agosto".
    expect(rango.match(/agosto/g)).toHaveLength(1)
  })

  it('nombra ambos meses cuando el rango los cruza', () => {
    const rango = formatDateRange('2026-08-28', '2026-09-03')

    expect(rango).toContain('agosto')
    expect(rango).toContain('septiembre')
  })

  it('nombra ambos años cuando el rango cruza el fin de año', () => {
    const rango = formatDateRange('2026-12-28', '2027-01-03')

    expect(rango).toContain('2026')
    expect(rango).toContain('2027')
  })

  it('aguanta un rango de una sola noche', () => {
    const rango = formatDateRange('2026-08-13', '2026-08-14')

    expect(rango).toContain('13')
    expect(rango).toContain('14')
  })
})

describe('formatMoney', () => {
  it('usa coma decimal y el símbolo detrás, como se escribe en español', () => {
    const precio = formatMoney(183.75)

    expect(precio).toContain('183,75')
    expect(precio).toContain('€')
    // El formato anglosajón (183.75) sería el que produce toFixed(2).
    expect(precio).not.toContain('183.75')
  })

  it('mantiene siempre dos decimales', () => {
    expect(formatMoney(25)).toContain('25,00')
    expect(formatMoney(0)).toContain('0,00')
  })

  it('formatea importes negativos, que aparecen en reembolsos', () => {
    expect(formatMoney(-50)).toContain('50,00')
    expect(formatMoney(-50)).toMatch(/-|−/)
  })

  it('respeta otras divisas', () => {
    expect(formatMoney(100, 'USD')).toContain('100,00')
  })
})

describe('plurales', () => {
  it('concuerda el número de noches', () => {
    expect(formatNights(1)).toBe('1 noche')
    expect(formatNights(7)).toBe('7 noches')
    expect(formatNights(0)).toBe('0 noches')
  })

  it('concuerda el número de mascotas', () => {
    expect(formatPets(1)).toBe('1 mascota')
    expect(formatPets(3)).toBe('3 mascotas')
  })
})
