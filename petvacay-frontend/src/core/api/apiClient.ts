import axios, { type AxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/core/security/authStore'

/**
 * Cliente HTTP único de la aplicación (especificaciones.md sección 2.1).
 *
 * Apunta a `/api/v1` para que el proxy de Vite (dev) o Nginx (producción)
 * resuelvan el destino real. Desde la Fase 4 ambos apuntan al API Gateway, que
 * reparte cada ruta entre los 4 microservicios: este fichero no lo sabe ni le
 * importa, que es exactamente el punto de tener un Gateway.
 */
const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api/v1',
  timeout: 10_000,
  headers: { 'Content-Type': 'application/json' },
  // Necesario para que el navegador envíe la cookie HttpOnly de refresco.
  withCredentials: true,
})

apiClient.interceptors.request.use(
  (config) => {
    const { token } = useAuthStore.getState()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

/**
 * Refresco en curso, compartido por todas las peticiones que fallen a la vez.
 *
 * <p>Sin esto, una pantalla que lanza cinco peticiones en paralelo al caducar el
 * token dispararía cinco refrescos simultáneos. Con rotación de tokens eso es
 * peor que ineficiente: el primero consume el token y los otros cuatro llegan
 * con uno ya usado, lo que el backend interpreta —correctamente— como reuso y
 * responde revocando la sesión entera. Serializar aquí evita provocar esa falsa
 * alarma.
 */
let refreshInFlight: Promise<boolean> | null = null

async function refreshAccessToken(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        // Cliente aparte: si usara `apiClient`, un 401 del propio refresco
        // volvería a entrar en este interceptor y crearía un bucle infinito.
        const { data } = await axios.post(
          `${apiClient.defaults.baseURL}/auth/refresh`,
          null,
          { withCredentials: true },
        )
        useAuthStore.getState().login({
          token: data.accessToken,
          profile: data.user,
          sitterVerified: data.sitterVerified,
          expiresAt: Date.now() + data.expiresIn * 1000,
        })
        return true
      } catch {
        useAuthStore.getState().logout()
        return false
      } finally {
        refreshInFlight = null
      }
    })()
  }
  return refreshInFlight
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config as AxiosRequestConfig & { _retried?: boolean }
    const isAuthCall = original?.url?.includes('/auth/')

    // Un 401 puede significar solo que el token de acceso (15 min) ha caducado.
    // Se intenta renovarlo con la cookie de refresco y se reintenta una vez; si
    // el refresco también falla, la sesión se ha terminado de verdad.
    if (error.response?.status === 401 && original && !original._retried && !isAuthCall) {
      original._retried = true
      if (await refreshAccessToken()) {
        return apiClient(original)
      }
    }

    return Promise.reject(error)
  },
)

/**
 * Restaura la sesión al cargar la aplicación.
 *
 * <p>El token de acceso solo vive en memoria, así que tras un refresco de página
 * no existe. La cookie de refresco sí sobrevive: esta llamada la canjea por un
 * token nuevo. Si no hay cookie o ya no vale, el usuario queda sin sesión, que
 * es el resultado correcto.
 */
export async function restoreSession(): Promise<void> {
  const store = useAuthStore.getState()
  try {
    await refreshAccessToken()
  } finally {
    store.markBootstrapped()
  }
}

export default apiClient
