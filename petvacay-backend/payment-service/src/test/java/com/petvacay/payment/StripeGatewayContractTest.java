package com.petvacay.payment;

import com.petvacay.payment.adapters.out.stripe.StripePaymentGatewayAdapter;
import com.petvacay.payment.ports.out.PaymentGatewayPort;
import com.petvacay.shared.domain.Money;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Ejercita {@link StripePaymentGatewayAdapter} — el adaptador REAL — contra
 * {@code stripe-mock}, el servidor de pruebas que publica la propia Stripe.
 *
 * <p><b>Por qué hace falta.</b> El adaptador simulado prueba todo el sistema
 * menos precisamente esta clase: sustituye las llamadas de red por
 * identificadores en memoria, así que no puede detectar que un parámetro esté
 * mal construido, que falte uno obligatorio o que el importe se convierta mal a
 * céntimos. Ese hueco estaba escrito en el javadoc del simulador ("la
 * integración con Stripe está implementada pero no verificada"); esto lo cierra
 * hasta donde se puede sin una cuenta de Stripe.
 *
 * <p><b>Qué NO cubre.</b> {@code stripe-mock} responde a partir de la
 * especificación OpenAPI de Stripe: valida la forma de la petición, pero no
 * mantiene estado. En concreto <b>no implementa idempotencia</b> — dos llamadas
 * con la misma clave devuelven identificadores distintos — ni webhooks ni firmas
 * ni cobros. La idempotencia de nuestro lado la cubre
 * {@link PaymentIdempotencyTest}; la de Stripe es parte de su contrato y solo se
 * puede comprobar con claves de prueba reales.
 *
 * <p><b>Cómo se ejecuta.</b> {@code docker compose up stripe-mock} levanta el
 * servidor en el puerto 12111. Si no está escuchando, el test se salta en lugar
 * de fallar: es una verificación de integración con una dependencia externa
 * opcional, y no debe romper el build de quien no la tenga levantada.
 */
@DisplayName("payment-service: contrato real contra stripe-mock")
class StripeGatewayContractTest {

    /**
     * Puerto por defecto de stripe-mock. Se puede apuntar a otro sitio con
     * {@code -Dpetvacay.stripe-mock.url=...} para ejecutarlo en integración
     * continua contra un contenedor con otra dirección.
     */
    private static final String STRIPE_MOCK_URL =
            System.getProperty("petvacay.stripe-mock.url", "http://localhost:12111");

    private static StripePaymentGatewayAdapter adapter;

    @BeforeAll
    static void connectToStripeMock() {
        assumeTrue(isListening(STRIPE_MOCK_URL),
                "stripe-mock no está escuchando en " + STRIPE_MOCK_URL
                        + " — arráncalo con: docker compose up -d stripe-mock");

        // stripe-mock no comprueba que la clave exista — no hay ninguna cuenta
        // detras — pero si exige que TENGA FORMA de clave de prueba: el prefijo
        // `sk_test_` seguido de caracteres alfanumericos. Que baste con esto es
        // justamente lo que permite verificar la integracion sin registrarse.
        adapter = new StripePaymentGatewayAdapter("sk_test_123", STRIPE_MOCK_URL);
    }

    @Test
    @DisplayName("crea un PaymentIntent que Stripe acepta y devuelve su client_secret")
    void createsAPaymentIntentStripeAccepts() {
        UUID bookingId = UUID.randomUUID();

        PaymentGatewayPort.PaymentIntent intent = adapter.createPaymentIntent(
                bookingId, UUID.randomUUID(), Money.eur("183.75"), "pay-" + bookingId);

        // Que la respuesta se parsee sin excepcion ya prueba que la peticion era
        // valida: stripe-mock devuelve 400 ante parametros que la API real
        // tambien rechazaria.
        assertThat(intent.paymentIntentId()).startsWith("pi_");
        // Es lo que React necesita para montar el formulario de Stripe Elements:
        // si no llegara, el pago no podria ni empezar en el navegador.
        assertThat(intent.clientSecret()).contains("_secret_");
        assertThat(intent.amount()).isEqualTo(Money.eur("183.75"));
    }

    /**
     * El fallo silencioso mas caro posible en una pasarela: Stripe cobra en
     * centimos, y convertir mal deja un cargo distinto del que la pantalla
     * enseño. Con {@code double}, {@code 183.75 * 100} da 18374.999... y truncar
     * cobraria un centimo de menos en cada reserva.
     */
    @Test
    @DisplayName("los importes con decimales que no caen redondos no pierden centimos")
    void convertsAmountsToMinorUnitsWithoutLosingCents() {
        for (String importe : new String[] {"183.75", "0.01", "999.99", "20.10", "1234.56"}) {
            UUID bookingId = UUID.randomUUID();

            PaymentGatewayPort.PaymentIntent intent = adapter.createPaymentIntent(
                    bookingId, UUID.randomUUID(), Money.eur(importe), "pay-" + bookingId);

            assertThat(intent.amount())
                    .as("el importe de %s no debe alterarse por el camino", importe)
                    .isEqualTo(Money.eur(importe));
        }
    }

    @Test
    @DisplayName("un reembolso se construye con parametros que Stripe acepta")
    void buildsAValidRefund() {
        PaymentGatewayPort.RefundResult refund = adapter.refund(
                "pi_" + randomStripeId(), Money.eur("175.00"), "refund-" + UUID.randomUUID());

        assertThat(refund.refundId()).startsWith("re_");
        assertThat(refund.amount()).isEqualTo(Money.eur("175.00"));
    }

    @Test
    @DisplayName("una transferencia al cuidador se construye con parametros que Stripe acepta")
    void buildsAValidTransfer() {
        UUID bookingId = UUID.randomUUID();

        PaymentGatewayPort.TransferResult transfer = adapter.transferToSitter(
                bookingId, "acct_" + randomStripeId(), Money.eur("148.75"), "payout-" + bookingId);

        assertThat(transfer.transferId()).startsWith("tr_");
        assertThat(transfer.amount()).isEqualTo(Money.eur("148.75"));
    }

    /**
     * Confirma que el mock no dice que si a todo.
     *
     * <p>Sin esta comprobacion los tests de arriba no probarian gran cosa: si
     * stripe-mock aceptara cualquier peticion, que acepte las nuestras no
     * significaria que estan bien construidas.
     *
     * <p>Se ataca por HTTP en crudo y no a traves del adaptador porque el
     * adaptador — correctamente — no sabe construir una peticion invalida: para
     * probar que el arbitro pita hace falta cometer la falta a mano. Lo que
     * stripe-mock comprueba son los <b>campos obligatorios</b> y el formato de
     * la clave; NO valida los valores de negocio (acepta sin rechistar un
     * importe negativo, cosa que la API real no haria).
     */
    @Test
    @DisplayName("stripe-mock rechaza lo invalido, asi que aceptar significa algo")
    void mockActuallyValidatesRequests() {
        // Un PaymentIntent sin `currency`, que es obligatorio.
        assertThat(postFormToStripeMock("/v1/payment_intents", "amount=1000"))
                .as("falta un parametro obligatorio: stripe-mock debe rechazarlo")
                .isEqualTo(400);

        // Y el mismo con la divisa puesta si pasa, para descartar que devuelva
        // 400 ante cualquier cosa.
        assertThat(postFormToStripeMock("/v1/payment_intents", "amount=1000&currency=eur"))
                .isEqualTo(200);
    }

    /** Codigo HTTP de un POST de formulario contra stripe-mock. */
    private static int postFormToStripeMock(String path, String body) {
        try {
            HttpResponse<Void> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(STRIPE_MOCK_URL + path))
                            .header("Authorization", "Bearer sk_test_123")
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo hablar con stripe-mock", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido hablando con stripe-mock", e);
        }
    }

    /** Identificador con la forma de los de Stripe, para los parametros de entrada. */
    private static String randomStripeId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * ¿Hay algo escuchando? Se comprueba con un socket y no con una peticion
     * HTTP para no confundir "no esta levantado" con "responde un error".
     */
    private static boolean isListening(String url) {
        URI uri = URI.create(url);
        int port = uri.getPort() > 0 ? uri.getPort() : 80;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(uri.getHost(), port), 1000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
