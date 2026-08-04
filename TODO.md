# TODO — PetVacay

Pendientes reales detectados durante el desarrollo y la auditoría final, no
tareas inventadas. Cada uno indica por qué importa y, si aplica, dónde tocar.

---

## Negocio (auditoría final — nada de esto lo pidió `fases.md`, pero sí `negocio.md`)

### 🔴 Crítico

- [ ] **Los fondos no se liberan automáticamente.** `negocio.md` §3.2 dice que
      el sistema libera los fondos 24h después de finalizar el servicio, pero
      solo existe un `@Scheduled` (`BookingLifecycleScheduler.expireStaleRequests`)
      y no dispara `start()`/`complete()` — esas transiciones solo se llaman a
      mano vía API. **En la práctica el cuidador nunca cobra solo.**
- [ ] **No existe forma de resolver una disputa.** `negocio.md` §4.4 dice que
      "el equipo de administración analiza las pruebas... y dicta una
      resolución manual", pero no hay ningún endpoint que saque una reserva de
      `EN_DISPUTA` — ni siquiera uno para listar qué reservas están en disputa
      (un admin tendría que conocer ya el `bookingId` de memoria). El propio
      dominio (`Booking.java`) tiene el comentario *"Una reserva en disputa
      solo puede resolverla el equipo de administración"* pero no hay caso de
      uso que lo haga. Una vez una reserva entra en disputa, **se queda
      congelada para siempre**, sin ninguna vía de recuperación. Falta:
      - Caso de uso en `booking-service` (`ManageBookingUseCase.resolveDispute`
        o similar) con las dos resoluciones posibles (a favor del cliente / del
        cuidador, o un reparto) y su propio `CancellationOutcome`.
      - Endpoint `GET /api/v1/admin/bookings?status=EN_DISPUTA` para listarlas.
      - Endpoint `POST /api/v1/admin/bookings/{id}/resolve-dispute`.

### 🟠 Alto

- [ ] **`MascotaEnPeligroEvent` no está implementado.** `negocio.md` §5 lo
      describe como evento crítico (botón de alerta médica, aviso al dueño,
      canal prioritario a soporte). No hay ni rastro en el código.
- [ ] **No existe ningún panel de administración en el frontend.** Todas las
      operaciones que `negocio.md` §2.3 atribuye al administrador (aprobar/
      rechazar cuidadores, resolver disputas, expulsar usuarios) solo se pueden
      invocar hoy con curl/Postman usando el token de `admin@petvacay.test` —
      no hay ni un módulo `modules/admin` ni una ruta protegida por
      `ROLE_ADMIN` en `AppRouter.tsx`. La parte de verificación de cuidadores
      ya tiene backend completo (`GET/POST /api/v1/admin/sitters/...`) y sería
      la más rápida de exponer; resolución de disputas depende del punto
      crítico de arriba, que no tiene ni backend todavía.

### 🟡 Medio

