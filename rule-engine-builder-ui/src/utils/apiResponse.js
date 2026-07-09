export function unwrapApiData(res) {
  return res && res.data ? res.data : res
}

export function isSuccessResult(res) {
  const data = unwrapApiData(res)
  return !!(data && data.success)
}

export function resultErrorMessage(res, fallback = '未知错误') {
  const data = unwrapApiData(res)
  return (data && data.errorMessage) || fallback
}
