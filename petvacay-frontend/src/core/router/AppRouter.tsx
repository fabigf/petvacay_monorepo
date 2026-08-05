import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from '@/shared/components/AppLayout'
import { ProtectedRoute } from '@/core/router/ProtectedRoute'
import { HomePage } from '@/modules/home/HomePage'
import { LoginPage } from '@/modules/auth/LoginPage'
import { RegisterPage } from '@/modules/auth/RegisterPage'
import { SearchPage } from '@/modules/search/SearchPage'
import { DashboardPage } from '@/modules/bookings/DashboardPage'
import { NewBookingPage } from '@/modules/bookings/NewBookingPage'
import { BookingDetailPage } from '@/modules/bookings/BookingDetailPage'
import { SitterOnboardingPage } from '@/modules/onboarding/SitterOnboardingPage'
import { PetsPage } from '@/modules/pets/PetsPage'
import { AdminPage } from '@/modules/admin/AdminPage'

export function AppRouter() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          {/* Rutas publicas */}
          <Route path="/" element={<HomePage />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* Rutas protegidas */}
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <DashboardPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/pets"
            element={
              <ProtectedRoute>
                <PetsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/bookings/new/:sitterId"
            element={
              <ProtectedRoute>
                <NewBookingPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/bookings/:bookingId"
            element={
              <ProtectedRoute>
                <BookingDetailPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="/dashboard/sitter"
            element={
              <ProtectedRoute roles={['ROLE_SITTER']}>
                <SitterOnboardingPage />
              </ProtectedRoute>
            }
          />

          {/* Back-office. El rol se comprueba también en cada endpoint del
              backend: esto solo evita enseñar una pantalla que no funcionaría. */}
          <Route
            path="/admin"
            element={
              <ProtectedRoute roles={['ROLE_ADMIN']}>
                <AdminPage />
              </ProtectedRoute>
            }
          />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
