import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

// jsdom no reinicia el DOM entre tests: sin esto, el segundo test de un fichero
// encontraría también los nodos que dejó el primero y las consultas por texto
// fallarían por "varios elementos coinciden" en vez de por el motivo real.
afterEach(() => {
  cleanup()
})
