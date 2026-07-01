const major = Number(process.versions.node.split('.')[0])

if (major < 14 || major >= 23) {
  console.error(
    `Unsupported Node.js ${process.version}. Use Node.js 14-22 for this Vue 2 project; Node.js 26 fails while building transitive native dependencies on Windows.`
  )
  process.exit(1)
}
