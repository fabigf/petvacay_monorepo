import { Link } from 'react-router-dom'
import {
  DemoNotice,
  LegalCallout,
  LegalList,
  LegalPage,
  LegalSection,
} from './LegalPage'

const SECTIONS = [
  { id: 'responsable', label: 'Quién trata tus datos' },
  { id: 'datos', label: 'Qué datos tratamos' },
  { id: 'finalidad', label: 'Para qué' },
  { id: 'direccion', label: 'Tu dirección' },
  { id: 'mascotas', label: 'Datos de la mascota' },
  { id: 'terceros', label: 'Con quién se comparten' },
  { id: 'conservacion', label: 'Cuánto se conservan' },
  { id: 'seguridad', label: 'Cómo se protegen' },
  { id: 'derechos', label: 'Tus derechos' },
]

/**
 * Política de privacidad.
 *
 * <p>Describe el tratamiento que la aplicación hace realmente: qué guarda cada
 * servicio, qué sale por cada endpoint y en qué momento. La sección sobre la
 * dirección del cuidador es la más importante y la que está respaldada por
 * tests (`IdentityAuthorizationTest`), porque es la promesa más fácil de
 * romper sin darse cuenta.
 */
export function PrivacyPage() {
  return (
    <LegalPage
      title="Política de privacidad"
      subtitle="Qué datos recogemos, para qué los usamos y quién puede verlos."
      updatedAt="6 de agosto de 2026"
      sections={SECTIONS}
    >
      <DemoNotice />

      <LegalSection id="responsable" title="Quién trata tus datos">
        <p>
          PetVacay trata los datos necesarios para gestionar tu cuenta y tus reservas. Este apartado
          explica exactamente qué se guarda y quién llega a verlo dentro de la plataforma.
        </p>
      </LegalSection>

      <LegalSection id="datos" title="Qué datos tratamos">
        <LegalList
          items={[
            <>
              <strong>Cuenta:</strong> nombre, apellidos, correo electrónico, fecha de nacimiento y
              contraseña. La contraseña se guarda <em>cifrada con BCrypt</em>, nunca en claro: ni el
              equipo de PetVacay puede leerla.
            </>,
            <>
              <strong>Perfil de cuidador:</strong> descripción, tipo de vivienda, fotografía,
              tarifas, capacidad, especies aceptadas y peso máximo admitido.
            </>,
            <>
              <strong>Ubicación del cuidador:</strong> dirección completa (calle, número y planta) y
              coordenadas geográficas.
            </>,
            <>
              <strong>Documento de identidad</strong> del cuidador, para la verificación previa.
            </>,
            <>
              <strong>Mascotas:</strong> nombre, especie, raza, edad, peso, necesidades médicas,
              restricciones alimentarias y sociabilidad.
            </>,
            <>
              <strong>Reservas y pagos:</strong> fechas, servicio, importes y estado. Los datos de tu
              tarjeta los trata directamente el proveedor de pagos y{' '}
              <strong>no se almacenan en PetVacay</strong>.
            </>,
          ]}
        />
      </LegalSection>

      <LegalSection id="finalidad" title="Para qué se usan">
        <p>
          Para prestar el servicio y para nada más: mostrar cuidadores cerca de ti, calcular precios,
          validar que hay plazas libres, cobrar y liquidar, avisarte de lo que pasa con tus reservas
          y arbitrar en caso de incidencia.
        </p>
        <p>No se venden a terceros ni se usan para publicidad de otras empresas.</p>
      </LegalSection>

      <LegalSection id="direccion" title="Tu dirección, si eres cuidador">
        <p>
          La dirección exacta de un cuidador es el dato más sensible de la plataforma y recibe un
          trato específico:
        </p>
        <LegalCallout title="Quién ve tu dirección exacta">
          Solo tú, el equipo de administración durante la verificación, y el cliente de una reserva{' '}
          <strong>a partir del momento en que confirma el pago</strong>. Nadie más.
        </LegalCallout>
        <LegalList
          items={[
            'En el buscador y en tu ficha pública solo se muestra la distancia aproximada y el municipio: nunca la calle, el número ni la planta.',
            'Un cliente con una solicitud aún sin pagar no ve tu dirección.',
            'La ficha pública que consulta cualquier usuario registrado se sirve deliberadamente sin los campos de dirección, no simplemente ocultos en pantalla.',
          ]}
        />
        <p>
          Las coordenadas aproximadas sí se usan en el buscador, porque son la base del cálculo de
          distancia que permite encontrar cuidadores cerca.
        </p>
      </LegalSection>

      <LegalSection id="mascotas" title="Datos de tu mascota">
        <p>
          La ficha de la mascota —incluidas sus necesidades médicas— se comparte con el cuidador de
          una reserva concreta. No es opcional: sin conocer el peso, la especie o si necesita
          medicación, un cuidador no puede decidir si acepta ni prepararse para atenderla.
        </p>
        <p>
          Fuera de esa reserva, la ficha no es visible para nadie más. Otro usuario no puede
          consultarla aunque conozca su identificador.
        </p>
      </LegalSection>

      <LegalSection id="terceros" title="Con quién se comparten">
        <LegalList
          items={[
            <>
              <strong>Proveedor de pagos:</strong> tramita el cobro, la custodia y la liquidación.
              Recibe el importe y una referencia de la reserva.
            </>,
            <>
              <strong>Servicio de mapas:</strong> al buscar una dirección durante el alta de un
              perfil de cuidador se consulta un servicio de geocodificación externo con el texto que
              se escribe en el buscador de direcciones.
            </>,
            <>
              <strong>La otra parte de la reserva:</strong> nombre de pila, y la dirección solo en
              las condiciones descritas arriba.
            </>,
          ]}
        />
      </LegalSection>

      <LegalSection id="conservacion" title="Cuánto tiempo se conservan">
        <p>
          Mientras la cuenta esté activa. Al dar de baja una cuenta se desactiva el acceso pero{' '}
          <strong>se conserva el historial de reservas y su rastro económico</strong>: son
          operaciones con obligaciones contables y con posibles reclamaciones asociadas, y borrarlas
          dejaría a la otra parte sin constancia de algo en lo que participó.
        </p>
      </LegalSection>

      <LegalSection id="seguridad" title="Cómo se protegen">
        <LegalList
          items={[
            'Las contraseñas se guardan con BCrypt, un algoritmo diseñado para que probar contraseñas resulte lento.',
            'La sesión usa un token de corta duración que vive solo en memoria del navegador, y un token de renovación en una cookie que el JavaScript de la página no puede leer.',
            'Ese token de renovación rota en cada uso: si alguien reutiliza uno ya consumido, se revoca la sesión entera por considerarse robada.',
            'Cada petición se valida en el servidor con la firma del token. Manipular datos en el navegador no concede ningún permiso adicional.',
            'Los intentos de inicio de sesión están limitados por origen para impedir ataques de fuerza bruta.',
          ]}
        />
      </LegalSection>

      <LegalSection id="derechos" title="Tus derechos">
        <p>
          Puedes acceder a tus datos y corregirlos desde tu perfil, y solicitar la baja de tu cuenta
          o una copia de tu información escribiendo al equipo de soporte desde la página de{' '}
          <Link to="/contacto" className="font-medium text-brand-700 underline hover:text-brand-800">
            contacto
          </Link>
          .
        </p>
        <p>
          El uso de cookies y almacenamiento local se detalla en la{' '}
          <Link to="/cookies" className="font-medium text-brand-700 underline hover:text-brand-800">
            política de cookies
          </Link>
          .
        </p>
      </LegalSection>
    </LegalPage>
  )
}
