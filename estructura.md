# Arquitectura Técnica, Infraestructura y Base de Datos (estructura.md)

Este documento detalla el diseño de software distribuido utilizando **React (Frontend)** y un ecosistema de **Microservicios, Arquitectura Hexagonal, CQRS y Apache Kafka** bajo **Java y Spring Boot**.

---

## 1. Topología del Sistema (Ecosistema Completo)

Para garantizar la escalabilidad, el sistema se divide en un cliente de interfaz único y múltiples microservicios desacoplados:

1. **Frontend Client (React SPA):** Aplicación de página única construida con React, TypeScript y Vite. Se comunica exclusivamente con el API Gateway.
2. **API Gateway (Spring Cloud Gateway):** Punto de entrada perimetral. Se encarga del enrutamiento, seguridad (validación de JWT) y rate limiting.
3. **Identity & User Service:** Gestiona la autenticación, perfiles de clientes, mascotas y el proceso KYC de los cuidadores (DB: PostgreSQL).
4. **Booking (Command) Service:** Gestiona el estado transaccional y las reglas de negocio de las reservas (DB: PostgreSQL).
5. **Search & Discovery (Query) Service:** Motor de búsqueda hiperrápido que lee datos de cuidadores optimizados (DB: MongoDB).
6. **Payment Service:** Orquesta la integración con la API de Stripe Connect y las transferencias de liquidación (DB: PostgreSQL).
7. **Notification Service:** Escucha eventos de Kafka y envía correos o push notifications (Memoria/Redis para plantillas).

---

## 2. Arquitectura del Frontend (React Client)

El Frontend no se conecta a las bases de datos ni a los microservicios individuales; consume únicamente el puerto expuesto por el Gateway.

### Estructura de Carpetas del Frontend (React):
```text
petvacay-frontend/
├── public/
├── src/
│   ├── assets/                     # Imágenes, logos, fuentes globales
│   ├── core/                       # Configuración base inmutable
│   │   ├── api/                    # Cliente Axios con Interceptores de Seguridad
│   │   ├── router/                 # React Router Dom (Rutas públicas/protegidas)
│   │   └── security/               # Manejo del estado de autenticación (Context API/Zustand)
│   ├── shared/                     # Componentes reutilizables globales
│   │   └── components/             # UI Components (Buttons, Modals, Navbar)
│   └── modules/                    # Módulos encapsulados por dominio de negocio
│       ├── search/                 # Módulo de Búsqueda (Filtros, Mapas, Tarjetas de Sitters)
│       ├── bookings/               # Módulo de Reservas (Formularios, Historial, Estados)
│       └── onboarding/             # Módulo KYC (Subida de DNI para Cuidadores)
├── package.json
└── vite.config.ts
```

### Tecnologías Clave en el Frontend:
* **Vite + TypeScript:** Para un tipado estricto en el manejo de estados de la mascota y contratos de API.
* **Tailwind CSS + Shadcn/ui:** Para el diseño ágil de la interfaz de usuario.
* **Axios:** Con interceptores automáticos para inyectar el Bearer Token.
* **Zustand:** Gestión de estado global ligero para la sesión del usuario y el carrito/pre-reserva.

---

## 3. Arquitectura Hexagonal en el Backend (Puertos y Adaptadores)

Cada microservicio backend aísla por completo las reglas de negocio de la infraestructura y del cliente React mediante el patrón Hexagonal.

### Estructura de Paquetes en Spring Boot (Ejemplo para `Booking Service`):
```text
com.petvacay.bookingservice
│
├── domain/                         # El Núcleo Puro (Java puro, sin dependencias de frameworks)
│   ├── model/                      # Objetos de Dominio (Booking, Status)
│   └── valueobjects/               # Objetos de Valor inmutables (Price, DateRange)
│
├── ports/                          # Fronteras del Hexágono
│   ├── in/                         # Casos de Uso / Interfaces de Entrada (Commands de React)
│   │   └── CreateBookingUseCase.java
│   └── out/                        # Interfaces de Salida (SPI - Service Provider Interface)
│       ├── BookingRepositoryPort.java
│       └── BookingEventPublisherPort.java
│
└── adapters/                       # Capa Externa (Dependiente de Tecnologías)
    ├── in/                         # Adaptadores de Entrada
    │   ├── web/                    # Controladores REST que exponen JSON a React
    │   └── kafka/                  # Consumidores de Mensajes de Kafka
    └── out/                        # Adaptadores de Salida
        ├── persistence/            # Implementación JPA / Spring Data PostgreSQL
        └── messaging/              # Implementación Kafka Producer
```

---

## 4. Patrón CQRS (Command Query Responsibility Segregation)

Separamos radicalmente las operaciones de escritura (comandos enviados desde React) de las operaciones de lectura (búsquedas del marketplace):

* **Flujo de Escritura:** React envía un formulario de reserva (POST). El Gateway lo envía a **Booking Service**, que ejecuta lógica transaccional en **PostgreSQL** y publica un evento a **Apache Kafka**.
* **Flujo de Lectura:** El microservicio **Search & Discovery** consume de Kafka y actualiza vistas planas en **MongoDB**. Cuando el usuario interactúa con los filtros de búsqueda en React, la petición (GET) va directamente a MongoDB, garantizando respuestas instantáneas (< 50ms) en la UI.

---

## 5. Infraestructura y Dockerización Local

El entorno completo, incluyendo el servidor de desarrollo de React, se orquesta localmente mediante Docker Compose.

### Arquitectura de Contenedores (`docker-compose.yml`):
* `petvacay-frontend`: Contenedor Node.js corriendo el servidor de desarrollo de React (Vite) en el puerto `3000`.
* `petvacay-gateway`: Spring Cloud Gateway en el puerto `8080` (Hace de proxy inverso para evitar problemas de CORS con React).
* `identity-service`, `booking-service`, `search-service`, `payment-service`: Aplicaciones Spring Boot independientes.
* `postgres-db` & `mongodb`: Motores de bases de datos aislados.
* `kafka` + `zookeeper`: Clúster de mensajería asíncrona local.