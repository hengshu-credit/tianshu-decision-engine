<template>
  <div class="uiue-list-page experiment-detail-page">
    <div class="detail-header">
      <div>
        <div class="detail-title">{{ form.id ? '分流实验详情' : '新建分流实验' }}</div>
        <div class="detail-meta">{{ form.experimentName || '未命名实验' }} / {{ form.experimentCode || '待填写编码' }}</div>
      </div>
      <div class="detail-actions">
        <el-button size="small" @click="$router.push('/experiment')">返回</el-button>
        <el-button v-if="form.id" size="small" @click="openVersionDialog">版本</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </div>
    </div>

    <div class="usage-guide">
      <div v-for="item in experimentGuideCards" :key="item.title" class="guide-item">
        <div class="guide-title">{{ item.title }}</div>
        <div class="guide-text">{{ item.text }}</div>
      </div>
    </div>

    <el-form ref="form" :model="form" :rules="rules" label-width="96px" size="small" class="base-form">
      <el-row :gutter="12">
        <el-col :span="6">
          <el-form-item label="项目" prop="projectId">
            <el-select v-model="form.projectId" filterable placeholder="选择项目" style="width:100%;" @change="onProjectChange">
              <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="实验编码" prop="experimentCode">
            <el-input v-model="form.experimentCode" placeholder="EXP_RISK_CHAMPION" />
          </el-form-item>
        </el-col>
        <el-col :span="6">
          <el-form-item label="实验名称" prop="experimentName">
            <el-input v-model="form.experimentName" placeholder="冠军挑战实验" />
          </el-form-item>
        </el-col>
        <el-col :span="3">
          <el-form-item label="请求键">
            <el-input v-model="form.requestKeyPath" placeholder="requestId" />
          </el-form-item>
        </el-col>
        <el-col :span="3">
          <el-form-item label="状态">
            <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="说明">
        <el-input v-model="form.description" type="textarea" :rows="2" placeholder="说明实验目的、冠军策略和挑战策略差异" />
      </el-form-item>
    </el-form>

    <el-tabs v-model="activeTab" class="detail-tabs">
      <el-tab-pane label="冠军挑战" name="champion">
        <div class="tab-toolbar">
          <div>
            <div class="tab-title">冠军组和挑战组配置</div>
            <div class="tab-subtitle">必须且只能有一个冠军组；挑战组可多个。随机分流和条件分流独立配置。</div>
          </div>
          <el-radio-group v-model="form.routingMode" size="small" @change="onRoutingModeChange">
            <el-radio-button label="RATIO">随机分流</el-radio-button>
            <el-radio-button label="CONDITION">条件分流</el-radio-button>
          </el-radio-group>
        </div>

        <el-alert v-if="form.routingMode === 'RATIO' && ratioTotal !== 100" type="warning" :closable="false" show-icon :title="'当前随机分流比例合计 ' + ratioTotal + '%，必须等于 100%'" />
        <div v-if="form.routingMode === 'RATIO'" class="ratio-config-panel">
          <div class="ratio-config-head">
            <div class="ratio-config-title">随机分流比例</div>
            <el-tag size="mini" :type="ratioTotal === 100 ? 'success' : 'warning'">合计 {{ ratioTotal }}%</el-tag>
          </div>
          <div class="ratio-config-grid">
            <div v-for="row in productionFormGroups" :key="'ratio-' + row._uid" class="ratio-config-item">
              <div class="ratio-group-meta">
                <el-tag size="mini" :type="row.groupType === 'CHAMPION' ? 'success' : 'info'">{{ groupTypeLabel(row.groupType) }}</el-tag>
                <span class="ratio-group-name">{{ row.groupName || row.groupCode }}</span>
                <span class="ratio-group-code">{{ row.groupCode }}</span>
              </div>
              <el-input-number v-model="row.trafficRatio" :min="0" :max="100" :precision="2" size="mini" class="ratio-input" />
            </div>
          </div>
        </div>
        <div v-if="form.routingMode === 'RATIO'" class="ratio-list">
          <div v-for="row in productionFormGroups" :key="row._uid" class="action-card">
            <group-action-form
              :row="row"
              :rules-for-project="rulesForProject"
              :show-type="true"
              :show-ratio="false"
              :show-invoke="false"
              @remove="removeGroup(row)"
            />
          </div>
        </div>
        <div v-else class="condition-route-list">
          <div v-for="row in productionFormGroups" :key="row._uid" class="route-row">
            <div class="route-condition">
              <div class="route-panel-title">{{ isFallbackGroup(row) ? '兜底条件' : '命中条件' }}</div>
              <div v-if="isFallbackGroup(row)" class="fallback-cell">以上条件均未命中时执行右侧动作</div>
              <condition-group-editor
                v-else-if="row.conditionConfig"
                :group="row.conditionConfig"
                :vars="varPickerOptions"
                :get-var-options-fn="getVarOptions"
                :selected-vars="selectedVarPickerOptions"
              />
              <monaco-editor v-else v-model="row.conditionExpression" language="ql" theme="qlexpress-dark" height="70px" />
            </div>
            <div class="route-action">
              <div class="route-panel-title">命中动作</div>
              <group-action-form
                :row="row"
                :rules-for-project="rulesForProject"
                :show-type="true"
                :show-ratio="false"
                :show-invoke="false"
                @remove="removeGroup(row)"
              />
            </div>
          </div>
        </div>
        <div class="table-actions">
          <el-button size="mini" icon="el-icon-plus" @click="addChallenger">添加挑战组</el-button>
          <el-button v-if="form.routingMode === 'CONDITION'" size="mini" @click="addProductionFallback">添加兜底动作</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane label="空跑测试" name="test">
        <div class="tab-toolbar">
          <div>
            <div class="tab-title">测试组配置</div>
            <div class="tab-subtitle">测试组在生产组执行后空跑，可独立选择随机分流或条件分流。</div>
          </div>
          <div class="test-mode">
            <el-radio-group v-model="form.testRoutingMode" size="small" @change="onTestRoutingModeChange">
              <el-radio-button label="RATIO">随机分流</el-radio-button>
              <el-radio-button label="CONDITION">条件分流</el-radio-button>
            </el-radio-group>
            <el-switch
              v-if="form.testRoutingMode === 'CONDITION'"
              v-model="form.testExclusive"
              :active-value="1"
              :inactive-value="0"
              active-text="互斥"
              inactive-text="非互斥"
            />
          </div>
        </div>

        <el-alert v-if="form.testRoutingMode === 'RATIO' && testFormGroups.length > 0 && testRatioTotal !== 100" type="warning" :closable="false" show-icon :title="'当前测试组随机比例合计 ' + testRatioTotal + '%，必须等于 100%'" />
        <div v-if="form.testRoutingMode === 'RATIO' && testFormGroups.length > 0" class="ratio-config-panel">
          <div class="ratio-config-head">
            <div class="ratio-config-title">测试组随机比例</div>
            <el-tag size="mini" :type="testRatioTotal === 100 ? 'success' : 'warning'">合计 {{ testRatioTotal }}%</el-tag>
          </div>
          <div class="ratio-config-grid">
            <div v-for="row in testFormGroups" :key="'test-ratio-' + row._uid" class="ratio-config-item">
              <div class="ratio-group-meta">
                <el-tag size="mini" type="info">{{ groupTypeLabel(row.groupType) }}</el-tag>
                <span class="ratio-group-name">{{ row.groupName || row.groupCode }}</span>
                <span class="ratio-group-code">{{ row.groupCode }}</span>
              </div>
              <el-input-number v-model="row.trafficRatio" :min="0" :max="100" :precision="2" size="mini" class="ratio-input" />
            </div>
          </div>
        </div>
        <div v-if="form.testRoutingMode === 'RATIO'" class="ratio-list">
          <div v-for="row in testFormGroups" :key="row._uid" class="action-card">
            <group-action-form
              :row="row"
              :rules-for-project="rulesForProject"
              :show-type="false"
              :show-ratio="false"
              :show-invoke="true"
              @remove="removeGroup(row)"
            />
          </div>
        </div>
        <div v-else class="condition-route-list">
          <div v-for="row in testFormGroups" :key="row._uid" class="route-row">
            <div class="route-condition">
              <div class="route-panel-title">{{ isFallbackGroup(row) ? '兜底条件' : '命中条件' }}</div>
              <div v-if="isFallbackGroup(row)" class="fallback-cell">测试组条件未命中时执行右侧动作</div>
              <condition-group-editor
                v-else-if="row.conditionConfig"
                :group="row.conditionConfig"
                :vars="varPickerOptions"
                :get-var-options-fn="getVarOptions"
                :selected-vars="selectedVarPickerOptions"
              />
              <monaco-editor v-else v-model="row.conditionExpression" language="ql" theme="qlexpress-dark" height="70px" />
            </div>
            <div class="route-action">
              <div class="route-panel-title">空跑动作</div>
              <group-action-form
                :row="row"
                :rules-for-project="rulesForProject"
                :show-type="false"
                :show-ratio="false"
                :show-invoke="true"
                @remove="removeGroup(row)"
              />
            </div>
          </div>
        </div>
        <div class="table-actions">
          <el-button size="mini" icon="el-icon-plus" @click="addTestGroup">添加测试组</el-button>
          <el-button v-if="form.testRoutingMode === 'CONDITION'" size="mini" @click="addTestFallback">添加兜底动作</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane label="输入字段" name="inputFields">
        <div class="field-tab-summary">共 {{ experimentInputFields.length }} 个输入字段，包含条件分流字段和实验组执行规则入参。</div>
        <el-table :data="experimentInputFields" border size="small" style="width:100%;">
          <el-table-column prop="source" label="来源" width="110" />
          <el-table-column prop="groupName" label="实验组" min-width="120" show-overflow-tooltip />
          <el-table-column prop="ruleCode" label="规则编码" min-width="140" show-overflow-tooltip />
          <el-table-column prop="fieldName" label="字段编码" min-width="160" show-overflow-tooltip />
          <el-table-column prop="fieldLabel" label="字段名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="fieldType" label="类型" width="100" align="center" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="输出字段" name="outputFields">
        <div class="field-tab-summary">共 {{ experimentOutputFields.length }} 个输出字段，来自实验组执行规则出参。</div>
        <el-table :data="experimentOutputFields" border size="small" style="width:100%;">
          <el-table-column prop="source" label="来源" width="110" />
          <el-table-column prop="groupName" label="实验组" min-width="120" show-overflow-tooltip />
          <el-table-column prop="ruleCode" label="规则编码" min-width="140" show-overflow-tooltip />
          <el-table-column prop="fieldName" label="字段编码" min-width="160" show-overflow-tooltip />
          <el-table-column prop="fieldLabel" label="字段名称" min-width="160" show-overflow-tooltip />
          <el-table-column prop="fieldType" label="类型" width="100" align="center" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="分流日志" name="logs">
        <div class="log-filter">
          <el-form :inline="true" size="small">
            <el-form-item label="阶段">
              <el-select v-model="logQuery.stage" clearable placeholder="全部" style="width:120px;">
                <el-option label="生产分流" value="PRODUCTION" />
                <el-option label="空跑测试" value="TEST" />
              </el-select>
            </el-form-item>
            <el-form-item label="请求键">
              <el-input v-model="logQuery.requestKey" clearable placeholder="请求唯一键" style="width:160px;" />
            </el-form-item>
            <el-form-item label="组编码">
              <el-input v-model="logQuery.groupCode" clearable placeholder="组编码" style="width:140px;" />
            </el-form-item>
            <el-form-item label="结果">
              <el-select v-model="logQuery.success" clearable placeholder="全部" style="width:100px;">
                <el-option label="成功" :value="1" />
                <el-option label="失败" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleLogQuery">查询</el-button>
              <el-button @click="resetLogQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </div>
        <el-table :data="logs" border size="small" v-loading="logLoading" style="width:100%;">
          <el-table-column prop="requestKey" label="请求键" min-width="150" show-overflow-tooltip />
          <el-table-column label="阶段" width="90" align="center">
            <template slot-scope="{ row }">{{ row.stage === 'TEST' ? '空跑测试' : '生产分流' }}</template>
          </el-table-column>
          <el-table-column prop="groupCode" label="组编码" min-width="120" show-overflow-tooltip />
          <el-table-column prop="groupName" label="组名称" min-width="120" show-overflow-tooltip />
          <el-table-column label="组类型" width="90">
            <template slot-scope="{ row }">{{ groupTypeLabel(row.groupType) }}</template>
          </el-table-column>
          <el-table-column prop="ruleCode" label="执行规则" min-width="150" show-overflow-tooltip />
          <el-table-column prop="routeReason" label="分流原因" min-width="220" show-overflow-tooltip />
          <el-table-column label="结果" width="70" align="center">
            <template slot-scope="{ row }"><el-tag :type="row.success === 1 ? 'success' : 'danger'" size="mini">{{ row.success === 1 ? '成功' : '失败' }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="executeTimeMs" label="耗时(ms)" width="90" align="center" />
          <el-table-column prop="createTime" label="时间" width="160">
            <template slot-scope="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template slot-scope="{ row }"><el-button type="text" size="mini" @click="openLogDetail(row)">详情</el-button></template>
          </el-table-column>
        </el-table>
        <el-pagination
          class="log-pager"
          :current-page="logQuery.pageNum"
          :page-size="logQuery.pageSize"
          :total="logTotal"
          layout="total,sizes,prev,pager,next"
          :page-sizes="[10,30,50,100]"
          @current-change="p => { logQuery.pageNum = p; loadLogs() }"
          @size-change="s => { logQuery.pageSize = s; logQuery.pageNum = 1; loadLogs() }"
        />
      </el-tab-pane>
    </el-tabs>

    <el-drawer title="分流日志详情" :visible.sync="logDetailVisible" size="70%">
      <div v-if="logDetail" class="log-detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="实验">{{ logDetail.experimentCode }}</el-descriptions-item>
          <el-descriptions-item label="请求键">{{ logDetail.requestKey || '-' }}</el-descriptions-item>
          <el-descriptions-item label="阶段">{{ logDetail.stage === 'TEST' ? '空跑测试' : '生产分流' }}</el-descriptions-item>
          <el-descriptions-item label="分流组">{{ logDetail.groupName || logDetail.groupCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="执行规则">{{ logDetail.ruleCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结果">{{ logDetail.success === 1 ? '成功' : '失败' }}</el-descriptions-item>
          <el-descriptions-item label="分流原因" :span="2">{{ logDetail.routeReason || '-' }}</el-descriptions-item>
          <el-descriptions-item label="错误信息" :span="2">{{ logDetail.errorMessage || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-block">
          <div class="detail-block-title">执行入参</div>
          <pre class="log-pre">{{ pretty(logDetail.inputParams) }}</pre>
        </div>
        <div class="detail-block">
          <div class="detail-block-title">输出结果</div>
          <pre class="log-pre">{{ pretty(logDetail.outputResult) }}</pre>
        </div>
        <div class="detail-block">
          <div class="detail-block-title">执行轨迹</div>
          <pre class="log-pre">{{ pretty(logDetail.traceInfo) }}</pre>
        </div>
      </div>
    </el-drawer>

    <el-dialog title="分流实验版本管理" :visible.sync="versionVisible" width="900px" append-to-body>
      <el-table :data="versionList" border size="mini" style="width:100%;">
        <el-table-column prop="version" label="版本" width="80" align="center" />
        <el-table-column prop="changeLog" label="变更说明" min-width="140" show-overflow-tooltip />
        <el-table-column prop="publishTime" label="时间" width="170" />
        <el-table-column label="操作" width="220" align="center">
          <template slot-scope="{ row, $index }">
            <el-button type="text" size="mini" @click="viewVersion(row)">查看</el-button>
            <el-button v-if="$index < versionList.length - 1" type="text" size="mini" @click="compareWithNext(row, $index)">对比</el-button>
            <el-button type="text" size="mini" @click="rollbackExperimentVersion(row)">回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="versionCompare" class="version-compare">
        <div class="version-compare-title">版本对比：v{{ versionCompare.left.version }} / v{{ versionCompare.right.version }}</div>
        <el-tag size="mini" :type="versionCompare.experimentChanged || versionCompare.groupsChanged ? 'warning' : 'success'">
          {{ versionCompare.experimentChanged || versionCompare.groupsChanged ? '实验配置有变化' : '实验配置无变化' }}
        </el-tag>
      </div>
      <pre v-if="versionPreview" class="version-preview">{{ versionPreview }}</pre>
    </el-dialog>
  </div>
</template>

<script>
import { listProjects } from '@/api/project'
import { listDefinitions, listInputFields, listOutputFields } from '@/api/definition'
import { getExperiment, listExperimentLogs, saveExperiment, listVersions, compareVersions, rollbackVersion } from '@/api/experiment'
import varPickerMixin from '@/mixins/varPickerMixin'
import ConditionGroupEditor from '@/components/decision/ConditionGroupEditor.vue'
import MonacoEditor from '@/components/MonacoEditor'
import GroupActionForm from './GroupActionForm.vue'
import {
  createEmptyGroup,
  createEmptyLeaf,
  walkConditionLeaves,
  hasUsableConditionLeaf,
  compileConditionTreeExpression
} from '@/utils/decisionConditionTree'

export default {
  name: 'ExperimentDetail',
  components: { ConditionGroupEditor, MonacoEditor, GroupActionForm },
  mixins: [varPickerMixin],
  data() {
    return {
      experimentGuideCards: [
        { title: '冠军组', text: '当前生产主策略，必须且只能有一个；随机分流时冠军组和挑战组比例合计必须为 100%。' },
        { title: '挑战组', text: '用于承接新策略流量，可配置多个；建议用描述记录和冠军组的规则差异、观察指标。' },
        { title: '空跑测试', text: 'TEST 组只记录试算结果，不影响生产结果；适合验证新规则输出、耗时和异常情况。' },
        { title: '版本回滚', text: '保存会形成版本；上线前可对比配置差异，异常时通过版本回滚恢复历史配置。' }
      ],
      activeTab: 'champion',
      saving: false,
      contentLoaded: true,
      projects: [],
      rulesForProject: [],
      ruleFieldMap: {},
      form: this.emptyForm(),
      rules: {
        projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
        experimentCode: [{ required: true, message: '请输入实验编码', trigger: 'blur' }],
        experimentName: [{ required: true, message: '请输入实验名称', trigger: 'blur' }]
      },
      logLoading: false,
      logs: [],
      logTotal: 0,
      logQuery: { pageNum: 1, pageSize: 10, requestKey: '', stage: '', groupCode: '', success: '' },
      logDetailVisible: false,
      logDetail: null,
      versionVisible: false,
      versionList: [],
      versionCompare: null,
      versionPreview: '',
      nextUid: 1
    }
  },
  computed: {
    experimentId() {
      return this.$route.params.experimentId
    },
    isCreateMode() {
      return !this.experimentId || this.experimentId === 'new'
    },
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
    },
    experimentInputFields() {
      const rows = []
      const seen = {}
      const push = field => {
        const key = [field.source, field.groupName, field.ruleCode, field.fieldName, field.fieldLabel, field.fieldType].join('|')
        if (seen[key]) return
        seen[key] = true
        rows.push(field)
      }
      ;(this.form.groups || []).forEach(group => {
        if (group.conditionConfig && !this.isFallbackGroup(group)) {
          walkConditionLeaves(group.conditionConfig, leaf => {
            if (!leaf.varCode) return
            push(this.normalizeExperimentField({
              source: '分流条件',
              groupName: group.groupName || group.groupCode,
              ruleCode: '',
              fieldName: leaf.varCode,
              fieldLabel: leaf.varLabel,
              fieldType: leaf.varType
            }))
            if (leaf.valueKind === 'VAR' && leaf.value) {
              push(this.normalizeExperimentField({
                source: '分流条件',
                groupName: group.groupName || group.groupCode,
                ruleCode: '',
                fieldName: leaf.value,
                fieldLabel: leaf.rightVarLabel,
                fieldType: leaf.rightVarType
              }))
            }
          })
        }
        this.ruleFieldsForGroup(group, 'input').forEach(push)
      })
      return rows
    },
    experimentOutputFields() {
      const rows = []
      const seen = {}
      ;(this.form.groups || []).forEach(group => {
        this.ruleFieldsForGroup(group, 'output').forEach(field => {
          const key = [field.groupName, field.ruleCode, field.fieldName, field.fieldLabel, field.fieldType].join('|')
          if (seen[key]) return
          seen[key] = true
          rows.push(field)
        })
      })
      return rows
    }
  },
  async created() {
    await this.loadProjects()
    if (this.isCreateMode) {
      this.form = this.emptyForm()
      if (this.projects.length > 0) {
        this.form.projectId = this.projects[0].id
        this.form.projectCode = this.projects[0].projectCode || ''
        await this.loadRules(this.form.projectId)
        await this.loadExperimentRefs(this.form.projectId)
      }
      return
    }
    await this.loadDetail()
    await this.loadLogs()
  },
  methods: {
    emptyForm() {
      return {
        id: null,
        projectId: null,
        projectCode: '',
        experimentCode: '',
        experimentName: '',
        description: '',
        routingMode: 'RATIO',
        testRoutingMode: 'CONDITION',
        conditionRuleCode: '',
        requestKeyPath: 'requestId',
        testExclusive: 1,
        status: 1,
        groups: [this.withUid(this.newGroup('CHAMPION', 'champion', '冠军组', 100))]
      }
    },
    withUid(group) {
      if (!group._uid) this.$set(group, '_uid', 'g_' + this.nextUid++)
      return group
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
      const copy = this.withUid({ ...group })
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
    async loadDetail() {
      const res = await getExperiment(this.experimentId)
      const data = res.data || {}
      this.form = { ...this.emptyForm(), ...JSON.parse(JSON.stringify(data)), groups: (data.groups || []).map(g => this.normalizeGroupForEdit(g)) }
      await this.loadRules(this.form.projectId)
      await this.loadExperimentRefs(this.form.projectId)
    },
    async loadRules(projectId) {
      if (!projectId) {
        this.rulesForProject = []
        this.ruleFieldMap = {}
        return
      }
      const res = await listDefinitions({ pageNum: 1, pageSize: 1000, projectId, status: 1 })
      this.rulesForProject = (res.data && res.data.records) || []
      await this.loadRuleFieldMap()
    },
    async loadRuleFieldMap() {
      const map = {}
      await Promise.all((this.rulesForProject || []).map(async rule => {
        if (!rule.id) return
        const [inputRes, outputRes] = await Promise.all([
          listInputFields(rule.id).catch(() => []),
          listOutputFields(rule.id).catch(() => [])
        ])
        map[rule.ruleCode] = {
          input: this.normalizeFieldListResponse(inputRes),
          output: this.normalizeFieldListResponse(outputRes)
        }
      }))
      this.ruleFieldMap = map
    },
    normalizeFieldListResponse(res) {
      const data = res && res.data ? res.data : res
      return Array.isArray(data) ? data : []
    },
    ruleFieldsForGroup(group, direction) {
      if (!group || !group.ruleCode) return []
      const entry = this.ruleFieldMap[group.ruleCode] || {}
      return ((entry && entry[direction]) || []).map(field => this.normalizeExperimentField({
        source: direction === 'input' ? '执行规则' : '执行规则',
        groupName: group.groupName || group.groupCode,
        ruleCode: group.ruleCode,
        fieldName: field.scriptName || field.fieldName || field.varCode,
        fieldLabel: field.fieldLabel || field.varLabel || field.fieldName,
        fieldType: field.fieldType || field.varType
      }))
    },
    normalizeExperimentField(field) {
      return {
        source: field.source || '-',
        groupName: field.groupName || '-',
        ruleCode: field.ruleCode || '-',
        fieldName: field.fieldName || '-',
        fieldLabel: field.fieldLabel || '-',
        fieldType: field.fieldType || '-'
      }
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
        this.projectIdForRefs = null
        this.projectRefs = []
        this.projectVars = []
        return
      }
      this.projectIdForRefs = projectId
      await this.refreshProjectRefs()
    },
    onRoutingModeChange(mode) {
      if (mode === 'RATIO') {
        this.removeFallbackGroups('PRODUCTION')
      }
      if (mode === 'RATIO' && this.ratioTotal === 0) {
        this.form.groups.forEach(g => {
          if (g.groupType === 'CHAMPION') g.trafficRatio = 100
        })
      } else if (mode === 'CONDITION') {
        this.ensureFallbackGroup('PRODUCTION')
      }
    },
    onTestRoutingModeChange(mode) {
      if (mode === 'RATIO') {
        this.removeFallbackGroups('TEST')
      }
      if (mode === 'RATIO' && this.testRatioTotal === 0 && this.testFormGroups.length === 1) {
        this.testFormGroups[0].trafficRatio = 100
      } else if (mode === 'CONDITION' && this.testFormGroups.length > 0) {
        this.ensureFallbackGroup('TEST')
      }
    },
    addChallenger() {
      const index = this.productionFormGroups.filter(g => !this.isFallbackGroup(g)).length
      this.insertBeforeFallback(this.withUid(this.newGroup('CHALLENGER', 'challenger_' + index, '挑战组' + index, 0)), 'PRODUCTION')
    },
    addTestGroup() {
      const index = this.testFormGroups.filter(g => !this.isFallbackGroup(g)).length + 1
      this.insertBeforeFallback(this.withUid(this.newGroup('TEST', 'test_' + index, '测试组' + index, 0)), 'TEST')
      if (this.form.testRoutingMode === 'CONDITION') this.ensureFallbackGroup('TEST')
    },
    addProductionFallback() {
      if (this.productionFormGroups.some(g => this.isFallbackGroup(g))) return
      const index = this.productionFormGroups.length
      const group = this.withUid(this.newGroup('CHALLENGER', 'fallback_' + index, '兜底组', 0))
      group.conditionExpression = ''
      group.conditionConfig = this.createFallbackConfig()
      this.form.groups.push(group)
    },
    addTestFallback() {
      if (this.testFormGroups.some(g => this.isFallbackGroup(g))) return
      const index = this.testFormGroups.length + 1
      const group = this.withUid(this.newGroup('TEST', 'test_fallback_' + index, '测试兜底组', 0))
      group.conditionExpression = ''
      group.conditionConfig = this.createFallbackConfig()
      this.form.groups.push(group)
    },
    ensureFallbackGroup(scope) {
      const groups = scope === 'TEST' ? this.testFormGroups : this.productionFormGroups
      if (groups.some(g => this.isFallbackGroup(g))) return
      if (scope === 'TEST') this.addTestFallback()
      else this.addProductionFallback()
    },
    insertBeforeFallback(group, scope) {
      const targetGroups = scope === 'TEST' ? this.testFormGroups : this.productionFormGroups
      const fallback = targetGroups.find(g => this.isFallbackGroup(g))
      const index = fallback ? this.form.groups.indexOf(fallback) : -1
      if (index >= 0) this.form.groups.splice(index, 0, group)
      else this.form.groups.push(group)
    },
    removeGroup(row) {
      const index = this.form.groups.indexOf(row)
      if (index >= 0) this.form.groups.splice(index, 1)
    },
    removeFallbackGroups(scope) {
      const isTarget = group => {
        if (!this.isFallbackGroup(group)) return false
        if (scope === 'TEST') return group.groupType === 'TEST'
        return group.groupType === 'CHAMPION' || group.groupType === 'CHALLENGER'
      }
      this.form.groups = this.form.groups.filter(group => !isTarget(group))
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
          this.saving = true
          try {
            const data = { ...this.form, groups: this.prepareGroupsForSave() }
            const res = await saveExperiment(data)
            this.$message.success('保存成功')
            if (!this.form.id && res.data && res.data.id) {
              this.form.id = res.data.id
              this.$router.replace('/experiment/detail/' + res.data.id)
            }
            resolve(true)
          } finally {
            this.saving = false
          }
        })
      })
    },
    validateGroups() {
      const champions = this.productionFormGroups.filter(g => g.groupType === 'CHAMPION')
      if (champions.length !== 1) return '必须且只能配置一组冠军组'
      if (this.form.routingMode === 'RATIO' && this.ratioTotal !== 100) return '冠军组和挑战组随机分流比例之和必须为100%'
      if (this.form.testRoutingMode === 'RATIO' && this.testFormGroups.length > 0 && this.testRatioTotal !== 100) return '测试组随机分流比例之和必须为100%'
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
        delete copy._uid
        if (this.isFallbackGroup(copy)) {
          copy.conditionExpression = ''
          copy.conditionConfig = this.isConditionModeForGroup(copy) ? JSON.stringify(this.createFallbackConfig()) : ''
          return copy
        }
        if (this.isConditionModeForGroup(copy)) {
          if (copy.conditionConfig) {
            copy.conditionExpression = compileConditionTreeExpression(copy.conditionConfig)
            copy.conditionConfig = JSON.stringify(copy.conditionConfig)
          }
        } else {
          copy.conditionExpression = ''
          copy.conditionConfig = ''
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
    async openVersionDialog() {
      if (!this.form.id) return
      this.versionVisible = true
      this.versionCompare = null
      this.versionPreview = ''
      const res = await listVersions(this.form.id)
      this.versionList = (res && res.data) || []
    },
    viewVersion(row) {
      this.versionCompare = null
      this.versionPreview = this.prettyVersionJson({
        experiment: this.parseVersionJson(row.experimentJson, null),
        groups: this.parseVersionJson(row.groupsJson, [])
      })
    },
    async compareWithNext(row, index) {
      const right = this.versionList[index + 1]
      if (!this.form.id || !right) return
      const res = await compareVersions(this.form.id, row.version, right.version)
      this.versionCompare = (res && res.data) || null
      this.versionPreview = ''
    },
    async rollbackExperimentVersion(row) {
      if (!this.form.id || !row) return
      await this.$confirm('确认回滚到 v' + row.version + '？', '提示', { type: 'warning' })
      await rollbackVersion(this.form.id, row.version)
      this.$message.success('已回滚')
      await this.loadDetail()
      await this.openVersionDialog()
    },
    prettyVersionJson(value) {
      if (!value) return ''
      try {
        return JSON.stringify(value, null, 2)
      } catch (e) {
        return String(value)
      }
    },
    parseVersionJson(text, fallback) {
      if (!text) return fallback
      try {
        return JSON.parse(text)
      } catch (e) {
        return fallback
      }
    },
    async loadLogs() {
      if (!this.form.id) {
        this.logs = []
        this.logTotal = 0
        return
      }
      this.logLoading = true
      try {
        const params = this.cleanParams({ ...this.logQuery, experimentId: this.form.id })
        const res = await listExperimentLogs(params)
        this.logs = (res.data && res.data.records) || []
        this.logTotal = (res.data && res.data.total) || 0
      } finally {
        this.logLoading = false
      }
    },
    handleLogQuery() {
      this.logQuery.pageNum = 1
      this.loadLogs()
    },
    resetLogQuery() {
      this.logQuery = { pageNum: 1, pageSize: this.logQuery.pageSize, requestKey: '', stage: '', groupCode: '', success: '' }
      this.loadLogs()
    },
    openLogDetail(row) {
      this.logDetail = row
      this.logDetailVisible = true
    },
    groupTypeLabel(type) {
      return { CHAMPION: '冠军组', CHALLENGER: '挑战组', TEST: '测试组' }[type] || type || '-'
    },
    formatTime(time) {
      if (!time) return '-'
      const d = new Date(time)
      if (Number.isNaN(d.getTime())) return time
      const pad = n => String(n).padStart(2, '0')
      return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds())
    },
    pretty(value) {
      if (value == null || value === '') return '-'
      try {
        return JSON.stringify(JSON.parse(value), null, 2)
      } catch (e) {
        return String(value)
      }
    },
    cleanParams(params) {
      Object.keys(params).forEach(k => {
        if (params[k] === '' || params[k] === null || params[k] === undefined) delete params[k]
      })
      return params
    }
  }
}
</script>

<style lang="scss" scoped>
.experiment-detail-page {
  .detail-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 14px;
  }

  .detail-title {
    font-size: 20px;
    font-weight: 700;
    color: #1f2937;
  }

  .detail-meta {
    color: #64748b;
    font-size: 13px;
    margin-top: 4px;
  }

  .detail-actions,
  .table-actions,
  .test-mode {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .usage-guide {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 10px;
    margin-bottom: 12px;
  }

  .guide-item {
    border: 1px solid #E2E8F0;
    border-radius: 4px;
    padding: 10px 12px;
    background: #FFFFFF;
  }

  .guide-title {
    color: #0F172A;
    font-weight: 700;
    margin-bottom: 6px;
  }

  .guide-text {
    color: #64748B;
    font-size: 12px;
    line-height: 1.6;
  }

  .base-form,
  .detail-tabs {
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 4px;
    padding: 14px;
    margin-bottom: 12px;
  }

  .tab-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 12px;
  }

  .tab-title {
    color: #1f2937;
    font-weight: 700;
  }

  .tab-subtitle {
    margin-top: 3px;
    color: #64748b;
    font-size: 12px;
  }

  .ratio-config-panel {
    margin-top: 12px;
    border: 1px solid #dbeafe;
    border-radius: 4px;
    background: #f8fbff;
    padding: 12px;
  }

  .ratio-config-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    margin-bottom: 10px;
  }

  .ratio-config-title {
    color: #1f2937;
    font-size: 13px;
    font-weight: 700;
  }

  .ratio-config-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
    gap: 10px;
  }

  .ratio-config-item {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 122px;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border: 1px solid #e5e7eb;
    border-radius: 4px;
    background: #fff;
  }

  @media (max-width: 1200px) {
    .usage-guide {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }

  .ratio-group-meta {
    display: flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
  }

  .ratio-group-name {
    color: #334155;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .ratio-group-code {
    color: #94a3b8;
    font-size: 12px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .ratio-input {
    width: 122px;
  }

  .ratio-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
    margin-top: 12px;
  }

  .action-card,
  .route-condition,
  .route-action {
    border: 1px solid #e5e7eb;
    border-radius: 4px;
    padding: 12px;
    background: #fff;
  }

  .condition-route-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
    margin-top: 12px;
  }

  .route-row {
    display: grid;
    grid-template-columns: minmax(0, 1.4fr) minmax(320px, 0.8fr);
    gap: 12px;
    align-items: start;
  }

  .route-panel-title,
  .field-label,
  .detail-block-title {
    color: #334155;
    font-weight: 700;
    font-size: 12px;
    margin-bottom: 6px;
  }

  .fallback-cell {
    min-height: 40px;
    display: flex;
    align-items: center;
    padding: 0 10px;
    color: #606266;
    background: #f8fafc;
    border: 1px solid #e5e7eb;
    border-radius: 4px;
  }

  .table-actions {
    margin-top: 12px;
  }

  ::v-deep .group-action-form .action-row {
    margin-top: 8px;
  }

  ::v-deep .group-action-form .action-footer {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 8px;
    margin-top: 10px;
  }

  ::v-deep .group-action-form .btn-delete {
    color: #f56c6c;
    margin-left: auto;
  }

  ::v-deep .route-condition .cg {
    padding: 8px;
    border: 1px solid #ebeef5;
    border-radius: 4px;
    background: #fff;
  }

  .log-filter {
    margin-bottom: 10px;
  }

  .field-tab-summary {
    color: #64748b;
    font-size: 12px;
    margin-bottom: 10px;
  }

  .log-pager {
    margin-top: 12px;
    text-align: right;
  }

  .log-detail {
    padding: 16px;
  }

  .detail-block {
    margin-top: 12px;
  }

  .log-pre {
    margin: 0;
    padding: 10px;
    max-height: 260px;
    overflow: auto;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 4px;
    font-size: 12px;
    line-height: 1.5;
    font-family: Menlo, Monaco, Consolas, monospace;
  }

  .version-compare {
    margin-top: 10px;
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .version-compare-title {
    font-weight: 600;
    color: #303133;
  }

  .version-preview {
    margin-top: 10px;
    padding: 10px;
    max-height: 320px;
    overflow: auto;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 4px;
    font-size: 12px;
    line-height: 1.5;
    font-family: Menlo, Monaco, Consolas, monospace;
  }
}

@media (max-width: 1100px) {
  .experiment-detail-page {
    .route-row {
      grid-template-columns: 1fr;
    }
  }
}
</style>
