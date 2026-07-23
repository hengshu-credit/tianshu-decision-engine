<template>
  <el-dialog :model-value="modelValue" title="跨环境部署决策制品" width="680px" @update:model-value="$emit('update:modelValue', $event)">
    <el-alert title="目标资源只按数据库 ID 显式绑定，不会按名称或编码自动匹配。" type="warning" :closable="false" show-icon />
    <el-form label-width="130px" class="deployment-form">
      <el-form-item label="环境编码"><el-input v-model="form.environmentCode" /></el-form-item>
      <el-form-item label="部署方式">
        <el-radio-group v-model="targetMode">
          <el-radio-button value="existing">部署到现有规则</el-radio-button>
          <el-radio-button value="create">创建目标规则</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="targetMode === 'existing'" label="目标规则 ID">
        <el-input-number v-model="form.targetDefinitionId" :min="1" />
      </el-form-item>
      <template v-else>
        <el-form-item label="目标项目 ID"><el-input-number v-model="form.targetProjectId" :min="1" /></el-form-item>
        <el-form-item label="目标规则编码"><el-input v-model="form.targetRuleCode" /></el-form-item>
        <el-form-item label="目标规则名称"><el-input v-model="form.targetRuleName" /></el-form-item>
        <el-form-item label="目标模型类型"><el-input v-model="form.targetModelType" placeholder="如 TABLE、FLOW、SCRIPT" /></el-form-item>
      </template>
      <el-form-item v-for="componentId in bindingComponentIds" :key="componentId" :label="componentId">
        <el-input-number v-model="form.bindings[componentId]" :min="1" data-testid="binding-id" />
      </el-form-item>
      <el-form-item label="部署备注"><el-input v-model="form.comment" type="textarea" :rows="2" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button data-testid="deploy" type="primary" :disabled="!ready" @click="submit">确认部署</el-button>
    </template>
  </el-dialog>
</template>

<script>
export default {
  name: 'ArtifactDeploymentDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    artifactId: { type: Number, required: true },
    bindingComponentIds: { type: Array, default: () => [] }
  },
  emits: ['update:modelValue', 'deploy'],
  data() {
    return {
      targetMode: 'existing',
      form: {
        environmentCode: '',
        targetDefinitionId: null,
        targetProjectId: null,
        targetRuleCode: '',
        targetRuleName: '',
        targetModelType: '',
        comment: '',
        bindings: {}
      }
    }
  },
  computed: {
    ready() {
      const targetReady = this.targetMode === 'existing'
        ? Boolean(this.form.targetDefinitionId)
        : Boolean(this.form.targetProjectId && this.form.targetRuleCode.trim()
            && this.form.targetRuleName.trim() && this.form.targetModelType.trim())
      return Boolean(this.form.environmentCode.trim() && targetReady
        && this.bindingComponentIds.every((id) => this.form.bindings[id]))
    }
  },
  methods: {
    submit() {
      const common = {
        artifactId: this.artifactId,
        environmentCode: this.form.environmentCode,
        comment: this.form.comment,
        bindings: { ...this.form.bindings }
      }
      if (this.targetMode === 'create') {
        this.$emit('deploy', {
          ...common,
          createRule: true,
          targetProjectId: this.form.targetProjectId,
          targetRuleCode: this.form.targetRuleCode,
          targetRuleName: this.form.targetRuleName,
          targetModelType: this.form.targetModelType
        })
      } else {
        this.$emit('deploy', {
          ...common,
          createRule: false,
          targetDefinitionId: this.form.targetDefinitionId
        })
      }
    }
  }
}
</script>

<style scoped>
.deployment-form { margin-top: 16px; }
</style>
