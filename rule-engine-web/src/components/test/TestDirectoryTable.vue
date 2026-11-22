<template>
  <section class="management-list-page management-table-region">
    <div class="uiue-search-container uiue-filter-toolbar">
      <el-form inline size="small" @submit.prevent @keyup.enter="$emit('search')">
        <el-form-item label="作用范围">
          <el-select v-model="filters.scope" clearable placeholder="全部" style="width: 100px">
            <el-option label="全局" value="GLOBAL" /><el-option label="项目级" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="filters.scope !== 'GLOBAL'" label="所属项目">
          <project-resource-select v-model="filters.projectId" style="width: 160px" />
        </el-form-item>
        <slot name="filters" />
        <el-form-item label="关键词">
          <remote-filter-select v-model:value="filters.keyword" allow-free-input :placeholder="placeholder"
            :fetch-options="suggestions" style="width: 210px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="$emit('search')">查询</el-button>
          <el-button @click="$emit('reset')">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="uiue-btn-bar"><div class="btn-right">
        <table-column-settings v-model="order" :columns="columns" :storage-key="storageKey" />
        <el-button size="small" :icon="Refresh" :loading="loading" @click="$emit('refresh')">刷新</el-button>
        <el-button v-if="createLabel" type="primary" size="small" :icon="Plus" @click="$emit('create')">{{ createLabel }}</el-button>
      </div></div>
    </div>
    <el-alert v-if="error" type="error" show-icon :title="error" :closable="false" />
    <el-table class="management-table" :data="rows" :aria-busy="loading" border size="small" show-overflow-tooltip :empty-text="emptyText">
      <el-table-column v-for="column in visibleColumns" :key="column.key" :label="column.label" :prop="column.key" :min-width="column.minWidth">
        <template #default="{ row }">
          <el-tag v-if="column.key === 'scope'" :class="row.scope === 'GLOBAL' ? 'el-tag--scope-global' : 'el-tag--scope-project'" size="small">
            {{ row.scope === 'GLOBAL' ? '全局' : '项目级' }}
          </el-tag>
          <template v-else>{{ column.format ? column.format(row) : row[column.key] || '—' }}</template>
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" align="center" :width="actionWidth"><template #default="{ row }"><slot name="actions" :row="row" /></template></el-table-column>
    </el-table>
    <slot />
  </section>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import ProjectResourceSelect from '@/components/ProjectResourceSelect.vue'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import TableColumnSettings from '@/components/common/TableColumnSettings.vue'

const filters = defineModel('filters', { type: Object, required: true })
const props = defineProps({
  columns: { type: Array, required: true }, rows: { type: Array, required: true },
  suggestions: { type: Function, required: true }, storageKey: { type: String, required: true },
  placeholder: { type: String, required: true }, emptyText: { type: String, required: true },
  actionWidth: { type: Number, required: true }, createLabel: { type: String, default: '' },
  error: { type: String, default: '' }, loading: Boolean
})
defineEmits(['search','reset','refresh','create'])
const order = ref(props.columns.map(column => column.key))
const visibleColumns = computed(() => order.value.map(key => props.columns.find(column => column.key === key)).filter(Boolean))
watch(() => filters.value.scope, scope => { if (scope === 'GLOBAL') filters.value.projectId = null })
</script>
