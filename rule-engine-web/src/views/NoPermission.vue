<template>
  <div class="no-permission-page">
    <div class="no-permission-card">
      <div class="no-permission-icon" aria-hidden="true"><app-icon name="Lock" /></div>
      <span class="page-eyebrow">CONSOLE ACCESS</span>
      <h1>账户已激活</h1>
      <p>当前账户暂未分配系统权限，请联系管理员分配角色后使用决策引擎。</p>
      <div class="no-permission-actions">
        <el-button type="primary" @click="$router.push('/account/profile')">个人设置</el-button>
        <el-button @click="logout">退出登录</el-button>
      </div>
    </div>
  </div>
</template>

<script>
import { consoleLogout } from '@/api/auth'
import { clearCurrentUser } from '@/security/permissionState'

export default {
  name: 'NoPermission',
  methods: {
    async logout() {
      try { await consoleLogout() } finally { clearCurrentUser(); this.$router.replace('/login') }
    },
  },
}
</script>

<style lang="scss" scoped>
.no-permission-page { min-height: calc(100vh - 52px); display: grid; place-items: center; padding: 32px; background: var(--tianshu-bg-page); }
.no-permission-card { width: min(520px, 100%); padding: 42px 36px; color: var(--tianshu-text-primary); text-align: center; background: var(--tianshu-bg-surface); border: 1px solid var(--tianshu-border-subtle); border-radius: 14px; box-shadow: var(--tianshu-shadow-medium); }
.no-permission-icon { width: 56px; height: 56px; display: grid; place-items: center; margin: 0 auto 18px; color: var(--el-color-primary); font-size: 28px; border-radius: 16px; background: var(--tianshu-info-bg); }
.page-eyebrow { color: var(--el-color-primary); font-size: 12px; font-weight: 700; letter-spacing: .08em; }
h1 { margin: 8px 0 10px; font-size: 24px; }
p { margin: 0; color: var(--tianshu-text-tertiary); line-height: 1.7; }
.no-permission-actions { display: flex; justify-content: center; gap: 10px; margin-top: 24px; }
</style>
