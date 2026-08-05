import apiClient from '@/core/api/apiClient'
import type { Notification } from '@/core/api/types'

/**
 * Buzón de avisos del usuario autenticado. El backend decide qué entra según el
 * rol del token: un administrador recibe además los avisos del equipo.
 */
export async function listNotifications(): Promise<Notification[]> {
  const { data } = await apiClient.get<Notification[]>('/notifications')
  return data
}

export async function markNotificationRead(notificationId: string): Promise<Notification> {
  const { data } = await apiClient.post<Notification>(`/notifications/${notificationId}/read`)
  return data
}

export async function markAllNotificationsRead(): Promise<number> {
  const { data } = await apiClient.post<{ updated: number }>('/notifications/read-all')
  return data.updated
}