- [ ] **No hay Notification Service** (`estructura.md`, servicio #7).
      Consecuencia concreta: `ReservaSolicitadaEvent` y `DisputaAbiertaEvent`
      se publican pero **nadie los consume** — el cuidador nunca se entera de
      que tiene una solicitud pendiente. Relacionado: `negocio.md` §5 dice que
      `PagoConfirmadoEvent` debe enviar al cliente "los datos exactos de la
      dirección de entrega de la mascota", pero esa dirección **ni siquiera se
      guarda** — `SitterProfile` solo tiene `latitude`/`longitude` (para
      calcular distancia en el buscador) y `homeType`, nunca una dirección
      postal legible. Aunque existiera el Notification Service, hoy no habría
      qué enviarle.
- [ ] **La cancelación del cuidador no penaliza el posicionamiento en el
      buscador, ni genera la reseña negativa automática.** `negocio.md` §4.3:
      "el cuidador recibe una penalización automática en su posicionamiento
      del buscador... **además de una reseña negativa del sistema**". El
      reembolso al cliente sí funciona. Ya existe la pieza que faltaba (el
      sistema de reseñas), así que esto es ahora un cambio contenido:
      `CancellationPolicy` tendría que disparar algo que baje el
      `rating`/posición del cuidador en `search-service` **y** que
      `ReviewApplicationService` cree una reseña de 1 estrella con un autor de
      sistema al cancelar el cuidador una reserva confirmada.
- [ ] **No hay forma de expulsar a un usuario o cuidador.** `negocio.md` §2.3:
      "Puede expulsar de manera fulminante a usuarios o cuidadores que
      infrinjan los términos de servicio". El dominio ya tiene el método
      `UserAccount.deactivate()` (`identity-service`) pero **no lo llama nadie**
      — ni caso de uso, ni controlador. El login ya rechaza a un usuario
      inactivo (`IdentityApplicationService`, dos comprobaciones de
      `isActive()`), así que solo falta el endpoint
      `POST /api/v1/admin/users/{id}/deactivate` que invoque `deactivate()`.

### 🟢 Menor

- [ ] `booking-service` no valida que los `petIds` recibidos pertenezcan de
      verdad al cliente que hace la solicitud.

---

## Buscador / tarjeta de cuidador (pedido explícito del usuario)

- [ ] **Mostrar el número de plazas (`maxSimultaneousPets`) en la tarjeta del
      buscador.** El dato ya vive en el modelo de lectura de `search-service`
      (`SitterView`/`SitterViewDocument`, se usa hoy solo internamente para
      filtrar por capacidad — `hasCapacity()`), pero nunca sale de ahí: el
      record `SitterSearchResult` (`ports/in/SearchSittersUseCase.java`) no lo
      incluye, así que tampoco llega al DTO de `SitterCard.tsx`. Cambio
      contenido: añadir el campo al record + `SearchApplicationService.toResult()`
      + `SitterSearchResult` en `core/api/types.ts` + un tag/línea más en
      `SitterCard.tsx` (p. ej. "Hasta 2 mascotas a la vez").
- [ ] **Ver las reseñas de un cuidador directamente desde su tarjeta del
      buscador.** Escribir una reseña tras usar el servicio **ya funciona**
      (`BookingDetailPage`, se habilita cuando la reserva está `FINALIZADO`) y
      leerlas también existe como capacidad de API
      (`GET /api/v1/reviews/sitters/{id}`, ya consumido por
      `NewBookingPage.tsx`) — lo que falta es el acceso desde la propia
      tarjeta de búsqueda: hoy `SitterCard.tsx` solo muestra la media y el
      recuento (`sitter.rating`/`sitter.reviewCount`), no hay forma de leer
      los comentarios sin pulsar antes "Reservar". Falta un enlace/botón "Ver
      reseñas" en la tarjeta que abra un modal o navegue a una vista de solo
      lectura del perfil público del cuidador, reutilizando
      `listReviewsForSitter` (`reviewsApi.ts`).

---

## Notificaciones en la app (pedido explícito del usuario)

- [ ] **Símbolo de notificaciones (campana) en `AppLayout.tsx` con mensajes por
      rol.** Centro de notificaciones dentro de la propia app — distinto del
      "No hay Notification Service" de arriba (ese es email/push externos;
      esto es una campanita con badge y un desplegable de mensajes, más simple
      de construir primero y reutilizable después como canal adicional del
      Notification Service real). Ejemplos de mensaje por rol que dio el
      usuario:
      - **Cliente:** "El cuidador ha aceptado tu solicitud" (al pasar
        `SOLICITADO` → `PENDIENTE_PAGO`), y equivalentes para rechazo,
        confirmación de pago, inicio/fin del servicio.
      - **Cuidador:** "Tienes una reserva pendiente" (`ReservaSolicitadaEvent`,
        que hoy se publica y nadie consume — ver arriba).
      - **Admin:** "Cuidador pendiente de verificar", "Incidencia por
        resolver" — depende de que exista primero el panel de admin y (para
        incidencias) el endpoint de resolución de disputas, ambos ya listados
        arriba como 🔴/🟠.
      Implementación sugerida: tabla de notificaciones en el servicio que
      corresponda (o una nueva, ligera) alimentada por los mismos eventos de
      dominio que ya se publican (`ReservaSolicitadaEvent`,
      `PagoConfirmadoEvent`, etc.), endpoint de listado + marcar como leída, y
      un icono en `AppLayout.tsx` con recuento de no leídas.

---

## Pagos

- [ ] **Integrar Stripe en modo test** (claves `pk_test_.../sk_test_...`, sin
      dinero real). Hoy `payment-service` corre siempre contra
      `SimulatedPaymentGatewayAdapter` (`petvacay.payments.provider=simulated`,
      valor por defecto): prueba la lógica propia (libro mayor, máquina de
      estados, idempotencia) pero no la integración real con Stripe, que existe
      en código (`StripePaymentGatewayAdapter`) pero nunca se ha ejercitado.
      Pendiente:
      - Cuenta de Stripe (gratis en modo test) + `VITE_STRIPE_PUBLISHABLE_KEY`
        en el frontend y la clave secreta en `payment-service`.
      - `petvacay.payments.provider=stripe` para activar el adaptador real.
      - Probar el flujo con tarjetas de prueba de Stripe (`4242 4242 4242 4242`
        éxito, `4000 0000 0000 0002` rechazada, `4000 0025 0000 3155` exige 3D
        Secure).
      - Webhook: verificar `StripeWebhookController` con la CLI de Stripe
        (`stripe listen --forward-to ...`) en local antes de exigir un dominio
        público con HTTPS en producción.

---

## Despliegue a producción

- [ ] **HTTPS/TLS.** `nginx.conf` solo escucha en `80` — sin esto la cookie
      `Secure` del refresh token nunca la envía el navegador y la sesión no
      sobrevive a un refresco de página. Decidir entre:
      - Certbot + Nginx (portable, hay que mantener la renovación), o
      - balanceador/CDN gestionado (Cloudflare, ALB de AWS, LB de DigitalOcean).
