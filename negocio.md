# Documento de Lógica de Negocio - Plataforma "PetVacay" (negocio.md)

Este documento define en profundidad las reglas de negocio, los flujos operativos, los actores clave y el modelo de monetización para la plataforma de intermediación de cuidado de mascotas durante periodos vacacionales.

---

## 1. Visión General del Negocio

**PetVacay** es un marketplace bidireccional gestionado que conecta a dueños de mascotas (clientes) que viajan por vacaciones con cuidadores locales de confianza (proveedores de servicios o *sitters*). 

El propósito principal de la plataforma es resolver la barrera de la desconfianza inherente al cuidado de animales mediante tres pilares de negocio:
1. **Validación estricta de identidad (KYC):** Filtro de cuidadores antes de permitirles ofrecer servicios.
2. **Pasarela de pago seguro en depósito (*Escrow*):** Protección financiera para ambas partes.
3. **Reputación auditable:** Sistema de reseñas cruzadas inmutables que premia el buen servicio.

---

## 2. Actores del Sistema

### 2.1. El Cliente (Dueño de la Mascota)
* **Definición:** Persona física que debe ausentarse de su hogar (habitualmente por vacaciones o viajes de trabajo) y requiere un servicio de alojamiento, guardería de día o paseos para sus animales de compañía.
* **Atributos y Reglas de Negocio:**
    * Debe registrar un perfil de usuario válido con un método de pago asociado antes de reservar.
    * Cada cliente puede tener una o más "Mascotas" asociadas a su perfil.
    * Es obligatorio detallar la ficha técnica de la mascota: especie, raza, edad, tamaño/peso, necesidades médicas especiales (ej. medicación inyectable), restricciones alimentarias y nivel de sociabilidad con otros animales o niños.

### 2.2. El Cuidador (Sitter)
* **Definición:** Persona particular o profesional independiente que ofrece espacio en su hogar, tiempo y capacidades para el cuidado de animales a cambio de una remuneración económica fija.
* **Atributos y Reglas de Negocio:**
    * Debe ser mayor de edad (+18 años) y superar el proceso de verificación de identidad de la plataforma.
    * Define sus propios servicios (Alojamiento nocturno, Guardería de día, Paseos por horas).
    * Configura de forma autónoma sus tarifas base por noche/hora, su capacidad máxima de mascotas simultáneas y las restricciones de los animales que acepta (ej. "Solo perros de menos de 10kg", "No acepta gatos").
    * Mantiene un calendario de disponibilidad en tiempo real sincronizado a través del sistema.

### 2.3. El Administrador de la Plataforma
* **Definición:** Personal interno de PetVacay encargado del soporte técnico, la supervisión de la comunidad y la resolución de conflictos.
* **Atributos y Reglas de Negocio:**
    * Tiene la potestad de aprobar o rechazar perfiles de cuidadores tras revisar su documentación.
    * Interviene como árbitro en disputas de reservas bloqueando o liberando fondos de forma manual si es necesario.
    * Puede expulsar de manera fulminante a usuarios o cuidadores que infrinjan los términos de servicio.

---

## 3. Modelo de Monetización y Flujo Financiero

Para evitar el problema de la desintermediación (que las partes se salten la plataforma en futuras ocasiones) y para sostener legalmente el negocio, se implementa un modelo de **Comisión Doble (Split Commission)** respaldado por un sistema transaccional automatizado.

### 3.1. Estructura de Comisiones
La plataforma cobra una tarifa por la infraestructura tecnológica, las herramientas de marketing, la gestión del soporte y el paraguas de seguridad provisto.
* **Comisión al Cuidador:** Se le aplica una retención del **15%** sobre el precio base que el cuidador haya estipulado para el servicio.
* **Tasa de Gestión al Cliente:** Se añade un cargo del **5%** sobre el total de la reserva al cliente en el momento del pago.

### 3.2. Mecanismo de Depósito en Custodia (Escrow)
El flujo del dinero está estrictamente desacoplado del tiempo de ejecución del servicio:
1. **Cobro Anticipado:** En cuanto una reserva es aceptada por el cuidador, el cliente paga el **100% del servicio + la tasa de gestión del 5%**.
2. **Retención en Custodia:** Los fondos no ingresan en la cuenta bancaria de PetVacay ni en la del cuidador; se congelan de forma segura en un depósito intermedio (*escrow*) gestionado por el proveedor de pagos (Stripe Connect).
3. **Liberación de Fondo:** 24 horas después de la fecha y hora oficiales de la finalización del servicio de cuidado, si el cliente no ha abierto ninguna disputa por negligencia, el sistema libera automáticamente los fondos:
    * El **85%** del precio base se transfiere al saldo líquido del cuidador.
    * El **15%** de la comisión del cuidador junto con el **5%** del cliente se consolidan como el ingreso neto operativo de la plataforma.

---

## 4. Flujos de Trabajo Clave (Workflows de Dominio)

### 4.1. Registro y Verificación del Cuidador (Onboarding & KYC)
1. El candidato se registra y rellena su perfil: descripción, fotos de la vivienda, experiencia previa y cuestionario de aptitud animal.
2. El sistema le exige subir una imagen nítida de su documento de identidad (DNI, NIE o Pasaporte).
3. El microservicio de identidad procesa el archivo de forma asíncrona. Un administrador humano realiza la verificación final del perfil.
4. **Regla de Negocio:** El perfil del cuidador permanece en estado `PENDIENTE_VERIFICACION`. Mientras esté en este estado, el cuidador no aparecerá indexado en el motor de búsqueda de la plataforma ni podrá recibir solicitudes.

