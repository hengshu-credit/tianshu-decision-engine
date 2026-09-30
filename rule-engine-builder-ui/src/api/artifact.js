import request from './request'

export function getArtifactDetail(artifactId) {
  return request({ url: `/rule/artifact/${artifactId}`, method: 'get' })
}

export function listArtifactDeployments(artifactId) {
  return request({ url: `/rule/artifact/${artifactId}/deployments`, method: 'get' })
}

export function listDeploymentBindings(deploymentId) {
  return request({ url: `/rule/artifact/deployments/${deploymentId}/bindings`, method: 'get' })
}

export function getArtifactDeploymentOptions(artifactId, targetProjectId) {
  return request({
    url: `/rule/artifact/${artifactId}/deployment-options`,
    method: 'get',
    params: targetProjectId ? { targetProjectId } : {},
  })
}

export function listPublishOutbox(definitionId, limit = 50) {
  return request({ url: '/rule/artifact/outbox', method: 'get', params: { definitionId, limit } })
}

export function downloadArtifact(artifactId) {
  return request({
    url: `/rule/artifact/${artifactId}/download`,
    method: 'get',
    responseType: 'blob'
  })
}

export function downloadArtifactMigration(artifactId, includeDependencies = true) {
  return request({
    url: `/rule/artifact/${artifactId}/migration-download`,
    method: 'get',
    params: { includeDependencies },
    responseType: 'blob',
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
