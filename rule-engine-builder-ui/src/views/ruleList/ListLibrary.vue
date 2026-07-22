<template>
  <div class="uiue-list-page list-library-page">
    <div class="module-hint">
      <div class="hint-title">名单管理</div>
      <div class="hint-text">
        管理黑名单、灰名单、白名单及其他名单库，名单类型仅用于标识和筛选，规则执行通过名单查询变量命中有效期内记录。
      </div>
    </div>

    <el-tabs v-model="activeTab" type="border-card" class="page-tabs">
      <el-tab-pane label="名单管理" name="list">
        <div class="uiue-search-container">
          <el-form :inline="true" size="small" @keyup.enter="handleQuery">
            <el-form-item label="项目编码">
              <project-filter-select
                v-model:value="query.projectCode"
                field="projectCode"
                placeholder="输入项目编码"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="项目名称">
              <project-filter-select
                v-model:value="query.projectName"
                field="projectName"
                placeholder="输入项目名称"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="作用范围">
              <el-select
                v-model="query.scope"
                clearable
                placeholder="全部"
                style="width: 110px"
              >
                <el-option label="全局" value="GLOBAL" />
                <el-option label="项目" value="PROJECT" />
              </el-select>
            </el-form-item>
            <el-form-item label="名单类型">
              <el-select
                v-model="query.listType"
                clearable
                placeholder="全部"
                style="width: 120px"
              >
                <el-option
                  v-for="item in listTypeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select
                v-model="query.status"
                clearable
                placeholder="全部"
                style="width: 100px"
              >
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item label="关键字">
              <remote-filter-select
                v-model:value="query.keyword"
                :fetch-options="fetchKeywordOptions"
                allow-free-input
                placeholder="编码/名称"
                style="width: 180px"
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
              >新建名单库</el-button
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
            prop="listCode"
            label="名单编码"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column
            prop="listName"
            label="名单名称"
            min-width="160"
            show-overflow-tooltip
          />
          <el-table-column label="名单类型" width="100" align="center">
            <template v-slot="{ row }">
              <el-tag size="small" :type="listTypeTag(row.listType)">{{
                listTypeLabel(row.listType)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="recordCount"
            label="有效记录"
            width="90"
            align="right"
          />
          <el-table-column
            prop="description"
            label="说明"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column label="状态" width="80" align="center">
            <template v-slot="{ row }">
              <el-switch
                v-model="row.status"
                :active-value="1"
                :inactive-value="0"
                @change="toggleStatus(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" align="center">
            <template v-slot="{ row }">
              <el-button
                link
                size="small"
                @click="$router.push('/list/' + row.id)"
                >详情</el-button
              >
              <el-button link size="small" @click="handleEdit(row)"
                >编辑</el-button
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
          :current-page="query.pageNum"
          :page-size="query.pageSize"
          :total="total"
          layout="total,sizes,prev,pager,next"
          :page-sizes="[10, 30, 50, 100, 200, 500]"
          @current-change="
            (p) => {
              query.pageNum = p
              loadData()
            }
          "
          @size-change="
            (s) => {
              query.pageSize = s
              query.pageNum = 1
              loadData()
            }
          "
        />
      </el-tab-pane>
      <el-tab-pane label="名单日志" name="logs">
        <module-call-log module-type="LIST" title="名单匹配日志" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      :title="form.id ? '编辑名单库' : '新建名单库'"
      v-model="dialogVisible"
      width="640px"
      append-to-body
    >
      <el-form
        ref="form"
        :model="form"
        :rules="rules"
        label-width="100px"
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
            <el-form-item v-if="form.scope === 'PROJECT'" label="所属项目">
              <el-select
                v-model="form.projectId"
                filterable
                clearable
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
            <el-form-item label="名单编码" prop="listCode">
              <el-input
                v-model="form.listCode"
                :disabled="!!form.id"
                placeholder="如 mobile_black_list"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名单名称" prop="listName">
              <el-input v-model="form.listName" placeholder="如 手机号黑名单" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="名单类型">
          <el-select v-model="form.listType" style="width: 180px">
            <el-option
              v-for="item in listTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
            style="margin-left: 16px"
          />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <div>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submit">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import { Plus as ElIconPlus } from '@element-plus/icons-vue'
import { listProjects } from '@/api/project'
import {
  listLibraries,
  createLibrary,
  updateLibrary,
  deleteLibrary,
} from '@/api/ruleList'
import ModuleCallLog from '@/components/common/ModuleCallLog.vue'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'

export default {
  data() {
    return {
      loading: false,
      activeTab: 'list',
      tableData: [],
      total: 0,
      projects: [],
      query: {
        pageNum: 1,
        pageSize: 10,
        projectCode: '',
        projectName: '',
        scope: '',
        listType: '',
        status: '',
        keyword: '',
      },
      dialogVisible: false,
      form: this.emptyForm(),
      listTypeOptions: [
        { label: '黑名单', value: 'BLACK' },
        { label: '灰名单', value: 'GREY' },
        { label: '白名单', value: 'WHITE' },
        { label: '其他', value: 'OTHER' },
      ],
      rules: {
        listCode: [
          { required: true, message: '请输入名单编码', trigger: 'blur' },
        ],
        listName: [
          { required: true, message: '请输入名单名称', trigger: 'blur' },
        ],
      },
      ElIconPlus: markRaw(ElIconPlus),
    }
  },
  name: 'ListLibrary',
  components: { ModuleCallLog, RemoteFilterSelect, ProjectFilterSelect },
  created() {
    this.loadProjects()
    this.loadData()
  },
  methods: {
    emptyForm() {
      return {
        id: null,
        projectId: '',
        scope: 'PROJECT',
        listCode: '',
        listName: '',
        listType: 'BLACK',
        description: '',
        status: 1,
      }
    },
    async loadProjects() {
      try {
        const res = await listProjects({ pageNum: 1, pageSize: 1000 })
        this.projects = (res.data && res.data.records) || []
      } catch (e) {
        this.projects = []
      }
    },
    async loadData() {
      this.loading = true
      try {
        const params = { ...this.query }
        if (params.status === '') delete params.status
        const res = await listLibraries(params)
        this.tableData = (res.data && res.data.records) || []
        this.total = (res.data && res.data.total) || 0
      } finally {
        this.loading = false
      }
    },
    async fetchKeywordOptions({ query, pageNum, pageSize }) {
      const res = await listLibraries({
        ...this.query,
        pageNum,
        pageSize,
        keyword: query || '',
      })
      const data = (res && res.data) || {}
      const values = []
      ;(data.records || []).forEach((row) => {
        if (row.listCode)
          values.push({ label: row.listCode, value: row.listCode })
        if (row.listName)
          values.push({ label: row.listName, value: row.listName })
      })
      return { records: values, total: data.total || values.length }
    },
    handleQuery() {
      this.query.pageNum = 1
      this.loadData()
    },
    resetQuery() {
      this.query = {
        pageNum: 1,
        pageSize: this.query.pageSize,
        projectCode: '',
        projectName: '',
        scope: '',
        listType: '',
        status: '',
        keyword: '',
      }
      this.loadData()
    },
    handleCreate() {
      this.form = this.emptyForm()
      this.dialogVisible = true
    },
    handleEdit(row) {
      this.form = { ...this.emptyForm(), ...row }
      this.dialogVisible = true
    },
    onScopeChange(scope) {
      if (scope === 'GLOBAL') this.form.projectId = 0
    },
    submit() {
      this.$refs.form.validate(async (valid) => {
        if (!valid) return
        const payload = { ...this.form }
        if (payload.scope === 'GLOBAL') payload.projectId = 0
        if (payload.scope === 'PROJECT' && !payload.projectId) {
          this.$message.warning('请选择所属项目')
          return
        }
        if (payload.id) await updateLibrary(payload)
        else await createLibrary(payload)
        this.$message.success('保存成功')
        this.dialogVisible = false
        this.loadData()
      })
    },
    async toggleStatus(row) {
      await updateLibrary({ ...row })
      this.$message.success(row.status === 1 ? '已启用' : '已停用')
    },
    handleDelete(row) {
      this.$confirm(`确定删除名单库「${row.listName}」？`, '确认删除', {
        type: 'warning',
      })
        .then(async () => {
          await deleteLibrary(row.id)
          this.$message.success('删除成功')
          this.loadData()
        })
        .catch(() => {})
    },
    listTypeLabel(type) {
      return (
        { BLACK: '黑名单', GREY: '灰名单', WHITE: '白名单', OTHER: '其他' }[
          type
        ] || type
      )
    },
    listTypeTag(type) {
      return (
        { BLACK: 'danger', GREY: 'info', WHITE: 'success', OTHER: 'warning' }[
          type
        ] || ''
      )
    },
  },
}
</script>

<style scoped>
.module-hint {
  margin-bottom: 12px;
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  color: #606266;
}
.hint-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}
.hint-text {
  font-size: 12px;
  line-height: 1.6;
}
</style>
