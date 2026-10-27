<template>
  <div class="uiue-list-page datasource-page management-list-page">
    <div class="module-hint">
      <div class="hint-title">外数管理</div>
      <div class="hint-text">
        集中配置第三方
        API、鉴权流程、入参与响应映射、同步/异步调用、超时和重试策略，供接口变量使用。
      </div>
    </div>
    <div class="usage-guide">
      <div
        v-for="item in apiGuideTemplates"
        :key="item.title"
        class="guide-item"
      >
        <div class="guide-title">{{ item.title }}</div>
        <div class="guide-text">{{ item.text }}</div>
      </div>
    </div>

    <el-tabs class="management-table-region" v-model="activeTab" @tab-click="onTabChange">
      <el-tab-pane class="management-table-region" label="数据源" name="datasource">
        <div class="uiue-search-container uiue-filter-toolbar">
          <el-form
            :inline="true"
            size="small"
            @keyup.enter="handleDatasourceQuery"
          >
            <el-form-item label="项目编码">
              <project-filter-select
                v-model:value="datasourceQuery.projectCode"
                field="projectCode"
                placeholder="输入项目编码"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="项目名称">
              <project-filter-select
                v-model:value="datasourceQuery.projectName"
                field="projectName"
                placeholder="输入项目名称"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="作用范围">
              <el-select
                v-model="datasourceQuery.scope"
                clearable
                placeholder="全部"
                style="width: 110px"
              >
                <el-option label="全局" value="GLOBAL" />
                <el-option label="项目" value="PROJECT" />
              </el-select>
            </el-form-item>
            <el-form-item label="数据源编码">
              <remote-filter-select
                v-model:value="datasourceQuery.datasourceCode"
                :fetch-options="fetchDatasourceCodeOptions"
                option-label-key="datasourceCode"
                option-value-key="datasourceCode"
                allow-free-input
                placeholder="前缀筛选"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="数据源名称">
              <remote-filter-select
                v-model:value="datasourceQuery.datasourceName"
                :fetch-options="fetchDatasourceNameOptions"
                option-label-key="datasourceName"
                option-value-key="datasourceName"
                allow-free-input
                placeholder="名称筛选"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="鉴权方式">
              <el-select
                v-model="datasourceQuery.authType"
                clearable
                placeholder="全部"
                style="width: 130px"
              >
                <el-option
                  v-for="item in authTypeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select
                v-model="datasourceQuery.status"
                clearable
                placeholder="全部"
                style="width: 100px"
              >
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleDatasourceQuery"
                >查询</el-button
              >
              <el-button @click="resetDatasourceQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <div class="uiue-btn-bar">
            <div class="btn-right">
          <table-column-settings v-model="tableColumnOrders.datasourceColumns" :columns="datasourceColumns" storage-key="tianshu:table-columns:datasource-list" />
              <governance-workspace-toolbar resource-type="EXTERNAL_DATASOURCE" />
              <el-button
                v-permission="'datasource:edit'"
                type="primary"
                size="small"
                :icon="ElIconPlus"
                @click="handleCreateDatasource"
                >新建数据源</el-button
              >
            </div>
          </div>
        </div>

        <el-table class="management-table" show-overflow-tooltip
          :data="datasourceList"
          border
          size="small"
          :aria-busy="datasourceLoading"
          style="width: 100%"
        >
          <template v-for="columnKey in (tableColumnOrders.datasourceColumns || datasourceColumns.map(column => column.key))" :key="columnKey">
          <el-table-column v-if="columnKey === 'scope'" label="作用范围" width="90" align="center">
            <template v-slot="{ row }">
              <el-tag
                :type="row.scope === 'GLOBAL' ? 'warning' : 'success'"
                size="small"
                >{{ scopeLabel(row.scope) }}</el-tag
              >
            </template>
          </el-table-column>
          <el-table-column v-if="columnKey === 'projectName'"
            prop="projectName"
            label="项目名称"
            min-width="120"
            show-overflow-tooltip
          >
            <template v-slot="{ row }">{{ row.projectName || '—' }}</template>
          </el-table-column>
          <el-table-column v-if="columnKey === 'datasourceCode'"
            prop="datasourceCode"
            label="数据源编码"
            min-width="140"
            show-overflow-tooltip
          />
          <el-table-column v-if="columnKey === 'datasourceName'"
            prop="datasourceName"
            label="数据源名称"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column v-if="columnKey === 'providerName'"
            prop="providerName"
            label="提供方"
            min-width="120"
            show-overflow-tooltip
          />
          <el-table-column v-if="columnKey === 'protocol'" label="协议" width="110" align="center">
            <template v-slot="{ row }">
              <el-tag size="small" type="info">{{
                optionLabel(protocolOptions, row.protocol)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="columnKey === 'baseUrl'"
            prop="baseUrl"
            label="基础地址"
            min-width="220"
            show-overflow-tooltip
          />
          <el-table-column v-if="columnKey === 'authType'" label="鉴权方式" width="110" align="center">
            <template v-slot="{ row }">
              <el-tag size="small">{{
                optionLabel(authTypeOptions, row.authType)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="columnKey === 'status'" prop="status" label="状态" width="160" align="center">
            <template v-slot="{ row }">
              <governance-resource-status resource-type="EXTERNAL_DATASOURCE" :resource-id="row.id" :status="row.status"><el-tag
                :type="row.status === 1 ? 'success' : 'info'"
                size="small"
                >{{ row.status === 1 ? '启用' : '停用' }}</el-tag
              ></governance-resource-status>
            </template>
          </el-table-column>
          </template>
          <el-table-column class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" :width="operationColumnWidth(['编辑', '测试', '版本', '发布', '加接口', '删除'])" align="center" fixed="right">
            <template v-slot="{ row }">
              <el-button
                v-permission="{ code: 'datasource:edit', projectId: (row.projectId ?? row.ownerProjectId), scope: (row.scope ?? row.ownerScope) }"
                link
                data-action="edit"
                size="small"
                type="warning"
                @click="handleEditDatasource(row)"
                >编辑</el-button
              >
              <el-button v-permission="{ code: 'datasource:edit', projectId: (row.projectId ?? row.ownerProjectId), scope: (row.scope ?? row.ownerScope) }"
                link
                data-action="execute"
                size="small"
                type="success"
                @click="handleTestDatasource(row)"
                >测试</el-button
              >
              <governance-publish-action resource-type="EXTERNAL_DATASOURCE" :resource-id="row.id" />
              <el-button v-permission="{ code: 'datasource:edit', projectId: (row.projectId ?? row.ownerProjectId), scope: (row.scope ?? row.ownerScope) }"
                link
                data-action="add"
                size="small"
                type="primary"
                :disabled="row.status !== 1"
                :title="row.status === 1 ? '配置接口' : '请先发布数据源，再配置接口'"
                @click="handleCreateApi(row)"
                >加接口</el-button
              >
              <el-button
                v-permission="{ code: 'datasource:edit', projectId: (row.projectId ?? row.ownerProjectId), scope: (row.scope ?? row.ownerScope) }"
                link
                data-action="delete"
                size="small"
                type="danger"
                class="btn-delete"
                @click="handleDeleteDatasource(row)"
                >删除</el-button
              >
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          :current-page="datasourceQuery.pageNum"
          :page-size="datasourceQuery.pageSize"
          :total="datasourceTotal"
          layout="total,sizes,prev,pager,next"
          :page-sizes="[10, 20, 30, 50, 100, 200, 500]"
          @current-change="
            (p) => {
              datasourceQuery.pageNum = p
              loadDatasources()
            }
          "
          @size-change="
            (s) => {
              datasourceQuery.pageSize = s
              datasourceQuery.pageNum = 1
              loadDatasources()
            }
          "
        />
      </el-tab-pane>

      <el-tab-pane class="management-table-region" label="API 接口" name="api">
        <div class="uiue-search-container uiue-filter-toolbar">
          <el-form :inline="true" size="small" @keyup.enter="handleApiQuery">
            <el-form-item label="项目编码">
              <project-filter-select
                v-model:value="apiQuery.projectCode"
                field="projectCode"
                placeholder="输入项目编码"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="项目名称">
              <project-filter-select
                v-model:value="apiQuery.projectName"
                field="projectName"
                placeholder="输入项目名称"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="数据源编码">
              <remote-filter-select
                v-model:value="apiQuery.datasourceCode"
                :fetch-options="fetchApiDatasourceCodeOptions"
                option-label-key="datasourceCode"
                option-value-key="datasourceCode"
                allow-free-input
                placeholder="前缀筛选"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="接口编码">
              <remote-filter-select
                v-model:value="apiQuery.apiCode"
                :fetch-options="fetchApiCodeOptions"
                option-label-key="apiCode"
                option-value-key="apiCode"
                allow-free-input
                placeholder="前缀筛选"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="接口名称">
              <remote-filter-select
                v-model:value="apiQuery.apiName"
                :fetch-options="fetchApiNameOptions"
                option-label-key="apiName"
                option-value-key="apiName"
                allow-free-input
                placeholder="名称筛选"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="调用模式">
              <el-select
                v-model="apiQuery.requestMode"
                clearable
                placeholder="全部"
                style="width: 110px"
              >
                <el-option label="同步" value="SYNC" />
                <el-option label="异步" value="ASYNC" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select
                v-model="apiQuery.status"
                clearable
                placeholder="全部"
                style="width: 100px"
              >
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleApiQuery">查询</el-button>
              <el-button @click="resetApiQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <div class="uiue-btn-bar">
            <div class="btn-right">
          <table-column-settings v-model="tableColumnOrders.apiColumns" :columns="apiColumns" storage-key="tianshu:table-columns:api-list" />
              <governance-workspace-toolbar resource-type="EXTERNAL_API" />
              <el-button
                v-permission="'datasource:edit'"
                type="primary"
                size="small"
                :icon="ElIconPlus"
                @click="handleCreateApi()"
                >新建接口</el-button
              >
            </div>
          </div>
        </div>

        <el-table class="management-table" show-overflow-tooltip
          :data="apiList"
          border
          size="small"
          :aria-busy="apiLoading"
          style="width: 100%"
        >
          <template v-for="columnKey in (tableColumnOrders.apiColumns || apiColumns.map(column => column.key))" :key="columnKey">
          <el-table-column v-if="columnKey === 'datasourceCode'"
            prop="datasourceCode"
            label="数据源"
            min-width="120"
            show-overflow-tooltip
          />
          <el-table-column v-if="columnKey === 'apiCode'"
            prop="apiCode"
            label="接口编码"
            min-width="140"
            show-overflow-tooltip
          />
          <el-table-column v-if="columnKey === 'apiName'"
            prop="apiName"
            label="接口名称"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column v-if="columnKey === 'request'" label="请求" min-width="180" show-overflow-tooltip>
            <template v-slot="{ row }">
              <el-tag size="small" type="info">{{ row.requestMethod }}</el-tag>
              <span class="endpoint-text">{{ row.endpointUrl }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="columnKey === 'mode'" label="模式" width="80" align="center">
            <template v-slot="{ row }">
              <el-tag
                :type="row.requestMode === 'ASYNC' ? 'warning' : 'success'"
                size="small"
                >{{ row.requestMode === 'ASYNC' ? '异步' : '同步' }}</el-tag
              >
            </template>
          </el-table-column>
          <el-table-column v-if="columnKey === 'auth'" label="鉴权" width="110" align="center">
            <template v-slot="{ row }">
              <el-tag size="small">{{
                optionLabel(authModeOptions, row.authMode)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="columnKey === 'timeout'" label="超时/重试" width="120" align="center">
            <template v-slot="{ row }"
              >{{ row.timeoutMs }}ms / {{ row.retryCount || 0 }}次</template
            >
          </el-table-column>
          <el-table-column v-if="columnKey === 'cache'" label="响应缓存" width="100" align="center">
            <template v-slot="{ row }">{{
              formatCacheSeconds(row.responseCacheSeconds)
            }}</template>
          </el-table-column>
          <el-table-column v-if="columnKey === 'status'" prop="status" label="状态" width="160" align="center">
            <template v-slot="{ row }">
              <governance-resource-status resource-type="EXTERNAL_API" :resource-id="row.id" :status="row.status"><el-tag
                :type="row.status === 1 ? 'success' : 'info'"
                size="small"
                >{{ row.status === 1 ? '启用' : '停用' }}</el-tag
              ></governance-resource-status>
            </template>
          </el-table-column>
          </template>
          <el-table-column class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" :width="operationColumnWidth(['编辑', '测试', '版本', '发布', '删除'])" align="center" fixed="right">
            <template v-slot="{ row }">
              <el-button
                v-permission="{ code: 'datasource:edit', projectId: (row.projectId ?? row.ownerProjectId), scope: (row.scope ?? row.ownerScope) }"
                link
                data-action="edit"
                size="small"
                type="warning"
                @click="handleEditApi(row)"
                >编辑</el-button
              >
              <el-button v-permission="{ code: 'datasource:edit', projectId: (row.projectId ?? row.ownerProjectId), scope: (row.scope ?? row.ownerScope) }"
                link
                data-action="execute"
                size="small"
                type="success"
                @click="handleInvokeApi(row)"
                >测试</el-button
              >
              <governance-publish-action resource-type="EXTERNAL_API" :resource-id="row.id" />
              <el-button
                v-permission="{ code: 'datasource:edit', projectId: (row.projectId ?? row.ownerProjectId), scope: (row.scope ?? row.ownerScope) }"
                link
                data-action="delete"
                size="small"
                type="danger"
                class="btn-delete"
                @click="handleDeleteApi(row)"
                >删除</el-button
              >
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          :current-page="apiQuery.pageNum"
          :page-size="apiQuery.pageSize"
          :total="apiTotal"
          layout="total,sizes,prev,pager,next"
          :page-sizes="[10, 20, 30, 50, 100, 200, 500]"
          @current-change="
            (p) => {
              apiQuery.pageNum = p
              loadApiConfigs()
            }
          "
          @size-change="
            (s) => {
              apiQuery.pageSize = s
              apiQuery.pageNum = 1
              loadApiConfigs()
            }
          "
        />
      </el-tab-pane>
      <el-tab-pane class="management-table-region" label="监控看板" name="monitor" lazy>
        <external-api-monitor />
      </el-tab-pane>
      <el-tab-pane class="management-table-region" label="调用日志" name="logs" lazy>
        <module-call-log class="management-table-region" module-type="DATASOURCE" title="外数调用日志" />
      </el-tab-pane>
    </el-tabs>



    <el-dialog
      title="数据源鉴权测试"
      v-model="authTestDialogVisible"
      width="780px"
      append-to-body
    >
      <div class="invoke-target">
        当前数据源：{{ authTestTarget.datasourceName }} /
        {{ authTestTarget.datasourceCode }}
      </div>
      <el-alert
        type="info"
        :closable="false"
        title="测试会按数据源鉴权配置请求 Token 接口；Token 在响应头时，tokenPath 可写 headers.Authorization。"
        style="margin-bottom: 12px"
      />
      <el-form label-width="90px" size="small">
        <el-form-item label="测试参数">
          <monaco-editor
            v-model:value="authTestParamsText"
            language="json"
            height="170px"
          />
        </el-form-item>
        <el-form-item label="测试结果">
          <monaco-editor
            v-model:value="authTestResultText"
            language="json"
            height="240px"
            read-only
          />
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <div>
          <el-button size="small" @click="authTestDialogVisible = false"
            >关闭</el-button
          >
          <el-button v-permission="'datasource:edit'"
            size="small"
            type="primary"
            :loading="authTestLoading"
            @click="runAuthTest"
            >执行测试</el-button
          >
        </div>
      </template>
    </el-dialog>



    <el-dialog
      title="API 调用测试"
      v-model="invokeDialogVisible"
      width="760px"
      append-to-body
    >
      <div class="invoke-target">
        当前接口：{{ invokeTarget.apiName }} / {{ invokeTarget.apiCode }}
      </div>
      <el-form label-width="90px" size="small">
        <el-form-item label="请求参数">
          <monaco-editor
            v-model:value="invokeParamsText"
            language="json"
            height="220px"
          />
        </el-form-item>
        <el-form-item label="调用结果">
          <monaco-editor
            v-model:value="invokeResultText"
            language="json"
            height="220px"
            read-only
          />
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <div>
          <el-button size="small" @click="invokeDialogVisible = false"
            >关闭</el-button
          >
          <el-button v-permission="'datasource:edit'"
            size="small"
            type="primary"
            :loading="invokeLoading"
            @click="runInvokeApi"
            >执行调用</el-button
          >
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import GovernancePublishAction from '@/components/GovernancePublishAction.vue'
import GovernanceWorkspaceToolbar from '@/components/GovernanceWorkspaceToolbar.vue'
import GovernanceResourceStatus from '@/components/GovernanceResourceStatus.vue'
import { completeResourceChange } from '@/utils/governanceWorkspace'
import { markRaw } from 'vue'
import { Plus as ElIconPlus } from '@element-plus/icons-vue'
import {
  deleteApiConfig,
  deleteDatasource,
  listApiConfigs,
  listDatasources,
  invokeApiConfig,
  testDatasourceAuth,
} from '@/api/datasource'
import { getProject, listProjects } from '@/api/project'
import { collectOperandInputPaths, setPathValue } from '@/utils/testParamTemplate'
import ModuleCallLog from '@/components/common/ModuleCallLog.vue'
import ExternalApiMonitor from '@/components/common/ExternalApiMonitor.vue'
import MonacoEditor from '@/components/MonacoEditor'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import TableColumnSettings from '@/components/common/TableColumnSettings.vue'
import { routeProjectId } from '@/utils/projectContext'
import { operationColumnWidth as getOperationColumnWidth } from '@/utils/operationColumnWidth'

export default {
  data() {
    return {
      tableColumnOrders: { datasourceColumns: null, apiColumns: null },
      datasourceColumns: [
        { key: 'scope', label: '作用范围' },
        { key: 'projectName', label: '项目名称' },
        { key: 'datasourceCode', label: '数据源编码' },
        { key: 'datasourceName', label: '数据源名称' },
        { key: 'providerName', label: '提供方' },
        { key: 'protocol', label: '协议' },
        { key: 'baseUrl', label: '基础地址' },
        { key: 'authType', label: '鉴权方式' },
        { key: 'status', label: '状态' },
      ],
      apiColumns: [
        { key: 'datasourceCode', label: '数据源' },
        { key: 'apiCode', label: '接口编码' },
        { key: 'apiName', label: '接口名称' },
        { key: 'request', label: '请求' },
        { key: 'mode', label: '模式' },
        { key: 'auth', label: '鉴权' },
        { key: 'timeout', label: '超时/重试' },
        { key: 'cache', label: '响应缓存' },
        { key: 'status', label: '状态' },
      ],
      apiGuideTemplates: [
        {
          title: 'HTTP 外数模板',
          text: '先配置数据源地址与鉴权，再定义 API 请求字段、条件分支和响应结构。异步接口可编排多步请求、轮询与回调。',
        },
        {
          title: '内部规则模板',
          text: '协议选择内部规则引擎，endpointUrl 填已发布 ruleCode；在请求字段中定义 params 的子字段，并通过内容选择器绑定取值。',
        },
        {
          title: '变量与对象取值',
          text: '变量或数据对象选择 API，按结果路径取值；只需覆盖接口开放的入参。大响应可整体绑定，无需逐字段重复映射。',
        },
      ],
      activeTab: 'datasource',
      contextProjectId: null,
      projects: [],
      datasourceList: [],
      datasourceOptions: [],
      datasourceTotal: 0,
      datasourceLoading: false,
      datasourceQuery: {
        pageNum: 1,
        pageSize: 20,
        projectId: '',
        projectCode: '',
        projectName: '',
        scope: '',
        datasourceCode: '',
        datasourceName: '',
        authType: '',
        status: '',
      },
      authTestDialogVisible: false,
      authTestLoading: false,
      authTestTarget: {},
      authTestParamsText: '{}',
      authTestResultText: '',
      apiList: [],
      apiTotal: 0,
      apiLoading: false,
      apiQuery: {
        pageNum: 1,
        pageSize: 20,
        projectId: '',
        projectCode: '',
        projectName: '',
        datasourceCode: '',
        apiCode: '',
        apiName: '',
        requestMode: '',
        status: '',
      },
      invokeDialogVisible: false,
      invokeLoading: false,
      invokeTarget: {},
      invokeParamsText: '{}',
      invokeResultText: '',
      authTypeOptions: [
        { label: '无', value: 'NONE' },
        { label: 'Basic', value: 'BASIC' },
        { label: 'Bearer', value: 'BEARER' },
        { label: 'API Key', value: 'API_KEY' },
        { label: 'OAuth2', value: 'OAUTH2' },
        { label: 'Token接口', value: 'TOKEN_API' },
        { label: '自定义', value: 'CUSTOM' },
      ],
      authModeOptions: [
        { label: '继承数据源', value: 'INHERIT' },
        { label: '无', value: 'NONE' },
        { label: 'Basic', value: 'BASIC' },
        { label: 'Bearer', value: 'BEARER' },
        { label: 'API Key', value: 'API_KEY' },
        { label: 'OAuth2', value: 'OAUTH2' },
        { label: 'Token接口', value: 'TOKEN_API' },
        { label: '自定义', value: 'CUSTOM' },
      ],
      protocolOptions: [
        { label: 'HTTP', value: 'HTTP' },
        { label: 'HTTPS', value: 'HTTPS' },
        { label: '内部规则引擎', value: 'RULE_ENGINE' },
      ],
      ElIconPlus: markRaw(ElIconPlus),
    }
  },
  name: 'DatasourceList',
  components: { GovernancePublishAction, GovernanceWorkspaceToolbar,
    GovernanceResourceStatus,
    TableColumnSettings,
    ModuleCallLog,
    ExternalApiMonitor,
    MonacoEditor,
    RemoteFilterSelect,
    ProjectFilterSelect,
  },
  async created() {
    const requestedTab = this.$route && this.$route.query && this.$route.query.tab
    if (['datasource', 'api', 'monitor', 'logs'].includes(requestedTab)) {
      this.activeTab = requestedTab
    }
    const contextProjectId = routeProjectId(
      this.$route,
      this.$store && this.$store.state.currentProject
    )
    let contextReady = true
    if (contextProjectId) {
      try {
        const response = await getProject(contextProjectId)
        if (!response.data) throw new Error('项目不存在')
        this.applyProjectContext(response.data)
      } catch (e) {
        this.contextProjectId = Number(contextProjectId)
        contextReady = false
        this.$message.error(e.message || '加载项目范围失败')
      }
    }
    this.loadProjects()
    if (!contextReady) return
    this.loadDatasources()
    this.loadApiConfigs()
    this.loadDatasourceOptions()
  },
  methods: {
    operationColumnWidth(labels) { return getOperationColumnWidth(labels) },
    applyProjectContext(project) {
      if (!project || !project.id) return
      this.contextProjectId = Number(project.id)
      this.datasourceQuery.projectId = this.contextProjectId
      this.apiQuery.projectId = this.contextProjectId
      if (project.projectCode) this.apiQuery.projectCode = project.projectCode
    },
    async loadProjects() {
      try {
        const res = await listProjects({ pageNum: 1, pageSize: 500, status: 1 })
        this.projects = (res.data && res.data.records) || []
      } catch (e) {
        this.projects = []
      }
    },
    async loadDatasources() {
      this.datasourceLoading = true
      try {
        const params = this.cleanParams({ ...this.datasourceQuery })
        const res = await listDatasources(params)
        this.datasourceList = (res.data && res.data.records) || []
        this.datasourceTotal = (res.data && res.data.total) || 0
      } finally {
        this.datasourceLoading = false
      }
    },
    fetchDatasourceCodeOptions({ query, pageNum, pageSize }) {
      return listDatasources({
        ...this.datasourceQuery,
        pageNum,
        pageSize,
        datasourceCode: query || '',
      })
    },
    fetchDatasourceNameOptions({ query, pageNum, pageSize }) {
      return listDatasources({
        ...this.datasourceQuery,
        pageNum,
        pageSize,
        datasourceName: query || '',
      })
    },
    async loadDatasourceOptions() {
      const res = await listDatasources({
        pageNum: 1,
        pageSize: 500,
        status: 1,
        ...(this.contextProjectId
          ? { projectId: this.contextProjectId }
          : {}),
      })
      this.datasourceOptions = (res.data && res.data.records) || []
    },
    async loadApiConfigs() {
      this.apiLoading = true
      try {
        const params = this.cleanParams({ ...this.apiQuery })
        const res = await listApiConfigs(params)
        this.apiList = (res.data && res.data.records) || []
        this.apiTotal = (res.data && res.data.total) || 0
      } finally {
        this.apiLoading = false
      }
    },
    fetchApiDatasourceCodeOptions({ query, pageNum, pageSize }) {
      return listApiConfigs({
        ...this.apiQuery,
        pageNum,
        pageSize,
        datasourceCode: query || '',
      })
    },
    fetchApiCodeOptions({ query, pageNum, pageSize }) {
      return listApiConfigs({
        ...this.apiQuery,
        pageNum,
        pageSize,
        apiCode: query || '',
      })
    },
    fetchApiNameOptions({ query, pageNum, pageSize }) {
      return listApiConfigs({
        ...this.apiQuery,
        pageNum,
        pageSize,
        apiName: query || '',
      })
    },
    onTabChange() {
      if (this.activeTab === 'api') {
        this.loadDatasourceOptions()
        this.loadApiConfigs()
      } else if (this.activeTab === 'datasource') {
        this.loadDatasources()
      }
    },
    handleDatasourceQuery() {
      this.datasourceQuery.pageNum = 1
      this.loadDatasources()
    },
    resetDatasourceQuery() {
      this.datasourceQuery = {
        pageNum: 1,
        pageSize: this.datasourceQuery.pageSize,
        projectId: this.contextProjectId || '',
        projectCode: '',
        projectName: '',
        scope: '',
        datasourceCode: '',
        datasourceName: '',
        authType: '',
        status: '',
      }
      this.loadDatasources()
    },
    handleApiQuery() {
      this.apiQuery.pageNum = 1
      this.loadApiConfigs()
    },
    resetApiQuery() {
      this.apiQuery = {
        pageNum: 1,
        pageSize: this.apiQuery.pageSize,
        projectId: this.contextProjectId || '',
        projectCode: '',
        projectName: '',
        datasourceCode: '',
        apiCode: '',
        apiName: '',
        requestMode: '',
        status: '',
      }
      this.loadApiConfigs()
    },
    handleCreateDatasource() {
      this.$router.push({
        path: '/datasource/source/new',
        query: this.contextProjectId
          ? { projectId: this.contextProjectId }
          : {},
      })
    },
    handleEditDatasource(row) {
      this.$router.push('/datasource/source/' + row.id)
    },
    handleTestDatasource(row) {
      this.authTestTarget = row
      this.authTestParamsText = '{}'
      this.authTestResultText = ''
      this.authTestDialogVisible = true
    },
    async handleCreateApi(row) {
      const query = {
        ...(row && row.id ? { datasourceId: row.id } : {}),
        ...(this.contextProjectId
          ? { projectId: this.contextProjectId }
          : {}),
      }
      this.$router.push({ path: '/datasource/api/new', query })
    },
    async handleEditApi(row) {
      this.$router.push('/datasource/api/' + row.id)
    },
    handleInvokeApi(row) {
      this.invokeTarget = row
      this.invokeParamsText = this.buildApiInvokeParamTemplate(row)
      this.invokeResultText = ''
      this.invokeDialogVisible = true
    },
    buildApiInvokeParamTemplate(row) {
      const spec = this.parseConfigForTemplate(row.executionConfig)
      const saved = spec?.samples?.[0]?.params
      if (saved) return this.stringifyJson(saved)
      const sample = {}
      collectOperandInputPaths([spec?.requestFields, spec?.requestBranches, spec?.steps, this.parseConfigForTemplate(row.authApiConfig)]).forEach(path => setPathValue(sample, path, ''))
      return this.stringifyJson(sample)
    },
    parseConfigForTemplate(value) {
      if (!value) return null
      if (typeof value !== 'string') return value
      try {
        return JSON.parse(value)
      } catch (e) {
        return value
      }
    },
    async runInvokeApi() {
      let params
      try {
        params = this.invokeParamsText ? JSON.parse(this.invokeParamsText) : {}
      } catch (e) {
        this.$message.error('请求参数不是合法 JSON')
        return
      }
      this.invokeLoading = true
      try {
        const res = await invokeApiConfig(this.invokeTarget.id, params)
        this.invokeResultText = JSON.stringify(res.data || {}, null, 2)
        this.$message.success('调用完成')
      } finally {
        this.invokeLoading = false
      }
    },
    async runAuthTest() {
      let params
      try {
        params = this.authTestParamsText
          ? JSON.parse(this.authTestParamsText)
          : {}
      } catch (e) {
        this.$message.error('测试参数不是合法 JSON')
        return
      }
      this.authTestLoading = true
      try {
        const res = await testDatasourceAuth(this.authTestTarget.id, params)
        this.authTestResultText = JSON.stringify(res.data || {}, null, 2)
        this.$message.success('鉴权测试完成')
      } finally {
        this.authTestLoading = false
      }
    },
    handleDeleteDatasource(row) {
      this.$confirm(
        '确定删除外数数据源「' + row.datasourceName + '」？如有下游接口，请先删除下游接口。',
        '确认',
        { type: 'warning' }
      )
        .then(async () => {
          const response = await deleteDatasource(row.id)
          await completeResourceChange(this, response, () => this.loadDatasources())
        })
        .catch(() => {})
    },
    handleDeleteApi(row) {
      this.$confirm('确定删除接口「' + row.apiName + '」?', '确认', {
        type: 'warning',
      })
        .then(async () => {
          const response = await deleteApiConfig(row.id)
          await completeResourceChange(this, response, () => this.loadApiConfigs())
        })
        .catch(() => {})
    },
    stringifyJson(value) {
      return JSON.stringify(value, null, 2)
    },
    cleanParams(params) {
      Object.keys(params).forEach((key) => {
        if (
          params[key] === '' ||
          params[key] === null ||
          params[key] === undefined
        )
          delete params[key]
      })
      return params
    },
    scopeLabel(scope) {
      return scope === 'GLOBAL' ? '全局' : '项目'
    },
    optionLabel(options, value) {
      const item = options.find((opt) => opt.value === value)
      return item ? item.label : value || '—'
    },
    formatCacheSeconds(seconds) {
      const value = Number(seconds || 0)
      if (value <= 0) return '不缓存'
      if (value % 86400 === 0) return value / 86400 + '天'
      if (value % 3600 === 0) return value / 3600 + '小时'
      if (value % 60 === 0) return value / 60 + '分钟'
      return value + '秒'
    },
  },
}
</script>

<style lang="scss" scoped>
.datasource-page {
  .module-hint {
    background: #eff6ff;
    border: 1px solid #bfdbfe;
    border-radius: 4px;
    padding: 12px 14px;
    margin-bottom: 14px;
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .hint-title {
    color: #1d4ed8;
    font-weight: 700;
    white-space: nowrap;
  }

  .hint-text {
    color: var(--tianshu-text-secondary);
    line-height: 1.5;
  }

  .usage-guide {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 14px;
  }

  .guide-item {
    border: 1px solid var(--tianshu-border-subtle);
    border-radius: 4px;
    padding: 10px 12px;
    background: var(--tianshu-bg-surface);
  }

  .guide-title {
    color: var(--tianshu-text-primary);
    font-weight: 700;
    margin-bottom: 6px;
  }

  .guide-text,
  .field-help {
    color: var(--tianshu-text-tertiary);
    font-size: 12px;
    line-height: 1.6;
  }

  .field-help {
    margin-top: 4px;
  }

  .form-section {
    border: 1px solid #e5e7eb;
    border-radius: 4px;
    padding: 12px 12px 0;
    margin-bottom: 12px;
  }

  .section-title {
    color: #334155;
    font-weight: 700;
    margin-bottom: 6px;
  }

  .section-help,
  .template-help {
    color: var(--tianshu-text-tertiary);
    font-size: 12px;
    line-height: 1.6;
  }

  .section-help {
    margin-bottom: 10px;
  }

  .template-tabs {
    margin-bottom: 12px;
  }

  .template-help {
    margin-bottom: 8px;
  }

  .mapping-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    margin-bottom: 10px;
  }

  .invoke-target {
    color: var(--tianshu-text-secondary);
    margin-bottom: 12px;
  }

  .endpoint-text {
    margin-left: 6px;
    color: var(--tianshu-text-secondary);
  }

  .json-input :deep(textarea) {
    font-family: Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    line-height: 1.5;
  }

  code {
    color: #1e40af;
    background: #eff6ff;
    border-radius: 3px;
    padding: 0 4px;
  }
}
</style>
