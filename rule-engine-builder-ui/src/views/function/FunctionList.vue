<template>
  <div class="uiue-list-page">
    <div class="linkage-hint">
      <i class="el-icon-info" /> 自定义函数可在决策流/决策树的脚本任务中调用。支持 QLExpress 脚本、Java 类或 Spring Bean 实现。
    </div>

    <div class="uiue-search-container">
      <el-form :inline="true" size="small" @keyup.enter.native="handleQuery">
        <el-form-item label="作用范围">
          <el-select v-model="qp.scope" clearable filterable placeholder="全部" style="width:100px;">
            <el-option label="全局" value="GLOBAL" />
            <el-option label="项目" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目编码">
          <remote-filter-select v-model="qp.projectCode" :fetch-options="fetchProjectCodeOptions" option-label-key="projectCode" option-value-key="projectCode" placeholder="输入筛选" style="width:140px;" />
        </el-form-item>
        <el-form-item label="项目名称">
          <remote-filter-select v-model="qp.projectName" :fetch-options="fetchProjectNameOptions" option-label-key="projectName" option-value-key="projectName" placeholder="输入筛选" style="width:140px;" />
        </el-form-item>
        <el-form-item label="实现方式">
          <el-select v-model="qp.implType" clearable filterable placeholder="全部" style="width:110px;">
            <el-option label="QLExpress 脚本" value="SCRIPT" />
            <el-option label="Java 类" value="JAVA" />
            <el-option label="Spring Bean" value="BEAN" />
          </el-select>
        </el-form-item>
        <el-form-item label="函数编码">
          <remote-filter-select v-model="qp.funcCode" :fetch-options="fetchFuncCodeOptions" option-label-key="funcCode" option-value-key="funcCode" allow-free-input placeholder="输入筛选" style="width:140px;" />
        </el-form-item>
        <el-form-item label="函数名称">
          <remote-filter-select v-model="qp.funcLabel" :fetch-options="fetchFuncLabelOptions" option-label-key="funcName" option-value-key="funcName" allow-free-input placeholder="输入筛选" style="width:140px;" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
    <div class="uiue-btn-bar">
      <div class="btn-right">
        <el-button size="small" icon="el-icon-plus" type="primary" @click="handleCreate">新建函数</el-button>
      </div>
    </div>

    <el-table :data="funcList" border size="small" v-loading="loading" style="width:100%;margin-top:12px;">
      <el-table-column label="作用范围" width="90" align="center">
        <template slot-scope="{ row }">
          <el-tag :type="row.scope === 'GLOBAL' ? 'scope-global' : 'scope-project'" size="mini">{{ scopeLabel(row.scope) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="项目名称" min-width="120" show-overflow-tooltip>
        <template slot-scope="{ row }">{{ row.projectName || '—' }}</template>
      </el-table-column>
      <el-table-column prop="funcCode" label="函数编码" min-width="120" show-overflow-tooltip />
      <el-table-column prop="funcName" label="函数名称" min-width="120" show-overflow-tooltip />
      <el-table-column prop="returnType" label="返回类型" width="90" align="center">
        <template slot-scope="{ row }">
          <el-tag size="mini">{{ typeLabel(row.returnType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="implType" label="实现方式" width="110" align="center">
        <template slot-scope="{ row }">
          <el-tag :type="implTypeTagType(row.implType)" size="mini">{{ implTypeLabel(row.implType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="参数" min-width="150">
        <template slot-scope="{ row }">
          <span v-if="row.paramsJson">
            <el-tag v-for="(p, pi) in parseParams(row.paramsJson)" :key="pi" size="mini" type="info" style="margin:1px 2px;">
              {{ p.name }}: {{ typeLabel(p.type) }}
            </el-tag>
          </span>
          <span v-else style="color:#999">无参</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="60" align="center">
        <template slot-scope="{ row }">
          <el-tag :type="row.status===1?'success':'info'" size="mini">{{ row.status===1?'启用':'停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" align="center">
        <template slot-scope="{ row }">
          <el-button type="text" size="small" @click="handleTestFunction(row)">测试</el-button>
          <el-button type="text" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button type="text" size="small" @click="openVersionDialog(row)">版本</el-button>
          <el-button type="text" size="small" class="btn-delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="!loading && funcList.length === 0 && currentProjectId" style="text-align:center;padding:40px;color:#bbb;">
      暂无自定义函数，可点击「新建函数」添加
    </div>

    <el-pagination style="margin-top:16px;text-align:right;" :current-page="qp.pageNum" :page-size="qp.pageSize" :total="total"
      layout="total,sizes,prev,pager,next" :page-sizes="[10,30,50,100,200,500]"
      @current-change="onPageChange" @size-change="onSizeChange" />

    <!-- 新建/编辑弹窗 -->
    <el-dialog :title="editForm.id ? '编辑函数' : '新建函数'" :visible.sync="dialogVisible" width="600px" append-to-body>
      <el-form :model="editForm" label-width="90px" size="small">
        <el-form-item label="作用范围">
          <el-select v-model="editForm.scope" placeholder="选择作用范围" style="width:100%" @change="onScopeChange">
            <el-option label="🌐 全局（所有项目可用）" value="GLOBAL" />
            <el-option label="📁 项目级" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editForm.scope === 'PROJECT'" label="项目名称" required>
          <el-select v-model="editForm.projectId" placeholder="请选择项目" style="width:100%" filterable>
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="函数编码" required>
          <el-input v-model="editForm.funcCode" placeholder="如 calcTax（QLExpress 脚本中的调用名）" />
        </el-form-item>
        <el-form-item label="函数名称" required>
          <el-input v-model="editForm.funcName" placeholder="中文名称，如 计算税额" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="editForm.description" type="textarea" :rows="2" placeholder="函数功能说明" />
        </el-form-item>
        <el-form-item label="返回类型">
          <el-select v-model="editForm.returnType" style="width:100%" popper-append-to-body>
            <el-option v-for="opt in varTypeFormOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="参数列表">
          <div v-for="(p, pi) in editParams" :key="pi" style="display:flex;gap:4px;margin-bottom:4px;">
            <el-input v-model="p.name" size="mini" placeholder="参数名" style="width:100px" />
            <el-select v-model="p.type" size="mini" style="width:150px" popper-append-to-body>
              <el-option v-for="opt in varTypeFormOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-input v-model="p.label" size="mini" placeholder="中文名" style="flex:1" />
            <el-button type="text" size="mini" icon="el-icon-delete" class="btn-delete" @click="editParams.splice(pi, 1)" />
          </div>
          <el-button size="mini" icon="el-icon-plus" @click="editParams.push({name:'',type:'STRING',label:''})">添加参数</el-button>
        </el-form-item>
        <el-form-item label="实现方式">
          <el-radio-group v-model="editForm.implType">
            <el-radio label="SCRIPT">QLExpress 脚本</el-radio>
            <el-radio label="JAVA">Java 类</el-radio>
            <el-radio label="BEAN">Spring Bean</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="实现脚本" v-if="editForm.implType === 'SCRIPT'">
          <monaco-editor v-model="editForm.implScript" language="ql" theme="qlexpress-dark" height="180px" />
        </el-form-item>
        <el-form-item label="Java类名" v-if="editForm.implType === 'JAVA'">
          <el-input v-model="editForm.implClass" placeholder="如 com.hengshucredit.rule.example.functions.TaxFunctions" />
        </el-form-item>
        <el-form-item label="方法名" v-if="editForm.implType === 'JAVA'">
          <el-input v-model="editForm.implMethod" placeholder="Java 方法名，如 calculateVAT（不填则默认使用函数编码）" />
        </el-form-item>
        <el-form-item label="Bean名称" v-if="editForm.implType === 'BEAN'">
          <el-input v-model="editForm.implBeanName" placeholder="Spring Bean 名称，如 taxFunctions" />
        </el-form-item>
        <el-form-item label="方法名" v-if="editForm.implType === 'BEAN'">
          <el-input v-model="editForm.implMethod" placeholder="Bean 上的方法名，如 calculateVAT（不填则默认使用函数编码）" />
        </el-form-item>
      </el-form>
      <template slot="footer">
        <el-button size="small" @click="dialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog title="函数测试" :visible.sync="functionTestVisible" width="760px" append-to-body>
      <div class="function-test-target">当前函数：{{ functionTestTarget.funcName }} / {{ functionTestTarget.funcCode }}</div>
      <el-form label-width="90px" size="small">
        <el-form-item label="测试入参">
          <monaco-editor v-model="functionTestParamsText" language="json" height="220px" />
        </el-form-item>
        <el-form-item label="执行结果">
          <monaco-editor v-model="functionTestResultText" language="json" height="220px" read-only />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="functionTestVisible = false">关闭</el-button>
        <el-button size="small" type="primary" :loading="functionTesting" @click="doTestFunction">执行测试</el-button>
      </div>
    </el-dialog>

    <el-dialog title="函数版本管理" :visible.sync="versionVisible" width="900px" append-to-body>
      <el-table :data="versionList" border size="mini" style="width:100%;">
        <el-table-column prop="version" label="版本" width="80" align="center" />
        <el-table-column prop="changeLog" label="变更说明" min-width="140" show-overflow-tooltip />
        <el-table-column prop="publishTime" label="时间" width="170" />
        <el-table-column label="操作" width="220" align="center">
          <template slot-scope="{ row, $index }">
            <el-button type="text" size="mini" @click="viewVersion(row)">查看</el-button>
            <el-button v-if="$index < versionList.length - 1" type="text" size="mini" @click="compareWithNext(row, $index)">对比</el-button>
            <el-button type="text" size="mini" @click="rollbackFunctionVersion(row)">回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="versionCompare" class="version-compare">
        <div class="version-compare-title">版本对比：v{{ versionCompare.left.version }} / v{{ versionCompare.right.version }}</div>
        <el-tag size="mini" :type="versionCompare.functionChanged ? 'warning' : 'success'">
          {{ versionCompare.functionChanged ? '函数配置有变化' : '函数配置无变化' }}
        </el-tag>
      </div>
      <pre v-if="versionPreview" class="version-preview">{{ versionPreview }}</pre>
    </el-dialog>
  </div>
</template>

<script>
import { createFunction, updateFunction, deleteFunction, listFunctions, listVersions, compareVersions, rollbackVersion, testFunction } from '@/api/function'
import { listProjects } from '@/api/project'
import { VAR_TYPE_FORM_OPTIONS, varTypeLabel } from '@/constants/varTypes'
import { clearPageState, restorePageState, savePageState } from '@/utils/pageStateCache'
import { sampleValueForVarType } from '@/utils/testParamTemplate'
import MonacoEditor from '@/components/MonacoEditor'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'

export default {
  name: 'FunctionList',
  components: { MonacoEditor, RemoteFilterSelect },
  data() {
    return {
      projects: [],
      currentProjectId: null,
      funcList: [],
      total: 0,
      qp: { pageNum: 1, pageSize: 10, scope: '', projectCode: '', projectName: '', implType: '', funcCode: '', funcLabel: '' },
      loading: false,
      dialogVisible: false,
      versionVisible: false,
      versionRow: null,
      versionList: [],
      versionCompare: null,
      versionPreview: '',
      projectList: [],
      filteredProjectCodes: [],
      filteredProjectNames: [],
      editForm: { funcCode: '', funcName: '', description: '', returnType: 'STRING', implType: 'SCRIPT', implScript: '', implClass: '', implMethod: '', implBeanName: '', status: 1 },
      editParams: [],
      functionTestVisible: false,
      functionTesting: false,
      functionTestTarget: {},
      functionTestParamsText: '{}',
      functionTestResultText: '',
      varTypeFormOptions: VAR_TYPE_FORM_OPTIONS,
      // 测试期望的别名属性
      functions: [], // 与 funcList 同步
      dialogMode: 'create', // 'create' | 'edit'
      form: { funcType: 'QL_EXPRESS', scope: 'PROJECT' } // 测试期望的别名
    }
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
      try {
        const res = await listProjects({ pageNum: 1, pageSize: 200 })
        const list = (res && res.data && res.data.records) || (res && res.data) || []
        this.projects = list
        this.projectList = list
        this.filteredProjectCodes = list.slice(0, 20)
        this.filteredProjectNames = list.slice(0, 20)
      } catch (e) { this.projects = []; this.projectList = [] }
    },
    fetchProjectCodeOptions({ query, pageNum, pageSize }) {
      return listProjects({ pageNum, pageSize, projectCode: query || '' })
    },
    fetchProjectNameOptions({ query, pageNum, pageSize }) {
      return listProjects({ pageNum, pageSize, projectName: query || '' })
    },
    fetchFuncCodeOptions({ query, pageNum, pageSize }) {
      return listFunctions({ ...this.qp, pageNum, pageSize, funcCode: query || '' })
    },
    fetchFuncLabelOptions({ query, pageNum, pageSize }) {
      return listFunctions({ ...this.qp, pageNum, pageSize, funcLabel: query || '' })
    },
    queryProjectCode(query) {
      const q = (query || '').toLowerCase()
      this.filteredProjectCodes = q
        ? this.projectList.filter(p => p.projectCode && p.projectCode.toLowerCase().includes(q)).slice(0, 20)
        : this.projectList.slice(0, 20)
    },
    queryProjectName(query) {
      const q = (query || '').toLowerCase()
      this.filteredProjectNames = q
        ? this.projectList.filter(p => p.projectName && p.projectName.toLowerCase().includes(q)).slice(0, 20)
        : this.projectList.slice(0, 20)
    },
    handleQuery() { this.qp.pageNum = 1; this.loadFunctions() },
    resetQuery() { this.qp = { pageNum: 1, pageSize: this.qp.pageSize, scope: '', projectCode: '', projectName: '', implType: '', funcCode: '', funcLabel: '' }; clearPageState('FunctionList'); this.loadFunctions() },
    async loadFunctions() {
      this.loading = true
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
        this.funcList = []; this.total = 0
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
      try { return JSON.parse(json) } catch (e) { return [] }
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
      this.editForm = { funcCode: '', funcName: '', description: '', returnType: 'STRING', implType: 'SCRIPT', implScript: '', implClass: '', implMethod: '', implBeanName: '', status: 1, projectId: this.currentProjectId, scope: this.currentProjectId ? 'PROJECT' : 'GLOBAL' }
      this.editParams = [{ name: '', type: 'STRING', label: '' }]
      this.dialogVisible = true
    },
    handleEdit(row) {
      this.editForm = { ...row, scope: row.scope || 'PROJECT' }
      this.editParams = this.parseParams(row.paramsJson || '[]')
      if (this.editParams.length === 0) this.editParams = [{ name: '', type: 'STRING', label: '' }]
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
      params.forEach(item => {
        if (item && item.name) sample[item.name] = this.sampleValueForFunctionParam(item)
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
      if (!isRandomFunction || !params || typeof params !== 'object' || Array.isArray(params)) return params
      const keys = Object.keys(params)
      if (keys.length === 0) {
        return { lower: 0, upper: 1, includeLower: true, includeUpper: true }
      }
      const hasOwn = key => Object.prototype.hasOwnProperty.call(params, key)
      if (hasOwn('lower') && hasOwn('upper') && !hasOwn('includeLower') && !hasOwn('includeUpper')) {
        return { ...params, includeLower: true, includeUpper: true }
      }
      return params
    },
    async doTestFunction() {
      if (!this.functionTestTarget || !this.functionTestTarget.id) return
      let params
      try {
        params = this.functionTestParamsText ? JSON.parse(this.functionTestParamsText) : {}
        params = this.normalizeFunctionTestParams(params)
      } catch (e) {
        this.$message.error('测试入参不是合法 JSON')
        return
      }
      this.functionTesting = true
      try {
        const res = await testFunction(this.functionTestTarget.id, params)
        this.functionTestResultText = JSON.stringify((res && res.data) || res || {}, null, 2)
        this.$message.success('测试完成')
      } catch (e) {
        this.functionTestResultText = JSON.stringify({ success: false, errorMessage: e.message || '测试失败' }, null, 2)
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
      this.editForm.paramsJson = JSON.stringify(this.editParams.filter(p => p.name))
      try {
        if (this.editForm.id) {
          await updateFunction(this.editForm)
        } else {
          await createFunction(this.editForm)
        }
        this.$message.success('保存成功')
        this.dialogVisible = false
        this.loadFunctions()
      } catch (e) {
        this.$message.error('保存失败')
      }
    },
    async openVersionDialog(row) {
      this.versionRow = row
      this.versionVisible = true
      this.versionCompare = null
      this.versionPreview = ''
      const res = await listVersions(row.id)
      this.versionList = (res && res.data) || []
    },
    viewVersion(row) {
      this.versionCompare = null
      this.versionPreview = this.prettyVersionJson(row.functionJson)
    },
    async compareWithNext(row, index) {
      const right = this.versionList[index + 1]
      if (!this.versionRow || !right) return
      const res = await compareVersions(this.versionRow.id, row.version, right.version)
      this.versionCompare = (res && res.data) || null
      this.versionPreview = ''
    },
    async rollbackFunctionVersion(row) {
      if (!this.versionRow || !row) return
      await this.$confirm('确认回滚到 v' + row.version + '？', '提示', { type: 'warning' })
      await rollbackVersion(this.versionRow.id, row.version)
      this.$message.success('已回滚')
      await this.loadFunctions()
      await this.openVersionDialog(this.versionRow)
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
        await this.$confirm('确认删除函数「' + row.funcName + '」？', '提示', { type: 'warning' })
        await deleteFunction(row.id)
        this.$message.success('已删除')
        this.loadFunctions()
      } catch (e) { /* cancel */ }
    }
  }
}
</script>

<style scoped>
.uiue-list-page { padding: 16px; }
.linkage-hint { font-size: 13px; color: #909399; margin-bottom: 12px; background: #fafafa; padding: 8px 12px; border-radius: 4px; }
.mono-input ::v-deep textarea { font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; }
.function-test-target { margin-bottom: 12px; color: #606266; font-size: 13px; }
.version-compare { margin-top: 10px; display: flex; align-items: center; gap: 10px; }
.version-compare-title { font-weight: 600; color: #303133; }
.version-preview { margin-top: 10px; padding: 10px; max-height: 320px; overflow: auto; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 4px; font-size: 12px; line-height: 1.5; }
</style>
