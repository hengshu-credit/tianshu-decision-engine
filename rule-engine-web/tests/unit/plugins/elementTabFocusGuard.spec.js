import { mount } from '@test-utils'
import { nextTick } from 'vue'
import ElementTabFocusGuard from '@/plugins/elementTabFocusGuard'

const TabsStub = {
  name: 'ElTabs',
  props: { modelValue: { type: String, default: '' } },
  computed: {
    currentName() {
      return this.modelValue
    },
  },
  template: `
    <div>
      <button id="tab-auth" role="tab" aria-controls="pane-auth">鉴权配置</button>
      <button id="tab-tokens" role="tab" aria-controls="pane-tokens">临时 Token</button>
      <slot />
    </div>
  `,
}

const TabPaneStub = {
  name: 'ElTabPane',
  props: {
    name: { type: String, required: true },
    label: { type: String, default: '' },
  },
  template: `
    <div
      :id="'pane-' + name"
      role="tabpanel"
      :aria-hidden="$parent.currentName !== name"
    >
      <slot />
    </div>
  `,
}

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
    const wrapper = mount(TabsHost, {
      attachTo: document.body,
      plugins: [ElementTabFocusGuard],
      stubs: {
        'el-tabs': TabsStub,
        'el-tab-pane': TabPaneStub,
      },
    })
    await nextTick()

    const action = wrapper.find('#open-token').element
    action.focus()
    expect(document.activeElement).toBe(action)

    wrapper.vm.activeTab = 'tokens'
    await nextTick()

    const authPane = wrapper.find('#pane-auth').element
    const tokenPane = wrapper.find('#pane-tokens').element
    expect(document.activeElement.id).toBe('tab-tokens')
    expect(authPane.hasAttribute('inert')).toBe(true)
    expect(tokenPane.hasAttribute('inert')).toBe(false)
    await nextTick()
    wrapper.unmount()
  })
})
