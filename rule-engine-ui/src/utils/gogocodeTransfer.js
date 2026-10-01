export function $emit(instance, event, ...args) {
  instance && instance.$emit && instance.$emit(event, ...args)
  if (event === 'update:value' && instance && instance.$emit) {
    instance.$emit('input', ...args)
  }
  return instance
}
export function plantRenderPara(params) {
  const transProps = {
    staticClass: 'class',
    staticStyle: 'style',
    on: '',
    domProps: '',
    props: '',
    attrs: '',
  }
  function obj2arr(obj) {
    return typeof obj == 'string'
      ? [obj]
      : Object.keys(obj).map((pk, index) => {
          return { [pk]: Object.values(obj)[index] }
        })
  }
  let result = {}
  for (let key in params) {
    if (transProps[key] == null) {
      if (typeof params[key] == 'object') {
        result[key] = obj2arr(params[key])
      } else {
        result[key] = params[key]
      }
    }
  }
  for (let key in params) {
    if (transProps[key] === '') {
      if (typeof params[key] == 'object') {
        for (let k in params[key]) {
          result[k] = params[key][k]
        }
      } else {
        result[key] = params[key]
      }
    }
  }
  for (let key in params) {
    if (transProps[key]) {
      result[transProps[key]] = result[transProps[key]] || []
      result[transProps[key]] = result[transProps[key]].concat(
        obj2arr(params[key])
      )
    }
  }
  return result
}
