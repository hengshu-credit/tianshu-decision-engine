<template>
  <el-dialog
    title="添加结束节点"
    v-model="innerVisible"
    width="560px"
    append-to-body
    :close-on-click-modal="false"
  >
    <el-alert
      title="执行到结束节点后，该节点之后的内容不会再执行。请选择中断范围。"
      type="warning"
      :closable="false"
      show-icon
    />

    <el-radio-group v-model="scope" class="scope-options">
      <el-radio :value="currentRule" class="scope-option scope-current">
        <span class="scope-title"><i class="scope-dot" />跳出当前规则</span>
        <span class="scope-description"
          >当前规则立即执行结束并返回；作为子规则执行时，外层规则仍会继续。</span
        >
      </el-radio>
      <el-radio :value="allRules" class="scope-option scope-all">
        <span class="scope-title"><i class="scope-dot" />跳出整体规则</span>
        <span class="scope-description"
          >整条规则立即执行结束，根规则及所有后续规则都不会再执行；即使当前规则是子规则也会整体结束。</span
        >
      </el-radio>
    </el-radio-group>

    <template v-slot:footer>
      <el-button size="small" @click="close">取消</el-button>
      <el-button size="small" :type="confirmButtonType" @click="confirm">
        确认添加
      </el-button>
    </template>
  </el-dialog>
</template>

<script>
import { $emit } from '../../utils/gogocodeTransfer'
import {
  END_SCOPE_CURRENT_RULE,
  END_SCOPE_ALL_RULES,
} from '@/utils/endNodeScope'

export default {
  name: 'EndNodeScopeDialog',
  props: {
    visible: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      scope: END_SCOPE_CURRENT_RULE,
      currentRule: END_SCOPE_CURRENT_RULE,
      allRules: END_SCOPE_ALL_RULES,
    }
  },
  computed: {
    innerVisible: {
      get() {
        return this.visible
      },
      set(value) {
        $emit(this, 'update:visible', value)
      },
    },
    confirmButtonType() {
      return this.scope === END_SCOPE_ALL_RULES ? 'danger' : 'warning'
    },
  },
  watch: {
    visible(value) {
      if (value) this.scope = END_SCOPE_CURRENT_RULE
    },
  },
  methods: {
    close() {
      this.innerVisible = false
    },
    confirm() {
      $emit(this, 'confirm', this.scope)
      this.innerVisible = false
    },
  },
  emits: ['update:visible', 'confirm'],
}
</script>

<style lang="scss" scoped>
.scope-options {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
  margin-top: 16px;
}
.scope-option {
  box-sizing: border-box;
  width: 100%;
  margin: 0;
  padding: 16px;
  border: 2px solid #e4e7ed;
  border-radius: 8px;
  :deep(.el-radio__label) {
    display: inline-flex;
    flex-direction: column;
    gap: 8px;
    width: calc(100% - 28px);
    white-space: normal;
  }

  &.is-checked.scope-current {
    border-color: #d46b08;
    background: #fff7e6;
  }

  &.is-checked.scope-all {
    border-color: #cf1322;
    background: #fff1f0;
  }
}
.scope-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #303133;
  font-size: 14px;
  font-weight: 600;
}
.scope-description {
  color: #606266;
  font-size: 13px;
  line-height: 1.6;
}
.scope-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #fa8c16;
}
.scope-all .scope-dot {
  background: #ff4d4f;
}
</style>
