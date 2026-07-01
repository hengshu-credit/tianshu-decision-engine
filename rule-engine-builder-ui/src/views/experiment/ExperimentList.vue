<template>
  <div class="uiue-list-page experiment-page">
    <div class="page-head">
      <div>
        <h2>分流实验</h2>
        <div class="page-subtitle">配置冠军挑战和测试组空跑，执行结果会返回实验标签并写入实验明细日志。</div>
        <div class="page-tip">冠军组和挑战组参与生产分流；测试组在生产组执行后空跑，可按条件命中、互斥执行，并可控制是否调用 API 外数。</div>
      </div>
      <el-button size="small" type="primary" icon="el-icon-plus" @click="handleCreate">新建实验</el-button>
    </div>

    <div class="uiue-search-container">
      <el-form :inline="true" size="small">
        <el-form-item label="项目">
          <el-select v-model="query.projectId" clearable filterable placeholder="全部项目" style="width:180px;">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width:110px;">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="query.keyword" clearable placeholder="编码或名称" style="width:180px;" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table :data="experiments" border size="small" v-loading="loading">
      <el-table-column prop="experimentCode" label="实验编码" min-width="150" show-overflow-tooltip />
      <el-table-column prop="experimentName" label="实验名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="projectCode" label="项目编码" min-width="120" show-overflow-tooltip />
      <el-table-column label="冠军挑战" width="110" align="center">
        <template slot-scope="{ row }">{{ routeModeLabel(row.routingMode) }}</template>
      </el-table-column>
      <el-table-column label="测试分流" width="110" align="center">
        <template slot-scope="{ row }">{{ routeModeLabel(row.testRoutingMode || 'CONDITION') }}</template>
      </el-table-column>
      <el-table-column label="生产组" min-width="190">
        <template slot-scope="{ row }">
          <el-tag v-for="g in productionGroups(row)" :key="g.groupCode" size="mini" :type="g.groupType === 'CHAMPION' ? 'success' : 'warning'" class="group-tag">
            {{ g.groupName || g.groupCode }}<span v-if="row.routingMode === 'RATIO'"> {{ g.trafficRatio || 0 }}%</span>
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="测试组" min-width="160">
        <template slot-scope="{ row }">
          <el-tag v-for="g in testGroups(row)" :key="g.groupCode" size="mini" type="info" class="group-tag">
            {{ g.groupName || g.groupCode }}<span v-if="(row.testRoutingMode || 'CONDITION') === 'RATIO'"> {{ g.trafficRatio || 0 }}%</span>
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template slot-scope="{ row }">
          <el-tag size="mini" :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="190" align="center">
        <template slot-scope="{ row }">
          <el-button type="text" size="mini" @click="handleTest(row)">执行</el-button>
          <el-button type="text" size="mini" @click="handleEdit(row)">编辑</el-button>
          <el-button type="text" size="mini" class="btn-delete" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      :current-page="query.pageNum"
      :page-size="query.pageSize"
      :total="total"
      layout="total,sizes,prev,pager,next"
      :page-sizes="[10,30,50,100]"
      @current-change="p => { query.pageNum = p; loadExperiments() }"
      @size-change="s => { query.pageSize = s; query.pageNum = 1; loadExperiments() }"
    />

    <el-dialog :title="form.id ? '编辑分流实验' : '新建分流实验'" :visible.sync="formVisible" width="1040px" append-to-body :close-on-click-modal="false">
      <el-form ref="form" :model="form" :rules="rules" label-width="110px" size="small">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="项目" prop="projectId">
              <el-select v-model="form.projectId" filterable placeholder="选择项目" style="width:100%;" @change="onProjectChange">
                <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="实验编码" prop="experimentCode">
              <el-input v-model="form.experimentCode" placeholder="EXP_RISK_CHAMPION" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="实验名称" prop="experimentName">
              <el-input v-model="form.experimentName" placeholder="冠军挑战实验" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="10">
            <el-form-item label="冠军挑战">
              <el-radio-group v-model="form.routingMode" @change="onRoutingModeChange">
                <el-radio-button label="RATIO">比例分流</el-radio-button>
                <el-radio-button label="CONDITION">条件分流</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="测试组">
              <el-radio-group v-model="form.testRoutingMode" @change="onTestRoutingModeChange">
                <el-radio-button label="RATIO">比例分流</el-radio-button>
                <el-radio-button label="CONDITION">条件分流</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item v-if="form.testRoutingMode === 'CONDITION'" label="测试互斥">
              <el-switch v-model="form.testExclusive" :active-value="1" :inactive-value="0" active-text="互斥" inactive-text="非互斥" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="section-title">冠军挑战配置</div>
        <el-alert v-if="ratioTotal !== 100 && form.routingMode === 'RATIO'" type="warning" :closable="false" show-icon :title="'当前生产组比例合计 ' + ratioTotal + '%，必须等于 100%'" />
        <el-table :data="productionFormGroups" border size="mini" class="group-table">
          <el-table-column v-if="form.routingMode === 'CONDITION'" label="条件" min-width="360">
            <template slot-scope="{ row }">
              <div v-if="isFallbackGroup(row)" class="fallback-cell">兜底动作</div>
              <condition-group-editor
                v-else-if="row.conditionConfig"
                :group="row.conditionConfig"
                :vars="varPickerOptions"
                :get-var-options-fn="getVarOptions"
                :selected-vars="selectedVarPickerOptions"
              />
              <el-input v-else v-model="row.conditionExpression" size="mini" placeholder="兼容旧表达式，例如 amount > 10000" />
            </template>
          </el-table-column>
          <el-table-column label="类型" width="120">
            <template slot-scope="{ row }">
              <el-select v-model="row.groupType" style="width:100%;">
                <el-option label="冠军组" value="CHAMPION" />
                <el-option label="挑战组" value="CHALLENGER" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="组编码" width="150"><template slot-scope="{ row }"><el-input v-model="row.groupCode" /></template></el-table-column>
          <el-table-column label="组名称" width="150"><template slot-scope="{ row }"><el-input v-model="row.groupName" /></template></el-table-column>
          <el-table-column label="执行规则" min-width="220">
            <template slot-scope="{ row }">
              <el-select v-model="row.ruleCode" filterable placeholder="选择规则" style="width:100%;">
                <el-option v-for="r in rulesForProject" :key="r.ruleCode" :label="ruleLabel(r)" :value="r.ruleCode" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column v-if="form.routingMode === 'RATIO'" label="比例%" width="120"><template slot-scope="{ row }"><el-input-number v-model="row.trafficRatio" :min="0" :max="100" :precision="2" style="width:100%;" /></template></el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template slot-scope="{ row }">
              <el-button type="text" size="mini" @click="removeGroup(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="table-actions">
          <el-button size="mini" icon="el-icon-plus" @click="addChallenger">添加生产组</el-button>
          <el-button v-if="form.routingMode === 'CONDITION'" size="mini" @click="addProductionFallback">添加兜底动作</el-button>
        </div>

        <div class="section-title">测试组配置</div>
        <el-alert v-if="testRatioTotal !== 100 && form.testRoutingMode === 'RATIO' && testFormGroups.length > 0" type="warning" :closable="false" show-icon :title="'当前测试组比例合计 ' + testRatioTotal + '%，必须等于 100%'" />
        <el-table :data="testFormGroups" border size="mini" class="group-table">
          <el-table-column v-if="form.testRoutingMode === 'CONDITION'" label="条件" min-width="360">
            <template slot-scope="{ row }">
              <div v-if="isFallbackGroup(row)" class="fallback-cell">兜底动作</div>
              <condition-group-editor
                v-else-if="row.conditionConfig"
                :group="row.conditionConfig"
                :vars="varPickerOptions"
                :get-var-options-fn="getVarOptions"
                :selected-vars="selectedVarPickerOptions"
              />
              <el-input v-else v-model="row.conditionExpression" size="mini" placeholder="兼容旧表达式，例如 amount > 10000" />
            </template>
          </el-table-column>
          <el-table-column label="组编码" width="150"><template slot-scope="{ row }"><el-input v-model="row.groupCode" /></template></el-table-column>
          <el-table-column label="组名称" width="150"><template slot-scope="{ row }"><el-input v-model="row.groupName" /></template></el-table-column>
          <el-table-column label="执行规则" min-width="220">
            <template slot-scope="{ row }">
              <el-select v-model="row.ruleCode" filterable placeholder="选择规则" style="width:100%;">
                <el-option v-for="r in rulesForProject" :key="r.ruleCode" :label="ruleLabel(r)" :value="r.ruleCode" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column v-if="form.testRoutingMode === 'RATIO'" label="比例%" width="120"><template slot-scope="{ row }"><el-input-number v-model="row.trafficRatio" :min="0" :max="100" :precision="2" style="width:100%;" /></template></el-table-column>
          <el-table-column label="调用API外数" width="120" align="center"><template slot-scope="{ row }"><el-switch v-model="row.invokeExternalSource" :active-value="1" :inactive-value="0" /></template></el-table-column>
          <el-table-column label="操作" width="80" align="center"><template slot-scope="{ row }"><el-button type="text" size="mini" @click="removeGroup(row)">删除</el-button></template></el-table-column>
        </el-table>
        <div class="table-actions">
          <el-button size="mini" icon="el-icon-plus" @click="addTestGroup">添加测试组</el-button>
          <el-button v-if="form.testRoutingMode === 'CONDITION'" size="mini" @click="addTestFallback">添加兜底动作</el-button>
        </div>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="formVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="handleSave">保存</el-button>
      </div>
    </el-dialog>

    <el-dialog title="执行分流实验" :visible.sync="testVisible" width="840px" append-to-body>
      <el-form size="small" label-width="100px">
        <el-form-item label="请求唯一键">
          <el-input v-model="testRequest.requestKey" placeholder="可选；为空时使用入参 requestId/orderNo" />
        </el-form-item>
        <el-form-item label="进件时间">
          <el-date-picker v-model="testRequest.requestTime" type="datetime" value-format="yyyy-MM-ddTHH:mm:ss" placeholder="用于测试组名单时点" style="width:100%;" />
        </el-form-item>
        <el-form-item label="入参JSON">
          <el-input v-model="testJson" type="textarea" :rows="8" />
        </el-form-item>
      </el-form>
      <div v-if="testResult" class="result-panel">
        <div class="section-title">执行结果</div>
        <pre>{{ formatJson(testResult) }}</pre>
      </div>
      <div slot="footer">
        <el-button size="small" @click="testVisible = false">关闭</el-button>
        <el-button size="small" type="primary" :loading="testing" @click="doExecute">执行</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listProjects } from '@/api/project'
