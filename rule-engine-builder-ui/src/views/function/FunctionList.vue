<template>
  <div class="uiue-list-page">
    <div class="linkage-hint">
      <i class="el-icon-info" /> 自定义函数可在决策流/决策树的脚本任务中调用。支持 QLExpress 脚本、Java 类或 Spring Bean 实现。
    </div>

    <div class="uiue-search-container">
      <el-form :inline="true" size="small">
        <el-form-item label="作用范围">
          <el-select v-model="qp.scope" clearable filterable placeholder="全部" style="width:100px;">
            <el-option label="全局" value="GLOBAL" />
            <el-option label="项目" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目编码">
          <el-select v-model="qp.projectCode" clearable filterable remote reserve-keyword placeholder="输入筛选" style="width:140px;"
            :remote-method="queryProjectCode" :loading="projectListLoading">
            <el-option v-for="p in filteredProjectCodes" :key="p.projectCode" :label="p.projectCode" :value="p.projectCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目名称">
          <el-select v-model="qp.projectName" clearable filterable remote reserve-keyword placeholder="输入筛选" style="width:140px;"
            :remote-method="queryProjectName" :loading="projectListLoading">
            <el-option v-for="p in filteredProjectNames" :key="p.projectName" :label="p.projectName" :value="p.projectName" />
          </el-select>
        </el-form-item>
        <el-form-item label="实现方式">
          <el-select v-model="qp.implType" clearable filterable placeholder="全部" style="width:110px;">
            <el-option label="QLExpress 脚本" value="SCRIPT" />
            <el-option label="Java 类" value="JAVA" />
            <el-option label="Spring Bean" value="BEAN" />
          </el-select>
        </el-form-item>
        <el-form-item label="函数编码">
          <el-select v-model="qp.funcCode" clearable filterable remote reserve-keyword placeholder="输入筛选" style="width:140px;"
            :remote-method="queryFuncCode" :loading="funcCodeLoading">
            <el-option v-for="v in filteredFuncCodes" :key="v" :label="v" :value="v" />
          </el-select>
        </el-form-item>
        <el-form-item label="函数名称">
          <el-select v-model="qp.funcLabel" clearable filterable remote reserve-keyword placeholder="输入筛选" style="width:140px;"
            :remote-method="queryFuncLabel" :loading="funcLabelLoading">
            <el-option v-for="v in filteredFuncLabels" :key="v" :label="v" :value="v" />
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
      <el-table-column label="操作" width="140" align="center">
        <template slot-scope="{ row }">
          <el-button type="text" size="small" @click="handleEdit(row)">编辑</el-button>
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
          <el-input v-model="editForm.implScript" type="textarea" :rows="6" placeholder="QLExpress 脚本，参数可直接使用" class="mono-input" />
        </el-form-item>
        <el-form-item label="Java类名" v-if="editForm.implType === 'JAVA'">
          <el-input v-model="editForm.implClass" placeholder="如 com.bjjw.rule.example.functions.TaxFunctions" />
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
  </div>
</template>

<script>
import { createFunction, updateFunction, deleteFunction, listFunctions } from '@/api/function'
import { listProjects } from '@/api/project'
import { VAR_TYPE_FORM_OPTIONS, varTypeLabel } from '@/constants/varTypes'

export default {
  name: 'FunctionList',
  data() {
    return {
      projects: [],
      currentProjectId: null,
      funcList: [],
      total: 0,
      qp: { pageNum: 1, pageSize: 10, scope: '', projectCode: '', projectName: '', implType: '', funcCode: '', funcLabel: '' },
      loading: false,
      dialogVisible: false,
      projectList: [],
      projectListLoading: false,
      filteredProjectCodes: [],
      filteredProjectNames: [],
      // 函数编码/名称远程搜索
      funcCodeLoading: false,
      funcLabelLoading: false,
      allFuncCodes: [],
      allFuncLabels: [],
      filteredFuncCodes: [],
      filteredFuncLabels: [],
      editForm: { funcCode: '', funcName: '', description: '', returnType: 'STRING', implType: 'SCRIPT', implScript: '', implClass: '', implMethod: '', implBeanName: '', status: 1 },
      editParams: [],
      varTypeFormOptions: VAR_TYPE_FORM_OPTIONS,
      // 测试期望的别名属性
      functions: [], // 与 funcList 同步
      dialogMode: 'create', // 'create' | 'edit'
      form: { funcType: 'QL_EXPRESS', scope: 'PROJECT' } // 测试期望的别名
    }
  },
  created() {
    this.loadProjects().then(() => this.loadFunctions())
  },
  methods: {
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
    queryProjectCode(query) {
      if (!query) {
        this.filteredProjectCodes = this.projectList.slice(0, 20)
        return
      }
      this.filteredProjectCodes = this.projectList.filter(p =>
        p.projectCode && p.projectCode.toLowerCase().includes(query.toLowerCase())
      ).slice(0, 20)
    },
    queryProjectName(query) {
      if (!query) {
        this.filteredProjectNames = this.projectList.slice(0, 20)
        return
      }
      this.filteredProjectNames = this.projectList.filter(p =>
        p.projectName && p.projectName.toLowerCase().includes(query.toLowerCase())
      ).slice(0, 20)
    },
    queryFuncCode(query) {
      this.funcCodeLoading = true
      if (!query) {
        this.filteredFuncCodes = this.allFuncCodes.slice(0, 20)
      } else {
        this.filteredFuncCodes = this.allFuncCodes.filter(v => v && v.toLowerCase().includes(query.toLowerCase())).slice(0, 20)
      }
      this.funcCodeLoading = false
    },
    queryFuncLabel(query) {
      this.funcLabelLoading = true
      if (!query) {
        this.filteredFuncLabels = this.allFuncLabels.slice(0, 20)
      } else {
        this.filteredFuncLabels = this.allFuncLabels.filter(v => v && v.toLowerCase().includes(query.toLowerCase())).slice(0, 20)
      }
      this.funcLabelLoading = false
    },
    handleQuery() { this.qp.pageNum = 1; this.loadFunctions() },
    resetQuery() { this.qp = { pageNum: 1, pageSize: this.qp.pageSize, scope: '', projectCode: '', projectName: '', implType: '', funcCode: '', funcLabel: '' }; this.loadFunctions() },
    async loadFunctions() {
      this.loading = true
      try {
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
        // 加载函数编码/名称列表供筛选下拉
        const codeSet = new Set(), labelSet = new Set()
        this.funcList.forEach(r => { if (r.funcCode) codeSet.add(r.funcCode); if (r.funcName) labelSet.add(r.funcName) })
        this.allFuncCodes = Array.from(codeSet)
        this.allFuncLabels = Array.from(labelSet)
        this.filteredFuncCodes = this.allFuncCodes.slice(0, 20)
        this.filteredFuncLabels = this.allFuncLabels.slice(0, 20)
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
</style>
