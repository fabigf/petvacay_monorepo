import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { login as loginRequest, register, type RegisterRequest } from './authApi'
import { useAuthStore } from '@/core/security/authStore'
import { Button, Card, Field, Input } from '@/shared/components/ui'
import { useProblemToast } from '@/shared/hooks/useProblemToast'

const emptyForm: RegisterRequest = {
  email: '',
  password: '',
  firstName: '',
  lastName: '',
  birthDate: '1990-01-01',
  asSitter: false,
}

export function RegisterPage() {
  const navigate = useNavigate()
  const login = useAuthStore((state) => state.login)
  const handleProblem = useProblemToast()

  const [form, setForm] = useState<RegisterRequest>(emptyForm)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setFieldErrors({})
    try {
      const profile = await register(form)

      // El registro solo crea la cuenta; el token lo emite el login. Se encadena
      // aquí para que el usuario no tenga que volver a escribir sus credenciales
      // recién creadas, pero son dos operaciones distintas a propósito: crear una
      // cuenta y obtener credenciales de acceso no son lo mismo.
      const session = await loginRequest({ email: form.email, password: form.password })
      login({
        token: session.accessToken,
        profile: session.user,
        sitterVerified: session.sitterVerified,
        expiresAt: Date.now() + session.expiresIn * 1000,
      })

      toast.success(`Bienvenida a PetVacay, ${profile.firstName}`)
      navigate(form.asSitter ? '/dashboard/sitter' : '/pets')
    } catch (error) {
      setFieldErrors(handleProblem(error).fieldErrors)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-md">
      <h1 className="mb-6 text-2xl font-bold text-slate-800">Crear cuenta</h1>
      <Card>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          <div className="grid grid-cols-2 gap-3">
            <Field label="Nombre" htmlFor="firstName" error={fieldErrors.firstName}>
              <Input
                id="firstName"
                required
                value={form.firstName}
                aria-invalid={Boolean(fieldErrors.firstName)}
                onChange={(e) => setForm({ ...form, firstName: e.target.value })}
              />
            </Field>
            <Field label="Apellidos" htmlFor="lastName" error={fieldErrors.lastName}>
              <Input
                id="lastName"
                required
                value={form.lastName}
                aria-invalid={Boolean(fieldErrors.lastName)}
                onChange={(e) => setForm({ ...form, lastName: e.target.value })}
              />
            </Field>
          </div>

          <Field label="Correo electrónico" htmlFor="email" error={fieldErrors.email}>
            <Input
              id="email"
              type="email"
              required
              value={form.email}
              aria-invalid={Boolean(fieldErrors.email)}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
            />
          </Field>

          <Field
            label="Contraseña"
            htmlFor="password"
            hint="Mínimo 8 caracteres."
            error={fieldErrors.password}
          >
            <Input
              id="password"
              type="password"
              required
              minLength={8}
              value={form.password}
              aria-invalid={Boolean(fieldErrors.password)}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
            />
          </Field>

          <Field
            label="Fecha de nacimiento"
            htmlFor="birthDate"
            hint="Para ofrecer servicios de cuidado es obligatorio ser mayor de 18 años."
            error={fieldErrors.birthDate}
          >
            <Input
              id="birthDate"
              type="date"
              required
              value={form.birthDate}
              aria-invalid={Boolean(fieldErrors.birthDate)}
              onChange={(e) => setForm({ ...form, birthDate: e.target.value })}
            />
          </Field>

          <label className="flex items-center gap-3 rounded-lg border border-sand-200 p-3">
            <input
              type="checkbox"
              className="size-4 accent-brand-600"
              checked={form.asSitter}
              onChange={(e) => setForm({ ...form, asSitter: e.target.checked })}
            />
            <span className="text-sm text-slate-700">
              Quiero ofrecer servicios de cuidado
              <span className="block text-xs text-slate-500">
                Tendrás que superar la verificación de identidad antes de aparecer en el buscador.
              </span>
            </span>
          </label>

          <Button type="submit" loading={submitting}>
            Crear cuenta
          </Button>

          <p className="text-center text-sm text-slate-500">
            ¿Ya tienes cuenta?{' '}
            <Link to="/login" className="font-medium text-brand-700 hover:underline">
              Entrar
            </Link>
          </p>
        </form>
      </Card>
    </div>
  )
}
