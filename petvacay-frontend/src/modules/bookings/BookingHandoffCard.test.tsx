import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { BookingHandoff } from '@/core/api/types'
import { BookingHandoffCard } from './BookingHandoffCard'

/**
 * Fija el comportamiento de la tarjeta de entrega, incluido el caso que la
 * rompió: un cuidador sin dirección guardada dejaba un recuadro vacío con un
 * pin y ningún texto, sin explicar nada.
 */
describe('BookingHandoffCard', () => {
  const handoff: BookingHandoff = {
    sitterAddress: 'Calle de Alcalá 45, 3ºA, Madrid',
    sitterLatitude: 40.4168,
    sitterLongitude: -3.7038,
    dropOffDate: '2026-08-13',
    pickUpDate: '2026-08-20',
    windowStart: '09:00:00',
    windowEnd: '20:00:00',
  }

  it('muestra la dirección completa del cuidador', () => {
    render(<BookingHandoffCard handoff={handoff} loading={false} />)

    expect(screen.getByText(/Calle de Alcalá 45, 3ºA, Madrid/)).toBeInTheDocument()
  })

  it('muestra la franja horaria en formato HH:mm, sin los segundos del backend', () => {
    render(<BookingHandoffCard handoff={handoff} loading={false} />)

    expect(screen.getAllByText(/de 09:00 a 20:00/)).toHaveLength(2)
    expect(screen.queryByText(/09:00:00/)).not.toBeInTheDocument()
  })

  it('distingue el día de entrega del de recogida', () => {
    render(<BookingHandoffCard handoff={handoff} loading={false} />)

    expect(screen.getByText('Entrega')).toBeInTheDocument()
    expect(screen.getByText('Recogida')).toBeInTheDocument()
    expect(screen.getByText(/13 de agosto/)).toBeInTheDocument()
    expect(screen.getByText(/20 de agosto/)).toBeInTheDocument()
  })

  it('ofrece ver la ubicación en el mapa y a pie de calle', () => {
    render(<BookingHandoffCard handoff={handoff} loading={false} />)

    const mapa = screen.getByRole('link', { name: /ver en el mapa/i })
    expect(mapa).toHaveAttribute('href', expect.stringContaining('openstreetmap.org'))
    expect(mapa).toHaveAttribute('href', expect.stringContaining('40.4168'))

    const calle = screen.getByRole('link', { name: /a pie de calle/i })
    expect(calle).toHaveAttribute('href', expect.stringContaining('map_action=pano'))

    // Se abren fuera sin ceder el referente ni el control de la pestaña.
    for (const enlace of [mapa, calle]) {
      expect(enlace).toHaveAttribute('target', '_blank')
      expect(enlace).toHaveAttribute('rel', 'noreferrer')
    }
  })

  it('explica la situación cuando el cuidador no tiene dirección guardada', () => {
    render(<BookingHandoffCard handoff={{ ...handoff, sitterAddress: '' }} loading={false} />)

    // El fallo original: recuadro vacío. Ahora hay un texto que dice qué pasa.
    expect(screen.getByText(/no ha completado su dirección/i)).toBeInTheDocument()
    // Y las fechas siguen siendo útiles aunque falte la dirección.
    expect(screen.getByText('Entrega')).toBeInTheDocument()
  })

  it('no ofrece enlaces de mapa si no hay coordenadas', () => {
    render(
      <BookingHandoffCard
        handoff={{ ...handoff, sitterLatitude: 0, sitterLongitude: 0 }}
        loading={false}
      />,
    )

    expect(screen.queryByRole('link', { name: /ver en el mapa/i })).not.toBeInTheDocument()
  })

  it('no pinta nada mientras la reserva no da derecho a la dirección', () => {
    // Antes del pago el backend rechaza la petición y `handoff` queda a null:
    // la tarjeta no debe dejar un hueco vacío en la pantalla.
    const { container } = render(<BookingHandoffCard handoff={null} loading={false} />)

    expect(container).toBeEmptyDOMElement()
  })

  it('muestra el esqueleto de carga mientras llega la respuesta', () => {
    render(<BookingHandoffCard handoff={null} loading={true} />)

    expect(screen.getByText('Entrega y recogida')).toBeInTheDocument()
  })
})
