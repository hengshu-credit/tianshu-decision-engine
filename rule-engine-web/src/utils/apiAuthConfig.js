export function emptyApiAuthConfig() {
  return {
    username: null, password: null, token: null, value: null, name: 'X-API-Key', location: 'HEADER',
    tokenUrl: '/oauth/token', method: 'POST', contentType: 'application/json',
    headers: {}, body: { grant_type: { kind: 'LITERAL', valueType: 'STRING', value: 'client_credentials' } },
    tokenPath: 'body.access_token', expiresInPath: 'body.expires_in', tokenPlacement: 'HEADER',
    tokenHeaderName: 'Authorization', tokenPrefix: '', tokenResponseScript: '', nullPolicy: 'FAIL',
  }
}

export function readApiAuthConfig(text) {
  const parsed = text ? JSON.parse(text) : {}
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('鉴权配置必须是对象')
  return { ...emptyApiAuthConfig(), ...parsed }
}

export function apiAuthConfig(type, config) {
  const fields = {
    BASIC: ['username', 'password'], BEARER: ['token'], API_KEY: ['name', 'value', 'location'],
    TOKEN_API: ['tokenUrl', 'method', 'contentType', 'headers', 'body', 'tokenPath', 'expiresInPath', 'tokenPlacement', 'tokenHeaderName', 'tokenPrefix', 'tokenResponseScript', 'responseSample'],
  }
  const names = fields[type === 'OAUTH2' ? 'TOKEN_API' : type]
  if (!names) return {}
  return Object.fromEntries([...names, 'nullPolicy'].filter(name => config[name] !== undefined).map(name => [name, config[name]]))
}
