export const EXAMPLE_CREDENTIALS = {
  LEGACY_TOKEN: '<PROJECT_TOKEN>',
  BASIC_USERNAME: '<USERNAME>',
  BASIC_PASSWORD: '<PASSWORD>',
  API_KEY: '<API_KEY>',
  ACCESS_KEY: '<ACCESS_KEY>',
  HMAC_SECRET: '<HMAC_SECRET>',
  BEARER_TOKEN: '<ACCESS_TOKEN>'
}

function endpointValues(endpoint, auth) {
  const method = String((endpoint && endpoint.method) || 'POST').toUpperCase()
  const baseUrl = String((endpoint && endpoint.baseUrl) || 'https://api.example.com').replace(/\/$/, '')
  const path = String((endpoint && endpoint.path) || '/')
  const body = typeof (endpoint && endpoint.body) === 'string'
    ? endpoint.body
    : JSON.stringify((endpoint && endpoint.body) || {})
  const queryName = auth && auth.authType === 'API_KEY' && auth.placement === 'QUERY'
    ? String(auth.parameterName || 'api_key')
    : ''
  const query = queryName ? `${encodeURIComponent(queryName)}=${encodeURIComponent(EXAMPLE_CREDENTIALS.API_KEY)}` : ''
  return {
    method,
    baseUrl,
    path,
    body,
    query,
    url: `${baseUrl}${path}${query ? `?${query}` : ''}`
  }
}

function headerValues(auth) {
  const type = String((auth && auth.authType) || 'LEGACY_TOKEN')
  if (type === 'LEGACY_TOKEN') return [{ name: 'X-Rule-Token', value: EXAMPLE_CREDENTIALS.LEGACY_TOKEN }]
  if (type === 'BEARER_TOKEN') return [{ name: 'Authorization', value: `Bearer ${EXAMPLE_CREDENTIALS.BEARER_TOKEN}` }]
  if (type === 'API_KEY' && auth.placement !== 'QUERY') {
    return [{ name: String(auth.parameterName || 'X-Rule-Api-Key'), value: EXAMPLE_CREDENTIALS.API_KEY }]
  }
  return []
}

function shellQuote(value) {
  return `'${String(value).replace(/'/g, `'"'"'`)}'`
}

function shellSample(endpoint, auth) {
  const value = endpointValues(endpoint, auth)
  const type = String((auth && auth.authType) || 'LEGACY_TOKEN')
  if (type === 'HMAC_SHA256') {
    return `BODY=${shellQuote(value.body)}
TIMESTAMP=$(date +%s)
NONCE=$(openssl rand -hex 16)
BODY_HASH=$(printf '%s' "$BODY" | openssl dgst -sha256 -hex | awk '{print $2}')
CANONICAL=$(printf '${value.method}\\n${value.path}\\n${value.query}\\n%s\\n%s\\n%s' "$BODY_HASH" "$TIMESTAMP" "$NONCE")
SIGNATURE=$(printf '%s' "$CANONICAL" | openssl dgst -sha256 -hmac '${EXAMPLE_CREDENTIALS.HMAC_SECRET}' -hex | awk '{print $2}')

curl -X ${value.method} ${shellQuote(value.url)} \\
  -H 'Content-Type: application/json' \\
  -H 'X-Rule-Access-Key: ${EXAMPLE_CREDENTIALS.ACCESS_KEY}' \\
  -H "X-Rule-Timestamp: $TIMESTAMP" \\
  -H "X-Rule-Nonce: $NONCE" \\
  -H "X-Rule-Signature: $SIGNATURE" \\
  --data-binary "$BODY"`
  }
  const headers = headerValues(auth).map(item => `  -H ${shellQuote(`${item.name}: ${item.value}`)} \\\n`).join('')
  const basic = type === 'BASIC'
    ? `  -u ${shellQuote(`${EXAMPLE_CREDENTIALS.BASIC_USERNAME}:${EXAMPLE_CREDENTIALS.BASIC_PASSWORD}`)} \\\n`
    : ''
  return `curl -X ${value.method} ${shellQuote(value.url)} \\
  -H 'Content-Type: application/json' \\
${headers}${basic}  --data-binary ${shellQuote(value.body)}`
}

function javaHeaderLines(auth) {
  const type = String((auth && auth.authType) || 'LEGACY_TOKEN')
  if (type === 'BASIC') {
    return `String basic = Base64.getEncoder().encodeToString("${EXAMPLE_CREDENTIALS.BASIC_USERNAME}:${EXAMPLE_CREDENTIALS.BASIC_PASSWORD}".getBytes(StandardCharsets.UTF_8));
connection.setRequestProperty("Authorization", "Basic " + basic);`
  }
  return headerValues(auth).map(item => `connection.setRequestProperty(${JSON.stringify(item.name)}, ${JSON.stringify(item.value)});`).join('\n')
}

