import { useEffect } from 'react'
import type { ReactNode } from 'react'

/**
 * Armazón común de las páginas informativas y legales.
 *
 * <p>Existe para que todas compartan el mismo ancho de lectura, la misma
 * jerarquía tipográfica y el mismo índice lateral, en lugar de que cada página
 * invente su maquetación. Añadir una página nueva debería ser escribir su
 * contenido, no volver a decidir cómo se ve.
 *
 * <p>El ancho se limita a `65ch`: en una pantalla grande, un texto que ocupa
 * todo el ancho obliga al ojo a recorrer líneas largas y se pierde el renglón al
 * saltar de una a otra.
 */
export function LegalPage({
  title,
  subtitle,
  updatedAt,
  sections,
  children,
}: {
  title: string
  subtitle?: string
  /** Fecha de última revisión, en texto ya formateado. */
  updatedAt?: string
  /** Índice lateral. Cada `id` debe coincidir con el de su `<LegalSection>`. */
  sections?: Array<{ id: string; label: string }>
  children: ReactNode
}) {
  // El título de la pestaña acompaña a la navegación: sin esto todas las
  // páginas se llamarían igual en el historial y en los marcadores.
  useEffect(() => {
    const previous = document.title
    document.title = `${title} · PetVacay`
    return () => {
      document.title = previous
    }
  }, [title])

  return (
    <div className="flex flex-col gap-8">
      <header className="border-b border-sand-200 pb-6">
        <h1 className="text-3xl font-bold tracking-tight text-slate-900 sm:text-4xl">{title}</h1>
        {subtitle && <p className="mt-3 max-w-2xl text-slate-600">{subtitle}</p>}
        {updatedAt && (
          <p className="mt-4 text-xs text-slate-500">Última actualización: {updatedAt}</p>
        )}
      </header>

      <div className="flex flex-col gap-8 lg:flex-row lg:gap-12">
        {sections && sections.length > 0 && (
          <nav aria-label="Contenido de la página" className="lg:w-56 lg:shrink-0">
            <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-slate-400">
              En esta página
            </p>
            <ul className="flex flex-col gap-1 border-l border-sand-200">
              {sections.map((section) => (
                <li key={section.id}>
                  <a
                    href={`#${section.id}`}
                    className="-ml-px block border-l-2 border-transparent py-1 pl-3 text-sm text-slate-600 transition hover:border-brand-400 hover:text-brand-700"
                  >
                    {section.label}
                  </a>
                </li>
              ))}
            </ul>
          </nav>
        )}

        <div className="min-w-0 max-w-[65ch] flex-1 flex flex-col gap-10">{children}</div>
      </div>
    </div>
  )
}

/**
 * Sección con ancla. El `scroll-mt` compensa la cabecera fija: sin él, saltar
 * desde el índice deja el título justo debajo de la barra superior y parece que
 * el enlace ha fallado.
 */
export function LegalSection({
  id,
  title,
  children,
}: {
  id: string
  title: string
  children: ReactNode
}) {
  return (
    <section id={id} className="scroll-mt-24">
      <h2 className="mb-3 text-xl font-bold text-slate-800">{title}</h2>
      <div className="flex flex-col gap-3 text-sm leading-relaxed text-slate-600">{children}</div>
    </section>
  )
}

/** Lista con viñetas, con el mismo ritmo vertical que los párrafos. */
export function LegalList({ items }: { items: ReactNode[] }) {
  return (
    <ul className="flex list-disc flex-col gap-2 pl-5 marker:text-brand-400">
      {items.map((item, index) => (
        <li key={index}>{item}</li>
      ))}
    </ul>
  )
}

/**
 * Dato destacado dentro del texto legal: un porcentaje, un plazo. Se saca del
 * párrafo porque son las cifras que la gente busca sin leer el resto.
 */
export function LegalCallout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="rounded-lg border border-brand-200 bg-brand-50 px-4 py-3">
      <p className="text-sm font-semibold text-brand-800">{title}</p>
      <div className="mt-1 text-sm text-brand-900/80">{children}</div>
    </div>
  )
}

/**
 * Aviso de que esto es un proyecto de demostración.
 *
 * <p>Va en todas las páginas legales a propósito. El contenido describe con
 * fidelidad lo que el sistema hace de verdad, pero no es el documento legal de
 * una empresa real y presentarlo como tal induciría a error a quien lo lea.
 */
export function DemoNotice() {
  return (
    <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
      <p>
        <span className="font-semibold">PetVacay es un proyecto de demostración.</span> Estos textos
        describen el funcionamiento real de la aplicación, pero no constituyen las condiciones
        legales de una empresa en activo ni existe una entidad prestando estos servicios.
      </p>
    </div>
  )
}
