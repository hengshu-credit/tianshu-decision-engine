<template>
  <div class="uiue-list-page">
    <div class="linkage-hint">
      <el-icon><el-icon-info /></el-icon>
      自定义函数可在决策流/决策树的脚本任务中调用。支持 QLExpress 脚本、Java
      类或 Spring Bean 实现。
    </div>

    <div v-if="projectLoadError" class="function-page-error" role="alert">
      {{ projectLoadError }}，项目筛选暂不可用。
      <el-button link size="small" @click="loadProjects">重试</el-button>
    </div>

    <div class="uiue-search-container uiue-filter-toolbar">
      <el-form :inline="true" size="small" @keyup.enter="handleQuery">
        <el-form-item label="作用范围">
          <el-select
            v-model="qp.scope"
            clearable
            filterable
            placeholder="全部"
            style="width: 100px"
          >
            <el-option label="全局" value="GLOBAL" />
            <el-option label="项目" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目编码">
          <project-filter-select
            v-model:value="qp.projectCode"
            field="projectCode"
            placeholder="输入筛选"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item label="项目名称">
          <project-filter-select
            v-model:value="qp.projectName"
            field="projectName"
            placeholder="输入筛选"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item label="实现方式">
          <el-select
            v-model="qp.implType"
            clearable
            filterable
            placeholder="全部"
            style="width: 110px"
          >
            <el-option label="QLExpress 脚本" value="SCRIPT" />
            <el-option label="Java 类" value="JAVA" />
            <el-option label="Spring Bean" value="BEAN" />
          </el-select>
        </el-form-item>
        <el-form-item label="函数编码">
          <remote-filter-select
            v-model:value="qp.funcCode"
            :fetch-options="fetchFuncCodeOptions"
            option-label-key="funcCode"
            option-value-key="funcCode"
            allow-free-input
            placeholder="输入筛选"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item label="函数名称">
          <remote-filter-select
            v-model:value="qp.funcLabel"
            :fetch-options="fetchFuncLabelOptions"
            option-label-key="funcName"
            option-value-key="funcName"
            allow-free-input
            placeholder="输入筛选"
            style="width: 140px"
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
            v-permission="'function:edit'"
            size="small"
            :icon="ElIconPlus"
            type="primary"
            @click="handleCreate"
            >新建函数</el-button
          >
        </div>
      </div>
    </div>

    <el-table show-overflow-tooltip
      :data="funcList"
      border
      size="small"
      v-loading="loading"
      style="width: 100%; margin-top: 12px"
    >
      <el-table-column label="作用范围" width="90" align="center">
        <template v-slot="{ row }">
          <el-tag
            :class="row.scope === 'GLOBAL' ? 'el-tag--scope-global' : 'el-tag--scope-project'"
            size="small"
            >{{ scopeLabel(row.scope) }}</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column label="项目名称" min-width="120" show-overflow-tooltip>
        <template v-slot="{ row }">{{ row.projectName || '—' }}</template>
      </el-table-column>
      <el-table-column
        prop="funcCode"
        label="函数编码"
        min-width="120"
        show-overflow-tooltip
      />
      <el-table-column
        prop="funcName"
        label="函数名称"
        min-width="120"
        show-overflow-tooltip
      />
      <el-table-column
        prop="returnType"
        label="返回类型"
        width="90"
        align="center"
      >
        <template v-slot="{ row }">
          <el-tag size="small">{{ typeLabel(row.returnType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="implType"
        label="实现方式"
        width="110"
        align="center"
      >
        <template v-slot="{ row }">
          <el-tag :type="implTypeTagType(row.implType)" size="small">{{
            implTypeLabel(row.implType)
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="参数" min-width="150">
        <template v-slot="{ row }">
          <span v-if="row.paramsJson">
            <el-tag
              v-for="(p, pi) in parseParams(row.paramsJson)"
              :key="pi"
              size="small"
              type="info"
              style="margin: 1px 2px"
            >
              {{ p.name }}: {{ typeLabel(p.type) }}
            </el-tag>
          </span>
          <span v-else style="color: var(--tianshu-text-tertiary)">无参</span>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="165" align="center">
        <template v-slot="{ row }">{{
          formatUpdateTime(row.updateTime)
        }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="60" align="center">
        <template v-slot="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{
            row.status === 1 ? '启用' : '停用'
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column fixed="right" class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" width="230" align="center">
        <template v-slot="{ row }">
          <el-button
            link
            data-action="execute"
            size="small"
            type="success"
            @click="handleTestFunction(row)"
            >测试</el-button
          >
          <el-button
            v-permission="'function:edit'"
            link
            data-action="edit"
            size="small"
            type="warning"
            @click="handleEdit(row)"
            >编辑</el-button
          >
          <el-button
            link
            data-action="version"
            size="small"
            type="info"
            @click="openVersionDialog(row)"
            >版本</el-button
          >
          <el-button
            v-permission="'function:edit'"
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

    <div v-if="functionLoadError" class="function-page-error" role="alert">
      {{ functionLoadError }}。
      <el-button link size="small" @click="loadFunctions">重试</el-button>
    </div>

    <div
      v-else-if="!loading && funcList.length === 0"
      class="function-empty"
    >
      <template v-if="hasActiveFunctionFilters">
        未找到符合当前筛选条件的函数，请调整条件或点击「重置」
      </template>
      <template v-else>暂无自定义函数，可点击「新建函数」添加</template>
    </div>

    <el-pagination
      style="margin-top: 16px; text-align: right"
      :current-page="qp.pageNum"
      :page-size="qp.pageSize"
      :total="total"
      layout="total,sizes,prev,pager,next"
      :page-sizes="[10, 30, 50, 100, 200, 500]"
      @current-change="onPageChange"
      @size-change="onSizeChange"
    />

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      :title="editForm.id ? '编辑函数' : '新建函数'"
      v-model="dialogVisible"
      width="600px"
      append-to-body
    >
      <el-form :model="editForm" label-width="90px" size="small">
        <el-form-item label="作用范围">
          <el-select
            v-model="editForm.scope"
            placeholder="选择作用范围"
            style="width: 100%"
            @change="onScopeChange"
          >
            <el-option label="🌐 全局（所有项目可用）" value="GLOBAL" />
            <el-option label="📁 项目级" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="editForm.scope === 'PROJECT'"
          label="项目名称"
          required
        >
          <el-select
            v-model="editForm.projectId"
            placeholder="请选择项目"
            style="width: 100%"
            filterable
          >
            <el-option
              v-for="p in projects"
              :key="p.id"
              :label="p.projectName"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="函数编码" required>
          <el-input
            v-model="editForm.funcCode"
            placeholder="如 calcTax（QLExpress 脚本中的调用名）"
          />
        </el-form-item>
        <el-form-item label="函数名称" required>
          <el-input
            v-model="editForm.funcName"
            placeholder="中文名称，如 计算税额"
          />
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="2"
            placeholder="函数功能说明"
          />
        </el-form-item>
        <el-form-item label="返回类型">
          <el-select
            v-model="editForm.returnType"
            style="width: 100%"
          >
            <el-option
              v-for="opt in varTypeFormOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="参数列表">
          <div
            v-for="(p, pi) in editParams"
            :key="pi"
            style="display: flex; gap: 4px; margin-bottom: 4px"
          >
            <el-input
              v-model="p.name"
              size="small"
              placeholder="参数名"
              style="width: 100px"
            />
            <el-select
              v-model="p.type"
              size="small"
              style="width: 150px"
            >
              <el-option
                v-for="opt in varTypeFormOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-input
              v-model="p.label"
              size="small"
              placeholder="中文名"
              style="flex: 1"
            />
            <el-button
              link
              size="small"
              :icon="ElIconDelete"
              class="btn-delete"
              @click="editParams.splice(pi, 1)"
            />
          </div>
          <el-button
            size="small"
            :icon="ElIconPlus"
            @click="editParams.push({ name: '', type: 'STRING', label: '' })"
            >添加参数</el-button
          >
        </el-form-item>
        <el-form-item label="实现方式">
          <el-radio-group v-model="editForm.implType">
            <el-radio value="SCRIPT">QLExpress 脚本</el-radio>
            <el-radio value="JAVA">Java 类</el-radio>
            <el-radio value="BEAN">Spring Bean</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="实现脚本" v-if="editForm.implType === 'SCRIPT'">
          <monaco-editor
            v-model:value="editForm.implScript"
            language="ql"
            height="180px"
          />
        </el-form-item>
        <el-form-item label="Java类名" v-if="editForm.implType === 'JAVA'">
          <el-input
            v-model="editForm.implClass"
            placeholder="如 com.hengshucredit.rule.example.functions.TaxFunctions"
          />
        </el-form-item>
        <el-form-item label="方法名" v-if="editForm.implType === 'JAVA'">
          <el-input
            v-model="editForm.implMethod"
            placeholder="Java 方法名，如 calculateVAT（不填则默认使用函数编码）"
          />
        </el-form-item>
        <el-form-item label="Bean名称" v-if="editForm.implType === 'BEAN'">
          <el-input
            v-model="editForm.implBeanName"
            placeholder="Spring Bean 名称，如 taxFunctions"
          />
        </el-form-item>
        <el-form-item label="方法名" v-if="editForm.implType === 'BEAN'">
          <el-input
            v-model="editForm.implMethod"
            placeholder="Bean 上的方法名，如 calculateVAT（不填则默认使用函数编码）"
          />
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <el-button size="small" @click="dialogVisible = false">取消</el-button>
        <el-button v-permission="'function:edit'" size="small" type="primary" @click="handleSave"
          >保存</el-button
        >
      </template>
    </el-dialog>

    <el-dialog
      title="函数测试"
      v-model="functionTestVisible"
      width="760px"
      append-to-body
    >
      <div class="function-test-target">
        当前函数：{{ functionTestTarget.funcName }} /
        {{ functionTestTarget.funcCode }}
      </div>
      <el-form label-width="90px" size="small">
        <el-form-item label="测试入参">
          <monaco-editor
            v-model:value="functionTestParamsText"
            language="json"
            height="220px"
          />
        </el-form-item>
        <el-form-item label="执行结果">
          <monaco-editor
            v-model:value="functionTestResultText"
            language="json"
            height="220px"
            read-only
          />
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <div>
          <el-button size="small" @click="functionTestVisible = false"
            >关闭</el-button
          >
          <el-button
            size="small"
            type="primary"
            :loading="functionTesting"
            @click="doTestFunction"
            >执行测试</el-button
          >
        </div>
      </template>
    </el-dialog>

    <el-dialog
      title="函数版本管理"
      v-model="versionVisible"
      width="900px"
      append-to-body
    >
      <div class="version-compare-toolbar">
        <el-select v-model="versionCompareLeft" size="small" placeholder="选择左侧版本">
          <el-option
            v-for="item in versionList"
            :key="`left-${item.version}`"
            :label="`v${item.version}`"
            :value="item.version"
          />
        </el-select>
        <span>对比</span>
        <el-select v-model="versionCompareRight" size="small" placeholder="选择右侧版本">
          <el-option
            v-for="item in versionList"
            :key="`right-${item.version}`"
            :label="`v${item.version}`"
            :value="item.version"
          />
        </el-select>
        <el-button
          size="small"
          type="primary"
          :disabled="!versionCompareLeft || !versionCompareRight"
          @click="compareSelectedVersions"
          >查看具体差异</el-button
        >
      </div>
      <el-table show-overflow-tooltip :data="versionList" border size="small" style="width: 100%">
        <el-table-column
          prop="version"
          label="版本"
          width="80"
          align="center"
        />
        <el-table-column
          prop="changeLog"
          label="变更说明"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column prop="publishTime" label="时间" width="170" />
        <el-table-column fixed="right" class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" width="220" align="center">
          <template v-slot="{ row, $index }">
            <el-button
              link
              data-action="inspect"
              size="small"
              type="primary"
              @click="viewVersion(row)"
              >查看</el-button
            >
            <el-button
              v-if="$index < versionList.length - 1"
              link
              data-action="compare"
              size="small"
              type="info"
              @click="compareWithNext(row, $index)"
              >对比</el-button
            >
            <el-button
              link
              data-action="rollback"
              size="small"
              type="warning"
              @click="rollbackFunctionVersion(row)"
              >回滚</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <div v-if="versionCompare" class="version-compare">
        <json-version-diff
          :original="versionCompare.left.functionJson"
          :modified="versionCompare.right.functionJson"
          :original-label="`v${versionCompare.left.version}`"
          :modified-label="`v${versionCompare.right.version}`"
          height="380px"
        />
      </div>
      <pre v-if="versionPreview" class="version-preview">{{
        versionPreview
      }}</pre>
    </el-dialog>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import {
  InfoFilled as ElIconInfo,
  Plus as ElIconPlus,
  Delete as ElIconDelete,
} from '@element-plus/icons-vue'
import {
  createFunction,
  updateFunction,
  deleteFunction,
  listFunctions,
  listVersions,
  compareVersions,
  rollbackVersion,
  testFunction,
} from '@/api/function'
import { listProjects } from '@/api/project'
import { isRequestErrorNotified } from '@/api/request'
import { VAR_TYPE_FORM_OPTIONS, varTypeLabel } from '@/constants/varTypes'
import {
  clearPageState,
  restorePageState,
  savePageState,
} from '@/utils/pageStateCache'
import { sampleValueForVarType } from '@/utils/testParamTemplate'
import MonacoEditor from '@/components/MonacoEditor'
import JsonVersionDiff from '@/components/common/JsonVersionDiff.vue'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'

export default {
  data() {
    return {
      projects: [],
      currentProjectId: null,
      projectLoadError: '',
      functionLoadError: '',
      funcList: [],
      total: 0,
      qp: {
        pageNum: 1,
        pageSize: 10,
        scope: '',
        projectCode: '',
        projectName: '',
        implType: '',
        funcCode: '',
        funcLabel: '',
      },
      loading: false,
      dialogVisible: false,
      versionVisible: false,
      versionRow: null,
      versionList: [],
      versionCompareLeft: null,
      versionCompareRight: null,
      versionCompare: null,
      versionPreview: '',
      projectList: [],
      filteredProjectCodes: [],
      filteredProjectNames: [],
      editForm: {
        funcCode: '',
        funcName: '',
        description: '',
        returnType: 'STRING',
        implType: 'SCRIPT',
        implScript: '',
        implClass: '',
        implMethod: '',
        implBeanName: '',
        status: 1,
      },
      editParams: [],
      functionTestVisible: false,
      functionTesting: false,
      functionTestTarget: {},
      functionTestParamsText: '{}',
      functionTestResultText: '',
      varTypeFormOptions: VAR_TYPE_FORM_OPTIONS,
      // 测试期望的别名属性
      // 与 funcList 同步
      functions: [],
      // 'create' | 'edit'
      dialogMode: 'create',
      // 测试期望的别名
      form: { funcType: 'QL_EXPRESS', scope: 'PROJECT' },
      ElIconPlus: markRaw(ElIconPlus),
      ElIconDelete: markRaw(ElIconDelete),
    }
  },
  components: {
    MonacoEditor,
    JsonVersionDiff,
    RemoteFilterSelect,
    ProjectFilterSelect,
    ElIconInfo,
  },
  name: 'FunctionList',
  computed: {
    hasActiveFunctionFilters() {
      return [
        'scope',
        'projectCode',
        'projectName',
        'implType',
        'funcCode',
        'funcLabel',
      ].some((key) => String(this.qp[key] || '').trim() !== '')
    },
  },
  created() {
    this.restoreCachedState()
    this.loadProjects().then(() => this.loadFunctions())
  },
  methods: {
    restoreCachedState() {
      const state = restorePageState('FunctionList')
      if (state.qp) this.qp = { ...this.qp, ...state.qp }
    },
    saveCachedState() {
      savePageState('FunctionList', { qp: this.qp })
    },
    async loadProjects() {
      this.projectLoadError = ''
      try {
        const res = await listProjects({ pageNum: 1, pageSize: 200 })
        const list =
          (res && res.data && res.data.records) || (res && res.data) || []
        this.projects = list
        this.projectList = list
        this.filteredProjectCodes = list.slice(0, 20)
        this.filteredProjectNames = list.slice(0, 20)
      } catch (e) {
        this.projects = []
        this.projectList = []
        this.projectLoadError = this.errorMessage(e, '加载项目列表失败')
      }
    },
    fetchFuncCodeOptions({ query, pageNum, pageSize }) {
      return listFunctions({
        ...this.qp,
        pageNum,
        pageSize,
        funcCode: query || '',
      })
    },
    fetchFuncLabelOptions({ query, pageNum, pageSize }) {
      return listFunctions({
        ...this.qp,
        pageNum,
        pageSize,
        funcLabel: query || '',
      })
    },
    queryProjectCode(query) {
      const q = (query || '').toLowerCase()
      this.filteredProjectCodes = q
        ? this.projectList
            .filter(
              (p) => p.projectCode && p.projectCode.toLowerCase().includes(q)
            )
            .slice(0, 20)
        : this.projectList.slice(0, 20)
    },
    queryProjectName(query) {
      const q = (query || '').toLowerCase()
      this.filteredProjectNames = q
        ? this.projectList
            .filter(
              (p) => p.projectName && p.projectName.toLowerCase().includes(q)
            )
            .slice(0, 20)
        : this.projectList.slice(0, 20)
    },
    handleQuery() {
      this.qp.pageNum = 1
      this.loadFunctions()
    },
    resetQuery() {
      this.qp = {
        pageNum: 1,
        pageSize: this.qp.pageSize,
        scope: '',
        projectCode: '',
        projectName: '',
        implType: '',
        funcCode: '',
        funcLabel: '',
      }
      clearPageState('FunctionList')
      this.loadFunctions()
    },
    async loadFunctions() {
      this.loading = true
      this.functionLoadError = ''
      try {
        this.saveCachedState()
        const params = { ...this.qp }
        if (this.currentProjectId) params.projectId = this.currentProjectId
        if (!params.scope) delete params.scope
        if (!params.projectCode) delete params.projectCode
        if (!params.projectName) delete params.projectName
        if (!params.implType) delete params.implType
        if (!params.funcCode) delete params.funcCode
        if (!params.funcLabel) delete params.funcLabel
        const res = await listFunctions(params)
        this.funcList = (res && res.data && res.data.records) || []
        this.total = (res && res.data && res.data.total) || 0
      } catch (e) {
        this.funcList = []
        this.total = 0
        this.functionLoadError = this.errorMessage(e, '加载函数列表失败')
      } finally {
        this.loading = false
      }
    },
    onPageChange(p) {
      this.qp.pageNum = p
      this.loadFunctions()
    },
    /** 每页条数变更 */
    onSizeChange(s) {
      this.qp.pageSize = s
      this.qp.pageNum = 1
      this.loadFunctions()
    },
    parseParams(json) {
      try {
        return JSON.parse(json)
      } catch (e) {
        return []
      }
    },
    errorMessage(error, fallback) {
      const responseMessage = error?.response?.data?.message
      const message = responseMessage || error?.message
      return typeof message === 'string' && message.trim()
        ? message.trim()
        : fallback
    },
    notifyOperationError(error, fallback) {
      if (!isRequestErrorNotified(error)) {
        this.$message.error(this.errorMessage(error, fallback))
      }
    },
    isDialogCancelled(error) {
      return error === 'cancel' || error === 'close'
    },
    formatUpdateTime(value) {
      if (!value) return '—'
      return String(value).replace('T', ' ').slice(0, 19)
    },
    typeLabel: varTypeLabel,
    scopeLabel(scope) {
      return { GLOBAL: '全局', PROJECT: '项目级' }[scope] || '项目级'
    },
    implTypeLabel(type) {
      return { SCRIPT: '脚本', JAVA: 'Java类', BEAN: 'Bean' }[type] || type
    },
    implTypeTagType(type) {
      return { SCRIPT: '', JAVA: 'warning', BEAN: 'success' }[type] || 'info'
    },
    handleCreate() {
      this.editForm = {
        funcCode: '',
        funcName: '',
        description: '',
        returnType: 'STRING',
        implType: 'SCRIPT',
        implScript: '',
        implClass: '',
        implMethod: '',
        implBeanName: '',
        status: 1,
        projectId: this.currentProjectId,
        scope: this.currentProjectId ? 'PROJECT' : 'GLOBAL',
      }
      this.editParams = [{ name: '', type: 'STRING', label: '' }]
      this.dialogVisible = true
    },
    handleEdit(row) {
      this.editForm = { ...row, scope: row.scope || 'PROJECT' }
      this.editParams = this.parseParams(row.paramsJson || '[]')
      if (this.editParams.length === 0)
        this.editParams = [{ name: '', type: 'STRING', label: '' }]
      this.dialogVisible = true
    },
    handleTestFunction(row) {
      this.functionTestTarget = row || {}
      this.functionTestParamsText = this.buildFunctionTestParamsTemplate(row)
      this.functionTestResultText = ''
      this.functionTestVisible = true
    },
    buildFunctionTestParamsTemplate(row) {
      const params = this.parseParams(row && row.paramsJson)
      const sample = {}
      params.forEach((item) => {
        if (item && item.name)
          sample[item.name] = this.sampleValueForFunctionParam(item)
      })
      return JSON.stringify(sample, null, 2)
    },
    sampleValueForFunctionParam(item) {
      if (item && Object.prototype.hasOwnProperty.call(item, 'example')) {
        try {
          return JSON.parse(JSON.stringify(item.example))
        } catch (e) {
          return item.example
        }
      }
      return sampleValueForVarType(item && item.type)
    },
    normalizeFunctionTestParams(params) {
      const code = this.functionTestTarget && this.functionTestTarget.funcCode
      const isRandomFunction = code === 'randomInt' || code === 'randomDecimal'
      if (
        !isRandomFunction ||
        !params ||
        typeof params !== 'object' ||
        Array.isArray(params)
      )
        return params
      const keys = Object.keys(params)
      if (keys.length === 0) {
        return { lower: 0, upper: 1, includeLower: true, includeUpper: true }
      }
      const hasOwn = (key) => Object.prototype.hasOwnProperty.call(params, key)
      if (
        hasOwn('lower') &&
        hasOwn('upper') &&
        !hasOwn('includeLower') &&
        !hasOwn('includeUpper')
      ) {
        return { ...params, includeLower: true, includeUpper: true }
      }
      return params
    },
    async doTestFunction() {
      if (!this.functionTestTarget || !this.functionTestTarget.id) return
      let params
      try {
        params = this.functionTestParamsText
          ? JSON.parse(this.functionTestParamsText)
          : {}
        params = this.normalizeFunctionTestParams(params)
      } catch (e) {
        this.$message.error('测试入参不是合法 JSON')
        return
      }
      this.functionTesting = true
      try {
        const res = await testFunction(this.functionTestTarget.id, params)
        this.functionTestResultText = JSON.stringify(
          (res && res.data) || res || {},
          null,
          2
        )
        this.$message.success('测试完成')
      } catch (e) {
        this.functionTestResultText = JSON.stringify(
          { success: false, errorMessage: e.message || '测试失败' },
          null,
          2
        )
      } finally {
        this.functionTesting = false
      }
    },
    onScopeChange(val) {
      if (val === 'GLOBAL') {
        this.editForm.projectId = 0
      }
    },
    async handleSave() {
      if (!this.editForm.funcCode || !this.editForm.funcName) {
        this.$message.warning('请填写函数编码和名称')
        return
      }
      if (this.editForm.scope === 'PROJECT' && !this.editForm.projectId) {
        this.$message.warning('请选择所属项目')
        return
      }
      // 全局函数 projectId 设为 0
      if (this.editForm.scope === 'GLOBAL') {
        this.editForm.projectId = 0
      }
      this.editForm.paramsJson = JSON.stringify(
        this.editParams.filter((p) => p.name)
      )
      try {
        const response = this.editForm.id
          ? await updateFunction(this.editForm)
          : await createFunction(this.editForm)
        this.$message.success('函数变更已送审')
        this.dialogVisible = false
        if (response.data && response.data.id)
          this.$router.push('/approval/' + response.data.id)
      } catch (e) {
        this.notifyOperationError(e, '保存函数失败')
      }
    },
    async openVersionDialog(row) {
      this.versionRow = row
      this.versionVisible = true
      this.versionCompare = null
      this.versionPreview = ''
      const res = await listVersions(row.id)
      this.versionList = (res && res.data) || []
      this.versionCompareLeft = this.versionList[1]?.version || null
      this.versionCompareRight = this.versionList[0]?.version || null
    },
    viewVersion(row) {
      this.versionCompare = null
      this.versionPreview = this.prettyVersionJson(row.functionJson)
    },
    async compareWithNext(row, index) {
      const right = this.versionList[index + 1]
      if (!this.versionRow || !right) return
      this.versionCompareLeft = row.version
      this.versionCompareRight = right.version
      await this.compareSelectedVersions()
    },
    async compareSelectedVersions() {
      if (
        !this.versionRow ||
        !this.versionCompareLeft ||
        !this.versionCompareRight
      )
        return
      const res = await compareVersions(
        this.versionRow.id,
        this.versionCompareLeft,
        this.versionCompareRight
      )
      this.versionCompare = (res && res.data) || null
      this.versionPreview = ''
    },
    async rollbackFunctionVersion(row) {
      if (!this.versionRow || !row) return
      await this.$confirm('确认回滚到 v' + row.version + '？', '提示', {
        type: 'warning',
      })
      const response = await rollbackVersion(
        this.versionRow.id,
        row.version
      )
      this.$message.success('已创建历史版本恢复审批')
      this.versionVisible = false
      if (response.data && response.data.id)
        this.$router.push('/approval/' + response.data.id)
    },
    prettyVersionJson(text) {
      if (!text) return ''
      try {
        return JSON.stringify(JSON.parse(text), null, 2)
      } catch (e) {
        return String(text)
      }
    },
    async handleDelete(row) {
      try {
        await this.$confirm('确认删除函数「' + row.funcName + '」？', '提示', {
          type: 'warning',
        })
        const response = await deleteFunction(row.id)
        this.$message.success('函数删除已送审')
        if (response.data && response.data.id)
          this.$router.push('/approval/' + response.data.id)
      } catch (e) {
        if (!this.isDialogCancelled(e)) {
          this.notifyOperationError(e, '删除函数失败')
        }
      }
    },
  },
}
</script>

<style scoped>
.uiue-list-page {
  padding: 16px;
}
.linkage-hint {
  font-size: 13px;
  color: var(--tianshu-text-tertiary);
  margin-bottom: 12px;
  background: var(--tianshu-bg-muted);
  padding: 8px 12px;
  border-radius: 4px;
}
.function-page-error {
  padding: 8px 12px;
  margin-bottom: 12px;
  color: #b42318;
  font-size: 13px;
  background: #fff4f2;
  border: 1px solid #fecdca;
  border-radius: 4px;
}
.function-empty {
  padding: 40px;
  color: var(--tianshu-text-tertiary);
  text-align: center;
}
.mono-input :deep(textarea) {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}
.function-test-target {
  margin-bottom: 12px;
  color: var(--tianshu-text-secondary);
  font-size: 13px;
}
.version-compare {
  margin-top: 10px;
}
.version-compare-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.version-compare-toolbar .el-select {
  width: 150px;
}
.version-compare-title {
  font-weight: 600;
  color: var(--tianshu-text-primary);
}
.version-preview {
  margin-top: 10px;
  padding: 10px;
  max-height: 320px;
  overflow: auto;
  background: var(--tianshu-bg-soft);
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
}
</style>
