<template>
  <div class="uiue-list-page">
    <div class="uiue-search-container">
      <el-form :inline="true" size="small">
        <el-form-item label="关键字">
          <el-input v-model="queryParams.keyword" placeholder="项目编码或名称" clearable @keyup.enter.native="handleQuery" />
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
          <el-button type="text" size="small" @click="handleExportDoc(row)">导出文档</el-button>
          <el-button type="text" size="small" class="btn-delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:16px;text-align:right;" :current-page="queryParams.pageNum" :page-size="queryParams.pageSize" :total="total"
      layout="total,sizes,prev,pager,next" :page-sizes="[10,30,50,100,200,500]"
      @current-change="p => { queryParams.pageNum = p; loadData() }" @size-change="s => { queryParams.pageSize = s; queryParams.pageNum = 1; loadData() }" />
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
    <!-- Token显示对话框 -->
    <el-dialog title="AccessToken" :visible.sync="tokenDialogVisible" width="500px">
      <div style="padding: 20px; background: #f5f7fa; border-radius: 4px;">
        <p style="margin: 0; font-family: monospace; word-break: break-all;">{{ fullToken }}</p>
      </div>
      <div style="margin-top: 10px; color: #909399; font-size: 12px;">
        <i class="el-icon-warning"></i> 请妥善保管Token，不要泄露给他人
      </div>
      <div slot="footer">
        <el-button size="small" @click="copyToken">复制</el-button>
        <el-button size="small" type="primary" @click="tokenDialogVisible = false">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>
<script>
import { listProjects, createProject, updateProject, deleteProject, getMaskedToken, exportApiDoc } from '@/api/project'
export default {
  name: 'ProjectList',
  data() {
    return {
      loading: false, tableData: [], total: 0,
      queryParams: { pageNum: 1, pageSize: 10, keyword: '' },
      dialogVisible: false,
      tokenDialogVisible: false,
      fullToken: '',
      form: { id: null, projectCode: '', projectName: '', description: '', status: 1 },
      rules: {
        projectCode: [{ required: true, message: '请输入项目编码', trigger: 'blur' }],
        projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }]
      }
    }
  },
  created() { this.loadData() },
  methods: {
    async loadData() {
      this.loading = true
      try {
        const res = await listProjects(this.queryParams)
        this.tableData = res.data.records
        this.total = res.data.total
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
      } finally { this.loading = false }
    },
    handleQuery() { this.queryParams.pageNum = 1; this.loadData() },
    resetQuery() { this.queryParams.keyword = ''; this.handleQuery() },
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
          const nested = {}
          ;(obj.fields || []).forEach(f => {
            nested[f.varCode] = f.varType === 'STRING' ? '' : null
          })
          params[obj.objectCode] = nested
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
          const nested = {}
          ;(obj.fields || []).forEach(f => {
            nested[f.varCode] = f.varType === 'STRING' ? '' : null
          })
          resultObj[obj.objectCode] = nested
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
        const inputObjRows = (rule.inputDataObjects || []).map(obj =>
          `<div class="sub-title">${escapeHtml(obj.objectLabel || obj.objectCode)} <span class="code">${escapeHtml(obj.objectCode)}</span></div>
          <table class="param-table">
            <thead><tr><th>参数名</th><th>类型</th><th>说明</th></tr></thead>
            <tbody>
              ${(obj.fields || []).map(f =>
                `<tr><td><code>params.${escapeHtml(obj.objectCode)}.${escapeHtml(f.varCode)}</code></td><td>${escapeHtml(f.varTypeLabel || f.varType)}</td><td>${escapeHtml(f.varLabel)}</td></tr>`
              ).join('')}
            </tbody>
          </table>`
        ).join('')

        // 收集输出数据对象
        const outputObjRows = (rule.outputDataObjects || []).map(obj =>
          `<div class="sub-title">${escapeHtml(obj.objectLabel || obj.objectCode)} <span class="code">${escapeHtml(obj.objectCode)}</span></div>
          <table class="param-table">
            <thead><tr><th>参数名</th><th>类型</th><th>说明</th></tr></thead>
            <tbody>
              ${(obj.fields || []).map(f =>
                `<tr><td><code>result.${escapeHtml(obj.objectCode)}.${escapeHtml(f.varCode)}</code></td><td>${escapeHtml(f.varTypeLabel || f.varType)}</td><td>${escapeHtml(f.varLabel)}</td></tr>`
              ).join('')}
            </tbody>
          </table>`
        ).join('')

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
