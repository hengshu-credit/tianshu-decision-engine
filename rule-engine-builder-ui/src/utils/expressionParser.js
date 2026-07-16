import {
  OPERATION_OPERATORS,
  createAccessOperand,
  createArrayOperand,
  createCastOperand,
  createFunctionOperand,
  createListQueryOperand,
  createLiteralOperand,
  createOperationOperand,
  createPathOperand,
  createReferenceOperand
} from '@/utils/operand'

export class ExpressionParseError extends Error {
  constructor(message, source, index) {
    super(message)
    this.name = 'ExpressionParseError'
    this.index = Math.max(0, Number(index) || 0)
    const before = String(source || '').substring(0, this.index)
    this.line = before.split('\n').length
    this.column = this.index - before.lastIndexOf('\n')
  }
}

const CAST_FUNCTIONS = Object.freeze({
  toNumberValue: 'NUMBER',
  toBooleanValue: 'BOOLEAN',
  toListValue: 'LIST',
  toMapValue: 'MAP',
  toStringValue: 'STRING'
})

function isIdentifierStart(char) {
  return !!char && /[A-Za-z_$\u00c0-\uffff]/.test(char)
}

function isIdentifierPart(char) {
  return !!char && /[A-Za-z0-9_$\u00c0-\uffff]/.test(char)
}

function readString(source, start) {
  const quote = source[start]
  let value = ''
  let index = start + 1
  while (index < source.length) {
    const char = source[index]
    if (char === quote) return { token: { type: 'STRING', value, index: start }, next: index + 1 }
    if (char === '\\') {
      index += 1
      if (index >= source.length) break
      const escaped = source[index]
      value += { n: '\n', r: '\r', t: '\t' }[escaped] || escaped
      index += 1
      continue
    }
    value += char
    index += 1
  }
  throw new ExpressionParseError('字符串未结束', source, start)
}

function tokenize(source) {
  const tokens = []
  let index = 0
  const operators = ['&&', '||', '==', '!=', '>=', '<=', '+', '-', '*', '/', '%', '>', '<']
  while (index < source.length) {
    const char = source[index]
    if (/\s/.test(char)) {
      index += 1
      continue
    }
    if (char === '"' || char === "'") {
      const result = readString(source, index)
      tokens.push(result.token)
      index = result.next
      continue
    }
    if (/\d/.test(char)) {
      const match = source.substring(index).match(/^\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/)
      tokens.push({ type: 'NUMBER', value: match[0], index })
      index += match[0].length
      continue
    }
    if (isIdentifierStart(char)) {
      const start = index
      index += 1
      while (index < source.length) {
        if (isIdentifierPart(source[index])) {
          index += 1
          continue
        }
        if (source[index] === '.' && isIdentifierStart(source[index + 1])) {
          index += 2
          while (isIdentifierPart(source[index])) index += 1
          continue
        }
        break
      }
      const value = source.substring(start, index)
      tokens.push({ type: value === 'true' || value === 'false' ? 'BOOLEAN' : 'IDENTIFIER', value, index: start })
      continue
    }
    const operator = operators.find(value => source.substring(index, index + value.length) === value)
    if (operator) {
      tokens.push({ type: 'OPERATOR', value: operator, index })
      index += operator.length
      continue
    }
    if ('()[],'.includes(char)) {
      tokens.push({ type: char, value: char, index })
      index += 1
      continue
    }
    if (char === '=') {
      tokens.push({ type: 'ASSIGN', value: char, index })
      index += 1
      continue
    }
    if (char === ';') {
      tokens.push({ type: 'SEMICOLON', value: char, index })
      index += 1
      continue
    }
    throw new ExpressionParseError('无法识别的字符 ' + char, source, index)
  }
  tokens.push({ type: 'EOF', value: '', index: source.length })
  return tokens
}

function optionCode(option) {
  return option && (option.varCode || option.refCode || option.code || (option._ref && option._ref.refCode))
}

function literalValue(operand) {
  if (!operand || operand.kind !== 'LITERAL') return undefined
  if (operand.valueType === 'NUMBER') return Number(operand.value)
  if (operand.valueType === 'BOOLEAN') return operand.value === 'true'
  return operand.value
}

class Parser {
  constructor(source, options) {
    this.source = source
    this.tokens = tokenize(source)
    this.position = 0
    this.vars = (options && options.vars) || []
    this.functions = (options && options.functions) || []
  }

  current() { return this.tokens[this.position] }

  consume(type) {
    const token = this.current()
    if (token.type !== type) this.fail(type === ')' ? '缺少右括号' : '缺少表达式', token)
    this.position += 1
    return token
  }

  fail(message, token = this.current()) {
    throw new ExpressionParseError(message, this.source, token.index)
  }

