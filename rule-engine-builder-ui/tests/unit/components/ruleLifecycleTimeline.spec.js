import { mount } from '@test-utils'
import RuleLifecycleTimeline from '@/components/rule/RuleLifecycleTimeline.vue'

describe('RuleLifecycleTimeline', () => {
  test('系统操作者显示业务中文且最新事件排在前面', () => {
    const wrapper = mount(RuleLifecycleTimeline, {
      props: {
        events: [
          {
            id: 1,
            action: 'SUBMIT',
            fromState: 'DRAFT',
            toState: 'REVIEW',
            actor: 'SYSTEM_CONSOLE',
            createTime: '2026-07-24 09:00:00'
          },
          {
            id: 2,
            action: 'APPROVE',
            fromState: 'REVIEW',
            toState: 'APPROVED',
            actor: '',
            createTime: '2026-07-24 09:10:00'
          }
        ]
      }
    })

    expect(wrapper.vm.orderedEvents.map((event) => event.id)).toEqual([2, 1])
    expect(wrapper.vm.actorLabel('SYSTEM_CONSOLE')).toBe('系统控制台')
    expect(wrapper.vm.actorLabel('')).toBe('系统控制台')
  })
})
