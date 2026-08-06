import { useEffect, useRef } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

/**
 * Mapa con un marcador arrastrable para ajustar el domicilio a mano.
 *
 * <p>La búsqueda de direcciones (Nominatim) no siempre acierta: depende de cómo
 * esté escrita la dirección, y su cobertura de números de portal es desigual en
 * España (ver `AddressPicker`). Cuando el texto no basta, esta es la vía de
 * escape — arrastrar el pin sobre el mapa hasta la puerta real — en vez de
 * dejar al cuidador atascado si la búsqueda no encuentra su domicilio exacto.
 *
 * <p>Usa Leaflet con las teselas públicas de OpenStreetMap: sin clave de API ni
 * cuenta de pago, igual que el geocodificador. Es el mismo criterio que ya
 * aplicamos con Nominatim frente a Google Maps — nadie debería necesitar dar
 * una tarjeta de crédito para completar su perfil de cuidador.
 *
 * <p>El marcador es un <code>divIcon</code> (SVG inline) y no el icono PNG por
 * defecto de Leaflet a propósito: ese PNG se referencia por una ruta relativa a
 * `node_modules` que los bundlers (Vite incluido) no resuelven solos, y es un
 * error clásico y confuso de integrar Leaflet con Vite/webpack. Un icono propio
 * evita el problema de raíz.
 */
export function LocationPinMap({
  latitude,
  longitude,
  onDragEnd,
}: {
  latitude: number
  longitude: number
  onDragEnd: (location: { latitude: number; longitude: number }) => void
}) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<L.Map | null>(null)
  const markerRef = useRef<L.Marker | null>(null)
  // El callback cambia en cada render (se recrea en el padre); guardarlo en un
  // ref evita reconstruir el mapa entero solo porque `onDragEnd` es una
  // referencia de función distinta cada vez.
  const onDragEndRef = useRef(onDragEnd)
  onDragEndRef.current = onDragEnd

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return

    const pinIcon = L.divIcon({
      html: '<span style="font-size: 32px; line-height: 1;">📍</span>',
      className: '',
      iconSize: [32, 32],
      // La punta del emoji está abajo a la izquierda del glifo, no en el
      // centro del cuadro: se ajusta el ancla para que señale el punto real en
      // vez del centro del icono.
      iconAnchor: [8, 30],
    })

    const map = L.map(containerRef.current, {
      center: [latitude, longitude],
      zoom: 17,
      scrollWheelZoom: false,
    })

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      maxZoom: 19,
    }).addTo(map)

    const marker = L.marker([latitude, longitude], { draggable: true, icon: pinIcon }).addTo(map)
    marker.on('dragend', () => {
      const position = marker.getLatLng()
      onDragEndRef.current({ latitude: position.lat, longitude: position.lng })
    })

    mapRef.current = map
    markerRef.current = marker

    return () => {
      map.remove()
      mapRef.current = null
      markerRef.current = null
    }
    // Solo al montar: recentrar en cada cambio de lat/lng reintroduciría el
    // salto que se quiere evitar mientras se arrastra (ver el efecto de abajo,
    // que sí sincroniza sin reconstruir el mapa).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Recoloca el marcador si la posición cambia por una vía DISTINTA al propio
  // arrastre — por ejemplo, al elegir otra sugerencia del buscador de texto —
  // sin recrear el mapa ni perder el nivel de zoom que el cuidador haya puesto.
  useEffect(() => {
    if (!markerRef.current || !mapRef.current) return
    const current = markerRef.current.getLatLng()
    if (Math.abs(current.lat - latitude) < 1e-9 && Math.abs(current.lng - longitude) < 1e-9) {
      return
    }
    markerRef.current.setLatLng([latitude, longitude])
    mapRef.current.panTo([latitude, longitude])
  }, [latitude, longitude])

  return (
    <div className="flex flex-col gap-1.5">
      <div
        ref={containerRef}
        className="h-64 w-full overflow-hidden rounded-lg border border-sand-200"
        role="application"
        aria-label="Mapa para ajustar la ubicación exacta arrastrando el marcador"
      />
      <p className="text-xs text-slate-500">
        Arrastra el marcador hasta la puerta exacta. Solo ajusta el punto en el mapa: la dirección
        escrita arriba no cambia.
      </p>
    </div>
  )
}