function javaSample(endpoint, auth) {
  const value = endpointValues(endpoint, auth)
  const type = String((auth && auth.authType) || 'LEGACY_TOKEN')
  const hmacSetup = type === 'HMAC_SHA256'
    ? `String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
String nonce = UUID.randomUUID().toString().replace("-", "");
String canonical = "${value.method}\\n${value.path}\\n${value.query}\\n" + sha256Hex(body.getBytes(StandardCharsets.UTF_8)) + "\\n" + timestamp + "\\n" + nonce;
String signature = hmacHex("${EXAMPLE_CREDENTIALS.HMAC_SECRET}", canonical);
connection.setRequestProperty("X-Rule-Access-Key", "${EXAMPLE_CREDENTIALS.ACCESS_KEY}");
connection.setRequestProperty("X-Rule-Timestamp", timestamp);
connection.setRequestProperty("X-Rule-Nonce", nonce);
connection.setRequestProperty("X-Rule-Signature", signature);`
    : javaHeaderLines(auth)
  const helpers = type === 'HMAC_SHA256'
    ? `
static String sha256Hex(byte[] value) throws Exception {
    return hex(MessageDigest.getInstance("SHA-256").digest(value));
}

static String hmacHex(String secret, String value) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return hex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
}

static String hex(byte[] value) {
    StringBuilder result = new StringBuilder();
    for (byte item : value) result.append(String.format("%02x", item & 0xff));
    return result.toString();
}`
    : ''
  return `import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

String body = ${JSON.stringify(value.body)};
HttpURLConnection connection = (HttpURLConnection) new URL(${JSON.stringify(value.url)}).openConnection();
connection.setRequestMethod("${value.method}");
connection.setRequestProperty("Content-Type", "application/json");
connection.setDoOutput(true);
${hmacSetup}
try (OutputStream output = connection.getOutputStream()) {
    output.write(body.getBytes(StandardCharsets.UTF_8));
}
int status = connection.getResponseCode();
InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
    for (String line; (line = reader.readLine()) != null;) System.out.println(line);
}
${helpers}`
}

function pythonSample(endpoint, auth) {
  const value = endpointValues(endpoint, auth)
  const type = String((auth && auth.authType) || 'LEGACY_TOKEN')
  let setup = `headers = {'Content-Type': 'application/json'}`
  if (type === 'BASIC') {
    setup += `
credentials = base64.b64encode(b'${EXAMPLE_CREDENTIALS.BASIC_USERNAME}:${EXAMPLE_CREDENTIALS.BASIC_PASSWORD}').decode()
headers['Authorization'] = 'Basic ' + credentials`
  } else if (type === 'HMAC_SHA256') {
    setup += `
timestamp = str(int(time.time()))
nonce = uuid.uuid4().hex
body_hash = hashlib.sha256(body).hexdigest()
canonical = '${value.method}\\n${value.path}\\n${value.query}\\n' + body_hash + '\\n' + timestamp + '\\n' + nonce
signature = hmac.new(b'${EXAMPLE_CREDENTIALS.HMAC_SECRET}', canonical.encode('utf-8'), hashlib.sha256).hexdigest()
headers.update({
    'X-Rule-Access-Key': '${EXAMPLE_CREDENTIALS.ACCESS_KEY}',
    'X-Rule-Timestamp': timestamp,
    'X-Rule-Nonce': nonce,
    'X-Rule-Signature': signature
})`
  } else {
    headerValues(auth).forEach(item => { setup += `\nheaders[${JSON.stringify(item.name)}] = ${JSON.stringify(item.value)}` })
  }
  return `import base64
import hashlib
import hmac
import time
import uuid
from urllib.request import Request, urlopen

body = ${JSON.stringify(value.body)}.encode('utf-8')
${setup}
request = Request(${JSON.stringify(value.url)}, data=body, headers=headers, method='${value.method}')
with urlopen(request, timeout=30) as response:
    print(response.status)
    print(response.read().decode('utf-8'))`
}

