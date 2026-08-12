# PetVacay

Marketplace gestionado que conecta a duenos de mascotas que viajan con cuidadores
locales verificados. Este repositorio contiene la implementacion de la plataforma
descrita en [negocio.md](negocio.md), [estructura.md](estructura.md),
[especificaciones.md](especificaciones.md) y [fases.md](fases.md).

**Estado actual: las 6 fases completadas** — 4 microservicios independientes con
base de datos propia detras de un API Gateway, CQRS con modelo de lectura en
MongoDB, eventos de dominio sobre Apache Kafka, autenticacion JWT RS256 con
tokens de refresco rotatorios, cobro con Stripe Connect y frontend React que solo
conoce el Gateway.

---

## Arranque rapido

### Con Docker (recomendado — es como esta pensado el sistema)

```bash
docker compose up --build
```

Levanta PostgreSQL (con 3 bases de datos logicas: `identity`, `booking`,
`payment`), MongoDB, Kafka (modo KRaft, sin Zookeeper), los 4 microservicios, el
Gateway (`:8080`) y el frontend (`:3000`). El orden de arranque respeta las
dependencias reales via healthchecks: los servicios no arrancan hasta que su base
de datos esta lista, y el Gateway no arranca hasta que los 4 servicios responden.

### Sin Docker, servicio a servicio (para iterar sobre uno solo)

Requisitos: JDK 21, Maven 3.9+, Node 20+.

```bash
# Infraestructura minima: Postgres + Mongo + Kafka
docker compose up -d postgres-db mongodb kafka

# Cada servicio en su propia terminal (perfil dev: H2 en memoria donde aplique)
cd petvacay-backend && mvn -pl identity-service -am spring-boot:run
cd petvacay-backend && mvn -pl booking-service -am spring-boot:run
cd petvacay-backend && mvn -pl search-service -am spring-boot:run
cd petvacay-backend && mvn -pl payment-service -am spring-boot:run
cd petvacay-backend && mvn -pl gateway-service -am spring-boot:run

# Frontend
cd petvacay-frontend && npm install && npm run dev
```

Solo `identity-service` arranca con datos de ejemplo (`petvacay.seed.enabled=true`
en su perfil `dev`): 3 cuidadores verificados, 1 pendiente de KYC, 1 cliente con 2
mascotas. Los identificadores generados se imprimen en su log de arranque. Para
que esos cuidadores lleguen al buscador hace falta que `search-service` este
tambien arrancado y conectado a Kafka (los eventos de identity-service no se
pierden: Kafka los retiene, asi que arrancar search-service mas tarde los
consume igualmente).

---

## Estructura

```
petvacay-workspace/
├── petvacay-backend/
│   ├── pom.xml               Aggregator Maven (packaging=pom)
│   ├── common/                Nucleo tecnico + contrato de eventos compartido
│   ├── identity-service/      :8082 · PostgreSQL (identity)
│   ├── booking-service/       :8083 · PostgreSQL (booking)
│   ├── search-service/        :8084 · MongoDB (sin base relacional propia)
│   ├── payment-service/       :8085 · PostgreSQL (payment)
│   └── gateway-service/       :8080 · Spring Cloud Gateway (sin base de datos)
├── petvacay-frontend/          React 19 · TypeScript · Vite · Tailwind v4 · Zustand
├── docker-compose.yml          Postgres · Mongo · Kafka · 4 servicios · Gateway · frontend
└── infra/postgres-init-databases.sql
```

### Un microservicio, un hexagono — sin cambios de diseno interno

Cada servicio con dominio propio (`identity`, `booking`, `search`, `payment`)
repite exactamente la misma disposicion que tenian como modulos del monolito en
las Fases 1-3:

| Capa | Contenido | Dependencias |
|---|---|---|
| `domain/` | Agregados, objetos de valor, politicas, eventos | Java puro |
| `ports/in/` | Casos de uso que expone el servicio | El dominio |
| `ports/out/` | SPI que el servicio necesita (repositorios, publicadores, clientes) | El dominio |
| `application/` | Orquestacion transaccional de los casos de uso y handlers de eventos | Puertos |
| `adapters/in/` | REST, listeners de Kafka, scheduler | Puertos de entrada |
| `adapters/out/` | JPA, MongoDB, Kafka, cliente HTTP hacia otro servicio | Puertos de salida |

**Lo unico que cambio en la Fase 4 es el limite de despliegue, no el diseno.**
El dominio nunca importo Spring, JPA ni Kafka; esa disciplina, mantenida desde la
Fase 1, es la razon por la que separar el monolito en 4 procesos independientes
fue mecanico: mover paquetes, dividir un par de adaptadores que resolvian una
dependencia cruzada en memoria, y nada de reescritura.

### `common`: el unico codigo compartido, y solo dos cosas

- **Infraestructura tecnica** que evitaria repetir codigo sin aportar nada:
  `Money`, `DateRange`, `BusinessRuleViolationException`,
  `ResourceNotFoundException`, `GlobalExceptionHandler`.
- **El contrato de eventos** que viaja por Kafka: `BookingEvents`,
  `IdentityEvents` y los tipos de datos que esos eventos transportan
  (`HomeType`, `Species`, `CancellationActor`, `CancellationOutcome`).

`common` **no** contiene agregados ni politicas de negocio: eso es privado de
cada servicio. En una organizacion mas grande, la parte de infraestructura
tecnica y la de contratos de integracion se separarian en dos librerias con
ciclos de vida independientes (los contratos cambian con mas cuidado que las
utilidades); aqui se mantienen juntas por proporcionalidad al tamano del
proyecto — documentado explicitamente en el javadoc del modulo.

`gateway-service` no depende de `common`: es pura configuracion de enrutado, no
conoce ningun tipo de dominio.

---

## El hallazgo central de la Fase 4: los eventos tuvieron que "engordar"

Antes de la Fase 4, cuando un cuidador superaba el KYC, el proyector del
buscador reaccionaba a un evento con solo el `sitterId` y **volvia a consultar
los repositorios de identidad en el mismo proceso** para reconstruir la vista de
lectura. Eso funciona en un monolito; deja de funcionar en cuanto
`search-service` es un proceso aparte, sin acceso a la base de datos de
`identity-service`.

La solucion, documentada en el javadoc de `IdentityEvents`: `identity-service`
traduce sus eventos de dominio "finos" (`CuidadorVerificadoEvent`,
`PerfilCuidadorActualizadoEvent`) a un evento de integracion "gordo"
(`CuidadorIndexadoEvent`) que lleva el snapshot completo — nombre, tarifa,
ubicacion, capacidad, especies aceptadas — antes de publicarlo. `search-service`
ya no vuelve a preguntarle nada a nadie: todo lo que necesita viaja en el propio
mensaje de Kafka.

