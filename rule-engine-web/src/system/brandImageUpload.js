export const BRAND_IMAGE_ACCEPT = '.jpg,.jpeg,.png,.svg,.gif,.webp,.bmp,.ico,image/jpeg,image/png,image/svg+xml,image/gif,image/webp,image/bmp,image/x-icon'

// Decode in an image context (never inline SVG) and upload pixels through the existing PNG endpoint.
export async function prepareBrandImage(file) {
  if (!file || file.size > 1024 * 1024 || !/\.(jpe?g|png|svg|gif|webp|bmp|ico)$/i.test(file.name)) {
    throw new Error('请选择 1 MB 以内的 JPG、PNG、SVG、GIF、WebP、BMP 或 ICO 图片')
  }
  const url = URL.createObjectURL(file)
  try {
    const img = new Image()
    img.src = url
    await img.decode()
    if (!img.naturalWidth || !img.naturalHeight || img.naturalWidth > 2048 || img.naturalHeight > 2048) {
      throw new Error('图片宽高须在 1–2048 像素之间')
    }
    if (/\.(jpe?g|png)$/i.test(file.name)) return file
    const canvas = document.createElement('canvas')
    canvas.width = img.naturalWidth
    canvas.height = img.naturalHeight
    canvas.getContext('2d').drawImage(img, 0, 0)
    const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/png'))
    if (!blob || blob.size > 1024 * 1024) throw new Error('图片转换后超过 1 MB，请缩小图片后重试')
    return new File([blob], file.name.replace(/\.[^.]+$/, '.png'), { type: 'image/png' })
  } catch (error) {
    if (error instanceof DOMException) throw new Error('图片无法读取，请检查图片文件是否完整')
    throw error
  } finally { URL.revokeObjectURL(url) }
}
