/**
 * Shrinks an image in the browser before uploading it (the longest side to maxSide px), so uploads stay light.
 * PNG keeps transparency (logos); JPEG is smaller for photos (banners).
 */
export async function shrinkImage(file: File, maxSide: number, type: 'image/png' | 'image/jpeg'): Promise<Blob> {
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(bitmap.width * scale)
  canvas.height = Math.round(bitmap.height * scale)
  canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  return new Promise((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error(type))), type, type === 'image/jpeg' ? 0.85 : undefined))
}
