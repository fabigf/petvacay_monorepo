import { BrowserRouter, Route, Routes } from 'react-router-dom'
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
import { TermsPage } from '@/modules/legal/TermsPage'
import { PrivacyPage } from '@/modules/legal/PrivacyPage'
import { CookiesPage } from '@/modules/legal/CookiesPage'
import { FaqPage } from '@/modules/legal/FaqPage'
import { HowItWorksPage } from '@/modules/legal/HowItWorksPage'
import { ContactPage } from '@/modules/legal/ContactPage'
import { NotFoundPage } from '@/modules/legal/NotFoundPage'

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

          {/* Informativas y legales. Rutas en español porque son las que se
              comparten y aparecen en buscadores. */}
          <Route path="/como-funciona" element={<HowItWorksPage />} />
          <Route path="/faq" element={<FaqPage />} />
          <Route path="/terminos" element={<TermsPage />} />
          <Route path="/privacidad" element={<PrivacyPage />} />
          <Route path="/cookies" element={<CookiesPage />} />
          <Route path="/contacto" element={<ContactPage />} />

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

          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
