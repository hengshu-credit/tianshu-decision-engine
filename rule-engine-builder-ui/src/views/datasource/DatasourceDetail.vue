<template>
  <div class="uiue-list-page datasource-detail-page">
    <div class="detail-header">
      <div>
        <div class="detail-title">{{ isCreateMode ? '新建外数数据源' : '编辑外数数据源' }}</div>
        <div class="detail-meta">{{ form.datasourceName || '未命名数据源' }} / {{ form.datasourceCode || '待填写编码' }}</div>
      </div>
      <div class="detail-actions">
        <el-button size="small" @click="$router.push('/datasource')">返回</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </div>
    </div>

    <el-form ref="form" :model="form" :rules="rules" label-width="110px" size="small" class="detail-form">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="作用范围">
            <el-select v-model="form.scope" style="width:100%" @change="onScopeChange">
              <el-option label="全局" value="GLOBAL" />
              <el-option label="项目级" value="PROJECT" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item v-if="form.scope === 'PROJECT'" label="所属项目" prop="projectId">
            <el-select v-model="form.projectId" filterable placeholder="请选择项目" style="width:100%">
              <el-option v-for="project in projects" :key="project.id" :label="project.projectName" :value="project.id" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="数据源编码" prop="datasourceCode">
            <el-input v-model="form.datasourceCode" placeholder="如 credit_report_provider" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="数据源名称" prop="datasourceName">
            <el-input v-model="form.datasourceName" placeholder="如 征信报告外数" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="服务提供方">
            <el-input v-model="form.providerName" placeholder="第三方机构或系统名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="鉴权方式">
            <el-select v-model="form.authType" style="width:100%" @change="onAuthTypeChange">
              <el-option v-for="item in authTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="基础地址" prop="baseUrl">
        <el-input v-model="form.baseUrl" :placeholder="baseUrlPlaceholder()" />
        <div class="field-help">{{ baseUrlHelp() }}</div>
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="协议">
            <el-select v-model="form.protocol" style="width:100%" @change="onProtocolChange">
              <el-option v-for="item in protocolOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Token缓存秒">
            <el-input-number v-model="form.tokenCacheSeconds" :min="0" :step="60" style="width:100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <div v-if="form.authType !== 'NONE'" class="form-section">
        <div class="section-title">数据源鉴权配置</div>
        <div class="section-help">API 接口选择“继承数据源”时会使用这些配置。路径可写 <code>body.data.token</code> 或 <code>headers.Authorization</code>。</div>
        <template v-if="form.authType === 'BASIC'">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="用户名">
                <el-input v-model="authConfig.username" autocomplete="off" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="密码">
                <el-input v-model="authConfig.password" type="password" autocomplete="new-password" show-password />
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="form.authType === 'BEARER'">
          <el-form-item label="Token">
            <el-input v-model="authConfig.token" type="password" autocomplete="new-password" show-password placeholder="直接写固定 token；动态 token 请使用 Token接口" />
          </el-form-item>
        </template>
        <template v-else-if="form.authType === 'API_KEY'">
          <el-row :gutter="12">
            <el-col :span="8">
              <el-form-item label="放置位置">
                <el-select v-model="authConfig.location" style="width:100%">
                  <el-option v-for="item in authLocationOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="字段名">
                <el-input v-model="authConfig.name" placeholder="如 X-API-Key" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="字段值">
                <el-input v-model="authConfig.value" type="password" autocomplete="new-password" show-password />
              </el-form-item>
            </el-col>
          </el-row>
        </template>
        <template v-else-if="form.authType === 'TOKEN_API' || form.authType === 'OAUTH2'">
          <el-row :gutter="12">
            <el-col :span="8">
              <el-form-item label="请求方式">
                <el-select v-model="authConfig.method" style="width:100%">
                  <el-option v-for="method in tokenHttpMethods" :key="method" :label="method" :value="method" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="16">
              <el-form-item label="鉴权地址">
                <el-input v-model="authConfig.tokenUrl" placeholder="/oauth/token 或完整 URL" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="8">
              <el-form-item label="Content-Type">
                <el-select v-model="authConfig.contentType" filterable allow-create clearable style="width:100%">
                  <el-option v-for="item in contentTypeOptions" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="Token路径">
                <el-input v-model="authConfig.tokenPath" placeholder="body.data.access_token 或 headers.Authorization" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="过期时间路径">
                <el-input v-model="authConfig.expiresInPath" placeholder="body.expires_in 或 headers.X-Expires-In" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="鉴权请求头">
                <monaco-editor v-model="authConfig.headers" language="json" height="130px" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="鉴权请求体">
                <monaco-editor v-model="authConfig.body" language="json" height="130px" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="8">
              <el-form-item label="Token放置方式">
                <el-select v-model="authConfig.tokenPlacement" style="width:100%">
                  <el-option label="写入请求Header" value="HEADER" />
                  <el-option label="仅供请求脚本使用" value="SCRIPT_ONLY" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="Token Header名称">
                <el-input v-model="authConfig.tokenHeaderName" placeholder="默认 Authorization；冰鉴填写 token_id" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="Token前缀">
                <el-input v-model="authConfig.tokenPrefix" placeholder="默认 Bearer；冰鉴留空" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="Token响应脚本">
            <monaco-editor v-model="authConfig.tokenResponseScript" language="javascript" height="150px" />
            <div class="field-help">可选。用于 XML 包裹、加密响应等场景；上下文为 body/rawBody/httpStatus/headers/input，返回解析后的对象，再按 Token 路径提取。</div>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="自定义JSON">
            <monaco-editor v-model="form.authConfig" language="json" height="150px" />
          </el-form-item>
        </template>
      </div>
      <el-form-item label="说明">
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
  createDatasource,
  getDatasource,
  updateDatasource
} from '@/api/datasource'
import { listProjects } from '@/api/project'
import MonacoEditor from '@/components/MonacoEditor'