import { listDefinitions } from '@/api/definition'
import { deleteExperiment, executeExperiment, listExperiments, saveExperiment } from '@/api/experiment'
import varPickerMixin from '@/mixins/varPickerMixin'
import ConditionGroupEditor from '@/components/decision/ConditionGroupEditor.vue'
import {
  createEmptyGroup,
  createEmptyLeaf,
  walkConditionLeaves,
  hasUsableConditionLeaf,
  compileConditionTreeExpression
} from '@/utils/decisionConditionTree'

export default {
  name: 'ExperimentList',
  components: { ConditionGroupEditor },
  mixins: [varPickerMixin],
  data() {
    return {
      loading: false,
      experiments: [],
      total: 0,
      projects: [],
      rulesForProject: [],
      contentLoaded: true,
      query: { pageNum: 1, pageSize: 10, projectId: '', status: '', keyword: '' },
      formVisible: false,
      form: this.emptyForm(),
      rules: {
        projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
        experimentCode: [{ required: true, message: '请输入实验编码', trigger: 'blur' }],
        experimentName: [{ required: true, message: '请输入实验名称', trigger: 'blur' }]
      },
      testVisible: false,
      testing: false,
      testExperiment: null,
      testJson: '{\n  "requestId": "REQ001"\n}',
      testRequest: { requestKey: '', requestTime: '' },
      testResult: null
    }
  },
  computed: {
    productionFormGroups() {
      return this.form.groups.filter(g => g.groupType === 'CHAMPION' || g.groupType === 'CHALLENGER')
    },
    testFormGroups() {
      return this.form.groups.filter(g => g.groupType === 'TEST')
    },
    ratioTotal() {
      return Number(this.productionFormGroups.reduce((sum, g) => sum + Number(g.trafficRatio || 0), 0).toFixed(2))
    },
    testRatioTotal() {
      return Number(this.testFormGroups.reduce((sum, g) => sum + Number(g.trafficRatio || 0), 0).toFixed(2))
    }
  },
  created() {
    this.loadProjects()
    this.loadExperiments()
  },
  methods: {
    emptyForm() {
      return {
        id: null,
        projectId: null,
        projectCode: '',
        experimentCode: '',
        experimentName: '',
        routingMode: 'RATIO',
        testRoutingMode: 'CONDITION',
        conditionRuleCode: '',
        requestKeyPath: 'requestId',
        testExclusive: 1,
        status: 1,
        groups: [this.newGroup('CHAMPION', 'champion', '冠军组', 100)]
      }
    },
    newGroup(type, code, name, ratio) {
      return {
        groupType: type,
        groupCode: code,
        groupName: name,
        ruleCode: '',
        trafficRatio: ratio || 0,
        conditionValue: code,
        conditionExpression: '',
        conditionConfig: this.createConditionRoot(),
        invokeExternalSource: 1,
        status: 1,
        sortOrder: 0
      }
    },
    createConditionRoot() {
      const root = createEmptyGroup('AND')
      root.children.push(createEmptyLeaf())
      return root
    },
    createFallbackConfig() {
      return { fallback: true }
    },
    normalizeGroupForEdit(group) {
      const copy = { ...group }
      copy.conditionConfig = this.parseConditionConfig(copy.conditionConfig)
      if (!copy.conditionConfig && !copy.conditionExpression) {
        copy.conditionConfig = this.createConditionRoot()
      }
      if (copy.invokeExternalSource === null || copy.invokeExternalSource === undefined) {
        copy.invokeExternalSource = 1
      }
      if (copy.trafficRatio === null || copy.trafficRatio === undefined) {
        copy.trafficRatio = 0
      }
      return copy
    },
    parseConditionConfig(config) {
      if (!config) return null
      if (typeof config === 'object') return JSON.parse(JSON.stringify(config))
      try {
        return JSON.parse(config)
      } catch (e) {
        return null
      }
    },
    async loadProjects() {
      const res = await listProjects({ pageNum: 1, pageSize: 500, status: 1 })
      this.projects = (res.data && res.data.records) || []
    },
    async loadRules(projectId) {
      if (!projectId) {
        this.rulesForProject = []
        return
      }
      const res = await listDefinitions({ pageNum: 1, pageSize: 1000, projectId, status: 1 })
      this.rulesForProject = (res.data && res.data.records) || []
    },
    async loadExperiments() {
      this.loading = true
      try {
        const res = await listExperiments(this.cleanParams({ ...this.query }))
        this.experiments = (res.data && res.data.records) || []
        this.total = (res.data && res.data.total) || 0
      } finally {
        this.loading = false
      }
    },
    handleQuery() {
      this.query.pageNum = 1
      this.loadExperiments()
    },
    resetQuery() {
      this.query = { pageNum: 1, pageSize: this.query.pageSize, projectId: '', status: '', keyword: '' }
      this.loadExperiments()
    },
    async handleCreate() {
      this.form = this.emptyForm()
      this.rulesForProject = []
      this.projectRefs = []
      this.projectVars = []
      this.formVisible = true
    },
    async handleEdit(row) {
      this.form = { ...this.emptyForm(), ...JSON.parse(JSON.stringify(row)), groups: (row.groups || []).map(g => this.normalizeGroupForEdit(g)) }
      await this.loadRules(this.form.projectId)
      await this.loadExperimentRefs(this.form.projectId)
      this.formVisible = true
    },
    async handleDelete(row) {
      await this.$confirm('确定删除实验 ' + row.experimentName + '？', '确认', { type: 'warning' })
      await deleteExperiment(row.id)
      this.$message.success('删除成功')
      this.loadExperiments()
    },
    onProjectChange(projectId) {
      const project = this.projects.find(p => p.id === projectId)
      this.form.projectCode = project ? project.projectCode : ''
      this.form.groups.forEach(g => { g.ruleCode = '' })
      this.loadRules(projectId)
      this.loadExperimentRefs(projectId)
    },
    async loadExperimentRefs(projectId) {
      if (!projectId) {
        this._projectId = null
        this.projectRefs = []
        this.projectVars = []
        return
      }
      this._projectId = projectId
      await this.refreshProjectRefs()
    },
    onRoutingModeChange(mode) {
      if (mode === 'RATIO' && this.ratioTotal === 0) {
        this.form.groups.forEach(g => {
          if (g.groupType === 'CHAMPION') g.trafficRatio = 100
        })
      } else if (mode === 'CONDITION') {
        this.ensureFallbackGroup('PRODUCTION')
      }
    },
    onTestRoutingModeChange(mode) {
      if (mode === 'RATIO' && this.testRatioTotal === 0 && this.testFormGroups.length === 1) {
        this.testFormGroups[0].trafficRatio = 100
      } else if (mode === 'CONDITION' && this.testFormGroups.length > 0) {
        this.ensureFallbackGroup('TEST')
      }
    },
    addChallenger() {
      const index = this.productionFormGroups.length
      this.insertBeforeFallback(this.newGroup('CHALLENGER', 'challenger_' + index, '挑战组' + index, 0), 'PRODUCTION')
    },
    addTestGroup() {
      const index = this.testFormGroups.length + 1
      this.insertBeforeFallback(this.newGroup('TEST', 'test_' + index, '测试组' + index, 0), 'TEST')
      if (this.form.testRoutingMode === 'CONDITION') this.ensureFallbackGroup('TEST')
    },
    addProductionFallback() {
      if (this.productionFormGroups.some(g => this.isFallbackGroup(g))) return
      const index = this.productionFormGroups.length
      const group = this.newGroup('CHALLENGER', 'fallback_' + index, '兜底组', 0)
      group.conditionExpression = ''
      group.conditionConfig = this.createFallbackConfig()
      this.form.groups.push(group)
    },
    addTestFallback() {
      if (this.testFormGroups.some(g => this.isFallbackGroup(g))) return
      const index = this.testFormGroups.length + 1
      const group = this.newGroup('TEST', 'test_fallback_' + index, '测试兜底组', 0)
      group.conditionExpression = ''
      group.conditionConfig = this.createFallbackConfig()
      this.form.groups.push(group)
    },
    ensureFallbackGroup(scope) {
      const groups = scope === 'TEST' ? this.testFormGroups : this.productionFormGroups
      if (groups.some(g => this.isFallbackGroup(g))) return
      if (scope === 'TEST') {
        this.addTestFallback()
      } else {
        this.addProductionFallback()
      }
    },
    insertBeforeFallback(group, scope) {
      const targetGroups = scope === 'TEST' ? this.testFormGroups : this.productionFormGroups
      const fallback = targetGroups.find(g => this.isFallbackGroup(g))
      const index = fallback ? this.form.groups.indexOf(fallback) : -1
      if (index >= 0) {
        this.form.groups.splice(index, 0, group)
      } else {
        this.form.groups.push(group)
      }
    },
    removeGroup(row) {
      const index = this.form.groups.indexOf(row)
      if (index >= 0) this.form.groups.splice(index, 1)
    },
    handleSave() {
      return new Promise(resolve => {
        this.$refs.form.validate(async valid => {
          if (!valid) return resolve(false)
          const error = this.validateGroups()
          if (error) {
            this.$message.error(error)
            return resolve(false)
          }
          const data = { ...this.form, groups: this.prepareGroupsForSave() }
          await saveExperiment(data)
          this.$message.success('保存成功')
          this.formVisible = false
          this.loadExperiments()
          resolve(true)
        })
      })
    },
    validateGroups() {
      const champions = this.productionFormGroups.filter(g => g.groupType === 'CHAMPION')
      if (champions.length !== 1) return '必须且只能配置一组冠军组'
      if (this.form.routingMode === 'RATIO' && this.ratioTotal !== 100) return '冠军组和挑战组分流比例之和必须为100%'
      if (this.form.testRoutingMode === 'RATIO' && this.testFormGroups.length > 0 && this.testRatioTotal !== 100) return '测试组分流比例之和必须为100%'
      const missing = this.form.groups.find(g => !g.groupCode || !g.ruleCode)
      if (missing) return '每个实验组都必须配置组编码和执行规则'
      const duplicateCode = this.findDuplicateGroupCode()
      if (duplicateCode) return '实验组编码不能重复: ' + duplicateCode
      if (this.form.routingMode === 'CONDITION') {
        const error = this.validateConditionGroups(this.productionFormGroups, '冠军挑战')
        if (error) return error
      }
      if (this.form.testRoutingMode === 'CONDITION' && this.testFormGroups.length > 0) {
        const error = this.validateConditionGroups(this.testFormGroups, '测试组')
        if (error) return error
      }
      return ''
    },
    validateConditionGroups(groups, label) {
      if (!groups.some(g => this.isFallbackGroup(g))) return label + '条件分流必须配置兜底动作'
      const invalid = groups.find(g => !this.isFallbackGroup(g) && !this.hasCondition(g))
      if (invalid) return label + '条件分流的非兜底规则必须配置条件'
      return ''
    },
    findDuplicateGroupCode() {
      const seen = {}
      for (const group of this.form.groups) {
        const code = group.groupCode
        if (!code) continue
        if (seen[code]) return code
        seen[code] = true
      }
      return ''
    },
    hasCondition(group) {
      if (group.conditionConfig) return hasUsableConditionLeaf(group.conditionConfig)
      return !!group.conditionExpression
    },
    isFallbackGroup(group) {
      return !!(group && group.conditionConfig && group.conditionConfig.fallback === true)
    },
    prepareGroupsForSave() {
      return this.form.groups.map((g, i) => {
        const copy = { ...g, sortOrder: i }
        if (this.isFallbackGroup(copy)) {
          copy.conditionExpression = ''
          copy.conditionConfig = JSON.stringify(this.createFallbackConfig())
          return copy
        }
        if (copy.conditionConfig) {
          if (this.isConditionModeForGroup(copy)) {
            copy.conditionExpression = compileConditionTreeExpression(copy.conditionConfig)
          } else if (!hasUsableConditionLeaf(copy.conditionConfig)) {
            copy.conditionExpression = ''
          }
          copy.conditionConfig = JSON.stringify(copy.conditionConfig)
        }
        return copy
      })
    },
    isConditionModeForGroup(group) {
      if (group.groupType === 'TEST') return this.form.testRoutingMode === 'CONDITION'
      return this.form.routingMode === 'CONDITION'
    },
    collectSelectedVarItems() {
      const result = []
      ;(this.form.groups || []).forEach(group => {
        if (!group.conditionConfig || this.isFallbackGroup(group)) return
        walkConditionLeaves(group.conditionConfig, leaf => {
          result.push(leaf)
          if (leaf.valueKind === 'VAR' && leaf.value) {
            result.push({
              varCode: leaf.value,
              varLabel: leaf.rightVarLabel,
              _varId: leaf._rightVarId,
              _refType: leaf._rightRefType || leaf._refType,
              varType: leaf.rightVarType
            })
          }
        })
      })
      return result
    },
    _syncModelVarRefs() {
      let changed = false
      ;(this.form.groups || []).forEach(group => {
        if (!group.conditionConfig || this.isFallbackGroup(group)) return
        walkConditionLeaves(group.conditionConfig, leaf => {
          if (leaf.varCode && this.syncVarItem(leaf)) changed = true
          if (leaf.valueKind === 'VAR' && leaf.value) {
            const fake = {
              varCode: leaf.value,
              varLabel: leaf.rightVarLabel,
              _varId: leaf._rightVarId,
              _refType: leaf._rightRefType || leaf._refType,
              varType: leaf.rightVarType
            }
            if (this.syncVarItem(fake)) {
              leaf.value = fake.varCode
              leaf.rightVarLabel = fake.varLabel
              leaf._rightVarId = fake._varId
              leaf.rightVarType = fake.varType
              changed = true
            }
          }
        })
      })
      if (changed) this.$forceUpdate()
    },
    handleTest(row) {
      this.testExperiment = row
      this.testResult = null
      this.testRequest = { requestKey: '', requestTime: '' }
      this.testJson = this.defaultTestJson(row)
      this.testVisible = true
    },
    defaultTestJson(row) {
      const code = row && row.experimentCode ? row.experimentCode : 'EXP'
      return JSON.stringify({
        requestId: code + '_REQ_001',
        taxpayerType: '一般纳税人',
        goodsCategory: '货物',
        annualRevenue: 5000,
        taxComplianceScore: 85,
        yearsInBusiness: 10,
        hasViolation: false,
        totalAmount: 113000,
        isExempt: false,
        creditLevel: 'A',
        taxBurdenDeviation: 0.08,
        violationCount: 0,
        serviceType: 'ICT服务',
        paymentMode: '后付费',
        customerType: '企业客户',
        taxpayerQualification: '一般纳税人',
        customerLevel: '金',
        monthlyConsumption: 5000,
        invoiceDeviationRate: 0.05,
        redInvoiceRatio: 0.02,
        zeroRateInvoiceRatio: 0.01,
        crossRegionInvoiceRatio: 0.08,
        billingAmount: 100000,
        basicServiceRatio: 0.6,
        vasServiceRatio: 0.4
      }, null, 2)
    },
    async doExecute() {
      let params
      try {
        params = JSON.parse(this.testJson || '{}')
      } catch (e) {
        this.$message.error('入参JSON格式错误: ' + e.message)
        return
      }
      this.testing = true
      try {
        const res = await executeExperiment(this.testExperiment.experimentCode, {
          params,
          requestKey: this.testRequest.requestKey || '',
          requestTime: this.testRequest.requestTime || null
        })
        this.testResult = res.data
      } finally {
        this.testing = false
      }
    },
    productionGroups(row) {
      return (row.groups || []).filter(g => g.groupType === 'CHAMPION' || g.groupType === 'CHALLENGER')
    },
    testGroups(row) {
      return (row.groups || []).filter(g => g.groupType === 'TEST')
    },
    routeModeLabel(mode) {
      return mode === 'RATIO' ? '比例分流' : '条件分流'
    },
    ruleLabel(rule) {
      return (rule.ruleName || rule.ruleCode) + ' / ' + rule.ruleCode
    },
    cleanParams(params) {
      Object.keys(params).forEach(k => {
        if (params[k] === '' || params[k] === null || params[k] === undefined) delete params[k]
      })
      return params
    },
    formatJson(value) {
      return JSON.stringify(value, null, 2)
    }
  }
}
</script>

