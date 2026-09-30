<template>
  <div class="login-page">
    <div class="login-bg" aria-hidden="true">
      <div class="login-bg__mesh" />
      <div class="login-bg__orb login-bg__orb--a" />
      <div class="login-bg__orb login-bg__orb--b" />
    </div>

    <div class="login-shell">
      <div class="login-card" role="main">
        <header class="login-brand">
          <div class="login-brand__icon" aria-hidden="true">
            <img
              :src="brandLogoUrl"
              alt="logo"
              class="login-logo-img"
            />
          </div>
          <div class="login-brand__text">
            <h1 class="login-brand__title">天枢决策引擎</h1>
            <p class="login-brand__subtitle">天工开物，枢衡定策</p>
          </div>
        </header>

        <el-form
          ref="form"
          class="login-form"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="submit"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              autocomplete="username"
              clearable
              placeholder="请输入用户名"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              autocomplete="current-password"
              show-password
              placeholder="请输入密码"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-form-item class="login-form__actions">
            <el-button
              type="primary"
              class="login-btn"
              :loading="loading"
              native-type="submit"
            >
              {{ loading ? '登录中…' : '登录' }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script>
import { consoleLogin } from '@/api/auth'

export default {
  name: 'Login',
  data() {
    return {
      brandLogoUrl: `${import.meta.env.BASE_URL || './'}images/hengshucredit_animated.svg`,
      loading: false,
      form: { username: '', password: '' },
      rules: {
        username: [
          { required: true, message: '请输入用户名', trigger: 'blur' },
        ],
        password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
      },
    }
  },
  methods: {
    /**
     * 校验表单并调用登录接口，成功后跳回 redirect 或首页。
     */
    submit() {
      this.$refs.form.validate(async (valid) => {
        if (!valid) return
        this.loading = true
        try {
          await consoleLogin({
            username: this.form.username,
            password: this.form.password,
          })
          const redirect = this.$route.query.redirect || '/project'
          this.$router.replace(redirect)
        } finally {
          this.loading = false
        }
      })
    },
  },
}
</script>

<style lang="scss" scoped>
$login-muted: var(--tianshu-text-tertiary);

.login-page {
  position: relative;
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto,
    sans-serif;
  overflow-x: hidden;
}
.login-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  background:
    radial-gradient(
      circle at 8% 12%,
      rgba(var(--el-color-primary-rgb), 0.14),
      transparent 38%
    ),
    radial-gradient(
      circle at 92% 88%,
      rgba(var(--tianshu-color-secondary-rgb), 0.1),
      transparent 42%
    ),
    linear-gradient(145deg, var(--tianshu-bg-page), var(--tianshu-bg-workspace));
}
.login-bg__mesh {
  position: absolute;
  inset: 0;
  opacity: 0.3;
  background-image: radial-gradient(
      circle at 20% 30%,
      rgba(var(--el-color-primary-rgb), 0.16) 0%,
      transparent 45%
    ),
    radial-gradient(
      circle at 80% 70%,
      rgba(var(--el-color-primary-rgb), 0.09) 0%,
      transparent 40%
    ),
    linear-gradient(rgba(var(--el-color-primary-rgb), 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(var(--el-color-primary-rgb), 0.04) 1px, transparent 1px);
  background-size: 100% 100%, 100% 100%, 48px 48px, 48px 48px;
  animation: mesh-drift 28s ease-in-out infinite alternate;
}
.login-bg__orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(64px);
  opacity: 0.55;
  animation: orb-float 18s ease-in-out infinite;
}
.login-bg__orb--a {
  width: min(42vw, 360px);
  height: min(42vw, 360px);
  top: -8%;
  left: -5%;
  background: rgba(var(--el-color-primary-rgb), 0.24);
  animation-delay: 0s;
}
.login-bg__orb--b {
  width: min(50vw, 420px);
  height: min(50vw, 420px);
  bottom: -12%;
  right: -8%;
  background: rgba(var(--el-color-primary-rgb), 0.14);
  animation-delay: -6s;
}
@keyframes mesh-drift {
  from {
    transform: scale(1) translate(0, 0);
  }
  to {
    transform: scale(1.04) translate(-1%, 1%);
  }
}
@keyframes orb-float {
  0%,
  100% {
    transform: translate(0, 0) scale(1);
  }
  50% {
    transform: translate(2%, 3%) scale(1.05);
  }
}
@media (prefers-reduced-motion: reduce) {
  .login-bg__mesh,
  .login-bg__orb {
    animation: none !important;
  }
  .login-card {
    animation: none !important;
  }
}
.login-shell {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 420px;
}
.login-card {
  padding: 36px 32px 28px;
  border-radius: 20px;
  background: var(--tianshu-bg-surface);
  border: 1px solid var(--tianshu-border-subtle);
  box-shadow: var(--tianshu-shadow-large);
  -webkit-backdrop-filter: blur(18px);
  backdrop-filter: blur(18px);
  animation: card-enter 0.5s cubic-bezier(0.22, 1, 0.36, 1) both;
}
@keyframes card-enter {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.login-brand {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--tianshu-border-subtle);
}
.login-brand__icon {
  flex-shrink: 0;
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: rgba(var(--el-color-primary-rgb), 0.12);
  border: 1px solid rgba(var(--el-color-primary-rgb), 0.24);
  overflow: hidden;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}
