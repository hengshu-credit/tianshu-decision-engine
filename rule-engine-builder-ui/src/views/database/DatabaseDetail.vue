<template>
  <div class="uiue-list-page database-detail-page">
    <div class="detail-header">
      <div>
        <div class="detail-title">
          {{ isCreateMode ? '新建数据库数据源' : '编辑数据库数据源' }}
        </div>
        <div class="detail-meta">
          {{ form.datasourceName || '未命名数据源' }} /
          {{ form.datasourceCode || '待填写编码' }}
        </div>
      </div>
      <div class="detail-actions">
        <el-button size="small" @click="$router.push('/database')"
          >返回</el-button
        >
        <el-button size="small" @click="handleTestDraft">测试连接</el-button>
        <el-button
          size="small"
          type="primary"
          :loading="saving"
          @click="handleSubmit"
          >保存</el-button
        >
      </div>
    </div>

    <el-form
      ref="form"
      :model="form"
      :rules="rules"
      label-width="120px"
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
            <el-select
              v-model="form.projectId"
              filterable
              placeholder="请选择项目"
              style="width: 100%"
            >
              <el-option
                v-for="project in projects"
                :key="project.id"
                :label="project.projectName"
                :value="project.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="数据源编码" prop="datasourceCode">
            <el-input
              v-model="form.datasourceCode"
              placeholder="如 customer_core_db"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="数据源名称" prop="datasourceName">
            <el-input
              v-model="form.datasourceName"
              placeholder="如 客户核心库"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="数据库类型">
            <el-select
              v-model="form.dbType"
              style="width: 100%"
              @change="onDbTypeChange"
            >
              <el-option
                v-for="item in dbTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="驱动类">
            <el-input v-model="form.driverClassName" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="连接方式">
        <el-radio-group v-model="form.connectionMode">
          <el-radio-button value="DIRECT">直连</el-radio-button>
          <el-radio-button value="SSH_TUNNEL">SSH隧道</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="14">
          <el-form-item label="数据库主机">
            <el-input
              v-model="form.host"
              placeholder="如 mysql.internal"
              @update:model-value="onJdbcPartChange"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="端口">
            <el-input-number
              v-model="form.port"
              :min="1"
              :max="65535"
              controls-position="right"
              style="width: 100%"
              :controls="false"
              @change="onJdbcPartChange"
            />
          </el-form-item>
        </el-col>
        <el-col :span="14">
          <el-form-item label="库名/服务名">
            <el-input
              v-model="form.databaseName"
              placeholder="如 rule_engine"
              @update:model-value="onJdbcPartChange"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="扩展参数">
        <el-input
          v-model="form.jdbcParams"
          placeholder="useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai，也可直接追加在JDBC URL后"
          @update:model-value="onJdbcPartChange"
        />
      </el-form-item>
      <el-form-item label="JDBC URL" prop="jdbcUrl">
        <el-input
          v-model="form.jdbcUrl"
          placeholder="jdbc:mysql://host:3306/db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
          @update:model-value="form.jdbcAutoBuild = false"
        >
          <template v-slot:append>
            <el-button @click="generateJdbcUrl(true)">生成</el-button>
          </template>
        </el-input>
        <el-checkbox
          v-model="form.jdbcAutoBuild"
          class="jdbc-checkbox"
          @change="onJdbcAutoBuildChange"
          >按上方表单自动生成 JDBC URL</el-checkbox
        >
      </el-form-item>
      <div v-if="form.connectionMode === 'SSH_TUNNEL'" class="form-section">
        <div class="section-title">SSH 隧道</div>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="SSH主机">
              <el-input v-model="form.sshHost" placeholder="堡垒机地址" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="SSH端口">
              <el-input-number
                v-model="form.sshPort"
                :min="1"
                :max="65535"
                controls-position="right"
                style="width: 100%"
                :controls="false"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="SSH用户">
              <el-input v-model="form.sshUsername" autocomplete="off" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="SSH密码">
              <el-input
                v-model="form.sshPassword"
                type="password"
                autocomplete="new-password"
                show-password
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="私钥口令">
              <el-input
                v-model="form.sshPassphrase"
                type="password"
                autocomplete="new-password"
                show-password
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="16">
            <el-form-item label="SSH私钥">
              <el-input
                v-model="form.sshPrivateKey"
                class="code-input"
                type="textarea"
                :rows="4"
                placeholder="可粘贴 PEM 私钥内容；密码和私钥二选一"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="SSH超时">
              <el-input-number
                v-model="form.sshTimeoutMs"
                :min="1000"
                :step="1000"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </div>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="用户名">
            <el-input v-model="form.username" autocomplete="off" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              type="password"
              autocomplete="new-password"
              show-password
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="最大连接数">
            <el-input-number
              v-model="form.maxPoolSize"
              :min="1"
              :max="100"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="最小空闲">
            <el-input-number
              v-model="form.minIdle"
              :min="0"
              :max="100"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="连接超时">
            <el-input-number
              v-model="form.connectionTimeoutMs"
              :min="100"
              :step="500"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="空闲超时">
            <el-input-number
              v-model="form.idleTimeoutMs"
              :min="10000"
              :step="60000"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="校验 SQL">
        <monaco-editor
          v-model:value="form.validationQuery"
          language="sql"
          theme="rule-sql-light"
          height="90px"
        />
      </el-form-item>
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
import {
  createDbDatasource,
  getDbDatasource,
  testDbDatasourceDraft,
  updateDbDatasource,
} from '@/api/database'
import { listProjects } from '@/api/project'
import MonacoEditor from '@/components/MonacoEditor'

