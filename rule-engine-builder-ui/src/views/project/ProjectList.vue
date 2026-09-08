<template>
  <div class="uiue-list-page">
    <div class="uiue-search-container uiue-filter-toolbar">
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
            value-format="YYYY-MM-DD"
            style="width: 240px"
            @change="onCreateTimeChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="uiue-btn-bar">
        <div class="btn-right">
          <el-button
            v-permission="'project:edit'"
            type="primary"
            size="small"
            :icon="ElIconPlus"
            @click="handleCreate"
            >新建项目</el-button
          >
        </div>
      </div>
    </div>
    <el-table show-overflow-tooltip
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
      <el-table-column class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" width="300" align="center" fixed="right">
        <template v-slot="{ row }">
          <div class="table-operation-group project-action-links">
            <el-button v-permission="'project:edit'" link data-action="edit" size="small" type="primary" @click="handleEdit(row)"
              >编辑</el-button
            >
            <el-button
              link
              data-action="detail"
              size="small"
              type="success"
              @click="$router.push('/project/' + row.id)"
              >进入</el-button
            >
            <el-button link data-action="configure" size="small" type="warning" @click="handleAuth(row)"
              >鉴权</el-button
            >
            <el-button link data-action="docs" size="small" type="info" @click="handleExportDoc(row)"
              >API</el-button
            >
            <el-button
              v-permission="'project:edit'"
              link
              data-action="delete"
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
          <el-button v-permission="'project:edit'" size="small" type="primary" @click="handleSubmit"
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
          const res = await updateProject(this.form)
          this.$message.success('审批草稿已创建')
          this.dialogVisible = false
          if (res.data && res.data.id)
            this.$router.push('/approval/' + res.data.id)
        } else {
          const res = await createProject(this.form)
          if (res.code === 200 && res.data) {
            this.$message.success('审批草稿已创建')
            this.dialogVisible = false
            this.$router.push('/approval/' + res.data.id)
          }
        }
      })
    },
    handleDelete(row) {
      this.$confirm('确定删除项目「' + row.projectName + '」?', '确认', {
        type: 'warning',
      })
        .then(async () => {
          const res = await deleteProject(row.id)
          this.$message.success('删除申请已创建')
          if (res.data && res.data.id)
            this.$router.push('/approval/' + res.data.id)
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
</style>
