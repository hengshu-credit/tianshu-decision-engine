export const API_REQUEST_POSITIONS = [
  { value: 'HEADER', label: 'Headers', hint: '请求头，例如 Authorization。' },
  { value: 'QUERY', label: 'Params', hint: 'Query 参数，拼接在请求地址后面，例如 ?page=1。' },
  { value: 'COOKIE', label: 'Cookies', hint: '请求 Cookies，例如 session_id。' },
  { value: 'BODY', label: 'Body', hint: '请求体，支持 JSON、x-www-form-urlencoded 和 form-data。' },
]

export const API_BODY_FORMATS = [
  { value: 'JSON', label: 'JSON', hint: '结构化数据（application/json），支持对象和列表。' },
  { value: 'FORM', label: 'x-www-form-urlencoded', hint: 'application/x-www-form-urlencoded：键值对经 URL 编码后放入请求体。' },
  { value: 'FORM_DATA', label: 'form-data', hint: 'multipart/form-data：使用 boundary 分隔各个表单项。' },
]

export function requestFieldPosition(field) {
  if (API_BODY_FORMATS.some(format => format.value === field.location)) return API_REQUEST_POSITIONS.find(position => position.value === 'BODY')
  return API_REQUEST_POSITIONS.find(position => position.value === field.location) || (field.location === 'INPUT'
    ? { value: 'INPUT', label: '调用入参', hint: '先覆盖本次调用的输入，再由 API 的请求映射放入相应位置。' }
    : { value: field.location, label: '未设置', hint: '请选择字段发送的位置。' })
}

export function requestFieldFormat(field) {
  return API_BODY_FORMATS.find(format => format.value === field.location) || (field.location === 'INPUT'
    ? { label: '由 API 映射', hint: '实际发送位置和格式由 API 请求映射决定。' }
    : { label: '键值对', hint: field.location === 'HEADER' ? '请求头名称及对应取值。' : field.location === 'COOKIE' ? 'Cookie 名称及对应取值。' : '请求参数名称及对应取值。' })
}

export function requestFieldContent(field) {
  const kind = ({ PDF: 'PDF 文件', IMAGE: '图片', ANY: '文件' })[field.file?.kind] || '文件'
  if (field.file?.mode === 'BASE64') return { label: '文件转 Base64', hint: `${kind}：取值应为文件地址，调用时下载并转换为 Base64 文本。` }
  if (field.file?.mode === 'ZIP_BASE64') return { label: '压缩后转 Base64', hint: `${kind}：取值应为文件地址，调用时打包 ZIP，再转换为 Base64 文本。` }
  return { label: '普通字段', hint: '直接使用配置的取值，不执行文件转换。' }
}
