import { isRestrictedJsonPath } from '@/utils/restrictedJsonPath'

test.each(['$', '$.customer.id', '$.items[0].score', '$["Mobile.Number"][0][' + JSON.stringify('姓 名"') + ']', '$[""]', '$.姓名'])('开放接口接受确定性路径 %s', path => {
  expect(isRestrictedJsonPath(path)).toBe(true)
})
test.each(['$..id', '$[*]', '$[?(@.score>60)]', '$.length()', '$["unclosed]', '$["bad\\x"]', '$[2147483648]', '$[-1]'])('开放接口拒绝动态或非法路径 %s', path => {
  expect(isRestrictedJsonPath(path)).toBe(false)
})
