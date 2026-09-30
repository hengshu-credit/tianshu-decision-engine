<template>
  <section class="api-execution-editor" aria-label="统一外数链路配置">
    <el-alert v-if="config.__parseError" :closable="false" type="error" title="统一链路配置无法解析" :description="`请导出并修复 JSON 后重新导入：${config.__parseError}`" />
    <el-alert :closable="false" type="info" title="API 只组装请求和统一响应；变量与对象选择结果路径，并覆盖已开放的入参。" />
    <div><el-button @click="configImportVisible = true">导入链路配置</el-button><span class="field-help">可导入经过检查的请求字段、步骤和响应配置，导入后仍需审批生效。</span></div>
    <el-tabs v-model="tab" :class="{ 'embedded-tabs': embeddedTabs }">
      <el-tab-pane label="请求字段" name="request">
        <el-form-item label="默认传空策略"><el-select v-model="config.nullPolicy"><el-option label="不传空值" value="OMIT" /><el-option label="传 null" value="NULL" /><el-option label="传空字符串" value="EMPTY" /></el-select></el-form-item>
        <api-parameter-fields v-model:fields="config.requestFields" :vars="vars" :functions="functions" :modules="[]" />
        <el-button @click="addBranch('requestBranches')">添加请求条件分支</el-button>
        <el-card v-for="(branch, index) in config.requestBranches" :key="branch.id" shadow="never">
          <div class="panel-toolbar"><el-input v-model="branch.name" placeholder="分支名称" /><el-button link type="danger" @click="config.requestBranches.splice(index, 1)">删除分支</el-button></div>
          <response-condition-tree-editor operand-mode :functions="functions" :group="branch.condition" :path-options="[]" :vars="vars" />
          <api-parameter-fields v-model:fields="branch.requestFields" :vars="vars" :functions="functions" :modules="[]" />
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="响应结构" name="response">
        <p class="field-help">按顺序选择首个命中分支，空条件作为兜底。原始响应始终保留在 response.body，组装结果存入 body。</p>
        <el-button @click="addBranch('responseBranches')">添加响应条件分支</el-button>
        <el-card v-for="(branch, index) in config.responseBranches" :key="branch.id" shadow="never">
          <div class="panel-toolbar"><el-input v-model="branch.name" placeholder="分支名称" /><el-button @click="openSample(branch)">导入响应样例生成字段</el-button><el-button @click="previewResponse(branch)">预览响应组装</el-button><el-button link type="danger" @click="config.responseBranches.splice(index, 1)">删除分支</el-button></div>
          <response-condition-tree-editor operand-mode :functions="functions" :group="branch.condition" :path-options="responsePaths(branch)" />
          <el-radio-group v-model="branch.mode"><el-radio-button value="FIELDS">按字段组装</el-radio-button><el-radio-button value="VALUE">整体取值</el-radio-button></el-radio-group>
          <api-value-editor v-if="branch.mode === 'VALUE'" v-model:value="branch.value" :modules="responsePaths(branch)" :functions="functions" />
          <api-parameter-fields v-else v-model:fields="branch.outputFields" output :vars="[]" :functions="functions" :modules="responsePaths(branch)" />
          <el-collapse v-if="branch.sample"><el-collapse-item title="查看响应字段结构"><api-sample-structure :sample="branch.sample" /></el-collapse-item></el-collapse>
        </el-card>
        <pre v-if="responsePreview" class="response-preview">{{ responsePreview }}</pre>
      </el-tab-pane>
      <el-tab-pane v-if="asyncMode" label="多步链路" name="steps">
        <p class="field-help">可覆盖入参统一在“请求字段”声明，步骤中从“链路入参”选择。步骤 ID 创建后保持稳定。轮询仅重复当前步骤；回调地址可在提交步骤中从 callbacks.步骤ID.url 选择。总超时使用 API 基础配置。</p>
        <el-button @click="addStep('HTTP')">添加请求步骤</el-button><el-button @click="addStep('CALLBACK')">添加回调步骤</el-button>
        <el-card v-for="(step, index) in config.steps" :key="step.id" shadow="never">
          <div class="panel-toolbar"><strong>{{ index + 1 }} · {{ step.type === 'HTTP' ? '主动请求' : '等待回调' }}</strong><el-input v-model="step.name" placeholder="步骤名称" /><el-button :disabled="index === 0" @click="moveStep(index, -1)">上移</el-button><el-button :disabled="index === config.steps.length - 1" @click="moveStep(index, 1)">下移</el-button><el-button link type="danger" @click="config.steps.splice(index, 1)">删除</el-button></div>
          <div class="field-help">步骤引用：<code>steps.{{ step.id }}</code></div>
          <el-collapse><el-collapse-item title="执行条件（为空时执行）"><response-condition-tree-editor operand-mode :functions="functions" :group="step.when" :path-options="stepModules(index)" /></el-collapse-item></el-collapse>
          <template v-if="step.type === 'HTTP'">
            <el-form-item label="关联 API"><el-select v-model="step.apiConfigId" clearable filterable placeholder="可选，复用 API 配置"><el-option v-for="api in apis" :key="api.id" :value="api.id" :label="api.apiName || api.apiCode" /></el-select></el-form-item>
            <el-form-item label="请求地址"><el-input v-model="step.endpointUrl" placeholder="独立地址或覆盖关联 API 地址" /></el-form-item>
            <el-form-item label="请求方法"><el-select v-model="step.requestMethod"><el-option v-for="method in ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']" :key="method" :value="method" :label="method" /></el-select></el-form-item>
            <el-form-item label="内容类型"><el-select v-model="step.contentType"><el-option v-for="type in contentTypes" :key="type" :value="type" :label="type" /></el-select></el-form-item>
            <el-form-item label="鉴权方式"><el-select v-model="step.authMode"><el-option v-for="mode in ['INHERIT', 'NONE', 'API', 'BASIC', 'BEARER', 'API_KEY', 'TOKEN_API', 'OAUTH2', 'CUSTOM']" :key="mode" :value="mode" :label="authLabel(mode)" /></el-select></el-form-item>
            <el-form-item v-if="step.authMode === 'API'" label="鉴权 API"><el-select v-model="step.authApiConfigId" filterable><el-option v-for="api in apis" :key="api.id" :value="api.id" :label="api.apiName || api.apiCode" /></el-select></el-form-item>
            <el-form-item v-else-if="!['NONE', 'INHERIT'].includes(step.authMode)" label="鉴权参数"><el-input v-model="step.authApiConfig" type="textarea" :rows="3" placeholder="与主 API 的鉴权 JSON 使用相同格式" /></el-form-item>
            <api-parameter-fields :allow-overrides="false" v-model:fields="step.requestFields" :vars="vars" :functions="functions" :modules="stepModules(index)" />
            <el-collapse>
              <el-collapse-item title="本步骤响应结构与条件分支">
                <el-button @click="addStepResponse(step)">添加本步骤响应分支</el-button>
                <el-card v-for="branch in step.responseBranches" :key="branch.id" shadow="never">
                  <div class="panel-toolbar"><el-input v-model="branch.name" placeholder="分支名称" /><el-button @click="openSample(branch)">导入响应样例生成字段</el-button></div>
                  <response-condition-tree-editor operand-mode :functions="functions" :group="branch.condition" :path-options="responsePaths(branch)" />
                  <el-radio-group v-model="branch.mode"><el-radio-button value="FIELDS">按字段组装</el-radio-button><el-radio-button value="VALUE">整体取值</el-radio-button></el-radio-group>
          <api-value-editor v-if="branch.mode === 'VALUE'" v-model:value="branch.value" :modules="responsePaths(branch)" :functions="functions" />
          <api-parameter-fields v-else v-model:fields="branch.outputFields" output :vars="[]" :functions="functions" :modules="[...responsePaths(branch), ...stepModules(index)]" />
                </el-card>
              </el-collapse-item>
              <el-collapse-item title="本步骤成功、异常与重试判断">
                <el-form-item label="成功条件"><response-condition-tree-editor operand-mode :functions="functions" :group="step.successConditionTree" :path-options="modules" /></el-form-item>
                <el-form-item label="异常条件"><response-condition-tree-editor operand-mode :functions="functions" :group="step.exceptionConditionTree" :path-options="modules" /></el-form-item>
                <el-form-item label="业务重试条件"><response-condition-tree-editor operand-mode :functions="functions" :group="step.retryConditionTree" :path-options="modules" /></el-form-item>
              </el-collapse-item>
            </el-collapse>
            <el-form-item label="轮询此步骤"><el-switch :model-value="!!step.poll" @update:model-value="togglePoll(step, $event)" /></el-form-item>
            <template v-if="step.poll">
              <el-form-item label="失败条件"><response-condition-tree-editor operand-mode :functions="functions" :group="step.poll.failure" :path-options="modules" /></el-form-item><el-form-item label="完成条件"><response-condition-tree-editor operand-mode :functions="functions" :group="step.poll.until" :path-options="modules" /></el-form-item>
              <div class="policy-row"><el-form-item label="轮询间隔毫秒"><el-input-number v-model="step.poll.intervalMs" :min="1" /></el-form-item><el-form-item label="最多尝试"><el-input-number v-model="step.poll.maxAttempts" :min="1" /></el-form-item><el-form-item label="退避倍数"><el-input-number v-model="step.poll.backoffMultiplier" :min="1" :max="10" /></el-form-item></div>
            </template>
            <div class="policy-row"><el-form-item label="失败重试次数"><el-input-number v-model="step.retryCount" :min="0" :max="10" /></el-form-item><el-form-item label="重试间隔毫秒"><el-input-number v-model="step.retryIntervalMs" :min="0" /></el-form-item><el-form-item label="退避倍数"><el-input-number v-model="step.retryBackoffMultiplier" :min="1" :max="10" /></el-form-item><el-checkbox :model-value="step.retryNonIdempotent === 1" @update:model-value="step.retryNonIdempotent = $event ? 1 : 0">允许非幂等请求重试</el-checkbox></div>
          </template>
          <template v-else>
            <el-form-item label="回调地址模板"><el-input v-model="step.callback.url" :placeholder="callbackPlaceholder" /></el-form-item>
            <el-form-item label="签名 Header"><el-input v-model="step.callback.signatureHeader" /></el-form-item>
            <el-form-item label="签名密钥"><el-input v-model="step.callback.signatureSecret" type="password" show-password /></el-form-item>
            <el-form-item label="状态路径"><el-input v-model="step.callback.statusPath" /></el-form-item>
            <el-form-item label="成功值"><el-input v-model="step.callback.successValue" /></el-form-item>
            <el-form-item label="失败值"><el-input v-model="step.callback.failureValue" /></el-form-item>
            <el-collapse><el-collapse-item title="回调条件树（配置成功条件后优先使用）"><el-form-item label="成功条件"><response-condition-tree-editor operand-mode :group="step.callback.successCondition" :path-options="modules" :functions="functions" /></el-form-item><el-form-item label="失败条件"><response-condition-tree-editor operand-mode :group="step.callback.failureCondition" :path-options="modules" :functions="functions" /></el-form-item></el-collapse-item></el-collapse>
          </template>
        </el-card>
      </el-tab-pane>
      <el-tab-pane label="条件策略" name="policies">
        <el-tabs v-model="policyTab" :class="{ 'embedded-tabs': embeddedTabs }">
          <el-tab-pane v-for="policy in policies" :key="policy.key" :label="policy.label" :name="policy.key">
            <el-button @click="addBranch(policy.key)">添加条件分支</el-button>
            <el-card v-for="(branch, index) in config[policy.key]" :key="branch.id" shadow="never">
              <div class="panel-toolbar"><el-input v-model="branch.name" placeholder="分支名称" /><el-button link type="danger" @click="config[policy.key].splice(index, 1)">删除</el-button></div>
              <response-condition-tree-editor operand-mode :functions="functions" :group="branch.condition" :path-options="modules" />
              <el-form-item v-if="policy.key === 'billingBranches'" label="是否计费"><el-switch v-model="branch.bill" /></el-form-item>
              <template v-else-if="policy.key === 'retryBranches'"><el-form-item label="重试次数"><el-input-number v-model="branch.retryCount" :min="0" :max="10" /></el-form-item><el-form-item label="间隔毫秒"><el-input-number v-model="branch.retryIntervalMs" :min="0" /></el-form-item><el-form-item label="退避倍数"><el-input-number v-model="branch.retryBackoffMultiplier" :min="1" :max="10" /></el-form-item><el-checkbox :model-value="branch.retryNonIdempotent === 1" @update:model-value="branch.retryNonIdempotent = $event ? 1 : 0">允许非幂等请求重试</el-checkbox></template>
              <el-form-item v-else label="异常兜底值"><api-value-editor v-model:value="branch.value" :modules="modules" :functions="functions" /></el-form-item>
            </el-card>
          </el-tab-pane>
        </el-tabs>
      </el-tab-pane>
      <el-tab-pane label="多份测试样例" name="samples">
        <el-button @click="addSample">添加样例</el-button>
        <el-card v-for="(sample, index) in config.samples" :key="sample.id" shadow="never"><div class="panel-toolbar"><el-input v-model="sample.name" placeholder="样例名称" /><el-button @click="$emit('select-sample', sample.params)">用于调用测试</el-button><el-button link type="danger" @click="config.samples.splice(index, 1)">删除</el-button></div><el-input :model-value="JSON.stringify(sample.params, null, 2)" type="textarea" :rows="5" @change="setSample(sample, $event)" /></el-card>
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="sampleVisible" title="导入响应样例" append-to-body width="760px"><p>粘贴供应商响应 JSON，字段名称与类型保持原样。超过 200 个叶子字段时，默认整体保留响应，避免逐字段重复映射。</p><el-radio-group v-model="sampleStrategy"><el-radio-button value="AUTO">自动选择</el-radio-button><el-radio-button value="VALUE">整体取值</el-radio-button><el-radio-button value="FIELDS">逐字段生成</el-radio-button></el-radio-group><el-input v-model="sampleText" type="textarea" :rows="12" /><template #footer><el-button @click="sampleVisible = false">取消</el-button><el-button type="primary" @click="importSample">生成结构</el-button></template></el-dialog>
    <el-dialog v-model="configImportVisible" title="导入链路配置" append-to-body width="760px"><p>粘贴 version=2 的链路 JSON。导入会替换当前链路配置，并根据步骤切换同步或异步模式，不会发起外数请求。</p><el-input v-model="configImportText" type="textarea" :rows="16" placeholder="粘贴链路配置 JSON" /><template #footer><el-button @click="configImportVisible = false">取消</el-button><el-button type="primary" @click="importConfig">导入并检查</el-button></template></el-dialog>
  </section>
