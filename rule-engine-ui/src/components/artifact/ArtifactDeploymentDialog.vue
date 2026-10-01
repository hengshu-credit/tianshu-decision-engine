<template>
  <el-dialog :model-value="modelValue" title="跨环境部署决策制品" width="680px" @update:model-value="$emit('update:modelValue', $event)">
    <el-alert title="目标资源按稳定 ID 关联；同编码候选会标记推荐，没有候选时请手工搜索或填写目标 ID。" type="info" :closable="false" show-icon />
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
      <transfer-association-panel
        :associations="bindingAssociations"
        :resource-bindings="form.bindings"
        :field-bindings="{}"
        @update:resource-bindings="form.bindings = $event"
        @search="searchBindingCandidates"
      />
      <el-form-item label="部署备注"><el-input v-model="form.comment" type="textarea" :rows="2" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button data-testid="deploy" type="primary" :disabled="!ready" @click="submit">确认部署</el-button>
    </template>
  </el-dialog>
</template>

<script>
import { getArtifactDeploymentOptions } from '@/api/artifact'
import { listTransferResources } from '@/api/transfer'
import TransferAssociationPanel from '@/components/transfer/TransferAssociationPanel.vue'

export default {
  name: 'ArtifactDeploymentDialog',
  components: { TransferAssociationPanel },
  props: {
    modelValue: { type: Boolean, default: false },
    artifactId: { type: Number, required: true },
    bindingComponentIds: { type: Array, default: () => [] }
  },
  emits: ['update:modelValue', 'deploy'],
  data() {
    return {
      targetMode: 'existing',
      options: { requirements: [] },
      optionsLoading: false,
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
    bindingItems() {
      if (this.options.requirements && this.options.requirements.length) return this.options.requirements
      return this.bindingComponentIds.map((componentId) => ({ componentId, candidates: [] }))
    },
    bindingAssociations() {
      return this.bindingItems.map((item) => ({
        ...item,
        referenceKey: item.componentId,
        targetKey: item.componentId,
        targetResourceType: item.resourceType || 'RESOURCE',
        targetCode: item.sourceCode,
        targetName: item.sourceName,
      }))
    },
    ready() {
      const targetReady = this.targetMode === 'existing'
        ? Boolean(this.form.targetDefinitionId)
        : Boolean(this.form.targetProjectId && this.form.targetRuleCode.trim()
            && this.form.targetRuleName.trim() && this.form.targetModelType.trim())
      return Boolean(this.form.environmentCode.trim() && targetReady
        && this.bindingItems.every((item) => this.form.bindings[item.componentId]))
    }
  },
  watch: {
    modelValue(value) {
      if (value) this.loadOptions()
    },
    artifactId() {
      if (this.modelValue) this.loadOptions()
    },
    'form.targetProjectId'() {
      if (this.modelValue) this.loadOptions()
    },
  },
  mounted() {
    if (this.modelValue) this.loadOptions()
  },
  methods: {
    async loadOptions() {
      this.optionsLoading = true
      try {
        const response = await getArtifactDeploymentOptions(this.artifactId, this.form.targetProjectId)
        const data = response.data || {}
        this.options = { requirements: data.requirements || [] }
      } catch (error) {
        this.options = { requirements: [] }
      } finally {
        this.optionsLoading = false
      }
    },
    async searchBindingCandidates(association) {
      try {
        const response = await listTransferResources({
          nodeType: ({ DATABASE: 'DB', DB_DATASOURCE: 'DB', EXTERNAL_API: 'API', DATA_OBJECT_ROOT: 'DATA_OBJECT', LIST_LIBRARY: 'LIST' }[association.targetResourceType] || association.targetResourceType),
          keyword: association.targetCode || '',
          pageNum: 1,
          pageSize: 50,
        })
        const target = this.options.requirements.find((item) => item.componentId === association.componentId)
        if (target) {
          const rows = response.data?.records || []
          target.candidates = rows.map((item) => ({
            id: item.id,
            code: item.code || item.resourceCode,
            name: item.displayName || item.name,
            projectId: item.projectId,
          }))
        }
      } catch (error) {
        this.$message.error(error.message || '加载目标资源候选失败')
      }
    },
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
.binding-editor { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.binding-select { min-width: 280px; }
.binding-source-code { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
