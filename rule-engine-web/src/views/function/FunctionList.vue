<template>
  <div class="uiue-list-page management-list-page">
    <div class="linkage-hint">
      <el-icon><el-icon-info /></el-icon>
      自定义函数可在规则设计器的函数选择器中使用，无需手写调用表达式。支持脚本、Java 类或 Spring Bean 实现。
    </div>

    <div v-if="currentProjectId" class="function-context-hint">
      当前项目：<strong>{{ currentProjectName || currentProjectId }}</strong>。列表包含全局函数和当前项目函数，新建默认保存为项目级函数。
    </div>

    <div v-if="projectLoadError" class="function-page-error" role="alert">
      {{ projectLoadError }}，项目筛选暂不可用。
      <el-button link size="small" @click="loadProjects">重试</el-button>
    </div>

    <div class="uiue-search-container uiue-filter-toolbar">
      <el-form :inline="true" size="small" @keyup.enter="handleQuery">
        <el-form-item label="作用范围">
          <el-select
            v-model="qp.scope"
            clearable
            filterable
            placeholder="全部"
            style="width: 100px"
          >
            <el-option label="全局" value="GLOBAL" />
            <el-option label="项目" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目编码">
          <project-filter-select
            v-model:value="qp.projectCode"
            field="projectCode"
            placeholder="输入筛选"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item label="项目名称">
          <project-filter-select
            v-model:value="qp.projectName"
            field="projectName"
            placeholder="输入筛选"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item label="实现方式">
          <el-select
            v-model="qp.implType"
            clearable
            filterable
            placeholder="全部"
            style="width: 110px"
          >
            <el-option label="QLExpress 脚本" value="SCRIPT" />
            <el-option label="Java 类" value="JAVA" />
            <el-option label="Spring Bean" value="BEAN" />
          </el-select>
        </el-form-item>
        <el-form-item label="函数编码">
          <remote-filter-select
            v-model:value="qp.funcCode"
            :fetch-options="fetchFuncCodeOptions"
            option-label-key="funcCode"
            option-value-key="funcCode"
            allow-free-input
            placeholder="输入筛选"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item label="函数名称">
          <remote-filter-select
            v-model:value="qp.funcLabel"
            :fetch-options="fetchFuncLabelOptions"
            option-label-key="funcName"
            option-value-key="funcName"
            allow-free-input
            placeholder="输入筛选"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="uiue-btn-bar">
        <div class="btn-right">
          <table-column-settings v-model="tableColumnOrders.functionColumns" :columns="functionColumns" storage-key="tianshu:table-columns:function-list" />
          <governance-workspace-toolbar resource-type="FUNCTION" />
          <el-button
            v-permission="'function:edit'"
            size="small"
            :icon="ElIconPlus"
            type="primary"
            @click="handleCreate"
            >新建函数</el-button
          >
        </div>
      </div>
    </div>

    <el-table class="management-table" show-overflow-tooltip
      :data="funcList"
      border
      size="small"
      :aria-busy="loading"
      style="width: 100%; margin-top: 12px"
    >
      <template v-for="columnKey in (tableColumnOrders.functionColumns || functionColumns.map(column => column.key))" :key="columnKey">
      <el-table-column v-if="columnKey === 'scope'" label="作用范围" width="90" align="center">
        <template v-slot="{ row }">
          <el-tag
            :class="row.scope === 'GLOBAL' ? 'el-tag--scope-global' : 'el-tag--scope-project'"
            size="small"
            >{{ scopeLabel(row.scope) }}</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column v-if="columnKey === 'projectName'" label="项目名称" min-width="120" show-overflow-tooltip>
        <template v-slot="{ row }">{{ row.projectName || '—' }}</template>
      </el-table-column>
      <el-table-column v-if="columnKey === 'funcCode'"
        prop="funcCode"
        label="函数编码"
        min-width="120"
        show-overflow-tooltip
      />
      <el-table-column v-if="columnKey === 'funcName'"
        prop="funcName"
        label="函数名称"
        min-width="120"
        show-overflow-tooltip
      />
      <el-table-column v-if="columnKey === 'returnType'"
        prop="returnType"
        label="返回类型"
        width="90"
        align="center"
      >
        <template v-slot="{ row }">
          <el-tag size="small">{{ typeLabel(row.returnType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="columnKey === 'implType'"
        prop="implType"
        label="实现方式"
        width="110"
        align="center"
      >
        <template v-slot="{ row }">
          <el-tag :type="implTypeTagType(row.implType)" size="small">{{
            implTypeLabel(row.implType)
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="columnKey === 'params'" label="参数" min-width="150">
        <template v-slot="{ row }">
          <span v-if="row.paramsJson">
            <el-tag
              v-for="(p, pi) in parseParams(row.paramsJson)"
              :key="pi"
              size="small"
              type="info"
              style="margin: 1px 2px"
            >
              {{ p.name }}: {{ typeLabel(p.type) }}
            </el-tag>
          </span>
          <span v-else style="color: var(--tianshu-text-tertiary)">无参</span>
        </template>
      </el-table-column>
      <el-table-column v-if="columnKey === 'updateTime'" label="更新时间" width="165" align="center">
        <template v-slot="{ row }">{{
          formatUpdateTime(row.updateTime)
        }}</template>
      </el-table-column>
      <el-table-column v-if="columnKey === 'status'" prop="status" label="状态" width="160" align="center">
        <template v-slot="{ row }">
          <governance-resource-status resource-type="FUNCTION" :resource-id="row.id" :status="row.status"><el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">{{
            row.status === 1 ? '启用' : '停用'
          }}</el-tag></governance-resource-status>
        </template>
      </el-table-column>
      </template>
      <el-table-column fixed="right" class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" :width="operationColumnWidth(['编辑', '测试', '版本', '发布', '删除'])" align="center">
        <template v-slot="{ row }">
          <el-button
            v-permission="{ code: 'function:edit', projectId: row.projectId, scope: row.scope }"
            v-if="!['JAVA', 'BEAN'].includes(row.implType) || canManageNative"
            link
            data-action="edit"
            size="small"
            type="warning"
            @click="editWorkspace('FUNCTION', row, handleEdit)"
            >编辑</el-button
          >
          <el-button v-permission="{ code: 'function:edit', projectId: row.projectId, scope: row.scope }"
            link
            data-action="execute"
            size="small"
            type="success"
            @click="handleTestFunction(row)"
            >测试</el-button
          >
          <resource-version-manager v-if="row.id" data-action="version" resource-type="FUNCTION" :resource-id="row.id" has-legacy @legacy="openVersionDialog(row)" />
          <governance-publish-action resource-type="FUNCTION" :resource-id="row.id" :show-versions="false" />
          <el-button
            v-permission="{ code: 'function:edit', projectId: row.projectId, scope: row.scope }"
            link
            data-action="delete"
            size="small"
            type="danger"
            class="btn-delete"
            @click="handleDelete(row)"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>

    <div v-if="functionLoadError" class="function-page-error" role="alert">
      {{ functionLoadError }}。
      <el-button link size="small" @click="loadFunctions">重试</el-button>
    </div>

    <div
      v-else-if="!loading && funcList.length === 0"
      class="function-empty"
    >
      <template v-if="hasActiveFunctionFilters">
        未找到符合当前筛选条件的函数，请调整条件或点击「重置」
      </template>
      <template v-else>暂无自定义函数，可点击「新建函数」添加</template>
    </div>

    <el-pagination
      style="margin-top: 16px; text-align: right"
      :current-page="qp.pageNum"
      :page-size="qp.pageSize"
      :total="total"
      layout="total,sizes,prev,pager,next"
      :page-sizes="[10, 20, 30, 50, 100, 200, 500]"
      @current-change="onPageChange"
      @size-change="onSizeChange"
    />

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      :title="editForm.id ? '编辑函数' : '新建函数'"
      v-model="dialogVisible"
      width="760px"
      append-to-body
    >
      <function-impact-panel v-if="dialogVisible" :form="editForm" :params="editParams" />
      <el-form :model="editForm" label-width="90px" size="small" :disabled="functionSaving">
        <el-form-item label="作用范围">
          <el-select
            v-model="editForm.scope"
            placeholder="选择作用范围"
            style="width: 100%"
            @change="onScopeChange"
          >
            <el-option label="🌐 全局（所有项目可用）" value="GLOBAL" />
            <el-option label="📁 项目级" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="editForm.scope === 'PROJECT'"
          label="项目名称"
          required
        >
          <project-resource-select
            v-model="editForm.projectId"
            placeholder="请选择项目"
            style="width: 100%"
            filterable
           :projects="projects" />
        </el-form-item>
        <el-form-item label="函数编码" required>
          <el-input
            v-model="editForm.funcCode"
            maxlength="128"
            show-word-limit
            placeholder="如 calcTax（QLExpress 脚本中的调用名）"
          />
        </el-form-item>
        <el-form-item label="函数名称" required>
          <el-input v-model="editForm.funcName" maxlength="128" show-word-limit placeholder="中文名称，如 计算税额" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="2"
            maxlength="512"
            show-word-limit
            placeholder="函数功能说明"
          />
        </el-form-item>
        <el-form-item label="返回类型">
          <el-select
            v-model="editForm.returnType"
            style="width: 100%"
          >
            <el-option
              v-for="opt in varTypeFormOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="实现方式">
          <el-radio-group v-model="editForm.implType" @change="onImplementationChange">
            <el-radio value="SCRIPT">QLExpress 脚本</el-radio>
            <el-radio value="JAVA" :disabled="!canManageNative">Java 类</el-radio>
            <el-radio value="BEAN" :disabled="!canManageNative">Spring Bean</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="isNativeFunction">
          <el-form-item :label="editForm.implType === 'JAVA' ? 'Java类名' : 'Bean名称'" required>
            <div class="function-method-target">
              <el-input v-if="editForm.implType === 'JAVA'" v-model="editForm.implClass" placeholder="已部署的 Java 类全名" />
              <el-input v-else v-model="editForm.implBeanName" placeholder="已部署的 Spring Bean 名称" />
              <el-button :loading="methodLoading" @click="loadFunctionMethods">读取方法</el-button>
            </div>
          </el-form-item>
          <el-form-item label="实际方法">
            <div class="function-method-selection">
              <el-select v-if="!nativeManual" :model-value="selectedNativeSignature" filterable style="width: 100%" placeholder="先读取方法，再选择实际签名" data-testid="function-method-select" @change="selectFunctionMethod">
                <el-option v-for="method in nativeMethodChoices" :key="method.signature" :value="method.signature" :label="method.displaySignature" :disabled="!method.available" :title="method.unavailableReason || method.displaySignature" />
              </el-select>
              <el-input v-else v-model="editForm.implMethod" placeholder="实际方法名（不包含括号和参数）" data-testid="function-method-name" />
              <div class="function-method-help">
                <span v-if="editForm.implMethod">已配置方法：{{ editForm.implMethod }}</span>
                <el-button link @click="nativeManual = !nativeManual">{{ nativeManual ? '返回方法选择' : '手工填写目标签名' }}</el-button>
              </div>
              <el-alert v-if="currentMethodCatalog" :title="currentMethodCatalog.message" :type="currentMethodCatalog.valid && !currentMethodCatalog.legacySignature ? 'success' : 'warning'" :closable="false" show-icon />
              <div v-else class="function-method-help">选择方法会带出实际参数类型；业务名称和参数说明保持可编辑，保存前会再次核对。</div>
              <div v-if="nativeManual" class="function-method-help">用于目标运行端尚未安装的实现。请明确填写方法名和每个参数的实际类型；未验证状态会保留在审批预检中。</div>
            </div>
          </el-form-item>
        </template>
        <el-form-item label="参数列表">
          <div class="function-form-help">参数名用于脚本调用；Java/Bean 函数的实际方法类型由方法选择结果带出，业务类型用于设计器字段类型匹配。</div>
          <el-alert v-if="parameterConfigError" :title="parameterConfigError" type="error" :closable="false" show-icon>
            <el-button link @click="resetFunctionParameters">重新配置参数</el-button>
          </el-alert>
          <div class="function-parameters">
          <div
            v-for="(p, pi) in editParams"
            :key="pi"
            class="function-parameter-row"
          >
            <el-input
              v-model="p.name"
              size="small"
              placeholder="参数名"
              :aria-label="`参数 ${pi + 1} 名称`"
            />
            <el-select
              v-model="p.type"
              size="small"
              :aria-label="`参数 ${pi + 1} 业务类型`"
            >
              <el-option
                v-for="opt in varTypeFormOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-input
              v-model="p.label"
              size="small"
              placeholder="中文名"
              :aria-label="`参数 ${pi + 1} 显示名称`"
            />
            <el-button
              link
              size="small"
              :icon="ElIconDelete"
              class="btn-delete"
              :aria-label="`删除参数 ${pi + 1}`"
              :disabled="isNativeFunction && !nativeManual"
              @click="editParams.splice(pi, 1)"
            />
            <div v-if="isNativeFunction" class="function-parameter-java-type">
              <span>实际方法类型</span>
              <el-select v-if="nativeManual" v-model="p.javaType" filterable allow-create default-first-option :aria-label="`参数 ${pi + 1} 实际方法类型`" placeholder="选择或填写精确 Java 类型">
                <el-option v-for="type in functionJavaTypes" :key="type" :value="type" :label="type" />
              </el-select>
              <code v-else>{{ methodParameterType(pi) }}</code>
            </div>
          </div>
          <div v-if="!editParams.length && !parameterConfigError" class="function-method-help">无参数</div>
          <el-button
            size="small"
            :icon="ElIconPlus"
            :disabled="isNativeFunction && !nativeManual"
            @click="editParams.push({ name: '', type: 'STRING', label: '', ...(isNativeFunction ? { javaType: '' } : {}) })"
            >添加参数</el-button
          >
          </div>
        </el-form-item>
        <el-form-item label="实现脚本" v-if="editForm.implType === 'SCRIPT'">
          <monaco-editor
            v-model:value="editForm.implScript"
            language="ql"
            height="180px"
            :functions="funcList"
          />
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <el-button size="small" @click="dialogVisible = false">取消</el-button>
        <el-button v-permission="'function:edit'" size="small" type="primary" :loading="functionSaving" @click="handleSave"
          >保存</el-button
        >
      </template>
    </el-dialog>

    <el-dialog
      title="函数测试"
      v-model="functionTestVisible"
      width="760px"
      append-to-body
    >
      <div class="function-test-target">
        当前函数：{{ functionTestTarget.funcName }} /
        {{ functionTestTarget.funcCode }}
      </div>
      <el-form label-width="90px" size="small">
        <el-form-item label="测试入参">
          <monaco-editor
            v-model:value="functionTestParamsText"
            language="json"
            height="220px"
          />
        </el-form-item>
        <el-form-item label="执行结果">
          <monaco-editor
            v-model:value="functionTestResultText"
            language="json"
            height="220px"
            read-only
          />
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <div>
          <el-button size="small" @click="functionTestVisible = false"
            >关闭</el-button
          >
          <el-button v-permission="'function:edit'"
            size="small"
            type="primary"
            :loading="functionTesting"
            @click="doTestFunction"
            >执行测试</el-button
          >
        </div>
      </template>
    </el-dialog>

    <el-dialog
      title="函数版本管理"
      v-model="versionVisible"
      width="900px"
      append-to-body
    >
      <div class="version-compare-toolbar">
        <el-select v-model="versionCompareLeft" size="small" placeholder="选择左侧版本">
          <el-option
            v-for="item in versionList"
            :key="`left-${item.version}`"
            :label="versionDisplayLabel(item)"
            :value="item.version"
          />
        </el-select>
        <span>对比</span>
        <el-select v-model="versionCompareRight" size="small" placeholder="选择右侧版本">
          <el-option
            v-for="item in versionList"
            :key="`right-${item.version}`"
            :label="versionDisplayLabel(item)"
            :value="item.version"
          />
        </el-select>
        <el-button
          size="small"
          type="primary"
          :disabled="!versionCompareLeft || !versionCompareRight"
          @click="compareSelectedVersions"
          >查看具体差异</el-button
        >
      </div>
      <el-table show-overflow-tooltip :data="versionList" border size="small" style="width: 100%">
        <el-table-column
          prop="displayVersion"
          label="版本"
          width="140"
          align="center"
        ><template #default="{ row }">{{ versionDisplayLabel(row) }}</template></el-table-column>
        <el-table-column
          prop="changeLog"
          label="变更说明"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column prop="publishTime" label="时间" width="170" />
        <el-table-column fixed="right" class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" :width="operationColumnWidth(['查看', '对比', '回滚'])" align="center">
          <template v-slot="{ row, $index }">
            <el-button
              link
              data-action="inspect"
              size="small"
              type="primary"
              @click="viewVersion(row)"
              >查看</el-button
            >
            <el-button
              v-if="$index < versionList.length - 1"
              link
              data-action="compare"
              size="small"
              type="info"
              @click="compareWithNext(row, $index)"
              >对比</el-button
            >
            <el-button v-permission="{ code: 'function:edit', projectId: row.projectId, scope: row.scope }"
              link
              data-action="rollback"
              size="small"
              type="warning"
              @click="rollbackFunctionVersion(row)"
              >回滚</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <div v-if="versionCompare" class="version-compare">
        <json-version-diff
          :original="versionCompare.left.functionJson"
          :modified="versionCompare.right.functionJson"
          :original-label="`v${versionCompare.left.version}`"
          :modified-label="`v${versionCompare.right.version}`"
          height="380px"
        />
      </div>
      <pre v-if="versionPreview" class="version-preview">{{
        versionPreview
      }}</pre>
    </el-dialog>
  </div>
</template>

<script>
import { hasPermission } from '@/security/permissionState'
import ProjectResourceSelect from '@/components/ProjectResourceSelect.vue'
import FunctionImpactPanel from '@/components/FunctionImpactPanel.vue'
import ResourceVersionManager from '@/components/common/ResourceVersionManager.vue'
import GovernancePublishAction from '@/components/GovernancePublishAction.vue'
import GovernanceWorkspaceToolbar from '@/components/GovernanceWorkspaceToolbar.vue'
import GovernanceResourceStatus from '@/components/GovernanceResourceStatus.vue'
import governanceWorkspaceMixin from '@/mixins/governanceWorkspaceMixin'
import { completeResourceChange } from '@/utils/governanceWorkspace'
import { chooseVersionRestore, versionDisplayLabel } from '@/utils/versionRestore'
import { markRaw } from 'vue'
import {
  InfoFilled as ElIconInfo,
  Plus as ElIconPlus,
  Delete as ElIconDelete,
} from '@element-plus/icons-vue'
import {
  createFunction,
  updateFunction,
  deleteFunction,
  listFunctions,
  listVersions,
  compareVersions,
  rollbackVersion,
  testFunction,
  inspectFunctionMethods,
} from '@/api/function'
import { listProjects } from '@/api/project'
import { isRequestErrorNotified } from '@/api/request'
import { VAR_TYPE_FORM_OPTIONS, varTypeLabel } from '@/constants/varTypes'
import {
  clearPageState,
  restorePageState,
  savePageState,
} from '@/utils/pageStateCache'
import { sampleValueForVarType } from '@/utils/testParamTemplate'
import { parseFunctionParameters, validatedFunctionParameters, explicitFunctionSignature, FUNCTION_JAVA_TYPES } from '@/utils/functionParameters'
import MonacoEditor from '@/components/MonacoEditor'
import JsonVersionDiff from '@/components/common/JsonVersionDiff.vue'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import TableColumnSettings from '@/components/common/TableColumnSettings.vue'
import { operationColumnWidth as getOperationColumnWidth } from '@/utils/operationColumnWidth'
import { projectPageStateKey, routeProjectId } from '@/utils/projectContext'

export default {
  mixins: [governanceWorkspaceMixin],
  data() {
    return {
      tableColumnOrders: { functionColumns: null },
      functionColumns: [
        { key: 'scope', label: '作用范围' },
        { key: 'projectName', label: '项目名称' },
        { key: 'funcCode', label: '函数编码' },
        { key: 'funcName', label: '函数名称' },
        { key: 'returnType', label: '返回类型' },
        { key: 'implType', label: '实现方式' },
        { key: 'params', label: '参数' },
        { key: 'updateTime', label: '更新时间' },
        { key: 'status', label: '状态' },
      ],
      projects: [],
      currentProjectId: null,
      projectLoadError: '',
      functionLoadError: '',
      funcList: [],
      total: 0,
      qp: {
        pageNum: 1,
        pageSize: 20,
        scope: '',
        projectCode: '',
        projectName: '',
        implType: '',
        funcCode: '',
        funcLabel: '',
      },
      loading: false,
      dialogVisible: false,
      versionVisible: false,
      versionRow: null,
      versionList: [],
      versionCompareLeft: null,
      versionCompareRight: null,
      versionCompare: null,
      versionPreview: '',
      projectList: [],
      filteredProjectCodes: [],
      filteredProjectNames: [],
      editForm: {
        funcCode: '',
        funcName: '',
        description: '',
        returnType: 'STRING',
        implType: 'SCRIPT',
        implScript: '',
        implClass: '',
        implMethod: '',
        implBeanName: '',
        status: 1,
      },
      editParams: [],
      parameterConfigError: '',
      methodCatalog: null,
      methodCatalogKey: '',
      methodCatalogTargetKey: '',
      methodLoading: false,
      methodRequestId: 0,
      editSessionId: 0,
      originalBindingKey: '',
      nativeManual: false,
      functionSaving: false,
      functionJavaTypes: FUNCTION_JAVA_TYPES,
      functionTestVisible: false,
      functionTesting: false,
      functionTestTarget: {},
      functionTestParamsText: '{}',
      functionTestResultText: '',
      varTypeFormOptions: VAR_TYPE_FORM_OPTIONS,
      // 测试期望的别名属性
      // 与 funcList 同步
      functions: [],
      // 'create' | 'edit'
      dialogMode: 'create',
      // 测试期望的别名
      form: { funcType: 'QL_EXPRESS', scope: 'PROJECT' },
      ElIconPlus: markRaw(ElIconPlus),
      ElIconDelete: markRaw(ElIconDelete),
    }
  },
  components: { FunctionImpactPanel, ProjectResourceSelect, ResourceVersionManager, GovernancePublishAction, GovernanceWorkspaceToolbar,
    GovernanceResourceStatus,
    TableColumnSettings,
    MonacoEditor,
    JsonVersionDiff,
    RemoteFilterSelect,
    ProjectFilterSelect,
    ElIconInfo,
  },
  name: 'FunctionList',
  computed: {
    canManageNative() { return hasPermission('function:native') },
    isNativeFunction() { return ['JAVA', 'BEAN'].includes(this.editForm.implType) },
    nativeBindingKey() { return this.functionBindingKey() },
    nativeTargetKey() { return JSON.stringify([this.editForm.implType, this.editForm.implClass, this.editForm.implBeanName]) },
    currentMethodCatalog() { return this.methodCatalogKey === this.nativeBindingKey ? this.methodCatalog : null },
    nativeMethodChoices() { return this.methodCatalogTargetKey === this.nativeTargetKey ? this.methodCatalog?.methods || [] : [] },
    selectedNativeSignature() {
      try { return explicitFunctionSignature(this.editForm, this.editParams) || this.currentMethodCatalog?.signature || '' }
      catch { return '' }
    },
    hasActiveFunctionFilters() {
      return [
        'scope',
        'projectCode',
        'projectName',
        'implType',
        'funcCode',
        'funcLabel',
      ].some((key) => String(this.qp[key] || '').trim() !== '')
    },
    currentProjectName() {
      const project = this.projects.find(item => String(item.id) === String(this.currentProjectId))
      return project?.projectName || ''
    },
  },
  created() {
    this.syncProjectContext()
    this.restoreCachedState()
    this.loadProjects().then(() => this.loadFunctions())
  },
  watch: {
    dialogVisible(visible) { if (!visible) { this.methodRequestId++; this.editSessionId++; this.methodLoading = false } },
    '$route.query.projectId'() { this.handleProjectContextChange() },
  },
  beforeUnmount() { this.methodRequestId++; this.editSessionId++ },
  methods: {
    syncProjectContext() {
      this.currentProjectId = routeProjectId(this.$route, this.$store && this.$store.state.currentProject)
    },
    handleProjectContextChange() {
      const previous = this.currentProjectId
      this.syncProjectContext()
      if (previous === this.currentProjectId) return
      this.qp.pageNum = 1
      this.restoreCachedState()
      this.loadFunctions()
    },
    operationColumnWidth(labels) { return getOperationColumnWidth(labels) },
    versionDisplayLabel,
    functionBindingKey(form = this.editForm, params = this.editParams) {
      return JSON.stringify([form.implType, form.implClass, form.implBeanName, form.implMethod, form.funcCode, params])
    },
    functionDeclarationKey() {
      return JSON.stringify([this.nativeTargetKey, this.editForm.implMethod?.trim() || this.editForm.funcCode,
        this.editParams.map(param => [param.type, param.javaType])])
    },
    resetMethodEditor() {
      this.editSessionId++; this.methodRequestId++; this.methodLoading = false
      this.methodCatalog = null; this.methodCatalogKey = ''; this.methodCatalogTargetKey = ''; this.nativeManual = false
      this.parameterConfigError = ''; this.functionSaving = false
    },
    onImplementationChange(type) {
      this.methodRequestId++; this.methodLoading = false; this.methodCatalog = null; this.methodCatalogKey = ''; this.nativeManual = false
      if (type === 'SCRIPT') this.editParams = this.editParams.map(param => {
        const copy = { ...param }; delete copy.javaType; return copy
      })
      else if (this.editParams.length === 1 && !this.editParams[0].name && !this.editParams[0].label
        && this.editParams[0].type === 'STRING' && Object.keys(this.editParams[0]).every(key => ['name', 'type', 'label'].includes(key))) {
        this.editParams = []
      }
    },
    resetFunctionParameters() {
      this.editParams = []; this.parameterConfigError = ''
      this.methodCatalog = null; this.methodCatalogKey = ''
    },
    async loadFunctionMethods() {
      const requestId = ++this.methodRequestId
      const session = this.editSessionId
      const key = this.nativeBindingKey
      const targetKey = this.nativeTargetKey
      const payload = { ...this.editForm, paramsJson: JSON.stringify(this.editParams) }
      this.methodLoading = true; this.methodCatalog = null; this.methodCatalogKey = ''
      try {
        const response = await inspectFunctionMethods(payload)
        if (requestId !== this.methodRequestId || session !== this.editSessionId || targetKey !== this.nativeTargetKey || !this.dialogVisible) return
        if (!response?.data || !Array.isArray(response.data.methods) || !['AVAILABLE', 'UNAVAILABLE', 'INVALID'].includes(response.data.status)) {
          throw new Error('返回的方法目录不完整，请重新读取')
        }
        this.methodCatalog = response.data; this.methodCatalogKey = key; this.methodCatalogTargetKey = targetKey
      } catch (error) {
        if (requestId !== this.methodRequestId || session !== this.editSessionId || key !== this.nativeBindingKey || !this.dialogVisible) return
        this.methodCatalog = { status: 'ERROR', valid: false, methods: [], message: this.errorMessage(error, '读取方法失败，请重试') }
        this.methodCatalogKey = key
      } finally { if (requestId === this.methodRequestId && session === this.editSessionId) this.methodLoading = false }
    },
    selectFunctionMethod(signature) {
      const selected = this.nativeMethodChoices.find(method => method.signature === signature && method.available)
      if (!selected) return
      const catalog = this.methodCatalog
      this.editForm.implMethod = selected.name
      this.editForm.returnType = this.returnTypeForJavaType(selected.returnJavaType)
      this.editParams = selected.parameters.map((parameter, index) => {
        const previous = this.editParams[index]
        const untouched = previous && !previous.name && !previous.label && previous.type === 'STRING' && !previous.javaType
        return { ...previous, name: previous?.name || parameter.name || '', label: previous?.label || '',
          type: !previous || untouched ? parameter.suggestedType : previous.type, javaType: parameter.javaType }
      })
      this.parameterConfigError = ''; this.nativeManual = false
      this.methodCatalog = { ...catalog, signature, legacySignature: false, valid: true, message: '已选择实际方法；保存前将再次核对完整参数配置' }
      this.methodCatalogKey = this.nativeBindingKey
    },
    returnTypeForJavaType(javaType) {
      const type = String(javaType || '')
      if (type === 'boolean' || type === 'java.lang.Boolean') return 'BOOLEAN'
      if (type === 'java.lang.String' || type === 'char' || type === 'java.lang.Character') return 'STRING'
      if (['byte', 'short', 'int', 'long', 'float', 'double'].includes(type)
        || ['java.lang.Byte', 'java.lang.Short', 'java.lang.Integer', 'java.lang.Long', 'java.lang.Float', 'java.lang.Double', 'java.math.BigDecimal'].includes(type)) return 'NUMBER'
      if (type === 'java.util.Map') return 'MAP'
      if (type.startsWith('[') || type === 'java.util.List' || type === 'java.util.Collection') return 'LIST'
      if (type === 'java.util.Date' || type.startsWith('java.time.')) return 'DATE'
      return 'OBJECT'
    },
    methodParameterType(index) {
      const explicit = this.editParams[index]?.javaType
      if (explicit) return explicit
      return this.currentMethodCatalog?.methods?.find(method => method.signature === this.currentMethodCatalog.signature)?.parameters?.[index]?.javaType || '旧映射，读取方法后确认'
    },
    restoreCachedState() {
      const state = restorePageState(projectPageStateKey('FunctionList', this.currentProjectId))
      if (state.qp) this.qp = { ...this.qp, ...state.qp }
    },
    saveCachedState() {
      savePageState(projectPageStateKey('FunctionList', this.currentProjectId), { qp: this.qp })
    },
    async loadProjects() {
      this.projectLoadError = ''
      try {
        const res = await listProjects({ pageNum: 1, pageSize: 200, status: 1 })
        const list =
          (res && res.data && res.data.records) || (res && res.data) || []
        this.projects = list
        this.projectList = list
        this.filteredProjectCodes = list.slice(0, 20)
        this.filteredProjectNames = list.slice(0, 20)
      } catch (e) {
        this.projects = []
        this.projectList = []
        this.projectLoadError = this.errorMessage(e, '加载项目列表失败')
      }
    },
    fetchFuncCodeOptions({ query, pageNum, pageSize }) {
      return listFunctions({
        ...this.qp,
        pageNum,
        pageSize,
        funcCode: query || '',
      })
    },
    fetchFuncLabelOptions({ query, pageNum, pageSize }) {
      return listFunctions({
        ...this.qp,
        pageNum,
        pageSize,
        funcLabel: query || '',
      })
    },
    queryProjectCode(query) {
      const q = (query || '').toLowerCase()
      this.filteredProjectCodes = q
        ? this.projectList
            .filter(
              (p) => p.projectCode && p.projectCode.toLowerCase().includes(q)
            )
            .slice(0, 20)
        : this.projectList.slice(0, 20)
    },
    queryProjectName(query) {
      const q = (query || '').toLowerCase()
      this.filteredProjectNames = q
        ? this.projectList
            .filter(
              (p) => p.projectName && p.projectName.toLowerCase().includes(q)
            )
            .slice(0, 20)
        : this.projectList.slice(0, 20)
    },
    handleQuery() {
      this.qp.pageNum = 1
      this.loadFunctions()
    },
    resetQuery() {
      this.qp = {
        pageNum: 1,
        pageSize: this.qp.pageSize,
        scope: '',
        projectCode: '',
        projectName: '',
        implType: '',
        funcCode: '',
        funcLabel: '',
      }
      clearPageState(projectPageStateKey('FunctionList', this.currentProjectId))
      this.loadFunctions()
    },
    async loadFunctions() {
      this.loading = true
      this.functionLoadError = ''
      try {
        this.saveCachedState()
        const params = { ...this.qp }
        if (this.currentProjectId) params.projectId = this.currentProjectId
        if (!params.scope) delete params.scope
        if (!params.projectCode) delete params.projectCode
        if (!params.projectName) delete params.projectName
        if (!params.implType) delete params.implType
        if (!params.funcCode) delete params.funcCode
        if (!params.funcLabel) delete params.funcLabel
        const res = await listFunctions(params)
        this.funcList = (res && res.data && res.data.records) || []
        this.total = (res && res.data && res.data.total) || 0
      } catch (e) {
        this.funcList = []
        this.total = 0
        this.functionLoadError = this.errorMessage(e, '加载函数列表失败')
      } finally {
        this.loading = false
      }
    },
    onPageChange(p) {
      this.qp.pageNum = p
      this.loadFunctions()
    },
    /** 每页条数变更 */
    onSizeChange(s) {
      this.qp.pageSize = s
      this.qp.pageNum = 1
      this.loadFunctions()
    },
    parseParams(json) {
      try {
        return JSON.parse(json)
      } catch (e) {
        return []
      }
    },
    errorMessage(error, fallback) {
      const responseMessage = error?.response?.data?.message
      const message = responseMessage || error?.message
      return typeof message === 'string' && message.trim()
        ? message.trim()
        : fallback
    },
    notifyOperationError(error, fallback) {
      if (!isRequestErrorNotified(error)) {
        this.$message.error(this.errorMessage(error, fallback))
      }
    },
    isDialogCancelled(error) {
      return error === 'cancel' || error === 'close'
    },
    formatUpdateTime(value) {
      if (!value) return '—'
      return String(value).replace('T', ' ').slice(0, 19)
    },
    typeLabel: varTypeLabel,
    scopeLabel(scope) {
      return { GLOBAL: '全局', PROJECT: '项目级' }[scope] || '项目级'
    },
    implTypeLabel(type) {
      return { SCRIPT: '脚本', JAVA: 'Java类', BEAN: 'Bean' }[type] || type
    },
    implTypeTagType(type) {
      return { SCRIPT: '', JAVA: 'warning', BEAN: 'success' }[type] || 'info'
    },
    handleCreate() {
      this.resetMethodEditor()
      this.editForm = {
        funcCode: '',
        funcName: '',
        description: '',
        returnType: 'STRING',
        implType: 'SCRIPT',
        implScript: '',
        implClass: '',
        implMethod: '',
        implBeanName: '',
        status: 1,
        projectId: this.currentProjectId,
        scope: this.currentProjectId ? 'PROJECT' : 'GLOBAL',
      }
      this.editParams = [{ name: '', type: 'STRING', label: '' }]
      this.originalBindingKey = this.functionDeclarationKey()
      this.dialogVisible = true
    },
    handleEdit(row) {
      this.resetMethodEditor()
      this.editForm = { ...row, scope: row.scope || 'PROJECT' }
      try { this.editParams = parseFunctionParameters(row.paramsJson) }
      catch (error) { this.editParams = []; this.parameterConfigError = error.message }
      if (this.editParams.length === 0 && !this.isNativeFunction)
        this.editParams = [{ name: '', type: 'STRING', label: '' }]
      this.originalBindingKey = this.functionDeclarationKey()
      this.dialogVisible = true
    },
    handleTestFunction(row) {
      this.functionTestTarget = row || {}
      this.functionTestParamsText = this.buildFunctionTestParamsTemplate(row)
      this.functionTestResultText = ''
      this.functionTestVisible = true
    },
    buildFunctionTestParamsTemplate(row) {
      const params = this.parseParams(row && row.paramsJson)
      const sample = {}
      params.forEach((item) => {
        if (item && item.name)
          sample[item.name] = this.sampleValueForFunctionParam(item)
      })
      return JSON.stringify(sample, null, 2)
    },
    sampleValueForFunctionParam(item) {
      if (item && Object.prototype.hasOwnProperty.call(item, 'example')) {
        try {
          return JSON.parse(JSON.stringify(item.example))
        } catch (e) {
          return item.example
        }
      }
      return sampleValueForVarType(item && item.type)
    },
    normalizeFunctionTestParams(params) {
      const code = this.functionTestTarget && this.functionTestTarget.funcCode
      const isRandomFunction = code === 'randomInt' || code === 'randomDecimal'
      if (
        !isRandomFunction ||
        !params ||
        typeof params !== 'object' ||
        Array.isArray(params)
      )
        return params
      const keys = Object.keys(params)
      if (keys.length === 0) {
        return { lower: 0, upper: 1, includeLower: true, includeUpper: true }
      }
      const hasOwn = (key) => Object.prototype.hasOwnProperty.call(params, key)
      if (
        hasOwn('lower') &&
        hasOwn('upper') &&
        !hasOwn('includeLower') &&
        !hasOwn('includeUpper')
      ) {
        return { ...params, includeLower: true, includeUpper: true }
      }
      return params
    },
    async doTestFunction() {
      if (!this.functionTestTarget || !this.functionTestTarget.id) return
      let params
      try {
        params = this.functionTestParamsText
          ? JSON.parse(this.functionTestParamsText)
          : {}
        params = this.normalizeFunctionTestParams(params)
      } catch (e) {
        this.$message.error('测试入参不是合法 JSON')
        return
      }
      this.functionTesting = true
      try {
        const res = await testFunction(this.functionTestTarget.id, params)
        this.functionTestResultText = JSON.stringify(
          (res && res.data) || res || {},
          null,
          2
        )
        this.$message.success('测试完成')
      } catch (e) {
        this.functionTestResultText = JSON.stringify(
          { success: false, errorMessage: e.message || '测试失败' },
          null,
          2
        )
      } finally {
        this.functionTesting = false
      }
    },
    onScopeChange(val) {
      if (val === 'GLOBAL') {
        this.editForm.projectId = 0
      }
    },
    async handleSave() {
      if (this.functionSaving) return
      if (!this.editForm.funcCode || !this.editForm.funcName) {
        this.$message.warning('请填写函数编码和名称')
        return
      }
      if (this.editForm.scope === 'PROJECT' && !this.editForm.projectId) {
        this.$message.warning('请选择所属项目')
        return
      }
      const session = this.editSessionId
      const key = this.nativeBindingKey
      const snapshotKey = JSON.stringify([this.editForm, this.editParams])
      let params
      try {
        if (this.parameterConfigError) throw new Error(this.parameterConfigError)
        params = validatedFunctionParameters(this.editParams)
      } catch (error) { this.$message.warning(error.message); return }
      const payload = { ...this.editForm, projectId: this.editForm.scope === 'GLOBAL' ? 0 : this.editForm.projectId, paramsJson: JSON.stringify(params) }
      this.functionSaving = true
      try {
        if (this.isNativeFunction) {
          const unchangedLegacyBinding = this.editForm.id && this.functionDeclarationKey() === this.originalBindingKey
          if (!unchangedLegacyBinding && (!payload.implMethod?.trim() || params.some(param => !param.javaType)))
            throw new Error('请选择实际方法，或明确填写方法名及每个参数的实际方法类型')
          const inspected = await inspectFunctionMethods(payload)
          if (session !== this.editSessionId || !this.dialogVisible) return
          if (snapshotKey !== JSON.stringify([this.editForm, this.editParams])) {
            this.$message.warning('配置已变化，请再次保存以核对新配置'); return
          }
          const catalog = inspected.data
          this.methodCatalog = catalog; this.methodCatalogKey = key; this.methodCatalogTargetKey = this.nativeTargetKey
          if (!catalog || !Array.isArray(catalog.methods) || !['AVAILABLE', 'UNAVAILABLE'].includes(catalog.status)) {
            throw new Error(catalog?.message || '方法签名尚未核对，请重新读取方法')
          }
          if (catalog.status === 'AVAILABLE' && (catalog.valid !== true || !catalog.signature)) throw new Error(catalog.message || '实际方法签名不匹配')
          const declared = explicitFunctionSignature(payload, params)
          if (catalog.status === 'AVAILABLE' && declared && catalog.signature !== declared) throw new Error('返回的方法签名与当前选择不一致，请重新读取方法')
          if (catalog.status === 'UNAVAILABLE' && !unchangedLegacyBinding
            && (!payload.implMethod?.trim() || params.some(param => !param.javaType))) {
            throw new Error('目标运行端尚未安装此实现，请填写方法名及每个参数的实际类型后再送审')
          }
        }
        const response = payload.id ? await updateFunction(payload) : await createFunction(payload)
        if (session !== this.editSessionId || !this.dialogVisible) return
        this.dialogVisible = false
        await completeResourceChange(this, response, () => this.loadFunctions())
      } catch (e) {
        if (session === this.editSessionId && this.dialogVisible) this.notifyOperationError(e, '保存函数失败')
      } finally { if (session === this.editSessionId) this.functionSaving = false }
    },
    async openVersionDialog(row) {
      this.versionRow = row
      this.versionVisible = true
      this.versionCompare = null
      this.versionPreview = ''
      const res = await listVersions(row.id)
      this.versionList = (res && res.data) || []
      this.versionCompareLeft = this.versionList[1]?.version || null
      this.versionCompareRight = this.versionList[0]?.version || null
    },
    viewVersion(row) {
      this.versionCompare = null
      this.versionPreview = this.prettyVersionJson(row.functionJson)
    },
    async compareWithNext(row, index) {
      const right = this.versionList[index + 1]
      if (!this.versionRow || !right) return
      this.versionCompareLeft = row.version
      this.versionCompareRight = right.version
      await this.compareSelectedVersions()
    },
    async compareSelectedVersions() {
      if (
        !this.versionRow ||
        !this.versionCompareLeft ||
        !this.versionCompareRight
      )
        return
      const res = await compareVersions(
        this.versionRow.id,
        this.versionCompareLeft,
        this.versionCompareRight
      )
      this.versionCompare = (res && res.data) || null
      this.versionPreview = ''
    },
    async rollbackFunctionVersion(row) {
      if (!this.versionRow || !row) return
      const mode = await chooseVersionRestore(row.displayVersion || row.businessVersion || row.version)
      if (!mode) return
      const response = await rollbackVersion(
        this.versionRow.id,
        row.version, mode
      )
      this.versionVisible = false
      await completeResourceChange(this, response, () => this.loadFunctions())
      await this.editWorkspace('FUNCTION', this.versionRow, this.handleEdit)
    },
    prettyVersionJson(text) {
      if (!text) return ''
      try {
        return JSON.stringify(JSON.parse(text), null, 2)
      } catch (e) {
        return String(text)
      }
    },
    async handleDelete(row) {
      try {
        await this.$confirm('确认删除函数「' + row.funcName + '」？', '提示', {
          type: 'warning',
        })
        const response = await deleteFunction(row.id)
        await completeResourceChange(this, response, () => this.loadFunctions())
      } catch (e) {
        if (!this.isDialogCancelled(e)) {
          this.notifyOperationError(e, '删除函数失败')
        }
      }
    },
  },
}
</script>

<style scoped>
.uiue-list-page {
  padding: 16px;
}
.linkage-hint {
  font-size: 13px;
  color: var(--tianshu-text-tertiary);
  margin-bottom: 12px;
  background: var(--tianshu-bg-muted);
  padding: 8px 12px;
  border-radius: 4px;
}
.function-context-hint {
  margin: 0 0 12px;
  padding: 8px 12px;
  color: var(--tianshu-text-secondary);
  background: var(--tianshu-bg-soft);
  border-left: 2px solid var(--el-color-primary);
  border-radius: 4px;
  font-size: 13px;
}
.function-form-help {
  width: 100%;
  margin-bottom: 8px;
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
  line-height: 1.6;
}
.function-page-error {
  padding: 8px 12px;
  margin-bottom: 12px;
  color: var(--el-color-error);
  font-size: 13px;
  background: var(--el-color-error-light-9);
  border: 1px solid var(--el-color-error-light-7);
  border-radius: 4px;
}
.function-empty {
  padding: 40px;
  color: var(--tianshu-text-tertiary);
  text-align: center;
}
.mono-input :deep(textarea) {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}
.function-test-target {
  margin-bottom: 12px;
  color: var(--tianshu-text-secondary);
  font-size: 13px;
}
.function-method-target { display: flex; gap: 8px; width: 100%; }
.function-method-target .el-input { min-width: 0; flex: 1; }
.function-method-selection, .function-parameters { width: 100%; min-width: 0; }
.function-method-help { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; color: var(--tianshu-text-secondary); font-size: 12px; line-height: 1.6; margin-top: 6px; overflow-wrap: anywhere; }
.function-parameter-row { display: grid; grid-template-columns: minmax(90px, 1fr) minmax(130px, 1fr) minmax(90px, 1fr) 28px; gap: 6px; margin-bottom: 10px; }
.function-parameter-row > * { min-width: 0; }
.function-parameter-java-type { grid-column: 1 / -1; display: flex; flex-wrap: wrap; gap: 8px; align-items: center; color: var(--tianshu-text-secondary); font-size: 12px; }
.function-parameter-java-type code { overflow-wrap: anywhere; }
.function-parameter-java-type .el-select { flex: 1; min-width: 180px; }
@media (max-width: 640px) {
  .function-parameter-row { grid-template-columns: minmax(0, 1fr) 28px; }
  .function-parameter-row > .el-input, .function-parameter-row > .el-select { grid-column: 1; }
  .function-parameter-row > .el-button { grid-column: 2; grid-row: 1; }
  .function-parameter-java-type .el-select { width: 100%; min-width: 0; flex-basis: 100%; }
}
.version-compare {
  margin-top: 10px;
}
.version-compare-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.version-compare-toolbar .el-select {
  width: 150px;
}
.version-compare-title {
  font-weight: 600;
  color: var(--tianshu-text-primary);
}
.version-preview {
  margin-top: 10px;
  padding: 10px;
  max-height: 320px;
  overflow: auto;
  background: var(--tianshu-bg-soft);
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
}
</style>
