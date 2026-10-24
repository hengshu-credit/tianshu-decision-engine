import { PLATFORM_RESPONSE_CODES } from '../platformResponse'

export function responseCodes(mapping) {
  return PLATFORM_RESPONSE_CODES.map(row => {
    const override = mapping?.enabled ? mapping.mappings?.[row.code] : null
    return { ...row, systemCode: row.code, code: override?.code || row.code, message: override?.message || row.message }
  })
}
