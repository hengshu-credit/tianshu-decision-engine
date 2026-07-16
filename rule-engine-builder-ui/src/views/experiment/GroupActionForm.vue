<template>
  <div class="group-action-form">
    <el-row :gutter="8">
      <el-col v-if="showType" :span="8">
        <div class="field-label">组类型</div>
        <el-select v-model="row.groupType" size="mini" style="width:100%;">
          <el-option label="冠军组" value="CHAMPION" />
          <el-option label="挑战组" value="CHALLENGER" />
        </el-select>
      </el-col>
      <el-col :span="showType ? 8 : 12">
        <div class="field-label">组编码</div>
        <el-input v-model="row.groupCode" size="mini" />
      </el-col>
      <el-col :span="showType ? 8 : 12">
        <div class="field-label">组名称</div>
        <el-input v-model="row.groupName" size="mini" />
      </el-col>
    </el-row>
    <el-row :gutter="8" class="action-row">
      <el-col :span="showRatio ? 14 : 24">
        <div class="field-label">执行规则</div>
        <rule-execution-selector
          :rule-id="row.ruleId"
          :rule-code="row.ruleCode"
          :rules="rulesForProject"
          @select="onRuleSelect"
        />
      </el-col>
      <el-col v-if="showRatio" :span="10">
        <div class="field-label">比例%</div>
        <el-input-number v-model="row.trafficRatio" :min="0" :max="100" :precision="2" size="mini" style="width:100%;" />
      </el-col>
    </el-row>
    <div class="action-footer">
      <el-switch v-if="showInvoke" v-model="row.invokeExternalSource" :active-value="1" :inactive-value="0" active-text="调用API外数" inactive-text="不调用API外数" />
      <el-button type="text" size="mini" class="btn-delete" @click="$emit('remove')">删除</el-button>
    </div>
  </div>
</template>

<script>
import RuleExecutionSelector from '@/components/common/RuleExecutionSelector.vue'

export default {
  name: 'GroupActionForm',
  components: { RuleExecutionSelector },
  props: {
    row: { type: Object, required: true },
    rulesForProject: { type: Array, default: () => [] },
    showType: { type: Boolean, default: false },
    showRatio: { type: Boolean, default: false },
    showInvoke: { type: Boolean, default: false }
  },
  methods: {
    onRuleSelect(rule) {
      this.$set(this.row, 'ruleId', rule ? rule.id : null)
      this.$set(this.row, 'ruleCode', rule ? rule.ruleCode : '')
    }
  }
}
</script>

<style lang="scss" scoped>
.group-action-form {
  .field-label {
    color: #334155;
    font-weight: 700;
    font-size: 12px;
    margin-bottom: 6px;
  }

  .action-row {
    margin-top: 8px;
  }

  .action-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
    margin-top: 10px;
  }

  .btn-delete {
    color: #f56c6c;
    margin-left: auto;
  }
}
</style>