De paso, esto saco a la luz un efecto colateral real: `PerfilCuidadorActualizadoEvent`
llevaba existiendo desde la Fase 1 sin ningun caso de uso que lo disparara (no
habia endpoint para editar un perfil ya creado). En vez de dejarlo como codigo
muerto ahora que la traduccion a evento "gordo" ya estaba implementada para
`approve()`, se completo el caso de uso (`PUT /api/v1/sitters/profile`).

---

## Contrato interno `identity-service` → `booking-service`

`booking-service` necesita saber si un cuidador esta verificado y cual es su
tarifa antes de aceptar una reserva. Hasta la Fase 3 eso era una lectura en
memoria (`SitterCatalogAdapter`); desde la Fase 4 es una llamada HTTP real
(`SitterCatalogHttpAdapter`, con `RestClient`) contra
`GET /api/v1/internal/sitters/{id}/summary` de `identity-service`.

Dos decisiones de diseno en este limite:

1. **La ruta `/internal/**` nunca se enruta a traves del Gateway** (ver
   `gateway-service/application.yml`): es un contrato exclusivo entre servicios,
   no una API publica. `booking-service` la llama directamente por la URL interna
   de `identity-service` (`http://identity-service:8082` dentro de Docker).
2. **El DTO de respuesta se duplica**, no se comparte desde `common`. Compartir
   el modelo compilado de un endpoint interno crearia acoplamiento de despliegue
   entre ambos servicios (cualquier cambio de forma en uno obligaria a
   recompilar el otro). Duplicar un registro de 6 campos en el limite de
   integracion es mas barato que esa alternativa — la practica estandar en
   microservicios.

Ni el dominio de `booking-service` ni `BookingApplicationService` se tocaron
para este cambio: seguian dependiendo de `SitterCatalogPort`, ajenos a que la
implementacion paso de una lectura en memoria a una llamada HTTP.

---

## Bases de datos: una por servicio, mismo servidor

`identity-service`, `booking-service` y `payment-service` tienen cada uno su
propia base de datos logica (`identity`, `booking`, `payment`) dentro del mismo
contenedor de PostgreSQL — un compromiso deliberado para el entorno local
(ver `infra/postgres-init-databases.sql`). Lo que importa para la autonomia de
cada servicio no es cuantos procesos de Postgres hay, sino que **ningun servicio
lee ni escribe las tablas de otro**: eso se cumple igual con 3 bases logicas en
un servidor que con 3 servidores separados, y en un despliegue real cada una
podria migrar a su propia instancia gestionada sin tocar una linea de codigo.

`search-service` no tiene base de datos relacional: su unico almacen es
MongoDB, coherente con que su unica razon de ser es servir el modelo de lectura
del buscador.

---

## CQRS

El lado de escritura vive en las bases de datos propias de `identity-service` y
`booking-service`. El lado de lectura es la vista desnormalizada de
`search-service` en MongoDB, con la ocupacion noche a noche embebida, de modo
que el filtro de disponibilidad no llama nunca a ningun otro servicio.

`SitterReadModelPort` tiene una unica implementacion desde la Fase 4
(`MongoSitterReadModelAdapter`). Hasta la Fase 3 existia tambien una
implementacion sobre PostgreSQL/H2, util cuando este modulo formaba parte de un
monolito que debia poder arrancar sin infraestructura externa; se retiro al
independizar `search-service` porque dejo de aportar nada (nadie mas comparte su
JVM) y no tenia tests que la ejercitaran. El puerto se mantiene como interfaz
aunque solo tenga un implementador: una eventual segunda implementacion (por
ejemplo Elasticsearch para busqueda por texto libre) seguiria encajando sin
tocar `SearchApplicationService` ni `SitterProjectionHandler`.

El indice geoespacial de Mongo aporta dos cosas sobre el antiguo adaptador de
PostgreSQL: el acotado por radio lo resuelve un **indice 2dsphere** con
`$nearSphere` en vez de calcular haversine en memoria, y los bloqueos de
calendario se aplican con **`$inc` atomico** por noche en lugar de
leer-modificar-escribir — imprescindible con los eventos llegando por Kafka y
procesandose en paralelo.

La sincronizacion es por eventos de dominio:

| Evento | Origen | Efecto en el modelo de lectura |
|---|---|---|
| `CuidadorIndexadoEvent` | identity-service | Alta o reproyeccion completa del cuidador |
| `CuidadorRechazadoEvent` | identity-service | Baja del indice |
| `PagoConfirmadoEvent` | booking-service | Bloqueo de las noches reservadas |
| `ReservaCanceladaEvent` / `ReservaFinalizadaEvent` | booking-service | Liberacion de las noches |

### Geolocalizacion y geocodificacion

Hasta esta fase, el punto desde el que se media la distancia en el buscador
estaba fijo en el centro de Madrid — el filtro por radio funcionaba, pero
medido siempre desde el mismo sitio sin importar quien buscara. Se resolvio en
dos capas, ninguna excluyente de la otra:

1. **Geolocalizacion del navegador** (`navigator.geolocation`), pedida en
   silencio al cargar `SearchPage`: si el usuario concede el permiso, el radio
   se mide desde su ubicacion real. La busqueda inicial **no espera** a esta
   respuesta — se lanza primero con Madrid por defecto para no dejar la
   pantalla vacia mientras el navegador pide permiso, y se repite en cuanto la
   geolocalizacion resuelve (o se queda como estaba si el usuario la deniega).
2. **Un campo de direccion o codigo postal**, para el caso que la
   geolocalizacion no cubre — alguien buscando cuidador en una ciudad donde no
   esta ("me voy a Barcelona, quiero ver quien hay alli"). Geocodifica contra
   `GET /api/v1/search/geocode` (nuevo endpoint de `search-service`), que a su
   vez llama a **Nominatim** (OpenStreetMap): gratuito y sin alta, coherente
   con el mismo criterio que el adaptador `simulated` de Stripe — nadie deberia
   necesitar una cuenta de pago para arrancar el proyecto.

**Limite real documentado en el codigo, no solo aqui:** la politica de uso de
Nominatim exige un maximo de **1 peticion/segundo en total**, no por cliente.
El Gateway le pone a esta ruta un `RequestRateLimiter` mucho mas estricto que
al resto del buscador (1/s, rafaga 2 — ver `search-geocode` en
`gateway-service/application.yml`), pero **por IP**, no de forma global: con
muchos usuarios concurrentes el conjunto podria seguir superando el limite de
Nominatim. Para produccion con trafico real, sustituir por un proveedor de
pago (Google/Mapbox) o una instancia propia de Nominatim autoalojada — el
cambio queda contenido en `NominatimGeocodingAdapter`, el resto del sistema
solo conoce el puerto `GeocodingPort`.

---

## Eventos de dominio sobre Apache Kafka

Desde la Fase 4, Kafka es el **unico** transporte real entre servicios: el bus
interno de Spring (`*SpringEventPublisher`, `*EventListener`) se conserva en cada
servicio solo para poder levantarlo en solitario sin infraestructura mientras se
itera sobre su REST API o su dominio — los eventos se publican igual, pero al no
compartir proceso con ningun consumidor, sencillamente no llegan a ningun sitio.
Es el comportamiento esperado al ejecutar un microservicio aislado de sus
vecinos, y se documenta asi en el javadoc de cada publicador.