.login-logo-img {
  width: 40px;
  height: 40px;
  object-fit: contain;
}
.login-card:hover .login-brand__icon {
  box-shadow: 0 8px 20px var(--tianshu-primary-shadow);
}
.login-brand__title {
  margin: 0;
  font-size: 1.375rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--tianshu-text-primary);
  line-height: 1.25;
}
.login-brand__subtitle {
  margin: 6px 0 0;
  font-size: 0.8125rem;
  font-weight: 500;
  color: var(--tianshu-text-tertiary);
  line-height: 1.4;
}
.login-form {
  margin-top: 4px;
}
.login-form__actions {
  margin-bottom: 0;
  margin-top: 8px;
}
.login-footnote {
  margin: 20px 0 0;
  text-align: center;
  font-size: 12px;
  color: $login-muted;
  line-height: 1.5;
}
:deep(.login-form .el-form-item) {
  margin-bottom: 20px;
}
:deep(.login-form .el-form-item__label) {
  padding: 0 0 8px;
  line-height: 1.3;
  font-weight: 600;
  font-size: 13px;
  color: var(--tianshu-text-secondary);
}
:deep(.login-form .el-input) {
  --el-input-text-color: var(--tianshu-text-primary);
  --el-input-bg-color: var(--tianshu-bg-elevated);
  --el-input-border-color: var(--tianshu-border);
  --el-input-hover-border-color: var(--el-color-primary-light-3);
  --el-input-focus-border-color: var(--el-color-primary);
  --el-input-placeholder-color: var(--tianshu-text-tertiary);
  --el-input-icon-color: var(--tianshu-text-tertiary);
}
:deep(.login-form .el-input__wrapper) {
  min-height: 44px;
  padding-inline: 12px;
  border-radius: 12px;
  background: var(--el-input-bg-color);
}
:deep(.login-form .el-input__inner) {
  line-height: 1.4;
  font-size: 15px;
  color: var(--el-input-text-color);
  caret-color: var(--el-input-focus-border-color);
}
:deep(.login-form .el-input__inner:-webkit-autofill),
:deep(.login-form .el-input__inner:-webkit-autofill:hover),
:deep(.login-form .el-input__inner:-webkit-autofill:focus) {
  -webkit-text-fill-color: var(--el-input-text-color);
  caret-color: var(--el-input-focus-border-color);
  -webkit-box-shadow: 0 0 0 1000px var(--el-input-bg-color) inset;
  transition: background-color 9999s ease-out 0s;
}
.login-btn {
  width: 100%;
  height: 46px !important;
  margin-top: 4px;
  padding: 0 !important;
  font-size: 15px !important;
  font-weight: 600 !important;
  letter-spacing: 0.02em;
  border-radius: 12px !important;
  color: var(--tianshu-brand-foreground) !important;
  background: var(--tianshu-brand-background) !important;
  border-color: var(--el-color-primary) !important;
  cursor: pointer;
  transition: filter 0.2s ease, box-shadow 0.2s ease,
    transform 0.2s ease;
}
.login-btn.el-button--primary.is-loading:disabled {
  opacity: 0.88;
  background: var(--tianshu-brand-background) !important;
  border-color: var(--el-color-primary) !important;
}
.login-btn.is-loading::before {
  background-color: transparent;
}
.login-btn:not(.is-loading):hover {
  background: var(--tianshu-brand-background) !important;
  border-color: var(--el-color-primary-dark-1) !important;
  box-shadow: 0 10px 28px var(--tianshu-primary-shadow);
  filter: brightness(1.08);
}
.login-btn:not(.is-loading):active {
  transform: translateY(1px);
}
@media (max-width: 480px) {
  .login-card {
    padding: 28px 22px 22px;
    border-radius: 16px;
  }
  .login-brand {
    flex-direction: column;
    align-items: flex-start;
    text-align: left;
  }
  .login-brand__icon {
    width: 48px;
    height: 48px;
  }
}
@media (min-width: 768px) {
  .login-shell {
    max-width: 440px;
  }
  .login-card {
    padding: 40px 40px 32px;
  }
}
</style>
