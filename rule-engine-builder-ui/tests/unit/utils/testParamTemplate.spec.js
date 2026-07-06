import {
  buildObjectFromPaths,
  collectReferencePaths,
  collectReferencePathsFromText,
  sampleValueForVarType
} from '@/utils/testParamTemplate'

describe('testParamTemplate', () => {
  test('collectReferencePathsFromText extracts dollar and template paths', () => {
    expect(collectReferencePathsFromText('$.customer.id')).toEqual(['customer.id'])
    expect(collectReferencePathsFromText('${customer.mobile}')).toEqual(['customer.mobile'])
    expect(collectReferencePathsFromText('prefix-${customer.id}-${requestId}')).toEqual(['customer.id', 'requestId'])
  })

  test('bare paths are opt-in to avoid treating fixed literals as inputs', () => {
    expect(collectReferencePathsFromText('request.mobile')).toEqual([])
    expect(collectReferencePathsFromText('request.mobile', { allowBarePath: true })).toEqual(['request.mobile'])
    expect(collectReferencePathsFromText('ONLINE', { allowBarePath: true })).toEqual([])
  })

  test('collectReferencePaths walks nested config values', () => {
    const config = {
      headers: { requestId: '${requestId}' },
      body: { certNo: '${customer.idNo}', mobile: '$.customer.mobile' }
    }

    expect(collectReferencePaths(config)).toEqual(['requestId', 'customer.idNo', 'customer.mobile'])
  })

  test('buildObjectFromPaths creates nested sample json', () => {
    expect(buildObjectFromPaths(['customer.id', 'customer.mobile', 'requestId'])).toEqual({
      customer: { id: '', mobile: '' },
      requestId: ''
    })
  })

  test('sampleValueForVarType returns editable defaults by type', () => {
    expect(sampleValueForVarType('NUMBER')).toBe(0)
    expect(sampleValueForVarType('BOOLEAN')).toBe(false)
    expect(sampleValueForVarType('LIST')).toEqual([])
    expect(sampleValueForVarType('OBJECT')).toEqual({})
    expect(sampleValueForVarType('STRING')).toBe('')
  })
})