**Contrato de mensaje:** cada evento se serializa a **JSON plano** (sin
cabeceras de tipo especificas de Java) al topic `event.eventName()` — p. ej.
`petvacay.identity.cuidador-indexado` — con `event.aggregateId()` como key, de
modo que todos los eventos de un mismo agregado caen en la misma particion y se
procesan en orden. JSON plano en vez del formato propietario de Spring Kafka
mantiene el contrato consumible desde cualquier lenguaje, relevante ahora que
cada servicio es un proceso independiente que en principio podria no estar
escrito en Java.

**Dos consumidores independientes del mismo evento:** `PagoConfirmadoEvent` lo
consumen tanto `petvacay-search-projector` (en `search-service`, bloquea el
calendario) como `petvacay-payment-settlement` (en `payment-service`, abre el
apunte de escrow), cada uno con su propio group id. Verificado en Docker real:
ambos servicios, en procesos y contenedores distintos, reciben el mismo mensaje
de forma independiente.

**Limitacion conocida, documentada en vez de ignorada:** los consumidores no son
idempotentes. Si el group id de un consumidor se resetea, reprocesaria eventos
ya aplicados, y como el bloqueo de calendario en MongoDB usa `$inc`, un
reproceso duplicaria la ocupacion. En un arranque normal esto no ocurre (los
offsets quedan comprometidos por group id), pero una solucion robusta frente a
reprocesos exigiria claves de idempotencia. Se deja fuera del alcance a
proposito, igual que la idempotencia de Stripe se deja para la Fase 6.

---

## API Gateway

`gateway-service` (Spring Cloud Gateway, reactivo sobre Netty) es el unico
proceso que el navegador contacta directamente. Enruta por prefijo de ruta:

| Prefijo | Servicio destino |
|---|---|
| `/api/v1/auth/**`, `/api/v1/users/**`, `/api/v1/pets/**`, `/api/v1/sitters/**`, `/api/v1/admin/**` | identity-service |
| `/api/v1/bookings/**` | booking-service |
| `/api/v1/search/**` | search-service |
| `/api/v1/payments/**` | payment-service |

No reescribe el cuerpo de las respuestas: los `ProblemDetail` RFC 7807 que
generan los servicios llegan intactos al navegador. El CORS, que hasta la Fase 3
declaraba cada servicio por separado, ahora vive **solo aqui** (`CorsConfig`):
es el unico punto que recibe trafico real de un origen distinto.

`/api/v1/internal/**` (el contrato de `identity-service` hacia `booking-service`)
deliberadamente **no** tiene ruta en el Gateway: nunca debe ser alcanzable desde
el navegador.

---

## Seguridad (Fase 5)

### JWT con firma asimetrica RS256

`identity-service` es el **unico** proceso con la clave privada; los cuatro
servicios y el Gateway solo tienen la publica. Con un secreto compartido (HS256)
cualquier servicio capaz de validar podria tambien emitir tokens: la firma
asimetrica es precisamente lo que hace posible esa separacion.

La clave publica va empaquetada en cada artefacto, de modo que **la validacion es
completamente offline**: ningun servicio necesita que identity-service este vivo
para rechazar un token invalido. Se descarto JWKS a proposito —
`search-service` y `payment-service` no tienen hoy ninguna dependencia sobre
identity-service y JWKS introduciria una. El precio es que rotar la clave exige
redesplegar; el razonamiento completo esta en
[`identity-service/src/main/resources/keys/README.md`](petvacay-backend/identity-service/src/main/resources/keys/README.md).

Claims del token: `sub` (userId), `roles`, `first_name` y — solo para
cuidadores — `is_verified`, que especificaciones.md 2.2 exige para decidir el
acceso a las vistas de cuidador sin una consulta extra.

### Validacion en dos capas, a proposito

El Gateway valida el token en el perimetro (estructura.md se lo asigna) **y cada
servicio lo valida otra vez por su cuenta**. La duplicacion es deliberada: si un
atacante alcanzara la red interna, o si una ruta futura se anadiera al Gateway
sin protegerla, cada servicio seguiria exigiendo un token valido. Delegar la
autenticacion solo en el perimetro convierte cualquier fallo del perimetro en un
compromiso total.

Lo que se comparte en `common` es **como se lee un token** (firma, caducidad,
mapeo de claims a authorities); lo que cada servicio declara por su cuenta es
**quien puede hacer que**, en su propio `SecurityFilterChain`.

| Servicio | Reglas |
|---|---|
| identity | `register`/`login` publicos · `/admin/**` requiere `ROLE_ADMIN` · `/sitters/profile` y `/sitters/kyc/**` requieren `ROLE_SITTER` · `/internal/**` solo autenticado |
| booking | Todo autenticado, mas comprobacion de propiedad por reserva (ver abajo) |
| search | `GET /search/**` **publico a proposito**: un visitante debe poder ver que cuidadores hay antes de registrarse |
| payment | Todo autenticado: son movimientos de dinero |

### Contrasenas: BCrypt

Sustituye al SHA-256 provisional de las Fases 1-4. SHA-256 es un hash rapido de
proposito general — justo lo que no se quiere para contrasenas, porque permite
probar miles de millones de candidatas por segundo contra una base de datos
robada. BCrypt incorpora sal automatica y un factor de coste que hace el calculo
deliberadamente lento. El puerto `PasswordHasherPort` no cambio: el dominio sigue
sin saber que algoritmo hay detras, que era el objetivo de definirlo asi en la
Fase 1.

El login devuelve el mismo error tanto si el email no existe como si la
contrasena es incorrecta, y calcula un hash de descarte cuando el usuario no
existe, para no filtrar por diferencia de tiempo que direcciones estan
registradas.

### Dos vulnerabilidades encontradas y cerradas en esta fase

Al retirar `X-User-Id` salieron a la luz dos fallos que las fases anteriores
habian dejado latentes. Ninguno era teorico:

**1. Falsificacion del actor de cancelacion (exploit financiero).** El endpoint
de cancelacion aceptaba `?actor=CLIENT|SITTER`, y de ese parametro dependia la
politica de reembolso. Un cliente podia cancelar declarandose cuidador y activar
asi el reembolso del 100% *incluidas las tasas* (183,75 €) en lugar del que le
correspondia (175,00 €), penalizando ademas injustamente al cuidador en el
buscador. Ahora el rol **se deduce en el servidor** de quien esta autenticado; el
parametro se ignora.

**2. Ausencia de comprobacion de propiedad.** Cualquier usuario autenticado podia
aceptar, cancelar o marcar como pagada la reserva de otro con solo conocer su
identificador. Los UUID no son secretos: acaban en logs, en la barra de
direcciones y en capturas de pantalla. Ahora `BookingApplicationService`
comprueba que quien invoca es el cliente o el cuidador de esa reserva concreta, y
que lo hace con el rol correcto (solo el cuidador acepta; solo el cliente paga).

