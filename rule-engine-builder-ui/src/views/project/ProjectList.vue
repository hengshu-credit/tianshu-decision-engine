<template>
  <div class="uiue-list-page">
    <div class="workflow-guide">
      <div class="workflow-guide-head">
        <div class="workflow-guide-title">从项目到发布</div>
        <div class="workflow-guide-text">
          按顺序完成配置、验证和接入，避免业务人员漏掉编译、发布、日志核对等关键步骤。
        </div>
      </div>
      <div class="workflow-steps">
        <div
          v-for="(item, index) in workflowSteps"
          :key="item.title"
          class="workflow-step"
        >
          <div class="step-index">{{ index + 1 }}</div>
          <div class="step-body">
            <div class="step-title">{{ item.title }}</div>
            <div class="step-text">{{ item.text }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="uiue-search-container">
      <el-form :inline="true" size="small" @keyup.enter="handleQuery">
        <el-form-item label="项目编码">
          <project-filter-select
            v-model:value="qp.projectCode"
            field="projectCode"
            placeholder="输入筛选"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="项目名称">
          <project-filter-select
            v-model:value="qp.projectName"
            field="projectName"
            placeholder="输入筛选"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-select
            v-model="qp.status"
            clearable
            filterable
            placeholder="全部"
            style="width: 100px"
            @change="handleQuery"
          >
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="createTimeRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="yyyy-MM-dd"
            style="width: 240px"
            @change="onCreateTimeChange"
          />
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
          >新建项目</el-button
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
      <el-table-column
        prop="projectCode"
        label="项目编码"
        min-width="140"
        show-overflow-tooltip
      />
      <el-table-column
        prop="projectName"
        label="项目名称"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        prop="description"
        label="描述"
        min-width="200"
        show-overflow-tooltip
      />
      <el-table-column prop="status" label="状态" min-width="70" align="center">
        <template v-slot="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{
            row.status === 1 ? '启用' : '停用'
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="160" />
      <el-table-column label="操作" min-width="220" align="center">
        <template v-slot="{ row }">
          <div class="project-action-links">
            <el-button link size="small" type="primary" @click="handleEdit(row)"
              >编辑</el-button
            >
            <el-button
              link
              size="small"
              type="success"
              @click="$router.push('/project/' + row.id)"
              >进入</el-button
            >
            <el-button link size="small" type="warning" @click="handleAuth(row)"
              >鉴权</el-button
            >
            <el-button link size="small" type="info" @click="handleExportDoc(row)"
              >API</el-button
            >
            <el-button
              link
              size="small"
              type="danger"
              class="btn-delete"
              @click="handleDelete(row)"
              >删除</el-button
            >
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      style="margin-top: 16px; text-align: right"
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
    <el-dialog
      :title="form.id ? '编辑项目' : '新建项目'"
      v-model="dialogVisible"
      width="500px"
    >
      <el-form
        ref="form"
        :model="form"
        :rules="rules"
        label-width="100px"
        size="small"
      >
        <el-form-item label="项目编码" prop="projectCode"
          ><el-input v-model="form.projectCode" :disabled="!!form.id"
        /></el-form-item>
        <el-form-item label="项目名称" prop="projectName"
          ><el-input v-model="form.projectName"
        /></el-form-item>
        <el-form-item label="描述"
          ><el-input v-model="form.description" type="textarea" :rows="3"
        /></el-form-item>
        <el-form-item label="状态"
          ><el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
        /></el-form-item>
      </el-form>
      <template v-slot:footer>
        <div>
          <el-button size="small" @click="dialogVisible = false"
            >取消</el-button
          >
          <el-button size="small" type="primary" @click="handleSubmit"
            >确定</el-button
          >
        </div>
      </template>
    </el-dialog>
    <project-auth-dialog
      v-model:visible="authDialogVisible"
      :project="currentAuthProject"
    />
  </div>
</template>

<script>
import { markRaw } from 'vue'
import { Plus as ElIconPlus } from '@element-plus/icons-vue'
import {
  listProjects,
  createProject,
  updateProject,
  deleteProject,
  exportApiDoc,
} from '@/api/project'
import {
  clearPageState,
  restorePageState,
  savePageState,
} from '@/utils/pageStateCache'
import { generateApiDocHtml } from '@/utils/apiDoc'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import ProjectAuthDialog from './ProjectAuthDialog.vue'
export default {
  data() {
    return {
      workflowSteps: [
        {
          title: '创建项目',
          text: '填写项目编码、名称和状态，保存后在统一鉴权中配置业务调用凭据。',
        },
        {
          title: '定义变量/对象',
          text: '在项目内维护输入变量、输出变量和数据对象，变量编码保持业务原样。',
        },
        {
          title: '设计规则',
          text: '进入规则设计器选择决策表、决策树、规则集、评分卡或 QL 脚本等模型。',
        },
        {
          title: '编译',
          text: '保存规则后执行编译，检查变量引用、输出字段和生成脚本是否正确。',
        },
        {
          title: '测试',
          text: '使用规则测试录入样例请求，核对执行结果、命中路径和追踪树。',
        },
        {
          title: '发布',
          text: '发布已验证版本，服务端会推送规则变更给客户端缓存。',
        },
        {
          title: 'SDK 接入',
          text: '业务系统可用 X-Rule-Token、账号密码、API Key 或 HMAC 接入，SDK 支持临时 Token 自动续期。',
        },
        {
          title: '查看日志/账单',
          text: '上线后在执行日志、分流日志和账单汇总里核对调用量、耗时、成功率和费用。',
        },
      ],
      loading: false,
      tableData: [],
      total: 0,
      qp: {
        pageNum: 1,
        pageSize: 10,
        projectCode: '',
        projectName: '',
        status: '',
        createBeginTime: '',
        createEndTime: '',
      },
      createTimeRange: [],
      allProjectCodes: [],
      allProjectNames: [],
      filteredProjectCodes: [],
      filteredProjectNames: [],
      dialogVisible: false,
      authDialogVisible: false,
      currentAuthProject: {},
      form: {
        id: null,
        projectCode: '',
        projectName: '',
        description: '',
        status: 1,
      },
      rules: {
        projectCode: [
          { required: true, message: '请输入项目编码', trigger: 'blur' },
        ],
        projectName: [
          { required: true, message: '请输入项目名称', trigger: 'blur' },
        ],
      },
      ElIconPlus: markRaw(ElIconPlus),
    }
  },
  name: 'ProjectList',
  components: { ProjectFilterSelect, ProjectAuthDialog },
  created() {
    this.restoreCachedState()
    this.loadData()
  },
  methods: {
    restoreCachedState() {
      const state = restorePageState('ProjectList')
      if (state.qp) this.qp = { ...this.qp, ...state.qp }
      if (state.createTimeRange) this.createTimeRange = state.createTimeRange
    },
    saveCachedState() {
      savePageState('ProjectList', {
        qp: this.qp,
        createTimeRange: this.createTimeRange,
      })
    },
    async loadData() {
      this.loading = true
      try {
        this.saveCachedState()
        const params = { ...this.qp }
        if (!params.projectCode) delete params.projectCode
        if (!params.projectName) delete params.projectName
        if (!params.status && params.status !== 0) delete params.status
        if (!params.createBeginTime) delete params.createBeginTime
        if (!params.createEndTime) delete params.createEndTime
        const res = await listProjects(params)
        this.tableData = res.data.records || []
        this.total = res.data.total || 0
        const codeSet = new Set(),
          nameSet = new Set()
        this.tableData.forEach((r) => {
          if (r.projectCode) codeSet.add(r.projectCode)
          if (r.projectName) nameSet.add(r.projectName)
        })
        this.allProjectCodes = Array.from(codeSet)
        this.allProjectNames = Array.from(nameSet)
        this.filteredProjectCodes = this.allProjectCodes.slice(0, 20)
        this.filteredProjectNames = this.allProjectNames.slice(0, 20)
      } finally {
        this.loading = false
      }
    },
    queryProjectCode(query) {
      const q = (query || '').toLowerCase()
      this.filteredProjectCodes = q
        ? this.allProjectCodes
            .filter((v) => v && v.toLowerCase().includes(q))
            .slice(0, 20)
        : this.allProjectCodes.slice(0, 20)
    },
    queryProjectName(query) {
      const q = (query || '').toLowerCase()
      this.filteredProjectNames = q
        ? this.allProjectNames
            .filter((v) => v && v.toLowerCase().includes(q))
            .slice(0, 20)
        : this.allProjectNames.slice(0, 20)
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
        status: '',
        createBeginTime: '',
        createEndTime: '',
      }
      this.createTimeRange = []
      clearPageState('ProjectList')
      this.handleQuery()
    },
    onCreateTimeChange(val) {
      this.qp.createBeginTime = val ? val[0] : ''
      this.qp.createEndTime = val ? val[1] : ''
      this.saveCachedState()
    },
    handleCreate() {
      this.form = {
        id: null,
        projectCode: '',
        projectName: '',
        description: '',
        status: 1,
      }
      this.dialogVisible = true
    },
    handleEdit(row) {
      this.form = { ...row }
      this.dialogVisible = true
    },
    async handleSubmit() {
      this.$refs.form.validate(async (v) => {
        if (!v) return
        if (this.form.id) {
          await updateProject(this.form)
          this.$message.success('更新成功')
          this.dialogVisible = false
          this.loadData()
        } else {
          const res = await createProject(this.form)
          if (res.code === 200 && res.data) {
            this.$message.success('创建成功')
            this.dialogVisible = false
            const createdProject = res.data.project || { ...this.form }
            await this.loadData()
            this.currentAuthProject = createdProject
            this.authDialogVisible = true
          }
        }
      })
    },
    handleDelete(row) {
      this.$confirm('确定删除项目「' + row.projectName + '」?', '确认', {
        type: 'warning',
      })
        .then(async () => {
          await deleteProject(row.id)
          this.$message.success('删除成功')
          this.loadData()
        })
        .catch(() => {})
    },
    handleAuth(row) {
      this.currentAuthProject = row
      this.authDialogVisible = true
    },
    async loadApiDocLogo() {
      const baseUrl = import.meta.env.BASE_URL || './'
      const response = await fetch(
        `${baseUrl}images/hengshucredit_animated.svg`
      )
      if (!response.ok) throw new Error('加载 hengshucredit Logo 失败')
      return response.text()
    },
    async handleExportDoc(row) {
      let objectUrl = ''
      try {
        const [res, logoSvg] = await Promise.all([
          exportApiDoc(row.id),
          this.loadApiDocLogo(),
        ])
        if (res.code === 200 && res.data) {
          const doc = res.data
          const html = generateApiDocHtml(doc, { logoSvg })
          const blob = new Blob([html], { type: 'text/html;charset=utf-8' })
          objectUrl = URL.createObjectURL(blob)
          const a = document.createElement('a')
          a.href = objectUrl
          a.download = `${doc.project.projectCode}-API文档.html`
          document.body.appendChild(a)
          a.click()
          document.body.removeChild(a)
        }
      } catch (e) {
        this.$message.error('导出文档失败')
      } finally {
        if (objectUrl) URL.revokeObjectURL(objectUrl)
      }
    },
  },
}
</script>

<style lang="scss" scoped>
.workflow-guide {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  padding: 14px;
  margin-bottom: 14px;
}
.workflow-guide-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
}
.workflow-guide-title {
  color: #1f2937;
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
}
.workflow-guide-text {
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}
.workflow-steps {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
.workflow-step {
  display: flex;
  gap: 8px;
  min-height: 82px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 10px;
  background: #f8fafc;
}
.step-index {
  width: 22px;
  height: 22px;
  line-height: 22px;
  border-radius: 50%;
  background: #2639e9;
  color: #fff;
  text-align: center;
  font-size: 12px;
  font-weight: 700;
  flex: 0 0 auto;
}
.step-title {
  color: #0f172a;
  font-weight: 700;
  margin-bottom: 5px;
}
.step-text {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}
.project-action-links {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
  white-space: nowrap;

  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}
@media (max-width: 1200px) {
  .workflow-steps {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
