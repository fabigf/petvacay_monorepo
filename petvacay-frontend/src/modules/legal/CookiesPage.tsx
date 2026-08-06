import { Link } from 'react-router-dom'
import { DemoNotice, LegalCallout, LegalPage, LegalSection } from './LegalPage'

const SECTIONS = [
  { id: 'que-usamos', label: 'Qué usamos' },
  { id: 'detalle', label: 'Detalle' },
  { id: 'no-usamos', label: 'Lo que no usamos' },
  { id: 'gestion', label: 'Cómo gestionarlas' },
]

interface StorageItem {
  name: string
  kind: string
  purpose: string
  duration: string
}

/**
 * Almacenamiento que la aplicación usa de verdad. La lista es corta porque el
 * inventario real es corto: no hay analítica ni publicidad.
 */
const ITEMS: StorageItem[] = [
  {
    name: 'petvacay_refresh',
    kind: 'Cookie (HttpOnly, Secure, SameSite=Strict)',
    purpose:
      'Mantiene la sesión iniciada al recargar la página. El JavaScript de la web no puede leerla y solo se envía a las rutas de autenticación.',
    duration: 'Hasta cerrar sesión o hasta que caduca',
  },
  {
    name: 'petvacay-auth',
    kind: 'Almacenamiento local del navegador',
    purpose:
      'Guarda tu nombre y tus roles para pintar la interfaz sin parpadeo mientras se restaura la sesión. No contiene ninguna credencial.',
    duration: 'Hasta cerrar sesión o borrar los datos del navegador',
  },
]

export function CookiesPage() {
  return (
    <LegalPage
      title="Política de cookies"
      subtitle="Qué guardamos en tu navegador y por qué. Son cuatro cosas y ninguna sirve para perseguirte."
      updatedAt="6 de agosto de 2026"
      sections={SECTIONS}
    >
      <DemoNotice />

      <LegalSection id="que-usamos" title="Qué usamos">
        <p>
          PetVacay usa exclusivamente almacenamiento <strong>técnico y necesario</strong>: lo
          imprescindible para que puedas iniciar sesión y seguir dentro al recargar la página. No hay
          cookies de analítica, de publicidad ni de terceros con fines de seguimiento.
        </p>
        <LegalCallout title="Por eso no verás un banner de consentimiento">
          Las cookies estrictamente necesarias para prestar un servicio que el usuario ha solicitado
          no requieren consentimiento previo. Si algún día se añadiera analítica, aparecería el
          banner correspondiente y podrías rechazarla.
        </LegalCallout>
      </LegalSection>

      <LegalSection id="detalle" title="Detalle de lo que se almacena">
        <div className="-mx-1 overflow-x-auto">
          <table className="w-full min-w-[34rem] border-collapse text-left text-sm">
            <thead>
              <tr className="border-b border-sand-200">
                <th scope="col" className="py-2 pr-4 font-semibold text-slate-700">
                  Nombre
                </th>
                <th scope="col" className="py-2 pr-4 font-semibold text-slate-700">
                  Tipo
                </th>
                <th scope="col" className="py-2 pr-4 font-semibold text-slate-700">
                  Para qué
                </th>
                <th scope="col" className="py-2 font-semibold text-slate-700">
                  Duración
                </th>
              </tr>
            </thead>
            <tbody>
              {ITEMS.map((item) => (
                <tr key={item.name} className="border-b border-sand-100 align-top">
                  <td className="py-3 pr-4 font-mono text-xs text-slate-800">{item.name}</td>
                  <td className="py-3 pr-4 text-slate-600">{item.kind}</td>
                  <td className="py-3 pr-4 text-slate-600">{item.purpose}</td>
                  <td className="py-3 text-slate-600">{item.duration}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p>
          El <strong>token de acceso</strong> con el que se firma cada petición no aparece en esta
          tabla porque no se almacena: vive solo en la memoria de la pestaña y desaparece al
          cerrarla. Es deliberado — así no hay de dónde robarlo.
        </p>
      </LegalSection>

      <LegalSection id="no-usamos" title="Lo que no usamos">
        <p>
          Sin cookies publicitarias, sin píxeles de redes sociales, sin perfilado de comportamiento y
          sin venta de datos a terceros. Cuando buscas una dirección al configurar un perfil de
          cuidador se consulta un servicio de mapas externo, pero esa consulta no deja ninguna cookie
          en tu navegador.
        </p>
      </LegalSection>

      <LegalSection id="gestion" title="Cómo gestionarlas">
        <p>
          Puedes borrar estos datos desde la configuración de tu navegador o, más sencillo, cerrando
          sesión: eso revoca la cookie en el servidor y limpia el almacenamiento local.
        </p>
        <p>
          Ten en cuenta que bloquear la cookie de sesión impide mantener la sesión iniciada: cada
          recarga de página te devolvería a la pantalla de acceso.
        </p>
        <p>
          Más información sobre el tratamiento de datos en la{' '}
          <Link to="/privacidad" className="font-medium text-brand-700 underline hover:text-brand-800">
            política de privacidad
          </Link>
          .
        </p>
      </LegalSection>
    </LegalPage>
  )
}