### 4.2. Ciclo de Vida de una Reserva (Booking Lifecycle)
Toda interacción de reserva sigue una máquina de estados controlada por eventos del sistema:

    [ Borrador ]
         │
         ▼ (Cliente envía solicitud)
    [ Solicitado ] ──(Expiración 24h o "> 48h del inicio" Rechazo)──► [ Cancelado ]
         │
         ▼ (Cuidador acepta la propuesta)
    [ Pendiente de Pago ] ──(Fallo de pago / Cancelación)──► [ Cancelado ]
         │
         ▼ (Pago confirmado exitosamente)
    [ Confirmado ] 
         │
         ▼ (Llega la fecha de inicio)
    [ En Progreso ]
         │
         ▼ (Llega la fecha de fin + ventana de 24h)
    [ Finalizado ] ──(Ejecución de pagos automáticos)

* **Expiración de Solicitud:** Si el cuidador no responde a una solicitud en un plazo máximo de 24 horas, el sistema cancela automáticamente el proceso para no bloquear la búsqueda del cliente.
* **Bloqueo de Calendario:** En el instante en que una reserva pasa al estado `CONFIRMADO`, los días implicados quedan automáticamente bloqueados en el calendario de ese cuidador en MongoDB, impidiendo que reciba otras solicitudes simultáneas que excedan su capacidad máxima de mascotas.
* **Franja de entrega y recogida:** No estaba especificada en el diseño original; se fija aquí como decisión de producto al implementarla. La mascota se entrega el día de inicio de la reserva y se recoge el día de fin, ambos dentro de una franja horaria fija y razonable de **09:00 a 20:00**. Es una franja única de la plataforma, no configurable todavía por cuidador — queda como mejora futura ligarla al horario real de cada cuidador. La dirección exacta y esta franja solo se revelan al cliente **a partir de `CONFIRMADO`** (pago ya retenido en custodia): antes de eso, incluida la fase de solicitud, el cliente solo ve la distancia aproximada del cuidador, nunca su domicilio — evita que cualquiera con una cuenta pueda averiguar dónde vive un cuidador sin haber llegado siquiera a pagar.

### 4.3. Políticas de Cancelación y Reembolsos
* **Cancelación con Antelación (> 48 horas del inicio):** Si el cliente cancela la reserva antes de las 48 horas del inicio del servicio, recibe un reembolso del 100% de la tarifa base. La tasa de gestión del 5% no es reembolsable.
* **Cancelación de Última Hora (< 48 horas del inicio):** Si el cliente cancela en las últimas 48 horas, se le devuelve el dinero excepto el importe equivalente a la primera noche del servicio, la cual se transfiere directamente al cuidador como compensación por el bloqueo de su calendario.
* **Cancelación por parte del Cuidador:** Si el cuidador cancela una reserva confirmada, se le reembolsa el 100% absoluto al cliente (incluyendo las tasas de gestión) y el cuidador recibe una penalización automática en su posicionamiento del buscador, además de una reseña negativa del sistema.

### 4.4. Gestión de Disputas e Incidencias
* Si durante el servicio ocurre una emergencia (enfermedad de la mascota, daños materiales o accidentes), cualquiera de las partes puede notificarlo en la app.
* Si el cliente abre una disputa formal dentro de las primeras 24 horas post-servicio, el flujo automático de liberación de fondos a Stripe se detiene de inmediato.
* El estado de la reserva se congela en `EN_DISPUTA` hasta que el equipo de administración analiza las pruebas (mensajes internos del chat de la app, informes veterinarios) y dicta una resolución manual.

---

## 5. Eventos de Negocio Críticos (Event-Driven Core)

La lógica del negocio se propaga de manera asíncrona mediante eventos de dominio que conectan los diferentes aspectos del ecosistema:

* **`ReservaSolicitadaEvent`:** Dispara el envío de notificaciones push y correos electrónicos de alta prioridad al cuidador para indicarle que tiene una propuesta pendiente de aprobación.
* **`PagoConfirmadoEvent`:** Evento vital que indica el éxito financiero. Dispara de forma paralela tres acciones:
    1. Envía la confirmación al cliente con los datos exactos de la dirección de entrega de la mascota, junto con la franja horaria de entrega y recogida (ver 4.2, "Franja de entrega y recogida").
    2. Envía un comando al servicio de búsquedas para actualizar el calendario de disponibilidad del cuidador en la base de datos de lectura.
    3. Inicia un temporizador programado (Scheduler) en el servicio de pagos para ejecutar la liberación del dinero una vez expire el servicio.
* **`MascotaEnPeligroEvent` (Urgencia):** Evento emitido si el cuidador pulsa el botón de alerta médica. Notifica de inmediato al dueño por todos los canales (SMS, Push, Llamada automatizada), habilita un canal de mensajería prioritario con el contacto de emergencia alternativo y envía un aviso al equipo de soporte de la plataforma para coordinar asistencia veterinaria local si fuera necesario.