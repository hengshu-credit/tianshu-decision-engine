<template>
  <section class="api-sample-structure" aria-label="响应结构预览">
    <div class="structure-toolbar"><span>共 {{ paths.length }} 个结构路径，字段名称和类型按样例保留</span><el-input v-model="query" clearable placeholder="搜索响应字段路径" @input="page = 1" /></div>
    <el-table :data="visibleRows" size="small" border max-height="360" row-key="value">
      <el-table-column prop="value" label="字段路径" min-width="320" show-overflow-tooltip />
      <el-table-column prop="type" label="样例类型" width="120" />
    </el-table>
    <el-pagination v-if="filtered.length > 50" v-model:current-page="page" :page-size="50" :total="filtered.length" layout="total, prev, pager, next" />
  </section>
</template>
<script>
import { samplePathOptions } from '@/utils/apiExecution'
export default {
  props: { sample: { default: null }, prefix: { type: String, default: 'response.body' } },
  data: () => ({ query: '', page: 1 }),
  computed: {
    paths() { return samplePathOptions(this.sample, this.prefix) },
    filtered() { return this.paths.filter(item => item.value.toLowerCase().includes(this.query.trim().toLowerCase())) },
    visibleRows() { return this.filtered.slice((this.page - 1) * 50, this.page * 50) },
  },
  watch: { sample() { this.page = 1 } },
}
</script>
<style scoped>
.api-sample-structure { display: grid; gap: 12px; margin-top: 12px; }
.structure-toolbar { display: flex; align-items: center; gap: 12px; color: var(--el-text-color-secondary); font-size: 12px; }
.structure-toolbar .el-input { width: 280px; margin-left: auto; }
@media (max-width: 720px) { .structure-toolbar { flex-wrap: wrap; } .structure-toolbar .el-input { width: 100%; } }
</style>
