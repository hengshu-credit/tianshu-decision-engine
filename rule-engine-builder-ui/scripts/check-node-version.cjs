const major = Number(process.versions.node.split('.')[0])

if (major < 14 || major >= 23) {
  console.error(
    `Unsupported Node.js ${process.version}. Use Node.js 18-22 for this Vue 3 project; newer releases are not part of the verified toolchain.`
  )
  process.exit(1)
}
