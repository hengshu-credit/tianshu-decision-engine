<template>
    <div class="sidebar-brand" :class="{ 'is-compact': compact }">
      <img
        :src="brand.logoUrl"
        :alt="brand.systemName"
        class="sidebar-brand__logo"
      />
      <div v-if="!compact" class="brand-copy">
        <strong>{{ brand.systemName }}</strong>
        <span v-if="brand.subtitle">{{ brand.subtitle }}</span>
      </div>
    </div>
</template>

<script>
import { systemBrand } from '@/system/systemBrand'
export default {
  name: 'NavigationBrand',
  props: { brand: { type: Object, default: () => systemBrand }, compact: Boolean },
}
</script>

<style scoped lang="scss">
.sidebar-brand {
  position: relative;
  display: flex;
  flex: none;
  align-items: center;
  min-height: 60px;
  padding: 0 16px;
  overflow: hidden;
  border-bottom: 1px solid var(--tianshu-sidebar-border, rgba(255, 255, 255, 0.08));
}
.sidebar-brand__logo {
  flex: none;
  width: 36px;
  height: 36px;
  object-fit: contain;
}
.brand-copy {
  display: flex;
  min-width: 0;
  margin-left: 10px;
  flex-direction: column;
  strong {
    overflow: hidden;
    color: var(--tianshu-sidebar-text-active, #ffffff);
    font-size: 16px;
    font-weight: 600;
    line-height: 22px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    overflow: hidden;
    margin-top: 2px;
    color: #22d3ee;
    font-size: 12px;
    line-height: 16px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.sidebar-brand.is-compact { padding: 0; justify-content: center; }
:global([data-sidebar-theme='light']) .brand-copy strong { color: var(--tianshu-text-primary, #172033); }
</style>
