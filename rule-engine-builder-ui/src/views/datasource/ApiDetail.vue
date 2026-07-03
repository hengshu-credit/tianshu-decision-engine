<template>
  <div class="uiue-list-page api-detail-page">
    <div class="detail-header">
      <div>
        <div class="detail-title">{{ isCreateMode ? '新建外数 API 接口' : '编辑外数 API 接口' }}</div>
        <div class="detail-meta">{{ form.apiName || '未命名接口' }} / {{ form.apiCode || '待填写编码' }}</div>
      </div>
      <div class="detail-actions">
        <el-button size="small" @click="$router.push({ path: '/datasource', query: { tab: 'api' } })">返回</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </div>
    </div>

    <el-form ref="form" :model="form" :rules="rules" label-width="120px" size="small" class="detail-form">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="所属数据源" prop="datasourceId">
            <el-select v-model="form.datasourceId" filterable placeholder="请选择数据源" style="width:100%" @change="onDatasourceChange">
              <el-option v-for="item in datasourceOptions" :key="item.id" :label="item.datasourceName + ' / ' + item.datasourceCode" :value="item.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="调用模式">
            <el-radio-group v-model="form.requestMode">
              <el-radio-button label="SYNC">同步</el-radio-button>
              <el-radio-button label="ASYNC">异步</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="接口编码" prop="apiCode">
            <el-input v-model="form.apiCode" placeholder="如 query_credit_report" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="接口名称" prop="apiName">
            <el-input v-model="form.apiName" placeholder="如 查询征信报告" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="方法">
            <el-select v-model="form.requestMethod" style="width:100%">
              <el-option v-for="method in httpMethods" :key="method" :label="method" :value="method" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="接口地址" prop="endpointUrl">
            <el-input v-model="form.endpointUrl" :placeholder="endpointPlaceholder()" />
            <div class="field-help">{{ endpointHelp() }}</div>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="Content-Type">
            <el-select v-model="form.contentType" filterable allow-create clearable placeholder="不设置或选择类型" style="width:100%">
              <el-option v-for="item in contentTypeOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="请求对象">
            <el-select v-model="form.requestObjectId" clearable filterable placeholder="选择请求数据对象" style="width:100%">
              <el-option v-for="item in dataObjectOptions" :key="item.id" :label="dataObjectLabel(item)" :value="item.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="响应对象">
            <el-select v-model="form.responseObjectId" clearable filterable placeholder="选择响应数据对象" style="width:100%">
              <el-option v-for="item in dataObjectOptions" :key="item.id" :label="dataObjectLabel(item)" :value="item.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-collapse v-model="collapse">
        <el-collapse-item title="鉴权与 Token 获取" name="auth">
          <el-row :gutter="12">
            <el-col :span="8">
              <el-form-item label="接口鉴权">
                <el-select v-model="form.authMode" style="width:100%">
                  <el-option v-for="item in authModeOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="Token缓存秒">
                <el-input-number v-model="form.tokenCacheSeconds" :min="0" :step="60" style="width:100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="异常策略">
                <el-select v-model="form.exceptionStrategy" style="width:100%">
                  <el-option v-for="item in exceptionStrategyOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="鉴权配置">
            <monaco-editor v-model="form.authApiConfig" language="json" height="150px" />
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="入参、请求头与响应映射" name="mapping">
          <el-tabs v-model="templateTab" type="card" class="template-tabs">
            <el-tab-pane label="HTTP 接口模板" name="HTTP">
              <div class="template-help">适用于第三方 HTTP/HTTPS 接口，Header/Query/请求体都可以从规则入参中取值。</div>
              <el-button size="mini" @click="applyTemplate('HTTP')">填入 HTTP 示例</el-button>
            </el-tab-pane>
            <el-tab-pane label="内部规则模板" name="RULE_ENGINE">
              <div class="template-help">适用于协议为“内部规则引擎”的数据源，接口地址填写已发布规则编码。</div>
              <el-button size="mini" @click="applyTemplate('RULE_ENGINE')">填入内部规则示例</el-button>
            </el-tab-pane>
          </el-tabs>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="Header配置">
                <monaco-editor v-model="form.headerConfig" language="json" height="150px" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="Query配置">
                <monaco-editor v-model="form.queryConfig" language="json" height="150px" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="入参映射">
                <monaco-editor v-model="form.requestMapping" language="json" height="150px" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="响应映射">
                <monaco-editor v-model="form.responseMapping" language="json" height="150px" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="请求体模板">
            <monaco-editor v-model="form.bodyTemplate" language="json" height="150px" />
          </el-form-item>
        </el-collapse-item>

        <el-collapse-item title="超时、重试、异步与计费" name="runtime">
          <el-row :gutter="12">
            <el-col :span="8">
              <el-form-item label="超时毫秒">
                <el-input-number v-model="form.timeoutMs" :min="100" :step="500" style="width:100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="重试次数">
                <el-input-number v-model="form.retryCount" :min="0" :max="10" style="width:100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="重试间隔">
                <el-input-number v-model="form.retryIntervalMs" :min="0" :step="100" style="width:100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="响应缓存秒">
                <el-input-number v-model="form.responseCacheSeconds" :min="0" :step="60" style="width:100%" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="单次价格">
                <el-input-number v-model="form.unitPrice" :min="0" :precision="6" :step="0.01" style="width:100%" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="异步回调地址">
                <el-input v-model="form.asyncCallbackUrl" placeholder="异步接口回调地址" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="异步结果路径">
                <el-input v-model="form.asyncResultPath" placeholder="如 data.result" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="计费项编码">
                <el-input v-model="form.billingItemCode" placeholder="如 EXT_CREDIT_REPORT" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="计费条件">
                <monaco-editor v-model="form.billingCondition" language="json" height="110px" />
                <div class="field-help">空表示正常计费；示例 {"path":"body.status","operator":"==","value":0}</div>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="兜底返回">
                <monaco-editor v-model="form.fallbackValue" language="json" height="110px" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-collapse-item>
      </el-collapse>

      <el-form-item label="说明" class="description-item">
        <el-input v-model="form.description" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="状态">
        <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
      </el-form-item>
    </el-form>
  </div>
