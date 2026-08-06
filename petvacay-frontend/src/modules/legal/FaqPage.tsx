import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { LegalPage, LegalSection } from './LegalPage'

interface Question {
  q: string
  a: ReactNode
}

interface Group {
  id: string
  label: string
  questions: Question[]
}

/**
 * Las respuestas describen el comportamiento real de la aplicación, con las
 * mismas cifras y plazos que los términos. Se agrupan por a quién le preocupa
 * cada cosa —cliente, cuidador, dinero, seguridad— en vez de por orden de
 * llegada, para que se pueda ir directo al bloque que interesa.
 */
const GROUPS: Group[] = [
  {
    id: 'clientes',
    label: 'Si buscas cuidador',
    questions: [
      {
        q: '¿Cómo reservo?',
        a: (
          <>
            Busca cuidadores cerca de ti, elige las mascotas y las fechas, y envía la solicitud. El
            cuidador tiene 24 horas para aceptarla. Cuando acepte, pagas y la reserva queda
            confirmada.
          </>
        ),
      },
      {
        q: '¿Por qué hay días que no puedo seleccionar en el calendario?',
        a: (
          <>
            Porque ese cuidador no tiene plazas libres suficientes esos días{' '}
            <strong>para el número de mascotas que has marcado</strong>. Si desmarcas una mascota,
            es posible que esas fechas vuelvan a estar disponibles: el calendario se recalcula al
            instante.
          </>
        ),
      },
      {
        q: '¿Cuándo sé dónde vive el cuidador?',
        a: (
          <>
            La dirección exacta, con número y planta, aparece en el detalle de tu reserva{' '}
            <strong>en cuanto confirmas el pago</strong>, junto con el día y la franja horaria de
            entrega y de recogida. Antes de pagar solo ves la zona aproximada, para proteger la
            privacidad del cuidador.
          </>
        ),
      },
      {
        q: '¿A qué hora dejo y recojo a mi mascota?',
        a: (
          <>
            La entrega es el día de inicio de la reserva y la recogida el día de fin, ambas entre las{' '}
            <strong>09:00 y las 20:00</strong>. Dentro de esa franja podéis acordar la hora que os
            venga bien.
          </>
        ),
      },
      {
        q: '¿Puedo reservar para varias mascotas a la vez?',
        a: (
          <>
            Sí, siempre que el cuidador tenga plazas para todas. El precio se multiplica por el
            número de mascotas y por el número de noches, y lo ves desglosado antes de pagar.
          </>
        ),
      },
      {
        q: '¿Qué pasa si el cuidador no responde?',
        a: (
          <>
            La solicitud caduca sola a las 24 horas y no se te cobra nada. Así no te quedas
            esperando indefinidamente y puedes buscar a otra persona.
          </>
        ),
      },
    ],
  },
  {
    id: 'cuidadores',
    label: 'Si quieres ser cuidador',
    questions: [
      {
        q: '¿Qué necesito para empezar?',
        a: (
          <>
            Ser mayor de edad, completar tu perfil (vivienda, tarifas, capacidad y qué animales
            aceptas) y subir tu documento de identidad. Un administrador lo revisa manualmente.
          </>
        ),
      },
      {
        q: '¿Por qué no aparezco en el buscador?',
        a: (
          <>
            Hasta que tu verificación no está aprobada, tu perfil permanece en estado{' '}
            <em>pendiente</em>: no se indexa en el buscador ni puede recibir solicitudes. Es la
            garantía que hace que los clientes se fíen del resto de cuidadores.
          </>
        ),
      },
      {
        q: '¿Quién ve mi dirección?',
        a: (
          <>
            Solo tú, el equipo que revisa tu verificación, y el cliente de una reserva ya pagada.
            En tu ficha pública nunca aparecen la calle, el número ni la planta.
          </>
        ),
      },
      {
        q: '¿Cuánto cobro y cuándo?',
        a: (
          <>
            Percibes tu tarifa base menos una retención del <strong>15%</strong>. El dinero se
            libera automáticamente <strong>24 horas después</strong> de terminar el servicio, si no
            hay ninguna disputa abierta.
          </>
        ),
      },
      {
        q: '¿Qué ocurre si cancelo una reserva confirmada?',
        a: (
          <>
            El cliente recupera el 100%, tasas incluidas, y tu perfil recibe una reseña negativa
            automática que baja tu posición en el buscador. Cancelar deja a alguien sin solución a
            última hora, y el sistema lo refleja.
          </>
        ),
      },
      {
        q: '¿Puedo poner precios distintos por servicio?',
        a: (
          <>
            Sí. Cada servicio que ofreces —alojamiento nocturno, guardería de día o paseos— lleva su
            propia tarifa, y solo se te solicitan los que hayas activado.
          </>
        ),
      },
    ],
  },
  {
    id: 'dinero',
    label: 'Pagos y cancelaciones',
    questions: [
      {
        q: '¿Cómo se reparte lo que pago?',
        a: (
          <>
            Sobre una tarifa base de 175&nbsp;€, tú pagas 183,75&nbsp;€ (la base más un 5% de tasa de
            gestión) y el cuidador percibe 148,75&nbsp;€ (la base menos un 15% de comisión). El
            desglose completo aparece antes de confirmar.
          </>
        ),
      },
      {
        q: '¿Dónde está mi dinero mientras dura la reserva?',
        a: (
          <>
            Retenido en depósito por el proveedor de pagos. Ni PetVacay ni el cuidador lo tienen
            disponible hasta que el servicio termina correctamente. Es lo que te protege si algo sale
            mal.
          </>
        ),
      },
      {
        q: '¿Qué me devuelven si cancelo?',
        a: (
          <>
            Con más de 48 horas de antelación, el 100% de la tarifa base (la tasa de gestión del 5%
            no se devuelve). Dentro de las últimas 48 horas, todo excepto el importe de la primera
            noche, que compensa al cuidador por haber bloqueado esas fechas.
          </>
        ),
      },
      {
        q: '¿Guardáis los datos de mi tarjeta?',
        a: (
          <>
            No. Los datos de la tarjeta viajan cifrados directamente al proveedor de pagos y nunca
            pasan por los servidores de PetVacay.
          </>
        ),
      },
      {
        q: '¿Puedo pagarle al cuidador por fuera y ahorrarme las comisiones?',
        a: (
          <>
            No es recomendable y va contra las normas de uso: un pago fuera de la plataforma se queda
            sin depósito en custodia, sin cobertura en caso de incidencia y sin posibilidad de abrir
            una disputa.
          </>
        ),
      },
    ],
  },
  {
    id: 'incidencias',
    label: 'Incidencias y seguridad',
    questions: [
      {
        q: 'Mi mascota ha tenido un problema durante la estancia. ¿Qué hago?',
        a: (
          <>
            El cuidador dispone de un botón de alerta médica que te avisa de inmediato y notifica al
            equipo de soporte. Si al terminar el servicio no estás conforme, puedes abrir una disputa
            formal desde el detalle de la reserva.
          </>
        ),
      },
      {
        q: '¿Cuánto tiempo tengo para abrir una disputa?',
        a: (
          <>
            <strong>24 horas</strong> desde el fin del servicio. Al abrirla, el pago al cuidador se
            congela hasta que el equipo revise el caso y dicte una resolución.
          </>
        ),
      },
      {
        q: '¿Puede alguien ver mi reserva si conoce su enlace?',
        a: (
          <>
            No. El detalle de una reserva solo lo pueden consultar sus dos partes; cualquier otra
            cuenta recibe un acceso denegado aunque conozca el identificador exacto. Lo mismo aplica
            a la ficha de tus mascotas y a la dirección de entrega.
          </>
        ),
      },
      {
        q: '¿Quién puede dejar reseñas?',
        a: (
          <>
            Solo el cliente, y solo cuando la reserva ha finalizado. Una por reserva. El cuidador no
            puede escribir reseñas para que nadie influya en su propia reputación.
          </>
        ),
      },
    ],
  },
]

