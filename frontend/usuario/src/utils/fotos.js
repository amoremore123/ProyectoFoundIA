// H52: una foto de la cámara del celular suele pesar 3–8 MB y medir 4000 px o más.
// Antes de validar y subir, se reduce en el navegador a JPEG de máximo 1920 px por lado.
// Si el navegador no puede decodificarla, se devuelve el archivo original y se aplican
// las validaciones de siempre (tipo y 5 MiB).
export const LADO_MAXIMO = 1920;
const CALIDAD = 0.85;
const UMBRAL_BYTES = 1.5 * 1024 * 1024; // las fotos livianas y chicas no se tocan

export async function reducirFoto(archivo) {
  if (!archivo || !archivo.type?.startsWith('image/')) return archivo;
  if (typeof createImageBitmap !== 'function' || typeof document === 'undefined') return archivo;
  let imagen;
  try {
    // from-image respeta la orientación EXIF (fotos verticales del celular)
    imagen = await createImageBitmap(archivo, { imageOrientation: 'from-image' });
  } catch {
    return archivo;
  }
  try {
    const escala = Math.min(1, LADO_MAXIMO / Math.max(imagen.width, imagen.height));
    const yaSirve = escala === 1 && archivo.size <= UMBRAL_BYTES && ['image/jpeg', 'image/png'].includes(archivo.type);
    if (yaSirve) return archivo;
    const lienzo = document.createElement('canvas');
    lienzo.width = Math.round(imagen.width * escala);
    lienzo.height = Math.round(imagen.height * escala);
    const ctx = lienzo.getContext('2d');
    if (!ctx) return archivo;
    ctx.drawImage(imagen, 0, 0, lienzo.width, lienzo.height);
    const blob = await new Promise((resolve) => lienzo.toBlob(resolve, 'image/jpeg', CALIDAD));
    if (!blob) return archivo;
    const nombre = `${(archivo.name || 'foto').replace(/\.[^.]+$/, '')}.jpg`;
    return new File([blob], nombre, { type: 'image/jpeg', lastModified: Date.now() });
  } catch {
    return archivo;
  } finally {
    imagen.close?.();
  }
}
