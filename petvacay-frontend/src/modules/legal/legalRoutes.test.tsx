import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { AppFooter } from '@/shared/components/AppFooter'
import { ContactPage } from './ContactPage'
import { CookiesPage } from './CookiesPage'
import { FaqPage } from './FaqPage'
import { HowItWorksPage } from './HowItWorksPage'
import { NotFoundPage } from './NotFoundPage'
import { PrivacyPage } from './PrivacyPage'
import { TermsPage } from './TermsPage'

/**
 * Las páginas informativas no tienen lógica, así que lo que se prueba no es
 * comportamiento sino que **siguen siendo alcanzables y coherentes**: los
 * enlaces del pie apuntan a rutas que existen, y cada página anuncia un único
 * `h1`. Son justo los detalles que se rompen al reorganizar rutas y que nadie
 * nota hasta que alguien pincha en «Privacidad» y no pasa nada.
 */
describe('páginas informativas y legales', () => {
  /** Rutas que el pie de página promete que existen. */
  const ROUTES = [
    '/search',
    '/como-funciona',
    '/register',
    '/faq',
    '/contacto',
    '/terminos',
    '/privacidad',
    '/cookies',
  ]

  const PAGES = [
    { name: 'Términos y condiciones', Page: TermsPage },
    { name: 'Política de privacidad', Page: PrivacyPage },
    { name: 'Política de cookies', Page: CookiesPage },
    { name: 'Preguntas frecuentes', Page: FaqPage },
    { name: 'Cómo funciona PetVacay', Page: HowItWorksPage },
    { name: 'Contacto y ayuda', Page: ContactPage },
    { name: 'Esta página no existe', Page: NotFoundPage },
  ]

  it.each(PAGES)('$name se pinta con un único encabezado principal', ({ name, Page }) => {
    render(
      <MemoryRouter>
        <Page />
      </MemoryRouter>,
    )

    const headings = screen.getAllByRole('heading', { level: 1 })
    expect(headings).toHaveLength(1)
    expect(headings[0]).toHaveTextContent(name)
  })

  it('el pie enlaza solo a rutas que la aplicación conoce', () => {
    render(
      <MemoryRouter>
        <AppFooter />
      </MemoryRouter>,
    )

    const hrefs = screen
      .getAllByRole('link')
      .map((link) => link.getAttribute('href'))
      .filter((href): href is string => href !== null)

    expect(hrefs.length).toBeGreaterThan(0)
    for (const href of hrefs) {
      expect(ROUTES).toContain(href)
    }
  })

  it('el pie ofrece los tres documentos legales', () => {
    render(
      <MemoryRouter>
        <AppFooter />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: /términos y condiciones/i })).toHaveAttribute(
      'href',
      '/terminos',
    )
    expect(screen.getByRole('link', { name: /privacidad/i })).toHaveAttribute('href', '/privacidad')
    expect(screen.getByRole('link', { name: /cookies/i })).toHaveAttribute('href', '/cookies')
  })

  /**
   * Texto plano de lo renderizado. Las cifras van dentro de `<strong>` en
   * mitad de un párrafo, así que buscarlas por nodo encuentra varios elementos
   * o ninguno según cómo esté partido el marcado; lo que interesa aquí es solo
   * que la cifra aparezca en la página.
   */
  function renderedText(Page: () => React.JSX.Element): string {
    const { container } = render(
      <MemoryRouter>
        <Page />
      </MemoryRouter>,
    )
    return container.textContent ?? ''
  }

  it('las páginas legales advierten de que es un proyecto de demostración', () => {
    // No pueden presentarse como las condiciones de una empresa real.
    for (const Page of [TermsPage, PrivacyPage, CookiesPage, ContactPage]) {
      expect(renderedText(Page)).toMatch(/proyecto de demostración/i)
      cleanup()
    }
  })

  it('los términos recogen las cifras reales del modelo de negocio', () => {
    const texto = renderedText(TermsPage)

    // Si alguna cambia en el dominio, el texto legal deja de describir el
    // sistema y este test obliga a actualizarlo.
    expect(texto).toContain('15%')
    expect(texto).toContain('5%')
    expect(texto).toContain('48 horas')
    expect(texto).toContain('24 horas')
    expect(texto).toContain('09:00 a 20:00')
  })

  it('la FAQ deja las respuestas accesibles a la búsqueda del navegador', () => {
    render(
      <MemoryRouter>
        <FaqPage />
      </MemoryRouter>,
    )

    // Con <details> el texto está en el DOM aunque la respuesta esté plegada.
    expect(screen.getByText(/no tiene plazas libres suficientes/i)).toBeInTheDocument()
  })
})