Ambos estan fijados por tests (`clientCannotForgeCancellationActor`,
`strangerCannotActOnSomeoneElsesBooking`, `clientCannotAcceptOwnBooking`) y
verificados contra el sistema real en Docker.

### Autorizacion entre servicios

`booking-service` propaga el JWT del usuario cuando consulta a `identity-service`,
en lugar de usar una credencial propia de maquina. Mantiene la trazabilidad de
extremo a extremo y evita introducir un segundo mecanismo de credenciales. El
limite conocido: solo funciona dentro del contexto de una peticion de usuario; si
un proceso en segundo plano necesitara consultar el catalogo, habria que anadir
un flujo client-credentials.

### Bootstrap del administrador

`register()` concede siempre `ROLE_CLIENT` (y opcionalmente `ROLE_SITTER`) y
**nunca** `ROLE_ADMIN`: si el endpoint publico pudiera crear administradores,
cualquiera podria darse de alta como tal y aprobar sus propios KYC. La cuenta
inicial la crea `AdminAccountBootstrap` escribiendo por el puerto de repositorio,
saltandose ese caso de uso a proposito porque es aprovisionamiento de
infraestructura. Credenciales por defecto en desarrollo:
`admin@petvacay.test` / `admin12345`, con un aviso en cada arranque mientras no
se cambien (`PETVACAY_ADMIN_EMAIL` / `PETVACAY_ADMIN_PASSWORD`).

### Endurecimiento posterior

Las tres limitaciones que la Fase 5 documento como pendientes se han cerrado:

**1. El token ya no vive en `localStorage`.** El de **acceso** vive solo en
memoria y dura 15 minutos; desaparece al cerrar la pestana, asi que un XSS no
tiene de donde leerlo. El de **refresco** viaja en una cookie
`HttpOnly; Secure; SameSite=Strict` con el path acotado a `/api/v1/auth`: el
JavaScript de la pagina no puede tocarla, y `SameSite=Strict` elimina el vector
CSRF sin necesidad de un token anti-CSRF adicional. Al recargar la pagina,
`SessionGate` canjea la cookie por un token nuevo antes de renderizar nada.

**2. Hay revocacion real, con rotacion y deteccion de reuso.** Los tokens de
refresco se guardan en servidor (solo su hash SHA-256), de modo que cerrar
sesion los invalida de verdad. Cada uso rota el token y consume el anterior; si
un token ya consumido vuelve a presentarse significa que dos partes tienen la
misma credencial — esta robada — y se revoca **la familia entera**, incluida la
sesion legitima. Es la deteccion de reuso de la RFC 6819.

Un test encontro aqui un fallo real que no era evidente: la revocacion se
disparaba dentro de la misma transaccion que despues lanzaba la excepcion de
rechazo, asi que el rollback deshacia la propia revocacion. El atacante recibia
un error, pero la sesion robada seguia viva. Se corrigio ejecutando la
revocacion en una transaccion independiente (`REQUIRES_NEW`).

**3. `payment-service` filtra el libro mayor por propietario.** Mantiene una
proyeccion `booking_parties` alimentada por `PagoConfirmadoEvent`, que ya
transportaba `clientId` y `sitterId`. Mismo patron que resolvio
`CuidadorIndexadoEvent` en la Fase 4: el dato viaja en el evento en vez de
consultarse por HTTP, de modo que payment-service puede autorizar aunque
booking-service este caido. El equipo de administracion tiene una ruta aparte
(`/payments/admin/...`, con `ROLE_ADMIN`) para arbitrar disputas.

### Rate limiting

estructura.md lo asigna al Gateway y ahi esta, con Redis como contador — en
memoria no valdria, porque con varias replicas del Gateway el limite se
multiplicaria por replica. Se limita por **IP** y no por usuario porque los
endpoints que de verdad hay que proteger, login y registro, son anonimos por
definicion: en el momento del ataque todavia no hay usuario al que atribuir la
peticion.

| Rutas | Limite |
|---|---|
| `/api/v1/auth/**` | 5/s, rafaga 10 — sin esto, el login es un oraculo de fuerza bruta |
| `/api/v1/search/**` | 50/s, rafaga 100 — publico y se consulta al teclear filtros |
| Resto | 30/s, rafaga 60 |

### Limitaciones que siguen abiertas

- **La clave publica de verificacion esta duplicada** entre `common` y
  `gateway-service`, porque el Gateway no puede depender de `common` (arrastraria
  el stack servlet y desactivaria WebFlux). Es una clave publica, asi que no hay
  coste de seguridad; el coste es acordarse de actualizar las dos al rotarla. En
  produccion ambas se inyectan desde la misma fuente externa y la duplicacion
  desaparece.
- **HTTPS no esta configurado.** `nginx.conf` solo escucha en el puerto 80. Sin
  TLS, la cookie `Secure` del refresh token nunca la envia el navegador y la
  sesion no sobrevive a un refresco de pagina — hay que decidir entre Certbot o
  un balanceador/CDN gestionado antes de un despliegue real (detalle en
  [TODO.md](TODO.md)).

### Cerrado en la auditoria final

- **`X-Forwarded-For` ya no es falsificable en el borde.** El rate limiting lo
  lee para no agrupar a todos los usuarios detras de un proxy bajo un mismo
  limite; `nginx.conf` ahora la sobrescribe con `$remote_addr` (la IP de la
  conexion TCP real) en vez de concatenarla con `$proxy_add_x_forwarded_for`,
  que dejaba pasar lo que el cliente quisiera anteponer.
- **CORS del Gateway dejo de estar fijo a `localhost`.** Ahora es configurable
  por `PETVACAY_CORS_ALLOWED_ORIGINS`, documentado en `.env.example`.

---

## Cobro con Stripe Connect (Fase 6)

### Los datos de tarjeta nunca tocan PetVacay

El flujo es el de especificaciones.md seccion 3: payment-service crea un
`PaymentIntent` y devuelve su `clientSecret`; React monta Stripe Elements con ese
secreto; el navegador envia la tarjeta **directamente a Stripe**; y el resultado
llega por webhook. En ningun momento el numero de tarjeta pasa por un servidor de
PetVacay — el campo es un iframe de Stripe que ni siquiera el propio JavaScript
de la pagina puede leer. Eso es lo que mantiene el sistema fuera del alcance mas
exigente de PCI-DSS.

La clave **publicable** de Stripe vive en el frontend (identifica la cuenta, no
autoriza nada); la **secreta**, solo en payment-service.

### Idempotencia en dos capas

- **Hacia Stripe**: toda operacion que mueve dinero lleva clave de idempotencia,
  derivada del identificador de la reserva. Si la red corta la respuesta de un
  cobro, el reintento devuelve el resultado original en vez de cobrar dos veces.
