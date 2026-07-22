const [major, minor] = process.versions.node.split('.').map(Number)

if (major < 20 || (major === 20 && minor < 19)) {
  console.error(
    `Unsupported Node.js ${process.version}. Vite 8 requires Node.js 20.19 or newer.`
  )
  process.exit(1)
}
