const catalogue = require('../../../src/constants/platformResponseCodes.json')
const codes = Object.fromEntries(catalogue.map(item => [item.name, item]))

function platformResponse(data, name = 'SUCCESS', detail = null) {
  const status = codes[name]
  return { code: status.code, message: status.message, success: status.code === codes.SUCCESS.code, detail, data }
}

module.exports = { platformResponse }