- **Desde Kafka**: cierra la limitacion que la Fase 3 documento y dejo abierta.
  Kafka garantiza entrega *al menos una vez*; un rebalanceo o un reinicio antes de
  comprometer el offset bastan para recibir el mismo evento dos veces, lo que
  duplicaria apuntes del libro mayor. La tabla `processed_events` descarta las
  reentregas por el `eventId` que todos los eventos ya llevaban desde la Fase 1.

Sobre por que ese descarte es "consulta y luego escribe" y no "inserta y captura
el conflicto": con un identificador asignado (no generado), `save()` de Spring
Data ejecuta un `merge` que sobrescribe en silencio, asi que la restriccion de
unicidad nunca llegaria a dispararse. La comprobacion explicita es segura aqui
porque los eventos de una misma reserva comparten particion de Kafka y los
consume un unico hilo en orden; la clave primaria queda como ultima defensa.

### El webhook es el punto mas expuesto del sistema

`POST /api/v1/payments/webhooks/stripe` es **publico por necesidad**: lo llama
Stripe, no un usuario con sesion. Cualquiera en internet puede enviarle un POST
diciendo "esta reserva esta pagada". Lo unico que lo hace seguro es la
verificacion de la firma `Stripe-Signature`.

Dos consecuencias de diseno:

- El cuerpo se recibe como `String` en crudo, no como objeto deserializado: la
  firma se calcula sobre los bytes exactos que envio Stripe, y dejar que Spring
  parsee y vuelva a serializar el JSON los cambiaria.
- Si no hay secreto configurado, el endpoint responde `503` y **descarta** el
  evento. Aceptar eventos sin poder verificarlos seria peor que no tener webhook.

### Sin claves de Stripe: adaptador simulado

`petvacay.payments.provider` elige entre `simulated` (por defecto) y `stripe`.
El simulado sustituye las llamadas de red por identificadores en memoria, de modo
que levantar el entorno local no exige una cuenta de Stripe; reproduce la
idempotencia, que es el comportamiento del que depende la correccion del sistema.

**Lo que el simulador no demuestra**: que la integracion real funcione. Un
simulador solo prueba el codigo propio. `StripePaymentGatewayAdapter` esta
implementado contra el SDK oficial y compila, pero **no se ha ejercitado contra
Stripe**: verificarlo exige claves de prueba reales. Es la unica parte del
sistema que se entrega implementada pero no verificada de extremo a extremo.

### Probar con Stripe en modo test (local)

Para ver el formulario de tarjeta real (Stripe Elements) en vez del boton de
pago simulado, hace falta una cuenta de Stripe (gratis) en **modo test** y
completar `PETVACAY_PAYMENTS_PROVIDER`, `STRIPE_SECRET_KEY`,
`STRIPE_WEBHOOK_SECRET` y `VITE_STRIPE_PUBLISHABLE_KEY` en el `.env`
(placeholders documentados en [`.env.example`](.env.example)).

