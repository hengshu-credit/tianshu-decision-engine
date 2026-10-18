<template>
  <auth-page-shell title="忘记密码" subtitle="通过注册邮箱设置新密码">
    <div class="auth-back"><router-link to="/login">← 返回登录</router-link></div>
    <el-result v-if="sent" icon="success" title="邮件已发送" sub-title="如果该邮箱已注册，系统会发送密码重置链接，请查收邮件。">
      <template #extra><el-button type="primary" @click="$router.replace('/login')">返回登录</el-button></template>
    </el-result>
    <el-form v-else ref="form" class="auth-form" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <el-form-item label="用户名或注册邮箱" prop="identifier"><el-input v-model="form.identifier" autocomplete="username" clearable placeholder="请输入用户名或邮箱" @keyup.enter="submit" /></el-form-item>
      <el-button class="auth-btn" type="primary" :loading="loading" native-type="submit">发送重置邮件</el-button>
      <p class="auth-hint">如果该邮箱已注册，系统会发送密码重置链接。</p>
      <router-link v-if="breakGlassRecoveryEnabled" class="recovery-link" to="/admin-recovery">超级管理员没有恢复邮箱？</router-link>
    </el-form>
  </auth-page-shell>
</template>

<script>
import AuthPageShell from '@/components/auth/AuthPageShell.vue'
import { getConsoleAuthConfig, requestConsolePasswordRecovery } from '@/api/auth'

export default {
  name: 'ForgotPassword',
  components: { AuthPageShell },
  data() {
    return { loading: false, sent: false, breakGlassRecoveryEnabled: false, form: { identifier: '' }, rules: { identifier: [{ required: true, message: '请输入用户名或邮箱', trigger: 'blur' }] } }
  },
  async created() {
    try {
      const response = await getConsoleAuthConfig()
      this.breakGlassRecoveryEnabled = response.data?.breakGlassRecoveryEnabled === true
    } catch (e) { this.breakGlassRecoveryEnabled = false }
  },
  methods: {
    submit() {
      this.$refs.form.validate(async valid => {
        if (!valid) return
        this.loading = true
        try { await requestConsolePasswordRecovery(this.form); this.sent = true } finally { this.loading = false }
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.auth-back { margin-bottom: 18px; font-size: 13px; }
.auth-back a { color: var(--el-color-primary); text-decoration: none; }
.auth-form { margin-top: 20px; }
.auth-btn { width: 100%; height: 44px; margin-top: 6px; border-radius: 12px; }
.auth-hint { margin: 16px 0 0; color: var(--tianshu-text-tertiary); font-size: 12px; line-height: 1.6; text-align: center; }
.recovery-link { display: block; margin-top: 12px; color: var(--el-color-primary); font-size: 12px; text-align: center; text-decoration: none; }
:deep(.auth-form .el-input__wrapper) { min-height: 44px; border-radius: 12px; background: var(--tianshu-bg-elevated); }
</style>
