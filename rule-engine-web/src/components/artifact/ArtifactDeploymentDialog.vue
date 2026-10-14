<template>
  <el-dialog :model-value="modelValue" title="跨环境部署决策制品" width="680px" @update:model-value="$emit('update:modelValue', $event)">
    <el-alert title="按名称或编码搜索并选择部署目标；同编码仅作推荐，不会自动关联。部署会将制品发布到所选规则。" type="info" :closable="false" show-icon />
    <div v-if="optionsError" class="deployment-options-error">
      <el-alert data-testid="artifact-options-error" :title="optionsError" type="error" :closable="false" show-icon />
      <el-button data-testid="retry-artifact-options" size="small" :loading="optionsLoading" @click="loadOptions">重新读取制品</el-button>
    </div>
    <el-form v-if="modelValue" label-width="130px" class="deployment-form">
      <el-form-item label="环境编码"><el-input v-model="form.environmentCode" /></el-form-item>
      <el-form-item label="部署方式">
        <el-radio-group v-model="targetMode">
          <el-radio-button value="existing">部署到现有规则</el-radio-button>
          <el-radio-button value="create">创建目标规则</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="目标作用范围">
        <el-radio-group v-model="targetScope" data-testid="artifact-target-scope">
          <el-radio-button value="PROJECT">项目规则</el-radio-button>
          <el-radio-button value="GLOBAL">全局规则</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="targetScope === 'PROJECT'" label="目标项目">
        <remote-filter-select
          v-model:value="form.targetProjectId"
          data-testid="artifact-target-project"
          aria-label="搜索目标项目"
          placeholder="按项目名称或编码搜索"
          :fetch-options="fetchProjectOptions"
          option-value-key="id"
          class="deployment-target-select"
          @load-error="projectOptionsError = '项目候选加载失败，请重新展开或搜索重试。'"
        />
        <span v-if="projectOptionsError" role="alert" class="deployment-picker-error">{{ projectOptionsError }}</span>
      </el-form-item>
      <el-form-item v-if="targetMode === 'existing'" label="目标规则">
        <remote-filter-select
          :key="'rule-' + pickerGeneration + '-' + packageModelType"
          v-model:value="form.targetDefinitionId"
          data-testid="artifact-target-rule"
          aria-label="搜索目标规则"
          :placeholder="targetContextReady ? '按规则名称或编码搜索' : '请先选择目标项目'"
          :disabled="!targetContextReady || optionsLoading || !packageModelType"
          :fetch-options="fetchRuleOptions"
          option-value-key="id"
          class="deployment-target-select"
          @load-error="ruleOptionsError = '规则候选加载失败，请重新展开或搜索重试。'"
        />
        <span v-if="ruleOptionsError" role="alert" class="deployment-picker-error">{{ ruleOptionsError }}</span>
      </el-form-item>
      <template v-else>
        <el-form-item label="目标规则编码"><el-input v-model="form.targetRuleCode" /></el-form-item>
        <el-form-item label="目标规则名称"><el-input v-model="form.targetRuleName" /></el-form-item>
      </template>
      <el-form-item label="目标模型类型">
        <span data-testid="artifact-model-type">{{ optionsLoading ? '正在读取制品类型…' : packageModelLabel || '暂未识别' }}</span>
        <span class="deployment-type-hint">由制品确定，目标规则需使用相同类型。</span>
      </el-form-item>
      <transfer-association-panel
        v-if="targetContextReady && !optionsLoading && !optionsError"
        :key="'bindings-' + pickerGeneration"
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
import { listProjects } from '@/api/project'
import { listDefinitions } from '@/api/definition'
import { RULE_MODEL_LABELS } from '@/utils/ruleCallConfig'
import TransferAssociationPanel from '@/components/transfer/TransferAssociationPanel.vue'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'

function emptyForm() {
  return { environmentCode: '', targetDefinitionId: null, targetProjectId: null,
    targetRuleCode: '', targetRuleName: '', comment: '', bindings: {} }
}

function hasTargetId(value) {
  return typeof value === 'number' ? Number.isSafeInteger(value) && value > 0
    : typeof value === 'string' && /^[1-9]\d*$/.test(value)
}

