import { Link } from 'react-router-dom'
import { DemoNotice, LegalCallout, LegalList, LegalPage, LegalSection } from './LegalPage'

const SECTIONS = [
  { id: 'urgencias', label: 'Urgencias' },
  { id: 'canales', label: 'Canales' },
  { id: 'antes', label: 'Antes de escribir' },
]

export function ContactPage() {
  return (
    <LegalPage
      title="Contacto y ayuda"
      subtitle="Cómo llegar hasta nosotros según lo que necesites."
      sections={SECTIONS}
    >
      <DemoNotice />

      <LegalSection id="urgencias" title="Si hay una urgencia con un animal">
        <LegalCallout title="No esperes a que te respondamos">
          Ante un problema de salud grave, acude directamente a un veterinario. Si eres el cuidador,
          usa además el <strong>botón de alerta médica</strong> del detalle de la reserva: avisa al
          dueño al instante y notifica a nuestro equipo.
        </LegalCallout>
        <p>
          Ese botón está en la pantalla de la reserva en curso. Deja constancia de la incidencia en
          el expediente, lo que resulta decisivo si después hay que resolver una disputa.
        </p>
      </LegalSection>

      <LegalSection id="canales" title="Canales de atención">
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="rounded-xl border border-sand-200 bg-white p-4">
            <p className="font-semibold text-slate-800">Soporte general</p>
            <p className="mt-1 text-sm text-slate-600">
              Dudas sobre reservas, pagos, verificación o tu cuenta.
            </p>
            <p className="mt-2 font-mono text-sm text-brand-700">soporte@petvacay.test</p>
            <p className="mt-1 text-xs text-slate-500">Respuesta en 24-48 h laborables</p>
          </div>

          <div className="rounded-xl border border-sand-200 bg-white p-4">
            <p className="font-semibold text-slate-800">Privacidad y datos</p>
            <p className="mt-1 text-sm text-slate-600">
              Acceso, corrección o supresión de tus datos personales.
            </p>
            <p className="mt-2 font-mono text-sm text-brand-700">privacidad@petvacay.test</p>
            <p className="mt-1 text-xs text-slate-500">Respuesta en un plazo máximo de 30 días</p>
          </div>
        </div>
        <p className="text-xs text-slate-500">
          Estas direcciones usan el dominio <code>.test</code> porque PetVacay es un proyecto de
          demostración: no hay un buzón real detrás.
        </p>
      </LegalSection>

      <LegalSection id="antes" title="Antes de escribir">
        <p>Muchas consultas ya están resueltas y te ahorran la espera:</p>
        <LegalList
          items={[
            <>
              Cómo funcionan las reservas, los pagos y las cancelaciones:{' '}
              <Link to="/faq" className="font-medium text-brand-700 underline hover:text-brand-800">
                preguntas frecuentes
              </Link>
              .
            </>,
            <>
              Reglas completas del servicio:{' '}
              <Link
                to="/terminos"
                className="font-medium text-brand-700 underline hover:text-brand-800"
              >
                términos y condiciones
              </Link>
              .
            </>,
            <>
              Qué datos tratamos y quién los ve:{' '}
              <Link
                to="/privacidad"
                className="font-medium text-brand-700 underline hover:text-brand-800"
              >
                política de privacidad
              </Link>
              .
            </>,
          ]}
        />
        <p>
          Si escribes sobre una reserva concreta, incluye su identificador —lo tienes en el detalle
          de la reserva, bajo el epígrafe «Id de reserva»—: nos permite localizarla sin pedirte más
          datos.
        </p>
      </LegalSection>
    </LegalPage>
  )
}
