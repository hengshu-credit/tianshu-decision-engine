import { createLocalVue, mount } from '@vue/test-utils'
import Vue from 'vue'

jest.unmock('element-ui')
import ElementUI from 'element-ui'
import ElementTabFocusGuard from '@/plugins/elementTabFocusGuard'

const TabsHost = {
  template: `
    <el-tabs v-model="activeTab">
      <el-tab-pane label="鉴权配置" name="auth">
        <button id="open-token" type="button">Token</button>
      </el-tab-pane>
      <el-tab-pane label="临时 Token" name="tokens">
        <button type="button">刷新</button>
      </el-tab-pane>
    </el-tabs>
  `,
  data() {
    return { activeTab: 'auth' }
  }
}

describe('ElementTabFocusGuard', () => {
  test('程序化切换页签前转移焦点并隔离隐藏页签', async () => {
    const localVue = createLocalVue()
    localVue.use(ElementUI)
    localVue.use(ElementTabFocusGuard)
    const wrapper = mount(TabsHost, { localVue, attachTo: document.body })
    await Vue.nextTick()

    const action = wrapper.find('#open-token').element
    action.focus()
    expect(document.activeElement).toBe(action)

    wrapper.vm.activeTab = 'tokens'
    await Vue.nextTick()

    const authPane = wrapper.find('#pane-auth').element
    const tokenPane = wrapper.find('#pane-tokens').element
    expect(document.activeElement.id).toBe('tab-tokens')
    expect(authPane.hasAttribute('inert')).toBe(true)
    expect(tokenPane.hasAttribute('inert')).toBe(false)
    await Vue.nextTick()
    wrapper.destroy()
  })
})
