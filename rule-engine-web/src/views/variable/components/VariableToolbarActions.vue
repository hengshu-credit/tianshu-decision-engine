<template>
  <div class="uiue-btn-bar variable-toolbar-actions">
    <div class="btn-right">
      <el-dropdown
        v-if="showBatchActions"
        v-permission="'approval:submit'"
        trigger="click"
        @command="$emit('import', $event)"
      >
        <el-button size="small" type="primary" :icon="ElIconUpload2"
          >批量导入
          <el-icon class="el-icon--right"><el-icon-arrow-down /></el-icon
        ></el-button>
        <template v-slot:dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="java-entity" :icon="ElIconDocument"
              >导入 Java 实体类</el-dropdown-item
            >
            <el-dropdown-item command="json-object" :icon="ElIconTickets"
              >导入 JSON 对象</el-dropdown-item
            >
            <el-dropdown-item command="ddl-table" :icon="ElIconSGrid"
              >导入 DDL 建表语句</el-dropdown-item
            >
            <el-dropdown-item command="java-const" :icon="ElIconCoin" divided
              >导入 Java 常量类</el-dropdown-item
            >
            <el-dropdown-item command="json-const" :icon="ElIconPriceTag"
              >导入 JSON 常量</el-dropdown-item
            >
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-button
        v-permission="'field:edit'"
        size="small"
        :icon="ElIconPlus"
        @click="$emit('create')"
        >{{ primaryCreateLabel }}</el-button
      >
      <el-button
        v-if="showBatchActions"
        v-permission="'field:edit'"
        size="small"
        :icon="ElIconVideoPlay"
        type="warning"
        :loading="validating"
        @click="$emit('validate')"
        >验证规则</el-button
      >
    </div>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import {
  ArrowDown as ElIconArrowDown,
  Coin as ElIconCoin,
  Document as ElIconDocument,
  Grid as ElIconSGrid,
  Plus as ElIconPlus,
  PriceTag as ElIconPriceTag,
  Tickets as ElIconTickets,
  Upload as ElIconUpload2,
  VideoPlay as ElIconVideoPlay,
} from '@element-plus/icons-vue'

export default {
  name: 'VariableToolbarActions',
  components: { ElIconArrowDown },
  props: {
    primaryCreateLabel: {
      type: String,
      required: true,
    },
    showBatchActions: {
      type: Boolean,
      default: true,
    },
    validating: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['import', 'create', 'validate'],
  data() {
    return {
      ElIconUpload2: markRaw(ElIconUpload2),
      ElIconDocument: markRaw(ElIconDocument),
      ElIconTickets: markRaw(ElIconTickets),
      ElIconSGrid: markRaw(ElIconSGrid),
      ElIconCoin: markRaw(ElIconCoin),
      ElIconPriceTag: markRaw(ElIconPriceTag),
      ElIconPlus: markRaw(ElIconPlus),
      ElIconVideoPlay: markRaw(ElIconVideoPlay),
    }
  },
}
</script>

<style scoped>
.variable-toolbar-actions {
  flex: 0 0 auto;
  margin: 0 0 0 auto;
}
</style>
