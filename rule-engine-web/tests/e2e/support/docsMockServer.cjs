const http = require('node:http')
const { createDocsApiData } = require('./docsFixtures.cjs')

const fixtures = createDocsApiData()
const port = Number(process.env.DOCS_MOCK_PORT || 8080)

const server = http.createServer(async (request, response) => {
  const url = new URL(request.url, `http://127.0.0.1:${port}`)
  const methodKey = `${request.method.toUpperCase()} ${url.pathname}`
  let data = fixtures.has(methodKey)
    ? fixtures.get(methodKey)
    : fixtures.get(url.pathname)

  try {
    if (typeof data === 'function') data = await data({ url, request })
    if (data === undefined) data = { records: [], total: 0 }
    response.writeHead(200, {
      'Content-Type': 'application/json; charset=utf-8',
    })
    response.end(JSON.stringify({ code: 200, message: 'success', data }))
  } catch (error) {
    response.writeHead(500, {
      'Content-Type': 'application/json; charset=utf-8',
    })
    response.end(
      JSON.stringify({
        code: 500,
        message: error instanceof Error ? error.message : String(error),
        data: null,
      })
    )
  }
})

server.listen(port, '127.0.0.1', () => {
  console.log(`Docs mock API listening on http://127.0.0.1:${port}`)
})