export default {
  name: 'ArtifactDeploymentDialog',
  components: { TransferAssociationPanel, RemoteFilterSelect },
  props: {
    modelValue: { type: Boolean, default: false },
    artifactId: { type: [String, Number], required: true },
    bindingComponentIds: { type: Array, default: () => [] }
  },
  emits: ['update:modelValue', 'deploy'],
  data() {
    return {
      targetMode: 'existing',
      targetScope: 'PROJECT',
      pickerGeneration: 0,
      projectOptionsError: '',
      ruleOptionsError: '',
      bindingSearchRequests: {},
      options: { requirements: [], modelType: '' },
      optionsLoading: false,
      optionsError: '',
      optionsRequestId: 0,
      form: emptyForm()
    }
  },
  computed: {
    effectiveTargetProjectId() { return this.targetScope === 'GLOBAL' ? 0 : this.form.targetProjectId },
    targetContextReady() { return this.targetScope === 'GLOBAL' || hasTargetId(this.form.targetProjectId) },
    targetContextKey() { return [this.artifactId, this.targetScope, this.effectiveTargetProjectId, this.targetMode].join(':') },
    packageModelType() {
      const type = this.options.modelType
      return typeof type === 'string' && Object.hasOwn(RULE_MODEL_LABELS, type) ? type : ''
    },
    packageModelLabel() { return RULE_MODEL_LABELS[this.packageModelType] || '' },
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
        ? hasTargetId(this.form.targetDefinitionId)
        : Boolean(this.form.targetRuleCode.trim()
            && this.form.targetRuleName.trim())
      return Boolean(this.modelValue && this.targetContextReady && !this.optionsLoading && !this.optionsError && this.packageModelType
        && this.form.environmentCode.trim() && targetReady
        && this.bindingItems.every((item) => this.form.bindings[item.componentId]))
    }
  },
  watch: {
    modelValue: {
      flush: 'sync',
      handler(value) {
        if (value) this.loadOptions()
        else {
          this.form = emptyForm()
          this.targetMode = 'existing'
          this.targetScope = 'PROJECT'
          this.projectOptionsError = ''
          this.ruleOptionsError = ''
          this.pickerGeneration++
          this.invalidateOptions()
        }
      },
    },
    targetScope: {
      flush: 'sync',
      handler() { this.form.targetProjectId = null },
    },
    targetContextKey: {
      flush: 'sync',
      handler() {
        this.form.targetDefinitionId = null
        this.form.bindings = {}
        this.projectOptionsError = ''
        this.ruleOptionsError = ''
        this.pickerGeneration++
        this.invalidateOptions()
        if (this.modelValue) this.loadOptions()
      },
    },
    'form.targetDefinitionId': {
      flush: 'sync',
      handler() { this.form.bindings = {} },
    },
  },
  mounted() {
    if (this.modelValue) this.loadOptions()
  },
  beforeUnmount() { this.invalidateOptions() },
  methods: {
    invalidateOptions() {
      this.optionsRequestId += 1
      this.options = { requirements: [], modelType: '' }
      this.optionsError = ''
      this.optionsLoading = false
      this.bindingSearchRequests = {}
    },
    async fetchProjectOptions({ query, pageNum, pageSize }) {
      const context = this.targetContextKey
      const generation = this.pickerGeneration
      this.projectOptionsError = ''
      const response = await listProjects({ keyword: query, pageNum, pageSize })
      if (!this.modelValue || context !== this.targetContextKey || generation !== this.pickerGeneration) return { records: [], total: 0 }
      const data = response?.data || response || {}
      return { ...data, records: (data.records || []).filter(project => hasTargetId(project.id) && String(project.status) !== '-1')
        .map(project => ({ id: project.id, label: `${project.projectName || project.projectCode} / ${project.projectCode || ''}` })) }
    },
    async fetchRuleOptions({ query, pageNum, pageSize }) {
      if (!this.modelValue || !this.targetContextReady || !this.packageModelType) return { records: [], total: 0 }
      const context = this.targetContextKey
      const generation = this.pickerGeneration
      const modelType = this.packageModelType
      const scope = this.targetScope
      const projectId = this.effectiveTargetProjectId
      this.ruleOptionsError = ''
      const response = await listDefinitions({ keyword: query, pageNum, pageSize, modelType, scope, projectId })
      if (!this.modelValue || context !== this.targetContextKey || generation !== this.pickerGeneration || modelType !== this.packageModelType) return { records: [], total: 0 }
      const data = response?.data || response || {}
      return { ...data, records: (data.records || []).filter(rule => hasTargetId(rule.id) && rule.modelType === modelType
        && String(rule.status) !== '-1' && rule.scope === scope && String(rule.projectId) === String(projectId))
        .map(rule => ({ id: rule.id, label: `${rule.ruleName || rule.ruleCode} / ${rule.ruleCode || ''}` })) }
    },
    async loadOptions() {
      const requestId = ++this.optionsRequestId
      const artifactId = this.artifactId
      const projectId = this.effectiveTargetProjectId
      const context = this.targetContextKey
      this.options = { requirements: [], modelType: '' }
      this.optionsError = ''
      this.optionsLoading = true
      try {
        const response = await getArtifactDeploymentOptions(artifactId, projectId)
        if (!this.modelValue || requestId !== this.optionsRequestId || artifactId !== this.artifactId || context !== this.targetContextKey) return
        const data = response?.data || response || {}
        this.options = { requirements: data.requirements || [], modelType: data.modelType || '' }
        if (!this.packageModelType) this.optionsError = '制品缺少受支持的模型类型，请重新导出后导入。'
      } catch (error) {
        if (!this.modelValue || requestId !== this.optionsRequestId || artifactId !== this.artifactId || context !== this.targetContextKey) return
        this.optionsError = error.message || '读取制品类型失败，请重试。'
      } finally {
        if (requestId === this.optionsRequestId) this.optionsLoading = false
      }
    },
    async searchBindingCandidates(association) {
      if (!this.modelValue || this.optionsLoading) return
      const context = this.targetContextKey
      const optionsRequestId = this.optionsRequestId
      const componentId = association.componentId
      const requestId = (this.bindingSearchRequests[componentId] || 0) + 1
      this.bindingSearchRequests[componentId] = requestId
      const current = () => this.modelValue && context === this.targetContextKey && optionsRequestId === this.optionsRequestId
        && this.bindingSearchRequests[componentId] === requestId
      try {
        const response = await listTransferResources({
          nodeType: ({ DATABASE: 'DB', DB_DATASOURCE: 'DB', SFTP: 'SFTP', EXTERNAL_API: 'API', DATA_OBJECT_ROOT: 'DATA_OBJECT', LIST_LIBRARY: 'LIST' }[association.targetResourceType] || association.targetResourceType),
          keyword: association.targetCode || '',
          pageNum: 1,
          pageSize: 50,
        })
        if (!current()) return
        const target = this.options.requirements.find((item) => item.componentId === componentId)
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
        if (current()) this.$message.error(error.message || '加载目标资源候选失败')
      }
    },
    submit() {
      if (!this.ready) return
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
          targetProjectId: this.effectiveTargetProjectId,
          targetRuleCode: this.form.targetRuleCode,
          targetRuleName: this.form.targetRuleName,
          targetModelType: this.packageModelType
        })
      } else {
        this.$emit('deploy', {
          ...common,
          createRule: false,
          targetProjectId: this.effectiveTargetProjectId,
          targetDefinitionId: this.form.targetDefinitionId
        })
      }
    }
  }
}
</script>

<style scoped>
.deployment-form { margin-top: 16px; }
.deployment-options-error { margin-top: 12px; display: flex; gap: 8px; align-items: center; }
.deployment-type-hint { margin-left: 12px; color: var(--el-text-color-secondary); }
.deployment-target-select { width: 100%; }
.deployment-picker-error { color: var(--tianshu-danger-text); font-size: 12px; }
.binding-editor { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.binding-select { min-width: 280px; }
.binding-source-code { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
