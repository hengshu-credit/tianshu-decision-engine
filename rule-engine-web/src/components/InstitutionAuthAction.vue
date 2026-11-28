<template>
  <el-button v-if="canView" size="small" @click="visible = true">机构鉴权</el-button>
  <project-auth-dialog v-if="visible && canView" v-model:visible="visible" institution />
</template>

<script setup>
import { computed, onDeactivated, ref } from 'vue'
import { hasPermission } from '@/security/permissionState'
import ProjectAuthDialog from '@/views/project/ProjectAuthDialog.vue'

const canView = computed(() => hasPermission({ anyOf: ['institution-auth:view', 'institution-auth:manage'], scope: 'GLOBAL' }))
const visible = ref(false)
onDeactivated(() => { visible.value = false })
</script>
