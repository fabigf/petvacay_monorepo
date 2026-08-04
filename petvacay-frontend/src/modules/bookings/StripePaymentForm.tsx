import { useState } from 'react'
import { loadStripe } from '@stripe/stripe-js'
import {
  Elements,
  PaymentElement,
  useElements,
  useStripe,
} from '@stripe/react-stripe-js'
import { toast } from 'sonner'
import { Button, Card } from '@/shared/components/ui'

/**
 * Clave PÚBLICA de Stripe. Es publicable por definición: identifica la cuenta y
 * no autoriza nada por sí sola. La clave secreta vive únicamente en
 * payment-service y jamás llega al navegador.
 */
const publishableKey = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY ?? ''
const stripePromise = publishableKey ? loadStripe(publishableKey) : null

interface StripePaymentFormProps {
  clientSecret: string
  amountLabel: string
  onSuccess: () => void
}

/**
 * Formulario de pago con Stripe Elements (especificaciones.md sección 3).
 *
 * <p>El campo de tarjeta es un iframe servido por Stripe: los datos van del
 * navegador a Stripe **sin pasar por PetVacay**, que es lo que mantiene el
 * sistema fuera del alcance más exigente de PCI-DSS. Este componente nunca ve
 * el número de tarjeta, ni siquiera puede leerlo del DOM.
 */
export function StripePaymentForm({ clientSecret, amountLabel, onSuccess }: StripePaymentFormProps) {
  if (!stripePromise) {
    return (
      <Card className="border-amber-200 bg-amber-50">
        <p className="text-sm text-amber-800">
          El pago con tarjeta no está configurado en este entorno. Define
          <code className="mx-1 rounded bg-amber-100 px-1">VITE_STRIPE_PUBLISHABLE_KEY</code>
          para habilitarlo.
        </p>
      </Card>
    )
  }

  return (
    <Elements stripe={stripePromise} options={{ clientSecret, locale: 'es' }}>
      <CheckoutForm amountLabel={amountLabel} onSuccess={onSuccess} />
    </Elements>
  )
}

function CheckoutForm({ amountLabel, onSuccess }: { amountLabel: string; onSuccess: () => void }) {
  const stripe = useStripe()
  const elements = useElements()
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!stripe || !elements) return

    setSubmitting(true)
    const { error, paymentIntent } = await stripe.confirmPayment({
      elements,
      // La confirmación se resuelve aquí mismo salvo que el banco exija 3D
      // Secure, en cuyo caso Stripe redirige y vuelve a esta URL.
      confirmParams: { return_url: window.location.href },
      redirect: 'if_required',
    })

    setSubmitting(false)

    if (error) {
      // Errores de tarjeta (fondos, CVC) o de validación: el mensaje ya viene
      // traducido por Stripe según el `locale`.
      toast.error('No se ha podido completar el pago', { description: error.message })
      return
    }

    if (paymentIntent?.status === 'succeeded') {
      // El estado definitivo de la reserva lo fija el webhook, no esta respuesta:
      // el navegador puede cerrarse justo aquí y el cobro seguiría siendo válido.
      toast.success('Pago aceptado', {
        description: 'Estamos confirmando la reserva; puede tardar unos segundos.',
      })
      onSuccess()
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <PaymentElement />
      <Button type="submit" loading={submitting} disabled={!stripe}>
        Pagar {amountLabel}
      </Button>
      <p className="text-xs text-slate-500">
        Los datos de tu tarjeta viajan directamente a Stripe. PetVacay no los recibe ni los
        almacena.
      </p>
    </form>
  )
}
