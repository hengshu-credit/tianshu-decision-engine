<template>
  <auth-page-shell title="设置新密码" subtitle="密码修改后需要重新登录">
    <div class="auth-back"><router-link to="/login">← 返回登录</router-link></div>
    <el-result v-if="completed" icon="success" title="密码已重置" sub-title="请使用新密码重新登录。">
      <template #extra><el-button type="primary" @click="$router.replace('/login')">返回登录</el-button></template>
    </el-result>
    <el-form v-else ref="form" class="auth-form" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <el-form-item label="新密码" prop="password"><el-input v-model="form.password" type="password" autocomplete="new-password" show-password placeholder="至少8位" /></el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" type="password" autocomplete="new-password" show-password placeholder="再次输入密码" /></el-form-item>
      <el-button class="auth-btn" type="primary" :loading="loading" native-type="submit">确认修改密码</el-button>
    </el-form>
  </auth-page-shell>
</template>

<script>
import { ElMessage } from 'element-plus'
import AuthPageShell from '@/components/auth/AuthPageShell.vue'
import { confirmConsolePasswordRecovery } from '@/api/auth'

export default {
  name: 'PasswordReset',
  components: { AuthPageShell },
  data() {
    const validateConfirm = (rule, value, callback) => value !== this.form.password ? callback(new Error('两次输入的密码不一致')) : callback()
    return { loading: false, completed: false, form: { password: '', confirmPassword: '' }, rules: { password: [{ required: true, message: '请输入新密码', trigger: 'blur' }, { min: 8, message: '密码不能少于8位', trigger: 'blur' }], confirmPassword: [{ required: true, message: '请确认密码', trigger: 'blur' }, { validator: validateConfirm, trigger: 'blur' }] } }
  },
  methods: {
    submit() {
      const token = this.$route.query.token
      if (!token) { ElMessage.error('重置链接无效'); return }
      this.$refs.form.validate(async valid => {
        if (!valid) return
        this.loading = true
        try { await confirmConsolePasswordRecovery({ token, password: this.form.password }); this.completed = true } finally { this.loading = false }
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
:deep(.auth-form .el-form-item__label) { color: var(--tianshu-text-secondary); }
:deep(.auth-form .el-input__wrapper) { min-height: 44px; border-radius: 12px; background: var(--tianshu-bg-elevated); }
</style>
