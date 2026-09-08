<template>
  <div class="uiue-list-page list-library-page">
    <div class="module-hint">
      <div class="hint-title">名单管理</div>
      <div class="hint-text">
        管理黑名单、灰名单、白名单及其他名单库，名单类型仅用于标识和筛选，规则执行通过名单查询变量命中有效期内记录。
      </div>
    </div>

    <div class="approval-guide" role="note">
      <div class="approval-guide-title">变更先审批，再生效</div>
      <div class="approval-guide-text">
        新建、修改、启停和删除名单库都会先生成审批草稿。审批通过前，线上名单库保持不变。
      </div>
    </div>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="名单管理" name="list">
        <div class="uiue-search-container uiue-filter-toolbar">
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
          <div class="uiue-btn-bar">
            <div class="btn-right">
              <el-button
                v-permission="'field:edit'"
                type="primary"
                size="small"
                :icon="ElIconPlus"
                @click="handleCreate"
                >新建名单库</el-button
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
                v-permission="'field:edit'"
                v-model="row.status"
                :active-value="1"
                :inactive-value="0"
                @change="toggleStatus(row, $event)"
              />
            </template>
          </el-table-column>
          <el-table-column fixed="right" class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" width="170" align="center">
            <template v-slot="{ row }">
              <el-button
              link
              data-action="detail"
              size="small"
              type="primary"
              @click="$router.push('/list/' + row.id)"
                >详情</el-button
              >
              <el-button
                v-permission="'field:edit'"
                link
                data-action="edit"
                size="small"
                type="warning"
                @click="handleEdit(row)"
                >编辑</el-button
              >
              <el-button
                v-permission="'field:edit'"
                link
                data-action="delete"
                size="small"
                type="danger"
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
      :title="form.id ? '修改名单库审批' : '新建名单库审批'"
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
          <el-button v-permission="'field:edit'" type="primary" @click="submit"
            >生成审批草稿</el-button
          >
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
  createLibraryDraft,
  updateLibraryDraft,
  changeLibraryStatusDraft,
  deleteLibraryDraft,
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
        const response = payload.id
          ? await updateLibraryDraft(payload)
          : await createLibraryDraft(payload)
        this.$message.success('审批草稿已生成，请检查后提交')
        this.dialogVisible = false
        if (response.data && response.data.id) {
          this.$router.push('/approval/' + response.data.id)
        }
      })
    },
    async toggleStatus(row, requestedStatus) {
      const targetStatus = requestedStatus == null
        ? row.status
        : requestedStatus
      const effectiveStatus = targetStatus === 1 ? 0 : 1
      row.status = effectiveStatus
      try {
        const response = await changeLibraryStatusDraft(row, targetStatus)
        this.$message.success(
          `${targetStatus === 1 ? '启用' : '停用'}审批草稿已生成`
        )
        if (response.data && response.data.id) {
          this.$router.push('/approval/' + response.data.id)
        }
      } finally {
        row.status = effectiveStatus
      }
    },
    handleDelete(row) {
      this.$confirm(
        `将为名单库「${row.listName}」生成删除审批。审批通过前，名单库和记录仍可正常使用。`,
        '发起删除审批',
        { type: 'warning' }
      )
        .then(async () => {
          const response = await deleteLibraryDraft(row)
          this.$message.success('删除审批草稿已生成')
          if (response.data && response.data.id) {
            this.$router.push('/approval/' + response.data.id)
          }
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
  background: var(--tianshu-bg-muted);
  border-radius: 4px;
  color: var(--tianshu-text-secondary);
}
.hint-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--tianshu-text-primary);
  margin-bottom: 4px;
}
.hint-text {
  font-size: 12px;
  line-height: 1.6;
}
.approval-guide {
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid var(--tianshu-info-border);
  border-radius: 6px;
  background: var(--tianshu-info-bg);
}
.approval-guide-title {
  color: var(--tianshu-info-text);
  font-size: 13px;
  font-weight: 600;
}
.approval-guide-text {
  margin-top: 3px;
  color: var(--tianshu-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}
</style>
