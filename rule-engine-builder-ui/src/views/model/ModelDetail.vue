<template>
  <div class="uiue-list-page">
    <!-- 页面头部 -->
    <div style="margin-bottom:16px;display:flex;align-items:center;justify-content:space-between;">
      <h2 style="margin:0;">{{ model.modelName || '模型详情' }}</h2>
      <el-button size="small" icon="el-icon-back" @click="$router.push('/model')">返回</el-button>
    </div>

    <!-- 基本信息 -->
    <el-descriptions :column="2" border size="small" style="margin-bottom:16px;" v-loading="loading">
      <el-descriptions-item label="模型编码">{{ model.modelCode }}</el-descriptions-item>
      <el-descriptions-item label="模型名称">{{ model.modelName }}</el-descriptions-item>
      <el-descriptions-item label="模型大类">{{ modelTypeLabel(model.modelType) }}</el-descriptions-item>
      <el-descriptions-item label="模型格式">{{ model.modelFormat }}</el-descriptions-item>
      <el-descriptions-item label="作用范围">{{ model.scope === 'GLOBAL' ? '全局' : '项目级' }}</el-descriptions-item>
      <el-descriptions-item label="所属项目">{{ model.projectName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="文件名">{{ model.modelFileName }}</el-descriptions-item>
      <el-descriptions-item label="文件大小">{{ formatFileSize(model.modelFileSize) }}</el-descriptions-item>
      <el-descriptions-item label="设计版本">{{ model.currentVersion }}</el-descriptions-item>
      <el-descriptions-item label="发布版本">{{ model.publishedVersion || '-' }}</el-descriptions-item>
    </el-descriptions>

    <!-- 描述 -->
    <el-card v-if="model.description" shadow="never" style="margin-bottom:16px;">
      <div slot="header" style="font-weight:600;">描述</div>
      <div style="color:#606266;font-size:14px;line-height:1.6;">{{ model.description }}</div>
    </el-card>

    <!-- 输入输出字段 -->
    <el-tabs type="border-card">
      <el-tab-pane label="输入字段">
        <el-table :data="model.inputFields" border size="small" max-height="400" v-loading="loading">
          <el-table-column prop="fieldName" label="字段名称" min-width="120" />
          <el-table-column prop="fieldLabel" label="中文名称" min-width="100" />
          <el-table-column prop="scriptName" label="脚本名称" min-width="120" />
          <el-table-column prop="fieldType" label="类型" width="80" align="center" />
          <el-table-column prop="dataType" label="数据用途" width="100" align="center" />
          <el-table-column prop="defaultValue" label="默认值" width="100" />
          <el-table-column prop="transformType" label="预处理" width="90" align="center" />
        </el-table>
        <div v-if="!model.inputFields || model.inputFields.length === 0" style="text-align:center;padding:40px 0;color:#909399;">暂无输入字段</div>
      </el-tab-pane>
      <el-tab-pane label="输出字段">
        <el-table :data="model.outputFields" border size="small" max-height="400" v-loading="loading">
          <el-table-column prop="fieldName" label="字段名称" min-width="120" />
          <el-table-column prop="fieldLabel" label="中文名称" min-width="100" />
          <el-table-column prop="fieldType" label="类型" width="90" align="center" />
          <el-table-column prop="isProbability" label="概率输出" width="80" align="center">
            <template slot-scope="{row}">{{ row.isProbability === 1 ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column prop="category" label="类别" width="100" />
        </el-table>
        <div v-if="!model.outputFields || model.outputFields.length === 0" style="text-align:center;padding:40px 0;color:#909399;">暂无输出字段</div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script>
import * as api from '@/api/model'

const MODEL_TYPE_LABELS = {
  CLASSIFICATION: '分类',
  REGRESSION: '回归',
  CLUSTERING: '聚类',
  ML: '机器学习'
}

export default {
  name: 'ModelDetail',
  data() {
    return {
      loading: false,
      model: {}
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      const id = this.$route.params.id
      if (!id) return
      this.loading = true
      try {
        const res = await api.getModel(id)
        this.model = res.data || {}
      } catch (e) {
        this.$message.error(e.message || '加载模型详情失败')
      } finally {
        this.loading = false
      }
    },
    modelTypeLabel(t) {
      return MODEL_TYPE_LABELS[t] || t || '—'
    },
    formatFileSize(size) {
      if (!size) return '-'
      if (size < 1024) return size + ' B'
      if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
      return (size / 1024 / 1024).toFixed(2) + ' MB'
    }
  }
}
</script>