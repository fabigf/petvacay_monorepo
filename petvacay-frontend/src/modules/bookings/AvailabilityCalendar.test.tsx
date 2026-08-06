import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { DailyAvailability } from '@/core/api/types'
import { AvailabilityCalendar } from './AvailabilityCalendar'

const getAvailability = vi.hoisted(() => vi.fn())
vi.mock('./bookingsApi', () => ({ getAvailability }))

/**
 * Fija la regla que motivó este calendario: una noche solo es seleccionable si
 * quedan plazas para TODAS las mascotas marcadas.
 *
 * <p>El caso de referencia (negocio.md 4.2) es un cuidador con 3 plazas y 2 ya
 * ocupadas del 13 al 20: con 2 mascotas marcadas esos días no se pueden elegir,
 * con 1 sí. Se prueba a través del componente y no de sus funciones internas
 * porque lo que importa es que el día quede realmente deshabilitado — un
 * cálculo correcto que no llegue al atributo `disabled` no impide nada.
 */
describe('AvailabilityCalendar', () => {
  const SITTER = 'sitter-1'

  /** Mes fijo para que el test no dependa de cuándo se ejecuta. */
  const YEAR = 2026
  const MONTH = 8 // agosto

  beforeEach(() => {
    vi.clearAllMocks()
    // Se congela "hoy" al 1 de agosto de 2026: el calendario arranca en el mes
    // del primer día seleccionable, así que sin esto el test miraría un mes
    // distinto cada vez que se ejecutara.
    vi.setSystemTime(new Date(YEAR, MONTH - 1, 1, 12, 0, 0))
  })

  function iso(day: number): string {
    return `${YEAR}-${String(MONTH).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  }

  /**
   * Mes completo con `capacity` plazas y `occupied` ocupadas del 13 al 19.
   *
   * <p>Incluye `free` porque el backend lo envia calculado: el calendario NO lo
   * deduce de capacity - occupied, y esa dependencia es justo la que hay que
   * reproducir aqui para que el test valga de algo.
   */
  function monthWithBusyMiddle(capacity: number, occupiedInMiddle: number): DailyAvailability[] {
    return Array.from({ length: 31 }, (_, index) => {
      const day = index + 1
      const occupied = day >= 13 && day < 20 ? occupiedInMiddle : 0
      return { date: iso(day), capacity, occupied, free: Math.max(0, capacity - occupied) }
    })
  }

  function renderCalendar(petCount: number, overrides: Partial<Parameters<typeof AvailabilityCalendar>[0]> = {}) {
    const onSelect = vi.fn()
    const onInvalidSelection = vi.fn()

    render(
      <AvailabilityCalendar
        sitterId={SITTER}
        petCount={petCount}
        startDate=""
        endDate=""
        onSelect={onSelect}
        onInvalidSelection={onInvalidSelection}
        {...overrides}
      />,
    )

    return { onSelect, onInvalidSelection }
  }

  /** El botón del día `day` del mes de referencia. */
  async function dayButton(day: number): Promise<HTMLElement> {
    return await screen.findByRole('button', { name: new RegExp(`^${day} de agosto`, 'i') })
  }

  it('bloquea los días sin plazas para las mascotas marcadas', async () => {
    getAvailability.mockResolvedValue(monthWithBusyMiddle(3, 2))
    renderCalendar(2)

    // 3 plazas, 2 ocupadas: solo queda 1 y se piden 2.
    await waitFor(async () => expect(await dayButton(13)).toBeDisabled())
    expect(await dayButton(19)).toBeDisabled()

    // Fuera de ese tramo hay las 3 plazas libres.
    expect(await dayButton(12)).toBeEnabled()
    expect(await dayButton(20)).toBeEnabled()
  })

  it('deja libres esos mismos días si solo se marca una mascota', async () => {
    getAvailability.mockResolvedValue(monthWithBusyMiddle(3, 2))
    renderCalendar(1)

    // Queda 1 plaza y se pide 1: entra.
    await waitFor(async () => expect(await dayButton(13)).toBeEnabled())
    expect(await dayButton(19)).toBeEnabled()
  })

  it('marca el día bloqueado también para lectores de pantalla', async () => {
    getAvailability.mockResolvedValue(monthWithBusyMiddle(3, 2))
    renderCalendar(2)

    const blocked = await screen.findByRole('button', {
      name: /^13 de agosto.*sin plazas para las mascotas elegidas/i,
    })
    expect(blocked).toBeDisabled()
  })

  it('no deja seleccionar días pasados', async () => {
    getAvailability.mockResolvedValue(monthWithBusyMiddle(3, 0))
    renderCalendar(1)

    // "Hoy" es el 1 de agosto y el primer día seleccionable es mañana.
    await waitFor(async () => expect(await dayButton(1)).toBeDisabled())
    expect(await dayButton(2)).toBeEnabled()
  })

  it('un día sin datos todavía no es seleccionable', async () => {
    // La petición nunca resuelve: no se puede confirmar hueco, así que lo
    // prudente es no dejar elegir en vez de dar por bueno el día.
    getAvailability.mockReturnValue(new Promise(() => {}))
    renderCalendar(1)

    expect(await dayButton(15)).toBeDisabled()
  })

  it('si la petición falla, los días quedan bloqueados en vez de darse por libres', async () => {
    getAvailability.mockRejectedValue(new Error('sin red'))
    renderCalendar(1)

    await waitFor(async () => expect(await dayButton(15)).toBeDisabled())
  })

  it('el primer clic fija la entrada y el segundo la salida', async () => {
    getAvailability.mockResolvedValue(monthWithBusyMiddle(3, 0))
    const user = userEvent.setup()
    const { onSelect } = renderCalendar(1)

    await user.click(await dayButton(5))
    expect(onSelect).toHaveBeenLastCalledWith(iso(5), '')
  })

  it('elegir una salida al otro lado de un tramo bloqueado reinicia la selección', async () => {
    getAvailability.mockResolvedValue(monthWithBusyMiddle(3, 2))
    const user = userEvent.setup()
    // Entrada ya elegida el día 10; el tramo 13-19 no cabe para 2 mascotas.
    const { onSelect } = renderCalendar(2, { startDate: iso(10), endDate: '' })

    await waitFor(async () => expect(await dayButton(13)).toBeDisabled())
    await user.click(await dayButton(22))

    // No se acepta 10 -> 22 porque cruzaría noches sin plaza: el clic pasa a
    // ser el inicio de una selección nueva.
    expect(onSelect).toHaveBeenLastCalledWith(iso(22), '')
  })

  it('acepta un rango cuyas noches tienen todas plaza', async () => {
    getAvailability.mockResolvedValue(monthWithBusyMiddle(3, 2))
    const user = userEvent.setup()
    const { onSelect } = renderCalendar(2, { startDate: iso(5), endDate: '' })

    await waitFor(async () => expect(await dayButton(8)).toBeEnabled())
    await user.click(await dayButton(8))

    expect(onSelect).toHaveBeenLastCalledWith(iso(5), iso(8))
  })

  it('avisa cuando el rango ya elegido deja de caber al marcar más mascotas', async () => {
    getAvailability.mockResolvedValue(monthWithBusyMiddle(3, 2))
    // 13 -> 16 estaba elegido con 1 mascota; ahora se marcan 2 y no cabe.
    const { onInvalidSelection } = renderCalendar(2, { startDate: iso(13), endDate: iso(16) })

    await waitFor(() => expect(onInvalidSelection).toHaveBeenCalled())
  })

  it('no avisa si el rango elegido sigue cabiendo', async () => {
    getAvailability.mockResolvedValue(monthWithBusyMiddle(3, 2))
    const { onInvalidSelection } = renderCalendar(1, { startDate: iso(13), endDate: iso(16) })

    await waitFor(async () => expect(await dayButton(13)).toBeEnabled())
    expect(onInvalidSelection).not.toHaveBeenCalled()
  })

  it('muestra el mensaje de error cuando se le pasa uno', async () => {
    getAvailability.mockResolvedValue(monthWithBusyMiddle(3, 0))
    renderCalendar(1, { error: 'Elige las fechas de la estancia' })

    expect(await screen.findByRole('alert')).toHaveTextContent('Elige las fechas de la estancia')
  })

  it('pide la disponibilidad de los dos meses visibles', async () => {
    getAvailability.mockResolvedValue(monthWithBusyMiddle(3, 0))
    renderCalendar(1)

    await waitFor(() => expect(getAvailability).toHaveBeenCalledTimes(2))
    expect(getAvailability).toHaveBeenCalledWith(SITTER, '2026-08-01', '2026-09-01')
    expect(getAvailability).toHaveBeenCalledWith(SITTER, '2026-09-01', '2026-10-01')
  })
})