- [ ] **Elegir dónde se despliega** (AWS / DigitalOcean / VPS) — condiciona la
      opción de HTTPS de arriba y si se usa `docker-compose.prod.yml` tal cual
      (un solo host) o un orquestador multi-máquina (Swarm/Kubernetes) para
      tolerancia a fallos real.
- [ ] **Base de datos gestionada.** Ahora mismo Postgres/Mongo son contenedores
      propios sin backups ni HA. Para producción con usuarios reales, evaluar
      RDS / Cloud SQL / Atlas — el cambio de código es mínimo (son variables de
      entorno), lo que falta es decidirlo y migrarlo.
- [ ] **Backups.** Sin base de datos gestionada, montar al menos un
      `pg_dump`/`mongodump` periódico con retención — hoy no hay ninguno.
- [ ] Desactivar `KAFKA_AUTO_CREATE_TOPICS_ENABLE` en producción real y crear
      los topics explícitamente (cómodo en dev, arriesgado en prod: un typo en
      un nombre de topic crea uno nuevo en silencio).

---

## Observabilidad

- [ ] **Configurar un canal real en Alertmanager** (`infra/alertmanager/alertmanager.yml`).
      Ahora mismo las 4 alertas de memoria se ven en la UI pero no avisan a
      nadie — falta decidir Slack, email u otro webhook y añadir las
      credenciales.
- [ ] Revisar si conviene publicar Prometheus/Alertmanager de otra forma que
      no sea solo `127.0.0.1` cuando se despliegue en el servidor real (túnel
      SSH, o Nginx con `auth_basic` delante).

---

## Documentación / limpieza

- [ ] Actualizar `README.md` con el endurecimiento de seguridad y la Fase 6
      (pagos, despliegue) — quedó a medias.

---

## Ya resuelto (para no repetir trabajo)

- ✅ Tildes/ñ revisadas en todo `petvacay-frontend/src` + `index.html`.
- ✅ CORS del Gateway configurable por entorno (antes fijo a `localhost`).
- ✅ `X-Forwarded-For` sobrescrito en Nginx, no concatenado (evita saltarse el
      rate limiting).
- ✅ `docker-compose.prod.yml`: puertos internos ya no se publican al host,
      clave pública JWT propagada a todos los servicios que la necesitan.
- ✅ `.env.example` + `.gitignore` con todas las variables documentadas.
- ✅ Límites de memoria (`mem_limit`) y heap de JVM (`-Xmx`/`-Xms`) explícitos
      en los 5 servicios Java + Mongo + Redis + Kafka, con `restart: unless-stopped`.
- ✅ Stack de monitorización (Prometheus + cAdvisor + Alertmanager) con 4
      reglas de alerta de memoria, verificado funcionando de punta a punta.
- ✅ Geolocalización real en el buscador (navegador + geocodificación de
      dirección/CP contra Nominatim) en vez del centro de Madrid fijo.
- ✅ **Sistema de reseñas** (uno de los tres pilares de negocio): dominio,
      persistencia y API en `booking-service`, proyección del rating agregado
      en `search-service` vía Kafka (`$inc` atómico, mismo patrón que el
      bloqueo de calendario). Verificado con reseñas reales creadas por la API,
      no solo compilado.
- ✅ Foto de perfil del cuidador, propagada hasta las tarjetas del buscador.
- ✅ El cuidador ya puede **editar** su perfil desde el frontend (el backend lo
      soportaba desde la Fase 4; el frontend nunca lo exponía).
- ✅ El panel del cuidador abre por defecto en "Como cuidador" si tiene ese rol,
      en vez de "Como cliente".
- ✅ El pie de página del frontend (`AppLayout.tsx`) ya no dice *"Fase 1 —
      monolito modular..."*: refleja la arquitectura de microservicios actual.
- ✅ Ficha del cuidador visible en el detalle de una reserva (foto, nombre,
      valoración, tipo de vivienda), Id de reserva etiquetado en vez de un UUID
      desnudo, y tarjetas reordenadas para que el flujo sea más claro.
- ✅ **El cuidador ya puede ofrecer varios servicios, cada uno con su propia
      tarifa** (`negocio.md` §2: "define sus propios servicios... configura de
      forma autónoma sus tarifas"). Antes solo existía `pricePerNight`, una
      única tarifa usada sea cual sea el `serviceType` de la reserva. Cambio de
      punta a punta: catálogo en `identity-service`
      (`SitterProfile.servicePrices: Map<ServiceType, Money>`), migración de
      Flyway que traduce la tarifa única existente como alojamiento nocturno,
      `booking-service` rechaza con `409 ERR_SERVICE_NOT_OFFERED` una reserva
      de un servicio que el cuidador no ofrece, y `search-service` proyecta
      "desde X€" (el mínimo del catálogo). Verificado en Docker contra un
      cuidador real ya migrado, no solo compilado.
