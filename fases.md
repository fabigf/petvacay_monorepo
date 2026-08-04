# Plan de Ruta por Fases de Desarrollo (fases.md)

Este plan de ruta establece una estrategia evolutiva para construir la plataforma, integrando el desarrollo de **React** y **Spring Boot** en paralelo mediante hitos funcionales.

---

## Fase 1: El Núcleo de Dominio y Esqueleto Frontend
* **Objetivo:** Validar las reglas de negocio y establecer la estructura base de ambas aplicaciones.
* **Backend:** Crear el proyecto Spring Boot monolítico modular. Diseñar las entidades de dominio y casos de uso con Arquitectura Hexagonal y persistencia básica en PostgreSQL.
* **Frontend (React):** Inicializar el proyecto con Vite, TypeScript y Tailwind CSS. Configurar las rutas base (`/`, `/search`, `/dashboard`) y crear componentes mockeados (sin API real) para visualizar el flujo de pantallas.

## Fase 2: Conexión de API y Primer Flujo CQRS (Lectura Fast)
* **Objetivo:** Conectar React con el Backend y habilitar el motor de búsquedas optimizado.
* **Backend:** Levantar PostgreSQL y MongoDB con Docker. Implementar el patrón CQRS interno: al guardar un cuidador en PostgreSQL, se emite un evento interno de Spring que actualiza MongoDB. Exponer el endpoint de búsqueda.
* **Frontend (React):** Implementar el módulo de búsqueda. Crear el buscador por filtros (fechas, tamaño de mascota) y conectar Axios para renderizar las tarjetas de los cuidadores consumiendo datos reales de MongoDB.

## Fase 3: Arquitectura Orientada a Eventos con Apache Kafka
* **Objetivo:** Desacoplar los procesos del backend mediante mensajería asíncrona sin romper la experiencia del cliente.
* **Backend:** Añadir Apache Kafka al entorno Docker. Reemplazar los eventos internos por productores y consumidores de Kafka.
* **Frontend (React):** Diseñar estados visuales de "Cargando" o "Procesando" para flujos asíncronos. Manejar la experiencia de usuario (UX) asumiendo la Consistencia Eventual del sistema.

## Fase 4: Desacoplamiento en Microservicios y API Gateway
* **Objetivo:** Separar el backend en servicios independientes manteniendo una única IP para el Frontend.
* **Backend:** Romper el monolito en 4 microservicios independientes (`identity`, `booking`, `search`, `payment`) con sus respectivas bases de datos independientes. Configurar **Spring Cloud Gateway**.
* **Frontend (React):** Configurar la URL base de Axios apuntando exclusivamente al puerto del Gateway (`http://localhost:8080`). React no se entera de que el backend se ha dividido en microservicios, lo que evita configuraciones complejas de CORS.

## Fase 5: Seguridad (JWT) y Flujo Completo de Reserva
* **Objetivo:** Proteger el sistema y permitir transacciones seguras de punta a punta.
* **Backend:** Implementar Spring Security con autenticación basada en JWT (claves públicas/privadas RS256).
* **Frontend (React):** Crear las pantallas de Login y Registro. Implementar un **Interceptor de Axios** que adjunte automáticamente el token JWT en la cabecera `Authorization: Bearer` en cada petición protegida. Guardar de forma segura el estado de la sesión utilizando Zustand o Context API.

## Fase 6: Pasarela de Pagos (Stripe) y Lanzamiento del MVP
* **Objetivo:** Integrar la facturación real y desplegar la plataforma completa.
* **Backend:** Desarrollar el `payment-service` integrado con webhooks de Stripe Connect.
* **Frontend (React):** Integrar **Stripe Elements (React SDK)** en la pantalla de checkout para capturar los datos de la tarjeta de crédito de forma segura (cumpliendo normativas PCI sin que los datos toquen tu base de datos).
* **Despliegue:** Dockerizar la aplicación React para producción usando un servidor **Nginx** como *reverse proxy* y subir todo el ecosistema a la nube (AWS / DigitalOcean).