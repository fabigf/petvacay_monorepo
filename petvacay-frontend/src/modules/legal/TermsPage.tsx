import { Link } from 'react-router-dom'
import {
  DemoNotice,
  LegalCallout,
  LegalList,
  LegalPage,
  LegalSection,
} from './LegalPage'

const SECTIONS = [
  { id: 'objeto', label: 'Objeto' },
  { id: 'cuentas', label: 'Cuentas y verificación' },
  { id: 'reservas', label: 'Reservas' },
  { id: 'precios', label: 'Precios y comisiones' },
  { id: 'pagos', label: 'Pagos y custodia' },
  { id: 'entrega', label: 'Entrega y recogida' },
  { id: 'cancelaciones', label: 'Cancelaciones' },
  { id: 'incidencias', label: 'Incidencias y disputas' },
  { id: 'resenas', label: 'Reseñas' },
  { id: 'conducta', label: 'Normas de uso' },
  { id: 'responsabilidad', label: 'Responsabilidad' },
]

/**
 * Condiciones del servicio.
 *
 * <p>Cada cifra y cada plazo de esta página sale de `negocio.md` y coincide con
 * lo que el código aplica de verdad: el 15% y el 5% son los de `PricingPolicy`,
 * las 24 y 48 horas las de `CancellationPolicy` y la máquina de estados. Si
 * alguna regla cambia en el dominio, este texto tiene que cambiar con ella —
 * unas condiciones que no describen el sistema real no sirven de nada.
 */
