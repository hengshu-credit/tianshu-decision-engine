<template>
  <div class="uiue-list-page management-list-page ledger-page">
    <el-tabs v-model="tab" :before-leave="confirmManagementLeave" @tab-change="changeTab">
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
export default {
  name: 'LedgerHome',
  components: { LedgerTimeline, LedgerArticles, LedgerAssets, LedgerManagement },
  data() { return { identityGeneration: 0, tab: ['timeline', 'articles', 'assets', 'management'].includes(this.$route.query.tab) ? this.$route.query.tab : 'timeline' } },
  computed: { projectId() { return routeProjectId(this.$route, this.$store.state.currentProject) } },
  watch: { '$route.query.tab'(value) { if (['timeline', 'articles', 'assets', 'management'].includes(value)) this.tab = value } },
  mounted() { window.addEventListener('tianshu:identity-change', this.clearIdentity) },
  beforeUnmount() { window.removeEventListener('tianshu:identity-change', this.clearIdentity) },
  beforeRouteLeave() { return this.confirmManagementLeave() },
  beforeRouteUpdate(to, from) { return to.fullPath === from.fullPath ? true : this.confirmManagementLeave() },
  methods: {
    confirmManagementLeave() { const editor = this.$refs.management; if (editor?.configurationLeaveApproved) { editor.configurationLeaveApproved = false; return true } return editor?.confirmConfigurationLeave() ?? true },
    clearIdentity() { this.identityGeneration++ }, changeTab() { this.$router.replace({ path: '/ledger', query: { ...this.$route.query, tab: this.tab } }) },
  },
}
</script>
