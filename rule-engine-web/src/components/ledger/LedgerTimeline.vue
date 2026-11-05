<template>
  <section class="ledger-timeline">
    <el-alert v-if="ledgerTypesError" :title="ledgerTypesError" type="error" :closable="false"><el-button link @click="loadLedgerTypes">重试</el-button></el-alert>
    <div class="uiue-search-container uiue-filter-toolbar">
      <el-form inline size="small" @keyup.enter="search">
        <el-form-item label="范围"><el-select v-model="filters.scope" clearable placeholder="全部" style="width:105px" @change="scopeChanged"><el-option label="全局" value="GLOBAL" /><el-option label="项目级" value="PROJECT" /></el-select></el-form-item>
        <el-form-item v-if="filters.scope === 'PROJECT'" label="项目"><project-resource-select v-model="filters.projectId" style="width:170px" @change="search" /></el-form-item>
        <el-form-item label="事件"><el-select v-model="filters.eventIds" multiple collapse-tags collapse-tags-tooltip clearable filterable remote :remote-method="searchEvents" placeholder="全部（可选）" aria-label="事件筛选" style="width:210px" @change="search"><el-option v-for="event in events" :key="event.id" :value="event.id" :label="event.title" /></el-select></el-form-item>
        <el-form-item label="事件类型"><el-select v-model="filters.category" clearable placeholder="全部" title="筛选文章自身或可见关联事件的类型" style="width:135px" @change="search"><el-option v-for="item in ledgerTypes" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="日期范围"><el-date-picker v-model="range" type="datetimerange" format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DDTHH:mm:ss" start-placeholder="开始时间" end-placeholder="结束时间" range-separator="至" :shortcuts="shortcuts" unlink-panels style="width:340px" popper-class="ledger-date-popper" @change="search" @visible-change="calendarOpened" @panel-change="calendarChanged">
          <template #default="cell"><el-tooltip :disabled="!marker(cell).titles.length" placement="top" popper-class="ledger-calendar-tooltip"><template #content><strong>{{ cell.dayjs.format('YYYY-MM-DD') }}</strong><div v-for="title in marker(cell).titles" :key="title" class="ledger-calendar-title">{{ title }}</div></template><span class="ledger-calendar-cell"><span>{{ cell.text }}</span><span class="ledger-calendar-marks"><span v-if="marker(cell).created">▪</span><span v-if="marker(cell).modified">◆</span></span></span></el-tooltip></template>
        </el-date-picker></el-form-item>
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="标题或正文" style="width:155px" /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
      <div class="uiue-btn-bar"><div class="btn-right"><el-button v-if="canWrite" size="small" @click="newEvent">新建事件</el-button><el-button size="small" @click="load">刷新</el-button><el-button v-if="canWrite" size="small" type="primary" @click="writeArticle">新增记录</el-button></div></div>
    </div>
    <div v-if="selectedEvents.length" class="ledger-event-summary"><div><strong>{{ selectedEvents.map(item => item.title).join(' / ') }}</strong><span>按活动时间排列，同一版本只出现一次</span></div><div v-if="selectedEvents.length === 1" class="uiue-action-group"><el-tag>{{ selectedEvents[0].statusLabel || statusLabel(selectedEvents[0].status) }}</el-tag><el-button v-if="selectedEvents[0].canEdit" link @click="editEvent(selectedEvents[0])">编辑事件</el-button></div></div>
    <div :aria-busy="loading" class="ledger-axis">
      <div v-for="item in records" :key="item.id" class="ledger-axis-row"><span class="ledger-axis-node" aria-hidden="true" /><el-popover :disabled="filters.eventIds.length > 0" :width="420" :trigger="['hover', 'focus']" placement="bottom-start" popper-class="ledger-card-popover"><template #reference><button type="button" class="ledger-entry" :class="{ 'is-detailed': filters.eventIds.length }" :aria-label="`打开文档：${item.title} v${item.versionNo}`" @click="read(item)"><span class="ledger-entry-meta"><time>{{ time(item.activityTime) }}</time><span class="ledger-scope">{{ scopeLabel(item) }}</span><span class="ledger-entry-events">{{ item.events.map(event => event.title).join(' / ') || '独立记录' }}</span><span class="ledger-category">{{ categoryLabel(item.category) }}</span><span class="ledger-version">v{{ item.versionNo }}</span></span><span class="ledger-entry-title">{{ item.title }}</span><template v-if="filters.eventIds.length"><span class="ledger-entry-summary">{{ item.summary }}</span><span class="ledger-entry-foot"><span>{{ item.actorName }} · {{ item.versionNo === 1 ? '首次发布' : '变更发布' }}</span><span>{{ item.changeSummary || '打开文档 →' }}</span></span></template></button></template><div class="ledger-popover-meta">{{ time(item.activityTime) }} · {{ scopeLabel(item) }} · v{{ item.versionNo }}</div><h3>{{ item.title }}</h3><p>{{ item.summary }}</p><div class="ledger-entry-foot"><span>{{ item.actorName }}</span><span>{{ item.changeSummary }}</span></div></el-popover></div>
      <el-empty v-if="!loading && !records.length" description="暂无符合条件的发布记录" />
    </div>
    <el-pagination :current-page="page" :page-size="size" :total="total" layout="total, prev, pager, next" @current-change="changePage" />
    <ledger-event-dialog v-model="eventDialog" :event="editingEvent" :types="ledgerTypes" :scope="filters.scope" :project-id="filters.projectId" @saved="eventSaved" />
  </section>
