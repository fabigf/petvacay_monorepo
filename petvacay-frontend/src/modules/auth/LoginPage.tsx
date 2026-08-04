import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { login as loginRequest } from './authApi'
import { useAuthStore } from '@/core/security/authStore'
import { Button, Card, Field, Input } from '@/shared/components/ui'
import { useProblemToast } from '@/shared/hooks/useProblemToast'

/**
 * Inicio de sesión con email y contraseña (Fase 5).
 *
 * <p>Sustituye al formulario provisional de las Fases 1-4, que pedía pegar a mano
 * el UUID del usuario porque todavía no había autenticación real. Ahora
 * `POST /auth/login` valida las credenciales contra BCrypt y devuelve un JWT
 * firmado en RS256.
 */
export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const login = useAuthStore((state) => state.login)
  const handleProblem = useProblemToast()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setFieldErrors({})
    try {
      const session = await loginRequest({ email, password })

      login({
        token: session.accessToken,
        profile: session.user,
        sitterVerified: session.sitterVerified,
        expiresAt: Date.now() + session.expiresIn * 1000,
      })

      toast.success(`Sesión iniciada como ${session.user.firstName}`)
      const from = (location.state as { from?: string } | null)?.from
      navigate(from ?? '/dashboard')
    } catch (error) {
      setFieldErrors(handleProblem(error).fieldErrors)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-md">
      <h1 className="mb-6 text-2xl font-bold text-slate-800">Entrar</h1>
      <Card>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <Field label="Correo electrónico" htmlFor="email" error={fieldErrors.email}>
            <Input
              id="email"
              type="email"
              required
              autoComplete="email"
              value={email}
              aria-invalid={Boolean(fieldErrors.email)}
              onChange={(e) => setEmail(e.target.value)}
            />
          </Field>

          <Field label="Contraseña" htmlFor="password" error={fieldErrors.password}>
            <Input
              id="password"
              type="password"
              required
              autoComplete="current-password"
              value={password}
              aria-invalid={Boolean(fieldErrors.password)}
              onChange={(e) => setPassword(e.target.value)}
            />
          </Field>

          <Button type="submit" loading={submitting}>
            Entrar
          </Button>

          <p className="text-center text-sm text-slate-500">
            ¿No tienes cuenta?{' '}
            <Link to="/register" className="font-medium text-brand-700 hover:underline">
              Crear una
            </Link>
          </p>
        </form>
      </Card>
    </div>
  )
}
