<template>
  <div
    v-if="visible"
    class="flow-node-add-menu"
    :style="{ left: x + 'px', top: y + 'px' }"
    @mousedown.stop
    @click.stop
  >
    <div class="flow-node-add-menu__title">新增下一个节点</div>
    <button
      v-for="option in options"
      :key="option.type"
      type="button"
      class="flow-node-add-menu__item"
      @click="$emit('select', option)"
    >
      <span
        class="flow-node-add-menu__dot"
        :style="{ backgroundColor: option.color }"
      />
      <app-icon :name="option.icon" />
      <span>{{ option.label }}</span>
    </button>
  </div>
</template>

<script>
import { $emit } from '../../utils/gogocodeTransfer'
export default {
  name: 'FlowNodeAddMenu',
  props: {
    visible: { type: Boolean, default: false },
    x: { type: Number, default: 0 },
    y: { type: Number, default: 0 },
    options: { type: Array, default: () => [] },
  },
  mounted() {
    window.addEventListener('keydown', this.onKeydown)
  },
  beforeUnmount() {
    window.removeEventListener('keydown', this.onKeydown)
  },
  methods: {
    onKeydown(event) {
      if (this.visible && event.key === 'Escape') $emit(this, 'close')
    },
  },
  emits: ['select', 'close'],
}
</script>

<style lang="scss" scoped>
.flow-node-add-menu {
  position: absolute;
  z-index: 30;
  width: 176px;
  padding: 6px;
  background: #ffffff;
  border: 1px solid #d9e0f2;
  border-radius: 6px;
  box-shadow: 0 8px 24px rgba(38, 57, 233, 0.16);
}
.flow-node-add-menu__title {
  padding: 5px 8px 7px;
  color: #8c8c8c;
  font-size: 12px;
}
.flow-node-add-menu__item {
  display: flex;
  align-items: center;
  width: 100%;
  height: 34px;
  padding: 0 9px;
  color: #262626;
  font-size: 13px;
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: 4px;
  cursor: pointer;
  &:hover,
  &:focus {
    color: #2639e9;
    background: #f0f3ff;
    outline: 0;
  }

  i {
    width: 22px;
  }
}
.flow-node-add-menu__dot {
  width: 7px;
  height: 7px;
  margin-right: 8px;
  border-radius: 50%;
}
</style>