/**
 * Se usa `<details>` nativo en vez de un acordeón hecho a mano: trae gratis el
 * comportamiento de teclado, el anuncio correcto a lectores de pantalla y —lo
 * más útil aquí— la búsqueda del navegador encuentra el texto aunque la
 * respuesta esté plegada.
 */
function FaqItem({ question }: { question: Question }) {
  return (
    <details className="group border-b border-sand-200 py-1">
      <summary className="flex cursor-pointer list-none items-center justify-between gap-4 py-3 text-sm font-medium text-slate-800 transition hover:text-brand-700">
        {question.q}
        <span
          aria-hidden
          className="shrink-0 text-lg leading-none text-brand-500 transition group-open:rotate-45"
        >
          +
        </span>
      </summary>
      <div className="pb-4 pr-8 text-sm leading-relaxed text-slate-600">{question.a}</div>
    </details>
  )
}

export function FaqPage() {
  return (
    <LegalPage
      title="Preguntas frecuentes"
      subtitle="Lo que más se pregunta sobre reservas, pagos, verificación y privacidad."
      sections={GROUPS.map((group) => ({ id: group.id, label: group.label }))}
    >
      {GROUPS.map((group) => (
        <LegalSection key={group.id} id={group.id} title={group.label}>
          <div className="border-t border-sand-200">
            {group.questions.map((question) => (
              <FaqItem key={question.q} question={question} />
            ))}
          </div>
        </LegalSection>
      ))}

      <LegalSection id="mas-ayuda" title="¿No has encontrado tu respuesta?">
        <p>
          Escríbenos desde la página de{' '}
          <Link to="/contacto" className="font-medium text-brand-700 underline hover:text-brand-800">
            contacto
          </Link>{' '}
          y te respondemos. Las condiciones completas están en los{' '}
          <Link to="/terminos" className="font-medium text-brand-700 underline hover:text-brand-800">
            términos y condiciones
          </Link>
          .
        </p>
      </LegalSection>
    </LegalPage>
  )
}