function javascriptSample(endpoint, auth) {
  const value = endpointValues(endpoint, auth)
  const type = String((auth && auth.authType) || 'LEGACY_TOKEN')
  const querySetup = type === 'API_KEY' && auth.placement === 'QUERY'
    ? `const url = new URL(${JSON.stringify(`${value.baseUrl}${value.path}`)});
url.searchParams.set(${JSON.stringify(String(auth.parameterName || 'api_key'))}, '${EXAMPLE_CREDENTIALS.API_KEY}');`
    : `const url = new URL(${JSON.stringify(value.url)});`
  let authSetup = ''
  if (type === 'BASIC') {
    authSetup = `headers.Authorization = 'Basic ' + btoa(unescape(encodeURIComponent('${EXAMPLE_CREDENTIALS.BASIC_USERNAME}:${EXAMPLE_CREDENTIALS.BASIC_PASSWORD}')));`
  } else if (type === 'HMAC_SHA256') {
    authSetup = `const encoder = new TextEncoder();
const timestamp = String(Math.floor(Date.now() / 1000));
const nonce = crypto.randomUUID().replace(/-/g, '');
const bodyHash = [...new Uint8Array(await crypto.subtle.digest('SHA-256', encoder.encode(body)))].map(value => value.toString(16).padStart(2, '0')).join('');
const canonical = '${value.method}\\n${value.path}\\n${value.query}\\n' + bodyHash + '\\n' + timestamp + '\\n' + nonce;
const key = await crypto.subtle.importKey('raw', encoder.encode('${EXAMPLE_CREDENTIALS.HMAC_SECRET}'), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
const signature = [...new Uint8Array(await crypto.subtle.sign('HMAC', key, encoder.encode(canonical)))].map(value => value.toString(16).padStart(2, '0')).join('');
headers['X-Rule-Access-Key'] = '${EXAMPLE_CREDENTIALS.ACCESS_KEY}';
headers['X-Rule-Timestamp'] = timestamp;
headers['X-Rule-Nonce'] = nonce;
headers['X-Rule-Signature'] = signature;`
  } else {
    authSetup = headerValues(auth).map(item => `headers[${JSON.stringify(item.name)}] = ${JSON.stringify(item.value)};`).join('\n')
  }
  return `const body = ${JSON.stringify(value.body)};
${querySetup}
const headers = { 'Content-Type': 'application/json' };
${authSetup}
const response = await fetch(url, { method: '${value.method}', headers, body });
console.log(response.status, await response.text());`
}

function goSample(endpoint, auth) {
  const value = endpointValues(endpoint, auth)
  const type = String((auth && auth.authType) || 'LEGACY_TOKEN')
  let authSetup = ''
  if (type === 'BASIC') {
    authSetup = `request.SetBasicAuth("${EXAMPLE_CREDENTIALS.BASIC_USERNAME}", "${EXAMPLE_CREDENTIALS.BASIC_PASSWORD}")`
  } else if (type === 'HMAC_SHA256') {
    authSetup = `timestamp := strconv.FormatInt(time.Now().Unix(), 10)
nonce := strconv.FormatInt(time.Now().UnixNano(), 16)
bodyDigest := sha256.Sum256([]byte(body))
canonical := "${value.method}\\n${value.path}\\n${value.query}\\n" + hex.EncodeToString(bodyDigest[:]) + "\\n" + timestamp + "\\n" + nonce
mac := hmac.New(sha256.New, []byte("${EXAMPLE_CREDENTIALS.HMAC_SECRET}"))
mac.Write([]byte(canonical))
request.Header.Set("X-Rule-Access-Key", "${EXAMPLE_CREDENTIALS.ACCESS_KEY}")
request.Header.Set("X-Rule-Timestamp", timestamp)
request.Header.Set("X-Rule-Nonce", nonce)
request.Header.Set("X-Rule-Signature", hex.EncodeToString(mac.Sum(nil)))`
  } else {
    authSetup = headerValues(auth).map(item => `request.Header.Set(${JSON.stringify(item.name)}, ${JSON.stringify(item.value)})`).join('\n')
  }
  return `package main

import (
    "bytes"
    "crypto/hmac"
    "crypto/sha256"
    "encoding/hex"
    "fmt"
    "io"
    "net/http"
    "strconv"
    "time"
)

func main() {
    body := ${JSON.stringify(value.body)}
    request, _ := http.NewRequest("${value.method}", ${JSON.stringify(value.url)}, bytes.NewBufferString(body))
    request.Header.Set("Content-Type", "application/json")
    ${authSetup}
    response, err := (&http.Client{Timeout: 30 * time.Second}).Do(request)
    if err != nil { panic(err) }
    defer response.Body.Close()
    value, _ := io.ReadAll(response.Body)
    fmt.Println(response.StatusCode, string(value))
}`
}

export function generateCodeSamples(endpoint, auth) {
  return {
    shell: shellSample(endpoint, auth),
    java: javaSample(endpoint, auth),
    python: pythonSample(endpoint, auth),
    javascript: javascriptSample(endpoint, auth),
    go: goSample(endpoint, auth)
  }
}
