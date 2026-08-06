// `defineConfig` se toma de vitest y no de vite: es el mismo tipo ampliado con
// la clave `test`, de modo que la configuración del proyecto y la de sus
// pruebas viven juntas en vez de en dos ficheros que hay que mantener a la par.
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // Los tests viven junto al código que prueban, no en un árbol paralelo:
    // así un fichero que se mueve arrastra su prueba en el mismo commit.
    include: ['src/**/*.test.{ts,tsx}'],
  },
  server: {
    port: 3000,
    host: true,
    // Fase 1-3: el backend era un monolito en :8081. Desde la Fase 4 el backend
    // son 4 microservicios detras de un API Gateway en :8080: React solo conoce
    // el Gateway, nunca los servicios individuales.
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
