<template>
  <auth-page-shell :title="initializationRequired ? '初始化超级管理员' : '注册账户'" :subtitle="initializationRequired ? '首次使用，请设置系统管理员账号和密码' : '验证邮箱后自动激活，登录后由管理员分配权限'">
    <el-result v-if="initialized" icon="success" title="超级管理员已创建" sub-title="账号已保存到系统，重启服务不会重新初始化。请使用刚设置的账号登录。">
      <template #extra><el-button type="primary" @click="$router.replace('/login')">前往登录</el-button></template>
    </el-result>
    <div v-else-if="verifying" class="auth-state">
      <el-icon class="is-loading" :size="28"><Loading /></el-icon>
      <p>正在验证邮箱，请稍候…</p>
    </div>
    <div v-else-if="verified" class="auth-state">
      <el-result icon="success" title="邮箱验证成功" sub-title="账户已激活，可以使用密码登录。">
        <template #extra><el-button type="primary" @click="$router.replace('/login')">返回登录</el-button></template>
      </el-result>
    </div>
    <el-result v-else-if="configLoaded && !initializationRequired && !registrationEnabled" icon="info" title="系统已初始化" sub-title="当前未开放普通账户注册，请登录或联系管理员分配账户。">
      <template #extra><el-button type="primary" @click="$router.replace('/login')">返回登录</el-button></template>
    </el-result>
    <template v-else>
      <div v-if="!initializationRequired" class="auth-back"><router-link to="/login">← 返回登录</router-link></div>
      <el-alert :title="initializationRequired ? '该账号将获得超级管理员权限。初始化完成后，此入口会自动关闭；无需配置邮箱或环境文件中的账号密码。' : '注册后默认没有业务权限，管理员赋权后才能访问引擎内容。'" type="info" :closable="false" show-icon />
      <el-form ref="form" class="auth-form" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" prop="username"><el-input v-model="form.username" autocomplete="username" clearable placeholder="3～64位用户名" /></el-form-item>
        <el-form-item label="显示名称" prop="displayName"><el-input v-model="form.displayName" clearable placeholder="请输入显示名称" /></el-form-item>
        <el-form-item v-if="!initializationRequired" label="注册邮箱" prop="email"><el-input v-model="form.email" type="email" autocomplete="email" clearable placeholder="用于验证和找回密码" /></el-form-item>
        <div class="form-grid">
          <el-form-item label="密码" prop="password"><el-input v-model="form.password" type="password" autocomplete="new-password" show-password placeholder="至少8位" /></el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword"><el-input v-model="form.confirmPassword" type="password" autocomplete="new-password" show-password placeholder="再次输入密码" /></el-form-item>
        </div>
        <el-button class="auth-btn" type="primary" :loading="loading" :disabled="!configLoaded" native-type="submit">{{ initializationRequired ? '创建超级管理员' : '提交注册' }}</el-button>
      </el-form>
      <div v-if="submitted" class="auth-success">验证邮件已发送，请查收 {{ submittedEmail }}。</div>
      <el-button v-if="!initializationRequired" class="auth-btn" :loading="resending" @click="resend">验证链接过期？重新发送</el-button>
    </template>
  </auth-page-shell>
</template>

<script>
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import AuthPageShell from '@/components/auth/AuthPageShell.vue'
import { consoleRegister, verifyConsoleRegistration, resendConsoleRegistration, getConsoleAuthConfig, initializeConsoleAdministrator } from '@/api/auth'

export default {
  name: 'Register',
  components: { AuthPageShell, Loading },
  data() {
    const validateConfirm = (rule, value, callback) => {
      if (value !== this.form.password) callback(new Error('两次输入的密码不一致'))
      else callback()
    }
    return {
      configLoaded: false,
      initializationRequired: false,
      registrationEnabled: false,
      initialized: false,
      verifying: false,
      verified: false,
      verificationSequence: 0,
      submitted: false,
      submittedEmail: '',
      loading: false,
      resending: false,
      form: { username: '', displayName: '', email: '', password: '', confirmPassword: '' },
      rules: {
        username: [{ required: true, message: '请输入用户名', trigger: 'blur' }, { min: 3, max: 64, message: '用户名长度必须为3到64位', trigger: 'blur' }],
        displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
        email: [{ required: true, message: '请输入注册邮箱', trigger: 'blur' }, { type: 'email', message: '请输入有效邮箱', trigger: 'blur' }],
        password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 8, message: '密码不能少于8位', trigger: 'blur' }],
        confirmPassword: [{ required: true, message: '请确认密码', trigger: 'blur' }, { validator: validateConfirm, trigger: 'blur' }],
      },
    }
  },
  async created() {
    try {
      const response = await getConsoleAuthConfig()
      this.initializationRequired = response.data?.initializationRequired === true
      this.registrationEnabled = response.data?.registrationEnabled === true
      this.configLoaded = true
    } catch { /* 配置请求失败时不允许提交，避免误走注册或初始化。 */ }
  },
  watch: { '$route.query.token': { immediate: true, handler(token) { this.verifyToken(token) } } },
  beforeUnmount() { this.verificationSequence++ },
  methods: {
    async verifyToken(token) {
      const sequence = ++this.verificationSequence
      this.verified = false
      this.verifying = Boolean(token)
      if (!token) return
      try {
        await verifyConsoleRegistration({ token })
        if (sequence === this.verificationSequence) this.verified = true
      } catch { /* 请求层显示无效或过期链接，用户可重新发送。 */ }
      finally { if (sequence === this.verificationSequence) this.verifying = false }
    },
    async resend() {
      const identifier = this.form.email.trim() || this.form.username.trim()
      if (!identifier) { ElMessage.warning('请先填写注册邮箱或用户名'); return }
      this.resending = true
      try {
        await resendConsoleRegistration({ identifier })
        ElMessage.success('如账户处于待验证状态，验证邮件已重新发送')
      } finally { this.resending = false }
    },
    submit() {
      if (!this.configLoaded) return
      this.$refs.form.validate(async valid => {
        if (!valid) return
        this.loading = true
        try {
          if (this.initializationRequired) {
            await initializeConsoleAdministrator({ username: this.form.username, displayName: this.form.displayName, password: this.form.password })
            this.form.password = ''; this.form.confirmPassword = ''
            this.initialized = true
            return
          }
          await consoleRegister({ ...this.form })
          this.submitted = true
          this.submittedEmail = this.form.email
          ElMessage.success('验证邮件已发送')
        } finally {
          this.loading = false
        }
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.auth-back { margin-bottom: 16px; font-size: 13px; }
.auth-back a { color: var(--el-color-primary); text-decoration: none; }
.auth-form { margin-top: 18px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.auth-btn { width: 100%; height: 44px; margin-top: 6px; border-radius: 12px; }
.auth-success { margin-top: 16px; padding: 12px; color: var(--el-color-success); background: var(--tianshu-success-bg); border-radius: 8px; text-align: center; font-size: 13px; }
.auth-state { text-align: center; color: var(--tianshu-text-secondary); }
.auth-state p { margin: 12px 0 0; }
:deep(.auth-form .el-form-item__label) { color: var(--tianshu-text-secondary); }
:deep(.auth-form .el-input__wrapper) { min-height: 44px; border-radius: 12px; background: var(--tianshu-bg-elevated); }
@media (max-width: 560px) { .form-grid { grid-template-columns: 1fr; gap: 0; } }
</style>
