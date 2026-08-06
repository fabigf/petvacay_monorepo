import { Link } from 'react-router-dom'

interface FooterColumn {
  title: string
  links: Array<{ to: string; label: string }>
}

const COLUMNS: FooterColumn[] = [
  {
    title: 'Plataforma',
    links: [
      { to: '/search', label: 'Buscar cuidador' },
      { to: '/como-funciona', label: 'Cómo funciona' },
      { to: '/register', label: 'Ser cuidador' },
    ],
  },
  {
    title: 'Ayuda',
    links: [
      { to: '/faq', label: 'Preguntas frecuentes' },
      { to: '/contacto', label: 'Contacto' },
    ],
  },
  {
    title: 'Legal',
    links: [
      { to: '/terminos', label: 'Términos y condiciones' },
      { to: '/privacidad', label: 'Privacidad' },
      { to: '/cookies', label: 'Cookies' },
    ],
  },
]

/**
 * Pie de página del sitio.
 *
 * <p>Sustituye a la línea suelta que describía la arquitectura del proyecto:
 * eso interesa a quien lee el repositorio, no a quien está buscando dónde dejar
 * a su perro. Un pie con las condiciones, la privacidad y la ayuda a la vista es
 * además donde la gente da por hecho que están.
 */
export function AppFooter() {
  return (
    <footer className="mt-16 border-t border-sand-200 bg-white">
      <div className="mx-auto max-w-6xl px-4 py-10">
        <div className="grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
          <div>
            <p className="text-lg font-bold tracking-tight text-brand-700">🐾 PetVacay</p>
            <p className="mt-2 max-w-xs text-sm text-slate-500">
              Cuidadores verificados para tu mascota, con pago protegido de principio a fin.
            </p>
          </div>

          {COLUMNS.map((column) => (
            <nav key={column.title} aria-label={column.title}>
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
                {column.title}
              </p>
              <ul className="mt-3 flex flex-col gap-2">
                {column.links.map((link) => (
                  <li key={link.to}>
                    <Link
                      to={link.to}
                      className="text-sm text-slate-600 transition hover:text-brand-700"
                    >
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </nav>
          ))}
        </div>

        <div className="mt-10 flex flex-col gap-2 border-t border-sand-100 pt-6 text-xs text-slate-500 sm:flex-row sm:items-center sm:justify-between">
          <p>© {new Date().getFullYear()} PetVacay · Proyecto de demostración</p>
          <p>Microservicios con arquitectura hexagonal, CQRS y mensajería por eventos.</p>
        </div>
      </div>
    </footer>
  )
}
