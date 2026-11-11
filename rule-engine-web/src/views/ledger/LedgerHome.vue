<template>
  <div class="uiue-list-page management-list-page ledger-page">
    <el-tabs :model-value="tab" :before-leave="confirmManagementLeave" @update:model-value="changeTab">
      <el-tab-pane label="时间线" name="timeline"><ledger-timeline v-if="tab === 'timeline'" :key="`timeline:${identityGeneration}`" :project-id="projectId" /></el-tab-pane>
      <el-tab-pane label="文章管理" name="articles"><ledger-articles v-if="tab === 'articles'" :key="`articles:${identityGeneration}`" :project-id="projectId" /></el-tab-pane>
      <el-tab-pane label="素材库" name="assets"><ledger-assets v-if="tab === 'assets'" :key="`assets:${identityGeneration}`" :project-id="projectId" /></el-tab-pane>
      <el-tab-pane label="事件与类型" name="management"><ledger-management v-if="tab === 'management'" ref="management" :key="`management:${identityGeneration}`" :project-id="projectId" /></el-tab-pane>
    </el-tabs>
  </div>
</template>
<script>
import LedgerTimeline from '@/components/ledger/LedgerTimeline.vue'
import LedgerArticles from '@/components/ledger/LedgerArticles.vue'
import LedgerAssets from '@/components/ledger/LedgerAssets.vue'
import LedgerManagement from '@/components/ledger/LedgerManagement.vue'
import { routeProjectId } from '@/utils/projectContext'
import '@/styles/ledger.scss'
const tabFromRoute = value => ['articles', 'assets', 'management'].includes(value) ? value : 'timeline'
export default {
  name: 'LedgerHome',
  components: { LedgerTimeline, LedgerArticles, LedgerAssets, LedgerManagement },
  data() { return { identityGeneration: 0, active: true, tab: tabFromRoute(this.$route.query.tab) } },
  computed: { projectId() { return routeProjectId(this.$route, this.$store.state.currentProject) } },
  watch: { '$route.query.tab'(value) { if (this.$route.path === '/ledger' && this.active) this.tab = tabFromRoute(value) } },
  activated() { this.active = true; this.tab = tabFromRoute(this.$route.query.tab) },
  deactivated() { this.active = false },
  mounted() { window.addEventListener('tianshu:identity-change', this.clearIdentity) },
  beforeUnmount() { window.removeEventListener('tianshu:identity-change', this.clearIdentity) },
  beforeRouteLeave() { return this.confirmManagementLeave() },
  beforeRouteUpdate(to, from) { return to.fullPath === from.fullPath ? true : this.confirmManagementLeave() },
  methods: {
    confirmManagementLeave() { const editor = this.$refs.management; if (editor?.configurationLeaveApproved) { editor.configurationLeaveApproved = false; return true } return editor?.confirmConfigurationLeave() ?? true },
    clearIdentity() { this.identityGeneration++ },
    changeTab(tab) {
      if (tab !== tabFromRoute(this.$route.query.tab)) return this.$router.replace({ path: '/ledger', query: { ...this.$route.query, tab } })
    },
  },
}
</script>