export default {
  name: 'DatasourceDetail',
  components: { MonacoEditor },
  data() {
    return {
      projects: [],
      saving: false,
      form: this.emptyForm(),
      authConfig: this.emptyAuthConfig('NONE'),
      rules: {
        datasourceCode: [{ required: true, message: '请输入数据源编码', trigger: 'blur' }],
        datasourceName: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
        baseUrl: [{
          validator: (rule, value, callback) => {
            if (this.form.protocol === 'RULE_ENGINE' || (value != null && String(value).trim() !== '')) {
              callback()
              return
            }
            callback(new Error('请输入基础地址'))
          },
          trigger: 'blur'
        }],
        projectId: [{ required: true, message: '请选择所属项目', trigger: 'change' }]
      },
      tokenHttpMethods: ['GET', 'POST', 'PUT'],
      contentTypeOptions: [
        'application/json',
        'application/x-www-form-urlencoded',
        'multipart/form-data',
        'text/plain',
        'application/xml'
      ],
      authLocationOptions: [
        { label: '请求头 Header', value: 'HEADER' },
        { label: 'URL Query', value: 'QUERY' }
      ],
      authTypeOptions: [
        { label: '无', value: 'NONE' },
        { label: 'Basic', value: 'BASIC' },
        { label: 'Bearer', value: 'BEARER' },
        { label: 'API Key', value: 'API_KEY' },
        { label: 'OAuth2', value: 'OAUTH2' },
        { label: 'Token接口', value: 'TOKEN_API' },
        { label: '自定义', value: 'CUSTOM' }
      ],
      protocolOptions: [
        { label: 'HTTP', value: 'HTTP' },
        { label: 'HTTPS', value: 'HTTPS' },
        { label: '内部规则引擎', value: 'RULE_ENGINE' }
      ]
    }
  },
  computed: {
    isCreateMode() {
      return !this.$route.params.id || this.$route.params.id === 'new'
    }
  },
  async created() {
    await this.loadProjects()
    if (!this.isCreateMode) {
      await this.loadDetail()
    }
  },
  methods: {
    emptyForm() {
      return {
        id: null, scope: 'PROJECT', projectId: null, datasourceCode: '', datasourceName: '', providerName: '',
        protocol: 'HTTPS', baseUrl: '', authType: 'NONE', authConfig: '', tokenCacheSeconds: 0,
        description: '', status: 1
      }
    },
    emptyAuthConfig(type) {
      const common = {
        username: '', password: '', token: '', name: 'X-API-Key', value: '', location: 'HEADER',
        tokenUrl: '/oauth/token', method: 'POST', contentType: 'application/json',
        tokenPath: 'body.access_token', expiresInPath: 'body.expires_in',
        tokenPlacement: 'HEADER', tokenHeaderName: 'Authorization', tokenPrefix: 'Bearer ',
        tokenResponseScript: '',
        headers: '{}', body: '{"grant_type":"client_credentials"}'
      }
      if (type === 'TOKEN_API' || type === 'OAUTH2') {
        common.tokenPath = 'body.access_token'
        common.expiresInPath = 'body.expires_in'
      }
      return common
    },
    async loadProjects() {
      try {
        const res = await listProjects({ pageNum: 1, pageSize: 500, status: 1 })
        this.projects = (res.data && res.data.records) || []
      } catch (e) {
        this.projects = []
      }
    },
    async loadDetail() {
      const res = await getDatasource(this.$route.params.id)
      const data = res && res.data ? res.data : res
      this.form = { ...this.emptyForm(), ...data }
      this.authConfig = this.parseAuthConfig(this.form.authConfig, this.form.authType)
    },
    onScopeChange(scope) {
      if (scope === 'GLOBAL') this.form.projectId = 0
    },
    onProtocolChange(protocol) {
      if (protocol === 'RULE_ENGINE' && !this.form.baseUrl) this.form.baseUrl = 'rule-engine://local'
      if (protocol !== 'RULE_ENGINE' && this.form.baseUrl === 'rule-engine://local') this.form.baseUrl = ''
    },
    onAuthTypeChange(type) {
      this.authConfig = this.emptyAuthConfig(type)
      if (type === 'NONE') this.form.authConfig = ''
    },
    normalizeForm(form) {
      const data = { ...form }
      if (data.scope === 'GLOBAL') data.projectId = 0
      if (data.protocol === 'RULE_ENGINE' && !data.baseUrl) data.baseUrl = 'rule-engine://local'
      if (data.protocol !== 'RULE_ENGINE' && !data.baseUrl) throw new Error('请输入基础地址')
      data.authConfig = this.buildAuthConfig(data.authType, data.authConfig)
      this.assertJson(data.authConfig, '鉴权配置')
      return data
    },
    buildAuthConfig(type, rawAuthConfig) {
      if (!type || type === 'NONE') return null
      if (type === 'BASIC') {
        return this.stringifyJson({ username: this.authConfig.username, password: this.authConfig.password })
      }
      if (type === 'BEARER') return this.stringifyJson({ token: this.authConfig.token })
      if (type === 'API_KEY') {
        return this.stringifyJson({ location: this.authConfig.location, name: this.authConfig.name, value: this.authConfig.value })
      }
      if (type === 'TOKEN_API' || type === 'OAUTH2') {
        return this.stringifyJson({
          tokenUrl: this.authConfig.tokenUrl,
          method: this.authConfig.method,
          contentType: this.authConfig.contentType,
          headers: this.parseJsonText(this.authConfig.headers, '鉴权请求头'),
          body: this.parseJsonText(this.authConfig.body, '鉴权请求体'),
          tokenPath: this.authConfig.tokenPath,
          expiresInPath: this.authConfig.expiresInPath,
          tokenPlacement: this.authConfig.tokenPlacement,
          tokenHeaderName: this.authConfig.tokenHeaderName,
          tokenPrefix: this.authConfig.tokenPrefix,
          tokenResponseScript: this.authConfig.tokenResponseScript
        })
      }
      return this.blankToNull(rawAuthConfig)
    },
    parseAuthConfig(text, type) {
      const base = this.emptyAuthConfig(type)
      if (!text) return base
      try {
        const parsed = JSON.parse(text)
        const merged = { ...base, ...parsed }
        if (parsed.headers && typeof parsed.headers !== 'string') merged.headers = this.stringifyJson(parsed.headers)
        if (parsed.body && typeof parsed.body !== 'string') merged.body = this.stringifyJson(parsed.body)
        return merged
      } catch (e) {
        return base
      }
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
            await updateDatasource(data)
            this.$message.success('更新成功')
          } else {
            await createDatasource(data)
            this.$message.success('创建成功')
          }
          this.$router.push('/datasource')
        } finally {
          this.saving = false
        }
      })
    },
    baseUrlPlaceholder() {
      return this.form.protocol === 'RULE_ENGINE' ? 'rule-engine://local' : 'https://api.example.com'
    },
    baseUrlHelp() {
      return this.form.protocol === 'RULE_ENGINE'
        ? '内部规则引擎数据源可留空，保存时会自动使用 rule-engine://local。'
        : '填写第三方服务基础地址；接口地址为相对路径时会拼接到该地址后。'
    },
    stringifyJson(value) {
      return JSON.stringify(value, null, 2)
    },
    parseJsonText(value, label) {
      if (value == null || String(value).trim() === '') return {}
      try {
        return JSON.parse(value)
      } catch (e) {
        throw new Error(label + '不是合法 JSON：' + e.message)
      }
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
.datasource-detail-page {
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
  .section-help {
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
  .section-help {
    font-size: 12px;
    line-height: 1.6;
    margin-top: 4px;
  }
  .form-section {
    border: 1px solid #e5e7eb;
    border-radius: 4px;
    padding: 12px 12px 0;
    margin-bottom: 12px;
  }
  .section-title {
    color: #334155;
    font-weight: 700;
    margin-bottom: 6px;
  }
  .json-input ::v-deep textarea {
    font-family: Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    line-height: 1.5;
  }
  code {
    color: #1e40af;
    background: #eff6ff;
    border-radius: 3px;
    padding: 0 4px;
  }
}
</style>
