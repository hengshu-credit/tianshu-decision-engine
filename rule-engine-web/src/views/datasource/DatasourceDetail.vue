<template>
  <div class="uiue-list-page datasource-detail-page">
    <div class="detail-header uiue-detail-header">
      <div class="uiue-detail-header__title">
        <el-button size="small" class="uiue-detail-back" aria-label="返回" title="返回外数列表" @click="$router.push('/datasource')"><app-icon name="Back" /></el-button>
        <div>
        <div class="detail-title">
          {{ isCreateMode ? '新建外数数据源' : '编辑外数数据源' }}
        </div>
        <div class="detail-meta">
          {{ form.datasourceName || '未命名数据源' }} /
          {{ form.datasourceCode || '待填写编码' }}
        </div>
        </div>
      </div>
      <div class="detail-actions uiue-detail-header__actions">
        <el-button
          v-permission="'datasource:edit'"
          size="small"
          type="primary"
          :loading="saving"
          :disabled="!configurationReady"
          data-testid="save-external-datasource"
          @click="handleSave"
          >保存</el-button
        >
      </div>
    </div>

    <div v-if="!configurationReady && !detailError" role="status">正在加载外数配置，请稍候…</div>
    <el-alert v-if="detailError" title="外数配置加载失败" type="error" :closable="false" show-icon>
      <span>{{ detailError }}</span>
      <el-button @click="loadDetail">重新加载</el-button>
    </el-alert>
    <el-form
      v-if="!detailError"
      :disabled="!configurationReady || saving"
      :aria-busy="detailLoading"
      ref="form"
      :model="form"
      :rules="rules"
      label-width="110px"
      size="small"
      class="detail-form"
    >
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="作用范围">
            <el-select
              v-model="form.scope"
              style="width: 100%"
              @change="onScopeChange"
            >
              <el-option label="全局" value="GLOBAL" />
              <el-option label="项目级" value="PROJECT" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item
            v-if="form.scope === 'PROJECT'"
            label="所属项目"
            prop="projectId"
          >
            <project-resource-select
              v-model="form.projectId"
              filterable
              placeholder="请选择项目"
              style="width: 100%"
              @change="loadApiPickerOptions"
             :projects="projects" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="数据源编码" prop="datasourceCode">
            <el-input
              v-model="form.datasourceCode"
              placeholder="如 credit_report_provider"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="数据源名称" prop="datasourceName">
            <el-input
              v-model="form.datasourceName"
              placeholder="如 征信报告外数"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="服务提供方">
            <el-input
              v-model="form.providerName"
              placeholder="第三方机构或系统名称"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="鉴权方式">
            <el-select
              v-model="form.authType"
              style="width: 100%"
              @change="onAuthTypeChange"
            >
              <el-option
                v-for="item in authTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
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
            <el-select
              v-model="form.protocol"
              style="width: 100%"
              @change="onProtocolChange"
            >
              <el-option
                v-for="item in protocolOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Token缓存秒">
            <el-input-number
              v-model="form.tokenCacheSeconds"
              :min="0"
              :step="60"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <api-auth-editor v-if="form.authType !== 'CUSTOM'" ref="authEditor" v-model:value="authConfig" :type="form.authType" :vars="apiPickerVars" :functions="apiPickerFunctions" />
      <el-form-item v-else label="自定义配置"><monaco-editor v-model:value="form.authConfig" language="json" height="180px" /></el-form-item>
      <el-form-item label="说明">
        <el-input v-model="form.description" type="textarea" :rows="2" />
      </el-form-item>
      <el-form-item label="状态">
        <el-switch
          v-model="form.status"
          :active-value="1"
          :inactive-value="0"
          active-text="启用"
          inactive-text="停用"
        />
      </el-form-item>
    </el-form>
  </div>
</template>

<script>
import ProjectResourceSelect from '@/components/ProjectResourceSelect.vue'
import { hasPermission } from '@/security/permissionState'
import { loadApiReferenceCatalog } from '@/utils/apiReferenceCatalog'
import { editableResource } from '@/api/governance'
import { completeResourceChange } from '@/utils/governanceWorkspace'
import ApiAuthEditor from '@/components/common/ApiAuthEditor.vue'
import { emptyApiAuthConfig, readApiAuthConfig, apiAuthConfig } from '@/utils/apiAuthConfig'
import workspaceTabTitleMixin from '@/mixins/workspaceTabTitleMixin'
import {
  createDatasource,
  getDatasource,
  updateDatasource,
} from '@/api/datasource'
import { listProjects } from '@/api/project'
import MonacoEditor from '@/components/MonacoEditor'
import { routeProjectId } from '@/utils/projectContext'