export function TermsPage() {
  return (
    <LegalPage
      title="Términos y condiciones"
      subtitle="Las reglas que rigen las reservas entre clientes y cuidadores en PetVacay."
      updatedAt="6 de agosto de 2026"
      sections={SECTIONS}
    >
      <DemoNotice />

      <LegalSection id="objeto" title="1. Objeto">
        <p>
          PetVacay es un <strong>marketplace de intermediación</strong>: pone en contacto a personas
          que necesitan cuidado para sus mascotas con cuidadores locales verificados, y gestiona la
          reserva, el cobro y la resolución de incidencias.
        </p>
        <p>
          La plataforma no presta directamente el servicio de cuidado. Quien aloja, pasea o cuida a
          la mascota es el cuidador, que actúa por cuenta propia y fija sus propias tarifas,
          capacidad y restricciones.
        </p>
      </LegalSection>

      <LegalSection id="cuentas" title="2. Cuentas y verificación de identidad">
        <p>
          Para reservar hace falta una cuenta con los datos de contacto reales y, al menos, una
          mascota registrada con su ficha completa: especie, raza, edad, peso, necesidades médicas,
          restricciones alimentarias y sociabilidad. Esa ficha la ve el cuidador de la reserva
          porque sin ella no puede decidir si acepta ni prepararse.
        </p>
        <p>Para ofrecer servicios como cuidador se exige además:</p>
        <LegalList
          items={[
            'Ser mayor de edad.',
            'Aportar un documento de identidad válido (DNI, NIE o pasaporte).',
            'Superar la revisión manual de ese documento por parte del equipo de PetVacay.',
          ]}
        />
        <LegalCallout title="Hasta que la verificación no se aprueba">
          El perfil permanece en estado <em>pendiente de verificación</em>: no aparece en el buscador
          y no puede recibir solicitudes de reserva.
        </LegalCallout>
      </LegalSection>

      <LegalSection id="reservas" title="3. Ciclo de vida de una reserva">
        <p>Toda reserva recorre los mismos estados:</p>
        <LegalList
          items={[
            <>
              <strong>Solicitada.</strong> El cliente elige fechas, servicio y mascotas. El cuidador
              dispone de <strong>24 horas</strong> para responder; pasado ese plazo la solicitud
              caduca automáticamente para no bloquear la búsqueda del cliente.
            </>,
            <>
              <strong>Pendiente de pago.</strong> El cuidador ha aceptado y el cliente debe abonar el
              importe para confirmarla.
            </>,
            <>
              <strong>Confirmada.</strong> El pago queda retenido en custodia y las fechas se
              bloquean en el calendario del cuidador.
            </>,
            <>
              <strong>En progreso.</strong> Desde la fecha de inicio del servicio.
            </>,
            <>
              <strong>Finalizada.</strong> Transcurridas 24 horas desde la fecha de fin sin ninguna
              disputa abierta.
            </>,
          ]}
        />
        <p>
          Una reserva solo se confirma si el cuidador tiene plazas libres para{' '}
          <strong>todas</strong> las mascotas incluidas durante <strong>todas</strong> las noches del
          periodo. El calendario de la aplicación ya muestra deshabilitadas las fechas que no caben.
        </p>
      </LegalSection>

      <LegalSection id="precios" title="4. Precios y comisiones">
        <p>
          El precio base lo fija libremente cada cuidador por noche o servicio, y se multiplica por
          el número de noches y por el número de mascotas.
        </p>
        <LegalCallout title="Modelo de comisión doble">
          Al cuidador se le retiene un <strong>15%</strong> sobre su precio base. Al cliente se le
          añade una <strong>tasa de gestión del 5%</strong> sobre el total en el momento del pago.
          Ambas sostienen la infraestructura, el soporte y la cobertura de la plataforma.
        </LegalCallout>
        <p>
          Ejemplo: 25&nbsp;€ por noche × 7 noches × 1 mascota = 175,00&nbsp;€ de tarifa base. El
          cliente paga 183,75&nbsp;€ (175,00 + 5%) y el cuidador percibe 148,75&nbsp;€ (175,00 −
          15%). El desglose completo se muestra siempre antes de pagar.
        </p>
      </LegalSection>

      <LegalSection id="pagos" title="5. Pago y depósito en custodia">
        <p>
          El cobro se realiza a través de un proveedor de pagos externo. PetVacay{' '}
          <strong>no almacena en ningún momento los datos de tu tarjeta</strong>: viajan cifrados
          directamente al proveedor.
        </p>
        <p>
          El importe no se entrega al cuidador al pagarlo: queda retenido en depósito (
          <em>escrow</em>). Se libera automáticamente <strong>24 horas después</strong> de la fecha
          de fin del servicio, siempre que no se haya abierto ninguna disputa. Si la hay, la
          liberación se detiene hasta que el equipo de PetVacay resuelva.
        </p>
      </LegalSection>

      <LegalSection id="entrega" title="6. Entrega y recogida de la mascota">
        <p>
          La mascota se entrega el día de inicio de la reserva y se recoge el día de fin, en ambos
          casos dentro de una franja horaria de <strong>09:00 a 20:00</strong>. Cliente y cuidador
          pueden acordar una hora concreta dentro de esa franja.
        </p>
        <LegalCallout title="La dirección exacta se revela al confirmar el pago">
          Antes de ese momento el cliente solo ve la zona aproximada del cuidador. Es una protección
          para el cuidador: evita que cualquiera con una cuenta pueda averiguar dónde vive sin haber
          llegado siquiera a reservar.
        </LegalCallout>
      </LegalSection>

      <LegalSection id="cancelaciones" title="7. Cancelaciones y reembolsos">
        <p>Qué se devuelve depende de quién cancela y de cuándo:</p>
        <LegalList
          items={[
            <>
              <strong>Cliente, con más de 48 horas de antelación:</strong> se reembolsa el 100% de la
              tarifa base. La tasa de gestión del 5% no es reembolsable.
            </>,
            <>
              <strong>Cliente, dentro de las 48 horas previas:</strong> se reembolsa todo excepto el
              importe de la primera noche, que se transfiere al cuidador como compensación por haber
              bloqueado su calendario.
            </>,
            <>
              <strong>Cuidador, una reserva ya confirmada:</strong> el cliente recupera el{' '}
              <strong>100%, tasas incluidas</strong>. Además el cuidador recibe una reseña negativa
              automática del sistema y pierde posiciones en el buscador.
            </>,
            <>
              <strong>Solicitud aún no pagada:</strong> se cancela sin ningún movimiento de dinero.
            </>,
          ]}
        />
      </LegalSection>

      <LegalSection id="incidencias" title="8. Incidencias y disputas">
        <p>
          Si durante el servicio ocurre una emergencia —enfermedad, accidente o daños— el cuidador
          dispone de un <strong>botón de alerta médica</strong> que avisa de inmediato al dueño y al
          equipo de soporte.
        </p>
        <p>
          El cliente puede abrir una <strong>disputa formal</strong> dentro de las 24 horas
          siguientes al fin del servicio. Al hacerlo, la liberación de fondos se detiene y la reserva
          queda congelada hasta que el equipo de PetVacay analice las pruebas y dicte una resolución,
          que puede ser a favor de una de las partes o un reparto intermedio.
        </p>
      </LegalSection>

      <LegalSection id="resenas" title="9. Reseñas">
        <p>
          Solo puede valorar quien ha contratado el servicio, y únicamente una vez la reserva ha
          finalizado. Se admite <strong>una reseña por reserva</strong>. El cuidador no puede
          escribir reseñas: es el sujeto valorado, no su autor.
        </p>
        <p>
          Las reseñas son públicas y no se editan ni se borran a petición del valorado. Sí se retiran
          las que incumplan las normas de uso.
        </p>
      </LegalSection>

      <LegalSection id="conducta" title="10. Normas de uso">
        <LegalList
          items={[
            'Facilitar información veraz sobre uno mismo y sobre la mascota. Ocultar una necesidad médica pone al animal en riesgo.',
            'No acordar pagos fuera de la plataforma: quedan fuera de la protección del depósito en custodia y de la cobertura en caso de incidencia.',
            'Tratar con respeto a la otra parte y al animal.',
            'No suplantar a otra persona ni crear cuentas para eludir una expulsión.',
          ]}
        />
        <p>
          PetVacay puede suspender una cuenta que incumpla estas normas. La suspensión no borra el
          historial: las reservas pasadas y su rastro económico se conservan.
        </p>
      </LegalSection>

      <LegalSection id="responsabilidad" title="11. Responsabilidad">
        <p>
          El cuidador es responsable del cuidado que presta y del cumplimiento de sus obligaciones
          legales y fiscales. El cliente es responsable de la veracidad de la ficha de su mascota y
          de su estado sanitario, incluida la vacunación.
        </p>
        <p>
          PetVacay responde de la correcta intermediación —verificación, cobro, custodia de fondos y
          arbitraje— pero no del resultado del servicio de cuidado en sí.
        </p>
        <p>
          ¿Dudas sobre alguno de estos puntos? Están explicados en lenguaje llano en las{' '}
          <Link to="/faq" className="font-medium text-brand-700 underline hover:text-brand-800">
            preguntas frecuentes
          </Link>
          , y el tratamiento de tus datos se detalla en la{' '}
          <Link to="/privacidad" className="font-medium text-brand-700 underline hover:text-brand-800">
            política de privacidad
          </Link>
          .
        </p>
      </LegalSection>
    </LegalPage>
  )
}
