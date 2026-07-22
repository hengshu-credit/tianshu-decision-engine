<template>
  <div class="uiue-list-page database-page">
    <div class="module-hint">
      <div class="hint-title">数据库管理</div>
      <div class="hint-text">
        统一维护外部数据库连接池，供数据查询变量（var_source=DB）通过后端访问外部库。
      </div>
    </div>
    <div class="usage-guide">
      <div
        v-for="item in databaseGuideCards"
        :key="item.title"
        class="guide-item"
      >
        <div class="guide-title">{{ item.title }}</div>
        <div class="guide-text">{{ item.text }}</div>
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="数据源配置" name="datasource">
        <div class="uiue-search-container">
          <el-form :inline="true" size="small" @keyup.enter="handleQuery">
            <el-form-item label="项目编码">
              <project-filter-select
                v-model:value="qp.projectCode"
                field="projectCode"
                placeholder="输入项目编码"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="项目名称">
              <project-filter-select
                v-model:value="qp.projectName"
                field="projectName"
                placeholder="输入项目名称"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="作用范围">
              <el-select
                v-model="qp.scope"
                clearable
                placeholder="全部"
                style="width: 110px"
              >
                <el-option label="全局" value="GLOBAL" />
                <el-option label="项目" value="PROJECT" />
              </el-select>
            </el-form-item>
            <el-form-item label="数据源编码">
              <remote-filter-select
                v-model:value="qp.datasourceCode"
                :fetch-options="fetchDatasourceCodeOptions"
                option-label-key="datasourceCode"
                option-value-key="datasourceCode"
                allow-free-input
                placeholder="前缀筛选"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="数据源名称">
              <remote-filter-select
                v-model:value="qp.datasourceName"
                :fetch-options="fetchDatasourceNameOptions"
                option-label-key="datasourceName"
                option-value-key="datasourceName"
                allow-free-input
                placeholder="名称筛选"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="数据库类型">
              <el-select
                v-model="qp.dbType"
                clearable
                placeholder="全部"
                style="width: 120px"
              >
                <el-option
                  v-for="item in dbTypeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select
                v-model="qp.status"
                clearable
                placeholder="全部"
                style="width: 100px"
              >
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleQuery">查询</el-button>
              <el-button @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="uiue-btn-bar">
          <div class="btn-right">
            <el-button
              type="primary"
              size="small"
              :icon="ElIconPlus"
              @click="handleCreate"
              >新建数据库</el-button
            >
          </div>
        </div>

        <el-table
          :data="tableData"
          border
          size="small"
          v-loading="loading"
          style="width: 100%"
        >
          <el-table-column label="作用范围" width="90" align="center">
            <template v-slot="{ row }">
              <el-tag
                :type="row.scope === 'GLOBAL' ? 'warning' : 'success'"
                size="small"
                >{{ row.scope === 'GLOBAL' ? '全局' : '项目' }}</el-tag
              >
            </template>
          </el-table-column>
          <el-table-column
            prop="projectName"
            label="项目名称"
            min-width="120"
            show-overflow-tooltip
          >
            <template v-slot="{ row }">{{ row.projectName || '—' }}</template>
          </el-table-column>
          <el-table-column
            prop="datasourceCode"
            label="数据源编码"
            min-width="140"
            show-overflow-tooltip
          />
          <el-table-column
            prop="datasourceName"
            label="数据源名称"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column
            prop="dbType"
            label="类型"
            width="100"
            align="center"
          >
            <template v-slot="{ row }"
              ><el-tag size="small">{{ row.dbType }}</el-tag></template
            >
          </el-table-column>
          <el-table-column label="连接方式" width="100" align="center">
            <template v-slot="{ row }">
              <el-tag
                :type="
                  row.connectionMode === 'SSH_TUNNEL' ? 'warning' : 'success'
                "
                size="small"
                >{{ connectionModeLabel(row.connectionMode) }}</el-tag
              >
            </template>
          </el-table-column>
          <el-table-column
            label="数据库地址"
            min-width="180"
            show-overflow-tooltip
          >
            <template v-slot="{ row }">{{ formatDbAddress(row) }}</template>
          </el-table-column>
          <el-table-column
            prop="jdbcUrl"
            label="JDBC URL"
            min-width="260"
            show-overflow-tooltip
          />
          <el-table-column
            label="SSH隧道"
            min-width="150"
            show-overflow-tooltip
          >
            <template v-slot="{ row }">{{ formatSshAddress(row) }}</template>
          </el-table-column>
          <el-table-column label="连接池" width="120" align="center">
            <template v-slot="{ row }"
              >{{ row.minIdle || 1 }} / {{ row.maxPoolSize || 5 }}</template
            >
          </el-table-column>
          <el-table-column prop="status" label="状态" width="70" align="center">
            <template v-slot="{ row }">
              <el-tag
                :type="row.status === 1 ? 'success' : 'info'"
                size="small"
                >{{ row.status === 1 ? '启用' : '停用' }}</el-tag
              >
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" align="center">
            <template v-slot="{ row }">
              <el-button link size="small" @click="handleEdit(row)"
                >编辑</el-button
              >
              <el-button link size="small" @click="handleTest(row)"
                >测试</el-button
              >
              <el-button link size="small" @click="openQuery(row)"
                >查询</el-button
              >
              <el-button
                link
                size="small"
                class="btn-delete"
                @click="handleDelete(row)"
                >删除</el-button
              >
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          :current-page="qp.pageNum"
          :page-size="qp.pageSize"
          :total="total"
          layout="total,sizes,prev,pager,next"
          :page-sizes="[10, 30, 50, 100, 200, 500]"
          @current-change="
            (p) => {
              qp.pageNum = p
              loadData()
            }
          "
          @size-change="
            (s) => {
              qp.pageSize = s
              qp.pageNum = 1
              loadData()
            }
          "
        />
      </el-tab-pane>
      <el-tab-pane label="调用日志" name="logs">
        <module-call-log module-type="DATABASE" title="数据库调用日志" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      :title="form.id ? '编辑数据库数据源' : '新建数据库数据源'"
      v-model="dialogVisible"
      width="900px"
      append-to-body
    >
      <el-form
        ref="form"
        :model="form"
        :rules="rules"
        label-width="120px"
        size="small"
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
            style="margin-top: 6px"
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
      <template v-slot:footer>
        <div>
          <el-button size="small" @click="dialogVisible = false"
            >取消</el-button
          >
          <el-button size="small" @click="handleTestDraft">测试连接</el-button>
          <el-button size="small" type="primary" @click="handleSubmit"
            >保存</el-button
          >
        </div>
      </template>
    </el-dialog>

    <el-dialog
      title="只读查询"
      v-model="queryDialogVisible"
      width="840px"
      append-to-body
    >
      <div class="query-target">
        当前数据源：{{ queryTarget.datasourceName }} /
        {{ queryTarget.datasourceCode }}
      </div>
      <el-form label-width="90px" size="small">
        <el-form-item label="SQL">
          <monaco-editor
            v-model:value="queryForm.sql"
            language="sql"
            theme="rule-sql-light"
            height="170px"
          />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="16">
            <el-form-item label="参数数组">
              <monaco-editor
                v-model:value="queryForm.paramsText"
                language="json"
                height="90px"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最大行数">
              <el-input-number
                v-model="queryForm.maxRows"
                :min="1"
                :max="500"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <el-table
        v-if="queryRows.length"
        :data="queryRows"
        border
        size="small"
        max-height="300"
        style="width: 100%"
      >
        <el-table-column
          v-for="col in queryColumns"
          :key="col"
          :prop="col"
          :label="col"
          min-width="120"
          show-overflow-tooltip
        />
      </el-table>
      <div v-else class="empty-query-result">暂无查询结果</div>
      <template v-slot:footer>
        <div>
          <el-button size="small" @click="queryDialogVisible = false"
            >关闭</el-button
          >
          <el-button
            size="small"
            type="primary"
            :loading="queryLoading"
            @click="runQuery"
            >执行查询</el-button
          >
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import { Plus as ElIconPlus } from '@element-plus/icons-vue'
import {
  createDbDatasource,
  deleteDbDatasource,
  listDbDatasources,
  queryDbDatasource,
  testDbDatasource,
  testDbDatasourceDraft,
  updateDbDatasource,
} from '@/api/database'
import { listProjects } from '@/api/project'
import ModuleCallLog from '@/components/common/ModuleCallLog.vue'
import MonacoEditor from '@/components/MonacoEditor'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'

