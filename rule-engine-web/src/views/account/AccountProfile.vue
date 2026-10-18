<template>
  <div class="uiue-list-page account-profile-page">
    <header class="page-header">
      <div>
        <span class="page-eyebrow">PERSONAL SETTINGS</span>
        <h1>个人设置</h1>
        <p>管理个人资料和控制台登录密码。</p>
      </div>
    </header>

    <section class="profile-grid">
      <div class="content-card">
        <div class="card-heading"><h2>个人资料</h2><span>登录名创建后不可修改</span></div>
        <el-form v-loading="loading" label-position="top" :model="profile">
          <el-form-item label="用户名"><el-input :model-value="profile.username" disabled /></el-form-item>
          <el-form-item label="显示名称"><el-input v-model="profile.displayName" clearable /></el-form-item>
          <el-form-item label="注册邮箱">
            <div class="email-value">
              <el-input v-model="profile.email" type="email" clearable placeholder="用于验证和找回密码" />
              <el-tag :type="profile.emailVerifiedAt ? 'success' : 'warning'" effect="plain">
                {{ profile.emailVerifiedAt ? '已验证' : '未验证' }}
              </el-tag>
            </div>
          </el-form-item>
          <el-form-item label="账户状态"><el-tag type="success">{{ profile.registrationStatus === 'ACTIVE' ? '已激活' : '待验证' }}</el-tag></el-form-item>
          <el-button type="primary" :loading="savingProfile" @click="saveProfile">保存资料</el-button>
        </el-form>
      </div>

      <div class="content-card">
        <div class="card-heading"><h2>修改密码</h2><span>修改后需要重新登录</span></div>
        <el-form ref="passwordForm" label-position="top" :model="passwordForm" :rules="passwordRules">
          <el-form-item label="当前密码" prop="currentPassword"><el-input v-model="passwordForm.currentPassword" type="password" show-password autocomplete="current-password" /></el-form-item>
          <el-form-item label="新密码" prop="password"><el-input v-model="passwordForm.password" type="password" show-password autocomplete="new-password" /></el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword"><el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
          <el-button type="primary" :loading="changingPassword" @click="changePassword">修改密码</el-button>
        </el-form>
      </div>
    </section>
  </div>
</template>

<script>
import { ElMessage } from 'element-plus'
import { changeConsolePassword, getConsoleProfile, updateConsoleProfile } from '@/api/auth'
import { clearCurrentUser } from '@/security/permissionState'

export default {
  name: 'AccountProfile',
  data() {
    const validateConfirm = (rule, value, callback) => value !== this.passwordForm.password ? callback(new Error('两次输入的密码不一致')) : callback()
    return {
      loading: false,
      savingProfile: false,
      changingPassword: false,
      profile: { username: '', displayName: '', email: '', emailVerifiedAt: null, registrationStatus: '' },
      passwordForm: { currentPassword: '', password: '', confirmPassword: '' },
      passwordRules: {
        currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
        password: [{ required: true, message: '请输入新密码', trigger: 'blur' }, { min: 8, message: '密码不能少于8位', trigger: 'blur' }],
        confirmPassword: [{ required: true, message: '请确认新密码', trigger: 'blur' }, { validator: validateConfirm, trigger: 'blur' }],
      },
    }
  },
  mounted() { this.loadProfile() },
  methods: {
    async loadProfile() {
      this.loading = true
      try { const response = await getConsoleProfile(); this.profile = { ...this.profile, ...(response.data || {}) } } finally { this.loading = false }
    },
    async saveProfile() {
      this.savingProfile = true
      try { const response = await updateConsoleProfile({ displayName: this.profile.displayName, email: this.profile.email }); this.profile = { ...this.profile, ...(response.data || {}) }; ElMessage.success(this.profile.emailVerifiedAt ? '个人资料已保存' : '资料已保存，验证邮件已发送') } finally { this.savingProfile = false }
    },
    changePassword() {
      this.$refs.passwordForm.validate(async valid => {
        if (!valid) return
        this.changingPassword = true
        try {
          await changeConsolePassword({ ...this.passwordForm })
          clearCurrentUser()
          ElMessage.success('密码已修改，请重新登录')
          this.$router.replace('/login')
        } finally { this.changingPassword = false }
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.account-profile-page { color: var(--tianshu-text-primary); }
.page-header { min-height: 76px; margin-bottom: 16px; }
.page-header h1 { margin: 4px 0 0; font-size: 24px; font-weight: 650; line-height: 32px; }
.page-header p { margin: 4px 0 0; color: var(--tianshu-text-tertiary); font-size: 14px; }
.page-eyebrow { color: var(--el-color-primary); font-size: 12px; font-weight: 700; letter-spacing: .08em; }
.profile-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.content-card { padding: 20px 24px 24px; background: var(--tianshu-bg-surface); border: 1px solid var(--tianshu-border-subtle); border-radius: 8px; box-shadow: 0 1px 2px rgba(15, 23, 42, .04); }
.card-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin-bottom: 20px; }
.card-heading h2 { margin: 0; font-size: 17px; }
.card-heading span { color: var(--tianshu-text-tertiary); font-size: 12px; }
.email-value { display: flex; align-items: center; gap: 8px; min-height: 32px; color: var(--tianshu-text-secondary); }
:deep(.el-input__wrapper) { min-height: 42px; border-radius: 10px; background: var(--tianshu-bg-elevated); }
@media (max-width: 900px) { .profile-grid { grid-template-columns: 1fr; } }
</style>
