<template>
  <div class="uiue-list-page">
    <div class="uiue-search-container">
      <el-form :inline="true" size="small">
        <el-form-item label="项目编码">
          <el-select v-model="qp.projectCode" clearable filterable remote reserve-keyword placeholder="输入筛选" style="width:160px;"
            :remote-method="queryProjectCode" :loading="projectCodeLoading">
            <el-option v-for="p in filteredProjectCodes" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目名称">
          <el-select v-model="qp.projectName" clearable filterable remote reserve-keyword placeholder="输入筛选" style="width:160px;"
            :remote-method="queryProjectName" :loading="projectNameLoading">
            <el-option v-for="p in filteredProjectNames" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-select v-model="qp.status" clearable filterable placeholder="全部" style="width:100px;" @change="handleQuery">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker v-model="createTimeRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期"
            value-format="yyyy-MM-dd" style="width:240px;" @change="onCreateTimeChange" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
    <div class="uiue-btn-bar">
      <div class="btn-right">
        <el-button type="primary" size="small" icon="el-icon-plus" @click="handleCreate">新建项目</el-button>
      </div>
    </div>
    <el-table :data="tableData" border size="small" v-loading="loading" style="width: 100%;">
      <el-table-column prop="projectCode" label="项目编码" min-width="140" show-overflow-tooltip />
      <el-table-column prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" min-width="70" align="center">
        <template slot-scope="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="mini">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="访问令牌" min-width="160">
        <template slot-scope="{ row }">
          <span v-if="row.maskedToken" style="font-family: monospace;">{{ row.maskedToken }}</span>
          <span v-else style="color: #909399;">未生成</span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="160" />
      <el-table-column label="操作" min-width="180" align="center">
        <template slot-scope="{ row }">
          <el-button type="text" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button type="text" size="small" @click="$router.push('/project/' + row.id)">进入</el-button>
          <el-button type="text" size="small" @click="handleViewToken(row)">令牌</el-button>
          <el-button type="text" size="small" @click="handleExportDoc(row)">API</el-button>
          <el-button type="text" size="small" class="btn-delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:16px;text-align:right;" :current-page="qp.pageNum" :page-size="qp.pageSize" :total="total"
      layout="total,sizes,prev,pager,next" :page-sizes="[10,30,50,100,200,500]"
      @current-change="p => { qp.pageNum = p; loadData() }" @size-change="s => { qp.pageSize = s; qp.pageNum = 1; loadData() }" />
    <el-dialog :title="form.id ? '编辑项目' : '新建项目'" :visible.sync="dialogVisible" width="500px">
      <el-form ref="form" :model="form" :rules="rules" label-width="100px" size="small">
        <el-form-item label="项目编码" prop="projectCode"><el-input v-model="form.projectCode" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="项目名称" prop="projectName"><el-input v-model="form.projectName" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="dialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="handleSubmit">确定</el-button>
      </div>
    </el-dialog>
    <!-- Token查看对话框 -->
    <el-dialog title="项目访问令牌" :visible.sync="tokenDialogVisible" width="520px">
      <div v-if="fullToken" style="padding: 16px; background: #f5f7fa; border-radius: 4px; position: relative;">
        <p style="margin: 0; font-family: monospace; word-break: break-all; font-size: 14px; line-height: 1.6;">{{ fullToken }}</p>
      </div>
      <div v-else style="padding: 16px; color: #909399; text-align: center;">暂无令牌，请重新生成</div>
      <div style="margin-top: 12px; color: #909399; font-size: 12px;">
        <i class="el-icon-warning"></i> 请妥善保管Token，不要泄露给他人。Client SDK 通过请求头 <code>X-Rule-Token</code> 或 Query 参数 <code>token</code> 传递。
      </div>
      <div slot="footer">
        <el-button size="small" @click="handleRegenerateToken">重新生成</el-button>
        <el-button size="small" @click="copyToken">复制</el-button>
        <el-button size="small" type="primary" @click="tokenDialogVisible = false">关闭</el-button>
      </div>
    </el-dialog>
    <!-- 重新生成确认对话框 -->
    <el-dialog title="重新生成令牌" :visible.sync="regenerateDialogVisible" width="400px">
      <div style="padding: 8px 0;">
        <p style="color: #f56c6c; font-size: 14px;">⚠️ 重新生成将导致原有令牌立即失效！</p>
        <p style="color: #606266; font-size: 13px; margin-top: 8px;">正在为项目「{{ currentTokenProject }}」生成新令牌，确认继续吗？</p>
      </div>
      <div slot="footer">
        <el-button size="small" @click="regenerateDialogVisible = false">取消</el-button>
        <el-button size="small" type="danger" :loading="regenerating" @click="confirmRegenerate">确认重新生成</el-button>
      </div>
    </el-dialog>
  </div>
