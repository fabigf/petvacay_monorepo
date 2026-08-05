import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '@/modules/notifications/notificationsApi'
import type { Notification, NotificationType } from '@/core/api/types'

/** Cada cuánto se vuelve a preguntar por avisos nuevos, en milisegundos. */
const POLL_INTERVAL_MS = 60_000

/**
 * Icono por tipo. Se elige aquí y no en el backend porque es decoración de esta
 * interfaz concreta: otro canal del futuro Notification Service (un correo, un
 * SMS) usaría el mismo aviso sin ningún emoji.
 */
const icons: Record<NotificationType, string> = {
  BOOKING_REQUESTED: '📩',
  BOOKING_ACCEPTED: '✅',
  BOOKING_REJECTED: '🚫',
  BOOKING_CONFIRMED: '💳',
  BOOKING_CANCELLED: '🚫',
  BOOKING_FINISHED: '🏁',
  DISPUTE_OPENED: '⚠️',
  DISPUTE_RESOLVED: '⚖️',
  PET_EMERGENCY: '🆘',
}

function relativeTime(iso: string): string {
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60_000)
  if (minutes < 1) return 'ahora mismo'
  if (minutes < 60) return `hace ${minutes} min`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `hace ${hours} h`
  return new Date(iso).toLocaleDateString('es-ES')
}

/**
 * Campana de avisos de la cabecera.
 *
 * <p>Se refresca por sondeo cada minuto en vez de mantener una conexión abierta
 * (WebSocket o SSE): con eventos que viajan por Kafka la app ya es eventualmente
 * consistente — un aviso puede tardar segundos en aparecer de todas formas — y
 * un sondeo ligero no obliga a sostener una conexión por usuario en el Gateway.
 */
export function NotificationBell() {
  const navigate = useNavigate()
  const [notifications, setNotifications] = useState<Notification[]>([])
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  const refresh = useCallback(() => {
    // Un fallo al consultar avisos no debe romper la cabecera ni sacar un toast:
    // es información secundaria y el siguiente sondeo lo reintenta.
    listNotifications()
      .then(setNotifications)
      .catch(() => undefined)
  }, [])

  useEffect(() => {
    refresh()
    const timer = window.setInterval(refresh, POLL_INTERVAL_MS)
    return () => window.clearInterval(timer)
  }, [refresh])

  // Cerrar al pulsar fuera: es un desplegable, no un diálogo modal.
  useEffect(() => {
    if (!open) return
    const onClickOutside = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [open])

  const unread = notifications.filter((notification) => notification.unread).length

  const handleOpenNotification = async (notification: Notification) => {
    setOpen(false)
    if (notification.unread) {
      setNotifications((current) =>
        current.map((item) =>
          item.notificationId === notification.notificationId ? { ...item, unread: false } : item,
        ),
      )
      await markNotificationRead(notification.notificationId).catch(() => refresh())
    }
    if (notification.bookingId) navigate(`/bookings/${notification.bookingId}`)
  }

  const handleMarkAllRead = async () => {
    setNotifications((current) => current.map((item) => ({ ...item, unread: false })))
    await markAllNotificationsRead().catch(() => refresh())
  }

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((current) => !current)}
        aria-label={unread > 0 ? `Notificaciones (${unread} sin leer)` : 'Notificaciones'}
        aria-expanded={open}
        className="relative rounded-lg px-2 py-2 text-lg text-slate-600 transition hover:bg-slate-100"
      >
        🔔
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 inline-flex min-w-4 items-center justify-center rounded-full bg-red-600 px-1 text-[10px] font-bold text-white">
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-20 mt-2 w-80 overflow-hidden rounded-xl border border-sand-200 bg-white shadow-lg">
          <div className="flex items-center justify-between border-b border-sand-200 px-4 py-2.5">
            <span className="text-sm font-semibold text-slate-700">Notificaciones</span>
            {unread > 0 && (
              <button
                type="button"
                onClick={() => void handleMarkAllRead()}
                className="text-xs font-medium text-brand-700 hover:text-brand-800"
              >
                Marcar todo como leído
              </button>
            )}
          </div>

          <div className="max-h-96 overflow-y-auto">
            {notifications.length === 0 && (
              <p className="px-4 py-6 text-center text-sm text-slate-500">No tienes avisos todavía.</p>
            )}

            {notifications.map((notification) => (
              <button
                key={notification.notificationId}
                type="button"
                onClick={() => void handleOpenNotification(notification)}
                className={`flex w-full gap-2.5 border-b border-sand-100 px-4 py-3 text-left transition last:border-0 hover:bg-sand-50 ${
                  notification.unread ? 'bg-brand-50/40' : ''
                }`}
              >
                <span aria-hidden className="text-base leading-5">
                  {icons[notification.type]}
                </span>
                <span className="flex-1">
                  <span className="block text-sm font-medium text-slate-800">{notification.title}</span>
                  {notification.body && (
                    <span className="mt-0.5 block text-xs text-slate-600">{notification.body}</span>
                  )}
                  <span className="mt-1 block text-[11px] text-slate-400">
                    {relativeTime(notification.createdAt)}
                  </span>
                </span>
                {notification.unread && (
                  <span aria-hidden className="mt-1.5 size-2 shrink-0 rounded-full bg-brand-600" />
                )}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
