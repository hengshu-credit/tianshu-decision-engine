<template>
  <div class="uiue-list-page datasource-page">
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

    <el-tabs v-model="activeTab" @tab-click="onTabChange">
      <el-tab-pane label="数据源" name="datasource">
        <div class="uiue-search-container">
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
        </div>

        <div class="uiue-btn-bar">
          <div class="btn-right">
            <el-button
              type="primary"
              size="small"
              :icon="ElIconPlus"
              @click="handleCreateDatasource"
              >新建数据源</el-button
            >
          </div>
        </div>

        <el-table
          :data="datasourceList"
          border
          size="small"
          v-loading="datasourceLoading"
          style="width: 100%"
        >
          <el-table-column label="作用范围" width="90" align="center">
            <template v-slot="{ row }">
              <el-tag
                :type="row.scope === 'GLOBAL' ? 'warning' : 'success'"
                size="small"
                >{{ scopeLabel(row.scope) }}</el-tag
              >
            </template>
          </el-table-column>
          <el-table-column
            prop="projectName"
            label="项目名称"
            min-width="120"
            show-overflow-tooltip
          >
            <template v-slot="{ row }">{{ row.projectName || '—' }}</template>
          </el-table-column>
          <el-table-column
            prop="datasourceCode"
            label="数据源编码"
            min-width="140"
            show-overflow-tooltip
          />
          <el-table-column
            prop="datasourceName"
            label="数据源名称"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column
            prop="providerName"
            label="提供方"
            min-width="120"
            show-overflow-tooltip
          />
          <el-table-column label="协议" width="110" align="center">
            <template v-slot="{ row }">
              <el-tag size="small" type="info">{{
                optionLabel(protocolOptions, row.protocol)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="baseUrl"
            label="基础地址"
            min-width="220"
            show-overflow-tooltip
          />
          <el-table-column label="鉴权方式" width="110" align="center">
            <template v-slot="{ row }">
              <el-tag size="small">{{
                optionLabel(authTypeOptions, row.authType)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="70" align="center">
            <template v-slot="{ row }">
              <el-tag
                :type="row.status === 1 ? 'success' : 'info'"
                size="small"
                >{{ row.status === 1 ? '启用' : '停用' }}</el-tag
              >
            </template>
          </el-table-column>
          <el-table-column label="操作" width="190" align="center" fixed="right">
            <template v-slot="{ row }">
              <el-button
                link
                size="small"
                @click="handleEditDatasource(row)"
                >编辑</el-button
              >
              <el-button
                link
                size="small"
                @click="handleTestDatasource(row)"
                >测试</el-button
              >
              <el-button link size="small" @click="handleCreateApi(row)"
                >加接口</el-button
              >
              <el-button
                link
                size="small"
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
          :page-sizes="[10, 30, 50, 100, 200, 500]"
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

      <el-tab-pane label="API 接口" name="api">
        <div class="uiue-search-container">
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
        </div>

        <div class="uiue-btn-bar">
          <div class="btn-right">
            <el-button
              type="primary"
              size="small"
              :icon="ElIconPlus"
              @click="handleCreateApi()"
              >新建接口</el-button
            >
          </div>
        </div>

        <el-table
          :data="apiList"
          border
          size="small"
          v-loading="apiLoading"
          style="width: 100%"
        >
          <el-table-column
            prop="datasourceCode"
            label="数据源"
            min-width="120"
            show-overflow-tooltip
          />
          <el-table-column
            prop="apiCode"
            label="接口编码"
            min-width="140"
            show-overflow-tooltip
          />
          <el-table-column
            prop="apiName"
            label="接口名称"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column label="请求" min-width="180" show-overflow-tooltip>
            <template v-slot="{ row }">
              <el-tag size="small" type="info">{{ row.requestMethod }}</el-tag>
              <span class="endpoint-text">{{ row.endpointUrl }}</span>
            </template>
          </el-table-column>
          <el-table-column label="模式" width="80" align="center">
            <template v-slot="{ row }">
              <el-tag
                :type="row.requestMode === 'ASYNC' ? 'warning' : 'success'"
                size="small"
                >{{ row.requestMode === 'ASYNC' ? '异步' : '同步' }}</el-tag
              >
            </template>
          </el-table-column>
          <el-table-column label="鉴权" width="110" align="center">
            <template v-slot="{ row }">
              <el-tag size="small">{{
                optionLabel(authModeOptions, row.authMode)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="超时/重试" width="120" align="center">
            <template v-slot="{ row }"
              >{{ row.timeoutMs }}ms / {{ row.retryCount || 0 }}次</template
            >
          </el-table-column>
          <el-table-column label="响应缓存" width="100" align="center">
            <template v-slot="{ row }">{{
              formatCacheSeconds(row.responseCacheSeconds)
            }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="70" align="center">
            <template v-slot="{ row }">
              <el-tag
                :type="row.status === 1 ? 'success' : 'info'"
                size="small"
                >{{ row.status === 1 ? '启用' : '停用' }}</el-tag
              >
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center" fixed="right">
            <template v-slot="{ row }">
              <el-button link size="small" @click="handleEditApi(row)"
                >编辑</el-button
              >
              <el-button link size="small" @click="handleInvokeApi(row)"
                >测试</el-button
              >
              <el-button
                link
                size="small"
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
          :page-sizes="[10, 30, 50, 100, 200, 500]"
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
      <el-tab-pane label="调用日志" name="logs">
        <module-call-log module-type="DATASOURCE" title="外数调用日志" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      :title="datasourceForm.id ? '编辑外数数据源' : '新建外数数据源'"
      v-model="datasourceDialogVisible"
      width="720px"
      append-to-body
    >
      <el-form
        ref="datasourceForm"
        :model="datasourceForm"
        :rules="datasourceRules"
        label-width="110px"
        size="small"
      >
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="作用范围">
              <el-select
                v-model="datasourceForm.scope"
                style="width: 100%"
                @change="onDatasourceScopeChange"
              >
                <el-option label="全局" value="GLOBAL" />
                <el-option label="项目级" value="PROJECT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              v-if="datasourceForm.scope === 'PROJECT'"
              label="所属项目"
              prop="projectId"
            >
              <el-select
                v-model="datasourceForm.projectId"
                filterable
                placeholder="请选择项目"
                style="width: 100%"
              >
                <el-option
                  v-for="project in projects"
                  :key="project.id"
                  :label="project.projectName"
                  :value="project.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="数据源编码" prop="datasourceCode">
              <el-input
                v-model="datasourceForm.datasourceCode"
                placeholder="如 credit_report_provider"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据源名称" prop="datasourceName">
              <el-input
                v-model="datasourceForm.datasourceName"
                placeholder="如 征信报告外数"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="服务提供方">
              <el-input
                v-model="datasourceForm.providerName"
                placeholder="第三方机构或系统名称"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="鉴权方式">
              <el-select
                v-model="datasourceForm.authType"
                style="width: 100%"
                @change="onDatasourceAuthTypeChange"
              >
                <el-option
                  v-for="item in authTypeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="基础地址" prop="baseUrl">
          <el-input
            v-model="datasourceForm.baseUrl"
            :placeholder="datasourceBaseUrlPlaceholder()"
          />
          <div class="field-help">{{ datasourceBaseUrlHelp() }}</div>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="协议">
              <el-select
                v-model="datasourceForm.protocol"
                style="width: 100%"
                @change="onDatasourceProtocolChange"
              >
                <el-option
                  v-for="item in protocolOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Token缓存秒">
              <el-input-number
                v-model="datasourceForm.tokenCacheSeconds"
                :min="0"
                :step="60"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <div v-if="datasourceForm.authType !== 'NONE'" class="form-section">
          <div class="section-title">数据源鉴权配置</div>
          <div class="section-help">
            这里配置数据源默认鉴权；API
            接口选择“继承数据源”时会使用这些配置。路径可写
            <code>body.data.token</code>，也可写
            <code>headers.Authorization</code> 从响应头读取。
          </div>
          <template v-if="datasourceForm.authType === 'BASIC'">
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="用户名">
                  <el-input
                    v-model="datasourceAuthConfig.username"
                    autocomplete="off"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="密码">
                  <el-input
                    v-model="datasourceAuthConfig.password"
                    type="password"
                    autocomplete="new-password"
                    show-password
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </template>
          <template v-else-if="datasourceForm.authType === 'BEARER'">
            <el-form-item label="Token">
              <el-input
                v-model="datasourceAuthConfig.token"
                type="password"
                autocomplete="new-password"
                show-password
                placeholder="直接写固定 token；动态 token 请使用 Token接口"
              />
            </el-form-item>
          </template>
          <template v-else-if="datasourceForm.authType === 'API_KEY'">
            <el-row :gutter="12">
              <el-col :span="8">
                <el-form-item label="放置位置">
                  <el-select
                    v-model="datasourceAuthConfig.location"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="item in authLocationOptions"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="字段名">
                  <el-input
                    v-model="datasourceAuthConfig.name"
                    placeholder="如 X-API-Key"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="字段值">
                  <el-input
                    v-model="datasourceAuthConfig.value"
                    type="password"
                    autocomplete="new-password"
                    show-password
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </template>
          <template
            v-else-if="
              datasourceForm.authType === 'TOKEN_API' ||
              datasourceForm.authType === 'OAUTH2'
            "
          >
            <el-row :gutter="12">
              <el-col :span="8">
                <el-form-item label="请求方式">
                  <el-select
                    v-model="datasourceAuthConfig.method"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="method in tokenHttpMethods"
                      :key="method"
                      :label="method"
                      :value="method"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="16">
                <el-form-item label="鉴权地址">
                  <el-input
                    v-model="datasourceAuthConfig.tokenUrl"
                    placeholder="/oauth/token 或完整 URL"
                  />
                  <div class="field-help">
                    相对路径会拼接数据源基础地址；测试数据源时会真实请求该地址获取
                    token。
                  </div>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="8">
                <el-form-item label="Content-Type">
                  <el-select
                    v-model="datasourceAuthConfig.contentType"
                    filterable
                    allow-create
                    clearable
                    style="width: 100%"
                  >
                    <el-option
                      v-for="item in contentTypeOptions"
                      :key="item"
                      :label="item"
                      :value="item"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="Token路径">
                  <el-input
                    v-model="datasourceAuthConfig.tokenPath"
                    placeholder="body.data.access_token 或 headers.Authorization"
                  />
                  <div class="field-help">
                    响应体用 <code>body.</code> 开头，响应头用
                    <code>headers.</code> 开头。
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="过期时间路径">
                  <el-input
                    v-model="datasourceAuthConfig.expiresInPath"
                    placeholder="body.expires_in 或 headers.X-Expires-In"
                  />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="鉴权请求头">
                  <monaco-editor
                    v-model:value="datasourceAuthConfig.headers"
                    language="json"
                    height="130px"
                  />
                  <div class="field-help">
                    <code>${字段}</code> 会从测试参数或规则入参中取值。
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="鉴权请求体">
                  <monaco-editor
                    v-model:value="datasourceAuthConfig.body"
                    language="json"
                    height="130px"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </template>
          <template v-else>
            <el-form-item label="自定义JSON">
              <monaco-editor
                v-model:value="datasourceForm.authConfig"
                language="json"
                height="150px"
              />
            </el-form-item>
          </template>
        </div>
        <el-form-item label="说明">
          <el-input
            v-model="datasourceForm.description"
            type="textarea"
            :rows="2"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="datasourceForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <div>
          <el-button size="small" @click="datasourceDialogVisible = false"
            >取消</el-button
          >
          <el-button size="small" type="primary" @click="handleSaveDatasource"
            >保存</el-button
          >
        </div>
      </template>
    </el-dialog>

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
          <el-button
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
      :title="apiForm.id ? '编辑 API 接口' : '新建 API 接口'"
      v-model="apiDialogVisible"
      width="860px"
      append-to-body
    >
      <el-form
        ref="apiForm"
        :model="apiForm"
        :rules="apiRules"
        label-width="120px"
        size="small"
      >
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="所属数据源" prop="datasourceId">
              <el-select
                v-model="apiForm.datasourceId"
                filterable
                placeholder="请选择数据源"
                style="width: 100%"
                @change="onApiDatasourceChange"
              >
                <el-option
                  v-for="item in datasourceOptions"
                  :key="item.id"
                  :label="item.datasourceName + ' / ' + item.datasourceCode"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="调用模式">
              <el-radio-group v-model="apiForm.requestMode">
                <el-radio-button value="SYNC">同步</el-radio-button>
                <el-radio-button value="ASYNC">异步</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="接口编码" prop="apiCode">
              <el-input
                v-model="apiForm.apiCode"
                placeholder="如 query_credit_report"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="接口名称" prop="apiName">
              <el-input
                v-model="apiForm.apiName"
                placeholder="如 查询征信报告"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="方法">
              <el-select v-model="apiForm.requestMethod" style="width: 100%">
                <el-option
                  v-for="method in httpMethods"
                  :key="method"
                  :label="method"
                  :value="method"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="接口地址" prop="endpointUrl">
              <el-input
                v-model="apiForm.endpointUrl"
                :placeholder="apiEndpointPlaceholder()"
              />
              <div class="field-help">{{ apiEndpointHelp() }}</div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="Content-Type">
              <el-select
                v-model="apiForm.contentType"
                filterable
                allow-create
                clearable
                placeholder="不设置或选择类型"
                style="width: 100%"
              >
                <el-option
                  v-for="item in contentTypeOptions"
                  :key="item"
                  :label="item"
                  :value="item"
                />
              </el-select>
              <div class="field-help">
                POST/PUT 常用 application/json；GET 或由三方自行判断时可留空。
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="请求对象">
              <el-select
                v-model="apiForm.requestObjectId"
                clearable
                filterable
                placeholder="选择请求数据对象"
                style="width: 100%"
              >
                <el-option
                  v-for="item in dataObjectOptions"
                  :key="item.id"
                  :label="dataObjectLabel(item)"
                  :value="item.id"
                />
              </el-select>
              <div class="field-help">
                用于说明接口请求体结构；映射仍通过下方
                <code>$.对象.字段</code> 或 <code>${对象.字段}</code> 取值。
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="响应对象">
              <el-select
                v-model="apiForm.responseObjectId"
                clearable
                filterable
                placeholder="选择响应数据对象"
                style="width: 100%"
              >
                <el-option
                  v-for="item in dataObjectOptions"
                  :key="item.id"
                  :label="dataObjectLabel(item)"
                  :value="item.id"
                />
              </el-select>
              <div class="field-help">
                用于说明三方返回结构；响应映射会把字段裁剪到返回结果的
                <code>body</code> 中。
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-collapse v-model="apiCollapse">
          <el-collapse-item title="鉴权与 Token 获取" name="auth">
            <el-row :gutter="12">
              <el-col :span="8">
                <el-form-item label="接口鉴权">
                  <el-select v-model="apiForm.authMode" style="width: 100%">
                    <el-option
                      v-for="item in authModeOptions"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="Token缓存秒">
                  <el-input-number
                    v-model="apiForm.tokenCacheSeconds"
                    :min="0"
                    :step="60"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="异常策略">
                  <el-select
                    v-model="apiForm.exceptionStrategy"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="item in exceptionStrategyOptions"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="鉴权配置">
              <monaco-editor
                v-model:value="apiForm.authApiConfig"
                language="json"
                height="150px"
              />
            </el-form-item>
          </el-collapse-item>

          <el-collapse-item title="入参、请求头与响应映射" name="mapping">
            <div class="mapping-toolbar">
              <div class="field-help">
                入参映射把规则引擎入参转换成接口入参；响应映射把三方返回转换成接口变量可读取的
                <code>body</code>。
              </div>
            </div>
            <el-tabs v-model="apiTemplateTab" type="card" class="template-tabs">
              <el-tab-pane label="HTTP 接口模板" name="HTTP">
                <div class="template-help">
                  适用于第三方 HTTP/HTTPS
                  接口：Header/Query/请求体都可以从规则入参中取值，例如
                  <code>$.customer.idNo</code> 或
                  <code>${customer.idNo}</code>。
                </div>
                <el-button size="small" @click="applyApiTemplate('HTTP')"
                  >填入 HTTP 示例</el-button
                >
              </el-tab-pane>
              <el-tab-pane label="内部规则模板" name="RULE_ENGINE">
                <div class="template-help">
                  适用于协议为“内部规则引擎”的数据源：接口地址填写已发布规则编码，requestMapping.params
                  会作为被调用规则的入参。
                </div>
                <el-button size="small" @click="applyApiTemplate('RULE_ENGINE')"
                  >填入内部规则示例</el-button
                >
              </el-tab-pane>
            </el-tabs>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="Header配置">
                  <monaco-editor
                    v-model:value="apiForm.headerConfig"
                    language="json"
                    height="150px"
                  />
                  <div class="field-help">
                    请求头名到取值表达式的 JSON；敏感头在日志中会脱敏。
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="Query配置">
                  <monaco-editor
                    v-model:value="apiForm.queryConfig"
                    language="json"
                    height="150px"
                  />
                  <div class="field-help">
                    URL Query 参数配置；值支持 <code>$.字段</code> 和
                    <code>${字段}</code>。
                  </div>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="入参映射">
                  <monaco-editor
                    v-model:value="apiForm.requestMapping"
                    language="json"
                    height="150px"
                  />
                  <div class="field-help">
                    没有请求体模板时，会把该映射作为 JSON 请求体；GET/DELETE
                    时不会发送请求体。
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="响应映射">
                  <monaco-editor
                    v-model:value="apiForm.responseMapping"
                    language="json"
                    height="150px"
                  />
                  <div class="field-help">
                    左侧是引擎读取的字段名，右侧是响应路径；映射后接口变量默认从
                    <code>body.score</code> 等字段读取。
                  </div>
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="请求体模板">
              <monaco-editor
                v-model:value="apiForm.bodyTemplate"
                language="json"
                height="150px"
              />
              <div class="field-help">
                优先级高于入参映射；适合三方字段名和内部对象字段名差异较大时使用。
              </div>
            </el-form-item>
          </el-collapse-item>

          <el-collapse-item title="超时、重试、异步与计费" name="runtime">
            <el-row :gutter="12">
              <el-col :span="8">
                <el-form-item label="超时毫秒">
                  <el-input-number
                    v-model="apiForm.timeoutMs"
                    :min="100"
                    :step="500"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="重试次数">
                  <el-input-number
                    v-model="apiForm.retryCount"
                    :min="0"
                    :max="10"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="重试间隔">
                  <el-input-number
                    v-model="apiForm.retryIntervalMs"
                    :min="0"
                    :step="100"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="响应缓存秒">
                  <el-input-number
                    v-model="apiForm.responseCacheSeconds"
                    :min="0"
                    :step="60"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="单次价格">
                  <el-input-number
                    v-model="apiForm.unitPrice"
                    :min="0"
                    :precision="6"
                    :step="0.01"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="异步回调地址">
                  <el-input
                    v-model="apiForm.asyncCallbackUrl"
                    placeholder="异步接口回调地址"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="异步结果路径">
                  <el-input
                    v-model="apiForm.asyncResultPath"
                    placeholder="如 data.result"
                  />
                  <div class="field-help">
                    异步接口回调或轮询结果中，真正业务结果所在的 JSON
                    路径；同步接口可留空。
                  </div>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="计费项编码">
                  <el-input
                    v-model="apiForm.billingItemCode"
                    placeholder="如 EXT_CREDIT_REPORT"
                  />
                  <div class="field-help">
                    用于账单汇总识别该接口的计费项目；不填则使用接口编码或默认
                    API 计费项。
                  </div>
                </el-form-item>
                <el-form-item label="计费条件">
                  <monaco-editor
                    v-model:value="apiForm.billingCondition"
                    language="json"
                    height="110px"
                  />
                  <div class="field-help">
                    空表示正常计费；示例
                    {"path":"body.status","operator":"==","value":0}
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="兜底返回">
                  <monaco-editor
                    v-model:value="apiForm.fallbackValue"
                    language="json"
                    height="110px"
                  />
                  <div class="field-help">
                    异常策略为“返回默认值”时使用，必须是合法
                    JSON；内容会作为返回结果的 <code>body</code>。
                  </div>
                </el-form-item>
              </el-col>
            </el-row>
          </el-collapse-item>
        </el-collapse>

        <el-form-item label="说明" style="margin-top: 12px">
          <el-input v-model="apiForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="apiForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <div>
          <el-button size="small" @click="apiDialogVisible = false"
            >取消</el-button
          >
          <el-button size="small" type="primary" @click="handleSaveApi"
            >保存</el-button
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
          <el-button
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
import { markRaw } from 'vue'
import { Plus as ElIconPlus } from '@element-plus/icons-vue'
import {
  createApiConfig,
  createDatasource,
  deleteApiConfig,
  deleteDatasource,
  listApiConfigs,
  listDatasources,
  invokeApiConfig,
  testDatasourceAuth,
  updateApiConfig,
  updateDatasource,
} from '@/api/datasource'
import { listProjects } from '@/api/project'
import { listDataObjects } from '@/api/dataObject'
import { collectReferencePaths, setPathValue } from '@/utils/testParamTemplate'
import ModuleCallLog from '@/components/common/ModuleCallLog.vue'
import MonacoEditor from '@/components/MonacoEditor'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'

export default {
  data() {
    return {
      apiGuideTemplates: [
        {
          title: 'HTTP 外数模板',
          text: '先建数据源基础地址，再建接口；headerConfig/queryConfig 配公共参数，requestMapping 用 $.字段 取进件值，responseMapping 裁剪接口 body。',
        },
        {
          title: '内部规则模板',
          text: '协议选择内部规则引擎，endpointUrl 填已发布 ruleCode；requestMapping.params 传入下游规则需要的字段。',
        },
        {
          title: '接口变量读取',
          text: '变量来源选择 API 后，在 sourceConfig 写 apiConfigId、paramMapping 和 resultPath，例如 body.score。',
        },
      ],
      activeTab: 'datasource',
      projects: [],
      datasourceList: [],
      datasourceOptions: [],
      dataObjectOptions: [],
      datasourceTotal: 0,
      datasourceLoading: false,
      datasourceDialogVisible: false,
      datasourceQuery: {
        pageNum: 1,
        pageSize: 10,
        projectCode: '',
        projectName: '',
        scope: '',
        datasourceCode: '',
        datasourceName: '',
        authType: '',
        status: '',
      },
      datasourceForm: this.emptyDatasourceForm(),
      datasourceAuthConfig: this.emptyAuthConfig('NONE'),
      authTestDialogVisible: false,
      authTestLoading: false,
      authTestTarget: {},
      authTestParamsText: '{}',
      authTestResultText: '',
      datasourceRules: {
        datasourceCode: [
          { required: true, message: '请输入数据源编码', trigger: 'blur' },
        ],
        datasourceName: [
          { required: true, message: '请输入数据源名称', trigger: 'blur' },
        ],
        baseUrl: [
          {
            validator: (rule, value, callback) => {
              if (
                this.datasourceForm.protocol === 'RULE_ENGINE' ||
                (value != null && String(value).trim() !== '')
              ) {
                callback()
                return
              }
              callback(new Error('请输入基础地址'))
            },
            trigger: 'blur',
          },
        ],
        projectId: [
          { required: true, message: '请选择所属项目', trigger: 'change' },
        ],
      },
      apiList: [],
      apiTotal: 0,
      apiLoading: false,
      apiDialogVisible: false,
      apiQuery: {
        pageNum: 1,
        pageSize: 10,
        projectCode: '',
        projectName: '',
        datasourceCode: '',
        apiCode: '',
        apiName: '',
        requestMode: '',
        status: '',
      },
      apiForm: this.emptyApiForm(),
      invokeDialogVisible: false,
      invokeLoading: false,
      invokeTarget: {},
      invokeParamsText: '{}',
      invokeResultText: '',
      apiTemplateTab: 'HTTP',
      apiRules: {
        datasourceId: [
          { required: true, message: '请选择数据源', trigger: 'change' },
        ],
        apiCode: [
          { required: true, message: '请输入接口编码', trigger: 'blur' },
        ],
        apiName: [
          { required: true, message: '请输入接口名称', trigger: 'blur' },
        ],
        endpointUrl: [
          { required: true, message: '请输入接口地址', trigger: 'blur' },
        ],
      },
      apiCollapse: ['auth', 'mapping', 'runtime'],
      httpMethods: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'],
      tokenHttpMethods: ['GET', 'POST', 'PUT'],
      contentTypeOptions: [
        'application/json',
        'application/x-www-form-urlencoded',
        'multipart/form-data',
        'text/plain',
        'application/xml',
      ],
      authLocationOptions: [
        { label: '请求头 Header', value: 'HEADER' },
        { label: 'URL Query', value: 'QUERY' },
      ],
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
      exceptionStrategyOptions: [
        { label: '快速失败', value: 'FAIL_FAST' },
        { label: '返回默认值', value: 'RETURN_DEFAULT' },
        { label: '忽略异常', value: 'IGNORE' },
        { label: '使用缓存', value: 'USE_CACHE' },
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
  components: {
    ModuleCallLog,
    MonacoEditor,
    RemoteFilterSelect,
    ProjectFilterSelect,
  },
  created() {
    if (this.$route && this.$route.query && this.$route.query.tab === 'api') {
      this.activeTab = 'api'
    }
    this.loadProjects()
    this.loadDatasources()
    this.loadApiConfigs()
    this.loadDatasourceOptions()
    this.loadDataObjectOptions(0)
  },
  methods: {
    emptyDatasourceForm() {
      return {
        id: null,
        scope: 'PROJECT',
        projectId: null,
        datasourceCode: '',
        datasourceName: '',
        providerName: '',
        protocol: 'HTTPS',
        baseUrl: '',
        authType: 'NONE',
        authConfig: '',
        tokenCacheSeconds: 0,
        description: '',
        status: 1,
      }
    },
    emptyAuthConfig(type) {
      const common = {
        username: '',
        password: '',
        token: '',
        name: 'X-API-Key',
        value: '',
        location: 'HEADER',
        tokenUrl: '/oauth/token',
        method: 'POST',
        contentType: 'application/json',
        tokenPath: 'body.access_token',
        expiresInPath: 'body.expires_in',
        headers: '{}',
        body: '{"grant_type":"client_credentials"}',
      }
      if (type === 'API_KEY') common.name = 'X-API-Key'
      if (type === 'TOKEN_API' || type === 'OAUTH2') {
        common.tokenPath = 'body.access_token'
        common.expiresInPath = 'body.expires_in'
      }
      return common
    },
    emptyApiForm() {
      return {
        id: null,
        datasourceId: null,
        apiCode: '',
        apiName: '',
        requestMethod: 'POST',
        endpointUrl: '',
        contentType: 'application/json',
        requestMode: 'SYNC',
        requestObjectId: null,
        responseObjectId: null,
        headerConfig: '',
        queryConfig: '',
        requestMapping: '',
        responseMapping: '',
        bodyTemplate: '',
        authMode: 'INHERIT',
        authApiConfig: '',
        tokenCacheSeconds: 0,
        timeoutMs: 3000,
        retryCount: 0,
        retryIntervalMs: 200,
        responseCacheSeconds: 0,
        exceptionStrategy: 'FAIL_FAST',
        fallbackValue: '',
        asyncCallbackUrl: '',
        asyncResultPath: '',
        billingItemCode: '',
        billingCondition: '',
        unitPrice: 0,
        description: '',
        status: 1,
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
      })
      this.datasourceOptions = (res.data && res.data.records) || []
    },
    async loadDataObjectOptions(projectId) {
      try {
        const res = await listDataObjects(projectId || 0)
        this.dataObjectOptions = Array.isArray(res.data)
          ? res.data
          : Array.isArray(res)
          ? res
          : []
      } catch (e) {
        this.dataObjectOptions = []
      }
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
      this.$router.push('/datasource/source/new')
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
      const query = row && row.id ? { datasourceId: row.id } : {}
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
      const savedSample = this.parseConfigForTemplate(
        row && row.testSampleParams
      )
      if (savedSample && typeof savedSample === 'object')
        return this.stringifyJson(savedSample)
      const sample = {}
      const paths = []
      const addPaths = (value) => {
        collectReferencePaths(this.parseConfigForTemplate(value), {
          allowBarePath: false,
        }).forEach((path) => {
          if (paths.indexOf(path) < 0) paths.push(path)
        })
      }

      addPaths(row && row.headerConfig)
      addPaths(row && row.queryConfig)
      addPaths(row && row.requestMapping)
      addPaths(row && row.bodyTemplate)

      const authConfig = this.parseConfigForTemplate(row && row.authApiConfig)
      if (authConfig && typeof authConfig === 'object') {
        addPaths(authConfig.headers)
        addPaths(authConfig.body)
        addPaths(authConfig.query)
        addPaths(authConfig.params)
      }

      paths.forEach((path) => setPathValue(sample, path, ''))
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
    handleSaveDatasource() {
      this.$refs.datasourceForm.validate(async (valid) => {
        if (!valid) return
        let data
        try {
          data = this.normalizeDatasource(this.datasourceForm)
        } catch (e) {
          this.$message.error(e.message)
          return
        }
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
      this.$refs.apiForm.validate(async (valid) => {
        if (!valid) return
        let data
        try {
          data = this.normalizeApi(this.apiForm)
        } catch (e) {
          this.$message.error(e.message)
          return
        }
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
      this.$confirm(
        '确定删除外数数据源「' + row.datasourceName + '」及其接口配置?',
        '确认',
        { type: 'warning' }
      )
        .then(async () => {
          await deleteDatasource(row.id)
          this.$message.success('删除成功')
          await this.loadDatasources()
          await this.loadDatasourceOptions()
        })
        .catch(() => {})
    },
    handleDeleteApi(row) {
      this.$confirm('确定删除接口「' + row.apiName + '」?', '确认', {
        type: 'warning',
      })
        .then(async () => {
          await deleteApiConfig(row.id)
          this.$message.success('删除成功')
          this.loadApiConfigs()
        })
        .catch(() => {})
    },
    onDatasourceScopeChange(scope) {
      if (scope === 'GLOBAL') this.datasourceForm.projectId = 0
    },
    onDatasourceProtocolChange(protocol) {
      if (protocol === 'RULE_ENGINE' && !this.datasourceForm.baseUrl) {
        this.datasourceForm.baseUrl = 'rule-engine://local'
      }
      if (
        protocol !== 'RULE_ENGINE' &&
        this.datasourceForm.baseUrl === 'rule-engine://local'
      ) {
        this.datasourceForm.baseUrl = ''
      }
    },
    onDatasourceAuthTypeChange(type) {
      this.datasourceAuthConfig = this.emptyAuthConfig(type)
      if (type === 'NONE') this.datasourceForm.authConfig = ''
    },
    onApiDatasourceChange(datasourceId) {
      this.apiForm.requestObjectId = null
      this.apiForm.responseObjectId = null
      this.loadDataObjectOptions(this.resolveDatasourceProjectId(datasourceId))
    },
    resolveDatasourceProjectId(datasourceId) {
      const datasource = this.datasourceOptions.find(
        (item) => item.id === datasourceId
      )
      return datasource && datasource.projectId ? datasource.projectId : 0
    },
    normalizeDatasource(form) {
      const data = { ...form }
      if (data.scope === 'GLOBAL') data.projectId = 0
      if (data.protocol === 'RULE_ENGINE' && !data.baseUrl)
        data.baseUrl = 'rule-engine://local'
      if (data.protocol !== 'RULE_ENGINE' && !data.baseUrl)
        throw new Error('请输入基础地址')
      data.authConfig = this.buildAuthConfig(data.authType, data.authConfig)
      this.assertJson(data.authConfig, '鉴权配置')
      return data
    },
    normalizeApi(form) {
      const data = { ...form }
      if (!data.requestObjectId) data.requestObjectId = null
      if (!data.responseObjectId) data.responseObjectId = null
      const jsonFields = {
        headerConfig: 'Header配置',
        queryConfig: 'Query配置',
        requestMapping: '入参映射',
        responseMapping: '响应映射',
        authApiConfig: '接口鉴权配置',
        bodyTemplate: '请求体模板',
        billingCondition: '计费条件',
        fallbackValue: '兜底返回',
      }
      Object.keys(jsonFields).forEach((key) => {
        data[key] = this.blankToNull(data[key])
        this.assertJson(data[key], jsonFields[key])
      })
      return data
    },
    applyApiTemplate(type) {
      if (type === 'RULE_ENGINE') {
        this.apiForm.requestMethod = 'POST'
        this.apiForm.contentType = 'application/json'
        this.apiForm.endpointUrl =
          this.apiForm.endpointUrl || 'RC_PRICING_TABLE'
        this.apiForm.requestMapping = this.stringifyJson({
          ruleCode: this.apiForm.endpointUrl || 'RC_PRICING_TABLE',
          params: {
            customerType: '$.customerType',
            productLine: '$.productLine',
          },
        })
        this.apiForm.responseMapping = this.stringifyJson({
          decision: 'body.decision',
          rate: 'body.rate',
          score: 'body.score',
        })
        this.apiForm.bodyTemplate = ''
        return
      }
      this.apiForm.requestMethod = 'POST'
      this.apiForm.contentType = 'application/json'
      this.apiForm.headerConfig = this.stringifyJson({
        'X-Request-Id': '${requestId}',
      })
      this.apiForm.requestMapping = this.stringifyJson({
        idNo: '$.customer.idNo',
        mobile: '$.customer.mobile',
        name: '$.customer.name',
      })
      this.apiForm.responseMapping = this.stringifyJson({
        score: 'body.data.score',
        riskLevel: 'body.data.riskLevel',
        hitReason: 'body.data.reason',
      })
      this.apiForm.bodyTemplate = this.stringifyJson({
        certNo: '${customer.idNo}',
        mobile: '${customer.mobile}',
        name: '${customer.name}',
      })
    },
    datasourceBaseUrlPlaceholder() {
      return this.datasourceForm.protocol === 'RULE_ENGINE'
        ? 'rule-engine://local'
        : 'https://api.example.com'
    },
    datasourceBaseUrlHelp() {
      return this.datasourceForm.protocol === 'RULE_ENGINE'
        ? '内部规则引擎数据源可留空，保存时会自动使用 rule-engine://local。'
        : '填写第三方服务基础地址；接口地址为相对路径时会拼接到该地址后。'
    },
    apiEndpointPlaceholder() {
      return this.isRuleEngineDatasource()
        ? '已发布规则编码，如 RC_PRICING_TABLE'
        : '/v1/report/query 或完整 URL'
    },
    apiEndpointHelp() {
      return this.isRuleEngineDatasource()
        ? '内部规则引擎接口会按规则编码查找已发布版本，不发起外部 HTTP 请求。'
        : 'HTTP 接口可填写相对路径或完整 URL；完整 URL 会覆盖数据源基础地址。'
    },
    isRuleEngineDatasource(datasourceId) {
      const id = datasourceId || this.apiForm.datasourceId
      const datasource = this.datasourceOptions.find((item) => item.id === id)
      return datasource && datasource.protocol === 'RULE_ENGINE'
    },
    stringifyJson(value) {
      return JSON.stringify(value, null, 2)
    },
    parseAuthConfig(text, type) {
      const base = this.emptyAuthConfig(type)
      if (!text) return base
      try {
        const parsed = JSON.parse(text)
        const merged = { ...base, ...parsed }
        if (parsed.headers && typeof parsed.headers !== 'string')
          merged.headers = this.stringifyJson(parsed.headers)
        if (parsed.body && typeof parsed.body !== 'string')
          merged.body = this.stringifyJson(parsed.body)
        return merged
      } catch (e) {
        return base
      }
    },
    buildAuthConfig(type, rawAuthConfig) {
      if (!type || type === 'NONE') return null
      if (type === 'BASIC') {
        return this.stringifyJson({
          username: this.datasourceAuthConfig.username,
          password: this.datasourceAuthConfig.password,
        })
      }
      if (type === 'BEARER') {
        return this.stringifyJson({ token: this.datasourceAuthConfig.token })
      }
      if (type === 'API_KEY') {
        return this.stringifyJson({
          location: this.datasourceAuthConfig.location,
          name: this.datasourceAuthConfig.name,
          value: this.datasourceAuthConfig.value,
        })
      }
      if (type === 'TOKEN_API' || type === 'OAUTH2') {
        const headers = this.parseJsonText(
          this.datasourceAuthConfig.headers,
          '鉴权请求头'
        )
        const body = this.parseJsonText(
          this.datasourceAuthConfig.body,
          '鉴权请求体'
        )
        return this.stringifyJson({
          tokenUrl: this.datasourceAuthConfig.tokenUrl,
          method: this.datasourceAuthConfig.method,
          contentType: this.datasourceAuthConfig.contentType,
          headers,
          body,
          tokenPath: this.datasourceAuthConfig.tokenPath,
          expiresInPath: this.datasourceAuthConfig.expiresInPath,
        })
      }
      return this.blankToNull(rawAuthConfig)
    },
    parseJsonText(value, label) {
      if (value == null || String(value).trim() === '') return {}
      try {
        return JSON.parse(value)
      } catch (e) {
        throw new Error(label + '不是合法 JSON：' + e.message)
      }
    },
    assertJson(value, label) {
      if (value == null || value === '') return
      try {
        JSON.parse(value)
      } catch (e) {
        throw new Error(label + '不是合法 JSON：' + e.message)
      }
    },
    dataObjectLabel(item) {
      const code = item.scriptName || item.objectCode || ''
      const name = item.objectName || item.objectLabel || ''
      const type = item.objectType ? '[' + item.objectType + '] ' : ''
      return type + (name || code) + (code && name !== code ? ' / ' + code : '')
    },
    blankToNull(value) {
      return value == null || String(value).trim() === '' ? null : value
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
    color: #475569;
    line-height: 1.5;
  }

  .usage-guide {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
    margin-bottom: 14px;
  }

  .guide-item {
    border: 1px solid #e2e8f0;
    border-radius: 4px;
    padding: 10px 12px;
    background: #ffffff;
  }

  .guide-title {
    color: #0f172a;
    font-weight: 700;
    margin-bottom: 6px;
  }

  .guide-text,
  .field-help {
    color: #64748b;
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
    color: #64748b;
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
    color: #475569;
    margin-bottom: 12px;
  }

  .endpoint-text {
    margin-left: 6px;
    color: #475569;
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