</template>
<script>
import ApiParameterFields from '@/components/common/ApiParameterFields.vue'
import ApiValueEditor from '@/components/common/ApiValueEditor.vue'
import ApiSampleStructure from '@/components/common/ApiSampleStructure.vue'
import ResponseConditionTreeEditor from '@/components/common/ResponseConditionTreeEditor.vue'
import { API_MODULE_FIELDS, newApiId, newCondition, parseExecution, prepareResponseSample, samplePathOptions, validateExecution, apiFieldOptions } from '@/utils/apiExecution'
import { listVariablesByProject } from '@/api/variable'
import { getVariableTree } from '@/api/dataObject'
import { listApiConfigs } from '@/api/datasource'
import { listAllFunctionsByProject } from '@/api/function'
import request from '@/api/request'
export default {
  components: { ApiParameterFields, ApiValueEditor, ApiSampleStructure, ResponseConditionTreeEditor },
  props: { value: { type: String, default: '' }, projectId: { type: [Number, String], default: 0 }, asyncMode: Boolean, apiConfig: { type: Object, default: () => ({}) }, embeddedTabs: Boolean, activeTab: { type: String, default: '' }, activePolicy: { type: String, default: '' } },
  emits: ['update:value', 'select-sample', 'select-mode', 'update:activeTab'],
  data() { return { config: parseExecution(this.value), tab: this.activeTab || 'request', policyTab: this.activePolicy || 'exceptionBranches', vars: [], functions: [], apis: [], sampleVisible: false, sampleText: '{}', sampleBranch: null, sampleStrategy: 'AUTO', configImportVisible: false, configImportText: '', responsePreview: '',
    modules: API_MODULE_FIELDS, callbackPlaceholder: 'https://公网地址/api/external-callback/${invocationId}', contentTypes: ['application/json', 'application/x-www-form-urlencoded', 'multipart/form-data'],
    policies: [{ key: 'exceptionBranches', label: '异常判断' }, { key: 'billingBranches', label: '计费条件' }, { key: 'retryBranches', label: '重试条件' }] } },
  computed: {
    inputPaths() { return this.vars.map(item => ({ value: item.varCode, label: item.varLabel || item.varCode })) },
    requestModules() { return (this.config.requestFields || []).map(item => ({ label: `链路入参 · ${item.path}`, value: `input.__apiFields.${item.id}` })) },
  },
  watch: {
    activeTab(value) { if (value && value !== this.tab) this.tab = value },
    tab(value) { if (this.embeddedTabs && value !== this.activeTab) this.$emit('update:activeTab', value) },
    activePolicy(value) { if (value && value !== this.policyTab) this.policyTab = value },
    value(text) { if (text !== JSON.stringify(this.config)) this.config = parseExecution(text) },
    config: { deep: true, handler(value) { this.$emit('update:value', JSON.stringify(value)) } },
    projectId: { immediate: true, handler() { this.loadOptions() } },
  },
  methods: {
    async loadOptions() {
      const projectId = Number(this.projectId || 0)
      if (projectId <= 0) {
        this.vars = []
        this.functions = []
        this.apis = []
        return
      }
      try {
        const [variables, objects, apis, functions] = await Promise.all([listVariablesByProject(projectId), getVariableTree(projectId), listApiConfigs({ pageNum: 1, pageSize: 1000, status: 1, projectId }), listAllFunctionsByProject(projectId)])
        if (String(projectId) !== String(this.projectId || 0)) return
        this.vars = apiFieldOptions(variables.data || [], objects.data?.tree || [])
        this.apis = apis.data?.records || []
        this.functions = functions.data || []
      } catch (error) { this.$message.error(error.message || '配置字段加载失败') }
    },
    addBranch(key) { if (!this.config[key]) this.config[key] = []; this.config[key].push({ id: newApiId(), name: '', mode: 'FIELDS', condition: newCondition(), requestFields: [], outputFields: [], bill: false, retryCount: 0, retryIntervalMs: 200, retryBackoffMultiplier: 2 }) },
    addStep(type) { this.config.steps.push({ id: newApiId(), name: '', type, when: newCondition(), successConditionTree: newCondition(), exceptionConditionTree: newCondition(), retryConditionTree: newCondition(), apiConfigId: null, endpointUrl: '', requestMethod: 'POST', contentType: 'application/json', authMode: 'INHERIT', requestFields: [], retryCount: 0, retryIntervalMs: 200, retryBackoffMultiplier: 2, retryNonIdempotent: 0, callback: { url: '', signatureHeader: 'X-Signature', signatureSecret: '', statusPath: 'body.status', successValue: 'SUCCESS', failureValue: 'FAILED', successCondition: newCondition(), failureCondition: newCondition() } }) },
    newCondition,
    addStepResponse(step) { if (!step.responseBranches) step.responseBranches = []; step.responseBranches.push({ id: newApiId(), name: '', mode: 'FIELDS', condition: newCondition(), outputFields: [] }) },
    moveStep(index, direction) { const [step] = this.config.steps.splice(index, 1); this.config.steps.splice(index + direction, 0, step) },
    togglePoll(step, enabled) { step.poll = enabled ? { intervalMs: 1000, maxAttempts: 20, backoffMultiplier: 1, until: newCondition(), failure: newCondition() } : null },
    stepModules(index) { return [...this.requestModules, ...this.config.steps.slice(0, index).flatMap(step => this.modules.map(item => ({ ...item, value: `steps.${step.id}.${item.value}`, label: `${step.name || step.id} · ${item.label}` }))), ...this.config.steps.filter(step => step.type === 'CALLBACK').map(step => ({ value: `callbacks.${step.id}.url`, label: `${step.name || step.id} · 回调地址` }))] },
    responsePaths(branch) { return [...new Map([...this.modules, ...this.config.steps.flatMap(step => this.modules.map(item => ({ ...item, value: `steps.${step.id}.${item.value}`, label: `${step.name || step.id} · ${item.label}` }))), ...(branch.sample ? samplePathOptions(branch.sample) : []), ...(branch.outputFields || []).map(field => ({ value: field.value?.value || field.path, label: field.path }))].map(item => [item.value, item])).values()] },
    openSample(branch) { this.sampleBranch = branch; this.sampleStrategy = 'AUTO'; this.sampleText = JSON.stringify(branch.sample || {}, null, 2); this.sampleVisible = true },
    importSample() { try { Object.assign(this.sampleBranch, prepareResponseSample(JSON.parse(this.sampleText), this.sampleBranch, this.sampleStrategy)); this.sampleVisible = false } catch { this.$message.error('请填写合法 JSON 样例') } },
    importConfig() {
      try {
        const incoming = JSON.parse(this.configImportText)
        if (incoming.version !== 2) throw new Error('仅支持 version=2 的统一链路配置')
        const mode = incoming.steps?.length ? 'ASYNC' : 'SYNC'
        const error = validateExecution(incoming, mode)
        if (error) throw new Error(error)
        this.config = parseExecution(JSON.stringify(incoming)); this.$emit('select-mode', mode); this.configImportVisible = false
      } catch (error) { this.$message.error(error.message || '链路配置格式不正确') }
    },
    async previewResponse(branch) { try { const response = await request.post(`/rule/datasource/api-config/${this.apiConfig.id || 0}/response-preview`, { config: { ...this.apiConfig, executionConfig: JSON.stringify(this.config) }, sample: { httpStatus: 200, body: branch.sample || {} } }); this.responsePreview = JSON.stringify(response.data, null, 2) } catch (error) { this.$message.error(error.message || '响应预览失败') } },
    addSample() { this.config.samples.push({ id: newApiId(), name: `样例 ${this.config.samples.length + 1}`, params: {} }) },
    setSample(sample, text) { try { sample.params = JSON.parse(text) } catch { this.$message.error('样例必须是合法 JSON') } },
    authLabel(mode) { return ({ INHERIT: '继承鉴权', NONE: '无鉴权', API: '关联 API 鉴权' })[mode] || mode },
  },
}
</script>
<style scoped>
.api-execution-editor { display: grid; gap: 16px; }
.api-execution-editor :deep(.embedded-tabs .el-tabs__header) { display: none; }
.api-execution-editor :deep(.el-card) { margin-top: 12px; border-color: var(--el-border-color); background: var(--el-bg-color); }
.panel-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.panel-toolbar > .el-input { flex: 1; }
.field-help { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; margin: 12px 0; }
.policy-row { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; }
.response-preview { max-height: 400px; overflow: auto; padding: 12px; background: var(--el-fill-color-light); color: var(--el-text-color-primary); }
@media (max-width: 900px) { .panel-toolbar { flex-wrap: wrap; } }
</style>
