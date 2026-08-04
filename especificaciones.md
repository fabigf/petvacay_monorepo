# Documento de Especificaciones Técnicas y Contratos de API (especificaciones.md)

Este documento detalla los contratos de interfaces (JSON), esquemas de eventos, reglas de seguridad y manejo de errores para la integración entre el Frontend en React y los Microservicios.

---

## 1. Contratos de API REST (Consumidos por React)

Todas las peticiones del cliente React se dirigen al API Gateway (`localhost:8080`).

### 1.1. Búsqueda de Cuidadores (Módulo `search` de React)
* **Endpoint:** `GET /api/v1/search/sitters`
* **Parámetros de Query:** `lat`, `lng`, `startDate`, `endDate`, `petType`
* **Respuesta Exitosa (`200 OK`):**
```json
[
  {
    "sitterId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "firstName": "Carlos",
    "rating": 4.85,
    "distanceKm": 1.2,
    "pricePerNight": 25.00,
    "currency": "EUR",
    "homeType": "HOUSE_GARDEN"
  }
]
```

### 1.2. Creación de Reserva (Módulo `bookings` de React)
* **Endpoint:** `POST /api/v1/bookings`
* **Cabecera Requerida:** `Authorization: Bearer <JWT_TOKEN>`
* **Cuerpo de la Petición (`Request Body`):**
```json
{
  "sitterId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "petIds": ["3a2c0b1e-1111-4bad-9bdd-2b0d7b3dcb6a"],
  "startDate": "2026-08-10",
  "endDate": "2026-08-17",
  "serviceType": "NIGHT_ACCOMMODATION"
}
```
* **Respuesta Exitosa (`201 Created`):**
```json
{
  "bookingId": "bc731a5e-5a2a-4c12-8833-124b8cfde990",
  "status": "SOLICITADO",
  "finalAmountToPay": 183.75
}
```

---

## 2. Gestión de Seguridad en el Cliente React

Para evitar vulnerabilidades de seguridad como ataques XSS y CSRF, la autenticación se gestionará bajo un estricto estándar Stateless.

### 2.1. Interceptor de Axios (Client Side)
La aplicación de React debe instanciar un cliente HTTP globalizado que inserte de manera automática las credenciales en peticiones protegidas.

```typescript
// Ejemplo conceptual de implementación en src/core/api/apiClient.ts
import axios from 'axios';

const apiClient = axios.create({
  baseURL: 'http://localhost:8080/api/v1',
  timeout: 5000,
});

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token'); // O el estado de Zustand
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
}, (error) => {
  return Promise.reject(error);
});

export default apiClient;
```

### 2.2. Roles y Rutas Protegidas en React
El enrutador (`React Router Dom`) utilizará un componente de orden superior (HOC) o Wrapper para evaluar las `Granted Authorities` inyectadas en el JWT antes de renderizar vistas:
* Las rutas como `/dashboard/sitter` requieren el rol `ROLE_SITTER` y el atributo `is_verified: true`.
* Si un usuario no autenticado intenta acceder, React lo redirige automáticamente a `/login`.

---

## 3. Integración con Stripe Elements (React SDK)

Para evitar que los datos de pago toquen los servidores de PetVacay (Garantizando el cumplimiento de la normativa PCI-DSS), el microservicio de pagos y el frontend de React interactúan de la siguiente manera:

1. Cuando React solicita proceder al pago de una reserva, el backend genera un `PaymentIntent` en Stripe y devuelve un **`client_secret`**.
2. React carga el componente `<Elements stripe={stripePromise} options={{ clientSecret }}>` usando el SDK oficial de Stripe.
3. El usuario introduce su tarjeta en un formulario controlado de forma segura por un iframe incrustado de Stripe.
4. Al pulsar "Pagar", el formulario envía los datos directamente a los servidores de Stripe. Stripe procesa la transacción y notifica al Backend por un Webhook, mientras que React recibe la confirmación visual de éxito.

---

## 4. Gestión y Renderizado de Errores en React

El backend devuelve las excepciones utilizando la norma **RFC 7807 (Problem Details)**. La aplicación de React está obligada a capturar este formato en el bloque `catch` de Axios para pintar el error correspondiente en la interfaz de usuario.

* **Payload de error enviado por el Backend (`409 Conflict`):**
```json
{
  "type": "[https://api.petvacay.com/errors/sitter-unavailable](https://api.petvacay.com/errors/sitter-unavailable)",
  "title": "Cuidador No Disponible",
  "status": 409,
  "detail": "El cuidador seleccionado ya dispone de una reserva confirmada para esas fechas.",
  "code": "ERR_BOOKING_DATE_CONFLICT"
}
```

* **Estrategia de Renderizado en React:**
    1. **Errores de Formulario (`400 Bad Request`):** React leerá el objeto `invalid_params` (si existe) para marcar en rojo los campos específicos del formulario (ej: "Fecha de fin no puede ser anterior a la de inicio").
    2. **Errores de Negocio (`409 Conflict`):** React capturará el string de la propiedad `detail` y lo renderizará en una notificación Toast (ej. usando `react-hot-toast` o `Sonner`) para alertar al usuario sin romper el flujo de la pantalla.