  parse() {
    const operand = this.parseExpression()
    const token = this.current()
    if (token.type === 'ASSIGN') this.fail('不支持赋值', token)
    if (token.type === 'SEMICOLON') this.fail('不支持分号', token)
    if (token.type !== 'EOF') this.fail('存在未解析内容', token)
    return operand
  }

  parseExpression() {
    const first = this.parsePrimary()
    const terms = [{ operand: first }]
    while (this.current().type === 'OPERATOR') {
      const operator = this.current()
      if (!OPERATION_OPERATORS.includes(operator.value)) this.fail('不支持的运算符 ' + operator.value, operator)
      this.position += 1
      terms.push({ operator: operator.value, operand: this.parsePrimary() })
    }
    return terms.length === 1 ? first : createOperationOperand(terms)
  }

  parsePrimary() {
    const token = this.current()
    if (token.type === 'OPERATOR' && token.value === '-' && this.tokens[this.position + 1].type === 'NUMBER') {
      this.position += 2
      return createLiteralOperand('-' + this.tokens[this.position - 1].value, 'NUMBER')
    }
    if (token.type === 'NUMBER') {
      this.position += 1
      return createLiteralOperand(token.value, 'NUMBER')
    }
    if (token.type === 'STRING') {
      this.position += 1
      return createLiteralOperand(token.value, 'STRING')
    }
    if (token.type === 'BOOLEAN') {
      this.position += 1
      return createLiteralOperand(token.value, 'BOOLEAN')
    }
    if (token.type === '(') {
      this.position += 1
      const operand = this.parseExpression()
      this.consume(')')
      return operand
    }
    if (token.type === '[') return this.parseArray()
    if (token.type === 'IDENTIFIER') {
      this.position += 1
      if (this.current().type === '(') return this.parseFunction(token)
      return this.parseReference(token)
    }
    if (token.type === 'ASSIGN') this.fail('不支持赋值', token)
    if (token.type === 'SEMICOLON') this.fail('不支持分号', token)
    this.fail('缺少表达式', token)
  }

  parseArray() {
    this.consume('[')
    const items = []
    if (this.current().type !== ']') {
      let hasNext = true
      while (hasNext) {
        items.push(this.parseExpression())
        hasNext = this.current().type === ','
        if (hasNext) this.position += 1
      }
    }
    this.consume(']')
    return createArrayOperand(items)
  }

  parseFunction(nameToken) {
    this.consume('(')
    const args = []
    if (this.current().type !== ')') {
      let hasNext = true
      while (hasNext) {
        args.push(this.parseExpression())
        hasNext = this.current().type === ','
        if (hasNext) this.position += 1
      }
    }
    this.consume(')')
    const code = nameToken.value
    if (code === 'objGet' || code === 'arrGet') {
      if (args.length !== 2) this.fail(code + ' 需要 2 个参数', nameToken)
      return createAccessOperand(args[0], code === 'arrGet' ? 'INDEX' : 'KEY', args[1])
    }
    if (CAST_FUNCTIONS[code]) {
      if (args.length !== 1) this.fail(code + ' 需要 1 个参数', nameToken)
      return createCastOperand(CAST_FUNCTIONS[code], args[0])
    }
    if (code === 'listQuery') return this.parseListQuery(args, nameToken)

    const matches = this.functions.filter(fn => (fn.functionCode || fn.funcCode || fn.functionName || fn.name) === code)
    if (!matches.length) this.fail('未知函数 ' + code, nameToken)
    if (matches.length > 1) this.fail('函数编码存在歧义 ' + code, nameToken)
    return createFunctionOperand(matches[0], args)
  }

  parseListQuery(args, token) {
    if (args.length !== 4 || args[0].kind !== 'ARRAY' || args[1].kind !== 'ARRAY') this.fail('listQuery 参数格式不正确', token)
    const listIds = args[0].items.map(literalValue)
    const itemTypes = args[1].items.map(literalValue)
    const combinationMode = literalValue(args[2])
    const matchMode = literalValue(args[3])
    if (listIds.some(value => value === undefined) || itemTypes.some(value => value === undefined) || combinationMode === undefined || matchMode === undefined) {
      this.fail('listQuery 参数必须是字面量', token)
    }
    return createListQueryOperand({ listIds, itemTypes, combinationMode, matchMode })
  }

  parseReference(token) {
    const matches = this.vars.filter(option => optionCode(option) === token.value)
    if (matches.length > 1) this.fail('字段编码存在歧义 ' + token.value, token)
    if (matches.length === 1) return createReferenceOperand(matches[0])
    return createPathOperand(token.value)
  }
}

export function parseExpressionScript(script, options = {}) {
  const source = String(script == null ? '' : script)
  return new Parser(source, options).parse()
}
