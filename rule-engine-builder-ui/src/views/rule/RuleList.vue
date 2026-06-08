<template>
  <div class="uiue-list-page">
    <div class="uiue-search-container">
      <el-form :inline="true" size="small">
        <el-form-item label="作用范围">
          <el-select v-model="queryParams.scope" clearable filterable placeholder="全部" style="width:100px;">
            <el-option label="全局" value="GLOBAL" />
            <el-option label="项目" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目编码">
          <el-select v-model="queryParams.projectCode" clearable filterable remote reserve-keyword placeholder="输入筛选" style="width:140px;"
            :remote-method="queryProjectCode" :loading="projectListLoading">
            <el-option v-for="p in filteredProjectCodes" :key="p.projectCode" :label="p.projectCode" :value="p.projectCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目名称">
          <el-select v-model="queryParams.projectName" clearable filterable remote reserve-keyword placeholder="输入筛选" style="width:140px;"
            :remote-method="queryProjectName" :loading="projectListLoading">
            <el-option v-for="p in filteredProjectNames" :key="p.projectName" :label="p.projectName" :value="p.projectName" />
          </el-select>
        </el-form-item>
        <el-form-item label="发布状态">
          <el-select v-model="queryParams.status" clearable filterable placeholder="全部" style="width:100px;">
            <el-option label="草稿" :value="0" />
            <el-option label="已发布" :value="1" />
            <el-option label="已下线" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型类型">
          <el-select v-model="queryParams.modelType" clearable filterable placeholder="全部" style="width:120px;">
            <el-option label="决策表" value="TABLE" />
            <el-option label="决策树" value="TREE" />
            <el-option label="决策流" value="FLOW" />
            <el-option label="交叉表" value="CROSS" />
            <el-option label="评分卡" value="SCORE" />
            <el-option label="复杂交叉表" value="CROSS_ADV" />
            <el-option label="复杂评分卡" value="SCORE_ADV" />
            <el-option label="QL脚本" value="SCRIPT" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则编码">
          <el-input v-model="queryParams.ruleCode" clearable placeholder="规则编码" style="width:140px;" />
        </el-form-item>
        <el-form-item label="规则名称">
          <el-input v-model="queryParams.ruleName" clearable placeholder="规则名称" style="width:140px;" />
        </el-form-item>
        <el-form-item label="发布版本">
          <el-input v-model="queryParams.publishedVersion" clearable placeholder="发布版本" style="width:100px;" />
        </el-form-item>
        <el-form-item>
          <el-button size="small" type="primary" icon="el-icon-search" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
    <div class="uiue-btn-bar">
      <div class="btn-right">
        <el-button type="primary" size="small" icon="el-icon-plus" @click="handleCreate">新建规则</el-button>
      </div>
    </div>
    <el-table :data="tableData" border size="small" v-loading="loading" style="width: 100%;">
      <el-table-column label="作用范围" width="90" align="center">
        <template slot-scope="{ row }">
          <el-tag :type="row.scope === 'GLOBAL' ? 'scope-global' : 'scope-project'" size="mini">{{ row.scope === 'GLOBAL' ? '全局' : '项目级' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="projectName" label="项目名称" min-width="130" show-overflow-tooltip />
      <el-table-column prop="ruleCode" label="规则编码" min-width="150" show-overflow-tooltip />
      <el-table-column prop="ruleName" label="规则名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="modelType" label="模型类型" min-width="90" align="center">
        <template slot-scope="{ row }">
          <el-tag size="mini">{{ modelTypeLabel(row.modelType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="发布状态" min-width="80" align="center">
        <template slot-scope="{ row }">
          <el-tag :type="statusTagType(row.status)" size="mini">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="currentVersion" label="设计版本" min-width="80" align="center" />
      <el-table-column prop="publishedVersion" label="发布版本" min-width="80" align="center" />
      <el-table-column prop="description" label="描述" min-width="140" show-overflow-tooltip />
      <el-table-column prop="inputFields" label="输入字段" min-width="150">
        <template slot-scope="{ row }">
          <span>{{ fieldTagsLabel(row.inputFields) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="outputFields" label="输出字段" min-width="150">
        <template slot-scope="{ row }">
          <span>{{ fieldTagsLabel(row.outputFields) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" min-width="160" />
      <el-table-column label="操作" width="180" align="center">
        <template slot-scope="{ row }">
          <el-button type="text" size="small" @click="handleDesign(row)">设计</el-button>
          <el-button type="text" size="small" @click="handlePublish(row)">{{ row.status === 1 ? '重新发布' : '发布' }}</el-button>
          <el-button type="text" size="small" v-if="row.status === 1" @click="handleUnpublish(row)">下线</el-button>
          <el-button type="text" size="small" class="btn-delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:16px;text-align:right;" :current-page="queryParams.pageNum" :page-size="queryParams.pageSize" :total="total"
      layout="total,sizes,prev,pager,next" :page-sizes="[10,30,50,100,200,500]"
      @current-change="p => { queryParams.pageNum = p; loadData() }" @size-change="s => { queryParams.pageSize = s; queryParams.pageNum = 1; loadData() }" />

    <!-- 新建规则弹窗 -->
    <el-dialog title="新建规则" :visible.sync="dialogVisible" width="500px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" size="small">
        <el-form-item label="作用范围">
          <el-select v-model="form.scope" placeholder="选择作用范围" style="width:100%;" @change="onRuleScopeChange">
            <el-option label="🌐 全局（所有项目可用）" value="GLOBAL" />
            <el-option label="📁 项目级" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scope === 'PROJECT'" label="项目名称" prop="projectId">
          <el-select v-model="form.projectId" placeholder="请选择项目" style="width:100%;" filterable clearable>
            <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="规则编码" prop="ruleCode"><el-input v-model="form.ruleCode" placeholder="英文标识，如 riskCheckRule" /></el-form-item>
        <el-form-item label="规则名称" prop="ruleName"><el-input v-model="form.ruleName" placeholder="中文名称，如 风控规则" /></el-form-item>
        <el-form-item label="模型类型" prop="modelType">
          <el-select v-model="form.modelType" style="width:100%;">
            <el-option label="决策表" value="TABLE" />
            <el-option label="决策树" value="TREE" />
            <el-option label="决策流" value="FLOW" />
            <el-option label="交叉表" value="CROSS" />
            <el-option label="评分卡" value="SCORE" />
            <el-option label="复杂交叉表" value="CROSS_ADV" />
            <el-option label="复杂评分卡" value="SCORE_ADV" />
            <el-option label="QL脚本" value="SCRIPT" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" placeholder="规则功能描述" /></el-form-item>
        <el-form-item label="输入字段">
          <el-input v-model="form.inputFields" type="textarea" :rows="2" placeholder="输入变量，多个用逗号分隔，如：amount, age, income" />
          <div style="color:#909399;font-size:12px;line-height:1.4;">提示：输入变量为规则的入参变量</div>
        </el-form-item>
        <el-form-item label="输出字段">
          <el-input v-model="form.outputFields" type="textarea" :rows="2" placeholder="输出变量，多个用逗号分隔，如：resultScore, level" />
          <div style="color:#909399;font-size:12px;line-height:1.4;">提示：输出变量为规则的结果变量</div>
        </el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="dialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="handleSubmit">确定</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import variables from '@/styles/variables.scss'
import { listDefinitions, createDefinition, deleteDefinition, publishRule, unpublishRule } from '@/api/definition'
import { listProjects } from '@/api/project'

export default {
  name: 'RuleList',
  data() {
    return {
      colorDanger: variables.colorDanger,
      loading: false,
      tableData: [],
      total: 0,
      projectList: [],
      projectListLoading: false,
      filteredProjectCodes: [],
      filteredProjectNames: [],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        scope: '',
        projectCode: '',
        projectName: '',
        modelType: '',
        status: '',
        ruleCode: '',
        ruleName: '',
        publishedVersion: ''
      },
      dialogVisible: false,
      form: {
        scope: 'GLOBAL',
        projectId: null,
        ruleCode: '',
        ruleName: '',
        modelType: 'TABLE',
        description: '',
        status: 0
      },
      rules: {
        ruleCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }],
        ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
        modelType: [{ required: true, message: '请选择模型类型', trigger: 'change' }]
      }
    }
  },
  created() {
    this.loadData()
    this.loadProjectList()
  },
  methods: {
    async loadProjectList() {
      this.projectListLoading = true
      try {
        const res = await listProjects({ pageNum: 1, pageSize: 500 })
        if (res.data) {
          const list = res.data.records || res.data || []
          this.projectList = list
          this.filteredProjectCodes = list.slice(0, 20)
          this.filteredProjectNames = list.slice(0, 20)
        }
      } catch (e) {
        console.error('加载项目列表失败:', e)
        this.projectList = []
        this.filteredProjectCodes = []
        this.filteredProjectNames = []
      } finally {
        this.projectListLoading = false
      }
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
    async loadData() {
      this.loading = true
      try {
        const params = { ...this.queryParams }
        Object.keys(params).forEach(key => {
          if (params[key] === '' || params[key] === null || params[key] === undefined) {
            delete params[key]
          }
        })
        const res = await listDefinitions(params)

        // 兼容后端返回 IPage（{ records, total }）和直接返回数组两种格式
        if (res.data) {
          if (Array.isArray(res.data)) {
            // 直接返回数组
            this.tableData = res.data
            this.total = res.data.length
          } else if (res.data.records) {
            // IPage 格式
            this.tableData = res.data.records
            this.total = res.data.total || 0
          } else {
            // 其他情况，尝试取第一个属性的值
            const keys = Object.keys(res.data)
            if (keys.length > 0) {
              const firstValue = res.data[keys[0]]
              if (Array.isArray(firstValue)) {
                this.tableData = firstValue
                this.total = firstValue.length
              }
            }
          }
        } else {
          this.tableData = []
          this.total = 0
        }
      } catch (e) {
        console.error('加载数据失败:', e)
        this.tableData = []
        this.total = 0
      } finally {
        this.loading = false
      }
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.loadData()
    },
    resetQuery() {
      this.queryParams = {
        pageNum: 1,
        pageSize: this.queryParams.pageSize,
        scope: '',
        projectCode: '',
        projectName: '',
        modelType: '',
        status: '',
        ruleCode: '',
        ruleName: '',
        publishedVersion: ''
      }
      this.loadData()
    },
    onRuleScopeChange(val) {
      if (val === 'GLOBAL') {
        this.form.projectId = null
      }
    },
    handleCreate() {
      this.form = {
        scope: 'GLOBAL',
        projectId: null,
        ruleCode: '',
        ruleName: '',
        modelType: 'TABLE',
        description: '',
        inputFields: '',
        outputFields: '',
        status: 0
      }
      this.dialogVisible = true
      this.$nextTick(() => { if (this.$refs.formRef) this.$refs.formRef.clearValidate() })
    },
    async handleSubmit() {
      this.$refs.formRef.validate(async valid => {
        if (!valid) return
        if (this.form.scope === 'PROJECT' && !this.form.projectId) {
          this.$message.warning('请选择项目')
          return
        }
        try {
          await createDefinition(this.form)
          this.$message.success('创建成功')
          this.dialogVisible = false
          this.loadData()
        } catch (e) {
          this.$message.error('操作失败')
        }
      })
    },
    handleDesign(row) {
      const routes = {
        TABLE: '/designer/table',
        TREE: '/designer/tree',
        FLOW: '/designer/flow',
        CROSS: '/designer/cross',
        SCORE: '/designer/score',
        CROSS_ADV: '/designer/cross-adv',
        SCORE_ADV: '/designer/score-adv',
        SCRIPT: '/designer/script'
      }
      const path = routes[row.modelType] || '/designer/table'
      this.$router.push(`${path}/${row.id}`)
    },
    async handlePublish(row) {
      try {
        await this.$confirm('确定发布规则「' + row.ruleName + '」？', '确认', { type: 'info' })
        await publishRule(row.id)
        this.$message.success('发布成功')
        this.loadData()
      } catch (e) {
        if (e !== 'cancel') this.$message.error('发布失败')
      }
    },
    async handleUnpublish(row) {
      try {
        await this.$confirm('确定下线规则「' + row.ruleName + '」？', '确认', { type: 'warning' })
        await unpublishRule(row.id)
        this.$message.success('下线成功')
        this.loadData()
      } catch (e) {
        if (e !== 'cancel') this.$message.error('下线失败')
      }
    },
    handleDelete(row) {
      this.$confirm('确定删除规则「' + row.ruleName + '」？', '确认', { type: 'warning' })
        .then(async () => {
          await deleteDefinition(row.id)
          this.$message.success('删除成功')
          this.loadData()
        }).catch(() => {})
    },
    fieldTagsLabel(val) {
      if (!val) return '—'
      try {
        const arr = JSON.parse(val)
        if (Array.isArray(arr) && arr.length) return arr.join(', ')
      } catch (_) { /* ignore */ }
      return val || '—'
    },
    modelTypeLabel(type) {
      return {
        TABLE: '决策表', TREE: '决策树', FLOW: '决策流',
        CROSS: '交叉表', SCORE: '评分卡',
        CROSS_ADV: '复杂交叉表', SCORE_ADV: '复杂评分卡',
        SCRIPT: 'QL脚本'
      }[type] || type
    },
    statusLabel(status) {
      return { 0: '草稿', 1: '已发布', 2: '已下线' }[status] || status
    },
    statusTagType(status) {
      return { 0: 'info', 1: 'success', 2: 'warning' }[status] || 'info'
    }
  }
}
</script>

<style scoped>
</style>