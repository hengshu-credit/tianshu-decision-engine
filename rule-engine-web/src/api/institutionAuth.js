import request from './request'
import { metadataResponse } from '@/utils/metadataResponse'

const baseUrl = '/rule/institution-auth'

export function listInstitutionAuths() {
  return request.get(baseUrl, metadataResponse)
}

export function createInstitutionAuth(data) {
  return request.post(baseUrl, data, metadataResponse)
}

export function updateInstitutionAuth(authId, data) {
  return request.put(`${baseUrl}/${authId}`, data, metadataResponse)
}

export function updateInstitutionAuthStatus(authId, status) {
  return request.put(`${baseUrl}/${authId}/status`, null, { ...metadataResponse, params: { status } })
}

export function getInstitutionAuthFull(authId) {
  return request.get(`${baseUrl}/${authId}/full`, metadataResponse)
}

export function regenerateInstitutionAuthSecret(authId) {
  return request.post(`${baseUrl}/${authId}/regenerate`, null, metadataResponse)
}