</template>
<script>
import dayjs from 'dayjs'
import ledgerTypesMixin from '@/mixins/ledgerTypesMixin'
import ProjectResourceSelect from '@/components/ProjectResourceSelect.vue'
import LedgerEventDialog from './LedgerEventDialog.vue'
import { listLedgerTimeline, listLedgerEvents, getLedgerCalendar, getLedgerEvent } from '@/api/ledger'
import { LEDGER_EVENT_STATUSES, ledgerDateShortcuts, ledgerTime } from '@/utils/ledger'
import { hasPermission } from '@/security/permissionState'
export default {
  mixins: [ledgerTypesMixin],
  name: 'LedgerTimeline', components: { ProjectResourceSelect, LedgerEventDialog },
  props: { projectId: { type: [String, Number], default: null } },
  data() { return { filters: { scope: this.projectId ? 'PROJECT' : '', projectId: this.projectId, eventIds: String(this.$route.query.eventIds || '').split(',').filter(value => /^[1-9]\d*$/.test(value)), category: '', keyword: '' }, range: [], events: [], records: [], page: 1, size: 20, total: 0, loading: false, hasLoaded: false, generation: 0, eventGeneration: 0, calendarGeneration: 0, months: {}, calendarPending: new Set(), eventDialog: false, editingEvent: null, shortcuts: ledgerDateShortcuts() } },
  computed: { canWrite() { return hasPermission('ledger:edit') }, selectedEvents() { return this.events.filter(event => this.filters.eventIds.some(id => String(id) === String(event.id))) } },
  watch: { projectId(value) { this.filters.scope = value ? 'PROJECT' : ''; this.filters.projectId = value; this.search() }, '$route.query.eventIds'(value) { if (this.$route.path === '/ledger' && value != null) { this.filters.eventIds = String(value).split(',').filter(id => /^[1-9]\d*$/.test(id)); this.searchEvents(''); this.search() } } },
  mounted() { this.searchEvents(''); this.load() },
  activated() { if (this.hasLoaded) this.load() },
  beforeUnmount() { this.generation++; this.eventGeneration++; this.calendarGeneration++ },
  methods: {
    time: ledgerTime,
    statusLabel(value) { return LEDGER_EVENT_STATUSES.find(item => item.value === value)?.label || value },
    scopeLabel(item) { return item.scope === 'GLOBAL' ? '全局' : item.projectName || `项目 #${item.projectId}` },
    query() { return { ...this.filters, projectId: this.filters.scope === 'PROJECT' ? this.filters.projectId : undefined, start: this.range?.[0], end: this.range?.[1] } },
    async load() { const generation = ++this.generation; this.loading = true; try { const response = await listLedgerTimeline({ ...this.query(), pageNum: this.page, pageSize: this.size }); if (generation === this.generation) { this.records = response.data?.records || []; this.total = response.data?.total || 0; this.hasLoaded = true } } catch { /* request 已提示。 */ } finally { if (generation === this.generation) this.loading = false } },
    search() { this.page = 1; this.months = {}; this.calendarPending = new Set(); this.calendarGeneration++; this.load() },
    reset() { this.filters = { scope: this.projectId ? 'PROJECT' : '', projectId: this.projectId, eventIds: [], category: '', keyword: '' }; this.range = []; this.search() },
    scopeChanged() { if (this.filters.scope !== 'PROJECT') this.filters.projectId = null; this.search() },
    changePage(page) { this.page = page; this.load() },
    async searchEvents(keyword) { const generation = ++this.eventGeneration; try { const response = await listLedgerEvents({ keyword }); if (generation !== this.eventGeneration) return; const keep = this.events.filter(item => this.filters.eventIds.some(id => String(id) === String(item.id))); const values = [...keep, ...(response.data || [])]; const missing = this.filters.eventIds.filter(id => !values.some(item => String(item.id) === String(id))); const selected = await Promise.all(missing.map(id => getLedgerEvent(id))); if (generation !== this.eventGeneration) return; this.events = [...new Map([...values, ...selected.map(item => item.data)].map(item => [String(item.id), item])).values()]; this.filters.eventIds = this.filters.eventIds.map(id => this.events.find(item => String(item.id) === String(id))?.id || id) } catch { /* request 已提示。 */ } },
    calendarOpened(open) { if (open) { const date = dayjs(this.range?.[0] || undefined); this.calendarChanged([date.subtract(1, 'month'), date, date.add(1, 'month')]) } },
    calendarChanged(dates) { for (const date of Array.isArray(dates) ? dates : [dates]) { if (date) this.loadMonth(dayjs(date).format('YYYY-MM')) } },
    async loadMonth(month) { if (this.months[month] || this.calendarPending.has(month)) return; const generation = this.calendarGeneration; this.calendarPending.add(month); try { const response = await getLedgerCalendar({ ...this.query(), month }); if (generation === this.calendarGeneration) this.months = { ...this.months, [month]: Object.fromEntries((response.data || []).map(item => [item.date, item])) } } catch { /* 日期筛选仍可使用。 */ } finally { if (generation === this.calendarGeneration) this.calendarPending.delete(month) } },
    marker(cell) { const date = cell.dayjs.format('YYYY-MM-DD'); return this.months[date.slice(0, 7)]?.[date] || { titles: [], created: 0, modified: 0 } },
    read(item) { this.$router.push({ path: `/ledger/articles/${item.articleId}`, query: { revisionId: item.id } }) },
    writeArticle() { this.$router.push({ path: '/ledger/articles/new', query: { projectId: this.filters.projectId || undefined, eventIds: this.filters.eventIds.join(',') || undefined } }) },
    newEvent() { this.editingEvent = null; this.eventDialog = true },
    editEvent(event) { this.editingEvent = event; this.eventDialog = true },
    eventSaved(event) { this.events = [...this.events.filter(item => String(item.id) !== String(event.id)), event]; this.search() },
  },
}
</script>