El webhook necesita ademas el [Stripe CLI](https://stripe.com/docs/stripe-cli)
para reenviar eventos a `localhost`, ya que Stripe no puede llamar directamente
a una maquina de desarrollo:

```bash
# Una sola vez: vincula el CLI a tu cuenta de Stripe
stripe login

# Cada sesion de desarrollo, en una terminal aparte (mientras trabajas con el
# resto del stack ya levantado, por Docker o servicio a servicio):
stripe listen --forward-to localhost:8080/api/v1/payments/webhooks/stripe
```

`stripe listen` imprime un `whsec_...` al arrancar — es el valor de
`STRIPE_WEBHOOK_SECRET`. No es un servicio en segundo plano: hay que
relanzarlo cada vez que se retoma el desarrollo, y dejarlo corriendo mientras
se prueban pagos para que los eventos (`payment_intent.succeeded`, etc.)
lleguen a `payment-service`. El login del CLI si es persistente entre
sesiones.

Con eso configurado, una tarjeta de prueba tipica es `4242 4242 4242 4242`
(fecha futura y CVC cualquiera).

### Despliegue de produccion

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

Diferencias respecto a desarrollo, todas deliberadas: el frontend se sirve como
estaticos por Nginx en lugar del servidor de Vite; **ningun servicio publica su
puerto al host**, ni siquiera el Gateway — solo Nginx (`:80`), que hace de proxy
de `/api` hacia el; y todos los secretos vienen del entorno, sin valores por
defecto — el compose falla al arrancar si falta alguno, que es mejor que
arrancar con la contrasena de ejemplo. Variables documentadas, una a una, en
[`.env.example`](.env.example).

Cada servicio lleva ademas `mem_limit` explicito y `restart: unless-stopped`.
Sin `mem_limit`, una JVM (o el cache de WiredTiger de Mongo) se dimensiona segun
la RAM que ve del **host**, no la que Docker le va a dejar usar de verdad — con
varios servicios en la misma maquina eso es la receta para que el OOM killer
del kernel mate al proceso equivocado bajo carga real. Las 5 JVMs (Gateway + 4
servicios) arrancan ademas con heap explicito (`-Xms256m -Xmx384m`) y
`-XX:+ExitOnOutOfMemoryError`: si el heap se agota, el proceso muere limpio (con
volcado en `/tmp/*.hprof`) en vez de degradarse a base de pausas de GC sin
liberar nada — `restart: unless-stopped` lo levanta de nuevo solo.

**Sigue pendiente**: HTTPS (ver "Limitaciones que siguen abiertas" mas arriba),
base de datos gestionada con backups, y decidir si este compose de un solo host
basta o hace falta un orquestador multi-maquina para tolerancia real a fallos.
Detalle completo en [TODO.md](TODO.md).

---

## Observabilidad

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  -f docker-compose.monitoring.yml up -d
```

Fichero separado y opcional (`docker-compose.monitoring.yml`) porque no es
parte del sistema en si, es infraestructura para vigilarlo. Tres piezas:

| Servicio | Que hace |
|---|---|
| **cAdvisor** | Expone memoria/CPU real de cada contenedor (via cgroups) en formato Prometheus |
| **Prometheus** | Scrapea cAdvisor + `/actuator/prometheus` de los 5 servicios Java cada 15s; evalua las reglas de `infra/prometheus/alerts.yml` |
| **Alertmanager** | Recibe las alertas que Prometheus dispara y decide a quien avisar |

Cuatro reglas de alerta, todas sobre memoria (el problema concreto que motivo
montar esto): dos por `mem_limit` del contenedor (aviso al 85%, critico al
95%), una por heap de JVM al 85% de `-Xmx`, y una que confirma a posteriori si
el kernel mato un contenedor por falta de memoria.

Ningun puerto se publica salvo a `127.0.0.1` (Prometheus `:9090`, Alertmanager
`:9094` — el `9093` lo ocupa ya el listener externo de Kafka): la UI de
Prometheus no tiene autenticacion propia, asi que solo es accesible desde la
propia maquina. En un servidor remoto, sustituir por un tunel SSH o enrutarlo a
traves de Nginx con `auth_basic`.

**Pendiente**: `infra/alertmanager/alertmanager.yml` no tiene ningun receptor
real configurado todavia (ni Slack ni email) — las alertas se ven en la UI pero
no avisan a nadie proactivamente. Ver [TODO.md](TODO.md).

---

## Reglas de negocio implementadas

### Modelo de comision doble

| Concepto | Regla |
|---|---|
| Precio base | tarifa/noche x noches x numero de mascotas |
| Tasa de gestion al cliente | +5% sobre el base |
| Retencion al cuidador | -15% sobre el base |
| Ingreso de la plataforma | 5% + 15% |

Ejemplo del contrato de API (25 €/noche, 7 noches, 1 mascota): base 175,00 €,
tasa 8,75 €, total al cliente **183,75 €**, liquidacion al cuidador 148,75 €,
ingreso de plataforma 35,00 €. Verificado en `PricingPolicyTest`.

### Maquina de estados de la reserva

```
BORRADOR → SOLICITADO → PENDIENTE_PAGO → CONFIRMADO → EN_PROGRESO → FINALIZADO
                │              │              │             │
                └──────────────┴──────────────┴─────────────┴──→ CANCELADO
                                               │             │
                                               └─────────────┴──→ EN_DISPUTA
```

- Una solicitud sin respuesta **caduca a las 24 h** y la cancela el scheduler de
  `booking-service`.
- El calendario del cuidador se bloquea **al confirmarse el pago**, no antes.
- Los fondos se liberan **24 h despues del fin del servicio**; una disputa abierta
  dentro de esa ventana detiene el pago y congela la reserva.

### Politicas de cancelacion

| Escenario | Reembolso al cliente | Al cuidador | Plataforma |
|---|---|---|---|
| Cliente, > 48 h | 100% del base | — | tasa 5% |
| Cliente, < 48 h | base − primera noche | primera noche | tasa 5% |
| Cuidador | 100% incluidas tasas | — | — |
| Aun sin pagar | sin movimiento | — | — |

Ninguna politica crea ni destruye dinero: la suma de reembolso, compensacion e
ingreso de plataforma siempre iguala lo cobrado al cliente (verificado en
`CancellationPolicyTest.moneyIsConservedInEveryPolicy`).

Dos decisiones tomadas donde el documento de negocio no era explicito, ambas
justificadas en el javadoc de `CancellationPolicy`:

1. **La tasa del 5% no se reembolsa tampoco en la cancelacion tardia.** Retribuye
   un servicio ya prestado y se devenga al reservar. Si se devolviera, cancelar
   tarde saldria mejor que cancelar pronto.
2. **La compensacion de la primera noche soporta la retencion del 15%.** Una
   compensacion debe dejar al cuidador indemne, no mejorado: asi cobra 21,25 € por
   la noche bloqueada, exactamente lo que habria ganado prestandola. Sin la
   retencion cobraria 25 €, es decir, mas por una noche cancelada que por una
   trabajada.

### KYC del cuidador

Un perfil nace en `PENDIENTE_VERIFICACION` y **no se indexa en el buscador ni
recibe solicitudes** hasta que un administrador lo aprueba. La aprobacion
publica `CuidadorIndexadoEvent`, que es lo que materializa su vista de busqueda
en `search-service`.

### Resenas cruzadas e inmutables

Uno de los tres pilares de negocio (`negocio.md`: "reputacion auditable...
sistema de resenas cruzadas inmutables"). Vive en `booking-service`, no en
`identity-service`: una resena es un hecho sobre una reserva concreta, no
sobre el perfil del cuidador — el agregado correcto es `Review`, ligado a
`Booking`, no a `SitterProfile`.

Reglas, todas en `ReviewApplicationService`:

- Solo se puede valorar una reserva **una vez `FINALIZADO`** el servicio.
- **Una resena por reserva y por rol**: el cliente valora al cuidador, el
  cuidador valora al cliente, cada uno como maximo una vez (restriccion unica
  en base de datos, `uq_review_booking_role`).
- Inmutable: no existe endpoint para editarla ni borrarla.
- Solo la resena del **cliente hacia el cuidador** dispara `ResenaCreadaEvent`
  hacia Kafka: es la unica direccion con vitrina publica (el buscador). La del
  cuidador hacia el cliente se guarda igual, pero no tiene ningun modelo de
  lectura que la consuma todavia.

`search-service` ya tenia `rating`/`reviewCount` en `SitterView` desde la Fase
2 — el campo llevaba ahi esperando desde el principio, simplemente nada lo
alimentaba. `SitterViewDocument` guarda `ratingSum` (no la media) y aplica
`$inc` atomico en cada resena, exactamente el mismo patron que ya resolvia el
bloqueo de calendario: varias resenas pueden llegar por Kafka casi a la vez, y
un `$inc` es atomico frente a eso mientras que un "leer, sumar, escribir" no lo
seria. La media se calcula al leer, no al escribir.

### Catalogo de servicios: una tarifa por servicio, no una unica tarifa

`negocio.md` seccion 2 es explicito: el cuidador "define sus propios servicios
(Alojamiento nocturno, Guarderia de dia, Paseos por horas)... configura de
forma autonoma sus tarifas". Hasta esta fase `SitterProfile` solo tenia
`pricePerNight`, una unica tarifa que se aplicaba sin importar que
`serviceType` pidiera la reserva — un gap real frente al propio documento de
negocio, no una funcionalidad nueva inventada.

`SitterProfile.servicePrices` es ahora un `Map<ServiceType, Money>`: el
cuidador ofrece los servicios que quiere, cada uno con su propia tarifa, y
puede no ofrecer los otros dos. Tres consecuencias en cadena, cada una en el
servicio que le corresponde:

- **`identity-service`**: valida que el catalogo no este vacio y que cada
  tarifa sea positiva (`ERR_SITTER_NO_SERVICES`, `ERR_SITTER_INVALID_PRICE`).
  Persistido con `@ElementCollection` sobre una tabla propia
  (`sitter_service_prices`), mismo patron que ya usaba `acceptedSpecies`.
- **`booking-service`**: `SitterCatalogPort.SitterSummary.servicePrices` ya no
  es un precio unico, es el mapa completo. Si el `serviceType` de la reserva no
  esta en el catalogo del cuidador, la creacion falla con
  `409 ERR_SERVICE_NOT_OFFERED` — antes de esta fase, cualquier `serviceType`
  se aceptaba y tarificaba igual, lo cual era incorrecto por definicion.
- **`search-service`**: el buscador mostraba (y sigue mostrando) un unico
  precio por tarjeta. Ahora es el **minimo** del catalogo del cuidador ("desde
  X€"), calculado en `SitterProjectionHandler` a partir del mapa que trae
  `CuidadorIndexadoEvent`.

Migracion de datos: `V4__sitter_service_prices.sql` traduce la tarifa unica de
cada cuidador ya existente como `NIGHT_ACCOMMODATION` — es el unico servicio
que el sistema soportaba hasta ahora, asi que ningun perfil se queda sin
catalogo tras el despliegue. Verificado contra un cuidador migrado en Docker:
su tarifa unica de antes aparece intacta como su unico servicio ofrecido.

---

## API

Todas las rutas cuelgan de `/api/v1` y se consumen a traves del Gateway
(`http://localhost:8080`) — la tabla de enrutado esta en la seccion anterior.

| Metodo | Ruta | Servicio |
|---|---|---|
| `POST` | `/auth/register` | identity |
| `GET` | `/users/me` | identity |
| `GET` `POST` `DELETE` | `/pets` · `/pets/{id}` | identity |
| `POST` `PUT` | `/sitters/profile` | identity |
| `POST` | `/sitters/kyc/document` | identity |
| `GET` | `/sitters/{id}/profile` | identity |
| `GET` | `/admin/sitters/pending` | identity |
| `POST` | `/admin/sitters/{id}/approve` · `/reject` | identity |
| `GET` | `/internal/sitters/{id}/summary` | identity (solo booking-service, no via Gateway) |
| `GET` | `/search/sitters` | search |
| `GET` | `/search/geocode` | search (proxy a Nominatim; rate limit propio, mas estricto) |
| `POST` | `/bookings` | booking |
| `GET` | `/bookings` · `/bookings/{id}` | booking |
| `POST` | `/bookings/{id}/accept` · `/reject` · `/confirm-payment` · `/start` · `/complete` · `/cancel` · `/disputes` | booking |
| `POST` `GET` | `/bookings/{id}/reviews` | booking |
| `GET` | `/reviews/sitters/{id}` | booking (publico: vitrina de reputacion) |
| `GET` | `/payments/bookings/{id}/ledger` | payment |

### Autenticacion

Ver la seccion **Seguridad (Fase 5)** mas abajo. La cabecera `X-User-Id` que las
Fases 1-4 usaban como sustituto provisional **ya no existe**: la identidad sale
del `subject` de un JWT firmado.

### Errores

Cada servicio responde **siempre** con RFC 7807 (Problem Details) y una
propiedad extra `code` estable — `GlobalExceptionHandler` vive en `common` y lo
comparten los 4. Ningun fallo atribuible al cliente se escapa como `500`:

| Situacion | Estado | `code` |
|---|---|---|
| Campos de formulario invalidos | `400` | `ERR_VALIDATION_FAILED` (+ `invalid_params`) |
| Cuerpo JSON ilegible o con tipos erroneos | `400` | `ERR_MALFORMED_REQUEST` |
| Parametro de ruta o query mal formado | `400` | `ERR_INVALID_PARAMETER` |
| Token ausente, caducado o mal firmado | `401` | `ERR_UNAUTHENTICATED` |
| Credenciales incorrectas en el login | `401` | `ERR_INVALID_CREDENTIALS` |
| Falta el rol necesario | `403` | `ERR_FORBIDDEN` |
| La reserva no pertenece al usuario | `403` | `ERR_NOT_BOOKING_PARTICIPANT` |
| Rol equivocado para esa transicion | `403` | `ERR_WRONG_BOOKING_ROLE` |
| Ruta inexistente | `404` | `ERR_ROUTE_NOT_FOUND` |
| Recurso inexistente | `404` | `ERR_*_NOT_FOUND` |
| Regla de negocio incumplida | `409` / `422` | `ERR_BOOKING_DATE_CONFLICT`, ... |

```json
{
  "type": "https://api.petvacay.com/errors/booking-date-conflict",
  "title": "Cuidador No Disponible",
  "status": 409,
  "detail": "El cuidador seleccionado ya dispone de una reserva confirmada para esas fechas.",
  "code": "ERR_BOOKING_DATE_CONFLICT"
}
```

El Gateway no reescribe el cuerpo de estas respuestas: el frontend los trata
segun su naturaleza (`useProblemToast`) exactamente igual que si hablase con un
monolito — los `400` pintan `invalid_params` campo a campo en el formulario, el
resto se muestran como toast sin romper el flujo de la pantalla.

### UX de consistencia eventual

Con los eventos viajando por Kafka entre procesos, la confirmacion de un comando
(p. ej. pagar una reserva) ya no implica que sus efectos derivados en otro
servicio (bloquear el calendario del cuidador en el buscador) sean visibles al
instante. El frontend lo comunica en dos puntos, en vez de fingir consistencia
inmediata:

- **`BookingDetailPage`**: tras confirmar el pago, un aviso transitorio
  (`ProcessingNotice`) explica que la disponibilidad puede tardar unos segundos
  en reflejarse.
- **`SearchPage`**: los resultados muestran la hora de la ultima actualizacion y
  un boton de "Actualizar disponibilidad" para refrescar sin cambiar los filtros.

---

## Tests

```bash
cd petvacay-backend && mvn test     # 46 tests, los 6 modulos
```

- `PricingPolicyTest`, `BookingLifecycleTest`, `CancellationPolicyTest`
  (booking-service) — dominio puro: comisiones, maquina de estados, expiracion a
  24h, ventana de escrow, disputas, bloqueo de capacidad y conservacion del
  dinero en las 4 politicas de cancelacion.
- `BookingServiceIntegrationTest` (booking-service) — API REST real: flujo
  completo de reserva, rechazo de cuidador no verificado, conflicto de fechas
  409, validacion 400, cuerpo mal formado 400, parametro invalido 400, ruta
  inexistente 404, y desde la Fase 5 la seguridad: 401 sin token, atribucion de
  la reserva al `subject` del token ignorando cabeceras manipuladas, y las tres
  pruebas que fijan las vulnerabilidades cerradas. Sustituye la lectura HTTP real
  a `identity-service` por un doble de prueba en el limite del puerto
  (`FakeSitterCatalog`), la tecnica estandar para probar un microservicio sin
  levantar sus vecinos.

- `RefreshTokenRotationTest` (identity-service) — rotacion, rechazo de tokens ya
  consumidos, revocacion de la familia completa al detectar reuso, y que las
  credenciales incorrectas no revelan si un email esta registrado. Este test
  encontro un fallo real: la revocacion por reuso se deshacia con el rollback de
  la transaccion que lanzaba el rechazo.
- `PaymentIdempotencyTest` (payment-service) — tres entregas del mismo evento
  producen un unico apunte, eventos distintos se registran ambos, y la pasarela
  devuelve el mismo intento ante la misma clave de idempotencia.
- `GeocodingApplicationServiceTest` (search-service) — unitario y sin contexto
  de Spring: la traduccion entre lo que devuelve `GeocodingPort` y el caso de
  uso, tanto cuando encuentra una direccion como cuando no encuentra nada.

`gateway-service` no tiene tests dedicados: su logica no trivial se hereda del
codigo ya probado en fases anteriores, y lo que cambio en la Fase 4 fueron los
limites de proceso, verificados manualmente contra el sistema real. El resto de
`search-service` (la busqueda por radio en si) tampoco tiene test dedicado por
el mismo motivo — depende de MongoDB real, y se verifico manualmente contra el
sistema en Docker (ver mas abajo).

### Verificacion manual contra el sistema en ejecucion

Los caminos de MongoDB, Kafka y las llamadas entre servicios no entran en la
suite porque requieren infraestructura levantada. Se verificaron con
`docker compose up` y trafico real **exclusivamente a traves del Gateway**:

- Registro de cliente y cuidador, alta de mascota, creacion de perfil, envio y
  aprobacion de KYC.
- Proyeccion al buscador tras aprobar el KYC (`CuidadorIndexadoEvent` por Kafka).
- Creacion de una reserva: `booking-service` resuelve el cuidador llamando por
  HTTP interno a `identity-service` (tarifa y verificacion correctas, sin pasar
  por el Gateway).
- Aceptacion, pago, apunte de escrow en `payment-service` (consumidor Kafka
  independiente del proyector del buscador).
- `GET /api/v1/internal/**` devuelve `404` a traves del Gateway (ruta no
  registrada) y `200` al llamar directamente al puerto de `identity-service`
  (uso interno legitimo).
- Una reserva contra un `sitterId` inexistente devuelve `404` con el `code`
  correcto: el error se propaga sin perdida a traves del limite HTTP entre
  `booking-service` e `identity-service`.
- Arranque completo del stack respetando el orden de dependencias por
  healthcheck (`docker compose up -d` sin `--build` desde cero).

Y, para la Fase 5, con trafico autenticado real por el Gateway:

- El buscador responde `200` sin token; `/bookings` y `/users/me` responden `401`
  en RFC 7807 (rechazado en el Gateway, antes de tocar ningun servicio).
- Un token con un solo caracter alterado se rechaza con `401`: la firma RS256 se
  verifica de verdad.
- Un cliente recibe `403` en `/admin/**`; un cuidador recibe `403` al intentar
  aprobar su propio KYC.
- Tras la aprobacion del admin, el cuidador aparece en el buscador (evento por
  Kafka), y una reserva contra el calcula la tarifa correcta — es decir,
  `booking-service` propago el JWT a `identity-service` con exito.
- El cliente recibe `ERR_WRONG_BOOKING_ROLE` al intentar aceptar su propia
  reserva; un tercero recibe `ERR_NOT_BOOKING_PARTICIPANT` al intentar
  cancelarla.
- **El exploit del actor, cerrado**: cancelando con `?actor=SITTER`, la respuesta
  devuelve `cancelledBy: CLIENT` y `refundToClient: 175.00` — la politica
  correcta — en lugar de los 183,75 € que el parametro habria forzado antes.

Y tras el endurecimiento y la Fase 6:

- Los mensajes de error llegan con la ortografia correcta: *"El correo
  electronico o la contrasena no son correctos"* se lee ya con sus tildes y su ñ.
- El login devuelve la cookie con `HttpOnly; Secure; SameSite=Strict` y el token
  de refresco **no** aparece en el cuerpo de la respuesta.
- Refrescar rota la cookie; presentar el token viejo devuelve
  `ERR_INVALID_REFRESH_TOKEN` **y deja tambien inservible el token vigente** — la
  revocacion en cadena de la familia, verificada de extremo a extremo.
- Rate limiting: 30 peticiones simultaneas a `/auth/login` producen exactamente
  10 respuestas `401` (la rafaga permitida) y 20 `429`, con los contadores
  visibles en Redis.
- El webhook de Stripe con una firma falsificada devuelve `400`; sin secreto
  configurado devuelve `503` y descarta el evento en lugar de aceptarlo.
- `GET /api/v1/search/geocode` contra Nominatim real (no un simulador): "Puerta
  del Sol, Madrid" y "Sagrada Familia, Barcelona" devuelven coordenadas
  correctas a traves del Gateway; una direccion inexistente devuelve `404`; y
  la quinta peticion seguida a la misma IP devuelve `429` — el rate limit
  dedicado de esta ruta (1/s, rafaga 2) funciona por separado del resto del
  buscador.
- Dos resenas reales creadas con `POST /bookings/{id}/reviews` contra una
  reserva finalizada: el rating de un cuidador paso de `0` a `5.0` (1 resena) en
  el buscador, confirmando que el evento llega por Kafka y el `$inc` atomico
  funciona.
- Catalogo de servicios: un cuidador migrado desde la tarifa unica conserva su
  precio intacto como `NIGHT_ACCOMMODATION`; pedirle `WALKING` (que no ofrece)
  devuelve `409 ERR_SERVICE_NOT_OFFERED`; pedirle el servicio que si ofrece
  crea la reserva con el precio correcto.

---

## Hoja de ruta

| Fase | Estado | Contenido |
|---|---|---|
| 1 · Nucleo de dominio y esqueleto frontend | **Hecha** | Hexagonal, dominio completo, React conectado |
| 2 · CQRS con MongoDB | **Hecha** | `MongoSitterReadModelAdapter` con 2dsphere y `$inc` |
| 3 · Apache Kafka | **Hecha** | Eventos de dominio como transporte principal; UX de consistencia eventual en React |
| 4 · Microservicios + Gateway | **Hecha** | 4 servicios independientes, bases de datos propias, Spring Cloud Gateway |
| 5 · Seguridad JWT | **Hecha** | RS256, BCrypt, autorizacion por rol y por propiedad; `X-User-Id` retirada |
| 5b · Endurecimiento | **Hecha** | Refresh rotatorio con deteccion de reuso, cookie HttpOnly, rate limiting, ledger por propietario |
| 6 · Stripe Connect + despliegue | **Hecha** | Stripe Elements, webhook con firma, idempotencia, compose de produccion |
| 6b · Auditoria final + observabilidad | **Hecha** | CORS y `X-Forwarded-For` corregidos, `.env.example`, limites de memoria y heap de JVM explicitos, Prometheus + cAdvisor + Alertmanager |
| 6c · Geolocalizacion, resenas y perfil | **Hecha** | Geolocalizacion real + geocodificacion (Nominatim), sistema de resenas cruzadas con rating agregado en el buscador, foto de perfil, edicion de perfil expuesta en el frontend |
| 6d · Catalogo de servicios por cuidador | **Hecha** | Cada cuidador ofrece los servicios que quiere con su propia tarifa (`negocio.md` seccion 2), en vez de una unica tarifa para los tres; migracion de datos existentes incluida |

### Nota sobre `package-lock.json`

El lock del frontend se genera desde Linux (`docker run --rm -v ${PWD}:/app -w /app
node:22-alpine npm install --package-lock-only`) porque npm omite del lock las
dependencias nativas opcionales de plataformas distintas a la del equipo donde
se ejecuta. Un lock generado solo en Windows hace fallar `npm ci` dentro de la
imagen. El lock resultante funciona en ambos sistemas.