export default {
  data() {
    return {
      databaseGuideCards: [
        {
          title: '只读连接',
          text: '生产建议配置只读账号，连接池只用于查询类 SQL；测试连接前确认网络、SSH 隧道和 validationQuery。',
        },
        {
          title: '查询模板',
          text: '在线查询建议使用 SELECT 和参数占位，先限制 maxRows，再把稳定 SQL 配到数据库变量。',
        },
        {
          title: '数据库变量',
          text: '变量来源选择 DB 后，在 sourceConfig 中配置 datasourceId、sql、params 和 resultPath，返回字段名保持数据库原样。',
        },
      ],
      projects: [],
      activeTab: 'datasource',
      tableData: [],
      total: 0,
      loading: false,
      qp: {
        pageNum: 1,
        pageSize: 10,
        projectCode: '',
        projectName: '',
        scope: '',
        datasourceCode: '',
        datasourceName: '',
        dbType: '',
        status: '',
      },
      dialogVisible: false,
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
      queryDialogVisible: false,
      queryTarget: {},
      queryLoading: false,
      queryForm: { sql: '', paramsText: '[]', maxRows: 100 },
      queryRows: [],
      queryColumns: [],
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
      ElIconPlus: markRaw(ElIconPlus),
    }
  },
  name: 'DatabaseList',
  components: {
    ModuleCallLog,
    MonacoEditor,
    RemoteFilterSelect,
    ProjectFilterSelect,
  },
  created() {
    this.loadProjects()
    this.loadData()
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
    async loadData() {
      this.loading = true
      try {
        const res = await listDbDatasources(this.cleanParams({ ...this.qp }))
        this.tableData = (res.data && res.data.records) || []
        this.total = (res.data && res.data.total) || 0
      } finally {
        this.loading = false
      }
    },
    fetchDatasourceCodeOptions({ query, pageNum, pageSize }) {
      return listDbDatasources({
        ...this.qp,
        pageNum,
        pageSize,
        datasourceCode: query || '',
      })
    },
    fetchDatasourceNameOptions({ query, pageNum, pageSize }) {
      return listDbDatasources({
        ...this.qp,
        pageNum,
        pageSize,
        datasourceName: query || '',
      })
    },
    handleQuery() {
      this.qp.pageNum = 1
      this.loadData()
    },
    resetQuery() {
      this.qp = {
        pageNum: 1,
        pageSize: this.qp.pageSize,
        projectCode: '',
        projectName: '',
        scope: '',
        datasourceCode: '',
        datasourceName: '',
        dbType: '',
        status: '',
      }
      this.loadData()
    },
    handleCreate() {
      this.$router.push('/database/new')
    },
    handleEdit(row) {
      this.$router.push('/database/' + row.id)
    },
    handleSubmit() {
      this.$refs.form.validate(async (valid) => {
        if (!valid) return
        const data = this.normalizeForm(this.form)
        if (data.id) {
          await updateDbDatasource(data)
          this.$message.success('更新成功')
        } else {
          await createDbDatasource(data)
          this.$message.success('创建成功')
        }
        this.dialogVisible = false
        this.loadData()
      })
    },
    handleDelete(row) {
      this.$confirm(
        '确定删除数据库数据源「' + row.datasourceName + '」?',
        '确认',
        { type: 'warning' }
      )
        .then(async () => {
          await deleteDbDatasource(row.id)
          this.$message.success('删除成功')
          this.loadData()
        })
        .catch(() => {})
    },
    async handleTest(row) {
      await testDbDatasource(row.id)
      this.$message.success('连接成功')
    },
    handleTestDraft() {
      this.$refs.form.validate(async (valid) => {
        if (!valid) return
        await testDbDatasourceDraft(this.normalizeForm(this.form))
        this.$message.success('连接成功')
      })
    },
    openQuery(row) {
      this.queryTarget = row
      this.queryForm = { sql: '', paramsText: '[]', maxRows: 100 }
      this.queryRows = []
      this.queryColumns = []
      this.queryDialogVisible = true
    },
    async runQuery() {
      let params
      try {
        params = this.queryForm.paramsText
          ? JSON.parse(this.queryForm.paramsText)
          : []
        if (!Array.isArray(params)) throw new Error('参数必须是数组')
      } catch (e) {
        this.$message.error('参数数组不是合法 JSON')
        return
      }
      this.queryLoading = true
      try {
        const res = await queryDbDatasource(this.queryTarget.id, {
          sql: this.queryForm.sql,
          params,
          maxRows: this.queryForm.maxRows,
        })
        this.queryRows = res.data || []
        this.queryColumns = this.queryRows.length
          ? Object.keys(this.queryRows[0])
          : []
      } finally {
        this.queryLoading = false
      }
    },
    onScopeChange(scope) {
      if (scope === 'GLOBAL') this.form.projectId = 0
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
      if (this.form.jdbcAutoBuild) {
        this.generateJdbcUrl(false)
      }
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
    formatDbAddress(row) {
      const host = row.host || ''
      const port = row.port ? ':' + row.port : ''
      const databaseName = row.databaseName ? '/' + row.databaseName : ''
      return host ? host + port + databaseName : '—'
    },
    formatSshAddress(row) {
      if (row.connectionMode !== 'SSH_TUNNEL') return '—'
      const host = row.sshHost || ''
      const port = row.sshPort ? ':' + row.sshPort : ''
      const user = row.sshUsername ? row.sshUsername + '@' : ''
      return host ? user + host + port : '未配置'
    },
    connectionModeLabel(mode) {
      return mode === 'SSH_TUNNEL' ? 'SSH隧道' : '直连'
    },
    normalizeForm(form) {
      const data = { ...form }
      if (data.scope === 'GLOBAL') data.projectId = 0
      if (!data.jdbcUrl) {
        data.jdbcUrl = this.buildJdbcUrl(data)
      }
      delete data.jdbcAutoBuild
      return data
    },
    cleanParams(params) {
      Object.keys(params).forEach((key) => {
        if (
          params[key] === '' ||
          params[key] === null ||
          params[key] === undefined
        )
          delete params[key]
      })
      return params
    },
  },
}
</script>

<style lang="scss" scoped>
.database-page {
  .module-hint {
    background: #ecfdf5;
    border: 1px solid #a7f3d0;
    border-radius: 4px;
    padding: 12px 14px;
    margin-bottom: 14px;
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .hint-title {
    color: #047857;
    font-weight: 700;
    white-space: nowrap;
  }

  .hint-text,
  .query-target {
    color: #475569;
    line-height: 1.5;
  }

  .query-target {
    margin-bottom: 12px;
  }

  .usage-guide {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 14px;
  }

  .guide-item {
    border: 1px solid #e2e8f0;
    border-radius: 4px;
    padding: 10px 12px;
    background: #ffffff;
  }

  .guide-title {
    color: #0f172a;
    font-weight: 700;
    margin-bottom: 6px;
  }

  .guide-text {
    color: #64748b;
    font-size: 12px;
    line-height: 1.6;
  }

  .sql-input :deep(textarea) {
    font-family: Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    line-height: 1.5;
  }

  .code-input :deep(textarea) {
    font-family: Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    line-height: 1.5;
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

  .empty-query-result {
    border: 1px dashed #cbd5e1;
    color: #94a3b8;
    text-align: center;
    padding: 28px;
    border-radius: 4px;
  }

  @media (max-width: 1200px) {
    .usage-guide {
      grid-template-columns: repeat(1, minmax(0, 1fr));
    }
  }
}
</style>
