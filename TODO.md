# TODO — PetVacay

Pendientes reales detectados durante el desarrollo y la auditoría final, no
tareas inventadas. Cada uno indica por qué importa y, si aplica, dónde tocar.

---

## Negocio (auditoría final — nada de esto lo pidió `fases.md`, pero sí `negocio.md`)

### 🟡 Medio

- [ ] **No hay Notification Service** (`estructura.md`, servicio #7). Ojo: el
      hueco que dejaba (que nadie consumiera `ReservaSolicitadaEvent` ni
      `DisputaAbiertaEvent`) **ya está cubierto dentro de la app** por el centro
      de notificaciones — ver "Ya resuelto". Lo que sigue faltando es el canal
      **externo**: correo, push y SMS. Relacionado: `negocio.md` §5 dice que
      `PagoConfirmadoEvent` debe enviar al cliente "los datos exactos de la
      dirección de entrega de la mascota", pero esa dirección **ni siquiera se
      guarda** — `SitterProfile` solo tiene `latitude`/`longitude` (para
      calcular distancia en el buscador) y `homeType`, nunca una dirección
      postal legible. Aunque existiera el Notification Service, hoy no habría
      qué enviarle.

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

- ✅ **Los fondos ya se liberan solos.** `BookingLifecycleScheduler` tiene dos
      barridos nuevos: `startDueBookings()` (CONFIRMADO → EN_PROGRESO al llegar
      la fecha de inicio) y `releaseDuePayouts()` (EN_PROGRESO → FINALIZADO 24h
      después del fin, lo que emite `ReservaFinalizadaEvent` y liquida al
      cuidador). Un fallo aislado no aborta el resto del lote.
- ✅ **Una disputa ya tiene salida** (`negocio.md` §4.4): `Booking.resolveDispute()`
      + `CancellationPolicy.resolveDispute()`, evento `DisputaResueltaEvent`
      consumido por `payment-service` (asienta el reparto) y `search-service`
      (libera el calendario, que la disputa mantenía bloqueado), y los endpoints
      `GET /api/v1/admin/bookings?status=` y
      `POST /api/v1/admin/bookings/{id}/resolve-dispute`. El reparto se
      parametriza solo con el reembolso al cliente: con reembolso 0 sale
      exactamente la misma liquidación que una reserva finalizada sin incidencias.
- ✅ **`MascotaEnPeligroEvent` implementado**: `POST /api/v1/bookings/{id}/emergency`
      (solo el cuidador), que avisa al dueño y al equipo de soporte sin tocar el
      estado de la reserva ni congelar el escrow — penalizar económicamente al
      cuidador por dar la alarma sería el incentivo contrario.
- ✅ **Panel de administración en el frontend** (`modules/admin`, ruta `/admin`
      protegida por `ROLE_ADMIN`): verificar/rechazar cuidadores, resolver
      incidencias y expulsar cuentas, las tres competencias de `negocio.md` §2.3.
- ✅ **Centro de notificaciones en la app**: campana con recuento en
      `AppLayout.tsx`, alimentada por los mismos eventos de dominio
      (`NotificationFactory`), con buzón propio por usuario y una audiencia
      compartida para el equipo de administración. `GET /api/v1/notifications`,
      marcar una o todas como leídas.
- ✅ **La cancelación del cuidador ya penaliza**: reseña automática de 1 estrella
      con autor `SYSTEM` (`Review.systemCancellationPenalty`) y penalización de
      posicionamiento en el buscador — cada cancelación suma 10 km ficticios a su
      distancia en el orden del listado, con tope de 50.
- ✅ **Expulsión de cuentas**: `POST /api/v1/admin/users/{id}/deactivate`, que
      además revoca los tokens de refresco (sin eso el expulsado seguiría
      renovando su sesión indefinidamente).
- ✅ `booking-service` valida que los `petIds` son del cliente que reserva
      (`ClientPetsPort` → `GET /api/v1/internal/users/{ownerId}/pet-ids`). Ante un
      fallo de red se rechaza la reserva, no se deja pasar.
- ✅ Las plazas del cuidador (`maxSimultaneousPets`) se ven en la tarjeta del
      buscador, y desde ella se pueden leer sus reseñas sin empezar a reservar.

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
