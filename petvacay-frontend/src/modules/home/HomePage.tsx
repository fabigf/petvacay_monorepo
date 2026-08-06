import { Link } from 'react-router-dom'
import { Button, Card } from '@/shared/components/ui'

const pillars = [
  {
    title: 'Identidad verificada',
    body: 'Ningún cuidador aparece en el buscador hasta que un administrador valida su documento de identidad.',
  },
  {
    title: 'Pago en custodia',
    body: 'El dinero queda retenido en escrow y solo se libera 24 horas después de terminar el servicio sin incidencias.',
  },
  {
    title: 'Reputación auditable',
    body: 'Reseñas cruzadas inmutables que premian el buen servicio y penalizan las cancelaciones del cuidador.',
  },
]

export function HomePage() {
  return (
    <div className="flex flex-col gap-12">
      <section className="text-center">
        <h1 className="mx-auto max-w-2xl text-4xl font-bold tracking-tight text-slate-900 sm:text-5xl">
          Vete de vacaciones tranquilo. Tu mascota también.
        </h1>
        <p className="mx-auto mt-4 max-w-xl text-slate-600">
          PetVacay conecta a dueños que viajan con cuidadores locales verificados. Alojamiento,
          guardería de día y paseos, con pago protegido de principio a fin.
        </p>
        <div className="mt-8 flex justify-center gap-3">
          <Link to="/search">
            <Button>Buscar cuidador</Button>
          </Link>
          <Link to="/register">
            <Button variant="secondary">Quiero ser cuidador</Button>
          </Link>
        </div>
      </section>

      <section className="grid gap-4 sm:grid-cols-3">
        {pillars.map((pillar) => (
          <Card key={pillar.title}>
            <h2 className="mb-2 font-semibold text-slate-800">{pillar.title}</h2>
            <p className="text-sm text-slate-600">{pillar.body}</p>
          </Card>
        ))}
      </section>

      <section>
        <div className="mb-4 flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="text-xl font-bold text-slate-800">Cómo funciona</h2>
          <Link
            to="/como-funciona"
            className="text-sm font-medium text-brand-700 underline hover:text-brand-800"
          >
            Ver el proceso completo
          </Link>
        </div>
        <ol className="grid gap-4 sm:grid-cols-4">
          {[
            'Busca cuidadores disponibles cerca de ti para tus fechas.',
            'Envía la solicitud: el cuidador tiene 24 horas para responder.',
            'Paga de forma segura; el importe queda retenido en custodia.',
            'Al terminar el servicio se libera el pago y dejáis reseña.',
          ].map((step, index) => (
            <li key={step} className="rounded-xl border border-sand-200 bg-white p-4">
              <span className="mb-2 inline-flex size-7 items-center justify-center rounded-full bg-brand-600 text-sm font-bold text-white">
                {index + 1}
              </span>
              <p className="text-sm text-slate-600">{step}</p>
            </li>
          ))}
        </ol>
      </section>
    </div>
  )
}
