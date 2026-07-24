<template>
  <div class="api-scenario-panel">
    <div class="scenario-toolbar">
      <div>
        <div class="scenario-title">API 测试用例</div>
        <div class="scenario-hint">
          配置真实业务场景的请求与响应；仅勾选且版本匹配的场景会进入 API 文档。
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

    <el-table
      v-loading="loading"
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
      <el-table-column label="操作" width="245" fixed="right">
        <template v-slot="{ row, $index }">
          <el-button link size="small" @click="openEdit(row)"
            >编辑</el-button
          >
          <el-button link size="small" @click="copyScenario(row)"
            >复制</el-button
          >
          <el-button
            link
            size="small"
            :disabled="$index === 0"
            @click="moveScenario($index, -1)"
            >上移</el-button
          >
          <el-button
            link
            size="small"
            :disabled="$index === scenarios.length - 1"
            @click="moveScenario($index, 1)"
            >下移</el-button
          >
          <el-button
            link
            size="small"
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
            <el-radio-group v-model="requestMode" size="small">
              <el-radio-button value="FORM">表单模式</el-radio-button>
              <el-radio-button value="JSON">JSON 模式</el-radio-button>
            </el-radio-group>
          </div>
          <div v-if="requestMode === 'FORM'" class="request-form">
            <div v-if="testFields.length === 0" class="empty-fields">
              当前规则没有可配置的输入字段，可切换到 JSON 模式编辑。
            </div>
            <el-form v-else label-width="150px" size="small">
              <el-form-item
                v-for="field in testFields"
                :key="field.fieldName"
                :label="field.fieldLabel || field.fieldName"
              >
                <el-switch
                  v-if="isBooleanType(field.fieldType)"
                  v-model="formParams[field.fieldName]"
                  @change="syncFormRequest"
                />
                <el-select
                  v-else-if="field.validValues && field.validValues.length"
                  v-model="formParams[field.fieldName]"
                  filterable
                  @change="syncFormRequest"
                >
                  <el-option
                    v-for="item in field.validValues"
                    :key="String(item)"
                    :label="String(item)"
                    :value="item"
                  />
                </el-select>
                <el-input-number
                  v-else-if="isNumberType(field.fieldType)"
                  v-model="formParams[field.fieldName]"
                  controls-position="right"
                  @change="syncFormRequest"
                />
                <el-input
                  v-else
                  v-model="formParams[field.fieldName]"
                  @update:model-value="syncFormRequest"
                />
                <div class="field-path">{{ field.fieldName }}</div>
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
            :icon="ElIconVideoPlay"
            @click="executeDraft"
            >调用生成响应</el-button
          >
          <el-button
            size="small"
            type="primary"
            :loading="saving"
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
  executeApiScenario,
  getRuleTestSchema,
} from '@/api/definition'
import {
  normalizeTestSchema,
  schemaFieldsToTestFields,
  flattenSchemaSample,
  buildNestedSchemaParams,
} from '@/utils/testSchema'

