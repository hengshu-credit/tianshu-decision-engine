function formatConsoleArg(value) {
  if (typeof value === 'string') return value
  if (value instanceof Error) return value.stack || value.message
  try {
    return JSON.stringify(value)
  } catch (e) {
    return String(value)
  }
}

function messageMatches(message, expected) {
  if (expected instanceof RegExp) {
    expected.lastIndex = 0
    return expected.test(message)
  }
  return message.indexOf(String(expected)) >= 0
}

function assertConsoleMessages(messages, expectedPatterns) {
  const remaining = (expectedPatterns || []).slice()
  const unexpected = []

  ;(messages || []).forEach(entry => {
    const index = remaining.findIndex(pattern => messageMatches(entry.message, pattern))
    if (index >= 0) {
      remaining.splice(index, 1)
      return
    }
    unexpected.push(entry)
  })

  if (!unexpected.length && !remaining.length) return

  const details = []
  if (unexpected.length) {
    details.push('检测到未声明的控制台输出：\n' + unexpected.map(entry => (
      '[' + entry.source + '] ' + entry.message
    )).join('\n'))
  }
  if (remaining.length) {
    details.push('以下预期控制台输出未发生：\n' + remaining.map(String).join('\n'))
  }
  throw new Error(details.join('\n'))
}

function installConsoleGate(VueConstructor) {
  let messages = []
  let expectedPatterns = []
  let originalConsoleError
  let originalWarnHandler

  global.expectConsoleError = pattern => {
    expectedPatterns.push(pattern)
  }

  beforeEach(() => {
    messages = []
    expectedPatterns = []
    originalConsoleError = console.error
    originalWarnHandler = VueConstructor.config.warnHandler
    console.error = jest.fn((...args) => {
      messages.push({
        source: 'console.error',
        message: args.map(formatConsoleArg).join(' ')
      })
    })
    VueConstructor.config.warnHandler = (message, vm, trace) => {
      messages.push({
        source: 'Vue warning',
        message: message + (trace || '')
      })
    }
  })

  afterEach(() => {
    console.error = originalConsoleError
    VueConstructor.config.warnHandler = originalWarnHandler
    assertConsoleMessages(messages, expectedPatterns)
  })
}

module.exports = {
  assertConsoleMessages,
  installConsoleGate
}
