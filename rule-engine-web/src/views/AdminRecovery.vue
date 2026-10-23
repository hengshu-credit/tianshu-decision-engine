<template>
  <auth-page-shell title="超级管理员恢复" subtitle="忘记账号和密码时，通过服务器恢复码重新设置">
    <div class="auth-back"><router-link to="/login">← 返回登录</router-link></div>
    <el-alert title="请联系服务器运维生成一次性恢复码，30分钟内有效。无需原用户名；恢复保留同一账户ID及引擎全部内容，不会重新初始化系统。" type="info" :closable="false" show-icon />
    <el-form ref="form" class="auth-form" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <el-form-item label="一次性恢复码" prop="recoveryKey"><el-input v-model="form.recoveryKey" type="password" show-password autocomplete="off" /></el-form-item>
      <el-form-item label="新的超级管理员用户名" prop="username"><el-input v-model="form.username" autocomplete="username" clearable placeholder="设置新的用户名，无需填写原账号" /></el-form-item>
      <el-form-item label="新密码" prop="password"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" /></el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" /></el-form-item>
      <el-button class="auth-btn" type="primary" :loading="loading" native-type="submit">重置超级管理员账号和密码</el-button>
    </el-form>
  </auth-page-shell>
</template>

<script>
import { ElMessage } from 'element-plus'
import AuthPageShell from '@/components/auth/AuthPageShell.vue'
import { emergencyResetConsolePassword } from '@/api/auth'

export default {
  name: 'AdminRecovery',
  components: { AuthPageShell },
  data() {
    const validateConfirm = (rule, value, callback) => value !== this.form.password ? callback(new Error('两次输入的密码不一致')) : callback()
    return { loading: false, form: { username: '', recoveryKey: '', password: '', confirmPassword: '' }, rules: { username: [{ required: true, message: '请设置新用户名', trigger: 'blur' }, { min: 3, max: 64, message: '用户名长度必须为3到64位', trigger: 'blur' }], recoveryKey: [{ required: true, message: '请输入一次性恢复码', trigger: 'blur' }], password: [{ required: true, message: '请输入新密码', trigger: 'blur' }, { min: 8, message: '密码不能少于8位', trigger: 'blur' }], confirmPassword: [{ required: true, message: '请确认密码', trigger: 'blur' }, { validator: validateConfirm, trigger: 'blur' }] } }
  },
  methods: {
    submit() {
      this.$refs.form.validate(async valid => {
        if (!valid) return
        this.loading = true
        try { await emergencyResetConsolePassword(this.form); ElMessage.success('账号和密码已重置，引擎内容保持不变，请重新登录'); this.$router.replace('/login') } finally { this.loading = false }
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
:deep(.auth-form .el-input__wrapper) { min-height: 44px; border-radius: 12px; background: var(--tianshu-bg-elevated); }
</style>
