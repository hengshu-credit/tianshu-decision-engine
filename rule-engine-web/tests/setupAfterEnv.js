const { config } = require('@vue/test-utils')
const { installConsoleGate } = require('./consoleGate')

installConsoleGate(config)
