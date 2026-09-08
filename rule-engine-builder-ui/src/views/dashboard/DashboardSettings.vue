<template>
  <div class="uiue-list-page dashboard-settings-page">
    <div class="module-hint dashboard-settings-heading">
      <div>
        <div class="hint-title">数据看板设置</div>
        <div class="hint-text">字段关联始终保存资源 ID；项目覆盖未配置的字段会逐项继承个人默认，再回落到系统默认。</div>
      </div>
      <el-button @click="$router.push('/dashboard')">返回看板</el-button>
    </div>

    <div class="uiue-search-container uiue-filter-toolbar">
      <el-form :inline="true" size="small">
        <el-form-item label="配置范围">
          <el-select
            v-model="projectId"
            clearable
            filterable
            placeholder="个人默认（仅全局字段）"
            style="width: 280px"
            @change="load"
          >
            <el-option
              v-for="project in projects"
              :key="project.id"
              :label="projectLabel(project)"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-tag :type="projectId ? 'success' : 'info'">
            {{ projectId ? '项目覆盖' : '个人默认' }}
          </el-tag>
          <el-tag v-if="projectId && settings.projectOverride" type="warning">已创建覆盖</el-tag>
        </el-form-item>
      </el-form>
    </div>

    <el-alert
      v-if="!settings.editable && !loading"
      title="当前控制台未启用用户登录，仅可查看系统默认字段映射。"
      type="info"
      :closable="false"
      show-icon
    />

    <el-alert
      title="经纬度仅在统计时接受有限数字，且经度须在 [-180, 180]、纬度须在 [-90, 90]；执行日志中的原始值不会被修改或删除。"
      type="warning"
      :closable="false"
      show-icon
    />

    <el-card shadow="never" class="dashboard-map-view-settings">
      <template #header>
        <div>
          <strong>地图初始视角</strong>
          <span>仅在当前浏览器会话中保存；未配置时默认展示中国。</span>
        </div>
      </template>
      <el-form :inline="true" size="small">
        <el-form-item label="中心经度">
          <el-input-number
            v-model="mapView.longitude"
            :min="-180"
            :max="180"
            :precision="4"
            :step="1"
          />
        </el-form-item>
        <el-form-item label="中心纬度">
          <el-input-number
            v-model="mapView.latitude"
            :min="-90"
            :max="90"
            :precision="4"
            :step="1"
          />
        </el-form-item>
        <el-form-item label="缩放比例">
          <el-input-number
            v-model="mapView.zoom"
            :min="0.5"
            :max="10"
            :precision="1"
            :step="0.1"
          />
        </el-form-item>
        <el-form-item class="dashboard-map-view-actions">
          <el-button @click="restoreChinaMapView">恢复中国默认值</el-button>
          <el-button type="primary" @click="saveMapView">保存地图视角</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table :data="rows" border size="small" v-loading="loading">
      <el-table-column prop="label" label="指标字段" min-width="130" />
      <el-table-column label="引用字段" min-width="300">
        <template #default="{ row }">
          <el-select
            v-model="row.referenceKey"
            filterable
            :disabled="!settings.editable"
            placeholder="选择变量或对象字段"
            style="width: 100%"
          >
            <el-option
              v-for="option in optionsFor(row)"
              :key="optionKey(option)"
              :label="option.label"
              :value="optionKey(option)"
            >
              <span>{{ option.label }}</span>
              <small class="dashboard-option-meta">{{ option.valueType }} · {{ scopeLabel(option.scope) }}</small>
            </el-option>
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="JSON 来源" width="140">
        <template #default="{ row }">
          <el-select v-model="row.jsonSource" :disabled="!settings.editable">
            <el-option label="输入参数" value="INPUT" />
            <el-option label="输出结果" value="OUTPUT" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="当前来源" width="130" align="center">
        <template #default="{ row }">
          <el-tag :type="sourceTag(row.source)" size="small">{{ sourceLabel(row.source) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="130" align="center">
        <template #default="{ row }">
          <el-tooltip v-if="!row.valid" :content="row.errorMessage" placement="top">
            <el-tag type="danger" size="small">映射失效</el-tag>
          </el-tooltip>
          <el-tag v-else type="success" size="small">可用</el-tag>
        </template>
      </el-table-column>
      <el-table-column fixed="right" class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" width="100" align="center">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :disabled="!settings.editable || !canRestore(row)"
            @click="restore(row)"
          >恢复继承</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-card v-if="decisionRow" shadow="never" class="dashboard-decision-values">
      <template #header>
        <strong>决策结果取值</strong>
        <span>不区分大小写，使用英文逗号分隔；三组取值不可重叠。</span>
      </template>
      <el-form label-width="90px" size="small">
        <el-form-item label="通过">
          <el-input v-model="decisionRow.passValues" :disabled="!settings.editable" placeholder="0,100,PASS" />
        </el-form-item>
        <el-form-item label="人审">
          <el-input v-model="decisionRow.reviewValues" :disabled="!settings.editable" placeholder="2,102,REVIEW" />
        </el-form-item>
        <el-form-item label="拒绝">
          <el-input v-model="decisionRow.rejectValues" :disabled="!settings.editable" placeholder="1,101,REJECT" />
        </el-form-item>
      </el-form>
    </el-card>

    <div class="dashboard-settings-actions">
      <el-button
        v-if="projectId"
        :disabled="!settings.editable || !settings.projectOverride"
        @click="restoreAll"
      >恢复全部继承</el-button>
      <el-button :disabled="!settings.editable" @click="load">取消修改</el-button>
      <el-button type="primary" :loading="saving" :disabled="!settings.editable" @click="save">保存设置</el-button>
    </div>
  </div>
</template>

<script>
import {
  deleteDashboardSetting,
  getDashboardSettings,
  saveDashboardSettings
} from '@/api/dashboard'
import { listProjects } from '@/api/project'
import {
  defaultDashboardMapView,
  readDashboardMapView,
  writeDashboardMapView
} from '@/utils/dashboardFilters'

const NUMERIC_FIELDS = new Set(['LONGITUDE', 'LATITUDE', 'PERIOD', 'AMOUNT'])

export default {
  name: 'DashboardSettings',
  data() {
    return {
      projectId: null,
      projects: [],
      settings: { editable: false, projectOverride: false, options: [], fields: [] },
      rows: [],
      mapView: readDashboardMapView(window.sessionStorage),
      loading: false,
      saving: false
    }
  },
  computed: {
    decisionRow() {
      return this.rows.find(row => row.metricField === 'DECISION_RESULT')
    }
  },
  mounted() {
    this.loadProjects()
    this.load()
  },
  methods: {
    persistMapView(successMessage) {
      const result = writeDashboardMapView(window.sessionStorage, this.mapView)
      if (!result.valid) {
        this.$message.warning(result.message)
        return false
      }
      this.mapView = result.value
      this.$message.success(successMessage)
      return true
    },
    saveMapView() {
      return this.persistMapView('地图初始视角已保存')
    },
    restoreChinaMapView() {
      this.mapView = defaultDashboardMapView()
      return this.persistMapView('已恢复中国默认视角')
    },
    async loadProjects() {
      try {
        const response = await listProjects({ pageNum: 1, pageSize: 1000, status: 1 })
        this.projects = (response.data && response.data.records) || []
      } catch (error) {
        this.projects = []
      }
    },
    async load() {
      this.loading = true
      try {
        const response = await getDashboardSettings({ projectId: this.projectId })
        this.applySettings(response.data || {})
      } catch (error) {
        this.$message.error(error.message || '字段映射加载失败')
      } finally {
        this.loading = false
      }
    },
    applySettings(settings) {
      this.settings = {
        editable: false,
        projectOverride: false,
        options: [],
        fields: [],
        ...settings
      }
      this.rows = this.settings.fields.map(field => {
        const mapping = field.mapping || {}
        const values = mapping.decisionValues || {}
        return {
          metricField: field.metricField,
          label: field.label,
          referenceKey: mapping.refType && mapping.refId
            ? `${mapping.refType}:${mapping.refId}` : '',
          jsonSource: mapping.jsonSource || 'INPUT',
          source: mapping.mappingSource,
          valid: mapping.valid !== false,
          errorMessage: mapping.errorMessage || '',
          passValues: this.joinValues(values.pass),
          reviewValues: this.joinValues(values.review),
          rejectValues: this.joinValues(values.reject)
        }
      })
    },
    async save() {
      if (!this.settings.editable) return
      const mappings = {}
      for (const row of this.rows) {
        const reference = this.parseReference(row.referenceKey)
        if (!reference) {
          this.$message.warning(`请选择${row.label}的引用字段`)
          return
        }
        mappings[row.metricField] = {
          ...reference,
          jsonSource: row.jsonSource,
          decisionValues: row.metricField === 'DECISION_RESULT'
            ? {
                pass: this.splitValues(row.passValues),
                review: this.splitValues(row.reviewValues),
                reject: this.splitValues(row.rejectValues)
              }
            : null
        }
      }
      this.saving = true
      try {
        const response = await saveDashboardSettings(
          { projectId: this.projectId }, { mappings }
        )
        this.applySettings(response.data || {})
        this.$message.success('数据看板字段映射已保存')
      } catch (error) {
        this.$message.error(error.message || '保存失败')
      } finally {
        this.saving = false
      }
    },
    async restore(row) {
      if (!this.settings.editable) return
      try {
        const response = await deleteDashboardSetting(
          row.metricField, { projectId: this.projectId }
        )
        this.applySettings(response.data || {})
        this.$message.success(`${row.label}已恢复继承`)
      } catch (error) {
        this.$message.error(error.message || '恢复失败')
      }
    },
    async restoreAll() {
      if (!this.settings.editable || !this.projectId) return
      this.saving = true
      try {
        let response = null
        for (const row of this.rows) {
          response = await deleteDashboardSetting(
            row.metricField, { projectId: this.projectId }
          )
        }
        if (response) this.applySettings(response.data || {})
        this.$message.success('项目字段映射已全部恢复继承')
      } catch (error) {
        this.$message.error(error.message || '恢复失败')
      } finally {
        this.saving = false
      }
    },
    optionsFor(row) {
      const options = this.settings.options || []
      if (!NUMERIC_FIELDS.has(row.metricField)) return options
      return options.filter(option => String(option.valueType).toUpperCase() === 'NUMBER')
    },
    parseReference(value) {
      const match = String(value || '').match(/^(VARIABLE|DATA_OBJECT_FIELD):(\d+)$/)
      return match ? { refType: match[1], refId: Number(match[2]) } : null
    },
    optionKey(option) { return `${option.refType}:${option.refId}` },
    joinValues(values) { return Array.isArray(values) ? values.join(',') : '' },
    splitValues(value) {
      return Array.from(new Set(String(value || '').split(',')
        .map(item => item.trim().toUpperCase()).filter(Boolean)))
    },
    projectLabel(project) {
      return `${project.projectName || project.projectCode}（${project.projectCode}）`
    },
    scopeLabel(scope) { return scope === 'GLOBAL' ? '全局' : '项目' },
    sourceLabel(source) {
      return {
        SYSTEM_DEFAULT: '系统默认',
        USER_DEFAULT: '个人默认',
        PROJECT_OVERRIDE: '项目覆盖'
      }[source] || '未知'
    },
    sourceTag(source) {
      return { SYSTEM_DEFAULT: 'info', USER_DEFAULT: 'success', PROJECT_OVERRIDE: 'warning' }[source] || 'info'
    },
    canRestore(row) {
      return row.source === (this.projectId ? 'PROJECT_OVERRIDE' : 'USER_DEFAULT')
    }
  }
}
</script>

<style scoped>
.dashboard-settings-page {
  display: grid;
  gap: 16px;
}

.dashboard-settings-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.dashboard-option-meta {
  float: right;
  margin-left: 16px;
  color: var(--el-text-color-secondary);
}

.dashboard-decision-values :deep(.el-card__header) {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.dashboard-decision-values :deep(.el-card__header span) {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.dashboard-map-view-settings :deep(.el-card__header > div) {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.dashboard-map-view-settings :deep(.el-card__header span) {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.dashboard-map-view-settings :deep(.el-form-item) {
  margin-bottom: 0;
}

.dashboard-map-view-actions {
  margin-left: auto;
}

.dashboard-settings-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  position: sticky;
  bottom: 12px;
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: color-mix(in srgb, var(--el-bg-color) 94%, transparent);
  box-shadow: var(--tianshu-shadow-md);
  backdrop-filter: blur(10px);
}

@media (max-width: 760px) {
  .dashboard-settings-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
