-- Importe autorizado de cada reserva, para poder cobrarlo sin creerse al cliente.
--
-- Hasta ahora `preparePayment` recibia el importe en el cuerpo de la peticion y
-- lo pasaba tal cual a la pasarela: quien reservaba podia pedir un PaymentIntent
-- de 0,01 EUR para una reserva de 588,00 EUR y pagar eso. El javadoc afirmaba
-- que el importe "no llega del cliente", pero nada lo comprobaba.
--
-- El dato ya viajaba en `ReservaSolicitadaEvent.amountToPay`, calculado por el
-- dominio de booking-service; solo faltaba conservarlo aqui. Se guarda junto a
-- las partes porque responde a la misma pregunta ("¿que se sabe con certeza de
-- esta reserva sin preguntarle a nadie?") y lo alimenta el mismo evento.

ALTER TABLE booking_parties
    ADD COLUMN amount_to_pay NUMERIC(12, 2),
    ADD COLUMN currency      VARCHAR(3);

-- Las filas existentes se quedan sin importe: se sembraron antes de que este
-- dato se guardara. `preparePayment` rechaza cobrar cuando falta, en lugar de
-- suponer una cantidad — es preferible que un pago antiguo falle de forma
-- visible a cobrar un importe inventado.
