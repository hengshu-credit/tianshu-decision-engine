import request from './request'

export function downloadArtifact(artifactId) {
  return request({
    url: `/rule/artifact/${artifactId}/download`,
    method: 'get',
    responseType: 'blob'
  })
}

export function importArtifact(file, expectedPackageDigest) {
  const data = new FormData()
  data.append('file', file)
  return request({
    url: '/rule/artifact/import',
    method: 'post',
    data,
    params: { expectedPackageDigest },
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 30 * 60 * 1000
  })
}

export function deployArtifact(data) {
  return request({ url: '/rule/artifact/deploy', method: 'post', data })
}