</template>

<script>
import {
  createApiConfig,
  getApiConfig,
  listDatasources,
  updateApiConfig
} from '@/api/datasource'
import { listDataObjects } from '@/api/dataObject'
import MonacoEditor from '@/components/MonacoEditor'

export default {
  name: 'ApiDetail',
  components: { MonacoEditor },
  data() {
    return {
      datasourceOptions: [],
      dataObjectOptions: [],
      saving: false,
      form: this.emptyForm(),
      templateTab: 'HTTP',
      collapse: ['auth', 'mapping', 'runtime'],
      rules: {
        datasourceId: [{ required: true, message: '请选择数据源', trigger: 'change' }],
        apiCode: [{ required: true, message: '请输入接口编码', trigger: 'blur' }],
        apiName: [{ required: true, message: '请输入接口名称', trigger: 'blur' }],
        endpointUrl: [{ required: true, message: '请输入接口地址', trigger: 'blur' }]
      },
      httpMethods: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'],
      contentTypeOptions: [
        'application/json',
        'application/x-www-form-urlencoded',
        'multipart/form-data',
        'text/plain',
        'application/xml'
      ],
      authModeOptions: [
        { label: '继承数据源', value: 'INHERIT' },
        { label: '无', value: 'NONE' },
        { label: 'Basic', value: 'BASIC' },
        { label: 'Bearer', value: 'BEARER' },
        { label: 'API Key', value: 'API_KEY' },
        { label: 'OAuth2', value: 'OAUTH2' },
        { label: 'Token接口', value: 'TOKEN_API' },
        { label: '自定义', value: 'CUSTOM' }
      ],
      exceptionStrategyOptions: [
        { label: '快速失败', value: 'FAIL_FAST' },
        { label: '返回默认值', value: 'RETURN_DEFAULT' },
        { label: '忽略异常', value: 'IGNORE' },
        { label: '使用缓存', value: 'USE_CACHE' }
      ]
    }
  },
  computed: {
    isCreateMode() {
      return !this.$route.params.id || this.$route.params.id === 'new'
    }
  },
  async created() {
    await this.loadDatasourceOptions()
    if (this.isCreateMode) {
      if (this.$route.query.datasourceId) {
        this.form.datasourceId = Number(this.$route.query.datasourceId)
        await this.loadDataObjectOptions(this.resolveDatasourceProjectId(this.form.datasourceId))
      } else {
        await this.loadDataObjectOptions(0)
      }
      return
    }
    await this.loadDetail()
  },
  methods: {
    emptyForm() {
      return {
        id: null, datasourceId: null, apiCode: '', apiName: '', requestMethod: 'POST', endpointUrl: '',
        contentType: 'application/json', requestMode: 'SYNC', requestObjectId: null, responseObjectId: null,
        headerConfig: '', queryConfig: '', requestMapping: '', responseMapping: '', bodyTemplate: '',
        authMode: 'INHERIT', authApiConfig: '', tokenCacheSeconds: 0, timeoutMs: 3000, retryCount: 0,
        retryIntervalMs: 200, responseCacheSeconds: 0, exceptionStrategy: 'FAIL_FAST', fallbackValue: '', asyncCallbackUrl: '',
        asyncResultPath: '', billingItemCode: '', billingCondition: '', unitPrice: 0, description: '', status: 1
      }
    },
    async loadDatasourceOptions() {
      const res = await listDatasources({ pageNum: 1, pageSize: 500, status: 1 })
      this.datasourceOptions = (res.data && res.data.records) || []
    },
    async loadDataObjectOptions(projectId) {
      try {
        const res = await listDataObjects(projectId || 0)
        this.dataObjectOptions = Array.isArray(res.data) ? res.data : (Array.isArray(res) ? res : [])
      } catch (e) {
        this.dataObjectOptions = []
      }
    },
    async loadDetail() {
      const res = await getApiConfig(this.$route.params.id)
      const data = res && res.data ? res.data : res
      this.form = { ...this.emptyForm(), ...data }
      await this.loadDataObjectOptions(this.resolveDatasourceProjectId(this.form.datasourceId))
    },
    onDatasourceChange(datasourceId) {
      this.form.requestObjectId = null
      this.form.responseObjectId = null
      this.loadDataObjectOptions(this.resolveDatasourceProjectId(datasourceId))
    },
    resolveDatasourceProjectId(datasourceId) {
      const datasource = this.datasourceOptions.find(item => String(item.id) === String(datasourceId))
      return datasource && datasource.projectId ? datasource.projectId : 0
    },
    isRuleEngineDatasource(datasourceId) {
      const id = datasourceId || this.form.datasourceId
      const datasource = this.datasourceOptions.find(item => String(item.id) === String(id))
      return datasource && datasource.protocol === 'RULE_ENGINE'
    },
    normalizeForm(form) {
      const data = { ...form }
      if (!data.requestObjectId) data.requestObjectId = null
      if (!data.responseObjectId) data.responseObjectId = null
      const jsonFields = {
        headerConfig: 'Header配置',
        queryConfig: 'Query配置',
        requestMapping: '入参映射',
        responseMapping: '响应映射',
        authApiConfig: '接口鉴权配置',
        bodyTemplate: '请求体模板',
        billingCondition: '计费条件',
        fallbackValue: '兜底返回'
      }
      Object.keys(jsonFields).forEach(key => {
        data[key] = this.blankToNull(data[key])
        this.assertJson(data[key], jsonFields[key])
      })
      return data
    },
    handleSave() {
      this.$refs.form.validate(async valid => {
        if (!valid) return
        let data
        try {
          data = this.normalizeForm(this.form)
        } catch (e) {
          this.$message.error(e.message)
          return
        }
        this.saving = true
        try {
          if (data.id) {
            await updateApiConfig(data)
            this.$message.success('更新成功')
          } else {
            await createApiConfig(data)
            this.$message.success('创建成功')
          }
          this.$router.push({ path: '/datasource', query: { tab: 'api' } })
        } finally {
          this.saving = false
        }
      })
    },
    applyTemplate(type) {
      if (type === 'RULE_ENGINE') {
        this.form.requestMethod = 'POST'
        this.form.contentType = 'application/json'
        this.form.endpointUrl = this.form.endpointUrl || 'RC_PRICING_TABLE'
        this.form.requestMapping = this.stringifyJson({
          ruleCode: this.form.endpointUrl || 'RC_PRICING_TABLE',
          params: {
            customerType: '$.customerType',
            productLine: '$.productLine'
          }
        })
        this.form.responseMapping = this.stringifyJson({
          decision: 'body.decision',
          rate: 'body.rate',
          score: 'body.score'
        })
        this.form.bodyTemplate = ''
        return
      }
      this.form.requestMethod = 'POST'
      this.form.contentType = 'application/json'
      this.form.headerConfig = this.stringifyJson({ 'X-Request-Id': '${requestId}' })
      this.form.requestMapping = this.stringifyJson({
        idNo: '$.customer.idNo',
        mobile: '$.customer.mobile',
        name: '$.customer.name'
      })
      this.form.responseMapping = this.stringifyJson({
        score: 'body.data.score',
        riskLevel: 'body.data.riskLevel',
        hitReason: 'body.data.reason'
      })
      this.form.bodyTemplate = this.stringifyJson({
        certNo: '${customer.idNo}',
        mobile: '${customer.mobile}',
        name: '${customer.name}'
      })
    },
    endpointPlaceholder() {
      return this.isRuleEngineDatasource() ? '已发布规则编码，如 RC_PRICING_TABLE' : '/v1/report/query 或完整 URL'
    },
    endpointHelp() {
      return this.isRuleEngineDatasource()
        ? '内部规则引擎接口会按规则编码查找已发布版本，不发起外部 HTTP 请求。'
        : 'HTTP 接口可填写相对路径或完整 URL；完整 URL 会覆盖数据源基础地址。'
    },
    dataObjectLabel(item) {
      const code = item.scriptName || item.objectCode || ''
      const name = item.objectName || item.objectLabel || ''
      const type = item.objectType ? '[' + item.objectType + '] ' : ''
      return type + (name || code) + (code && name !== code ? ' / ' + code : '')
    },
    stringifyJson(value) {
      return JSON.stringify(value, null, 2)
    },
    assertJson(value, label) {
      if (value == null || value === '') return
      try {
        JSON.parse(value)
      } catch (e) {
        throw new Error(label + '不是合法 JSON：' + e.message)
      }
    },
    blankToNull(value) {
      return value == null || String(value).trim() === '' ? null : value
    }
  }
}
</script>

<style lang="scss" scoped>
.api-detail-page {
  .detail-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 14px;
  }
  .detail-title {
    font-size: 20px;
    font-weight: 700;
    color: #1f2937;
  }
  .detail-meta,
  .field-help,
  .template-help {
    color: #64748b;
  }
  .detail-meta {
    font-size: 13px;
    margin-top: 4px;
  }
  .detail-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .detail-form {
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 4px;
    padding: 16px;
  }
  .field-help,
  .template-help {
    font-size: 12px;
    line-height: 1.6;
    margin-top: 4px;
  }
  .template-tabs {
    margin-bottom: 12px;
  }
  .description-item {
    margin-top: 12px;
  }
  .json-input ::v-deep textarea {
    font-family: Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    line-height: 1.5;
  }
}
</style>
