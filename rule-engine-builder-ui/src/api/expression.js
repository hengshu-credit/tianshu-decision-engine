import request from './request'

export function compileExpression(data) {
  return request({ url: '/rule/expression/compile', method: 'post', data })
}

export function getExpressionTestSchema(data) {
  return request({ url: '/rule/expression/schema', method: 'post', data })
}

export function executeExpression(data) {
  return request({ url: '/rule/expression/test', method: 'post', data })
}
