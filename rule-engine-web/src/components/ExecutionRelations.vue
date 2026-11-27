<template>
  <section aria-label="按执行流水查看调用与费用">
    <p>流水 <code>{{ detail.traceId }}</code> · 尝试 {{ detail.attemptNo ?? '未知' }}</p>
    <details v-if="detail.outputResult != null"><summary>业务输出</summary><pre>{{ detail.outputResult }}</pre></details>
    <el-button v-if="detail.traceInfo" link type="primary" @click="$emit('view-trace')">查看规则命中与子规则追踪</el-button>
    <el-radio-group v-model="section" size="small" aria-label="关联记录类型" @change="changeSection">
      <el-radio-button v-for="item in sections" :key="item.key" :value="item.key">{{ item.label }}</el-radio-button>
    </el-radio-group>
    <async-state :loading="loading" :error="error" @retry="load">
      <p v-for="item in subtotals" :key="item.currency" class="charge-total">已入账合计 {{ item.amount }} {{ item.currency || '币种未记录' }}</p>
      <el-table :data="rows" border :empty-text="section === 'billing' ? '暂未查到可见计费明细，可刷新核对入账状态' : '暂未查到此流水的可见记录'">
        <el-table-column v-for="[key, label] in columns" :key="key" :prop="key" :label="label" min-width="118" show-overflow-tooltip>
          <template #default="{ row }">{{ displayValue(key, row[key]) }}</template>
        </el-table-column>
        <el-table-column label="来源配置" min-width="110">
          <template #default="{ row }"><router-link v-if="location(row)" :to="location(row)">查看配置</router-link><span v-else>—</span></template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="total > 20" :current-page="page" :page-size="20" :total="total" layout="prev, pager, next" @current-change="changePage" />
    </async-state>
    <el-button link @click="load">刷新记录</el-button>
  </section>
</template>
<script>
import AsyncState from '@/components/common/AsyncState.vue'
import { listRuntimeLogs } from '@/api/runtimeLog'
import { listExperimentLogs } from '@/api/experiment'
import { listTraceCharges } from '@/api/billing'
import { hasPermission } from '@/security/permissionState'

const fields = {
  calls: [['moduleType','模块'],['targetName','调用目标'],['actionType','动作'],['traceId','模块流水'],['attemptNo','尝试'],['cacheStatus','缓存'],['success','调用结果'],['costTimeMs','耗时（ms）']],
  experiment: [['experimentCode','实验'],['groupName','分组'],['stage','执行阶段'],['routeReason','命中依据'],['childTraceId','子流水']],
  billing: [['billingName','计费项'],['billingBasis','计费依据'],['attemptNo','尝试'],['quantity','数量'],['unitPrice','单价'],['amount','金额'],['currency','币种'],['occurTime','入账记录时间']],
}
export default {
  name: 'ExecutionRelations', components: { AsyncState }, props: { detail: { type: Object, required: true } }, emits: ['view-trace'],
  data: () => ({ section: 'calls', page: 1, total: 0, subtotals: [], rows: [], loading: false, error: '', generation: 0 }),
  computed: {
    scope() { return this.detail.executionProjectId ? { projectId: this.detail.executionProjectId, scope: 'PROJECT' } : undefined },
    sections() { return [{ key: 'calls', label: '模块调用', permission: 'log:view' }, { key: 'experiment', label: '实验分流', permission: 'experiment:view' }, { key: 'billing', label: '计费明细', permission: 'billing:view' }].filter(item => hasPermission(item.permission, this.scope)) },
    columns() { return fields[this.section] },
  },
  created() { this.load() },
  beforeUnmount() { this.generation++ },
  methods: {
    displayValue(key, value) {
      const labels = { moduleType: { DATASOURCE: '外数', DATABASE: '数据库', MODEL: '模型', LIST: '名单', SFTP: 'SFTP' },
        billingBasis: { DRAFT_TRIAL: '草稿试运行', EXECUTION_SNAPSHOT: '执行时配置', CURRENT_CONFIG: '当前配置', LEGACY_REPLAY: '历史重放' },
        cacheStatus: { HIT: '命中缓存', MISS: '未命中', BYPASS: '直接调用' }, success: { 1: '成功', 0: '失败' } }
      return labels[key]?.[value] ?? value ?? '未记录'
    },
    changeSection() { this.page = 1; this.load() },
    changePage(page) { this.page = page; this.load() },
    location(row) {
      let target
      if (this.section === 'experiment') target = ['/experiment/detail/', row.experimentId, 'experiment:view']
      else if (this.section === 'billing') target = row.billingTarget === 'API'
        ? ['/datasource/api/', row.targetRefId, 'datasource:view'] : ['/rule/', row.ruleId, 'rule:view']
      else target = {
        MODEL: ['/model/', row.targetRefId, 'model:view'], DATABASE: ['/database/', row.datasourceId || row.targetRefId, 'database:view'],
        DATASOURCE: row.actionType === 'AUTH_TEST' ? ['/datasource/source/', row.datasourceId || row.targetRefId, 'datasource:view'] : ['/datasource/api/', row.targetRefId, 'datasource:view'],
        LIST: ['/list/', row.targetRefId, 'list:view'],
      }[row.moduleType]
      if (!target || !target[1] || !hasPermission(target[2], this.scope)) return null
      return { path: target[0] + target[1], query: this.scope ? { projectId: this.scope.projectId } : {} }
    },
    async load() {
      const id = ++this.generation
      this.rows = []; this.total = 0; this.subtotals = []; this.error = ''; this.loading = false
      if (!this.sections.some(item => item.key === this.section)) return
      this.loading = true
      const paging = { pageNum: this.page, pageSize: 20, projectId: this.scope?.projectId }
      const traceId = this.detail.traceId
      try {
        let response
        if (this.section === 'billing') response = await listTraceCharges({ ...paging, rootTraceId: traceId })
        else if (this.section === 'experiment') response = await listExperimentLogs({ ...paging, traceId })
        else response = await listRuntimeLogs({ ...paging, traceId })
        if (id !== this.generation) return
        this.subtotals = response.data.subtotals || []
        this.rows = response.data.records
        this.total = Number(response.data.total)
      } catch (error) { if (id === this.generation) this.error = error.message || '关联记录加载失败' }
      finally { if (id === this.generation) this.loading = false }
    },
  },
}
</script>
