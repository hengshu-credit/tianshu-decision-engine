<template>
  <div class="uiue-list-page datasource-page">
    <div class="module-hint">
      <div class="hint-title">外数管理</div>
      <div class="hint-text">集中配置第三方 API、鉴权流程、入参与响应映射、同步/异步调用、超时和重试策略，供接口变量使用。</div>
    </div>

    <el-tabs v-model="activeTab" @tab-click="onTabChange">
      <el-tab-pane label="数据源" name="datasource">
        <div class="uiue-search-container">
          <el-form :inline="true" size="small">
            <el-form-item label="作用范围">
              <el-select v-model="datasourceQuery.scope" clearable placeholder="全部" style="width:110px;">
                <el-option label="全局" value="GLOBAL" />
                <el-option label="项目" value="PROJECT" />
              </el-select>
            </el-form-item>
            <el-form-item label="数据源编码">
              <el-input v-model="datasourceQuery.datasourceCode" clearable placeholder="前缀筛选" style="width:150px;" />
            </el-form-item>
            <el-form-item label="数据源名称">
              <el-input v-model="datasourceQuery.datasourceName" clearable placeholder="名称筛选" style="width:150px;" />
            </el-form-item>
            <el-form-item label="鉴权方式">
              <el-select v-model="datasourceQuery.authType" clearable placeholder="全部" style="width:130px;">
                <el-option v-for="item in authTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="datasourceQuery.status" clearable placeholder="全部" style="width:100px;">
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleDatasourceQuery">查询</el-button>
              <el-button @click="resetDatasourceQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="uiue-btn-bar">
          <div class="btn-right">
            <el-button type="primary" size="small" icon="el-icon-plus" @click="handleCreateDatasource">新建数据源</el-button>
          </div>
        </div>

        <el-table :data="datasourceList" border size="small" v-loading="datasourceLoading" style="width:100%;">
          <el-table-column label="作用范围" width="90" align="center">
            <template slot-scope="{ row }">
              <el-tag :type="row.scope === 'GLOBAL' ? 'warning' : 'success'" size="mini">{{ scopeLabel(row.scope) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="projectName" label="项目名称" min-width="120" show-overflow-tooltip>
            <template slot-scope="{ row }">{{ row.projectName || '—' }}</template>
          </el-table-column>
          <el-table-column prop="datasourceCode" label="数据源编码" min-width="140" show-overflow-tooltip />
          <el-table-column prop="datasourceName" label="数据源名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="providerName" label="提供方" min-width="120" show-overflow-tooltip />
          <el-table-column prop="baseUrl" label="基础地址" min-width="220" show-overflow-tooltip />
          <el-table-column label="鉴权方式" width="110" align="center">
            <template slot-scope="{ row }">
              <el-tag size="mini">{{ optionLabel(authTypeOptions, row.authType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="70" align="center">
            <template slot-scope="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="mini">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" align="center">
            <template slot-scope="{ row }">
              <el-button type="text" size="small" @click="handleEditDatasource(row)">编辑</el-button>
              <el-button type="text" size="small" @click="handleCreateApi(row)">加接口</el-button>
              <el-button type="text" size="small" class="btn-delete" @click="handleDeleteDatasource(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          :current-page="datasourceQuery.pageNum"
          :page-size="datasourceQuery.pageSize"
          :total="datasourceTotal"
          layout="total,sizes,prev,pager,next"
          :page-sizes="[10,30,50,100,200,500]"
          @current-change="p => { datasourceQuery.pageNum = p; loadDatasources() }"
          @size-change="s => { datasourceQuery.pageSize = s; datasourceQuery.pageNum = 1; loadDatasources() }"
        />
      </el-tab-pane>

      <el-tab-pane label="API 接口" name="api">
        <div class="uiue-search-container">
          <el-form :inline="true" size="small">
            <el-form-item label="数据源编码">
              <el-input v-model="apiQuery.datasourceCode" clearable placeholder="前缀筛选" style="width:150px;" />
            </el-form-item>
            <el-form-item label="接口编码">
              <el-input v-model="apiQuery.apiCode" clearable placeholder="前缀筛选" style="width:150px;" />
            </el-form-item>
            <el-form-item label="接口名称">
              <el-input v-model="apiQuery.apiName" clearable placeholder="名称筛选" style="width:150px;" />
            </el-form-item>
            <el-form-item label="调用模式">
              <el-select v-model="apiQuery.requestMode" clearable placeholder="全部" style="width:110px;">
                <el-option label="同步" value="SYNC" />
                <el-option label="异步" value="ASYNC" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="apiQuery.status" clearable placeholder="全部" style="width:100px;">
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleApiQuery">查询</el-button>
              <el-button @click="resetApiQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="uiue-btn-bar">
          <div class="btn-right">
            <el-button type="primary" size="small" icon="el-icon-plus" @click="handleCreateApi()">新建接口</el-button>
          </div>
        </div>

        <el-table :data="apiList" border size="small" v-loading="apiLoading" style="width:100%;">
          <el-table-column prop="datasourceCode" label="数据源" min-width="120" show-overflow-tooltip />
          <el-table-column prop="apiCode" label="接口编码" min-width="140" show-overflow-tooltip />
          <el-table-column prop="apiName" label="接口名称" min-width="150" show-overflow-tooltip />
          <el-table-column label="请求" min-width="180" show-overflow-tooltip>
            <template slot-scope="{ row }">
              <el-tag size="mini" type="info">{{ row.requestMethod }}</el-tag>
              <span class="endpoint-text">{{ row.endpointUrl }}</span>
            </template>
          </el-table-column>
          <el-table-column label="模式" width="80" align="center">
            <template slot-scope="{ row }">
              <el-tag :type="row.requestMode === 'ASYNC' ? 'warning' : 'success'" size="mini">{{ row.requestMode === 'ASYNC' ? '异步' : '同步' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="鉴权" width="110" align="center">
            <template slot-scope="{ row }">
              <el-tag size="mini">{{ optionLabel(authModeOptions, row.authMode) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="超时/重试" width="120" align="center">
            <template slot-scope="{ row }">{{ row.timeoutMs }}ms / {{ row.retryCount || 0 }}次</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="70" align="center">
            <template slot-scope="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="mini">{{ row.status === 1 ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center">
            <template slot-scope="{ row }">
              <el-button type="text" size="small" @click="handleEditApi(row)">编辑</el-button>
              <el-button type="text" size="small" @click="handleInvokeApi(row)">测试</el-button>
              <el-button type="text" size="small" class="btn-delete" @click="handleDeleteApi(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          :current-page="apiQuery.pageNum"
          :page-size="apiQuery.pageSize"
          :total="apiTotal"
          layout="total,sizes,prev,pager,next"
          :page-sizes="[10,30,50,100,200,500]"
          @current-change="p => { apiQuery.pageNum = p; loadApiConfigs() }"
          @size-change="s => { apiQuery.pageSize = s; apiQuery.pageNum = 1; loadApiConfigs() }"
        />
      </el-tab-pane>
    </el-tabs>

    <el-dialog :title="datasourceForm.id ? '编辑外数数据源' : '新建外数数据源'" :visible.sync="datasourceDialogVisible" width="720px" append-to-body>
      <el-form ref="datasourceForm" :model="datasourceForm" :rules="datasourceRules" label-width="110px" size="small">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="作用范围">
              <el-select v-model="datasourceForm.scope" style="width:100%" @change="onDatasourceScopeChange">
                <el-option label="全局" value="GLOBAL" />
                <el-option label="项目级" value="PROJECT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item v-if="datasourceForm.scope === 'PROJECT'" label="所属项目" prop="projectId">
              <el-select v-model="datasourceForm.projectId" filterable placeholder="请选择项目" style="width:100%">
                <el-option v-for="project in projects" :key="project.id" :label="project.projectName" :value="project.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="数据源编码" prop="datasourceCode">
              <el-input v-model="datasourceForm.datasourceCode" placeholder="如 credit_report_provider" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据源名称" prop="datasourceName">
              <el-input v-model="datasourceForm.datasourceName" placeholder="如 征信报告外数" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="服务提供方">
              <el-input v-model="datasourceForm.providerName" placeholder="第三方机构或系统名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="鉴权方式">
              <el-select v-model="datasourceForm.authType" style="width:100%">
                <el-option v-for="item in authTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="基础地址" prop="baseUrl">
          <el-input v-model="datasourceForm.baseUrl" placeholder="https://api.example.com" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="协议">
              <el-select v-model="datasourceForm.protocol" style="width:100%">
                <el-option label="HTTP" value="HTTP" />
                <el-option label="HTTPS" value="HTTPS" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Token缓存秒">
              <el-input-number v-model="datasourceForm.tokenCacheSeconds" :min="0" :step="60" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="鉴权配置">
          <el-input v-model="datasourceForm.authConfig" class="json-input" type="textarea" :rows="5" placeholder='{"headerName":"Authorization","tokenPath":"data.token"}' />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="datasourceForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="datasourceForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="datasourceDialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="handleSaveDatasource">保存</el-button>
      </div>
    </el-dialog>

    <el-dialog :title="apiForm.id ? '编辑 API 接口' : '新建 API 接口'" :visible.sync="apiDialogVisible" width="860px" append-to-body>
      <el-form ref="apiForm" :model="apiForm" :rules="apiRules" label-width="120px" size="small">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="所属数据源" prop="datasourceId">
              <el-select v-model="apiForm.datasourceId" filterable placeholder="请选择数据源" style="width:100%">
                <el-option v-for="item in datasourceOptions" :key="item.id" :label="item.datasourceName + ' / ' + item.datasourceCode" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="调用模式">
              <el-radio-group v-model="apiForm.requestMode">
                <el-radio-button label="SYNC">同步</el-radio-button>
                <el-radio-button label="ASYNC">异步</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="接口编码" prop="apiCode">
              <el-input v-model="apiForm.apiCode" placeholder="如 query_credit_report" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="接口名称" prop="apiName">
              <el-input v-model="apiForm.apiName" placeholder="如 查询征信报告" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="6">
            <el-form-item label="方法">
              <el-select v-model="apiForm.requestMethod" style="width:100%">
                <el-option v-for="method in httpMethods" :key="method" :label="method" :value="method" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="18">
            <el-form-item label="接口地址" prop="endpointUrl">
              <el-input v-model="apiForm.endpointUrl" placeholder="/v1/report/query 或完整 URL" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="Content-Type">
              <el-input v-model="apiForm.contentType" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="请求对象ID">
              <el-input-number v-model="apiForm.requestObjectId" :min="0" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="响应对象ID">
              <el-input-number v-model="apiForm.responseObjectId" :min="0" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-collapse v-model="apiCollapse">
          <el-collapse-item title="鉴权与 Token 获取" name="auth">
            <el-row :gutter="12">
              <el-col :span="8">
                <el-form-item label="接口鉴权">
                  <el-select v-model="apiForm.authMode" style="width:100%">
                    <el-option v-for="item in authModeOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="Token缓存秒">
                  <el-input-number v-model="apiForm.tokenCacheSeconds" :min="0" :step="60" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="异常策略">
                  <el-select v-model="apiForm.exceptionStrategy" style="width:100%">
                    <el-option v-for="item in exceptionStrategyOptions" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="鉴权配置">
              <el-input v-model="apiForm.authApiConfig" class="json-input" type="textarea" :rows="5" placeholder='{"tokenUrl":"/oauth/token","method":"POST","tokenPath":"data.access_token","expiresInPath":"data.expires_in"}' />
            </el-form-item>
          </el-collapse-item>

          <el-collapse-item title="入参、请求头与响应映射" name="mapping">
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="Header配置">
                  <el-input v-model="apiForm.headerConfig" class="json-input" type="textarea" :rows="5" placeholder='{"X-App-Id":"${appId}"}' />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="Query配置">
                  <el-input v-model="apiForm.queryConfig" class="json-input" type="textarea" :rows="5" placeholder='{"name":"$.customer.name"}' />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="入参映射">
                  <el-input v-model="apiForm.requestMapping" class="json-input" type="textarea" :rows="5" placeholder='{"idNo":"Customer.idNo"}' />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="响应映射">
                  <el-input v-model="apiForm.responseMapping" class="json-input" type="textarea" :rows="5" placeholder='{"score":"data.score","riskLevel":"data.level"}' />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="请求体模板">
              <el-input v-model="apiForm.bodyTemplate" class="json-input" type="textarea" :rows="5" placeholder='{"certNo":"${Customer.idNo}","name":"${Customer.name}"}' />
            </el-form-item>
          </el-collapse-item>

          <el-collapse-item title="超时、重试、异步与计费" name="runtime">
            <el-row :gutter="12">
              <el-col :span="8">
                <el-form-item label="超时毫秒">
                  <el-input-number v-model="apiForm.timeoutMs" :min="100" :step="500" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="重试次数">
                  <el-input-number v-model="apiForm.retryCount" :min="0" :max="10" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="重试间隔">
                  <el-input-number v-model="apiForm.retryIntervalMs" :min="0" :step="100" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="单次价格">
                  <el-input-number v-model="apiForm.unitPrice" :min="0" :precision="6" :step="0.01" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="异步回调地址">
                  <el-input v-model="apiForm.asyncCallbackUrl" placeholder="异步接口回调地址" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="异步结果路径">
                  <el-input v-model="apiForm.asyncResultPath" placeholder="如 data.result" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="计费项编码">
                  <el-input v-model="apiForm.billingItemCode" placeholder="如 EXT_CREDIT_REPORT" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="兜底返回">
                  <el-input v-model="apiForm.fallbackValue" class="json-input" type="textarea" :rows="3" placeholder='{"success":false}' />
                </el-form-item>
              </el-col>
            </el-row>
          </el-collapse-item>
        </el-collapse>

        <el-form-item label="说明" style="margin-top:12px;">
          <el-input v-model="apiForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="apiForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="apiDialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="handleSaveApi">保存</el-button>
      </div>
    </el-dialog>

    <el-dialog title="API 调用测试" :visible.sync="invokeDialogVisible" width="760px" append-to-body>
      <div class="invoke-target">当前接口：{{ invokeTarget.apiName }} / {{ invokeTarget.apiCode }}</div>
      <el-form label-width="90px" size="small">
        <el-form-item label="请求参数">
          <el-input v-model="invokeParamsText" class="json-input" type="textarea" :rows="8" placeholder='{"customer":{"idNo":"110101199001010000","name":"张三"}}' />
        </el-form-item>
        <el-form-item label="调用结果">
          <el-input v-model="invokeResultText" class="json-input" type="textarea" :rows="8" readonly />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="invokeDialogVisible = false">关闭</el-button>
        <el-button size="small" type="primary" :loading="invokeLoading" @click="runInvokeApi">执行调用</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import {
  createApiConfig,
  createDatasource,
  deleteApiConfig,
  deleteDatasource,
  listApiConfigs,
  listDatasources,
  invokeApiConfig,
  updateApiConfig,
  updateDatasource
} from '@/api/datasource'
import { listProjects } from '@/api/project'

export default {
  name: 'DatasourceList',
  data() {
    return {
      activeTab: 'datasource',
      projects: [],
      datasourceList: [],
      datasourceOptions: [],
      datasourceTotal: 0,
      datasourceLoading: false,
      datasourceDialogVisible: false,
      datasourceQuery: { pageNum: 1, pageSize: 10, scope: '', datasourceCode: '', datasourceName: '', authType: '', status: '' },
      datasourceForm: this.emptyDatasourceForm(),
      datasourceRules: {
        datasourceCode: [{ required: true, message: '请输入数据源编码', trigger: 'blur' }],
        datasourceName: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
        baseUrl: [{ required: true, message: '请输入基础地址', trigger: 'blur' }],
        projectId: [{ required: true, message: '请选择所属项目', trigger: 'change' }]
      },
      apiList: [],
      apiTotal: 0,
      apiLoading: false,
      apiDialogVisible: false,
      apiQuery: { pageNum: 1, pageSize: 10, datasourceCode: '', apiCode: '', apiName: '', requestMode: '', status: '' },
      apiForm: this.emptyApiForm(),
      invokeDialogVisible: false,
      invokeLoading: false,
      invokeTarget: {},
      invokeParamsText: '{}',
      invokeResultText: '',
      apiRules: {
        datasourceId: [{ required: true, message: '请选择数据源', trigger: 'change' }],
        apiCode: [{ required: true, message: '请输入接口编码', trigger: 'blur' }],
        apiName: [{ required: true, message: '请输入接口名称', trigger: 'blur' }],
        endpointUrl: [{ required: true, message: '请输入接口地址', trigger: 'blur' }]
      },
      apiCollapse: ['auth', 'mapping', 'runtime'],
      httpMethods: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'],
      authTypeOptions: [
        { label: '无', value: 'NONE' },
        { label: 'Basic', value: 'BASIC' },
        { label: 'Bearer', value: 'BEARER' },
        { label: 'API Key', value: 'API_KEY' },
        { label: 'OAuth2', value: 'OAUTH2' },
        { label: 'Token接口', value: 'TOKEN_API' },
        { label: '自定义', value: 'CUSTOM' }
      ],
      authModeOptions: [
        { label: '继承数据源', value: 'INHERIT' },
        { label: '无', value: 'NONE' },
        { label: 'Basic', value: 'BASIC' },
        { label: 'Bearer', value: 'BEARER' },
        { label: 'API Key', value: 'API_KEY' },
        { label: 'OAuth2', value: 'OAUTH2' },
        { label: 'Token接口', value: 'TOKEN_API' },
        { label: '自定义', value: 'CUSTOM' }
      ],
      exceptionStrategyOptions: [
        { label: '快速失败', value: 'FAIL_FAST' },
        { label: '返回默认值', value: 'RETURN_DEFAULT' },
        { label: '忽略异常', value: 'IGNORE' },
        { label: '使用缓存', value: 'USE_CACHE' }
      ]
    }
  },
  created() {
    this.loadProjects()
    this.loadDatasources()
    this.loadDatasourceOptions()
  },
  methods: {
    emptyDatasourceForm() {
      return {
        id: null, scope: 'PROJECT', projectId: null, datasourceCode: '', datasourceName: '', providerName: '',
        protocol: 'HTTPS', baseUrl: '', authType: 'NONE', authConfig: '', tokenCacheSeconds: 0,
        description: '', status: 1
      }
    },
    emptyApiForm() {
      return {
        id: null, datasourceId: null, apiCode: '', apiName: '', requestMethod: 'POST', endpointUrl: '',
        contentType: 'application/json', requestMode: 'SYNC', requestObjectId: null, responseObjectId: null,
        headerConfig: '', queryConfig: '', requestMapping: '', responseMapping: '', bodyTemplate: '',
        authMode: 'INHERIT', authApiConfig: '', tokenCacheSeconds: 0, timeoutMs: 3000, retryCount: 0,
        retryIntervalMs: 200, exceptionStrategy: 'FAIL_FAST', fallbackValue: '', asyncCallbackUrl: '',
        asyncResultPath: '', billingItemCode: '', unitPrice: 0, description: '', status: 1
      }
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
    async loadDatasourceOptions() {
      const res = await listDatasources({ pageNum: 1, pageSize: 500, status: 1 })
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
    onTabChange() {
      if (this.activeTab === 'api') {
        this.loadDatasourceOptions()
        this.loadApiConfigs()
      } else {
        this.loadDatasources()
      }
    },
    handleDatasourceQuery() {
      this.datasourceQuery.pageNum = 1
      this.loadDatasources()
    },
    resetDatasourceQuery() {
      this.datasourceQuery = { pageNum: 1, pageSize: this.datasourceQuery.pageSize, scope: '', datasourceCode: '', datasourceName: '', authType: '', status: '' }
      this.loadDatasources()
    },
    handleApiQuery() {
      this.apiQuery.pageNum = 1
      this.loadApiConfigs()
    },
    resetApiQuery() {
      this.apiQuery = { pageNum: 1, pageSize: this.apiQuery.pageSize, datasourceCode: '', apiCode: '', apiName: '', requestMode: '', status: '' }
      this.loadApiConfigs()
    },
    handleCreateDatasource() {
      this.datasourceForm = this.emptyDatasourceForm()
      this.datasourceDialogVisible = true
    },
    handleEditDatasource(row) {
      this.datasourceForm = { ...this.emptyDatasourceForm(), ...row }
      this.datasourceDialogVisible = true
    },
    handleCreateApi(row) {
      this.activeTab = 'api'
      this.apiForm = this.emptyApiForm()
      if (row && row.id) this.apiForm.datasourceId = row.id
      this.loadDatasourceOptions()
      this.apiDialogVisible = true
    },
    handleEditApi(row) {
      this.apiForm = { ...this.emptyApiForm(), ...row }
      this.loadDatasourceOptions()
      this.apiDialogVisible = true
    },
    handleInvokeApi(row) {
      this.invokeTarget = row
      this.invokeParamsText = '{}'
      this.invokeResultText = ''
      this.invokeDialogVisible = true
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
    handleSaveDatasource() {
      this.$refs.datasourceForm.validate(async valid => {
        if (!valid) return
        const data = this.normalizeDatasource(this.datasourceForm)
        if (data.id) {
          await updateDatasource(data)
          this.$message.success('更新成功')
        } else {
          await createDatasource(data)
          this.$message.success('创建成功')
        }
        this.datasourceDialogVisible = false
        await this.loadDatasources()
        await this.loadDatasourceOptions()
      })
    },
    handleSaveApi() {
      this.$refs.apiForm.validate(async valid => {
        if (!valid) return
        const data = this.normalizeApi(this.apiForm)
        if (data.id) {
          await updateApiConfig(data)
          this.$message.success('更新成功')
        } else {
          await createApiConfig(data)
          this.$message.success('创建成功')
        }
        this.apiDialogVisible = false
        this.loadApiConfigs()
      })
    },
    handleDeleteDatasource(row) {
      this.$confirm('确定删除外数数据源「' + row.datasourceName + '」及其接口配置?', '确认', { type: 'warning' }).then(async () => {
        await deleteDatasource(row.id)
        this.$message.success('删除成功')
        await this.loadDatasources()
        await this.loadDatasourceOptions()
      }).catch(() => {})
    },
    handleDeleteApi(row) {
      this.$confirm('确定删除接口「' + row.apiName + '」?', '确认', { type: 'warning' }).then(async () => {
        await deleteApiConfig(row.id)
        this.$message.success('删除成功')
        this.loadApiConfigs()
      }).catch(() => {})
    },
    onDatasourceScopeChange(scope) {
      if (scope === 'GLOBAL') this.datasourceForm.projectId = 0
    },
    normalizeDatasource(form) {
      const data = { ...form }
      if (data.scope === 'GLOBAL') data.projectId = 0
      data.authConfig = this.blankToNull(data.authConfig)
      return data
    },
    normalizeApi(form) {
      const data = { ...form }
      if (!data.requestObjectId) data.requestObjectId = null
      if (!data.responseObjectId) data.responseObjectId = null
      const jsonFields = ['headerConfig', 'queryConfig', 'requestMapping', 'responseMapping', 'authApiConfig']
      jsonFields.forEach(key => {
        data[key] = this.blankToNull(data[key])
      })
      return data
    },
    blankToNull(value) {
      return value == null || String(value).trim() === '' ? null : value
    },
    cleanParams(params) {
      Object.keys(params).forEach(key => {
        if (params[key] === '' || params[key] === null || params[key] === undefined) delete params[key]
      })
      return params
    },
    scopeLabel(scope) {
      return scope === 'GLOBAL' ? '全局' : '项目'
    },
    optionLabel(options, value) {
      const item = options.find(opt => opt.value === value)
      return item ? item.label : (value || '—')
    }
  }
}
</script>

<style lang="scss" scoped>
.datasource-page {
  .module-hint {
    background: #EFF6FF;
    border: 1px solid #BFDBFE;
    border-radius: 4px;
    padding: 12px 14px;
    margin-bottom: 14px;
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .hint-title {
    color: #1D4ED8;
    font-weight: 700;
    white-space: nowrap;
  }

  .hint-text {
    color: #475569;
    line-height: 1.5;
  }

  .invoke-target {
    color: #475569;
    margin-bottom: 12px;
  }

  .endpoint-text {
    margin-left: 6px;
    color: #475569;
  }

  .json-input ::v-deep textarea {
    font-family: Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    line-height: 1.5;
  }
}
</style>
