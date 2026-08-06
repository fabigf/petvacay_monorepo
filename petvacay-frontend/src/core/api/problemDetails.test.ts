import { AxiosError, AxiosHeaders } from 'axios'
import { describe, expect, it } from 'vitest'
import { toFieldErrors, toProblemDetails } from './problemDetails'

/**
 * Fija el contrato del que depende toda la gestión de errores de la interfaz:
 * pase lo que pase, siempre hay `title`, `detail` y `code`.
 *
 * <p>Si esta función devolviera `undefined` en algún camino, el fallo no se
 * vería aquí sino como una pantalla en blanco o un toast vacío justo cuando algo
 * ya ha ido mal — el peor momento para tener un segundo error encima.
 */
describe('toProblemDetails', () => {
  function axiosErrorWith(status: number, data: unknown): AxiosError {
    const error = new AxiosError('Request failed')
    error.response = {
      status,
      statusText: '',
      data,
      headers: new AxiosHeaders(),
      config: { headers: new AxiosHeaders() },
    }
    return error
  }

  it('deja pasar tal cual el RFC 7807 que envía el backend', () => {
    const problem = {
      title: 'Cuidador No Disponible',
      status: 409,
      detail: 'El cuidador ya tiene una reserva confirmada para esas fechas.',
      code: 'ERR_BOOKING_DATE_CONFLICT',
    }

    expect(toProblemDetails(axiosErrorWith(409, problem))).toEqual(problem)
  })

  it('traduce un fallo de red a un mensaje accionable, no a un error técnico', () => {
    const offline = new AxiosError('Network Error')

    const problem = toProblemDetails(offline)

    expect(problem.code).toBe('ERR_NETWORK')
    expect(problem.status).toBe(0)
    expect(problem.detail).toContain('conexión')
  })

  it('trata el tiempo de espera agotado igual que la falta de red', () => {
    const timeout = new AxiosError('timeout of 10000ms exceeded')
    timeout.code = 'ECONNABORTED'

    expect(toProblemDetails(timeout).code).toBe('ERR_NETWORK')
  })

  it('no confunde una respuesta HTML de error con un problema RFC 7807', () => {
    // Un 502 de un proxy devuelve HTML, no JSON: sin la comprobación de `title`
    // esa cadena acabaría pintada tal cual en la pantalla.
    const problem = toProblemDetails(axiosErrorWith(502, '<html>Bad Gateway</html>'))

    expect(problem.code).toBe('ERR_UNKNOWN')
    expect(problem.status).toBe(502)
  })

  it('sobrevive a un cuerpo JSON sin la forma esperada', () => {
    const problem = toProblemDetails(axiosErrorWith(500, { mensaje: 'vaya' }))

    expect(problem.code).toBe('ERR_UNKNOWN')
    expect(problem.title).toBe('Error inesperado')
  })

  it('normaliza también lo que no es un error de Axios', () => {
    expect(toProblemDetails(new Error('boom')).detail).toBe('boom')
    expect(toProblemDetails('algo raro').detail).toBe('algo raro')
    expect(toProblemDetails(null).code).toBe('ERR_UNKNOWN')
  })

  it('devuelve siempre los cuatro campos que la interfaz da por hechos', () => {
    const casos: unknown[] = [
      new Error('boom'),
      null,
      undefined,
      axiosErrorWith(500, null),
      new AxiosError('Network Error'),
    ]

    for (const caso of casos) {
      const problem = toProblemDetails(caso)
      expect(problem.title).toBeTruthy()
      expect(problem.code).toBeTruthy()
      expect(typeof problem.status).toBe('number')
      expect(problem.detail).toBeDefined()
    }
  })
})

describe('toFieldErrors', () => {
  it('convierte invalid_params en un mapa campo -> motivo', () => {
    const errores = toFieldErrors({
      title: 'Datos no válidos',
      status: 400,
      detail: '',
      code: 'ERR_VALIDATION_FAILED',
      invalid_params: [
        { name: 'email', reason: 'El correo electrónico no es válido' },
        { name: 'password', reason: 'La contraseña debe tener al menos 8 caracteres' },
      ],
    })

    expect(errores).toEqual({
      email: 'El correo electrónico no es válido',
      password: 'La contraseña debe tener al menos 8 caracteres',
    })
  })

  it('devuelve un mapa vacío cuando el error no trae campos', () => {
    expect(toFieldErrors({ title: 'x', status: 409, detail: '', code: 'ERR_X' })).toEqual({})
  })
})
