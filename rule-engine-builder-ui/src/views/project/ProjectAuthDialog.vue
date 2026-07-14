<template>
  <el-dialog
    class="project-auth-dialog"
    title="项目调用鉴权"
    :visible="visible"
    width="1180px"
    top="5vh"
    append-to-body
    @close="$emit('update:visible', false)"
  >
    <div class="project-context">
      <div>
        <div class="project-name">{{ project.projectName || '未命名项目' }}</div>
        <div class="project-code">{{ project.projectCode }}</div>
      </div>
      <div class="project-help">每种鉴权独立统计；临时 Token 到期前可续期，过期后仍保留配置的冗余有效时间。</div>
    </div>

    <el-tabs v-model="activeTab" @tab-click="onTabClick">
      <el-tab-pane label="鉴权配置" name="auth">
        <div class="section-toolbar">
          <div class="section-copy">列表仅显示脱敏值，完整账号、密码和密钥可随时再次查看。</div>
          <el-button type="primary" size="small" icon="el-icon-plus" @click="openCreateAuth">新增鉴权</el-button>
        </div>
        <el-table v-loading="authLoading" :data="authList" border size="small" empty-text="暂无鉴权配置">
          <el-table-column prop="authCode" label="鉴权编码" min-width="135" show-overflow-tooltip />
          <el-table-column prop="authName" label="名称" min-width="120" show-overflow-tooltip />
          <el-table-column label="鉴权方式" min-width="110">
            <template slot-scope="{ row }">{{ authTypeLabel(row.authType) }}</template>
          </el-table-column>
          <el-table-column label="账号 / Access Key" min-width="145">
            <template slot-scope="{ row }"><code>{{ row.identifierMasked || '—' }}</code></template>
          </el-table-column>
          <el-table-column label="密码 / 密钥" min-width="145">
            <template slot-scope="{ row }"><code>{{ row.secretMasked || '—' }}</code></template>
          </el-table-column>
          <el-table-column label="Token 时效" min-width="130">
            <template slot-scope="{ row }">
              <div>{{ formatDuration(row.tokenTtlSeconds) }}</div>
              <div class="table-secondary">冗余 {{ formatDuration(row.tokenGraceSeconds) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="74" align="center">
            <template slot-scope="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="mini">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="225" align="center">
            <template slot-scope="{ row }">
              <el-button type="text" size="small" @click="viewFullAuth(row)">完整值</el-button>
              <el-button v-if="row.authType !== 'LEGACY_TOKEN'" type="text" size="small" @click="openEditAuth(row)">编辑</el-button>
              <el-button type="text" size="small" @click="openTokens(row)">Token</el-button>
              <el-button type="text" size="small" @click="toggleAuth(row)">{{ row.status === 1 ? '停用' : '启用' }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="临时 Token" name="tokens">
        <div v-if="selectedAuth" class="section-toolbar">
          <div>
            <span class="section-title">{{ selectedAuth.authName }}</span>
            <code class="inline-code">{{ selectedAuth.authCode }}</code>
          </div>
          <el-button size="small" @click="loadTokens">刷新</el-button>
        </div>
        <el-alert v-else title="请先在“鉴权配置”中选择一项并点击 Token。" type="info" :closable="false" show-icon />
        <el-table v-if="selectedAuth" v-loading="tokenLoading" :data="tokenList" border size="small" empty-text="该鉴权尚未签发临时 Token">
          <el-table-column prop="tokenCode" label="Token 编码" min-width="210" show-overflow-tooltip />
          <el-table-column label="Token" min-width="155"><template slot-scope="{ row }"><code>{{ row.tokenMasked }}</code></template></el-table-column>
          <el-table-column prop="issuedTime" label="签发时间" min-width="155" />
          <el-table-column prop="expireTime" label="正常截止" min-width="155" />
          <el-table-column prop="graceExpireTime" label="冗余截止" min-width="155" />
          <el-table-column label="状态" width="82" align="center">
            <template slot-scope="{ row }"><el-tag :type="tokenStatus(row).type" size="mini">{{ tokenStatus(row).label }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="125" align="center">
            <template slot-scope="{ row }">
              <el-button type="text" size="small" @click="viewFullToken(row)">完整值</el-button>
              <el-button v-if="row.status === 1" class="btn-delete" type="text" size="small" @click="revokeToken(row)">撤销</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="selectedAuth"
          class="dialog-pagination"
          :current-page="tokenQuery.pageNum"
          :page-size="tokenQuery.pageSize"
          :total="tokenTotal"
          layout="total,prev,pager,next"
          @current-change="onTokenPageChange"
        />
      </el-tab-pane>

      <el-tab-pane label="访问审计" name="logs">
        <el-form :inline="true" size="small" class="log-filter" @keyup.enter.native="queryAccessLogs">
          <el-form-item label="鉴权编码"><el-input v-model="logQuery.authCode" clearable placeholder="如 BASIC_MAIN" /></el-form-item>
          <el-form-item label="Token 编码"><el-input v-model="logQuery.tokenCode" clearable placeholder="如 TOKEN_..." /></el-form-item>
          <el-form-item label="结果">
            <el-select v-model="logQuery.success" clearable placeholder="全部" style="width:100px">
              <el-option label="成功" :value="1" /><el-option label="失败" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item><el-button type="primary" @click="queryAccessLogs">查询</el-button><el-button @click="resetAccessLogs">重置</el-button></el-form-item>
        </el-form>
        <el-table v-loading="logLoading" :data="accessLogs" border size="small" empty-text="暂无访问记录">
          <el-table-column prop="createTime" label="访问时间" min-width="155" />
          <el-table-column prop="authCode" label="鉴权编码" min-width="125" show-overflow-tooltip />
          <el-table-column prop="authType" label="方式" min-width="105"><template slot-scope="{ row }">{{ authTypeLabel(row.authType) }}</template></el-table-column>
          <el-table-column prop="tokenCode" label="Token 编码" min-width="160" show-overflow-tooltip />
          <el-table-column prop="requestMethod" label="方法" width="70" align="center" />
          <el-table-column prop="requestUri" label="请求路径" min-width="220" show-overflow-tooltip />
          <el-table-column label="结果" width="72" align="center"><template slot-scope="{ row }"><el-tag :type="row.success === 1 ? 'success' : 'danger'" size="mini">{{ row.success === 1 ? '成功' : '失败' }}</el-tag></template></el-table-column>
          <el-table-column prop="failureReason" label="失败原因" min-width="150" show-overflow-tooltip />
        </el-table>
        <el-pagination class="dialog-pagination" :current-page="logQuery.pageNum" :page-size="logQuery.pageSize" :total="logTotal" layout="total,prev,pager,next" @current-change="onLogPageChange" />
      </el-tab-pane>
    </el-tabs>

    <span slot="footer"><el-button size="small" type="primary" @click="$emit('update:visible', false)">关闭</el-button></span>

    <el-dialog :title="authForm.id ? '编辑鉴权' : '新增鉴权'" :visible.sync="authFormVisible" width="600px" append-to-body>
      <el-form ref="authForm" :model="authForm" :rules="authRules" label-width="120px" size="small">
        <el-form-item label="鉴权编码" prop="authCode"><el-input v-model="authForm.authCode" :disabled="!!authForm.id" placeholder="稳定编码，创建后不可修改" /></el-form-item>
        <el-form-item label="显示名称" prop="authName"><el-input v-model="authForm.authName" placeholder="如 合作方主账号" /></el-form-item>
        <el-form-item label="鉴权方式" prop="authType">
          <el-select v-model="authForm.authType" :disabled="!!authForm.id" style="width:100%">
            <el-option label="账号密码（Basic）" value="BASIC" />
            <el-option label="API Key" value="API_KEY" />
            <el-option label="HMAC-SHA256" value="HMAC_SHA256" />
          </el-select>
        </el-form-item>
        <template v-if="authForm.authType === 'BASIC'">
          <el-form-item label="账号"><el-input v-model="authForm.identifier" autocomplete="off" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="authForm.secret" show-password autocomplete="new-password" /></el-form-item>
        </template>
        <template v-else-if="authForm.authType === 'API_KEY'">
          <el-form-item label="传递位置"><el-select v-model="authForm.placement" style="width:100%"><el-option label="请求头 Header" value="HEADER" /><el-option label="查询参数 Query" value="QUERY" /></el-select></el-form-item>
          <el-form-item label="参数名"><el-input v-model="authForm.parameterName" placeholder="X-Rule-Api-Key" /></el-form-item>
          <el-form-item label="API Key"><el-input v-model="authForm.secret" show-password placeholder="新建时留空则自动生成" /></el-form-item>
        </template>
        <template v-else-if="authForm.authType === 'HMAC_SHA256'">
          <el-form-item label="Access Key"><el-input v-model="authForm.identifier" placeholder="新建时留空则自动生成" /></el-form-item>
          <el-form-item label="Secret"><el-input v-model="authForm.secret" show-password placeholder="新建时留空则自动生成" /></el-form-item>
        </template>
        <div class="form-section">
          <div class="form-section-title">临时 Token 生命周期</div>
          <el-form-item label="有效时长（秒）"><el-input-number v-model="authForm.tokenTtlSeconds" :min="1" :max="604800" controls-position="right" /></el-form-item>
          <el-form-item label="冗余时长（秒）"><el-input-number v-model="authForm.tokenGraceSeconds" :min="0" :max="86400" controls-position="right" /></el-form-item>
        </div>
        <el-form-item label="状态"><el-switch v-model="authForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <span slot="footer"><el-button size="small" @click="authFormVisible = false">取消</el-button><el-button size="small" type="primary" :loading="savingAuth" @click="submitAuth">保存</el-button></span>
    </el-dialog>

    <el-dialog title="完整鉴权值" :visible.sync="fullDialogVisible" width="560px" append-to-body>
      <el-alert title="完整值来自加密存储，请仅在受控环境查看和复制。" type="warning" :closable="false" show-icon />
      <div v-if="fullCredential.identifier" class="secret-row"><span class="secret-label">账号 / Access Key</span><code>{{ fullCredential.identifier }}</code><el-button type="text" @click="copyValue(fullCredential.identifier)">复制</el-button></div>
      <div class="secret-row"><span class="secret-label">密码 / 密钥</span><code>{{ fullCredential.secret || '—' }}</code><el-button v-if="fullCredential.secret" type="text" @click="copyValue(fullCredential.secret)">复制</el-button></div>
      <span slot="footer"><el-button size="small" type="primary" @click="fullDialogVisible = false">关闭</el-button></span>
    </el-dialog>

    <el-dialog title="完整临时 Token" :visible.sync="fullTokenDialogVisible" width="620px" append-to-body>
      <div class="token-code-block"><code>{{ fullTokenValue }}</code></div>
      <span slot="footer"><el-button size="small" @click="copyValue(fullTokenValue)">复制</el-button><el-button size="small" type="primary" @click="fullTokenDialogVisible = false">关闭</el-button></span>
    </el-dialog>
  </el-dialog>
</template>

<script>
import {
  listProjectAuths, createProjectAuth, updateProjectAuth, updateProjectAuthStatus,
  getProjectAuthFull, listProjectAuthTokens, getProjectAuthTokenFull,
  revokeProjectAuthToken, listProjectAuthAccessLogs
} from '@/api/project'

export default {
  name: 'ProjectAuthDialog',
  props: {
    visible: { type: Boolean, default: false },
    project: { type: Object, default: () => ({}) }
  },
  data() {
    return {
      activeTab: 'auth',
      authLoading: false,
      authList: [],
      authFormVisible: false,
      savingAuth: false,
      authForm: this.emptyAuthForm(),
      authRules: {
        authCode: [{ required: true, message: '请输入鉴权编码', trigger: 'blur' }],
        authName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
        authType: [{ required: true, message: '请选择鉴权方式', trigger: 'change' }]
      },
      fullDialogVisible: false,
      fullCredential: {},
      selectedAuth: null,
      tokenLoading: false,
      tokenList: [],
      tokenTotal: 0,
      tokenQuery: { pageNum: 1, pageSize: 10 },
      fullTokenDialogVisible: false,
      fullTokenValue: '',
      logLoading: false,
      accessLogs: [],
      logTotal: 0,
      logQuery: { pageNum: 1, pageSize: 10, authCode: '', tokenCode: '', success: '' }
    }
  },
  watch: {
    visible: {
      immediate: true,
      handler(value) {
        if (value && this.project.id) {
          this.activeTab = 'auth'
          this.loadAuths()
        }
      }
    }
  },
  methods: {
    emptyAuthForm() {
      return {
        id: null, authCode: '', authName: '', authType: 'BASIC', identifier: '', secret: '',
        placement: 'HEADER', parameterName: 'X-Rule-Api-Key', tokenTtlSeconds: 7200,
        tokenGraceSeconds: 600, status: 1
      }
    },
    async loadAuths() {
      this.authLoading = true
      try {
        const res = await listProjectAuths(this.project.id)
        this.authList = res.data || []
      } finally {
        this.authLoading = false
      }
    },
    onTabClick(tab) {
      if (tab.name === 'logs') this.loadAccessLogs()
      if (tab.name === 'tokens' && this.selectedAuth) this.loadTokens()
    },
    openCreateAuth() {
      this.authForm = this.emptyAuthForm()
      this.authFormVisible = true
    },
    async openEditAuth(row) {
      const res = await getProjectAuthFull(this.project.id, row.id)
      this.authForm = { ...this.emptyAuthForm(), ...res.data }
      this.authFormVisible = true
    },
    submitAuth() {
      this.$refs.authForm.validate(async valid => {
        if (!valid) return
        this.savingAuth = true
        try {
          if (this.authForm.id) {
            await updateProjectAuth(this.project.id, this.authForm.id, this.authForm)
          } else {
            await createProjectAuth(this.project.id, this.authForm)
          }
          this.$message.success('鉴权配置已保存')
          this.authFormVisible = false
          await this.loadAuths()
        } finally {
          this.savingAuth = false
        }
      })
    },
    async toggleAuth(row) {
      const status = row.status === 1 ? 0 : 1
      await updateProjectAuthStatus(this.project.id, row.id, status)
      this.$message.success(status === 1 ? '鉴权已启用' : '鉴权已停用')
      await this.loadAuths()
    },
    async viewFullAuth(row) {
      const res = await getProjectAuthFull(this.project.id, row.id)
      this.fullCredential = res.data || {}
      this.fullDialogVisible = true
    },
    async openTokens(row) {
      this.selectedAuth = row
      this.tokenQuery.pageNum = 1
      this.activeTab = 'tokens'
      await this.loadTokens()
    },
    async loadTokens() {
      if (!this.selectedAuth) return
      this.tokenLoading = true
      try {
        const res = await listProjectAuthTokens(this.project.id, this.selectedAuth.id, this.tokenQuery)
        const data = res.data || {}
        this.tokenList = data.records || []
        this.tokenTotal = data.total || 0
      } finally {
        this.tokenLoading = false
      }
    },
    onTokenPageChange(page) {
      this.tokenQuery.pageNum = page
      this.loadTokens()
    },
    async viewFullToken(row) {
      const res = await getProjectAuthTokenFull(this.project.id, this.selectedAuth.id, row.id)
      this.fullTokenValue = (res.data && res.data.accessToken) || ''
      this.fullTokenDialogVisible = true
    },
    async revokeToken(row) {
      try {
        await this.$confirm('撤销后该 Token 立即失效，确认继续吗？', '撤销临时 Token', { type: 'warning' })
        await revokeProjectAuthToken(this.project.id, this.selectedAuth.id, row.id)
        this.$message.success('Token 已撤销')
        await this.loadTokens()
      } catch (e) {
        // 用户取消撤销时不需要提示。
      }
    },
    queryAccessLogs() {
      this.logQuery.pageNum = 1
      this.loadAccessLogs()
    },
    resetAccessLogs() {
      this.logQuery = { pageNum: 1, pageSize: this.logQuery.pageSize, authCode: '', tokenCode: '', success: '' }
      this.loadAccessLogs()
    },
    async loadAccessLogs() {
      this.logLoading = true
      try {
        const params = { ...this.logQuery }
        Object.keys(params).forEach(key => {
          if (params[key] === '' || params[key] === null || params[key] === undefined) delete params[key]
        })
        const res = await listProjectAuthAccessLogs(this.project.id, params)
        const data = res.data || {}
        this.accessLogs = data.records || []
        this.logTotal = data.total || 0
      } finally {
        this.logLoading = false
      }
    },
    onLogPageChange(page) {
      this.logQuery.pageNum = page
      this.loadAccessLogs()
    },
    authTypeLabel(type) {
      return { LEGACY_TOKEN: '兼容令牌', BASIC: '账号密码', API_KEY: 'API Key', HMAC_SHA256: 'HMAC-SHA256' }[type] || type || '—'
    },
    formatDuration(seconds) {
      const value = Number(seconds || 0)
      if (value === 0) return '0 秒'
      if (value % 3600 === 0) return (value / 3600) + ' 小时'
      if (value % 60 === 0) return (value / 60) + ' 分钟'
      return value + ' 秒'
    },
    tokenStatus(row) {
      if (row.status !== 1) return { label: '已撤销', type: 'info' }
      const now = Date.now()
      if (row.graceExpireTime && now > new Date(row.graceExpireTime).getTime()) return { label: '已失效', type: 'info' }
      if (row.expireTime && now > new Date(row.expireTime).getTime()) return { label: '冗余期', type: 'warning' }
      return { label: '有效', type: 'success' }
    },
    copyValue(value) {
      const input = document.createElement('textarea')
      input.value = value
      document.body.appendChild(input)
      input.select()
      document.execCommand('copy')
      document.body.removeChild(input)
      this.$message.success('已复制到剪贴板')
    }
  }
}
</script>

<style lang="scss" scoped>
.project-context {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  padding: 12px 16px;
  margin-bottom: 16px;
  border: 1px solid #dbeafe;
  border-left: 4px solid #2639e9;
  border-radius: 4px;
  background: #eff6ff;
}

.project-name,
.section-title,
.form-section-title {
  color: #0f172a;
  font-weight: 700;
}

.project-code,
.project-help,
.section-copy,
.table-secondary {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.project-code,
code {
  font-family: Menlo, Monaco, Consolas, monospace;
}

.project-help {
  max-width: 560px;
}

.section-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}

.inline-code {
  margin-left: 8px;
  padding: 2px 6px;
  border-radius: 3px;
  color: #1e40af;
  background: #eff6ff;
}

.dialog-pagination {
  margin-top: 16px;
  text-align: right;
}

.log-filter {
  padding: 12px 12px 0;
  margin-bottom: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  background: #f8fafc;
}

.form-section {
  padding: 12px 12px 0;
  margin-bottom: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
}

.form-section-title {
  margin-bottom: 12px;
}

.secret-row {
  display: grid;
  grid-template-columns: 130px minmax(0, 1fr) 48px;
  align-items: start;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #e5e7eb;
}

.secret-label {
  color: #64748b;
}

.secret-row code,
.token-code-block code {
  color: #0f172a;
  word-break: break-all;
  line-height: 1.6;
}

.token-code-block {
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  background: #f8fafc;
}

@media (max-width: 1200px) {
  .project-context {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
