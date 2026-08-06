import { Link } from 'react-router-dom'
import { Button } from '@/shared/components/ui'

/**
 * Página para una ruta que no existe.
 *
 * <p>Sustituye al redirigir en silencio a la portada. Ese comportamiento tenía
 * un problema concreto: ante un enlace mal copiado, la persona acababa en la
 * portada sin entender por qué y creyendo que la aplicación había perdido su
 * reserva. Decirlo y ofrecer una salida es más útil que disimularlo.
 */
export function NotFoundPage() {
  return (
    <div className="mx-auto flex max-w-md flex-col items-center py-16 text-center">
      <span aria-hidden className="text-5xl">
        🐾
      </span>
      <h1 className="mt-6 text-3xl font-bold tracking-tight text-slate-900">
        Esta página no existe
      </h1>
      <p className="mt-3 text-slate-600">
        El enlace puede estar mal copiado o la página haberse movido. Tus reservas siguen donde
        estaban.
      </p>

      <div className="mt-8 flex flex-wrap justify-center gap-3">
        <Link to="/">
          <Button>Ir a la portada</Button>
        </Link>
        <Link to="/search">
          <Button variant="secondary">Buscar cuidador</Button>
        </Link>
      </div>

      <p className="mt-8 text-sm text-slate-500">
        ¿Necesitas ayuda?{' '}
        <Link to="/contacto" className="font-medium text-brand-700 underline hover:text-brand-800">
          Escríbenos
        </Link>
        .
      </p>
    </div>
  )
}
