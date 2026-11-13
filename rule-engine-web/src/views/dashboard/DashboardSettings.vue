<template>
  <div class="uiue-list-page dashboard-settings-page">
    <div class="module-hint dashboard-settings-heading">
      <div>
        <div class="hint-title">数据看板设置</div>
        <dismissible-guidance guide-id="dashboard-settings.intro" label="看板设置说明" class="hint-text">字段关联始终保存资源 ID；个人项目覆盖逐项继承个人默认，再继承管理员维护的公共默认。</dismissible-guidance>
      </div>
      <el-button @click="$router.push('/dashboard')">返回看板</el-button>
    </div>

    <div class="uiue-search-container uiue-filter-toolbar">
      <el-form :inline="true" size="small">
        <el-form-item v-if="canManageDefaults" label="配置类型">
          <el-radio-group v-model="editSystemDefaults" :disabled="saving" @change="changeScope">
            <el-radio-button :value="false">我的配置</el-radio-button>
            <el-radio-button :value="true">公共默认</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!editSystemDefaults" label="配置范围">
          <project-resource-select
            v-model="projectId"
            clearable
            filterable
            :disabled="saving"
            placeholder="个人默认（仅全局字段）"
            style="width: 280px"
            @change="load"
          />
        </el-form-item>
        <el-form-item>
          <el-tag :type="projectId ? 'success' : 'info'">
            {{ editSystemDefaults ? '公共默认' : projectId ? '项目覆盖' : '个人默认' }}
          </el-tag>
          <el-tag v-if="projectId && settings.projectOverride" type="warning">已创建覆盖</el-tag>
        </el-form-item>
      </el-form>
      <div v-if="editSystemDefaults" class="btn-right">
        <el-button :disabled="saving || loading" @click="loadPersonalDefaults">载入我的默认配置</el-button>
      </div>
    </div>

    <el-alert v-if="editSystemDefaults" title="公共默认仅可关联全局字段，保存后供所有账户继承；已有个人或项目覆盖继续保留。清空字段后保存即可移除该项公共配置。" type="info" :closable="false" />
    <el-alert
      v-if="!settings.editable && !loading"
      title="当前控制台未启用用户登录，仅可查看公共默认字段映射。"
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
          <span>仅在当前浏览器会话中保存；未配置时默认展示全球。</span>
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
          <el-button @click="restoreGlobalMapView">恢复全球默认值</el-button>
          <el-button type="primary" @click="saveMapView">保存地图视角</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table :data="rows" border size="small" :aria-busy="loading">
      <el-table-column prop="label" label="指标字段" min-width="130" />
      <el-table-column label="引用字段" min-width="300">
        <template #default="{ row }">
          <var-picker
            :value="fieldPickerKey(row)"
            :vars="fieldPickerOptions(row)"
            value-key="reference"
            :allow-custom="false"
            :auto-switch-custom="false"
            :disabled="!settings.editable"
            placeholder="选择引擎字段或常量"
            @input="selectFieldReference(row, $event)"
          ><template #version>当前生效</template></var-picker>
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
          <el-tag v-if="!row.referenceKey" type="info" size="small">未配置</el-tag>
          <el-tooltip v-else-if="!row.valid" :content="row.errorMessage" placement="top">
            <el-tag type="danger" size="small">映射失效</el-tag>
          </el-tooltip>
          <el-tag v-else type="success" size="small">可用</el-tag>
        </template>
      </el-table-column>
      <el-table-column fixed="right" class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" :width="operationColumnWidth(['恢复继承'])" align="center">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :disabled="!settings.editable || !canRestore(row)"
            @click="restore(row)"
          >{{ editSystemDefaults ? '清除配置' : '恢复继承' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-card v-if="decisionRow" shadow="never" class="dashboard-decision-values">
      <template #header>
        <strong>决策结果取值</strong>
        <span>输入阈值、选择字段或配置表达式；计算结果与决策结果值匹配，三组取值不可重叠。路径读取上方所选 JSON 来源。</span>
      </template>
      <el-form label-width="90px" size="small">
        <el-form-item v-for="group in decisionGroups" :key="group.key" :label="group.label">
          <div class="dashboard-decision-references">
            <div v-for="(operand, index) in decisionRow[group.key + 'Operands']" :key="index" class="dashboard-decision-operand">
              <operand-picker :value="operand" :vars="decisionOptions" :allowed-kinds="decisionKinds" :disabled="!settings.editable"
                @input="updateDecisionOperand(group.key, index, $event)" />
              <el-button :disabled="!settings.editable" link type="danger" :aria-label="`删除${group.label}取值 ${index + 1}`" @click="removeDecisionOperand(group.key, index)">删除</el-button>
            </div>
            <operand-picker :key="group.key + ':' + decisionRow[group.key + 'Operands'].length" :value="null" :vars="decisionOptions"
              :allowed-kinds="decisionKinds" :disabled="!settings.editable" :placeholder="`添加${group.label}取值：阈值、路径或表达式`"
              @select="addDecisionOperand(group.key, $event)" />
          </div>
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
      <el-button type="primary" :loading="saving" :disabled="!settings.editable" @click="save">{{ editSystemDefaults ? '保存公共默认' : '保存设置' }}</el-button>
    </div>
  </div>
</template>

<script>
import DismissibleGuidance from '@/components/common/DismissibleGuidance.vue'
import ProjectResourceSelect from '@/components/ProjectResourceSelect.vue'
import VarPicker from '@/components/common/VarPicker.vue'
import OperandPicker from '@/components/common/OperandPicker.vue'
import { createLiteralOperand, createReferenceOperand } from '@/utils/operand'
import {
  deleteDashboardSetting,
  getDashboardSettings,
  saveDashboardSettings,
  getDashboardDefaults,
  saveDashboardDefaults
} from '@/api/dashboard'
import { hasPermission } from '@/security/permissionState'
import { operationColumnWidth as getOperationColumnWidth } from '@/utils/operationColumnWidth'
import {
  defaultDashboardMapView,
  readDashboardMapView,
  writeDashboardMapView
} from '@/utils/dashboardFilters'

const NUMERIC_FIELDS = new Set(['LONGITUDE', 'LATITUDE', 'PERIOD', 'AMOUNT'])

export default {
  components: { DismissibleGuidance, ProjectResourceSelect, VarPicker, OperandPicker },
  name: 'DashboardSettings',
  data() {
    return {
      decisionKinds: ['LITERAL', 'REFERENCE', 'PATH', 'OPERATION', 'ACCESS', 'CAST', 'ARRAY'],
      decisionGroups: [{ key: 'pass', label: '通过' }, { key: 'review', label: '人审' }, { key: 'reject', label: '拒绝' }],
      projectId: null,
      editSystemDefaults: false,
      initialRows: [],
      loadRequestId: 0,
      settings: { editable: false, projectOverride: false, options: [], fields: [] },
      rows: [],
      mapView: readDashboardMapView(window.sessionStorage),
      loading: false,
      saving: false
    }
  },
  computed: {
    canManageDefaults() { return hasPermission('system:edit') },
    decisionOptions() { return this.settings.options.map(this.pickerOption) },
    constantOptions() {
      return this.settings.options.filter(option => option.varSource === 'CONSTANT' && !['OBJECT', 'LIST', 'MAP', 'ARRAY'].includes(option.valueType)).map(this.pickerOption)
    },
    decisionRow() {
      return this.rows.find(row => row.metricField === 'DECISION_RESULT')
    }
  },
  mounted() {
    this.load()
  },
  methods: {
    operationColumnWidth(labels) { return getOperationColumnWidth(labels) },
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
    restoreGlobalMapView() {
      this.mapView = defaultDashboardMapView()
      return this.persistMapView('已恢复全球默认视角')
    },

    changeScope() {
      this.projectId = null
      this.load()
    },
    async loadPersonalDefaults() {
      const requestId = ++this.loadRequestId
      this.loading = true
      try {
        const response = await getDashboardSettings({ projectId: null })
        if (requestId === this.loadRequestId && this.editSystemDefaults) {
          this.applySettings({ ...response.data, defaultVersion: this.settings.defaultVersion })
        }
      } catch (error) {
        this.$message.error(error.message || '个人默认加载失败')
      } finally {
        if (requestId === this.loadRequestId) this.loading = false
      }
    },
    async load() {
      const requestId = ++this.loadRequestId
      this.loading = true
      try {
        const response = this.editSystemDefaults
          ? await getDashboardDefaults()
          : await getDashboardSettings({ projectId: this.projectId })
        if (requestId === this.loadRequestId) this.applySettings(response.data || {})
      } catch (error) {
        if (requestId === this.loadRequestId) this.$message.error(error.message || '字段映射加载失败')
      } finally {
        if (requestId === this.loadRequestId) this.loading = false
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
          passOperands: this.decisionOperands(values, 'pass', mapping.refId),
          reviewOperands: this.decisionOperands(values, 'review', mapping.refId),
          rejectOperands: this.decisionOperands(values, 'reject', mapping.refId)
        }
      })
      this.initialRows = JSON.parse(JSON.stringify(this.rows))
    },
    async save() {
      if (!this.settings.editable || this.loading) return
      const mappings = {}
      for (const row of this.rows) {
        if (row.metricField === 'DECISION_RESULT' && !row.referenceKey &&
          this.decisionGroups.some(group => row[group.key + 'Operands'].length)) {
          this.$message.warning('请先选择决策结果的引用字段')
          return
        }
        if (!row.referenceKey) continue
        const ownSource = this.projectId ? 'PROJECT_OVERRIDE' : 'USER_DEFAULT'
        const original = this.initialRows.find(item => item.metricField === row.metricField)
        if (!this.editSystemDefaults && row.source !== ownSource && original &&
          ['referenceKey', 'jsonSource', 'passOperands', 'reviewOperands', 'rejectOperands'].every(key => JSON.stringify(row[key]) === JSON.stringify(original[key]))) continue
        const reference = this.parseReference(row.referenceKey)
        if (!reference) {
          this.$message.warning(`请选择${row.label}的引用字段`)
          return
        }
        if (row.metricField === 'DECISION_RESULT' && this.decisionGroups.some(group => !row[group.key + 'Operands'].length)) {
          this.$message.warning('通过、人审和拒绝取值均不能为空')
          return
        }
        mappings[row.metricField] = {
          ...reference,
          jsonSource: row.jsonSource,
          decisionValues: row.metricField === 'DECISION_RESULT'
            ? {
                passOperands: row.passOperands,
                reviewOperands: row.reviewOperands,
                rejectOperands: row.rejectOperands
              }
            : null
        }
      }
      this.saving = true
      try {
        const response = this.editSystemDefaults
          ? await saveDashboardDefaults({ mappings, version: this.settings.defaultVersion })
          : await saveDashboardSettings({ projectId: this.projectId }, { mappings })
        this.applySettings(response.data || {})
        this.$message.success(this.editSystemDefaults ? '公共默认已保存，未覆盖的账户将自动继承' : '数据看板字段映射已保存')
      } catch (error) {
        this.$message.error(error.message || '保存失败')
      } finally {
        this.saving = false
      }
    },
    async restore(row) {
      if (!this.settings.editable) return
      if (this.editSystemDefaults) { this.selectFieldReference(row, ''); return }
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
    pickerOption(option) {
      const refType = option.varSource === 'CONSTANT' ? 'CONSTANT' : option.refType === 'DATA_OBJECT_FIELD' ? 'DATA_OBJECT' : option.refType
      return { id: option.refId, _varId: option.refId, _refType: refType, varCode: option.code || '', varLabel: option.label,
        varType: option.valueType, varSource: option.varSource, constantValue: option.constantValue, versionMode: 'LATEST', catalogMode: 'FROZEN',
        _ref: { category: refType === 'CONSTANT' ? 'constant' : refType === 'DATA_OBJECT' ? 'object' : 'standalone' } }
    },
    fieldPickerOptions(row) {
      const options = this.optionsFor(row).map(this.pickerOption)
      if (row.referenceKey && !this.settings.options.some(option => this.optionKey(option) === row.referenceKey)) {
        const ref = this.parseReference(row.referenceKey)
        if (ref) options.push({ ...this.pickerOption({ ...ref, label: '不可用的字段引用' }), disabled: true })
      }
      return options
    },
    fieldPickerKey(row) {
      const key = row.referenceKey || ''
      const option = this.settings.options.find(item => this.optionKey(item) === row.referenceKey)
      return option?.varSource === 'CONSTANT' ? key.replace('VARIABLE:', 'CONSTANT:') : key.replace('DATA_OBJECT_FIELD:', 'DATA_OBJECT:')
    },
    selectFieldReference(row, value) {
      row.referenceKey = String(value || '').replace('CONSTANT:', 'VARIABLE:').replace('DATA_OBJECT:', 'DATA_OBJECT_FIELD:')
      if (!row.referenceKey && row.metricField === 'DECISION_RESULT') {
        this.decisionGroups.forEach(group => { row[group.key + 'Operands'] = [] })
      }
    },
    decisionOperands(values, group, refId) {
      if (!refId) return []
      if (values[group + 'Operands']?.length) return JSON.parse(JSON.stringify(values[group + 'Operands']))
      const ids = values[group + 'RefIds'] || []
      if (ids.length) return ids.map(id => createReferenceOperand(this.constantOptions.find(option => String(option.id) === String(id)) || {
        _varId: id, _refType: 'CONSTANT', varCode: '不可用的常量引用', varLabel: '不可用的常量引用', versionMode: 'LATEST',
      }))
      return (values[group] || []).map(value => createLiteralOperand(value, 'STRING'))
    },
    addDecisionOperand(group, operand) {
      if (!this.settings.editable || !operand) return
      this.decisionRow[group + 'Operands'].push(JSON.parse(JSON.stringify(operand)))
    },
    updateDecisionOperand(group, index, operand) {
      if (!this.settings.editable) return
      if (!operand) { this.removeDecisionOperand(group, index); return }
      this.decisionRow[group + 'Operands'].splice(index, 1, JSON.parse(JSON.stringify(operand)))
    },
    removeDecisionOperand(group, index) {
      if (this.settings.editable) this.decisionRow[group + 'Operands'].splice(index, 1)
    },
    optionsFor(row) {
      const options = this.settings.options || []
      if (!NUMERIC_FIELDS.has(row.metricField)) return options
      return options.filter(option => String(option.valueType).toUpperCase() === 'NUMBER')
    },
    parseReference(value) {
      const match = String(value || '').match(/^(VARIABLE|DATA_OBJECT_FIELD):(\d+)$/)
      return match ? { refType: match[1], refId: Number.isSafeInteger(Number(match[2])) ? Number(match[2]) : match[2] } : null
    },
    optionKey(option) { return `${option.refType}:${option.refId}` },
    projectLabel(project) {
      return `${project.projectName || project.projectCode}（${project.projectCode}）`
    },
    sourceLabel(source) {
      return {
        SYSTEM_DEFAULT: '公共默认',
        USER_DEFAULT: '个人默认',
        PROJECT_OVERRIDE: '项目覆盖'
      }[source] || '未知'
    },
    sourceTag(source) {
      return { SYSTEM_DEFAULT: 'info', USER_DEFAULT: 'success', PROJECT_OVERRIDE: 'warning' }[source] || 'info'
    },
    canRestore(row) {
      if (this.editSystemDefaults) return Boolean(row.referenceKey)
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

.dashboard-decision-references { width: 100%; }
.dashboard-decision-operand { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; }
.dashboard-decision-operand > .operand-picker { flex: 1; min-width: 0; }

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
