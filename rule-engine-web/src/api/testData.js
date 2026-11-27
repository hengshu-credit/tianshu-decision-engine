import request from './request'
import { metadataResponse } from '@/utils/metadataResponse'

const base = '/rule/test-data'
export const listMockRules = () => request.get(base + '/rules', metadataResponse)
export const saveMockRule = rule => rule.id
  ? request.put(base + '/rules/' + rule.id, rule) : request.post(base + '/rules', rule)
export const deleteMockRule = id => request.delete(base + '/rules/' + id)
export const generateTestInput = (id, data) => request.post(base + '/generate/' + id, data, metadataResponse)
export const getSavedSample = row => request.get(base + '/samples/' + row.type + '/' + row.ownerId, { params: { source: row.source, id: row.id } })
export const listTestCases = params => request.get(base + '/cases', { ...metadataResponse, params })
export const testCatalog = (type, params) => request.get(base + '/' + type + '/catalog', { ...metadataResponse, params })
const casesPath = (type, owner) => base + '/' + type + '/' + owner + '/cases'
export const getResourceCase = (type, owner, id) => request.get(casesPath(type, owner) + '/' + id, metadataResponse)
export const saveResourceCase = (type, owner, id, data) => id
  ? request.put(casesPath(type, owner) + '/' + id, data) : request.post(casesPath(type, owner), data)
export const deleteResourceCase = (type, owner, id) => request.delete(casesPath(type, owner) + '/' + id)

export const previewMockRule = rule => request.post(base + '/rules/preview',rule)
export const listMockFields = id => request.get(base + '/rules/' + id + '/fields',metadataResponse)
export const setMockField = (id,type,ref,remove=false) => request({
  url: base + '/rules/' + id + '/fields/' + type + '/' + ref, method: remove ? 'delete' : 'put'
})

export const saveDefaultInput = (type, owner, paramsJson) => request.put(base + '/defaults/' + type + '/' + owner, { paramsJson })
export const saveResourceCaseInput = (type, owner, id, paramsJson, originalJson) =>
  request.put(casesPath(type, owner) + '/' + id + '/input', { paramsJson, originalJson })

export const listExampleCases = params => request.get(base + '/example-cases', { ...metadataResponse, params })
export const getExampleCase = (type, id, projectId) => request.get(base + '/example-cases/' + type + '/' + id, { ...metadataResponse, params: { projectId } })
