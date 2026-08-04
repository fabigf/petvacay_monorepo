import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import { AppRouter } from '@/core/router/AppRouter'
import { SessionGate } from '@/core/router/SessionGate'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <SessionGate>
      <AppRouter />
    </SessionGate>
  </StrictMode>,
)