export default {
  name: 'DatasourceDetail',
  mixins: [workspaceTabTitleMixin(vm => vm.form.datasourceName)],
  components: { ProjectResourceSelect,
    MonacoEditor, ApiAuthEditor },
  data() {
    return {
      projects: [],
      saving: false,
      detailLoading: false,
      detailLoaded: false,
      detailError: '',
      detailLoadSequence: 0,
      form: this.emptyForm(),
      authConfig: this.emptyAuthConfig('NONE'),
      apiPickerVars: [],
      apiPickerRequestId: 0,
      apiPickerFunctions: [],
      rules: {
        datasourceCode: [
          { required: true, message: '请输入数据源编码', trigger: 'blur' },
        ],
        datasourceName: [
          { required: true, message: '请输入数据源名称', trigger: 'blur' },
        ],
        baseUrl: [
          {
            validator: (rule, value, callback) => {
              if (
                this.form.protocol === 'RULE_ENGINE' ||
                (value != null && String(value).trim() !== '')
              ) {
                callback()
                return
              }
              callback(new Error('请输入基础地址'))
            },
            trigger: 'blur',
          },
        ],
        projectId: [
          { required: true, message: '请选择所属项目', trigger: 'change' },
        ],
      },
      tokenHttpMethods: ['GET', 'POST', 'PUT'],
      contentTypeOptions: [
        'application/json',
        'application/x-www-form-urlencoded',
        'multipart/form-data',
        'text/plain',
        'application/xml',
      ],
      authLocationOptions: [
        { label: '请求头 Header', value: 'HEADER' },
        { label: 'URL Query', value: 'QUERY' },
      ],
      authTypeOptions: [
        { label: '无', value: 'NONE' },
        { label: 'Basic', value: 'BASIC' },
        { label: 'Bearer', value: 'BEARER' },
        { label: 'API Key', value: 'API_KEY' },
        { label: 'OAuth2', value: 'OAUTH2' },
        { label: 'Token接口', value: 'TOKEN_API' },
        { label: '自定义', value: 'CUSTOM' },
      ],
      protocolOptions: [
        { label: 'HTTP', value: 'HTTP' },
        { label: 'HTTPS', value: 'HTTPS' },
        { label: '内部规则引擎', value: 'RULE_ENGINE' },
      ],
    }
  },
  computed: {
    configurationReady() { return this.isCreateMode || (this.detailLoaded && !this.detailLoading && !this.detailError) },
    isCreateMode() {
      return !this.$route.params.id || this.$route.params.id === 'new'
    },
  },
  async created() {
    const projects = this.loadProjects()
    if (!this.isCreateMode) {
      await this.loadDetail()
    } else {
      const projectId = routeProjectId(
        this.$route,
        this.$store && this.$store.state.currentProject
      )
      if (projectId) this.applyProjectContext(projectId)
    }
    await projects
    await this.loadApiPickerOptions()
  },
  activated() { if (!this.isCreateMode && !this.detailLoaded && !this.detailLoading) this.loadDetail() },
  deactivated() { this.detailLoadSequence++; this.detailLoading = false },
  beforeUnmount() { this.detailLoadSequence++ },
  methods: {
    applyProjectContext(projectId) {
      this.form.scope = 'PROJECT'
      this.form.projectId = Number(projectId)
    },
    async loadApiPickerOptions() {
      const requestId = ++this.apiPickerRequestId
      const projectId = this.form.scope === 'GLOBAL' ? 0 : this.form.projectId
      this.apiPickerVars = []; this.apiPickerFunctions = []
      if (projectId == null || projectId === '') return
      const catalog = await loadApiReferenceCatalog(Number(projectId) || 0)
      if (requestId !== this.apiPickerRequestId) return
      this.apiPickerVars = catalog.vars; this.apiPickerFunctions = catalog.functions
      if (catalog.errors.length) this.$message.warning(catalog.errors.join('；'))
    },
    emptyForm() {
      return {
        id: null,
        scope: 'PROJECT',
        projectId: null,
        datasourceCode: '',
        datasourceName: '',
        providerName: '',
        protocol: 'HTTPS',
        baseUrl: '',
        authType: 'NONE',
        authConfig: '',
        tokenCacheSeconds: 0,
        description: '',
        status: 1,
      }
    },
    emptyAuthConfig() { return emptyApiAuthConfig() },
    async loadProjects() {
      try {
        const res = await listProjects({ pageNum: 1, pageSize: 500, status: 1 })
        this.projects = (res.data && res.data.records) || []
      } catch (e) {
        this.projects = []
      }
    },
    async loadDetail() {
      if (this.isCreateMode || this.detailLoading) return
      const id = this.$route.params.id, sequence = ++this.detailLoadSequence
      const current = () => sequence === this.detailLoadSequence && String(this.$route.params.id) === String(id)
      this.detailLoading = true; this.detailLoaded = false; this.detailError = ''
      try {
        const res = await getDatasource(id)
        if (res.data?.id && hasPermission('datasource:edit')) res.data = await editableResource('EXTERNAL_DATASOURCE', res.data.id, res.data)
        if (!current()) return
        const data = res && res.data ? res.data : res
        if (!data?.id || String(data.id) !== String(id)) throw new Error('外数配置不存在或已删除，请返回列表核对。')
        this.form = { ...this.emptyForm(), ...data }
        this.authConfig = this.parseAuthConfig(this.form.authConfig, this.form.authType)
        this.detailLoaded = true
      } catch (error) {
        if (current()) this.detailError = error.message || '无法读取外数配置，请重新加载。'
      } finally {
        if (sequence === this.detailLoadSequence) this.detailLoading = false
      }
    },
    onScopeChange(scope) {
      if (scope === 'GLOBAL') this.form.projectId = 0
      this.loadApiPickerOptions()
    },
    onProtocolChange(protocol) {
      if (protocol === 'RULE_ENGINE' && !this.form.baseUrl)
        this.form.baseUrl = 'rule-engine://local'
      if (
        protocol !== 'RULE_ENGINE' &&
        this.form.baseUrl === 'rule-engine://local'
      )
        this.form.baseUrl = ''
    },
    onAuthTypeChange(type) {
      this.authConfig = this.emptyAuthConfig(type)
      if (type === 'NONE') this.form.authConfig = ''
    },
    normalizeForm(form) {
      const data = { ...form }
      if (data.scope === 'GLOBAL') data.projectId = 0
      if (data.protocol === 'RULE_ENGINE' && !data.baseUrl)
        data.baseUrl = 'rule-engine://local'
      if (data.protocol !== 'RULE_ENGINE' && !data.baseUrl)
        throw new Error('请输入基础地址')
      data.authConfig = this.buildAuthConfig(data.authType, data.authConfig)
      this.assertJson(data.authConfig, '鉴权配置')
      return data
    },
    buildAuthConfig(type, rawAuthConfig) {
      if (this.$refs?.authEditor && !this.$refs.authEditor.validate()) throw new Error('请修正鉴权配置中的字段名')
      return type === 'CUSTOM' ? rawAuthConfig : JSON.stringify(apiAuthConfig(type, this.authConfig))
    },
    parseAuthConfig(text) { return readApiAuthConfig(text) },
    handleSave() {
      if (!this.configurationReady || this.saving) return
      this.$refs.form.validate(async (valid) => {
        if (!valid || !this.configurationReady) return
        let data
        try {
          data = this.normalizeForm(this.form)
        } catch (e) {
          this.$message.error(e.message)
          return
        }
        this.saving = true
        try {
          const response = data.id
            ? await updateDatasource(data)
            : await createDatasource(data)
          await completeResourceChange(this, response)
            if (!this.form.id && response.data?.resourceId) {
              this.form.id = response.data.resourceId
              this.detailLoaded = true
              await this.$router.replace('/datasource/source/' + this.form.id)
            }
        } finally {
          this.saving = false
        }
      })
    },
    baseUrlPlaceholder() {
      return this.form.protocol === 'RULE_ENGINE'
        ? 'rule-engine://local'
        : 'https://api.example.com'
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
    },
  },
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
    color: var(--tianshu-text-primary);
  }
  .detail-meta,
  .field-help,
  .section-help {
    color: var(--tianshu-text-tertiary);
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
    background: var(--tianshu-bg-surface);
    border: 1px solid var(--tianshu-border);
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
    border: 1px solid var(--tianshu-border);
    border-radius: 4px;
    padding: 12px 12px 0;
    margin-bottom: 12px;
  }
  .section-title {
    color: var(--tianshu-text-primary);
    font-weight: 700;
    margin-bottom: 6px;
  }
  .json-input :deep(textarea) {
    font-family: Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    line-height: 1.5;
  }
  .token-header-name-item :deep(.el-form-item__label) {
    white-space: nowrap;
  }
  code {
    color: #1e40af;
    background: #eff6ff;
    border-radius: 3px;
    padding: 0 4px;
  }
}
</style>
