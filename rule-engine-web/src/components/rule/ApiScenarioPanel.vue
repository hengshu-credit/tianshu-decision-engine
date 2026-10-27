<template>
  <div class="api-scenario-panel">
    <div class="scenario-toolbar">
      <div>
        <div class="scenario-title">API 测试用例</div>
        <div class="scenario-hint">
          配置真实业务场景的请求与响应；仅勾选且版本匹配的场景会进入 API 文档。
          调用生成响应会按规则配置访问真实来源或执行名单动作，并应用计费规则。
        </div>
      </div>
      <div>
        <el-button
          size="small"
          :icon="ElIconRefresh"
          :loading="loading"
          @click="loadScenarios"
          >刷新</el-button
        >
        <el-button
          size="small"
          type="primary"
          :icon="ElIconPlus"
          @click="openCreate"
          >新增场景</el-button
        >
      </div>
    </div>

    <el-alert
      title="场景中的账号、密码、Token、API Key 等内容只能填写示例值；生产环境以单独提供的凭据为准。"
      type="warning"
      :closable="false"
      show-icon
      class="scenario-alert"
    />

    <el-table show-overflow-tooltip
      :aria-busy="loading"
      :data="scenarios"
      border
      size="small"
      empty-text="暂无 API 测试用例"
    >
      <el-table-column prop="scenarioName" label="场景名称" min-width="150" />
      <el-table-column label="响应码" width="90" align="center">
        <template v-slot="{ row }">{{
          row.outerCode == null ? '—' : row.outerCode
        }}</template>
      </el-table-column>
      <el-table-column label="业务码" min-width="110">
        <template v-slot="{ row }">{{
          row.businessCode == null ? '未配置' : row.businessCode
        }}</template>
      </el-table-column>
      <el-table-column label="响应来源" width="100" align="center">
        <template v-slot="{ row }">
          <el-tag
            size="small"
            :type="row.responseSource === 'EXECUTED' ? 'success' : 'info'"
          >
            {{ row.responseSource === 'EXECUTED' ? '调用生成' : '手工录入' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="文档状态" width="110" align="center">
        <template v-slot="{ row }">
          <el-tag size="small" :type="scenarioDocState(row).type">{{
            scenarioDocState(row).label
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="规则版本" width="90" align="center">
        <template v-slot="{ row }">v{{ row.ruleVersion }}</template>
      </el-table-column>
      <el-table-column class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" :width="operationColumnWidth(['编辑', '复制', '上移', '下移', '删除'])" fixed="right">
        <template v-slot="{ row, $index }">
          <el-button
            link
            size="small"
            type="warning"
            @click="openEdit(row)"
            >编辑</el-button
          >
          <el-button
            link
            size="small"
            type="primary"
            @click="copyScenario(row)"
            >复制</el-button
          >
          <el-button
            link
            size="small"
            type="info"
            :disabled="$index === 0"
            @click="moveScenario($index, -1)"
            >上移</el-button
          >
          <el-button
            link
            size="small"
            type="info"
            :disabled="$index === scenarios.length - 1"
            @click="moveScenario($index, 1)"
            >下移</el-button
          >
          <el-button
            link
            size="small"
            type="danger"
            class="danger-action"
            @click="removeScenario(row)"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      :title="draft.id ? '编辑 API 测试用例' : '新增 API 测试用例'"
      v-model="draftVisible"
      width="1100px"
      :close-on-click-modal="false"
      append-to-body
    >
      <el-alert v-if="executionSource" :title="executionSourceLabel(executionSource, executionSourceName || draftSchemaIdentity)" type="info" :closable="false" show-icon class="scenario-alert" />
      <el-alert v-if="draftNotice" type="warning" :closable="false" show-icon class="scenario-alert">
        {{ draftNotice }}
      </el-alert>
      <el-alert v-if="responseOutdated" type="warning" :closable="false" show-icon class="scenario-alert">
        请求或测试版本已变化，当前响应来自修改前的调用。请重新调用，或确认将当前响应作为手工示例保存。
        <el-button link type="primary" data-testid="scenario-keep-manual-response" @click="keepManualResponse">保留为手工响应</el-button>
      </el-alert>
      <el-form :model="draft" label-width="100px" size="small">
        <div class="form-grid">
          <el-form-item label="场景名称" required>
            <el-input
              v-model="draft.scenarioName"
              maxlength="128"
              placeholder="例如：风险拒绝"
            />
          </el-form-item>
          <el-form-item label="场景说明">
            <el-input
              v-model="draft.description"
              maxlength="512"
              placeholder="说明该输入对应的业务场景"
            />
          </el-form-item>
          <el-form-item label="加入文档">
            <el-switch
              v-model="draft.includeInDoc"
              :active-value="1"
              :inactive-value="0"
            />
            <span class="field-tip">发布版本匹配时才会导出</span>
          </el-form-item>
          <el-form-item label="启用状态">
            <el-switch
              v-model="draft.status"
              :active-value="1"
              :inactive-value="0"
            />
          </el-form-item>
        </div>
        <el-form-item label="业务码路径">
          <el-input
            v-model="draft.businessCodePath"
            placeholder="可选，例如 data.result.code；留空则不生成内层业务码"
          />
        </el-form-item>
      </el-form>

      <div class="editor-grid">
        <section class="editor-card">
          <div class="editor-heading">
            <span>请求报文</span>
            <el-radio-group :model-value="requestMode" size="small" @update:model-value="changeRequestMode">
              <el-radio-button value="FORM">表单模式</el-radio-button>
              <el-radio-button value="JSON">JSON 模式</el-radio-button>
            </el-radio-group>
          </div>
          <div v-if="requestMode === 'FORM'" class="request-form">
            <div v-if="schemaLoading" class="empty-fields">正在加载请求字段…</div>
            <el-alert v-else-if="schemaError" type="warning" :closable="false" show-icon>
              {{ schemaError }}
              <el-button link type="primary" @click="loadDraftSchema(false)">重新加载字段</el-button>
            </el-alert>
            <el-alert v-else-if="formProblem" type="warning" :closable="false" show-icon>{{ formProblem }}</el-alert>
            <div v-else-if="testFields.length === 0" class="empty-fields">
              当前规则没有可配置的输入字段，可切换到 JSON 模式编辑。
            </div>
            <el-form v-else label-width="150px" size="small">
              <el-form-item
                v-for="field in testFields"
                :key="field.fieldName"
                :label="field.fieldLabel || field.fieldName"
                :error="formErrors[field.fieldName]"
              >
                <div class="scenario-field-controls" :data-field="field.fieldName">
                  <el-select v-model="formStates[field.fieldName]" aria-label="字段传入方式" :disabled="Boolean(formBlocked[field.fieldName])" @change="syncFormRequest">
                    <el-option label="未提供" value="MISSING" />
                    <el-option label="填写值" value="VALUE" />
                    <el-option label="传入 null" value="NULL" />
                  </el-select>
                  <template v-if="formStates[field.fieldName] === 'VALUE'">
                    <el-switch
                      v-if="isBooleanType(field.fieldType)"
                      v-model="formParams[field.fieldName]"
                      :disabled="Boolean(formBlocked[field.fieldName])"
                      @change="syncFormRequest"
                    />
                    <el-select
                      v-else-if="field.validValues && field.validValues.length && !isNumberType(field.fieldType)"
                      v-model="formParams[field.fieldName]"
                      filterable
                      :disabled="Boolean(formBlocked[field.fieldName])"
                      @change="syncFormRequest"
                    >
                      <el-option v-for="item in field.validValues" :key="String(item)" :label="String(item)" :value="item" />
                    </el-select>
                    <el-input
                      v-else
                      v-model="formParams[field.fieldName]"
                      :type="isStructuredType(field.fieldType) ? 'textarea' : 'text'"
                      :inputmode="isNumberType(field.fieldType) ? 'decimal' : undefined"
                      :disabled="Boolean(formBlocked[field.fieldName])"
                      :placeholder="isStructuredType(field.fieldType) ? '填写 JSON 数组或对象' : undefined"
                      @update:model-value="syncFormRequest"
                    />
                  </template>
                  <div class="field-path">{{ field.fieldName }}{{ formBlocked[field.fieldName] ? ' · ' + formBlocked[field.fieldName] : '' }}</div>
                </div>
              </el-form-item>
            </el-form>
          </div>
          <monaco-editor
            v-else
            :value="draft.requestJson"
            language="json"
            height="360px"
            @input="onRequestEdited"
          />
        </section>

        <section class="editor-card">
          <div class="editor-heading">
            <span>响应报文</span>
            <el-tag
              size="small"
              :type="draft.responseSource === 'EXECUTED' ? 'success' : 'info'"
            >
              {{
                draft.responseSource === 'EXECUTED' ? '调用生成' : '手工录入'
              }}
            </el-tag>
          </div>
          <monaco-editor
            :value="draft.responseJson"
            language="json"
            height="360px"
            @input="onResponseEdited"
          />
        </section>
      </div>

      <template v-slot:footer>
        <div>
          <el-button size="small" @click="draftVisible = false">取消</el-button>
          <el-button
            size="small"
            :loading="executing"
            :disabled="saving || schemaLoading || hasFormErrors"
            :icon="ElIconVideoPlay"
            @click="executeDraft"
            >调用生成响应</el-button
          >
          <el-button
            size="small"
            type="primary"
            :loading="saving"
            :disabled="executing || schemaLoading || hasFormErrors || responseOutdated"
            @click="saveDraft"
            >保存场景</el-button
          >
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import {
  Refresh as ElIconRefresh,
  Plus as ElIconPlus,
  VideoPlay as ElIconVideoPlay,
} from '@element-plus/icons-vue'
import { $emit } from '../../utils/gogocodeTransfer'
import MonacoEditor from '@/components/MonacoEditor'
import {
  DEFAULT_RULE_REQUEST_TIMEOUT_MS,
  listApiScenarios,
  createApiScenario,
  updateApiScenario,
  deleteApiScenario,
  copyApiScenario,
  sortApiScenarios,
  executeApiScenarioValidated,
  getRuleTestSchema,
} from '@/api/definition'
import {
  normalizeTestSchema,
  schemaFieldsToTestFields,
  flattenSchemaSample,
  buildNestedSchemaParams,
} from '@/utils/testSchema'
import { parseExactJson, parseExecutionSnapshotJson, stringifyExactJson, isExactNumber, exactNumberText, createExactNumber } from '@/utils/exactJson'
import { executionSourceKey, executionSourceLabel, requireExecutionIdentity } from '@/utils/ruleTestExecutionSource'
import { operationColumnWidth as getOperationColumnWidth } from '@/utils/operationColumnWidth'

const hasOwn = (value, key) => Object.prototype.hasOwnProperty.call(value, key)
const isRecord = value => value !== null && typeof value === 'object' && !Array.isArray(value) && !isExactNumber(value)

// 已存在的完整字段名优先；否则沿 Schema 路径定位，不能覆盖原报文中的非对象父值。
function fieldLocation(params, path, create = false) {
  const parts = hasOwn(params, path) ? [path] : path.split('.')
  let target = params
  for (const key of parts.slice(0, -1)) {
    if (!hasOwn(target, key)) {
      if (!create) return { exists: false }
      Object.defineProperty(target, key, { value: {}, enumerable: true, configurable: true, writable: true })
    }
    if (!isRecord(target[key])) return { blocked: true }
    target = target[key]
  }
  const key = parts[parts.length - 1]
  return { target, key, exists: hasOwn(target, key), value: target[key] }
}

export default {
  data() {
    return {
      loading: false,
      saving: false,
      executing: false,
      draftVisible: false,
      requestMode: 'FORM',
      scenarios: [],
      scenariosRequestId: 0,
      draftSessionId: 0,
      schemaRequestId: 0,
      schemaLoading: false,
      schemaError: '',
      draftSchemaIdentity: null,
      draftNotice: '',
      executedRequestJson: null,
      executedSourceKey: null,
      testFields: [],
      formParams: Object.create(null),
      formStates: Object.create(null),
      formBaseline: Object.create(null),
      formBlocked: Object.create(null),
      formErrors: Object.create(null),
      formProblem: '',
      draft: this.emptyDraft(),
      ElIconRefresh: markRaw(ElIconRefresh),
      ElIconPlus: markRaw(ElIconPlus),
      ElIconVideoPlay: markRaw(ElIconVideoPlay),
    }
  },
  name: 'ApiScenarioPanel',
  components: { MonacoEditor },
  props: {
    executionSource: { type: Object, default: null },
    executionSourceName: { type: String, default: '' },
    rule: {
      type: Object,
      required: true,
    },
  },
  computed: {
    responseOutdated() {
      return this.draft.responseSource === 'EXECUTED' && (
        (this.executedRequestJson !== null && this.executedRequestJson !== this.draft.requestJson)
        || (this.executedSourceKey !== null && this.executedSourceKey !== executionSourceKey(this.executionSource)))
    },
    hasFormErrors() { return Object.keys(this.formErrors).length > 0 },
  },
  watch: {
    executionSource: { deep: true, handler() {
      this.invalidateDraft()
      if (this.draftVisible) {
        this.draftNotice = '测试版本已切换，输入与手工响应已保留，请重新核对字段并调用。'
        this.loadDraftSchema(false)
      }
    } },
    'rule.id': {
      deep: true,
      immediate: true,

      handler(id) {
        this.invalidateDraft()
        this.draftVisible = false
        if (id) this.loadScenarios()
      },
    },
    draftVisible(visible) {
      if (!visible) this.invalidateDraft()
    },
  },
  beforeUnmount() {
    this.invalidateDraft()
    this.scenariosRequestId++
  },
  methods: {
    operationColumnWidth(labels) { return getOperationColumnWidth(labels) },
    executionSourceLabel,
    emptyDraft() {
      return {
        id: null,
        scenarioName: '',
        description: '',
        requestJson: JSON.stringify(
          { clientAppName: 'api-doc-example', params: {} },
          null,
          2
        ),
        responseJson: JSON.stringify(
          { code: '000000', message: '成功', data: null },
          null,
          2
        ),
        responseSource: 'MANUAL',
        businessCodePath: '',
        includeInDoc: 0,
        sortOrder: this.scenarios ? this.scenarios.length : 0,
        status: 1,
      }
    },
    unwrapResponse(response) {
      return response && Object.prototype.hasOwnProperty.call(response, 'data')
        ? response.data
        : response
    },
    async loadScenarios() {
      if (!this.rule.id) return
      const ruleId = this.rule.id
      const requestId = ++this.scenariosRequestId
      this.loading = true
      this.scenarios = []
      try {
        const response = await listApiScenarios(ruleId)
        if (requestId !== this.scenariosRequestId || String(ruleId) !== String(this.rule.id)) return
        const data = this.unwrapResponse(response)
        this.scenarios = Array.isArray(data) ? data : []
      } catch (error) {
        if (requestId !== this.scenariosRequestId || String(ruleId) !== String(this.rule.id)) return
        this.$message.error(error.message || '加载 API 测试用例失败')
      } finally {
        if (requestId === this.scenariosRequestId) this.loading = false
      }
    },
    invalidateDraft() {
      this.draftSessionId++
      this.schemaRequestId++
      this.schemaLoading = false
      this.executing = false
      this.saving = false
    },
    isCurrentDraft(session, ruleId) {
      return this.draftVisible && session === this.draftSessionId && String(ruleId) === String(this.rule.id)
    },
    beginDraft(draft) {
      this.invalidateDraft()
      this.draft = draft
      this.executedRequestJson = draft.responseSource === 'EXECUTED' ? draft.requestJson : null
      this.executedSourceKey = null
      this.draftNotice = ''
      this.schemaError = ''
      this.draftSchemaIdentity = null
      this.testFields = []
      this.formParams = Object.create(null)
      this.formStates = Object.create(null)
      this.formBaseline = Object.create(null)
      this.formErrors = Object.create(null)
      this.formBlocked = Object.create(null)
      this.formProblem = ''
      this.requestMode = 'FORM'
      this.draftVisible = true
    },
    async openCreate() {
      this.beginDraft(this.emptyDraft())
      await this.loadDraftSchema(true)
    },
    async loadDraftSchema(useSample = false) {
      const session = this.draftSessionId
      const ruleId = this.rule.id
      const requestId = ++this.schemaRequestId
      const initialRequest = this.draft.requestJson
      const source = this.executionSource ? { ...this.executionSource } : null
      this.schemaLoading = true
      this.schemaError = ''
      try {
        const response = await getRuleTestSchema({
          targetType: 'RULE',
          targetId: ruleId,
          ...(source ? { executionSource: source } : {}),
        })
        if (!this.isCurrentDraft(session, ruleId) || requestId !== this.schemaRequestId) return
        const schema = normalizeTestSchema(response)
        requireExecutionIdentity(schema.executionIdentity, ruleId, source)
        this.draftSchemaIdentity = schema.executionIdentity || null
        this.testFields = schemaFieldsToTestFields(schema.inputs)
        // 只为从未编辑的新建空稿填样例；编辑已有场景永远以原请求为准。
        if (useSample && this.draft.requestJson === initialRequest) {
          const params = typeof schema.sampleParamsJson === 'string'
            ? this.parseJsonObject(schema.sampleParamsJson, '规则样例')
            : buildNestedSchemaParams(this.testFields, flattenSchemaSample(this.testFields, schema.sampleParams))
          this.draft.requestJson = stringifyExactJson({ clientAppName: 'api-doc-example', params }, 2)
        }
        this.hydrateFormRequest()
      } catch (error) {
        if (!this.isCurrentDraft(session, ruleId) || requestId !== this.schemaRequestId) return
        this.schemaError = source
          ? `所选测试版本字段读取失败：${error.message || '请重试'}。原报文已保留，重新加载成功后才能调用。`
          : '加载请求字段失败，原报文已保留。可以重新加载或使用 JSON 模式编辑。'
      } finally {
        if (this.isCurrentDraft(session, ruleId) && requestId === this.schemaRequestId) this.schemaLoading = false
      }
    },
    async openEdit(row) {
      this.beginDraft({
        ...this.emptyDraft(),
        ...row,
        responseSource: row.responseSource || 'MANUAL',
        businessCodePath: row.businessCodePath || '',
      })
      await this.loadDraftSchema(false)
    },
    hydrateFormRequest() {
      this.formParams = Object.create(null)
      this.formStates = Object.create(null)
      this.formBaseline = Object.create(null)
      this.formBlocked = Object.create(null)
      this.formErrors = Object.create(null)
      this.formProblem = ''
      let body
      try { body = this.parseJsonObject(this.draft.requestJson, '请求报文') } catch (error) {
        this.formProblem = '原请求不是合法 JSON 对象，请切换到 JSON 模式修正。'
        return
      }
      if (hasOwn(body, 'params') && !isRecord(body.params)) {
        this.formProblem = '原请求中的 params 不是对象，已保留原值。请切换到 JSON 模式编辑。'
        return
      }
      this.testFields.forEach(field => {
        const path = field.fieldName
        const location = fieldLocation(body.params || {}, path)
        const state = !location.exists ? 'MISSING' : location.value === null ? 'NULL' : 'VALUE'
        let value = this.isBooleanType(field.fieldType) ? false : ''
        if (state === 'VALUE') {
          value = isExactNumber(location.value) ? exactNumberText(location.value)
            : typeof location.value === 'object' ? stringifyExactJson(location.value, 2) : location.value
        }
        this.formStates[path] = state
        this.formParams[path] = value
        this.formBaseline[path] = { state, value }
        if (location.blocked) this.formBlocked[path] = '上级字段不是对象，请在 JSON 模式编辑'
      })
    },
    changeRequestMode(mode) {
      if (mode === 'JSON' && this.hasFormErrors) {
        this.$message.warning('请先修正表单中的无效值，再切换模式，避免丢失尚未写入报文的内容')
        return
      }
      if (mode === 'FORM') this.hydrateFormRequest()
      this.requestMode = mode
    },
    fieldValue(field) {
      const value = this.formParams[field.fieldName]
      if (this.isNumberType(field.fieldType)) {
        if (String(value).trim() === '') throw new Error('请填写数字，或选择未提供 / 传入 null')
        try { return createExactNumber(value) } catch (error) { throw new Error('请填写有效数字') }
      }
      if (this.isStructuredType(field.fieldType)) {
        try { return parseExactJson(value) } catch (error) { throw new Error('请填写合法 JSON') }
      }
      return value
    },
    syncFormRequest() {
      if (this.formProblem) return
      const body = this.parseJsonObject(this.draft.requestJson, '请求报文')
      const errors = Object.create(null)
      let changed = false
      this.testFields.forEach(field => {
        const path = field.fieldName
        const state = this.formStates[path]
        const baseline = this.formBaseline[path]
        if (!baseline || (state === baseline.state && (state !== 'VALUE' || this.formParams[path] === baseline.value)) || this.formBlocked[path]) return
        try {
          const value = state === 'VALUE' ? this.fieldValue(field) : null
          if (!hasOwn(body, 'params')) body.params = {}
          const location = fieldLocation(body.params, path, state !== 'MISSING')
          if (location.blocked) throw new Error('上级字段不是对象，请在 JSON 模式编辑')
          if (state === 'MISSING') {
            if (location.target) delete location.target[location.key]
          } else {
            Object.defineProperty(location.target, location.key, { value, enumerable: true, configurable: true, writable: true })
          }
          changed = true
        } catch (error) { errors[path] = error.message }
      })
      this.formErrors = errors
      if (!changed || this.hasFormErrors) return
      this.draft.requestJson = stringifyExactJson(body, 2)
      this.testFields.forEach(field => {
        const path = field.fieldName
        this.formBaseline[path] = { state: this.formStates[path], value: this.formParams[path] }
      })
    },
    onRequestEdited(value) {
      this.draft.requestJson = value
    },
    onResponseEdited(value) {
      const changed = value !== this.draft.responseJson
      this.draft.responseJson = value
      if (changed) this.draft.responseSource = 'MANUAL'
    },
    keepManualResponse() {
      this.draft.responseSource = 'MANUAL'
      this.executedRequestJson = null
    },
    parseJsonObject(text, label) {
      try {
        const value = parseExactJson(text)
        if (!isRecord(value))
          throw new Error()
        return value
      } catch (error) {
        throw new Error(`${label}不是合法 JSON 对象`)
      }
    },
    validateResponseJson(text, label) {
      try { parseExecutionSnapshotJson(text) }
      catch { throw new Error(`${label}不是合法 JSON 对象`) }
    },
    async executeDraft() {
      if (this.executing || this.saving || this.schemaLoading || !this.draftVisible) return false
      if (this.executionSource && this.schemaError) { this.$message.error(this.schemaError); return false }
      try {
        if (this.hasFormErrors) throw new Error('请先修正请求表单中的无效值')
        this.parseJsonObject(this.draft.requestJson, '请求报文')
      } catch (error) {
        this.$message.error(error.message)
        return false
      }
      const session = this.draftSessionId
      const ruleId = this.rule.id
      const requestJson = this.draft.requestJson
      const responseJson = this.draft.responseJson
      const responseSource = this.draft.responseSource
      const source = this.executionSource ? { ...this.executionSource } : null
      const payload = { requestJson, ...(source ? { executionSource: source } : {}) }
      if (this.draft.id) payload.scenarioId = this.draft.id
      if (this.draft.expectationJson != null) payload.expectationJson = this.draft.expectationJson
      this.draftNotice = ''
      this.executing = true
      try {
        const response = await executeApiScenarioValidated(
          ruleId,
          payload,
          DEFAULT_RULE_REQUEST_TIMEOUT_MS
        )
        if (!this.isCurrentDraft(session, ruleId)) return false
        if (this.draft.requestJson !== requestJson || this.hasFormErrors) {
          this.draftNotice = '请求已变化，已忽略修改前的调用响应，请重新调用。'
          return false
        }
        if (this.draft.responseJson !== responseJson || this.draft.responseSource !== responseSource) {
          this.draftNotice = '响应已手工修改，已保留当前内容，未覆盖为刚才的调用响应。'
          return false
        }
        const result = this.unwrapResponse(response)
        requireExecutionIdentity(result?.execution?.executionIdentity, ruleId, source)
        this.validateResponseJson(result?.responseJson, '调用响应')
        this.draft.responseJson = result.responseJson
        this.draft.responseSource = 'EXECUTED'
        this.executedRequestJson = requestJson
        this.executedSourceKey = executionSourceKey(source)
        return response
      } catch (error) {
        if (this.isCurrentDraft(session, ruleId)) {
          if (this.draft.requestJson !== requestJson || this.hasFormErrors) this.draftNotice = '请求已变化，已忽略修改前的调用错误，请重新调用。'
          else this.$message.error(error.message || '调用失败，当前响应内容已保留')
        }
        return false
      } finally {
        if (this.isCurrentDraft(session, ruleId)) this.executing = false
      }
    },
    draftPayload() {
      const payload = {
        scenarioName: this.draft.scenarioName.trim(),
        description: this.draft.description,
        requestJson: this.draft.requestJson,
        responseJson: this.draft.responseJson,
        responseSource: this.draft.responseSource,
        businessCodePath: this.draft.businessCodePath,
        includeInDoc: this.draft.includeInDoc,
        sortOrder: this.draft.sortOrder,
        status: this.draft.status,
      }
      if (this.draft.expectationJson != null) payload.expectationJson = this.draft.expectationJson
      return payload
    },
    async saveDraft() {
      if (this.saving || this.executing || this.schemaLoading || !this.draftVisible) return
      if (!this.draft.scenarioName || !this.draft.scenarioName.trim()) {
        this.$message.error('请输入场景名称')
        return
      }
      try {
        if (this.hasFormErrors) throw new Error('请先修正请求表单中的无效值')
        if (this.responseOutdated) throw new Error('请求已变化，请重新调用或明确保留为手工响应')
        this.parseJsonObject(this.draft.requestJson, '请求报文')
        this.validateResponseJson(this.draft.responseJson, '响应报文')
      } catch (error) {
        this.$message.error(error.message)
        return
      }
      const payload = this.draftPayload()
      const session = this.draftSessionId
      const ruleId = this.rule.id
      const scenarioId = this.draft.id
      this.saving = true
      this.draftNotice = ''
      try {
        const response = scenarioId
          ? await updateApiScenario(ruleId, scenarioId, payload)
          : await createApiScenario(ruleId, payload)
        if (!this.isCurrentDraft(session, ruleId)) {
          if (String(ruleId) === String(this.rule.id)) await this.loadScenarios()
          return
        }
        if (!scenarioId) this.draft.id = this.unwrapResponse(response)?.id || null
        // 这里比较的只有字符串与标量，不解析或重写请求、响应的 JSON 数字。
        const changed = JSON.stringify(payload) !== JSON.stringify(this.draftPayload()) || this.hasFormErrors
        if (changed) this.draftNotice = '已保存提交时的内容；当前修改尚未保存，请核对后再次保存。'
        else {
          this.$message.success('API 测试用例已保存')
          this.draftVisible = false
        }
        await this.loadScenarios()
        $emit(this, 'saved')
      } catch (error) {
        if (this.isCurrentDraft(session, ruleId)) this.$message.error(error.message || '保存 API 测试用例失败')
      } finally {
        if (this.isCurrentDraft(session, ruleId)) this.saving = false
      }
    },
    async copyScenario(row) {
      const ruleId = this.rule.id
      try {
        const result = await this.$prompt(
          '请输入复制后的场景名称',
          '复制 API 测试用例',
          {
            inputValue: `${row.scenarioName}-副本`,
            inputValidator: (value) => Boolean(value && value.trim()),
            inputErrorMessage: '场景名称不能为空',
          }
        )
        if (String(ruleId) !== String(this.rule.id)) return
        await copyApiScenario(ruleId, row.id, result.value.trim())
        if (String(ruleId) === String(this.rule.id)) await this.loadScenarios()
      } catch (error) {
        if (error !== 'cancel' && error !== 'close')
          this.$message.error(error.message || '复制失败')
      }
    },
    async removeScenario(row) {
      const ruleId = this.rule.id
      try {
        await this.$confirm(
          `确定删除场景“${row.scenarioName}”吗？`,
          '删除确认',
          { type: 'warning' }
        )
        if (String(ruleId) !== String(this.rule.id)) return
        await deleteApiScenario(ruleId, row.id)
        if (String(ruleId) !== String(this.rule.id)) return
        await this.loadScenarios()
        $emit(this, 'deleted')
      } catch (error) {
        if (error !== 'cancel' && error !== 'close')
          this.$message.error(error.message || '删除失败')
      }
    },
    async moveScenario(index, offset) {
      const ruleId = this.rule.id
      const requestId = this.scenariosRequestId
      const target = index + offset
      if (target < 0 || target >= this.scenarios.length) return
      const reordered = this.scenarios.slice()
      const moved = reordered.splice(index, 1)[0]
      reordered.splice(target, 0, moved)
      try {
        await sortApiScenarios(
          ruleId,
          reordered.map((item) => item.id)
        )
        if (String(ruleId) === String(this.rule.id) && requestId === this.scenariosRequestId) this.scenarios = reordered
      } catch (error) {
        this.$message.error(error.message || '调整场景顺序失败')
      }
    },
    scenarioDocState(row) {
      if (row.status !== 1 || row.includeInDoc !== 1)
        return { label: '未加入', type: 'info' }
      if (
        !this.rule.publishedVersion ||
        row.ruleVersion !== this.rule.publishedVersion
      ) {
        return { label: '版本已过期', type: 'warning' }
      }
      if (
        row.revisionId &&
        this.rule.publishedRevisionId &&
        String(row.revisionId) !== String(this.rule.publishedRevisionId)
      ) {
        return { label: '修订已过期', type: 'warning' }
      }
      if (
        row.artifactDigest &&
        this.rule.publishedArtifactDigest &&
        row.artifactDigest !== this.rule.publishedArtifactDigest
      ) {
        return { label: '制品已变化', type: 'warning' }
      }
      return { label: '已加入', type: 'success' }
    },
    isBooleanType(type) {
      return ['BOOLEAN', 'BOOL'].indexOf(String(type || '').toUpperCase()) >= 0
    },
    isNumberType(type) {
      return (
        [
          'NUMBER',
          'INTEGER',
          'INT',
          'LONG',
          'DOUBLE',
          'FLOAT',
          'DECIMAL',
          'PROBABILITY',
        ].indexOf(String(type || '').toUpperCase()) >= 0
      )
    },
    isStructuredType(type) {
      return ['ARRAY', 'LIST', 'VECTOR', 'OBJECT', 'MAP'].includes(String(type || '').toUpperCase())
    },
  },
  emits: ['saved', 'deleted'],
}
</script>

<style lang="scss" scoped>
.api-scenario-panel {
  min-height: 240px;
}
.scenario-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}
.scenario-title {
  color: var(--tianshu-text-primary);
  font-size: 15px;
  font-weight: 600;
}
.scenario-hint,
.field-tip,
.field-path {
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
}
.scenario-alert {
  margin-bottom: 12px;
}
.danger-action {
  color: #f56c6c;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 20px;
}
.editor-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.editor-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--tianshu-border);
  border-radius: 4px;
}
.editor-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 0 12px;
  color: var(--tianshu-text-primary);
  font-weight: 600;
  background: var(--tianshu-bg-muted);
  border-bottom: 1px solid var(--tianshu-border);
}
.request-form {
  height: 360px;
  padding: 12px;
  overflow: auto;
}
.request-form :deep(.el-form-item) {
  margin-bottom: 14px;
}
.field-path {
  line-height: 18px;
}
.scenario-field-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
}
.scenario-field-controls > .el-select:first-child {
  flex: 0 0 112px;
}
.scenario-field-controls > .el-input,
.scenario-field-controls > .el-textarea,
.scenario-field-controls > .el-select:not(:first-child) {
  flex: 1 1 120px;
  min-width: 0;
}
.scenario-field-controls .field-path {
  flex-basis: 100%;
  overflow-wrap: anywhere;
}
.empty-fields {
  padding: 80px 20px;
  color: var(--tianshu-text-tertiary);
  text-align: center;
}
@media screen and (max-width: 1000px) {
  .form-grid,
  .editor-grid {
    grid-template-columns: 1fr;
  }
}
</style>
