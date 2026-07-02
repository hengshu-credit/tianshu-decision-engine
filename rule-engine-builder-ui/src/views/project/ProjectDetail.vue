<template>
  <div class="uiue-list-page">
    <div style="margin-bottom:16px;display:flex;align-items:center;justify-content:space-between;">
      <h2 style="margin:0;">{{ project ? project.projectName : '加载中...' }}</h2>
      <el-button size="small" icon="el-icon-back" @click="$router.push('/project')">返回</el-button>
    </div>

    <!-- 筛选条件 -->
    <div class="uiue-search-container">
      <el-form :inline="true" size="small">
        <el-form-item label="作用范围">
          <el-select v-model="qp.scope" clearable style="width:120px;">
            <el-option label="项目级" value="PROJECT" />
            <el-option label="全局" value="GLOBAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型类型">
          <el-select v-model="qp.modelType" clearable style="width:140px;">
            <el-option label="决策表" value="TABLE" />
            <el-option label="决策树" value="TREE" />
            <el-option label="决策流" value="FLOW" />
            <el-option label="规则集" value="RULE_SET" />
            <el-option label="交叉表" value="CROSS" />
            <el-option label="评分卡" value="SCORE" />
            <el-option label="复杂交叉表" value="CROSS_ADV" />
            <el-option label="复杂评分卡" value="SCORE_ADV" />
            <el-option label="QL脚本" value="SCRIPT" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则编码">
          <el-input v-model="qp.ruleCode" clearable style="width:150px;" />
        </el-form-item>
        <el-form-item label="规则名称">
          <el-input v-model="qp.ruleName" clearable style="width:150px;" />
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker v-model="createTimeRange" type="daterange" range-separator="~" start-placeholder="开始日期" end-placeholder="结束日期" value-format="yyyy-MM-dd" style="width:240px;" />
        </el-form-item>
        <el-form-item label="更新时间">
          <el-date-picker v-model="updateTimeRange" type="daterange" range-separator="~" start-placeholder="开始日期" end-placeholder="结束日期" value-format="yyyy-MM-dd" style="width:240px;" />
        </el-form-item>
        <el-form-item><el-button type="primary" @click="handleQuery">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
    </div>

    <!-- 操作按钮栏 -->
    <div class="uiue-btn-bar">
      <div class="btn-right">
        <el-button type="primary" size="small" icon="el-icon-plus" @click="openAddRuleDialog">添加规则</el-button>
        <el-button type="primary" size="small" icon="el-icon-document-add" @click="dlgVis=true">新建规则</el-button>
      </div>
    </div>

    <!-- 规则列表 -->
    <el-table :data="list" border size="small" v-loading="loading" style="width: 100%;">
      <el-table-column prop="ruleCode" label="规则编码" min-width="150" show-overflow-tooltip />
      <el-table-column prop="ruleName" label="规则名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="modelType" label="模型类型" min-width="90" align="center">
        <template slot-scope="{row}"><el-tag size="mini">{{mtl(row.modelType)}}</el-tag></template>
      </el-table-column>
      <el-table-column prop="scope" label="作用范围" min-width="90" align="center">
        <template slot-scope="{row}">
          <el-tag :type="row.scope === 'GLOBAL' ? 'scope-global' : 'scope-project'" size="mini">
            {{ row.scope === 'GLOBAL' ? '全局' : '项目级' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" min-width="70" align="center">
        <template slot-scope="{row}"><el-tag :type="{0:'info',1:'success',2:'warning'}[row.status]" size="mini">{{['草稿','已发布','已下线'][row.status]}}</el-tag></template>
      </el-table-column>
      <el-table-column prop="currentVersion" label="设计版本" min-width="80" align="center" />
      <el-table-column prop="publishedVersion" label="发布版本" min-width="80" align="center" />
      <el-table-column prop="createTime" label="创建时间" min-width="160" align="center">
        <template slot-scope="{row}">{{row.createTime ? formatDateTime(row.createTime) : '-'}}</template>
      </el-table-column>
      <el-table-column prop="updateTime" label="修改时间" min-width="160" align="center">
        <template slot-scope="{row}">{{row.updateTime ? formatDateTime(row.updateTime) : '-'}}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="180" align="center">
        <template slot-scope="{row}">
          <el-button type="text" size="small" @click="go(row)">设计</el-button>
          <el-button type="text" size="small" @click="pub(row)">{{ row.status === 1 ? '重新发布' : '发布' }}</el-button>
          <el-button type="text" size="small" v-if="row.status===1" @click="unpub(row)">下线</el-button>
          <el-button type="text" size="small" class="btn-delete" @click="del(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:16px;text-align:right;" :current-page="qp.pageNum" :page-size="qp.pageSize" :total="total"
      layout="total,sizes,prev,pager,next" :page-sizes="[10,30,50,100,200,500]"
      @current-change="p=>{qp.pageNum=p;load()}" @size-change="s=>{qp.pageSize=s;qp.pageNum=1;load()}" />

    <!-- 新建规则对话框 -->
    <el-dialog title="新建规则" :visible.sync="dlgVis" width="500px">
      <el-form ref="f" :model="fm" :rules="{ruleCode:[{required:true,message:'必填',trigger:'blur'}],ruleName:[{required:true,message:'必填',trigger:'blur'}],modelType:[{required:true,message:'必选',trigger:'change'}]}" label-width="100px" size="small">
        <el-form-item label="规则编码" prop="ruleCode"><el-input v-model="fm.ruleCode" /></el-form-item>
        <el-form-item label="规则名称" prop="ruleName"><el-input v-model="fm.ruleName" /></el-form-item>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="fm.modelType" style="width:100%">
            <el-option label="决策表" value="TABLE" />
            <el-option label="决策树" value="TREE" />
            <el-option label="决策流" value="FLOW" />
            <el-option label="规则集" value="RULE_SET" />
            <el-option label="交叉表" value="CROSS" />
            <el-option label="评分卡" value="SCORE" />
            <el-option label="复杂交叉表" value="CROSS_ADV" />
            <el-option label="复杂评分卡" value="SCORE_ADV" />
            <el-option label="QL脚本" value="SCRIPT" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="fm.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button size="small" @click="dlgVis=false">取消</el-button><el-button size="small" type="primary" @click="submit">确定</el-button></div>
    </el-dialog>

    <!-- 添加全局规则对话框 -->
    <el-dialog title="添加全局规则" :visible.sync="addRuleDlgVis" width="800px">
      <div style="margin-bottom:12px;">
        <el-form :inline="true" size="small">
          <el-form-item label="模型类型">
            <el-select v-model="globalQp.modelType" clearable style="width:140px;">
              <el-option label="决策表" value="TABLE" />
              <el-option label="决策树" value="TREE" />
              <el-option label="决策流" value="FLOW" />
              <el-option label="规则集" value="RULE_SET" />
              <el-option label="交叉表" value="CROSS" />
              <el-option label="评分卡" value="SCORE" />
              <el-option label="复杂交叉表" value="CROSS_ADV" />
              <el-option label="复杂评分卡" value="SCORE_ADV" />
              <el-option label="QL脚本" value="SCRIPT" />
            </el-select>
          </el-form-item>
          <el-form-item label="规则编码">
            <el-input v-model="globalQp.ruleCode" clearable style="width:150px;" />
          </el-form-item>
          <el-form-item label="规则名称">
            <el-input v-model="globalQp.ruleName" clearable style="width:150px;" />
          </el-form-item>
          <el-form-item><el-button type="primary" size="small" @click="loadGlobalRules">查询</el-button><el-button size="small" @click="resetGlobalQuery">重置</el-button></el-form-item>
        </el-form>
      </div>
      <el-table :data="globalRuleList" border size="small" v-loading="globalLoading" style="width: 100%;" max-height="400">
        <el-table-column prop="ruleCode" label="规则编码" min-width="150" show-overflow-tooltip />
        <el-table-column prop="ruleName" label="规则名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="modelType" label="模型类型" min-width="90" align="center">
          <template slot-scope="{row}"><el-tag size="mini">{{mtl(row.modelType)}}</el-tag></template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="70" align="center">
          <template slot-scope="{row}"><el-tag :type="{0:'info',1:'success',2:'warning'}[row.status]" size="mini">{{['草稿','已发布','已下线'][row.status]}}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template slot-scope="{row}">
            <el-button type="text" size="small" @click="addGlobalToProject(row)">添加</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top:12px;text-align:right;" :current-page="globalQp.pageNum" :page-size="globalQp.pageSize" :total="globalTotal"
        layout="total,sizes,prev,pager,next" :page-sizes="[10,30,50]"
        @current-change="p=>{globalQp.pageNum=p;loadGlobalRules()}" @size-change="s=>{globalQp.pageSize=s;globalQp.pageNum=1;loadGlobalRules()}" />
    </el-dialog>
  </div>
</template>

<script>
import { createDefinition, deleteDefinition } from '@/api/definition'
import { getProject } from '@/api/project'
import request from '@/api/request'
import { restorePageState, savePageState } from '@/utils/pageStateCache'
export default {
  name: 'ProjectDetail',
  data() {
    return {
      pid: null,
      project: null,
      loading: false,
      list: [],
      total: 0,
      qp: {
        pageNum: 1,
        pageSize: 10,
        scope: '',
        modelType: '',
        ruleCode: '',
        ruleName: '',
        createBeginTime: '',
        createEndTime: '',
        updateBeginTime: '',
        updateEndTime: ''
      },
      createTimeRange: null,
      updateTimeRange: null,
      dlgVis: false,
      fm: { ruleCode: '', ruleName: '', modelType: '', description: '' },
      // 添加全局规则相关
      addRuleDlgVis: false,
      globalLoading: false,
      globalRuleList: [],
      globalTotal: 0,
      globalQp: {
        pageNum: 1,
        pageSize: 10,
        modelType: '',
        ruleCode: '',
        ruleName: ''
      }
    }
  },
  created() {
    this.pid = this.$route.params.id
    this.restoreCachedState()
    getProject(this.pid).then(r => { this.project = r.data })
    this.load()
  },
  methods: {
    cacheKey() {
      return 'ProjectDetail:' + this.pid
    },
    restoreCachedState() {
      const state = restorePageState(this.cacheKey())
      if (state.qp) this.qp = { ...this.qp, ...state.qp }
      if (state.globalQp) this.globalQp = { ...this.globalQp, ...state.globalQp }
      if (state.createTimeRange) this.createTimeRange = state.createTimeRange
      if (state.updateTimeRange) this.updateTimeRange = state.updateTimeRange
    },
    saveCachedState() {
      savePageState(this.cacheKey(), {
        qp: this.qp,
        globalQp: this.globalQp,
        createTimeRange: this.createTimeRange,
        updateTimeRange: this.updateTimeRange
      })
    },
    async load() {
      this.loading = true
      try {
        // 处理时间范围参数
        if (this.createTimeRange && this.createTimeRange.length === 2) {
          this.qp.createBeginTime = this.createTimeRange[0]
          this.qp.createEndTime = this.createTimeRange[1]
        } else {
          this.qp.createBeginTime = ''
          this.qp.createEndTime = ''
        }
        if (this.updateTimeRange && this.updateTimeRange.length === 2) {
          this.qp.updateBeginTime = this.updateTimeRange[0]
          this.qp.updateEndTime = this.updateTimeRange[1]
        } else {
          this.qp.updateBeginTime = ''
          this.qp.updateEndTime = ''
        }
        this.saveCachedState()
        const r = await request({
          url: '/rule/definition/project-list/' + this.pid,
          method: 'get',
          params: this.qp
        })
        this.list = r.data.records
        this.total = r.data.total
      } finally {
        this.loading = false
      }
    },
    handleQuery() {
      this.qp.pageNum = 1
      this.load()
    },
    resetQuery() {
      this.qp = {
        pageNum: 1,
        pageSize: this.qp.pageSize,
        scope: '',
        modelType: '',
        ruleCode: '',
        ruleName: '',
        createBeginTime: '',
        createEndTime: '',
        updateBeginTime: '',
        updateEndTime: ''
      }
      this.createTimeRange = null
      this.updateTimeRange = null
      this.saveCachedState()
      this.load()
    },
    mtl(t) {
      return {
        TABLE: '决策表',
        TREE: '决策树',
        FLOW: '决策流',
        RULE_SET: '规则集',
        CROSS: '交叉表',
        SCORE: '评分卡',
        CROSS_ADV: '复杂交叉表',
        SCORE_ADV: '复杂评分卡',
        SCRIPT: 'QL脚本'
      }[t] || t
    },
    formatDateTime(dateTime) {
      if (!dateTime) return ''
      const date = new Date(dateTime)
      const y = date.getFullYear()
      const m = String(date.getMonth() + 1).padStart(2, '0')
      const d = String(date.getDate()).padStart(2, '0')
      const h = String(date.getHours()).padStart(2, '0')
      const min = String(date.getMinutes()).padStart(2, '0')
      const s = String(date.getSeconds()).padStart(2, '0')
      return `${y}-${m}-${d} ${h}:${min}:${s}`
    },
    go(r) {
      const m = {
        TABLE: 'table',
        TREE: 'tree',
        FLOW: 'flow',
        RULE_SET: 'ruleset',
        CROSS: 'cross',
        SCORE: 'score',
        CROSS_ADV: 'cross-adv',
        SCORE_ADV: 'score-adv',
        SCRIPT: 'script'
      }
      this.$router.push('/designer/' + m[r.modelType] + '/' + r.id)
    },
    async pub(r) {
      await this.$confirm('确定发布?')
      await request({ url: '/rule/definition/publish/' + r.id, method: 'post', data: {} })
      this.$message.success('发布成功')
      this.load()
    },
    async unpub(r) {
      await this.$confirm('确定下线?')
      await request({ url: '/rule/definition/unpublish/' + r.id, method: 'post' })
      this.$message.success('下线成功')
      this.load()
    },
    async del(r) {
      await this.$confirm('确定删除?')
      await deleteDefinition(r.id)
      this.$message.success('删除成功')
      this.load()
    },
    submit() {
      this.$refs.f.validate(async v => {
        if (!v) return
        await createDefinition({ ...this.fm, projectId: this.pid })
        this.$message.success('创建成功')
        this.dlgVis = false
        this.load()
      })
    },
    // 打开添加全局规则对话框
    openAddRuleDialog() {
      this.addRuleDlgVis = true
      this.globalQp = {
        pageNum: 1,
        pageSize: 10,
        modelType: '',
        ruleCode: '',
        ruleName: ''
      }
      this.loadGlobalRules()
    },
    // 加载全局规则列表
    async loadGlobalRules() {
      this.globalLoading = true
      try {
        this.saveCachedState()
        const r = await request({
          url: '/rule/definition/global-list',
          method: 'get',
          params: this.globalQp
        })
        this.globalRuleList = r.data.records || []
        this.globalTotal = r.data.total
      } finally {
        this.globalLoading = false
      }
    },
    resetGlobalQuery() {
      this.globalQp = {
        pageNum: 1,
        pageSize: this.globalQp.pageSize,
        modelType: '',
        ruleCode: '',
        ruleName: ''
      }
      this.saveCachedState()
      this.loadGlobalRules()
    },
    // 将全局规则添加到项目
    async addGlobalToProject(rule) {
      try {
        await request({
          url: '/rule/definition/add-global-to-project',
          method: 'post',
          data: {
            definitionId: rule.id,
            projectId: this.pid
          }
        })
        this.$message.success('添加成功')
        this.load()
      } catch (e) {
        this.$message.error(e.message || '添加失败')
      }
    }
  }
}
</script>