export default {
  data() {
    return {
      loading: false,
      saving: false,
      executing: false,
      draftVisible: false,
      requestMode: 'FORM',
      scenarios: [],
      testFields: [],
      formParams: {},
      draft: this.emptyDraft(),
      ElIconRefresh: markRaw(ElIconRefresh),
      ElIconPlus: markRaw(ElIconPlus),
      ElIconVideoPlay: markRaw(ElIconVideoPlay),
    }
  },
  name: 'ApiScenarioPanel',
  components: { MonacoEditor },
  props: {
    rule: {
      type: Object,
      required: true,
    },
  },
  watch: {
    'rule.id': {
      deep: true,
      immediate: true,

      handler(id) {
        if (id) this.loadScenarios()
      },
    },
  },
  methods: {
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
          { code: 200, message: 'success', data: null },
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
      this.loading = true
      try {
        const response = await listApiScenarios(this.rule.id)
        const data = this.unwrapResponse(response)
        this.scenarios = Array.isArray(data) ? data : []
      } catch (error) {
        this.$message.error(error.message || '加载 API 测试用例失败')
      } finally {
        this.loading = false
      }
    },
    async openCreate() {
      this.draft = this.emptyDraft()
      this.testFields = []
      this.formParams = {}
      this.requestMode = 'FORM'
      this.draftVisible = true
      try {
        const response = await getRuleTestSchema({
          targetType: 'RULE',
          targetId: this.rule.id,
        })
        const schema = normalizeTestSchema(response)
        this.testFields = schemaFieldsToTestFields(schema.inputs)
        this.formParams = flattenSchemaSample(
          this.testFields,
          schema.sampleParams
        )
        this.syncFormRequest()
      } catch (error) {
        this.requestMode = 'JSON'
        this.$message.warning(
          error.message || '加载规则请求字段失败，请使用 JSON 模式编辑'
        )
      }
    },
    openEdit(row) {
      this.draft = {
        ...this.emptyDraft(),
        ...row,
        responseSource: row.responseSource || 'MANUAL',
        businessCodePath: row.businessCodePath || '',
      }
      this.testFields = []
      this.formParams = {}
      this.requestMode = 'JSON'
      this.draftVisible = true
    },
    syncFormRequest() {
      const params = buildNestedSchemaParams(this.testFields, this.formParams)
      this.draft.requestJson = JSON.stringify(
        { clientAppName: 'api-doc-example', params },
        null,
        2
      )
    },
    onRequestEdited(value) {
      this.draft.requestJson = value
    },
    onResponseEdited(value) {
      const changed = value !== this.draft.responseJson
      this.draft.responseJson = value
      if (changed) this.draft.responseSource = 'MANUAL'
    },
    parseJsonObject(text, label) {
      try {
        const value = JSON.parse(text)
        if (!value || typeof value !== 'object' || Array.isArray(value))
          throw new Error()
        return value
      } catch (error) {
        throw new Error(`${label}不是合法 JSON 对象`)
      }
    },
    async executeDraft() {
      let requestBody
      try {
        requestBody = this.parseJsonObject(this.draft.requestJson, '请求报文')
      } catch (error) {
        this.$message.error(error.message)
        throw error
      }
      const previousResponse = this.draft.responseJson
      const previousSource = this.draft.responseSource
      this.executing = true
      try {
        const response = await executeApiScenario(
          this.rule.id,
          requestBody,
          DEFAULT_RULE_REQUEST_TIMEOUT_MS
        )
        this.draft.responseJson = JSON.stringify(response, null, 2)
        this.draft.responseSource = 'EXECUTED'
        return response
      } catch (error) {
        this.draft.responseJson = previousResponse
        this.draft.responseSource = previousSource
        this.$message.error(error.message || '调用失败，原响应内容已保留')
        throw error
      } finally {
        this.executing = false
      }
    },
    async saveDraft() {
      if (!this.draft.scenarioName || !this.draft.scenarioName.trim()) {
        this.$message.error('请输入场景名称')
        return
      }
      try {
        this.parseJsonObject(this.draft.requestJson, '请求报文')
        this.parseJsonObject(this.draft.responseJson, '响应报文')
      } catch (error) {
        this.$message.error(error.message)
        return
      }
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
      this.saving = true
      try {
        if (this.draft.id) {
          await updateApiScenario(this.rule.id, this.draft.id, payload)
        } else {
          await createApiScenario(this.rule.id, payload)
        }
        this.$message.success('API 测试用例已保存')
        this.draftVisible = false
        await this.loadScenarios()
        $emit(this, 'saved')
      } catch (error) {
        this.$message.error(error.message || '保存 API 测试用例失败')
      } finally {
        this.saving = false
      }
    },
    async copyScenario(row) {
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
        await copyApiScenario(this.rule.id, row.id, result.value.trim())
        await this.loadScenarios()
      } catch (error) {
        if (error !== 'cancel' && error !== 'close')
          this.$message.error(error.message || '复制失败')
      }
    },
    async removeScenario(row) {
      try {
        await this.$confirm(
          `确定删除场景“${row.scenarioName}”吗？`,
          '删除确认',
          { type: 'warning' }
        )
        await deleteApiScenario(this.rule.id, row.id)
        await this.loadScenarios()
        $emit(this, 'deleted')
      } catch (error) {
        if (error !== 'cancel' && error !== 'close')
          this.$message.error(error.message || '删除失败')
      }
    },
    async moveScenario(index, offset) {
      const target = index + offset
      if (target < 0 || target >= this.scenarios.length) return
      const reordered = this.scenarios.slice()
      const moved = reordered.splice(index, 1)[0]
      reordered.splice(target, 0, moved)
      try {
        await sortApiScenarios(
          this.rule.id,
          reordered.map((item) => item.id)
        )
        this.scenarios = reordered
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
  color: #303133;
  font-size: 15px;
  font-weight: 600;
}
.scenario-hint,
.field-tip,
.field-path {
  color: #64748b;
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
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}
.editor-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 0 12px;
  color: #303133;
  font-weight: 600;
  background: #f5f7fa;
  border-bottom: 1px solid #dcdfe6;
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
.empty-fields {
  padding: 80px 20px;
  color: #64748b;
  text-align: center;
}
@media screen and (max-width: 1000px) {
  .form-grid,
  .editor-grid {
    grid-template-columns: 1fr;
  }
}
</style>
