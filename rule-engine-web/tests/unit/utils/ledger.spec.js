import { snapshotContent } from '@/utils/ledger'

test('文章版本预览和配置对比保留关联事件与项目的大整数 ID', () => {
  const snapshot = snapshotContent('{"body":"正文中的 9007199254740993 保持原样","eventIds":[9007199254740993],"projectId":9007199254740995}')
  expect(snapshot.eventIds).toEqual(['9007199254740993'])
  expect(snapshot.projectId).toBe('9007199254740995')
  expect(snapshot.body).toBe('正文中的 9007199254740993 保持原样')
})