</template>
<script>
import { listProjects, createProject, updateProject, deleteProject, getMaskedToken, getFullToken, regenerateToken, exportApiDoc } from '@/api/project'
import { clearPageState, restorePageState, savePageState } from '@/utils/pageStateCache'
export default {
  name: 'ProjectList',
  data() {
    return {
      loading: false, tableData: [], total: 0,
      qp: { pageNum: 1, pageSize: 10, projectCode: '', projectName: '', status: '', createBeginTime: '', createEndTime: '' },
      createTimeRange: [],
      // 项目编码/名称远程搜索
      projectCodeLoading: false, filteredProjectCodes: [], allProjectCodes: [],
      projectNameLoading: false, filteredProjectNames: [], allProjectNames: [],
      dialogVisible: false,
      tokenDialogVisible: false,
      fullToken: '',
      regenerateDialogVisible: false,
      regenerating: false,
      currentTokenProject: '',
      currentTokenId: null,
      form: { id: null, projectCode: '', projectName: '', description: '', status: 1 },
      rules: {
        projectCode: [{ required: true, message: '请输入项目编码', trigger: 'blur' }],
        projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }]
      }
    }
  },
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
        createTimeRange: this.createTimeRange
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
        // 加载每个项目的脱敏Token
        for (let row of this.tableData) {
          try {
            const tokenRes = await getMaskedToken(row.id)
            if (tokenRes.code === 200 && tokenRes.data) {
              this.$set(row, 'maskedToken', tokenRes.data)
            }
          } catch (e) {
            // ignore
          }
        }
        // 加载项目编码/名称列表供筛选下拉
        const codeSet = new Set(), nameSet = new Set()
        this.tableData.forEach(r => { if (r.projectCode) codeSet.add(r.projectCode); if (r.projectName) nameSet.add(r.projectName) })
        this.allProjectCodes = Array.from(codeSet)
        this.allProjectNames = Array.from(nameSet)
        this.filteredProjectCodes = this.allProjectCodes.slice(0, 20)
        this.filteredProjectNames = this.allProjectNames.slice(0, 20)
      } finally { this.loading = false }
    },
    handleQuery() { this.qp.pageNum = 1; this.loadData() },
    resetQuery() {
      this.qp = { pageNum: 1, pageSize: this.qp.pageSize, projectCode: '', projectName: '', status: '', createBeginTime: '', createEndTime: '' }
      this.createTimeRange = []
      clearPageState('ProjectList')
      this.handleQuery()
    },
    onCreateTimeChange(val) {
      this.qp.createBeginTime = val ? val[0] : ''
      this.qp.createEndTime = val ? val[1] : ''
      this.saveCachedState()
    },
    queryProjectCode(query) {
      this.projectCodeLoading = true
      if (!query) {
        this.filteredProjectCodes = this.allProjectCodes.slice(0, 20)
      } else {
        this.filteredProjectCodes = this.allProjectCodes.filter(v => v && v.toLowerCase().includes(query.toLowerCase())).slice(0, 20)
      }
      this.projectCodeLoading = false
    },
    queryProjectName(query) {
      this.projectNameLoading = true
      if (!query) {
        this.filteredProjectNames = this.allProjectNames.slice(0, 20)
      } else {
        this.filteredProjectNames = this.allProjectNames.filter(v => v && v.toLowerCase().includes(query.toLowerCase())).slice(0, 20)
      }
      this.projectNameLoading = false
    },
    handleCreate() { this.form = { id: null, projectCode: '', projectName: '', description: '', status: 1 }; this.dialogVisible = true },
    handleEdit(row) { this.form = { ...row }; this.dialogVisible = true },
    async handleSubmit() {
      this.$refs.form.validate(async v => {
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
            // 显示新生成的Token
            this.fullToken = res.data.accessToken
            this.tokenDialogVisible = true
            this.loadData()
          }
        }
      })
    },
    handleDelete(row) {
      this.$confirm('确定删除项目「' + row.projectName + '」?', '确认', { type: 'warning' }).then(async () => {
        await deleteProject(row.id); this.$message.success('删除成功'); this.loadData()
      }).catch(() => {})
    },
    copyToken() {
      const input = document.createElement('textarea')
      input.value = this.fullToken
      document.body.appendChild(input)
      input.select()
      document.execCommand('copy')
      document.body.removeChild(input)
      this.$message.success('已复制到剪贴板')
    },
    async handleViewToken(row) {
      try {
        const res = await getFullToken(row.id)
        if (res.code === 200 && res.data) {
          this.fullToken = res.data
          this.currentTokenId = row.id
          this.currentTokenProject = row.projectName
          this.tokenDialogVisible = true
        }
      } catch (e) {
        this.$message.error('获取令牌失败')
      }
    },
    handleRegenerateToken() {
      this.tokenDialogVisible = false
      this.regenerateDialogVisible = true
    },
    async confirmRegenerate() {
      this.regenerating = true
      try {
        const res = await regenerateToken(this.currentTokenId)
        if (res.code === 200 && res.data) {
          this.fullToken = res.data
          this.$message.success('令牌已重新生成')
          this.regenerateDialogVisible = false
          this.tokenDialogVisible = true
          this.loadData()
        } else {
          this.$message.error('重新生成失败')
        }
      } catch (e) {
        this.$message.error('重新生成失败')
      } finally {
        this.regenerating = false
      }
    },
    async handleExportDoc(row) {
      try {
        const res = await exportApiDoc(row.id)
        if (res.code === 200 && res.data) {
          const doc = res.data
          const html = this.generateDocHtml(doc)
          // 下载HTML文件
          const blob = new Blob([html], { type: 'text/html;charset=utf-8' })
          const url = URL.createObjectURL(blob)
          const a = document.createElement('a')
          a.href = url
          a.download = `${doc.project.projectCode}-API文档.html`
          a.click()
          URL.revokeObjectURL(url)
        }
      } catch (e) {
        this.$message.error('导出文档失败')
      }
    },
    generateDocHtml(doc) {
      const escapeHtml = (str) => {
        if (!str) return ''
        return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
      }
      const leafName = (path) => {
        if (!path) return ''
        const text = String(path)
        return text.indexOf('.') === -1 ? text : text.substring(text.lastIndexOf('.') + 1)
      }
      const stripObjectPrefix = (path, obj) => {
        if (!path) return ''
        const objectCode = (obj && (obj.scriptName || obj.objectCode)) || ''
        const text = String(path)
        return objectCode && text.indexOf(objectCode + '.') === 0 ? text.substring(objectCode.length + 1) : text
      }
      const fieldPath = (field, obj) => stripObjectPrefix(field.scriptName || field.varCode || '', obj)
      const fieldDisplayName = (field, obj) => leafName(fieldPath(field, obj)) || field.varCode || field.scriptName || ''
      const buildFieldTree = (fields, obj) => {
        const rows = (fields || []).map(f => ({ ...f, _children: [], _displayName: fieldDisplayName(f, obj), _path: fieldPath(f, obj) }))
        const byCode = {}
        rows.forEach(f => {
          if (f.varCode) byCode[f.varCode] = f
          if (f.scriptName) byCode[f.scriptName] = f
          if (f._path) byCode[f._path] = f
          if (f._displayName) byCode[f._displayName] = f
        })
        const roots = []
        rows.forEach(f => {
          const parent = f.parentVarCode ? byCode[f.parentVarCode] : null
          if (parent && parent !== f) parent._children.push(f)
          else roots.push(f)
        })
        return roots
      }
      const buildExampleFromNodes = (nodes) => {
        const obj = {}
        ;(nodes || []).forEach(node => {
          obj[node._displayName || node.varCode || node.scriptName] = exampleValueForField(node)
        })
        return obj
      }
      const exampleValueForField = (field) => {
        if (field._children && field._children.length) return buildExampleFromNodes(field._children)
        const type = field.varType || ''
        if (type === 'STRING' || type === 'ENUM' || type === 'DATE') return ''
        if (type === 'BOOLEAN') return false
        if (type === 'OBJECT' || type === 'MAP') return {}
        if (type === 'LIST') return []
        return null
      }
      const buildObjectExample = (obj) => buildExampleFromNodes(buildFieldTree(obj.fields || [], obj))
      const renderFieldNodes = (nodes) => {
        if (!nodes || !nodes.length) return '<div class="field-empty">暂无字段</div>'
        return '<ul class="field-tree">' + nodes.map(f => {
          const name = escapeHtml(f._displayName || f.varCode || f.scriptName)
          const type = escapeHtml(f.varTypeLabel || f.varType || '-')
          const label = escapeHtml(f.varLabel || '-')
          const ref = f.refObjectCode ? '<span class="field-ref">引用 ' + escapeHtml(f.refObjectCode) + '</span>' : ''
          const row = '<span class="field-name"><code>' + name + '</code></span><span class="field-type">' + type + '</span><span class="field-label">' + label + '</span>' + ref
          if (f._children && f._children.length) {
            return '<li><details open><summary>' + row + '</summary>' + renderFieldNodes(f._children) + '</details></li>'
          }
          return '<li><div class="field-leaf">' + row + '</div></li>'
        }).join('') + '</ul>'
      }
      const renderObjectFields = (obj) => {
        const objectName = obj.scriptName || obj.objectCode
        return `<div class="sub-title">${escapeHtml(obj.objectLabel || obj.objectCode)} <span class="code">${escapeHtml(objectName)}</span></div>
          <div class="object-field-tree">${renderFieldNodes(buildFieldTree(obj.fields || [], obj))}</div>`
      }
      const renderProjectDataObjects = () => {
        const objects = doc.dataObjects || []
        if (!objects.length) return ''
        return `<div class="section">
            <h3>项目数据对象</h3>
            <div class="desc-box"><p>以下为当前项目可引用的数据对象字段，嵌套字段按对象层级逐级展开，字段名仅展示当前层级名称。</p></div>
            ${objects.map(obj => renderObjectFields(obj)).join('')}
          </div>`
      }

      // 生成每个规则的请求示例JSON（使用该规则的实际输入变量）
      const generateRequestExample = (rule) => {
        const params = {}
        // 收集输入变量（使用该规则解析出的 inputVariables）
        ;(rule.inputVariables || []).forEach(v => {
          let val = v.exampleValue || v.defaultValue || null
          if (v.varType === 'NUMBER') val = val !== null && val !== '' ? Number(val) : null
          else if (v.varType === 'BOOLEAN') val = val === 'true'
          else if (v.varType === 'STRING' && !val) val = ''
          params[v.varCode] = val
        })
        // 收集输入数据对象字段
        ;(rule.inputDataObjects || []).forEach(obj => {
          params[obj.objectCode] = buildObjectExample(obj)
        })
        return JSON.stringify({
          definitionId: rule.id,
          params: params
        }, null, 2)
      }

      // 生成响应示例JSON（使用该规则的实际输出数据对象）
      const generateResponseExample = (rule) => {
        const resultObj = {}
        ;(rule.outputDataObjects || []).forEach(obj => {
          resultObj[obj.objectCode] = buildObjectExample(obj)
        })
        return JSON.stringify({
          success: true,
          result: resultObj,
          executeTimeMs: 15,
          traces: [
            { nodeName: '开始', expression: null, result: null }
          ]
        }, null, 2)
      }

      // 生成侧边栏HTML
      const sidebarItems = doc.rules.map((rule, idx) =>
        `<div class="sidebar-item ${idx === 0 ? 'active' : ''}" data-id="${idx}">
          <div class="sidebar-item-title">${escapeHtml(rule.ruleName)}</div>
          <div class="sidebar-item-desc">${escapeHtml(rule.ruleCode)}</div>
        </div>`
      ).join('')

      // 生成右侧内容区HTML
      const dataObjectCatalog = renderProjectDataObjects()
      const contentPanels = doc.rules.map((rule, idx) => {
        const requestExample = generateRequestExample(rule)
        const responseExample = generateResponseExample(rule)

        // 收集输入参数（使用该规则解析出的 inputVariables）
        const inputVars = rule.inputVariables || []
        const inputParams = inputVars.map(v =>
          `<tr>
            <td><code>params.${escapeHtml(v.varCode)}</code></td>
            <td>${escapeHtml(v.varTypeLabel || v.varType)}</td>
            <td>${v.varSource === 'INPUT' ? '<span class="tag tag-required">必填</span>' : '<span class="tag">选填</span>'}</td>
            <td>${escapeHtml(v.varLabel)}</td>
            <td>${escapeHtml(v.defaultValue || v.exampleValue || '-')}</td>
            <td>${escapeHtml(v.description || '-')}</td>
          </tr>`
        ).join('')

        // 收集输入数据对象
        const inputObjRows = (rule.inputDataObjects || []).map(obj => renderObjectFields(obj)).join('')

        // 收集输出数据对象
        const outputObjRows = (rule.outputDataObjects || []).map(obj => renderObjectFields(obj)).join('')

        return `<div class="content-panel ${idx === 0 ? 'active' : ''}" id="panel-${idx}">
          <div class="content-header">
            <div class="header-top">
              <div class="header-left">
                <h2>${escapeHtml(rule.ruleName)}</h2>
                <div class="content-meta">
                  <span class="method-badge">POST</span>
                  <code class="url">/api/rule/definition/execute</code>
                  <span class="tag tag-info">${escapeHtml(rule.modelTypeLabel || rule.modelType)}</span>
                  <span class="tag ${rule.status === 1 ? 'tag-success' : 'tag-warning'}">${escapeHtml(rule.statusLabel)}</span>
                </div>
              </div>
            </div>
            ${rule.description ? `<p class="description">${escapeHtml(rule.description)}</p>` : ''}
          </div>

          <div class="section">
            <h3>基本信息</h3>
            <table class="info-table">
              <tbody>
                <tr><td class="info-label">规则编码</td><td><code>${escapeHtml(rule.ruleCode)}</code></td><td class="info-label">规则名称</td><td>${escapeHtml(rule.ruleName)}</td></tr>
                <tr><td class="info-label">模型类型</td><td>${escapeHtml(rule.modelTypeLabel || rule.modelType)}</td><td class="info-label">发布状态</td><td>${escapeHtml(rule.statusLabel)}</td></tr>
                <tr><td class="info-label">当前版本</td><td>v${rule.currentVersion || 0}</td><td class="info-label">发布版本</td><td>${rule.publishedVersion ? 'v' + rule.publishedVersion : '未发布'}</td></tr>
              </tbody>
            </table>
          </div>

          <div class="section">
            <h3>认证鉴权</h3>
            <div class="auth-box">
              <p>请求头中添加 <code>X-Rule-Token</code> 字段，或通过 Query 参数 <code>token</code> 传递：</p>
              <pre><code>X-Rule-Token: &lt;项目访问令牌&gt;</code></pre>
              <p class="tips">访问令牌在「规则项目」页面获取，需与规则所属项目一致。</p>
            </div>
          </div>

          <div class="section">
            <h3>接口描述</h3>
            <div class="desc-box">
              <p>该接口用于执行已发布的决策规则，支持决策表、决策树、决策流、交叉表、评分卡等多种模型类型。</p>
              <p>请求成功后返回规则执行结果，包括决策输出和执行追踪信息。</p>
            </div>
          </div>

          ${idx === 0 ? dataObjectCatalog : ''}

          <div class="section">
            <h3>请求参数</h3>
            <table class="param-table">
              <thead><tr><th>参数名</th><th>类型</th><th>必填</th><th>说明</th><th>示例值</th><th>备注</th></tr></thead>
              <tbody>
                <tr><td><code>definitionId</code></td><td>Long</td><td><span class="tag tag-required">必填</span></td><td>规则定义ID</td><td>${rule.id || '1'}</td><td>规则唯一标识</td></tr>
                <tr><td><code>params</code></td><td>Object</td><td><span class="tag">选填</span></td><td>输入参数</td><td>{...}</td><td>包含所有输入变量和数据对象</td></tr>
              </tbody>
            </table>
            ${inputParams ? `<table class="param-table"><thead><tr><th>参数名</th><th>类型</th><th>必填</th><th>说明</th><th>示例值</th><th>备注</th></tr></thead><tbody>${inputParams}</tbody></table>` : ''}
            ${inputObjRows}
          </div>

          <div class="section">
            <h3>请求示例</h3>
            <pre class="code-block"><code class="language-json">${escapeHtml(requestExample)}</code></pre>
          </div>

          <div class="section">
            <h3>响应参数</h3>
            <table class="param-table">
              <thead><tr><th>参数名</th><th>类型</th><th>说明</th></tr></thead>
              <tbody>
                <tr><td><code>success</code></td><td>Boolean</td><td>执行是否成功</td></tr>
                <tr><td><code>result</code></td><td>Object</td><td>决策结果（输出对象）</td></tr>
                <tr><td><code>executeTimeMs</code></td><td>Long</td><td>执行耗时（毫秒）</td></tr>
                <tr><td><code>errorMessage</code></td><td>String</td><td>错误信息（执行失败时返回）</td></tr>
                <tr><td><code>traces</code></td><td>Array</td><td>执行追踪信息（可选）</td></tr>
                <tr><td><code>traces[].nodeName</code></td><td>String</td><td>节点名称</td></tr>
                <tr><td><code>traces[].expression</code></td><td>String</td><td>执行的表达式</td></tr>
                <tr><td><code>traces[].result</code></td><td>Object</td><td>节点执行结果</td></tr>
              </tbody>
            </table>
            ${outputObjRows}
          </div>

          <div class="section">
            <h3>响应示例</h3>
            <pre class="code-block"><code class="language-json">${escapeHtml(responseExample)}</code></pre>
          </div>

          <div class="section">
            <h3>错误码</h3>
            <table class="param-table">
              <thead><tr><th>错误码</th><th>说明</th></tr></thead>
              <tbody>
                <tr><td><code>success: true</code></td><td>执行成功</td></tr>
                <tr><td><code>success: false</code></td><td>执行失败，详见 errorMessage</td></tr>
              </tbody>
            </table>
          </div>

          <div class="section">
            <h3>SDK调用示例</h3>
            <div class="code-tabs">
              <div class="code-tab active" data-lang="java">Java</div>
              <div class="code-tab" data-lang="python">Python</div>
              <div class="code-tab" data-lang="go">Go</div>
              <div class="code-tab" data-lang="js">JavaScript</div>
            </div>
            <pre class="code-block java"><code>RuleEngineClient client = new RuleEngineClient();
client.setServerUrl("http://localhost:8080");
client.setAppName("${escapeHtml(doc.project.projectCode)}");
client.setToken("&lt;your_token&gt;");

// 构建请求参数（参考规则测试中的输入参数）
Map&lt;String, Object&gt; params = new HashMap&lt;&gt;();
// ${inputVars.slice(0, 5).map(v => `params.put("${v.varCode}", ${v.exampleValue || v.defaultValue || 'null'});`).join('\n// ')}

RuleResult result = client.execute("${rule.ruleCode}", params);
System.out.println("Result: " + result.getResult());
System.out.println("ExecuteTime: " + result.getExecuteTimeMs() + "ms");</code></pre>
            <pre class="code-block python" style="display:none"><code>from rule_engine_client import RuleEngineClient

client = RuleEngineClient(
    server_url="http://localhost:8080",
    app_name="${escapeHtml(doc.project.projectCode)}",
    token="&lt;your_token&gt;"
)

params = {
${inputVars.slice(0, 5).map(v => `    "${v.varCode}": ${v.exampleValue || v.defaultValue || 'None'},`).join('\n')}
}
result = client.execute("${rule.ruleCode}", params)
print(f"Result: {result.result}")
print(f"ExecuteTime: {result.execute_time_ms}ms")</code></pre>
            <pre class="code-block go" style="display:none"><code>client := NewRuleEngineClient("http://localhost:8080", "${escapeHtml(doc.project.projectCode)}", "&lt;your_token&gt;")

params := map[string]interface{}{
${inputVars.slice(0, 5).map(v => `    "${v.varCode}": ${v.exampleValue || v.defaultValue || 'nil'},`).join('\n')}
}
result, err := client.Execute("${rule.ruleCode}", params)</code></pre>
            <pre class="code-block js" style="display:none"><code>const client = new RuleEngineClient({
  serverUrl: 'http://localhost:8080',
  appName: '${escapeHtml(doc.project.projectCode)}',
  token: '&lt;your_token&gt;'
});

const params = {
${inputVars.slice(0, 5).map(v => `  ${v.varCode}: ${v.exampleValue || v.defaultValue || 'null'},`).join('\n')}
};
const result = await client.execute('${rule.ruleCode}', params);
console.log('Result:', result.result);
console.log('ExecuteTime:', result.executeTimeMs, 'ms');</code></pre>
          </div>
        </div>`
      }).join('')

      // 生成完整的HTML文档
      let html = '<!DOCTYPE html><html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"><title>' + escapeHtml(doc.project.projectName) + ' - API文档</title><style>'
      html += '*{margin:0;padding:0;box-sizing:border-box}'
      html += ':root{--primary:#2639E9;--primary-dark:#1a30d4;--bg:#f5f7fa;--border:#e2e8f0;--text:#1a202c;--text-muted:#718096;--success:#48bb78;--warning:#ed8936;--danger:#f56565;--code-bg:#1a1a2e}'
      html += 'body{font-family:-apple-system,BlinkMacSystemFont,\'Segoe UI\',Roboto,\'Helvetica Neue\',Arial,sans-serif;background:var(--bg);color:var(--text)}'
      html += '.container{display:flex;min-height:100vh}.sidebar{width:280px;background:#fff;border-right:1px solid var(--border);position:fixed;height:100vh;overflow-y:auto;flex-shrink:0}'
      html += '.sidebar-header{padding:20px;background:linear-gradient(135deg,var(--primary) 0%,#764ba2 100%);color:#fff;position:sticky;top:0;z-index:10}'
      html += '.sidebar-header .logo-title{display:flex;align-items:center;gap:8px;margin-bottom:8px}'
      html += '.sidebar-header .logo-title .logo{width:28px;height:28px;background:#fff;border-radius:6px;display:flex;align-items:center;justify-content:center;font-size:14px;font-weight:bold;color:var(--primary)}'
      html += '.sidebar-header .brand{font-size:16px;font-weight:600}'
      html += '.sidebar-header h1{font-size:15px;font-weight:500;line-height:1.4;margin-top:4px}'
      html += '.sidebar-header p{font-size:12px;opacity:0.8}'
      html += '.sidebar-search{padding:12px 16px;border-bottom:1px solid var(--border)}'
      html += '.sidebar-search input{width:100%;padding:8px 12px;border:1px solid var(--border);border-radius:6px;font-size:13px;outline:none}'
      html += '.sidebar-search input:focus{border-color:var(--primary)}.sidebar-nav{padding:8px 0}'
      html += '.sidebar-item{padding:12px 16px;cursor:pointer;border-left:3px solid transparent;transition:all .2s}'
      html += '.sidebar-item:hover{background:#f7fafc}'
      html += '.sidebar-item.active{background:#f0f4ff;border-left-color:var(--primary)}'
      html += '.sidebar-item.active .sidebar-item-title{color:var(--primary);font-weight:600}'
      html += '.sidebar-item-title{font-size:14px;font-weight:500;margin-bottom:2px}'
      html += '.sidebar-item-desc{font-size:12px;color:var(--text-muted);font-family:monospace}'
      html += '.main{margin-left:280px;flex:1;padding:24px 32px;max-width:1200px}'
      html += '.content-panel{display:none}.content-panel.active{display:block}'
      html += '.content-header{margin-bottom:32px;padding-bottom:20px;border-bottom:1px solid var(--border)}'
      html += '.header-top{display:flex;justify-content:space-between;align-items:flex-start}'
      html += '.header-left{flex:1}'
      html += '.content-header h2{font-size:24px;font-weight:600;margin-bottom:12px}'
      html += '.content-meta{display:flex;align-items:center;gap:10px;flex-wrap:wrap}'
      html += '.method-badge{background:#48bb78;color:#fff;padding:4px 10px;border-radius:4px;font-size:12px;font-weight:600}'
      html += '.url{background:var(--code-bg);color:#a0aec0;padding:6px 12px;border-radius:4px;font-size:13px;font-family:monospace}'
      html += '.content-meta .description{margin-top:12px;color:var(--text-muted);font-size:14px}'
      html += '.section{margin-bottom:32px}'
      html += '.section h3{font-size:16px;font-weight:600;margin-bottom:16px;padding-bottom:8px;border-bottom:2px solid var(--primary);display:inline-block}'
      html += '.info-table{width:100%;border-collapse:collapse;background:#fff;border-radius:8px;overflow:hidden;margin-bottom:16px}'
      html += '.info-table td{padding:10px 14px;border-bottom:1px solid var(--border);font-size:13px}'
      html += '.info-table tr:last-child td{border-bottom:none}'
      html += '.info-table .info-label{background:#f8fafc;font-weight:500;color:var(--text-muted);width:100px}'
      html += '.auth-box{background:#fff;border:1px solid var(--border);border-radius:8px;padding:16px}'
      html += '.auth-box p{margin-bottom:8px;font-size:14px}.auth-box p:last-child{margin-bottom:0}'
      html += '.desc-box{background:#fff;border:1px solid var(--border);border-radius:8px;padding:16px}'
      html += '.desc-box p{margin-bottom:8px;font-size:14px;line-height:1.6}.desc-box p:last-child{margin-bottom:0}'
      html += '.tips{color:var(--text-muted)!important;font-size:12px!important;margin-top:8px!important}'
      html += '.param-table{width:100%;border-collapse:collapse;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.05);margin-bottom:16px}'
      html += '.param-table th,.param-table td{padding:12px 16px;text-align:left;border-bottom:1px solid var(--border);font-size:13px}'
      html += '.param-table th{background:#f8fafc;font-weight:600;color:var(--text-muted);font-size:12px;text-transform:uppercase}'
      html += '.param-table tr:last-child td{border-bottom:none}.param-table tr:hover td{background:#f7fafc}'
      html += '.param-table code{background:#edf2f7;padding:2px 6px;border-radius:3px;font-family:\'Fira Code\',monospace;font-size:12px;color:var(--primary)}'
      html += '.tag{display:inline-block;padding:2px 8px;border-radius:4px;font-size:11px;font-weight:500}'
      html += '.tag-required{background:#fed7d7;color:#c53030}.tag-info{background:#bee3f8;color:#2b6cb0}'
      html += '.tag-success{background:#c6f6d5;color:#276749}.tag-warning{background:#feebc8;color:#c05621}.tag-default{background:#edf2f7;color:#4a5568}'
      html += '.sub-title{margin:16px 0 8px;font-size:14px;font-weight:600;color:var(--text)}'
      html += '.sub-title .code{margin-left:8px;font-size:12px;color:var(--text-muted);font-weight:normal}'
      html += '.object-field-tree{background:#fff;border:1px solid var(--border);border-radius:8px;padding:8px 10px;margin-bottom:16px}'
      html += '.field-tree{list-style:none;margin:0;padding-left:14px}.field-tree>li{margin:4px 0}'
      html += '.field-tree details{border-left:2px solid #edf2f7;padding-left:8px}.field-tree summary{cursor:pointer;display:flex;align-items:center;gap:10px;min-height:28px}'
      html += '.field-leaf{display:flex;align-items:center;gap:10px;min-height:28px;padding-left:14px}'
      html += '.field-name{min-width:150px}.field-type{min-width:80px;color:var(--text-muted);font-size:12px}.field-label{color:var(--text);font-size:13px}.field-ref{font-size:12px;color:var(--primary)}.field-empty{color:var(--text-muted);font-size:13px;padding:8px}'
      html += '.code-block{background:var(--code-bg);color:#a0aec0;padding:16px;border-radius:8px;overflow-x:auto;font-family:\'Fira Code\',\'Consolas\',monospace;font-size:13px;line-height:1.6;margin-top:8px}'
      html += '.code-tabs{display:flex;gap:0;margin-bottom:0}'
      html += '.code-tab{padding:8px 16px;background:#e2e8f0;cursor:pointer;font-size:13px;border-radius:6px 6px 0 0;margin-right:4px;transition:all .2s}'
      html += '.code-tab:hover{background:#cbd5e0}.code-tab.active{background:var(--code-bg);color:#fff}'
      html += '::-webkit-scrollbar{width:6px;height:6px}::-webkit-scrollbar-track{background:#f1f1f1}::-webkit-scrollbar-thumb{background:#c1c1c1;border-radius:3px}::-webkit-scrollbar-thumb:hover{background:#a1a1a1}'
      html += '@media(max-width:768px){.sidebar{width:240px}.main{margin-left:240px}}'
      html += '</style></head><body>'
      html += '<div class="container"><div class="sidebar"><div class="sidebar-header"><div class="logo-title"><div class="logo">衡</div><span class="brand">衡枢决策引擎</span></div><h1>' + escapeHtml(doc.project.projectName) + '</h1><p>' + escapeHtml(doc.project.projectCode) + ' | ' + (doc.project.status === 1 ? '启用' : '停用') + '</p></div>'
      html += '<div class="sidebar-search"><input type="text" placeholder="搜索接口..." oninput="filterSidebar(this.value)"></div><div class="sidebar-nav">' + sidebarItems + '</div></div>'
      html += '<div class="main">' + contentPanels + '</div></div>'
      html += '<script>'
      html += "document.querySelectorAll('.sidebar-item').forEach(function(item){item.addEventListener('click',function(){document.querySelectorAll('.sidebar-item').forEach(function(i){i.classList.remove('active')});item.classList.add('active');var id=item.dataset.id;document.querySelectorAll('.content-panel').forEach(function(p){p.classList.remove('active')});document.getElementById('panel-'+id).classList.add('active')})});"
      html += "document.querySelectorAll('.code-tab').forEach(function(tab){tab.addEventListener('click',function(){var lang=tab.dataset.lang;var panel=tab.closest('.content-panel');panel.querySelectorAll('.code-tab').forEach(function(t){t.classList.remove('active')});tab.classList.add('active');panel.querySelectorAll('.code-block').forEach(function(b){b.style.display='none'});panel.querySelector('.code-block.'+lang).style.display='block'})});"
      html += "function filterSidebar(keyword){document.querySelectorAll('.sidebar-item').forEach(function(item){var text=item.textContent.toLowerCase();item.style.display=text.includes(keyword.toLowerCase())?'':'none'})}"
      html += '</scr' + 'ipt></body></html>'
      return html
    }
  }
}
</script>
