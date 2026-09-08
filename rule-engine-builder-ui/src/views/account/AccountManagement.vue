<template>
  <div class="uiue-list-page account-page">
    <header class="page-header">
      <div>
        <span class="page-eyebrow">CONSOLE ACCESS</span>
        <h1>账户管理</h1>
        <p>通过角色批量授权，并可对单个账户设置额外允许或明确拒绝。</p>
      </div>
      <el-button
        v-if="activeTab === 'accounts'"
        v-permission="'account:manage'"
        type="primary"
        @click="openAccountDialog()"
      >
        新增账户
      </el-button>
      <el-button
        v-else
        v-permission="'role:manage'"
        type="primary"
        @click="openRoleDialog()"
      >
        新增角色
      </el-button>
    </header>

    <section class="content-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="账户" name="accounts">
          <el-table show-overflow-tooltip v-loading="loading" :data="accounts" row-key="id">
            <el-table-column label="账户" min-width="180">
              <template #default="{ row }">
                <div class="identity-cell">
                  <span class="identity-avatar">{{
                    accountInitial(row)
                  }}</span>
                  <span>
                    <strong>{{ row.displayName || row.username }}</strong>
                    <small>{{ row.username }}</small>
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="角色" min-width="220">
              <template #default="{ row }">
                <div class="tag-list">
                  <el-tag
                    v-for="code in row.roleCodes"
                    :key="code"
                    type="info"
                    effect="plain"
                  >
                    {{ roleName(code) }}
                  </el-tag>
                  <span v-if="!row.roleCodes || !row.roleCodes.length" class="muted">
                    未分配角色
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="最终权限" width="110" align="right">
              <template #default="{ row }">
                <strong>{{ (row.effectivePermissions || []).length }}</strong>
                <span class="muted"> 项</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'">
                  {{ row.status === 1 ? '已启用' : '已停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-permission="'account:manage'"
                  link
                  data-action="edit"
                  type="primary"
                  @click="openAccountDialog(row)"
                >
                  编辑
                </el-button>
                <el-button
                  v-permission="'account:manage'"
                  link
                  data-action="configure"
                  type="primary"
                  @click="openPermissionDialog(row)"
                >
                  权限调整
                </el-button>
                <el-button
                  v-permission="'account:manage'"
                  link
                  data-action="reset"
                  @click="openPasswordDialog(row)"
                >
                  重置密码
                </el-button>
                <el-button
                  v-permission="'account:manage'"
                  link
                  :data-action="row.status === 1 ? 'disable' : 'publish'"
                  :type="row.status === 1 ? 'danger' : 'success'"
                  @click="toggleAccount(row)"
                >
                  {{ row.status === 1 ? '停用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="角色" name="roles">
          <el-table show-overflow-tooltip v-loading="loading" :data="roles" row-key="id">
            <el-table-column prop="roleName" label="角色名称" min-width="180" />
            <el-table-column prop="roleCode" label="角色编码" min-width="160" />
            <el-table-column label="已授权" width="110" align="right">
              <template #default="{ row }">
                <strong>{{ (row.permissionCodes || []).length }}</strong>
                <span class="muted"> 项</span>
              </template>
            </el-table-column>
            <el-table-column prop="memberCount" label="成员" width="90" align="right" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'">
                  {{ row.status === 1 ? '已启用' : '已停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-permission="'role:manage'"
                  link
                  data-action="configure"
                  type="primary"
                  @click="openRoleDialog(row)"
                >
                  配置权限
                </el-button>
                <el-button
                  v-permission="'role:manage'"
                  link
                  :data-action="row.status === 1 ? 'disable' : 'publish'"
                  :type="row.status === 1 ? 'danger' : 'success'"
                  @click="toggleRole(row)"
                >
                  {{ row.status === 1 ? '停用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog
      v-model="accountDialogVisible"
      :title="accountForm.id ? '编辑账户' : '新增账户'"
      width="560px"
    >
      <el-form label-position="top">
        <el-form-item label="用户名">
          <el-input
            v-model="accountForm.username"
            data-testid="account-username-input"
            :disabled="Boolean(accountForm.id)"
          />
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input v-model="accountForm.displayName" />
        </el-form-item>
        <el-form-item :label="accountForm.id ? '新密码（留空不修改）' : '初始密码'">
          <el-input v-model="accountForm.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="accountForm.roleIds" multiple clearable>
            <el-option
              v-for="role in enabledRoles"
              :key="role.id"
              :label="role.roleName"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="accountDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveAccount">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="permissionDialogVisible"
      title="账户权限调整"
      width="920px"
    >
      <div class="permission-summary">
        <div>
          <span>角色继承</span>
          <strong>{{ inheritedPermissionCount }}</strong>
        </div>
        <div>
          <span>单独允许</span>
          <strong>{{ allowCount }}</strong>
        </div>
        <div>
          <span>明确拒绝</span>
          <strong>{{ denyCount }}</strong>
        </div>
        <div>
          <span>最终有效</span>
          <strong>{{ effectivePermissionCount }}</strong>
        </div>
      </div>
      <el-form label-position="top">
        <el-form-item label="所属角色">
          <el-select v-model="permissionRoleIds" multiple clearable>
            <el-option
              v-for="role in enabledRoles"
              :key="role.id"
              :label="role.roleName"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <el-table :data="permissionRows" max-height="420">
        <el-table-column prop="permissionGroup" label="模块" width="130" />
        <el-table-column label="权限" min-width="220">
          <template #default="{ row }">
            <strong>{{ row.permissionName }}</strong>
            <small class="permission-code">{{ row.permissionCode }}</small>
          </template>
        </el-table-column>
        <el-table-column label="角色继承" width="100">
          <template #default="{ row }">
            <el-tag :type="row.inherited ? 'success' : 'info'" effect="plain">
              {{ row.inherited ? '已授权' : '未授权' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="账户调整" width="260">
          <template #default="{ row }">
            <el-radio-group v-model="overrideDraft[row.permissionCode]">
              <el-radio-button value="INHERIT">继承</el-radio-button>
              <el-radio-button value="ALLOW">允许</el-radio-button>
              <el-radio-button value="DENY">拒绝</el-radio-button>
            </el-radio-group>
          </template>
        </el-table-column>
        <el-table-column label="最终结果" width="100">
          <template #default="{ row }">
            <el-tag :type="row.effective ? 'success' : 'danger'">
              {{ row.effective ? '允许' : '拒绝' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="permissionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="savePermissions">保存调整</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="roleDialogVisible"
      :title="roleForm.id ? '配置角色' : '新增角色'"
      width="760px"
    >
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="角色名称">
            <el-input v-model="roleForm.roleName" />
          </el-form-item>
          <el-form-item label="角色编码">
            <el-input v-model="roleForm.roleCode" />
          </el-form-item>
        </div>
        <el-form-item label="说明">
          <el-input v-model="roleForm.description" type="textarea" />
        </el-form-item>
      </el-form>
      <div class="permission-groups">
        <section
          v-for="group in permissionGroups"
          :key="group.name"
          class="permission-group"
        >
          <strong>{{ group.name }}</strong>
          <el-checkbox-group v-model="roleForm.permissionCodes">
            <el-checkbox
              v-for="permission in group.items"
              :key="permission.permissionCode"
              :value="permission.permissionCode"
            >
              {{ permission.permissionName }}
            </el-checkbox>
          </el-checkbox-group>
        </section>
      </div>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRole">保存角色</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordDialogVisible" title="重置密码" width="460px">
      <el-alert
        title="保存后旧密码立即失效，当前用户的既有登录会话不受影响。"
        type="warning"
        :closable="false"
      />
      <el-form label-position="top" class="password-form">
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="savePassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  changeConsoleAccountStatus,
  changeConsoleRoleStatus,
  listConsoleAccounts,
  listConsolePermissions,
  listConsoleRoles,
  resetConsoleAccountPassword,
  saveConsoleAccount,
  saveConsoleAccountPermissions,
  saveConsoleRole
} from '@/api/consoleAccount'

function emptyAccountForm() {
  return {
    id: null,
    username: '',
    displayName: '',
    password: '',
    status: 1,
    roleIds: []
  }
}

function emptyRoleForm() {
  return {
    id: null,
    roleCode: '',
    roleName: '',
    description: '',
    status: 1,
    permissionCodes: []
  }
}

export default {
  name: 'AccountManagement',
  data() {
    return {
      activeTab: 'accounts',
      loading: false,
      accounts: [],
      roles: [],
      permissionCatalog: [],
      accountDialogVisible: false,
      permissionDialogVisible: false,
      roleDialogVisible: false,
      passwordDialogVisible: false,
      accountForm: emptyAccountForm(),
      roleForm: emptyRoleForm(),
      selectedAccount: null,
      passwordAccount: null,
      newPassword: '',
      permissionRoleIds: [],
      overrideDraft: {}
    }
  },
  computed: {
    enabledRoles() {
      return this.roles.filter(role => role.status === 1)
    },
    inheritedPermissionCodes() {
      const selected = new Set(this.permissionRoleIds)
      const codes = new Set()
      this.roles.forEach(role => {
        if (!selected.has(role.id) || role.status !== 1) return
        ;(role.permissionCodes || []).forEach(code => codes.add(code))
      })
      return codes
    },
    permissionRows() {
      return this.permissionCatalog.map(permission => {
        const code = permission.permissionCode
        const inherited = this.inheritedPermissionCodes.has(code)
        const override = this.overrideDraft[code] || 'INHERIT'
        return {
          ...permission,
          inherited,
          override,
          effective: override === 'DENY'
            ? false
            : override === 'ALLOW' || inherited
        }
      })
    },
    inheritedPermissionCount() {
      return this.inheritedPermissionCodes.size
    },
    allowCount() {
      return Object.values(this.overrideDraft).filter(value => value === 'ALLOW').length
    },
    denyCount() {
      return Object.values(this.overrideDraft).filter(value => value === 'DENY').length
    },
    effectivePermissionCount() {
      return this.permissionRows.filter(row => row.effective).length
    },
    permissionGroups() {
      const groups = new Map()
      this.permissionCatalog.forEach(permission => {
        const name = permission.permissionGroup || '其他'
        if (!groups.has(name)) groups.set(name, [])
        groups.get(name).push(permission)
      })
      return Array.from(groups.entries()).map(([name, items]) => ({
        name,
        items
      }))
    }
  },
  mounted() {
    this.loadData()
  },
  methods: {
    async loadData() {
      this.loading = true
      try {
        const [accounts, roles, permissions] = await Promise.all([
          listConsoleAccounts(),
          listConsoleRoles(),
          listConsolePermissions()
        ])
        this.accounts = accounts.data || []
        this.roles = roles.data || []
        this.permissionCatalog = permissions.data || []
      } finally {
        this.loading = false
      }
    },
    accountInitial(account) {
      const value = (account && (account.displayName || account.username)) || 'U'
      return Array.from(value.trim())[0].toUpperCase()
    },
    roleName(code) {
      const role = this.roles.find(item => item.roleCode === code)
      return role ? role.roleName : code
    },
    openAccountDialog(account) {
      this.accountForm = account
        ? {
            id: account.id,
            username: account.username,
            displayName: account.displayName,
            password: '',
            status: account.status,
            roleIds: (account.roleIds || []).slice()
          }
        : emptyAccountForm()
      this.accountDialogVisible = true
    },
    async saveAccount() {
      await saveConsoleAccount(this.accountForm)
      this.accountDialogVisible = false
      ElMessage.success('账户已保存')
      await this.loadData()
    },
    openPermissionDialog(account) {
      this.selectedAccount = account
      this.permissionRoleIds = (account.roleIds || []).slice()
      const overrides = account.permissionOverrides || {}
      this.overrideDraft = {}
      this.permissionCatalog.forEach(permission => {
        this.overrideDraft[permission.permissionCode] =
          overrides[permission.permissionCode] || 'INHERIT'
      })
      this.permissionDialogVisible = true
    },
    async savePermissions() {
      const overrides = {}
      Object.entries(this.overrideDraft).forEach(([code, effect]) => {
        if (effect !== 'INHERIT') overrides[code] = effect
      })
      await saveConsoleAccountPermissions(this.selectedAccount.id, {
        roleIds: this.permissionRoleIds,
        permissionOverrides: overrides
      })
      this.permissionDialogVisible = false
      ElMessage.success('账户最终权限已更新')
      await this.loadData()
    },
    async toggleAccount(account) {
      const next = account.status === 1 ? 0 : 1
      await ElMessageBox.confirm(
        `确认${next === 1 ? '启用' : '停用'}账户“${account.displayName || account.username}”？`,
        '账户状态确认'
      )
      await changeConsoleAccountStatus(account.id, next)
      await this.loadData()
    },
    openPasswordDialog(account) {
      this.passwordAccount = account
      this.newPassword = ''
      this.passwordDialogVisible = true
    },
    async savePassword() {
      await resetConsoleAccountPassword(
        this.passwordAccount.id, this.newPassword
      )
      this.passwordDialogVisible = false
      this.newPassword = ''
      ElMessage.success('密码已重置')
    },
    openRoleDialog(role) {
      this.roleForm = role
        ? {
            id: role.id,
            roleCode: role.roleCode,
            roleName: role.roleName,
            description: role.description || '',
            status: role.status,
            permissionCodes: (role.permissionCodes || []).slice()
          }
        : emptyRoleForm()
      this.roleDialogVisible = true
    },
    async saveRole() {
      await saveConsoleRole(this.roleForm)
      this.roleDialogVisible = false
      ElMessage.success('角色已保存')
      await this.loadData()
    },
    async toggleRole(role) {
      const next = role.status === 1 ? 0 : 1
      await ElMessageBox.confirm(
        `确认${next === 1 ? '启用' : '停用'}角色“${role.roleName}”？`,
        '角色状态确认'
      )
      await changeConsoleRoleStatus(role.id, next)
      await this.loadData()
    }
  }
}
</script>

<style lang="scss" scoped>
.account-page {
  color: var(--tianshu-text-primary);
}
.page-header {
  display: flex;
  min-height: 76px;
  margin-bottom: 16px;
  align-items: flex-start;
  justify-content: space-between;

  h1 {
    margin: 4px 0 0;
    color: #111827;
    font-size: 24px;
    font-weight: 650;
    line-height: 32px;
  }

  p {
    margin: 4px 0 0;
    color: var(--tianshu-text-tertiary);
    font-size: 14px;
    line-height: 22px;
  }
}
.page-eyebrow {
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}
.content-card {
  padding: 16px 20px 24px;
  background: var(--tianshu-bg-surface);
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.identity-cell {
  display: flex;
  align-items: center;
  gap: 12px;

  > span:last-child {
    display: flex;
    min-width: 0;
    flex-direction: column;
  }

  strong {
    color: #1f2937;
    font-weight: 600;
    line-height: 20px;
  }

  small {
    color: var(--tianshu-text-disabled);
    line-height: 18px;
  }
}
.identity-avatar {
  display: inline-flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  color: #334155;
  font-weight: 600;
  background: #eef2ff;
  border-radius: 50%;
}
.tag-list {
  display: inline-flex;
  flex-wrap: nowrap;
  gap: 4px;
}
.muted {
  color: var(--tianshu-text-disabled);
  font-weight: 400;
}
.permission-summary {
  display: grid;
  margin-bottom: 16px;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid #e5e7eb;
  border-radius: 8px;

  > div {
    display: flex;
    min-height: 68px;
    padding: 12px 16px;
    flex-direction: column;
    justify-content: center;
    border-right: 1px solid #e5e7eb;

    &:last-child {
      border-right: 0;
    }
  }

  span {
    color: var(--tianshu-text-tertiary);
    font-size: 12px;
  }

  strong {
    margin-top: 4px;
    color: #1f2937;
    font-size: 20px;
  }
}
.permission-code {
  display: block;
  margin-top: 2px;
  color: var(--tianshu-text-disabled);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
}
.permission-groups {
  display: grid;
  max-height: 380px;
  overflow: auto;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.permission-group {
  padding: 16px;
  background: var(--tianshu-bg-soft);
  border: 1px solid #e5e7eb;
  border-radius: 8px;

  > strong {
    display: block;
    margin-bottom: 12px;
    color: #334155;
  }
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.password-form {
  margin-top: 16px;
}
@media (max-width: 900px) {
  .permission-summary,
  .permission-groups,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .permission-summary > div {
    border-right: 0;
    border-bottom: 1px solid #e5e7eb;

    &:last-child {
      border-bottom: 0;
    }
  }
}
</style>
