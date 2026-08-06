import { Link } from 'react-router-dom'
import { Button } from '@/shared/components/ui'
import { LegalCallout, LegalList, LegalPage, LegalSection } from './LegalPage'

const SECTIONS = [
  { id: 'clientes', label: 'Para clientes' },
  { id: 'cuidadores', label: 'Para cuidadores' },
  { id: 'confianza', label: 'Por qué es seguro' },
]

interface Step {
  title: string
  body: string
}

const CLIENT_STEPS: Step[] = [
  {
    title: 'Busca',
    body: 'Indica tu zona y encuentra cuidadores verificados cerca de ti, con sus tarifas, su tipo de vivienda y su valoración.',
  },
  {
    title: 'Solicita',
    body: 'Elige mascotas, servicio y fechas. El calendario ya oculta los días sin plazas para las mascotas que has marcado.',
  },
  {
    title: 'Paga con protección',
    body: 'Cuando el cuidador acepta, pagas y el importe queda retenido en custodia. Recibes la dirección exacta y la franja de entrega.',
  },
  {
    title: 'Disfruta y valora',
    body: 'Al terminar el servicio se libera el pago y puedes dejar tu reseña. Si algo falló, tienes 24 horas para abrir una disputa.',
  },
]

const SITTER_STEPS: Step[] = [
  {
    title: 'Crea tu perfil',
    body: 'Describe tu vivienda, tus tarifas por servicio, cuántas mascotas puedes atender a la vez y qué animales aceptas.',
  },
  {
    title: 'Verifica tu identidad',
    body: 'Sube tu documento de identidad. Un administrador lo revisa manualmente antes de publicar tu perfil.',
  },
  {
    title: 'Recibe solicitudes',
    body: 'Aparecerás en el buscador de tu zona. Tienes 24 horas para aceptar o rechazar cada solicitud.',
  },
  {
    title: 'Cobra sin sorpresas',
    body: 'El dinero está retenido desde que el cliente paga, y se te libera 24 horas después de terminar el servicio.',
  },
]

function Steps({ steps }: { steps: Step[] }) {
  return (
    <ol className="grid gap-4 sm:grid-cols-2">
      {steps.map((step, index) => (
        <li key={step.title} className="rounded-xl border border-sand-200 bg-white p-4">
          <span className="mb-2 inline-flex size-7 items-center justify-center rounded-full bg-brand-600 text-sm font-bold text-white">
            {index + 1}
          </span>
          <p className="font-semibold text-slate-800">{step.title}</p>
          <p className="mt-1 text-sm text-slate-600">{step.body}</p>
        </li>
      ))}
    </ol>
  )
}

export function HowItWorksPage() {
  return (
    <LegalPage
      title="Cómo funciona PetVacay"
      subtitle="De la búsqueda al pago, explicado paso a paso para las dos partes."
      sections={SECTIONS}
    >
      <LegalSection id="clientes" title="Si buscas cuidador">
        <Steps steps={CLIENT_STEPS} />
        <LegalCallout title="El precio, siempre antes de pagar">
          Verás desglosada la tarifa base, la tasa de gestión del 5% y el total, calculado sobre el
          número real de noches y de mascotas.
        </LegalCallout>
        <div className="pt-1">
          <Link to="/search">
            <Button>Buscar cuidador</Button>
          </Link>
        </div>
      </LegalSection>

      <LegalSection id="cuidadores" title="Si quieres ofrecer tus servicios">
        <Steps steps={SITTER_STEPS} />
        <div className="pt-1">
          <Link to="/register">
            <Button variant="secondary">Quiero ser cuidador</Button>
          </Link>
        </div>
      </LegalSection>

      <LegalSection id="confianza" title="Por qué puedes fiarte">
        <p>La plataforma se apoya en tres mecanismos, no en la buena voluntad:</p>
        <LegalList
          items={[
            <>
              <strong>Verificación previa.</strong> Ningún cuidador aparece en el buscador hasta que
              una persona del equipo revisa su documento de identidad.
            </>,
            <>
              <strong>Pago en custodia.</strong> El dinero no llega al cuidador hasta 24 horas
              después de terminar el servicio. Si abres una disputa, se congela.
            </>,
            <>
              <strong>Reputación con consecuencias.</strong> Solo valora quien contrató el servicio,
              y cancelar una reserva confirmada penaliza automáticamente al cuidador en el buscador.
            </>,
          ]}
        />
        <p>
          El detalle completo está en los{' '}
          <Link to="/terminos" className="font-medium text-brand-700 underline hover:text-brand-800">
            términos y condiciones
          </Link>
          .
        </p>
      </LegalSection>
    </LegalPage>
  )
}