<style lang="scss" scoped>
.experiment-page {
  .page-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 14px;
  }

  h2 {
    margin: 0;
    font-size: 20px;
    font-weight: 700;
    color: #1f2937;
  }

  .page-subtitle {
    margin-top: 4px;
    color: #64748b;
    font-size: 13px;
  }

  .page-tip {
    margin-top: 4px;
    color: #64748b;
    font-size: 12px;
  }

  .group-tag {
    margin-right: 4px;
    margin-bottom: 4px;
  }

  .section-title {
    margin: 16px 0 8px;
    font-size: 14px;
    font-weight: 700;
    color: #1f2937;
  }

  .group-table {
    margin-bottom: 8px;
  }

  .table-actions {
    display: flex;
    gap: 8px;
    margin-bottom: 8px;
  }

  .fallback-cell {
    min-height: 34px;
    display: flex;
    align-items: center;
    padding: 0 10px;
    color: #606266;
    background: #f5f7fa;
    border: 1px solid #ebeef5;
    border-radius: 4px;
  }

  ::v-deep .group-table .cg {
    padding: 8px;
    border: 1px solid #ebeef5;
    border-radius: 4px;
    background: #fff;
  }

  .result-panel pre {
    margin: 0;
    padding: 12px;
    max-height: 360px;
    overflow: auto;
    background: #f8fafc;
    border: 1px solid #e5e7eb;
    border-radius: 4px;
    font-size: 12px;
    line-height: 1.5;
  }
}
</style>