export default {
  name: 'DatabaseDetail',
  components: { MonacoEditor },
  data() {
    return {
      projects: [],
      saving: false,
      form: this.emptyForm(),
      rules: {
        datasourceCode: [
          { required: true, message: '请输入数据源编码', trigger: 'blur' },
        ],
        datasourceName: [
          { required: true, message: '请输入数据源名称', trigger: 'blur' },
        ],
        jdbcUrl: [
          { required: true, message: '请输入 JDBC URL', trigger: 'blur' },
        ],
        projectId: [
          { required: true, message: '请选择所属项目', trigger: 'change' },
        ],
      },
      dbTypeOptions: [
        {
          label: 'MySQL',
          value: 'MYSQL',
          driver: 'com.mysql.cj.jdbc.Driver',
          validation: 'SELECT 1',
          port: 3306,
          params:
            'useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false',
        },
        {
          label: 'PostgreSQL',
          value: 'POSTGRESQL',
          driver: 'org.postgresql.Driver',
          validation: 'SELECT 1',
          port: 5432,
          params: 'sslmode=disable',
        },
        {
          label: 'Oracle',
          value: 'ORACLE',
          driver: 'oracle.jdbc.OracleDriver',
          validation: 'SELECT 1 FROM DUAL',
          port: 1521,
          params: '',
        },
        {
          label: 'SQL Server',
          value: 'SQLSERVER',
          driver: 'com.microsoft.sqlserver.jdbc.SQLServerDriver',
          validation: 'SELECT 1',
          port: 1433,
          params: 'encrypt=false;trustServerCertificate=true',
        },
        {
          label: '其他',
          value: 'OTHER',
          driver: '',
          validation: 'SELECT 1',
          port: 0,
          params: '',
        },
      ],
    }
  },
  computed: {
    isCreateMode() {
      return !this.$route.params.id || this.$route.params.id === 'new'
    },
  },
  async created() {
    await this.loadProjects()
    if (this.isCreateMode) {
      this.form = this.emptyForm()
      if (this.$route.query.projectId)
        this.form.projectId = Number(this.$route.query.projectId)
      this.$nextTick(() => this.generateJdbcUrl(false))
      return
    }
    await this.loadDetail()
  },
  methods: {
    emptyForm() {
      return {
        id: null,
        scope: 'PROJECT',
        projectId: null,
        datasourceCode: '',
        datasourceName: '',
        dbType: 'MYSQL',
        connectionMode: 'DIRECT',
        host: '',
        port: 3306,
        databaseName: '',
        jdbcParams:
          'useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false',
        jdbcAutoBuild: true,
        driverClassName: 'com.mysql.cj.jdbc.Driver',
        jdbcUrl: '',
        username: '',
        password: '',
        sshHost: '',
        sshPort: 22,
        sshUsername: '',
        sshPassword: '',
        sshPrivateKey: '',
        sshPassphrase: '',
        sshTimeoutMs: 10000,
        maxPoolSize: 5,
        minIdle: 1,
        connectionTimeoutMs: 3000,
        idleTimeoutMs: 600000,
        validationQuery: 'SELECT 1',
        description: '',
        status: 1,
      }
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
      const res = await getDbDatasource(this.$route.params.id)
      const data = res && res.data ? res.data : res
      this.form = this.normalizeLoadedForm({
        ...this.emptyForm(),
        ...data,
        jdbcAutoBuild: false,
      })
    },
    onScopeChange(scope) {
      if (scope === 'GLOBAL') this.form.projectId = 0
      if (
        scope === 'PROJECT' &&
        (!this.form.projectId || this.form.projectId <= 0)
      )
        this.form.projectId = null
    },
    onDbTypeChange(value) {
      const option = this.dbTypeOptions.find((item) => item.value === value)
      if (!option) return
      if (option.driver) this.form.driverClassName = option.driver
      if (option.validation) this.form.validationQuery = option.validation
      if (option.port) this.form.port = option.port
      this.form.jdbcParams = option.params || ''
      this.onJdbcPartChange()
    },
    onJdbcPartChange() {
      if (this.form.jdbcAutoBuild) this.generateJdbcUrl(false)
    },
    onJdbcAutoBuildChange(enabled) {
      if (enabled) this.generateJdbcUrl(true)
    },
    generateJdbcUrl(showWarning) {
      const jdbcUrl = this.buildJdbcUrl(this.form)
      if (jdbcUrl) {
        this.form.jdbcUrl = jdbcUrl
        return true
      }
      if (showWarning)
        this.$message.warning('请先填写数据库主机、端口和库名/服务名')
      return false
    },
    buildJdbcUrl(form) {
      const host = (form.host || '').trim()
      const port = form.port
      const databaseName = (form.databaseName || '').trim()
      const params = (form.jdbcParams || '').trim()
      if (!host || !port) return ''
      if (form.dbType === 'MYSQL') {
        if (!databaseName) return ''
        return this.appendQuestionParams(
          `jdbc:mysql://${host}:${port}/${databaseName}`,
          params
        )
      }
      if (form.dbType === 'POSTGRESQL') {
        if (!databaseName) return ''
        return this.appendQuestionParams(
          `jdbc:postgresql://${host}:${port}/${databaseName}`,
          params
        )
      }
      if (form.dbType === 'ORACLE') {
        if (!databaseName) return ''
        return this.appendQuestionParams(
          `jdbc:oracle:thin:@//${host}:${port}/${databaseName}`,
          params
        )
      }
      if (form.dbType === 'SQLSERVER') {
        let url = `jdbc:sqlserver://${host}:${port}`
        if (databaseName) url += `;databaseName=${databaseName}`
        if (params) url += `;${params.replace(/^[?;]/, '')}`
        return url
      }
      return form.jdbcUrl || ''
    },
    appendQuestionParams(url, params) {
      if (!params) return url
      const normalized = params.replace(/^[?&]/, '')
      return `${url}${url.indexOf('?') >= 0 ? '&' : '?'}${normalized}`
    },
    normalizeForm(form) {
      const data = { ...form }
      if (data.scope === 'GLOBAL') data.projectId = 0
      if (!data.jdbcUrl) data.jdbcUrl = this.buildJdbcUrl(data)
      delete data.jdbcAutoBuild
      return data
    },
    normalizeLoadedForm(form) {
      const data = { ...form }
      if (
        data.scope === 'PROJECT' &&
        (!data.projectId || data.projectId <= 0)
      ) {
        data.projectId = null
      }
      return data
    },
    handleSubmit() {
      this.$refs.form.validate(async (valid) => {
        if (!valid) return
        this.saving = true
        try {
          const data = this.normalizeForm(this.form)
          if (data.id) {
            await updateDbDatasource(data)
            this.$message.success('更新成功')
          } else {
            await createDbDatasource(data)
            this.$message.success('创建成功')
          }
          this.$router.push('/database')
        } finally {
          this.saving = false
        }
      })
    },
    handleTestDraft() {
      this.$refs.form.validate(async (valid) => {
        if (!valid) return
        await testDbDatasourceDraft(this.normalizeForm(this.form))
        this.$message.success('连接成功')
      })
    },
  },
}
</script>

<style lang="scss" scoped>
.database-detail-page {
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
  .detail-meta {
    color: #64748b;
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
  .jdbc-checkbox {
    margin-top: 6px;
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
    margin-bottom: 10px;
  }
  .code-input :deep(textarea) {
    font-family: Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    line-height: 1.5;
  }
}
</style>
