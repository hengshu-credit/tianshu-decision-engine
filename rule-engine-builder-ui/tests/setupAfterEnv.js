const VueModule = require('vue')
const VueConstructor = VueModule.default || VueModule
const { installConsoleGate } = require('./consoleGate')

installConsoleGate(VueConstructor)
