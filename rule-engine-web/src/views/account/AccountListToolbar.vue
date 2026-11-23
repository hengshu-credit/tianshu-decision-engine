<template>
  <div class="uiue-search-container uiue-filter-toolbar">
    <el-form :inline="true" size="small" @submit.prevent="search">
      <el-form-item label="关键词">
        <el-input v-model="keyword" clearable :placeholder="keywordPlaceholder" class="keyword-input" @clear="search" />
      </el-form-item>
      <el-form-item v-if="roles" label="角色">
        <el-select v-model="roleId" clearable filterable placeholder="全部角色" class="role-select" @change="search">
          <el-option v-for="role in roles" :key="role.id" :value="role.id" :label="role.roleName" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="status" clearable placeholder="全部状态" class="status-select" @change="search">
          <el-option label="已启用" :value="1" />
          <el-option label="已停用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" native-type="submit">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>
    <div class="uiue-btn-bar"><div class="btn-right"><slot /></div></div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  keywordPlaceholder: { type: String, required: true },
  roles: { type: Array, default: null },
})
const emit = defineEmits(['query'])
const keyword = ref('')
const status = ref(null)
const roleId = ref(null)

function search() {
  emit('query', { keyword: keyword.value.trim().toLowerCase(), status: status.value, roleId: roleId.value })
}
function reset() {
  keyword.value = ''
  status.value = null
  roleId.value = null
  search()
}
</script>

<style scoped>
.keyword-input { width: 210px; }
.role-select { width: 150px; }
.status-select { width: 104px; }
</style>
