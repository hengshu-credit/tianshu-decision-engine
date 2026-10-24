<template>
  <div class="auth-page">
    <div class="auth-bg" aria-hidden="true">
      <div class="auth-bg__mesh" />
      <div class="auth-bg__orb auth-bg__orb--a" />
      <div class="auth-bg__orb auth-bg__orb--b" />
    </div>
    <div class="auth-shell">
      <div class="auth-card" role="main">
        <header class="auth-brand">
          <div class="auth-brand__icon" aria-hidden="true">
            <img :src="brand.logoUrl" :alt="brand.systemName" class="auth-logo-img" />
          </div>
          <div>
            <h1>{{ title || brand.systemName }}</h1>
            <p v-if="subtitle || brand.subtitle">{{ subtitle || brand.subtitle }}</p>
          </div>
        </header>
        <slot />
      </div>
    </div>
  </div>
</template>

<script>
import { systemBrand } from '@/system/systemBrand'
export default {
  name: 'AuthPageShell',
  props: {
    title: { type: String, default: '' },
    subtitle: { type: String, default: '' },
  },
  data() {
    return {
      brand: systemBrand,
    }
  },
}
</script>

<style lang="scss" scoped>
.auth-page {
  position: relative;
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  color: var(--tianshu-text-primary);
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  overflow-x: hidden;
}
.auth-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  background:
    radial-gradient(circle at 8% 12%, rgba(var(--el-color-primary-rgb), 0.14), transparent 38%),
    radial-gradient(circle at 92% 88%, rgba(var(--tianshu-color-secondary-rgb), 0.1), transparent 42%),
    linear-gradient(145deg, var(--tianshu-bg-page), var(--tianshu-bg-workspace));
}
.auth-bg__mesh {
  position: absolute;
  inset: 0;
  opacity: 0.3;
  background-image:
    radial-gradient(circle at 20% 30%, rgba(var(--el-color-primary-rgb), 0.16) 0%, transparent 45%),
    radial-gradient(circle at 80% 70%, rgba(var(--el-color-primary-rgb), 0.09) 0%, transparent 40%),
    linear-gradient(rgba(var(--el-color-primary-rgb), 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(var(--el-color-primary-rgb), 0.04) 1px, transparent 1px);
  background-size: 100% 100%, 100% 100%, 48px 48px, 48px 48px;
}
.auth-bg__orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(64px);
  opacity: 0.55;
}
.auth-bg__orb--a {
  width: min(42vw, 360px);
  height: min(42vw, 360px);
  top: -8%;
  left: -5%;
  background: rgba(var(--el-color-primary-rgb), 0.24);
}
.auth-bg__orb--b {
  width: min(50vw, 420px);
  height: min(50vw, 420px);
  right: -8%;
  bottom: -12%;
  background: rgba(var(--el-color-primary-rgb), 0.14);
}
.auth-shell {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 520px;
}
.auth-card {
  padding: 36px 32px 28px;
  background: var(--tianshu-bg-surface);
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 20px;
  box-shadow: var(--tianshu-shadow-large);
  -webkit-backdrop-filter: blur(18px);
  backdrop-filter: blur(18px);
}
.auth-brand {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--tianshu-border-subtle);
}
.auth-brand__icon {
  flex: none;
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(var(--el-color-primary-rgb), 0.24);
  border-radius: 14px;
  background: rgba(var(--el-color-primary-rgb), 0.12);
}
.auth-logo-img {
  width: 40px;
  height: 40px;
  object-fit: contain;
}
.auth-brand h1 {
  margin: 0;
  color: var(--tianshu-text-primary);
  font-size: 1.375rem;
  font-weight: 700;
  line-height: 1.25;
}
.auth-brand p {
  margin: 6px 0 0;
  color: var(--tianshu-text-tertiary);
  font-size: 0.8125rem;
}
@media (max-width: 480px) {
  .auth-card { padding: 28px 22px 22px; border-radius: 16px; }
  .auth-brand { align-items: flex-start; flex-direction: column; }
}
</style>
