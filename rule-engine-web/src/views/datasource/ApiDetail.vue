<template>
  <div class="uiue-list-page api-detail-page">
    <div class="detail-header uiue-detail-header">
      <div class="uiue-detail-header__title">
        <el-button size="small" class="uiue-detail-back" aria-label="返回" title="返回接口列表" @click="handleBack"><app-icon name="Back" /></el-button>
        <div>
        <div class="detail-title">
          {{ isCreateMode ? '新建外数 API 接口' : '编辑外数 API 接口' }}
        </div>
        <div class="detail-meta">
          {{ form.apiName || '未命名接口' }} /
          {{ form.apiCode || '待填写编码' }}
        </div>
        </div>
      </div>
      <div class="detail-actions uiue-detail-header__actions">
        <el-button
          v-permission="'datasource:edit'"
          size="small"
          type="primary"
          :loading="saving"
          :disabled="initializing || Boolean(initializationError)"
          @click="handleSave"
          >保存草稿</el-button
        >
      </div>
    </div>

    <div v-if="initializing" data-testid="api-detail-loading" role="status">正在加载接口配置，请稍候…</div>
    <el-alert v-else-if="initializationError" type="error" :closable="false" title="接口配置加载失败" :description="initializationError" show-icon>
      <p>{{ initializationError }}</p>
      <el-button size="small" @click="initializeRoute">重新加载</el-button>
    </el-alert>
    <el-form
      v-if="!initializationError"
      :disabled="initializing"
      :inert="initializing || undefined"
      :aria-busy="initializing"
      ref="form"
      :model="form"
      :rules="rules"
      label-width="118px"
      size="small"
      class="detail-form"
    >
      <config-layer-guide
        title="配置检查"
        description="按基础信息、数据映射、请求预览、草稿试运行、审批生效的顺序完成；高级参数已有安全默认值。"
        aria-label="外数 API 配置进度"
        :items="configurationChecklist"
        show-progress
        interactive
        @select="goToChecklistItem"
      />

      <resource-preflight-panel
        resource-type="EXTERNAL_API"
        :resource-id="form.id"
        :config-signature="preflightConfigSignature"
      />

      <div class="basic-panel">
        <div class="panel-heading">
          <div>
            <div class="panel-title">基础配置</div>
            <div class="panel-subtitle">
              接口归属、调用方式、地址和关联数据对象
            </div>
          </div>
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
        </div>
        <el-row :gutter="12">
          <el-col :lg="12" :md="24">
            <el-form-item label="所属数据源" prop="datasourceId">
              <remote-filter-select
                v-model:value="form.datasourceId"
                :fetch-options="fetchDatasourceChoices"
                :selected-option="selectedDatasourceChoice"
                option-value-key="id"
                option-label-key="displayName"
                placeholder="请选择数据源"
                style="width: 100%"
                @change="onDatasourceChange"
              />
            </el-form-item>
          </el-col>
          <el-col :lg="12" :md="24">
            <el-form-item label="调用模式">
              <el-radio-group v-model="form.requestMode">
                <el-radio-button value="SYNC">同步</el-radio-button>
                <el-radio-button value="ASYNC">异步</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :lg="12" :md="24">
            <el-form-item label="接口编码" prop="apiCode">
              <el-input
                v-model="form.apiCode"
                placeholder="如 query_credit_report"
              />
            </el-form-item>
          </el-col>
          <el-col :lg="12" :md="24">
            <el-form-item label="接口名称" prop="apiName">
              <el-input v-model="form.apiName" placeholder="如 查询征信报告" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :lg="8" :md="24">
            <el-form-item label="方法">
              <el-select v-model="form.requestMethod" style="width: 100%">
                <el-option
                  v-for="method in httpMethods"
                  :key="method"
                  :label="method"
                  :value="method"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :lg="16" :md="24">
            <el-form-item label="接口地址" prop="endpointUrl">
              <el-input
                v-model="form.endpointUrl"
                :placeholder="endpointPlaceholder()"
              />
              <div class="field-help">{{ endpointHelp() }}</div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :lg="8" :md="24">
            <el-form-item label="Content-Type">
              <el-select
                v-model="form.contentType"
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
            </el-form-item>
          </el-col>


        </el-row>
        <el-form-item label="API说明">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="接口用途、供应商联系人、变更说明等"
          />
        </el-form-item>
      </div>

      <section class="config-group-bar" aria-label="配置能力分组">
        <button v-for="group in configGroups" :key="group.name" type="button" :class="{ 'is-active': activeConfigGroup === group.name }" @click="switchConfigGroup(group.name)">
          <strong>{{ group.label }}</strong><small>{{ group.help }}</small>
        </button>
      </section>

      <el-tabs v-model="activeConfigTab" class="config-tabs" :before-leave="validateActiveEditors" @tab-change="syncConfigGroupForTab">
        <el-tab-pane
          v-if="isConfigTabVisible('auth')"
          label="接口鉴权"
          name="auth"
        >
          <div class="tab-section">
            <el-row :gutter="12">
              <el-col :lg="8" :md="24">
                <el-form-item label="接口鉴权">
                  <el-select
                    v-model="form.authMode"
                    style="width: 100%"
                    @change="onAuthModeChange"
                  >
                    <el-option
                      v-for="item in authModeOptions"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :lg="8" :md="24">
                <el-form-item label="Token缓存秒">
                  <el-input-number
                    v-model="form.tokenCacheSeconds"
                    :min="0"
                    :step="60"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :lg="8" :md="24">
                <el-form-item label="提前刷新秒">
                  <el-input-number
                    v-model="form.tokenRefreshAheadSeconds"
                    :min="0"
                    :max="3600"
                    :step="10"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :lg="8" :md="12"
                ><el-form-item label="鉴权失败刷新"
                  ><el-switch
                    v-model="form.tokenRefreshOnUnauthorized"
                    :active-value="1"
                    :inactive-value="0"
                    active-text="命中条件后刷新一次" /></el-form-item
              ></el-col>
              <el-col :lg="8" :md="12"
                ><el-form-item label="Token日志"
                  ><el-switch
                    v-model="form.tokenLogEnabled"
                    :active-value="1"
                    :inactive-value="0"
                    active-text="记录获取/刷新" /></el-form-item
              ></el-col>
            </el-row>
            <el-form-item v-if="form.tokenRefreshOnUnauthorized === 1" label="Token 失效条件">
              <el-radio-group v-model="tokenFailureMode"><el-radio-button value="DEFAULT">HTTP 401 / 403</el-radio-button><el-radio-button value="CUSTOM">自定义条件</el-radio-button></el-radio-group>
              <response-condition-tree-editor v-if="tokenFailureMode === 'CUSTOM'" :group="tokenFailureConditionRoot" :path-options="responsePathOptions" />
            </el-form-item>
            <api-auth-editor v-if="form.authMode !== 'CUSTOM'" ref="authEditor" v-model:value="apiAuthConfig" :type="form.authMode" :vars="apiPickerVars" :functions="apiPickerFunctions" />
            <el-form-item v-else label="自定义配置"><monaco-editor v-model:value="form.authApiConfig" language="json" height="180px" /></el-form-item>
          </div>
        </el-tab-pane>

        <template v-if="form.executionConfig && activeConfigGroup === 'business'">
          <el-tab-pane v-for="item in executionTabs" :key="item.name" :label="item.label" :name="`execution.${item.name}`">
            <api-execution-editor v-if="activeConfigTab === `execution.${item.name}`" ref="executionEditors" v-model:value="form.executionConfig" :active-tab="item.editorTab || item.name" :active-policy="item.policy || ''" embedded-tabs
              :api-config="form" :project-id="resolveDatasourceProjectId(form.datasourceId) || contextProjectId || 0" :async-mode="form.requestMode === 'ASYNC'"
              @select-mode="form.requestMode = $event" @select-sample="invokeParamsText = JSON.stringify($event, null, 2); goToConfigTab('test')" />
          </el-tab-pane>
        </template>

        <el-tab-pane
          v-if="isConfigTabVisible('connection')"
          label="连接&流控"
          name="connection"
        >
          <div class="tab-section">
            <div class="config-card">
              <div class="section-title">独立连接池</div>
              <div class="field-help">
                每个 API
                配置使用独立连接池；获取连接、建连、读取和连接生命周期分别受控，避免慢供应商拖垮其他接口。
              </div>
              <el-row :gutter="12">
                <el-col :lg="6" :md="12"
                  ><el-form-item label="最大连接"
                    ><el-input-number
                      v-model="form.maxConnections"
                      :min="1"
                      :max="10000"
                      style="width: 100%" /></el-form-item
                ></el-col>
                <el-col :lg="6" :md="12"
                  ><el-form-item label="单路由连接"
                    ><el-input-number
                      v-model="form.maxConnectionsPerRoute"
                      :min="1"
                      :max="form.maxConnections || 1"
                      style="width: 100%" /></el-form-item
                ></el-col>
                <el-col :lg="6" :md="12"
                  ><el-form-item label="取连接超时"
                    ><el-input-number
                      v-model="form.connectionRequestTimeoutMs"
                      :min="1"
                      :max="600000"
                      :step="100"
                      style="width: 100%" /></el-form-item
                ></el-col>
                <el-col :lg="6" :md="12"
                  ><el-form-item label="建连超时"
                    ><el-input-number
                      v-model="form.connectTimeoutMs"
                      :min="1"
                      :max="600000"
                      :step="100"
                      style="width: 100%" /></el-form-item
                ></el-col>
              </el-row>
              <el-row :gutter="12">
                <el-col :lg="6" :md="12"
                  ><el-form-item label="读取超时"
                    ><el-input-number
                      v-model="form.readTimeoutMs"
                      :min="1"
                      :max="600000"
                      :step="100"
                      style="width: 100%" /></el-form-item
                ></el-col>
                <el-col :lg="6" :md="12"
                  ><el-form-item label="空闲清理秒"
                    ><el-input-number
                      v-model="form.idleConnectionTimeoutSeconds"
                      :min="1"
                      :max="3600"
                      style="width: 100%" /></el-form-item
                ></el-col>
                <el-col :lg="6" :md="12"
                  ><el-form-item label="连接TTL秒"
                    ><el-input-number
                      v-model="form.connectionTtlSeconds"
                      :min="1"
                      :max="86400"
                      style="width: 100%" /></el-form-item
                ></el-col>
              </el-row>
            </div>
            <div class="config-card">
              <div class="section-title">限流与隔离</div>
              <div class="field-help">
                QPS 为空表示不限流；并发等待为 0
                时超出并发立即失败。所有等待都会受当前规则请求总截止时间约束。
              </div>
              <el-row :gutter="12">
                <el-col :lg="6" :md="12"
                  ><el-form-item label="QPS"
                    ><el-input-number
                      v-model="form.qpsLimit"
                      :min="1"
                      :max="100000"
                      :precision="2"
                      :step="1"
                      style="width: 100%" /></el-form-item
                ></el-col>
                <el-col :lg="6" :md="12"
                  ><el-form-item label="突发容量"
                    ><el-input-number
                      v-model="form.burstCapacity"
                      :disabled="!form.qpsLimit"
                      :min="1"
                      :max="1000000"
                      style="width: 100%" /></el-form-item
                ></el-col>
                <el-col :lg="6" :md="12"
                  ><el-form-item label="最大并发"
                    ><el-input-number
                      v-model="form.maxConcurrent"
                      :min="1"
                      :max="10000"
                      style="width: 100%" /></el-form-item
                ></el-col>
                <el-col :lg="6" :md="12"
                  ><el-form-item label="并发等待ms"
                    ><el-input-number
                      v-model="form.concurrentWaitTimeoutMs"
                      :min="0"
                      :max="60000"
                      :step="100"
                      style="width: 100%" /></el-form-item
                ></el-col>
              </el-row>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane
          v-if="isConfigTabVisible('scripts')"
          label="脚本处理"
          name="scripts"
        >
          <div class="tab-section">
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="简单字段继续使用映射；仅把动态时间、签名、加解密和响应解包写入脚本。请求预览不会访问任何外部地址。"
            />
            <div class="section-toolbar script-variable-toolbar">
              <div>
                <div class="section-title">脚本变量</div>
                <div class="field-help">
                  脚本通过 mapGet(vars, &quot;变量名&quot;)
                  读取；敏感变量会在预览和调用日志中脱敏。
                </div>
              </div>
              <el-button
                size="small"
                :icon="ElIconPlus"
                @click="addScriptVariableRow"
                >添加变量</el-button
              >
            </div>
            <el-table
              :data="scriptVariableRows"
              border
              size="small"
              class="config-table"
            >
              <el-table-column label="变量名" min-width="180">
                <template v-slot="{ row }">
                  <el-input v-model="row.name" placeholder="如 appSecret" />
                </template>
              </el-table-column>
              <el-table-column label="变量值" min-width="280">
                <template v-slot="{ row }">
                  <api-value-editor v-model:value="row.value" :vars="apiPickerVars" :functions="apiPickerFunctions" :modules="[]" :sensitive="row.sensitive" />
                </template>
              </el-table-column>
              <el-table-column label="敏感" width="90" align="center">
                <template v-slot="{ row }">
                  <el-switch v-model="row.sensitive" />
                </template>
              </el-table-column>
              <el-table-column fixed="right" class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" :width="operationColumnWidth(['删除'])" align="center">
                <template v-slot="{ $index }">
                  <el-button
                    link
                    size="small"
                    type="danger"
                    class="btn-delete"
                    @click="removeScriptVariableRow($index)"
                    >删除</el-button
                  >
                </template>
              </el-table-column>
            </el-table>

            <el-row :gutter="12" class="script-editors">
              <el-col :lg="12" :md="24">
                <el-form-item label="请求前置脚本">
                  <monaco-editor
                    v-model:value="form.requestScript"
                    language="javascript"
                    height="300px"
                  />
                  <div class="field-help">
                    上下文：input/body/headers/query/vars/token/state/endpoint/method/nowMillis/requestId。用
                    apiPut(body, key, value) 原地写入。
                  </div>
                </el-form-item>
              </el-col>
              <el-col :lg="12" :md="24">
                <el-form-item label="响应后置脚本">
                  <monaco-editor
                    v-model:value="form.responseScript"
                    language="javascript"
                    height="300px"
                  />
                  <div class="field-help">
                    上下文：input/body/rawBody/httpStatus/headers/vars/state。非空返回值会替换
                    body，再执行响应映射。
                  </div>
                </el-form-item>
              </el-col>
            </el-row>
            <div class="field-help script-function-help">
              可用函数：apiMd5/apiSha1/apiSha256/apiSm3、apiHmacSha1Base64/apiHmacSha256Base64、apiHmacSha1Base64Key/apiHmacSha256Base64Key、apiSortedKeyValue、apiTripleDesEncryptBase64/apiTripleDesDecryptBase64、apiRsaEncryptBase64/apiRsaSignBase64、apiRandomBase64、apiUrlEncode、apiBase64Encode/apiBase64Decode、apiTimestamp/apiTimestampMillis/apiUuid32、apiPut/apiRemove。请求与响应可通过
              state 共享仅本次调用有效的临时值；报文留存选择 PROCESSED 时，可显式设置
              state.logRequestBody 或 state.logResponseBody 留存中间明文。
            </div>
          </div>
        </el-tab-pane>













        <el-tab-pane
          v-if="isConfigTabVisible('retry')"
          label="异常&重试"
          name="retry"
        >
          <div class="tab-section">
            <el-row :gutter="12">
              <el-col :lg="6" :md="12">
                <el-form-item label="超时毫秒">
                  <el-input-number
                    v-model="form.timeoutMs"
                    :min="100"
                    :step="500"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :lg="6" :md="12">
                <el-form-item label="重试次数">
                  <el-input-number
                    v-model="form.retryCount"
                    :min="0"
                    :max="10"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :lg="6" :md="12">
                <el-form-item label="重试间隔">
                  <el-input-number
                    v-model="form.retryIntervalMs"
                    :min="0"
                    :step="100"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :lg="6" :md="12">
                <el-form-item label="异常策略">
                  <el-select
                    v-model="form.exceptionStrategy"
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
            <div v-if="retryConfigurationMessage" class="field-help strategy-warning" role="alert">
              {{ retryConfigurationMessage }}
            </div>
            <div class="config-card">
              <div class="section-title">接口异常条件树</div>
              <div class="field-help">
                命中后按异常策略处理。可判断路径缺失、字段为空、字段类型是否为 STRING/NUMBER/BOOLEAN/OBJECT/ARRAY 等。
              </div>
              <response-condition-tree-editor
                :group="exceptionConditionRoot"
                :path-options="responsePathOptions"
              />
            </div>
            <div class="config-card">
              <div class="section-title">业务成功条件</div>
              <div class="field-help">
                HTTP
                请求完成后按响应字段判断业务是否成功；条件不满足时按业务错误处理，并进入下面的重试判定。
              </div>
              <response-condition-tree-editor
                :group="successConditionRoot"
                :path-options="responsePathOptions"
              />
            </div>
            <div class="config-card">
              <div class="section-title">重试判定与退避</div>
              <div class="field-help">
                默认只重试 502/503/504
                和连接错误；读取超时默认不重试，避免供应商已受理时重复扣费或重复提交。
              </div>
              <el-row :gutter="12">
                <el-col :lg="8" :md="24"
                  ><el-form-item label="HTTP状态码"
                    ><el-input
                      v-model="form.retryStatusCodes"
                      placeholder="502,503,504" /></el-form-item
                ></el-col>
                <el-col :lg="4" :md="8"
                  ><el-form-item label="连接错误"
                    ><el-switch
                      v-model="form.retryOnConnectionError"
                      :active-value="1"
                      :inactive-value="0" /></el-form-item
                ></el-col>
                <el-col :lg="4" :md="8"
                  ><el-form-item label="读取超时"
                    ><el-switch
                      v-model="form.retryOnTimeout"
                      :active-value="1"
                      :inactive-value="0" /></el-form-item
                ></el-col>
                <el-col :lg="4" :md="8"
                  ><el-form-item label="退避倍数"
                    ><el-input-number
                      v-model="form.retryBackoffMultiplier"
                      :min="1"
                      :max="10"
                      :precision="2"
                      :step="0.5"
                      style="width: 100%" /></el-form-item
                ></el-col>
                <el-col :lg="4" :md="8"
                  ><el-form-item label="最大间隔ms"
                    ><el-input-number
                      v-model="form.retryMaxIntervalMs"
                      :min="0"
                      :max="60000"
                      :step="100"
                      style="width: 100%" /></el-form-item
                ></el-col>
              </el-row>
              <div class="retry-idempotency-confirm">
                <el-form-item label="允许非幂等方法重试">
                  <el-switch
                    v-model="form.retryNonIdempotent"
                    :active-value="1"
                    :inactive-value="0"
                  />
                </el-form-item>
                <div class="field-help">
                  POST、PUT、PATCH、DELETE 可能产生重复副作用；仅在供应商确认接口幂等后开启。
                </div>
              </div>
              <div class="section-title">业务响应重试条件</div>
              <div class="field-help">
                仅当业务成功条件不满足、且本条件树命中时才重试。支持且/或嵌套；不配置则所有业务错误都不重试。
              </div>
              <response-condition-tree-editor
                :group="retryConditionRoot"
                :path-options="responsePathOptions"
              />
            </div>
            <div class="config-card">
              <div class="section-toolbar compact">
                <div>
                  <div class="section-title">熔断保护</div>
                  <div class="field-help">
                    按 API 独立统计滑动窗口；打开后经过等待时间进入半开探测。
                  </div>
                </div>
                <el-switch
                  v-model="form.circuitBreakerEnabled"
                  :active-value="1"
                  :inactive-value="0"
                  active-text="启用"
                />
              </div>
              <el-row v-if="form.circuitBreakerEnabled === 1" :gutter="12">
                <el-col :lg="4" :md="8"
                  ><el-form-item label="失败率%"
                    ><el-input-number
                      v-model="form.circuitFailureRate"
                      :min="1"
                      :max="100"
                      style="width: 100%" /></el-form-item
                ></el-col>
                <el-col :lg="4" :md="8"
                  ><el-form-item label="最小调用数"
                    ><el-input-number
                      v-model="form.circuitMinCalls"
                      :min="1"
                      :max="10000"
                      style="width: 100%" /></el-form-item
                ></el-col>
                <el-col :lg="4" :md="8"
                  ><el-form-item label="窗口大小"
                    ><el-input-number
                      v-model="form.circuitWindowSize"
                      :min="form.circuitMinCalls || 1"
                      :max="10000"
                      style="width: 100%" /></el-form-item
                ></el-col>
                <el-col :lg="4" :md="8"
                  ><el-form-item label="打开秒数"
                    ><el-input-number
                      v-model="form.circuitOpenSeconds"
                      :min="1"
                      :max="3600"
                      style="width: 100%" /></el-form-item
                ></el-col>
                <el-col :lg="4" :md="8"
                  ><el-form-item label="半开探测数"
                    ><el-input-number
                      v-model="form.circuitHalfOpenCalls"
                      :min="1"
                      :max="1000"
                      style="width: 100%" /></el-form-item
                ></el-col>
              </el-row>
            </div>
            <el-form-item
              v-if="form.exceptionStrategy === 'RETURN_DEFAULT'"
              label="兜底返回"
            >
              <monaco-editor
                v-model:value="form.fallbackValue"
                language="json"
                height="180px"
              />
              <div class="field-help">
                接口异常时作为返回结果，响应映射后的接口变量仍可按
                <code>body.xxx</code> 读取。
              </div>
            </el-form-item>
          </div>
        </el-tab-pane>

        <el-tab-pane
          v-if="isConfigTabVisible('billing')"
          label="缓存&计费"
          name="billing"
        >
          <div class="tab-section">
            <el-row :gutter="12">
              <el-col :lg="6" :md="24">
                <el-form-item label="响应缓存秒">
                  <el-input-number
                    v-model="form.responseCacheSeconds"
                    :min="0"
                    :step="60"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :lg="9" :md="24">
                <el-form-item label="计费名称">
                  <el-input
                    v-model="form.billingItemCode"
                    placeholder="如 EXT_CREDIT_REPORT"
                  />
                  <div class="field-help">
                    用于账单明细展示，可填写业务能理解的名称或编码，例如“征信报告查询”。
                  </div>
                </el-form-item>
              </el-col>
              <el-col :lg="9" :md="24">
                <el-form-item label="单次价格">
                  <el-input-number
                    v-model="form.unitPrice"
                    :min="0"
                    :precision="6"
                    :step="0.01"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :lg="6" :md="12"
                ><el-form-item label="本地最大条数"
                  ><el-input-number
                    v-model="form.responseCacheMaxSize"
                    :min="1"
                    :max="1000000"
                    style="width: 100%" /></el-form-item
              ></el-col>
              <el-col :lg="6" :md="12"
                ><el-form-item label="单条最大字节"
                  ><el-input-number
                    v-model="form.responseCacheMaxBytes"
                    :min="1024"
                    :max="10485760"
                    :step="1024"
                    style="width: 100%" /></el-form-item
              ></el-col>
              <el-col :lg="6" :md="12"
                ><el-form-item label="Redis二级缓存"
                  ><el-switch
                    v-model="form.responseCacheRedisEnabled"
                    :active-value="1"
                    :inactive-value="0" /></el-form-item
              ></el-col>
              <el-col :lg="6" :md="12"
                ><el-form-item label="过期兜底秒"
                  ><el-input-number
                    v-model="form.staleCacheSeconds"
                    :min="0"
                    :max="86400"
                    :step="60"
                    style="width: 100%" /></el-form-item
              ></el-col>
            </el-row>

            <div class="config-card">
              <div class="section-toolbar compact">
                <div>
                  <div class="section-title">缓存键</div>
                  <div class="field-help">
                    配置字段时按顺序组合并生成脱敏摘要；未配置时默认对全部请求参数生成摘要。
                    已配置字段任一缺失时绕过响应缓存，并记录 CACHE_KEY_INCOMPLETE。
                  </div>
                </div>
                <el-button
                  size="small"
                  :icon="ElIconPlus"
                  @click="addCacheKeyRow"
                  >添加字段</el-button
                >
              </div>
              <div
                v-for="(row, index) in cacheKeyRows"
                :key="'cache-key-' + index"
                class="cache-key-row"
              >
                <span class="cache-key-order">{{ index + 1 }}</span>
                <protocol-path-picker v-model:value="row.path" :paths="requestPathOptions" context-label="当前外数 API · 请求字段" placeholder="如 customer.name" />
                <el-button
                  link
                  size="small"
                  :disabled="index === 0"
                  @click="moveCacheKeyRow(index, -1)"
                  >上移</el-button
                >
                <el-button
                  link
                  size="small"
                  :disabled="index === cacheKeyRows.length - 1"
                  @click="moveCacheKeyRow(index, 1)"
                  >下移</el-button
                >
                <el-button
                  link
                  size="small"
                  class="btn-delete"
                  @click="removeCacheKeyRow(index)"
                  >删除</el-button
                >
              </div>
            </div>

            <el-form-item label="计费模式">
              <el-radio-group v-model="billingConfig.mode">
                <el-radio-button value="QUERY">查询计费</el-radio-button>
                <el-radio-button value="HIT">查得计费</el-radio-button>
              </el-radio-group>
              <div class="field-help">
                查询计费：只要请求已发出就计费，本地参数校验失败不计费；查得计费：只有响应满足下面条件才计费。
              </div>
            </el-form-item>
            <div v-if="billingConfig.mode === 'HIT'" class="config-card">
              <div class="section-title">查得条件</div>
              <div class="field-help">
                该条件树既决定是否按“查得”计费，也作为供应商查得率的统计口径。
              </div>
              <response-condition-tree-editor
                :group="billingConfig.conditionRoot"
                :path-options="responsePathOptions"
              />
            </div>
          </div>
        </el-tab-pane>

        <template v-if="form.executionConfig && activeConfigGroup === 'reliability'">
          <el-tab-pane v-for="item in executionPolicyTabs" :key="item.name" :label="item.label" :name="`execution.${item.name}`">
            <api-execution-editor
              v-if="activeConfigTab === `execution.${item.name}`"
              ref="executionEditors"
              v-model:value="form.executionConfig"
              :active-tab="item.editorTab"
              :active-policy="item.policy"
              embedded-tabs
              :api-config="form"
              :project-id="resolveDatasourceProjectId(form.datasourceId) || contextProjectId || 0"
              :async-mode="form.requestMode === 'ASYNC'"
            />
          </el-tab-pane>
        </template>

        <el-tab-pane
          v-if="isConfigTabVisible('payloadCapture')"
          label="报文留存"
          name="payloadCapture"
        >
          <div class="tab-section payload-capture-section" data-testid="payload-capture-tab">
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="只影响调用日志留存和提供给外部分析系统的副本，不改变实时规则结果。"
            >
              <template #default>
                复杂脚本如需留存中间明文，请在脚本中显式设置
                <code>state.logRequestBody</code> 或
                <code>state.logResponseBody</code>；选择 PROCESSED 不会额外执行解密脚本。
              </template>
            </el-alert>
            <el-alert
              v-if="payloadCaptureError"
              class="payload-capture-warning"
              type="warning"
              :closable="false"
              show-icon
              :title="payloadCaptureError + '，当前已使用安全默认值。'"
            />
            <el-row :gutter="12" class="payload-capture-grid">
              <el-col :lg="12" :md="24">
                <div class="config-card payload-capture-card">
                  <div class="section-title">请求报文</div>
                  <div class="field-help">
                    选择留存实际发送内容，或脚本显式写入 state.logRequestBody 的处理中间副本。
                  </div>
                  <el-form-item label="留存来源">
                    <el-radio-group v-model="payloadCapture.request.source" size="small">
                      <el-radio-button value="ORIGINAL">实际发送内容</el-radio-button>
                      <el-radio-button value="PROCESSED">处理中间副本</el-radio-button>
                    </el-radio-group>
                  </el-form-item>
                  <el-form-item label="保留供应商原文">
                    <el-switch v-model="payloadCapture.request.saveOriginal" active-text="保留" inactive-text="不保留" />
                    <div class="field-help">关闭后只保存处理副本；解密或留存处理失败时系统仍会保留原文并标记原因。</div>
                  </el-form-item>
                  <el-form-item label="留存解密副本">
                    <el-switch v-model="payloadCapture.request.decrypt.enabled" active-text="启用" inactive-text="关闭" />
                    <el-select v-if="payloadCapture.request.decrypt.enabled" v-model="payloadCapture.request.decrypt.mode" size="small" style="width: 170px; margin-left: 8px">
                      <el-option label="Base64 解码" value="BASE64" />
                      <el-option label="3DES Base64 解密" value="TRIPLE_DES_BASE64" />
                    </el-select>
                  </el-form-item>
                  <template v-if="payloadCapture.request.decrypt.enabled">
                    <el-form-item label="解密路径">
                      <el-input v-model="payloadCapture.request.decrypt.path" placeholder="$ 或 $.payload" />
                    </el-form-item>
                    <el-form-item v-if="payloadCapture.request.decrypt.mode === 'TRIPLE_DES_BASE64'" label="密钥变量名">
                      <el-input v-model="payloadCapture.request.decrypt.keyVariable" placeholder="脚本变量中的密钥名，如 desKey" />
                    </el-form-item>
                  </template>
                  <el-form-item label="排除路径">
                    <div class="payload-path-list">
                      <div
                        v-for="(path, index) in payloadCapture.request.excludePaths"
                        :key="`request-path-${index}`"
                        class="payload-path-row"
                      >
                        <el-input
                          v-model="payloadCapture.request.excludePaths[index]"
                          placeholder="如 $.items[*].base64 或 $['含点.字段']"
                        />
                        <el-button link size="small" class="btn-delete" @click="removePayloadCapturePath('request', index)">删除</el-button>
                      </div>
                      <el-button size="small" @click="addPayloadCapturePath('request')">添加路径</el-button>
                      <div class="field-help">每行填写一个 JSONPath；保留原大小写，支持 $.x、[0]、[*] 和 ['含点.字段']。</div>
                    </div>
                  </el-form-item>
                  <el-form-item label="单字段上限（字节）">
                    <el-input-number
                      v-model="payloadCapture.request.maxFieldBytes"
                      :min="0"
                      :max="maxPayloadCaptureFieldBytes"
                      :step="1024"
                      controls-position="right"
                      style="width: 180px"
                    />
                    <div class="field-help">超过该值自动省略；填写 0 关闭限制，最大 50 MiB。</div>
                  </el-form-item>
                  <el-form-item label="超长字段策略">
                    <div class="payload-path-list">
                      <div v-for="(policy, index) in payloadCapture.request.oversizedFields" :key="`request-long-${index}`" class="payload-path-row oversized-field-row">
                        <el-input v-model="policy.path" placeholder="字段路径，如 $.authorizationFile" />
                        <el-switch v-model="policy.store" active-text="保存" inactive-text="省略" />
                        <el-button link size="small" class="btn-delete" @click="removePayloadCapturePolicy('request', index)">删除</el-button>
                      </div>
                      <el-button size="small" @click="addPayloadCapturePolicy('request')">添加超长字段</el-button>
                      <div class="field-help">未配置的字段默认保存；“省略”表示该路径即使内容不大也不写入留存副本。适用于预判会很大的图片、文件、Base64 和密文。</div>
                    </div>
                  </el-form-item>
                </div>
              </el-col>
              <el-col :lg="12" :md="24">
                <div class="config-card payload-capture-card">
                  <div class="section-title">响应报文</div>
                  <div class="field-help">
                    选择供应商原始内容，或 响应后置脚本之后、响应组装之前的处理中间副本。
                  </div>
                  <el-form-item label="留存来源">
                    <el-radio-group v-model="payloadCapture.response.source" size="small">
                      <el-radio-button value="ORIGINAL">原始内容</el-radio-button>
                      <el-radio-button value="PROCESSED">处理中间副本</el-radio-button>
                    </el-radio-group>
                  </el-form-item>
                  <el-form-item label="保留供应商原文">
                    <el-switch v-model="payloadCapture.response.saveOriginal" active-text="保留" inactive-text="不保留" />
                    <div class="field-help">关闭后可避免保存大型 Base64/密文原文；解密失败会强制留存并标明回退原因。</div>
                  </el-form-item>
                  <el-form-item label="留存解密副本">
                    <el-switch v-model="payloadCapture.response.decrypt.enabled" active-text="启用" inactive-text="关闭" />
                    <el-select v-if="payloadCapture.response.decrypt.enabled" v-model="payloadCapture.response.decrypt.mode" size="small" style="width: 170px; margin-left: 8px">
                      <el-option label="Base64 解码" value="BASE64" />
                      <el-option label="3DES Base64 解密" value="TRIPLE_DES_BASE64" />
                    </el-select>
                  </el-form-item>
                  <template v-if="payloadCapture.response.decrypt.enabled">
                    <el-form-item label="解密路径">
                      <el-input v-model="payloadCapture.response.decrypt.path" placeholder="$ 或 $.payload" />
                    </el-form-item>
                    <el-form-item v-if="payloadCapture.response.decrypt.mode === 'TRIPLE_DES_BASE64'" label="密钥变量名">
                      <el-input v-model="payloadCapture.response.decrypt.keyVariable" placeholder="脚本变量中的密钥名，如 desKey" />
                    </el-form-item>
                  </template>
                  <div v-if="payloadCapture.response.source === 'PROCESSED' && !String(form.responseScript || '').trim()" class="field-help payload-capture-warning">
                    当前未配置响应后置脚本，仍可选择 PROCESSED；留存内容将依赖脚本显式写入 state.logResponseBody。
                  </div>
                  <el-form-item label="排除路径">
                    <div class="payload-path-list">
                      <div
                        v-for="(path, index) in payloadCapture.response.excludePaths"
                        :key="`response-path-${index}`"
                        class="payload-path-row"
                      >
                        <el-input
                          v-model="payloadCapture.response.excludePaths[index]"
                          placeholder="如 $.items[*].base64 或 $['含点.字段']"
                        />
                        <el-button link size="small" class="btn-delete" @click="removePayloadCapturePath('response', index)">删除</el-button>
                      </div>
                      <el-button size="small" @click="addPayloadCapturePath('response')">添加路径</el-button>
                      <div class="field-help">每行填写一个 JSONPath；保留原大小写，支持 $.x、[0]、[*] 和 ['含点.字段']。</div>
                    </div>
                  </el-form-item>
                  <el-form-item label="单字段上限（字节）">
                    <el-input-number
                      v-model="payloadCapture.response.maxFieldBytes"
                      :min="0"
                      :max="maxPayloadCaptureFieldBytes"
                      :step="1024"
                      controls-position="right"
                      style="width: 180px"
                    />
                    <div class="field-help">超过该值自动省略；填写 0 关闭限制，最大 50 MiB。</div>
                  </el-form-item>
                  <el-form-item label="超长字段策略">
                    <div class="payload-path-list">
                      <div v-for="(policy, index) in payloadCapture.response.oversizedFields" :key="`response-long-${index}`" class="payload-path-row oversized-field-row">
                        <el-input v-model="policy.path" placeholder="字段路径，如 $.reportFile" />
                        <el-switch v-model="policy.store" active-text="保存" inactive-text="省略" />
                        <el-button link size="small" class="btn-delete" @click="removePayloadCapturePolicy('response', index)">删除</el-button>
                      </div>
                      <el-button size="small" @click="addPayloadCapturePolicy('response')">添加超长字段</el-button>
                      <div class="field-help">未配置的字段默认保存；“省略”表示该路径即使内容不大也不写入留存副本。适用于预判会很大的图片、文件、Base64 和密文。</div>
                    </div>
                  </el-form-item>
                </div>
              </el-col>
            </el-row>
          </div>
        </el-tab-pane>

        <el-tab-pane
          v-if="isConfigTabVisible('test')"
          lazy
          label="接口测试"
          name="test"
        >
          <div class="tab-section">
            <div class="section-toolbar">
              <div>
                <div class="section-title">API 调用测试</div>
                <div v-if="form.executionConfig" class="field-help">可保存多份具名样例，选择样例后预览或测试；预览不会请求鉴权及外数。</div>
                <div v-else class="field-help">
                  每个 API 只保存一份测试样例；生成测试 JSON
                  会根据请求对象字段类型创建空字符串、0、false、{}、[]
                  等默认值。
                </div>
                <div class="field-help">试运行会按当前配置访问鉴权与外数服务，可能产生供应商调用费用。修改配置或入参后，请重新验证，不能沿用旧结果作为当前配置的通过证据。</div>
              </div>
              <div>
                <el-button size="small" @click="regenerateTestParams"
                  >生成测试 JSON</el-button
                >
                <el-button size="small" @click="loadSavedSample"
                  >加载已存样例</el-button
                >
                <el-button v-permission="'datasource:edit'"
                  size="small"
                  :disabled="!form.id && !form.executionConfig"
                  @click="saveCurrentSample"
                  >保存样例</el-button
                >
                <el-button v-permission="'datasource:edit'"
                  size="small"
                  type="success"
                  :disabled="!form.datasourceId"
                  :loading="previewLoading"
                  @click="runRequestPreview"
                  >生成请求预览</el-button
                >
                <el-button v-permission="'datasource:edit'"
                  size="small"
                  type="primary"
                  :disabled="!form.datasourceId || initializing || Boolean(initializationError) || trialLoading"
                  :loading="invokeLoading"
                  @click="runInvokeApi"
                  >{{ form.id ? '执行测试' : trialRequestKey ? '重试草稿试运行' : trial ? '开始新试运行' : '草稿试运行' }}</el-button
                >
              </div>
            </div>
            <section v-if="!form.id" class="api-trial-panel" data-testid="api-trial-panel">
              <div class="section-toolbar">
                <div><strong>草稿试运行会话</strong><div class="field-help">不会创建正式接口或自动提交审批。离开页面后，可从最近会话找回结果并继续处理。</div></div>
                <el-button size="small" :loading="trialHistoryLoading" @click="loadTrialHistory">刷新最近会话</el-button>
              </div>
              <el-select :model-value="trial && trial.trialId" :disabled="trialLoading || invokeLoading" filterable placeholder="选择本人最近的试运行" data-testid="api-trial-select" @change="selectTrial">
                <el-option v-for="item in trialHistory" :key="item.trialId" :value="item.trialId" :label="trialHistoryLabel(item)" />
              </el-select>
              <el-alert v-if="trialHistoryError" type="warning" :closable="false" show-icon>{{ trialHistoryError }}</el-alert>
              <el-alert v-if="trialError" type="warning" :closable="false" show-icon>{{ trialError }}</el-alert>
              <p v-if="trialRequestKey" class="field-help">上次创建尚未确认。重试会沿用同一请求标识；也可刷新最近会话查找已建立的试运行。</p>
              <div v-if="trial" class="api-trial-state" data-testid="api-trial-state">
                <div class="api-trial-heading"><el-tag :type="trialStatusType(trial.status)">{{ trialStatusLabel(trial.status) }}</el-tag><strong>{{ trial.apiName || '本次草稿' }}</strong><span>会话 {{ trial.trialId }}</span></div>
                <div class="field-help">{{ trialStatusHelp(trial.status) }}<span v-if="trial.expiresAt != null && trial.expiresAt !== ''"> 有效期至 {{ formatTrialTime(trial.expiresAt) }}。</span></div>
                <el-alert v-if="trialRecovered || trialSnapshotChanged" :type="trial.status === 'CANCELLED' ? 'info' : 'warning'" :closable="false" show-icon>{{ trial.status === 'CANCELLED' ? '当前展示的是已取消会话的历史快照，仅供查看，不会改写当前编辑表单，也不能继续恢复。' : trialRecovered ? '当前展示的是已保存的试运行快照；找回会话不会改写当前编辑表单。继续操作仍使用该会话原配置与参数。' : '配置或参数已修改；当前会话仍按开始时的配置运行，结果不能作为当前配置的测试结论。' }}</el-alert>
                <el-alert v-if="trial.errorMessage" :type="trial.status === 'CANCELLED' ? 'info' : 'warning'" :closable="false" show-icon>
                  <strong v-if="trial.status === 'CANCELLED'">取消前的提示（仅供查看）：</strong>{{ trial.errorMessage }}
                </el-alert>
                <div class="api-trial-actions">
                  <el-button size="small" :loading="trialLoading" data-testid="api-trial-refresh" @click="refreshTrial">查询状态</el-button>
                  <el-button v-permission="'datasource:edit'" size="small" type="primary" :disabled="trial.canResume !== true || trialLoading" data-testid="api-trial-resume" @click="resumeTrial">继续试运行</el-button>
                  <el-button v-permission="'datasource:edit'" size="small" type="danger" plain :disabled="trial.canCancel !== true || trialLoading" data-testid="api-trial-cancel" @click="cancelTrial">取消试运行</el-button>
                </div>
                <div class="field-help">取消仅停止后续步骤，已经发出的供应商请求及已产生的费用不会撤销。</div>
              </div>
            </section>
            <el-form-item v-if="form.executionConfig" label="选择测试样例">
              <el-select v-model="selectedSampleId" clearable filterable placeholder="选择已保存样例" @change="selectExecutionSample">
                <el-option v-for="sample in executionSamples" :key="sample.id" :value="sample.id" :label="sample.name" />
              </el-select>
            </el-form-item>
            <el-alert
              class="invoke-risk-notice"
              type="warning"
              :closable="false"
              show-icon
            >
              <template #default>
                <strong>执行测试会真实访问外部接口，可能产生供应商费用或触发业务副作用。</strong>
                <span>仅检查报文时请使用“生成请求预览”，预览不会访问外部地址。</span>
                <span>当前配置测试不读取或写入响应缓存；缓存命中请通过已生效的接口变量或规则验证。</span>
              </template>
            </el-alert>
            <el-form-item v-if="previewTokenRequired" label="预览Token">
              <el-input
                v-model="previewToken"
                placeholder="Token/OAuth接口预览可填占位值；不会请求Token地址"
              />
            </el-form-item>
            <el-alert v-else type="info" :closable="false" show-icon :title="previewAuthDescription" />
            <el-alert v-if="testFeedback" type="warning" :closable="false" show-icon>{{ testFeedback }}</el-alert>
            <el-alert v-if="requestPreviewText && previewSnapshotChanged" type="warning" :closable="false" show-icon>配置或参数已变化，下面是此前的请求预览，请重新生成。</el-alert>
            <el-alert v-if="form.id && invokeResultText && invokeSnapshotChanged" type="warning" :closable="false" show-icon>配置或参数已变化，下面是此前的调用结果，请重新测试。</el-alert>
            <el-row :gutter="12">
              <el-col :lg="12" :md="24">
                <el-form-item label="测试参数">
                  <monaco-editor
                    v-model:value="invokeParamsText"
                    language="json"
                    height="240px"
                  />
                </el-form-item>
              </el-col>
              <el-col :lg="12" :md="24">
                <el-form-item label="请求预览">
                  <monaco-editor
                    v-model:value="requestPreviewText"
                    language="json"
                    height="240px"
                    read-only
                  />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="执行结果">
              <monaco-editor
                v-model:value="invokeResultText"
                language="json"
                height="220px"
                read-only
              />
            </el-form-item>
            <external-call-trace v-if="invokeResultText" :steps="invokeTraceSteps" title="完整外数调用链" />
            <div v-if="!form.id" class="empty-state">
              新接口可先保存草稿、预览和试运行；点击发布并审批通过后才会生效。
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-form>
  </div>
</template>

<script>
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import { getDatasource } from '@/api/datasource'
import { hasPermission } from '@/security/permissionState'
import { loadApiReferenceCatalog } from '@/utils/apiReferenceCatalog'
import { editableResource } from '@/api/governance'
import { completeResourceChange } from '@/utils/governanceWorkspace'
import ProtocolPathPicker from '@/components/common/ProtocolPathPicker.vue'
import ApiAuthEditor from '@/components/common/ApiAuthEditor.vue'
import { emptyApiAuthConfig, readApiAuthConfig, apiAuthConfig } from '@/utils/apiAuthConfig'
import ApiValueEditor from '@/components/common/ApiValueEditor.vue'
import ConfigLayerGuide from '@/components/common/ConfigLayerGuide.vue'
import workspaceTabTitleMixin from '@/mixins/workspaceTabTitleMixin'
import configurationDraftMixin from '@/mixins/configurationDraftMixin'
import ApiExecutionEditor from './components/ApiExecutionEditor.vue'
import ExternalCallTrace from '@/components/common/ExternalCallTrace.vue'
import { selectTraceSteps } from '@/utils/externalCallTrace'
import { operationColumnWidth as getOperationColumnWidth } from '@/utils/operationColumnWidth'
import { emptyExecution, newApiId, validateExecution, parseExecution } from '@/utils/apiExecution'
import {
  emptyPayloadCaptureConfig,
  MAX_PAYLOAD_CAPTURE_FIELD_BYTES,
  parsePayloadCaptureConfig,
  stringifyPayloadCaptureConfig,
  retryConfigurationError,
} from '@/utils/externalApiConfig'
import { markRaw } from 'vue'
import { Plus as ElIconPlus } from '@element-plus/icons-vue'
import {
  createApiConfig,
  getApiConfig,
  invokeApiConfigPreview,
  createApiConfigTrial,
  listApiConfigTrials,
  getApiConfigTrial,
  resumeApiConfigTrial,
  cancelApiConfigTrial,
  listDatasources,
  previewApiConfigRequest,
  updateApiConfig,
} from '@/api/datasource'
import { apiStagePaths } from '@/utils/apiContext'
import ResponseConditionTreeEditor from '@/components/common/ResponseConditionTreeEditor.vue'
import ResourcePreflightPanel from '@/components/common/ResourcePreflightPanel.vue'
import MonacoEditor from '@/components/MonacoEditor'
import {
  collectOperandInputPaths,
  sampleValueForVarType,
  setPathValue,
} from '@/utils/testParamTemplate'
import { routeProjectId } from '@/utils/projectContext'
import { trialStatusLabel, trialStatusType } from '@/utils/apiTrialStatus'

export default {
  data() {
    return {
      datasourceOptions: [],
      initializing: true,
      initializationError: '',
      initializationId: 0,
      detailRoutePath: '',
      routeActive: true,
      contextProjectId: null,
      apiPickerVars: [],
      apiPickerRequestId: 0,
      apiPickerFunctions: [],
      saving: false,
      invokeLoading: false,
      previewLoading: false,
      previewToken: '',
      invokeParamsText: '{}',
      selectedSampleId: '',
      invokeResultText: '',
      requestPreviewText: '',
      testFeedback: '',
      invokeRequestId: 0,
      previewRequestId: 0,
      invokeSourceSignature: '',
      previewSourceSignature: '',
      trial: null,
      trialHistory: [],
      trialHistoryLoading: false,
      trialHistoryError: '',
      trialHistoryRequestId: 0,
      trialLoading: false,
      trialRequestId: 0,
      trialRequestKey: '',
      trialSourceSignature: '',
      trialRecovered: false,
      trialError: '',
      trialPollTimer: null,
      form: this.emptyForm(),
      activeConfigGroup: 'business',
      activeConfigTab: 'auth',
      configGroups: [
        {
          name: 'business',
          label: '业务配置',
          help: '鉴权、请求、响应、报文留存和测试',
        },
        {
          name: 'reliability',
          label: '稳定性策略',
          help: '连接、重试、熔断和计费',
        },
        {
          name: 'advanced',
          label: '高级能力',
          help: '仅在签名或加解密时使用脚本',
        },
      ],
      configTabs: [
        { name: 'execution', group: 'business' },
        { name: 'auth', group: 'business' },
        { name: 'payloadCapture', group: 'business' },
        { name: 'test', group: 'business' },
        { name: 'connection', group: 'reliability' },
        { name: 'retry', group: 'reliability' },
        { name: 'billing', group: 'reliability' },
        { name: 'scripts', group: 'advanced' },
      ],
      payloadCapture: emptyPayloadCaptureConfig(),
      payloadCaptureError: '',
      maxPayloadCaptureFieldBytes: MAX_PAYLOAD_CAPTURE_FIELD_BYTES,
      apiAuthConfig: this.emptyAuthConfig('INHERIT'),
      scriptVariableRows: [this.emptyScriptVariableRow()],
      cacheKeyRows: [this.emptyCacheKeyRow()],
      successConditionRoot: this.emptyApiConditionRoot(
        'httpStatus',
        'starts_with',
        '2'
      ),
      exceptionConditionRoot: this.emptyApiConditionRoot(),
      retryConditionRoot: this.emptyApiConditionRoot(),
      tokenFailureMode: 'DEFAULT',
      tokenFailureConditionRoot: this.emptyApiConditionRoot('body.code', '==', 'TOKEN_EXPIRED'),
      billingConfig: this.emptyBillingConfig(),
      rules: {
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
      httpMethods: ['GET', 'POST', 'PUT', 'DELETE', 'PATCH'],
      contentTypeOptions: [
        'application/json',
        'application/x-www-form-urlencoded',
        'multipart/form-data',
        'text/plain',
        'application/xml',
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
        { label: '异常跳过（继续规则）', value: 'SKIP' },
        { label: '异常跳出（结束规则）', value: 'BREAK' },
        { label: '异常等待（按 trace 恢复）', value: 'WAIT' },
        { label: '异常重试', value: 'RETRY' },
        { label: '返回默认值', value: 'RETURN_DEFAULT' },
        { label: '忽略异常', value: 'IGNORE' },
        { label: '使用缓存', value: 'USE_CACHE' },
      ],
      ElIconPlus: markRaw(ElIconPlus),
    }
  },
  name: 'ApiDetail',
  mixins: [configurationDraftMixin(vm => ({ form: vm.form, apiAuthConfig: vm.apiAuthConfig, scriptVariableRows: vm.scriptVariableRows, cacheKeyRows: vm.cacheKeyRows, successConditionRoot: vm.successConditionRoot, exceptionConditionRoot: vm.exceptionConditionRoot, retryConditionRoot: vm.retryConditionRoot, tokenFailureMode: vm.tokenFailureMode, tokenFailureConditionRoot: vm.tokenFailureConditionRoot, billingConfig: vm.billingConfig, payloadCapture: vm.payloadCapture })), workspaceTabTitleMixin(vm => vm.form.apiName)],
  components: { RemoteFilterSelect,
    ProtocolPathPicker,
    ConfigLayerGuide,
    ApiExecutionEditor,
    ExternalCallTrace,
    MonacoEditor,
    ResponseConditionTreeEditor,
    ResourcePreflightPanel,
    ApiAuthEditor,
    ApiValueEditor,
  },
  computed: {
    selectedDatasourceChoice() {
      const item = this.datasourceOptions.find(value => String(value.id) === String(this.form.datasourceId))
      return item ? { ...item, displayName: `${item.datasourceName || ''} / ${item.datasourceCode || ''}` } : null
    },
    invokeTraceSteps() {
      try {
        const result = JSON.parse(this.invokeResultText)
        return selectTraceSteps(result?.traceSteps, result?.externalCall?.traceSteps)
      } catch (error) { return [] }
    },
    previewSnapshotChanged() { return this.previewSourceSignature !== this.capturePreviewSignature() },
    invokeSnapshotChanged() { return this.invokeSourceSignature !== this.captureTestSignature() },
    trialSnapshotChanged() { return Boolean(this.trialSourceSignature && this.trialSourceSignature !== this.captureTestSignature()) },
    retryConfigurationMessage() { return retryConfigurationError(this.form) },
    effectivePreviewAuthMode() {
      if (this.form.authMode !== 'INHERIT') return String(this.form.authMode || 'NONE').toUpperCase()
      const datasource = this.datasourceOptions.find(item => String(item.id) === String(this.form.datasourceId))
      return String(datasource?.authType || 'NONE').toUpperCase()
    },
    previewTokenRequired() { return ['TOKEN_API', 'OAUTH2'].includes(this.effectivePreviewAuthMode || String(this.form.authMode || 'NONE').toUpperCase()) },
    previewAuthDescription() {
      const labels = { NONE: '当前接口无鉴权，预览不会附加 Token Header。', BASIC: '当前接口按 Basic 鉴权配置生成 Authorization，不使用预览 Token。', BEARER: '当前接口按 Bearer 鉴权配置生成 Authorization，不使用预览 Token。', API_KEY: '当前接口按 API Key 的名称、位置和值配置发送，不使用预览 Token。', CUSTOM: '当前接口按自定义鉴权脚本和参数处理，不使用预览 Token。' }
      return labels[this.effectivePreviewAuthMode] || '当前鉴权配置将决定请求头、Query 或脚本处理方式。'
    },
    executionSamples() { return this.form.executionConfig ? JSON.parse(this.form.executionConfig).samples || [] : [] },
    isCreateMode() {
      return !this.$route.params.id || this.$route.params.id === 'new'
    },
    requestPathOptions() { return this.apiPickerVars.map(field => ({ value: field.varCode, label: field.varLabelText || field.varLabel, type: field.varType })) },
    responsePathOptions() { return apiStagePaths(parseExecution(this.form.executionConfig), 'RESULT') },
    visibleConfigTabs() {
      return this.configTabs.filter((item) => {
        if (item.name === 'execution') return false

        if (item.group !== this.activeConfigGroup) return false
        return !item.asyncOnly || this.form.requestMode === 'ASYNC'
      })
    },
    executionTabs() {
      const tabs = [
        { name: 'request', label: '请求字段' },
        { name: 'response', label: '响应结构' },
        { name: 'samples', label: '多份测试样例' },
      ]
      if (this.form.requestMode === 'ASYNC') tabs.splice(2, 0, { name: 'steps', label: '多步链路' })
      return tabs
    },
    executionPolicyTabs() {
      return [
        { name: 'exception-policy', label: '异常条件', editorTab: 'policies', policy: 'exceptionBranches' },
        { name: 'retry-policy', label: '重试条件', editorTab: 'policies', policy: 'retryBranches' },
      ]
    },
    visibleExecutionTabNames() {
      if (!this.form.executionConfig) return []
      const tabs = this.activeConfigGroup === 'reliability' ? this.executionPolicyTabs : this.executionTabs
      return tabs.map(item => `execution.${item.name}`)
    },
    configurationChecklist() {
      const basicReady = Boolean(
        this.form.datasourceId &&
          String(this.form.apiCode || '').trim() &&
          String(this.form.apiName || '').trim() &&
          String(this.form.endpointUrl || '').trim()
      )
      const unified = this.form.executionConfig ? JSON.parse(this.form.executionConfig) : null
      const requestReady = Boolean(unified && !validateExecution(unified, this.form.requestMode))
      const responseReady = Boolean(unified && unified.responseBranches?.length)
      const previewReady = Boolean(this.requestPreviewText && !this.previewSnapshotChanged)
      const trialReady = Boolean(this.trial?.status === 'SUCCEEDED' && this.trial.result?.success !== false && !this.trialRecovered && this.trialSourceSignature === this.captureTestSignature())
      const effectiveReady = Boolean(this.form.id)
      return [
        {
          key: 'basic',
          order: 1,
          label: '基础信息',
          status: basicReady ? 'READY' : 'ATTENTION',
          help: basicReady ? '调用目标已填写' : '先填写数据源、名称、编码和地址',
        },
        {
          key: 'request',
          order: 2,
          label: '请求数据',
          tab: 'execution',
          status: requestReady ? 'READY' : 'ATTENTION',
          help: requestReady ? '已有请求结构' : '按接口文档配置参数或请求体',
        },
        {
          key: 'response',
          order: 3,
          label: '响应数据',
          tab: 'execution',
          status: responseReady ? 'READY' : 'ATTENTION',
          help: responseReady ? '已有输出映射' : '建议配置业务需要的输出字段',
        },
        {
          key: 'preview',
          order: 4,
          label: '请求预览',
          tab: 'test',
          status: previewReady ? 'READY' : 'ATTENTION',
          help: previewReady ? '本次已生成预览' : '先预览，不访问外部服务',
        },
        ...(!this.form.id ? [{
          key: 'trial', order: 5, label: '草稿试运行（可选）', tab: 'test',
          status: trialReady ? 'READY' : 'ATTENTION',
          help: trialReady ? '已按当前配置完成试运行' : '可先保存草稿，再验证真实返回',
        }] : []),
        {
          key: 'effective',
          order: this.form.id ? 5 : 6,
          label: '审批生效',
          status: effectiveReady ? 'READY' : 'ATTENTION',
          help: effectiveReady ? '正式接口已存在' : '生成草稿并完成审批后生效',
        },
      ]
    },
    readyChecklistCount() {
      return this.configurationChecklist.filter(
        (item) => item.status === 'READY'
      ).length
    },
    preflightConfigSignature() {
      return JSON.stringify(this.form || {})
    },
  },
  watch: {
    'form.requestMode'(value) { if (value !== 'ASYNC' && this.activeConfigTab === 'execution.steps') this.activeConfigTab = 'execution.request' },
    '$route.fullPath': async function (value, oldValue) {
      if (value === oldValue) return
      await this.initializeRoute()
    },
  },
  async created() {
    this.detailRoutePath = this.$route.path || ''
    this.contextProjectId = routeProjectId(
      this.$route,
      this.$store && this.$store.state.currentProject
    )
    await this.initializeRoute()
  },
  activated() {
    const resuming = !this.routeActive
    this.routeActive = true
    if (resuming && (this.initializing || this.initializationError)) this.initializeRoute()
  },
  deactivated() {
    this.routeActive = false
    this.initializationId++
    this.invalidateTestRequests()
  },
  beforeUnmount() {
    this.routeActive = false
    this.initializationId++
    this.invalidateTestRequests()
  },
  methods: {
    operationColumnWidth(labels) { return getOperationColumnWidth(labels) },
    emptyPayloadCaptureConfig() {
      return emptyPayloadCaptureConfig()
    },
    hasConfiguredRow(rows, keys) {
      return (rows || []).some((row) =>
        (keys || []).some((key) => String((row && row[key]) || '').trim())
      )
    },
    isConfigTabVisible(name) {
      return this.visibleConfigTabs.some((item) => item.name === name)
    },
    syncConfigGroupForTab(name) {
      if (String(name).startsWith('execution.')) {
        this.activeConfigGroup = this.executionPolicyTabs.some(item => `execution.${item.name}` === name)
          ? 'reliability'
          : 'business'
        return
      }
      const item = this.configTabs.find((tab) => tab.name === name || name.startsWith(`${tab.name}.`))
      if (item) this.activeConfigGroup = item.group
    },
    switchConfigGroup(group) {
      if (!this.validateActiveEditors()) return
      this.activeConfigGroup = group
      const tabs = this.visibleConfigTabs
      const executionNames = Array.isArray(this.visibleExecutionTabNames) ? this.visibleExecutionTabNames : []
      const available = [...tabs.map(item => item.name), ...executionNames]
      if (!available.includes(this.activeConfigTab)) {
        this.activeConfigTab = available[0] || 'auth'
      }
    },
    goToConfigTab(name) {
      if (!this.validateActiveEditors()) return
      const tab = this.configTabs.find((item) => item.name === name)
      if (!tab) return
      this.activeConfigGroup = tab.group
      this.activeConfigTab = name === 'execution' ? 'execution.request' : tab.name
    },
    goToChecklistItem(item) {
      if (item && item.tab) {
        this.goToConfigTab(item.tab)
        return
      }
      const panel = this.$el && this.$el.querySelector('.basic-panel')
      if (panel && panel.scrollIntoView) {
        panel.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
    },
    ownsDetailRoute() {
      return this.routeActive !== false && (!this.detailRoutePath || this.detailRoutePath === this.$route.path)
    },
    isCurrentInitialization(requestId) {
      return this.ownsDetailRoute() && requestId === this.initializationId
    },
    async initializeRoute() {
      if (!this.ownsDetailRoute()) return
      const requestId = this.initializationId = (this.initializationId || 0) + 1
      this.invalidateTestRequests()
      this.initializing = true
      this.initializationError = ''
      try {
        await this.loadDatasourceOptions()
        if (!this.isCurrentInitialization(requestId)) return
        this.form = this.emptyForm()
        this.activeConfigGroup = 'business'
        this.activeConfigTab = 'auth'
        this.invokeResultText = ''
        this.requestPreviewText = ''
        this.previewToken = ''
        if (this.isCreateMode) {
          this.form.executionConfig = JSON.stringify(emptyExecution())
          if (this.$route.query.datasourceId) {
            this.form.datasourceId = Number(this.$route.query.datasourceId)
            await this.ensureSelectedDatasource()
            await this.loadApiPickerOptions(
              this.resolveDatasourceProjectId(this.form.datasourceId)
            )
          } else {
            await this.loadApiPickerOptions(0)
          }
          if (!this.isCurrentInitialization(requestId)) return
          this.syncEditableRowsFromForm()
          this.regenerateTestParams()
          this.loadTrialHistory()
          return
        }
        await this.loadDetail(requestId)
      } catch (error) {
        if (this.isCurrentInitialization(requestId)) this.initializationError = error.message || '无法读取接口配置，请重试'
      } finally {
        if (this.isCurrentInitialization(requestId)) {
          this.initializing = false
          if (!this.initializationError) this.markConfigurationSaved()
        }
      }
    },
    emptyForm() {
      return {
        id: null,
        datasourceId: null,
        apiCode: '',
        apiName: '',
        requestMethod: 'POST',
        endpointUrl: '',
        contentType: 'application/json',
        requestMode: 'SYNC',
        executionConfig: JSON.stringify(emptyExecution()),
        requestScript: '',
        responseScript: '',
        authMode: 'INHERIT',
        authApiConfig: '',
        tokenCacheSeconds: 0,
        timeoutMs: 3000,
        asyncTimeoutMs: 30000,
        retryCount: 0,
        retryNonIdempotent: 0,
        maxConnections: 100,
        maxConnectionsPerRoute: 100,
        connectionRequestTimeoutMs: 100,
        connectTimeoutMs: 500,
        readTimeoutMs: 3000,
        idleConnectionTimeoutSeconds: 30,
        connectionTtlSeconds: 300,
        qpsLimit: null,
        burstCapacity: null,
        maxConcurrent: 50,
        concurrentWaitTimeoutMs: 0,
        tokenRefreshAheadSeconds: 60,
        tokenRefreshOnUnauthorized: 1,
        tokenFailureCondition: '',
        tokenLogEnabled: 1,
        retryIntervalMs: 200,
        retryStatusCodes: '502,503,504',
        retryOnConnectionError: 1,
        retryOnTimeout: 0,
        retryBackoffMultiplier: 2,
        retryMaxIntervalMs: 1000,
        circuitBreakerEnabled: 1,
        circuitFailureRate: 50,
        circuitMinCalls: 20,
        circuitWindowSize: 50,
        circuitOpenSeconds: 10,
        circuitHalfOpenCalls: 5,
        responseCacheSeconds: 0,
        responseCacheMaxSize: 10000,
        responseCacheMaxBytes: 1048576,
        responseCacheRedisEnabled: 0,
        staleCacheSeconds: 0,
        cacheKeyConfig: '',
        successCondition: '',
        exceptionCondition: '',
        retryCondition: '',
        exceptionStrategy: 'FAIL_FAST',
        fallbackValue: '',
        payloadCaptureConfig: '',
        billingItemCode: '',
        billingCondition: '',
        unitPrice: 0,
        description: '',
        status: 1,
      }
    },
    emptyCacheKeyRow() {
      return { path: '' }
    },
    emptyApiConditionRoot(path, operator, value) {
      return {
        type: 'group',
        operator: 'AND',
        children: [
          {
            type: 'condition',
            path: path || '',
            operator: operator || '==',
            value: value == null ? '' : value,
          },
        ],
      }
    },
    emptyBillingConfig() {
      return {
        mode: 'QUERY',
        conditionRoot: {
          type: 'group',
          operator: 'AND',
          children: [
            {
              type: 'condition',
              path: 'body.code',
              operator: '==',
              value: '0',
            },
          ],
        },
      }
    },
    emptyAuthConfig() { return emptyApiAuthConfig() },
    emptyScriptVariableRow() { return { name: '', value: null, sensitive: true } },
    async loadDatasourceOptions() {
      const res = await listDatasources({
        pageNum: 1,
          pageSize: 20,
        status: 1,
        ...(this.contextProjectId
          ? { projectId: this.contextProjectId }
          : {}),
      })
      this.datasourceOptions = (res.data && res.data.records) || []
    },
    async ensureSelectedDatasource() {
      const id = this.form.datasourceId
      if (!id || this.datasourceOptions.some(item => String(item.id) === String(id))) return
      const response = await getDatasource(id)
      if (!response.data?.id) throw new Error('所属数据源不存在或不可访问，请重新选择')
      this.datasourceOptions.push(response.data)
    },
    async fetchDatasourceChoices({ query, pageNum, pageSize }) {
      const response = await listDatasources({ pageNum, pageSize, status: 1, ...(query ? { datasourceName: query } : {}) })
      const data = response.data || {}
      const records = data.records || []
      const known = new Map(this.datasourceOptions.map(item => [String(item.id), item]))
      records.forEach(item => known.set(String(item.id), item))
      this.datasourceOptions = [...known.values()]
      return { data: { ...data, records: records.map(item => ({ ...item, displayName: `${item.datasourceName || ''} / ${item.datasourceCode || ''}` })) } }
    },
    async loadApiPickerOptions(projectId) {
      const requestId = ++this.apiPickerRequestId

      this.apiPickerVars = []; this.apiPickerFunctions = []
      if (projectId == null || projectId === '') return
      const catalog = await loadApiReferenceCatalog(Number(projectId) || 0)
      if (requestId !== this.apiPickerRequestId) return
      this.apiPickerVars = catalog.vars; this.apiPickerFunctions = catalog.functions
      if (catalog.errors.length) this.$message.warning(catalog.errors.join('；'))
    },
    async loadDetail(requestId = this.initializationId) {
      const res = await getApiConfig(this.$route.params.id)
      if (!this.isCurrentInitialization(requestId)) return
      if (res.data?.id && hasPermission('datasource:edit')) res.data = await editableResource('EXTERNAL_API', res.data.id, res.data)
      if (!this.isCurrentInitialization(requestId)) return
      const data = res && res.data ? res.data : res
      const executionConfig = typeof data?.executionConfig === 'string' ? data.executionConfig : JSON.stringify(data?.executionConfig)
      if (!executionConfig || JSON.parse(executionConfig).version !== 2) throw new Error('接口缺少当前执行配置，请重新配置请求与响应。')
      this.form = { ...this.emptyForm(), ...data, executionConfig }
      await this.ensureSelectedDatasource()
      await this.loadApiPickerOptions(
        this.resolveDatasourceProjectId(this.form.datasourceId)
      )
      if (!this.isCurrentInitialization(requestId)) return
      this.syncEditableRowsFromForm()
      this.loadSavedSample()
    },
    onDatasourceChange(datasourceId) {
      this.loadApiPickerOptions(this.resolveDatasourceProjectId(datasourceId))
    },
    onAuthModeChange(type) {
      this.apiAuthConfig = this.emptyAuthConfig(type)
      if (type === 'INHERIT' || type === 'NONE') this.form.authApiConfig = ''
    },
    resolveDatasourceProjectId(datasourceId) {
      const datasource = this.datasourceOptions.find(
        (item) => String(item.id) === String(datasourceId)
      )
      return datasource && datasource.projectId ? datasource.projectId : 0
    },
    isRuleEngineDatasource(datasourceId) {
      const id = datasourceId || this.form.datasourceId
      const datasource = this.datasourceOptions.find(
        (item) => String(item.id) === String(id)
      )
      return datasource && datasource.protocol === 'RULE_ENGINE'
    },
    syncEditableRowsFromForm() {
      this.syncAuthConfigFromForm()
      this.syncCacheKeyConfigFromForm()
      this.syncSuccessConditionFromForm()
      this.syncExceptionConditionFromForm()
      this.syncRetryConditionFromForm()
      this.tokenFailureMode = this.form.tokenFailureCondition ? 'CUSTOM' : 'DEFAULT'
      this.tokenFailureConditionRoot = this.normalizeApiConditionRoot(this.parseConfigForTemplate(this.form.tokenFailureCondition), 'body.code', '==', 'TOKEN_EXPIRED')
      this.syncBillingConfigFromForm()
      this.syncPayloadCaptureFromForm()
    },
    syncAuthConfigFromForm() {
      this.apiAuthConfig = readApiAuthConfig(this.form.authApiConfig)
      this.scriptVariableRows = this.apiAuthConfig.scriptVariables || []
    },
    syncBillingConfigFromForm() {
      const parsed = this.parseConfigForTemplate(this.form.billingCondition)
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        this.billingConfig = {
          mode:
            parsed.mode ||
            (parsed.condition || parsed.path || parsed.field ? 'HIT' : 'QUERY'),
          conditionRoot: this.normalizeApiConditionRoot(
            parsed.condition || parsed,
            'body.code',
            '==',
            '0'
          ),
        }
      } else {
        this.billingConfig = this.emptyBillingConfig()
      }
    },
    syncPayloadCaptureFromForm() {
      try {
        this.payloadCapture = parsePayloadCaptureConfig(this.form.payloadCaptureConfig)
        const defaults = emptyPayloadCaptureConfig()
        for (const side of ['request', 'response']) {
          this.payloadCapture[side].decrypt = { ...defaults[side].decrypt, ...this.payloadCapture[side].decrypt }
        }
        this.payloadCaptureError = ''
      } catch (error) {
        this.payloadCapture = emptyPayloadCaptureConfig()
        this.payloadCaptureError = error.message || '报文留存配置格式不正确'
      }
    },
    buildPayloadCaptureConfig() {
      return stringifyPayloadCaptureConfig(this.payloadCapture)
    },
    addPayloadCapturePath(side) {
      if (!this.payloadCapture[side]) this.payloadCapture[side] = emptyPayloadCaptureConfig()[side]
      this.payloadCapture[side].excludePaths.push('')
    },
    removePayloadCapturePath(side, index) {
      const target = this.payloadCapture[side]
      if (!target || !Array.isArray(target.excludePaths)) return
      target.excludePaths.splice(index, 1)
    },
    addPayloadCapturePolicy(side) {
      if (!this.payloadCapture[side]) this.payloadCapture[side] = emptyPayloadCaptureConfig()[side]
      this.payloadCapture[side].oversizedFields.push({ path: '', store: false })
    },
    removePayloadCapturePolicy(side, index) {
      const target = this.payloadCapture[side]
      if (!target || !Array.isArray(target.oversizedFields)) return
      target.oversizedFields.splice(index, 1)
    },
    syncCacheKeyConfigFromForm() {
      const parsed = this.parseConfigForTemplate(this.form.cacheKeyConfig)
      const components =
        parsed && Array.isArray(parsed.components) ? parsed.components : []
      this.cacheKeyRows = components.map((item) => ({
        path:
          typeof item === 'string'
            ? item
            : (item && (item.path || item.sourcePath)) || '',
      }))
      if (!this.cacheKeyRows.length)
        this.cacheKeyRows = [this.emptyCacheKeyRow()]
    },
    syncSuccessConditionFromForm() {
      const parsed = this.parseConfigForTemplate(this.form.successCondition)
      this.successConditionRoot = this.normalizeApiConditionRoot(
        parsed,
        'httpStatus',
        'starts_with',
        '2'
      )
    },
    syncExceptionConditionFromForm() {
      const parsed = this.parseConfigForTemplate(this.form.exceptionCondition)
      this.exceptionConditionRoot = this.normalizeApiConditionRoot(parsed, '', 'missing', '')
    },
    syncRetryConditionFromForm() {
      const parsed = this.parseConfigForTemplate(this.form.retryCondition)
      this.retryConditionRoot = this.normalizeApiConditionRoot(
        parsed,
        '',
        '==',
        ''
      )
    },
    normalizeApiConditionRoot(
      condition,
      defaultPath,
      defaultOperator,
      defaultValue
    ) {
      const source =
        condition && condition.condition ? condition.condition : condition
      if (!source || typeof source !== 'object' || Array.isArray(source)) {
        return this.emptyApiConditionRoot(
          defaultPath,
          defaultOperator,
          defaultValue
        )
      }
      const normalize = (node) => {
        if (node && node.type === 'group') {
          return {
            type: 'group',
            operator: node.operator || node.op || 'AND',
            children: Array.isArray(node.children)
              ? node.children.map(normalize)
              : [],
          }
        }
        const item = node || {}
        const result = {
          type: 'condition',
          path: item.path || item.field || item.varCode || '',
          operator: item.operator || '==',
        }
        if (Array.isArray(item.values))
          result.values = item.values.map((value) => String(value))
        else
          result.value =
            item.value == null ? '' : this.valueToEditableText(item.value)
        return result
      }
      const normalized = normalize(source)
      if (normalized.type === 'group') return normalized
      return { type: 'group', operator: 'AND', children: [normalized] }
    },
    valueToEditableText(value) {
      if (value == null) return ''
      if (typeof value === 'string') return value
      return JSON.stringify(value)
    },
    syncStructuredConfigToForm() {
      this.form.authApiConfig = this.buildApiAuthConfig()
      this.form.cacheKeyConfig = this.jsonTextOrBlank(this.buildCacheKeyConfig())
      this.form.successCondition = this.jsonTextOrBlank(this.buildSuccessConditionConfig())
      this.form.exceptionCondition = this.jsonTextOrBlank(this.buildExceptionConditionConfig())
      this.form.retryCondition = this.jsonTextOrBlank(this.buildRetryConditionConfig())
      this.form.tokenFailureCondition = this.tokenFailureMode === 'CUSTOM' ? JSON.stringify(this.sanitizeApiConditionTree(this.tokenFailureConditionRoot)) : ''
      this.form.billingCondition = this.jsonTextOrBlank(this.buildBillingConditionConfig())
      this.form.payloadCaptureConfig = this.buildPayloadCaptureConfig()
    },
    buildApiAuthConfig() {
      if (this.$refs?.authEditor && !this.$refs.authEditor.validate()) throw new Error('请修正鉴权配置中的字段名')
      const config = this.form.authMode === 'CUSTOM' ? this.parseJsonText(this.form.authApiConfig, '接口鉴权配置') : apiAuthConfig(this.form.authMode, this.apiAuthConfig)
      const variables = this.buildScriptVariables()
      if (variables.length) config.scriptVariables = variables
      return Object.keys(config).length ? JSON.stringify(config) : ''
    },
    buildScriptVariables() {
      const result = []
      const names = {}
      ;(this.scriptVariableRows || []).forEach((row) => {
        const name = row && row.name != null ? String(row.name).trim() : ''
        if (!name) return
        if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(name)) {
          throw new Error(
            '脚本变量名仅支持字母、数字和下划线，且不能以数字开头：' + name
          )
        }
        if (names[name]) throw new Error('脚本变量名不能重复：' + name)
        names[name] = true
        result.push({
          name,
          value: row.value,
          sensitive: row.sensitive !== false,
        })
      })
      return result
    },
    buildBillingConditionConfig() {
      if (!this.billingConfig || this.billingConfig.mode === 'QUERY') {
        return { mode: 'QUERY' }
      }
      return {
        mode: 'HIT',
        condition: this.sanitizeApiConditionTree(
          this.billingConfig.conditionRoot
        ),
      }
    },
    buildCacheKeyConfig() {
      const components = (this.cacheKeyRows || [])
        .map((item) => String(item && item.path ? item.path : '').trim())
        .filter(Boolean)
        .map((path) => ({ path }))
      return components.length ? { components } : {}
    },
    buildSuccessConditionConfig() {
      return this.sanitizeApiConditionTree(this.successConditionRoot)
    },
    buildExceptionConditionConfig() {
      const condition = this.sanitizeApiConditionTree(this.exceptionConditionRoot)
      return this.apiConditionTreeHasPath(condition) ? condition : {}
    },
    buildRetryConditionConfig() {
      const condition = this.sanitizeApiConditionTree(this.retryConditionRoot)
      return this.apiConditionTreeHasPath(condition) ? condition : {}
    },
    apiConditionTreeHasPath(node) {
      if (!node || typeof node !== 'object') return false
      if (node.type === 'group') {
        return (
          Array.isArray(node.children) &&
          node.children.some((child) => this.apiConditionTreeHasPath(child))
        )
      }
      return Boolean(node.path)
    },
    sanitizeApiConditionTree(node) {
      if (!node || typeof node !== 'object') return this.emptyApiConditionRoot()
      if (node.type === 'group') {
        return {
          type: 'group',
          operator: node.operator === 'OR' ? 'OR' : 'AND',
          children: (node.children || []).map((child) =>
            this.sanitizeApiConditionTree(child)
          ),
        }
      }
      const result = {
        type: 'condition',
        path: String(node.path || '').trim(),
        operator: node.operator || '==',
      }
      if (node.operator === 'in' || node.operator === 'not_in') {
        result.values = (node.values || [])
          .map((value) => String(value).trim())
          .filter(Boolean)
      } else {
        result.value = node.value == null ? '' : String(node.value)
      }
      return result
    },
    validateApiConditionTree(node, label) {
      if (!node || typeof node !== 'object') throw new Error(label + '不能为空')
      if (node.type === 'group') {
        if (!Array.isArray(node.children) || !node.children.length)
          throw new Error(label + '至少需要一个判断条件')
        node.children.forEach((child) =>
          this.validateApiConditionTree(child, label)
        )
        return
      }
      if (!node.path) throw new Error(label + '存在未填写的响应字段路径')
      if (
        (node.operator === 'in' || node.operator === 'not_in') &&
        (!node.values || !node.values.length)
      ) {
        throw new Error(label + '的列表判断至少需要一个值')
      }
    },
    addScriptVariableRow() {
      this.scriptVariableRows.push(this.emptyScriptVariableRow())
    },
    addCacheKeyRow() {
      this.cacheKeyRows.push(this.emptyCacheKeyRow())
    },
    removeCacheKeyRow(index) {
      this.cacheKeyRows.splice(index, 1)
      if (!this.cacheKeyRows.length)
        this.cacheKeyRows.push(this.emptyCacheKeyRow())
    },
    moveCacheKeyRow(index, offset) {
      const target = index + offset
      if (target < 0 || target >= this.cacheKeyRows.length) return
      const row = this.cacheKeyRows.splice(index, 1)[0]
      this.cacheKeyRows.splice(target, 0, row)
    },
    removeScriptVariableRow(index) {
      this.scriptVariableRows.splice(index, 1)
      if (!this.scriptVariableRows.length)
        this.scriptVariableRows.push(this.emptyScriptVariableRow())
    },
    normalizeForm(form) {
      if (!this.validateActiveEditors()) throw new Error('请修正当前配置中的字段名')
      this.syncStructuredConfigToForm()
      const data = { ...form }
      if (!data.executionConfig) throw new Error('请配置接口的请求与响应')
      ;['headerConfig', 'queryConfig', 'requestMapping', 'responseMapping', 'bodyTemplate'].forEach(key => { delete data[key] })
      const retryError = retryConfigurationError(data)
      if (retryError) throw new Error(retryError)
      const executionError = validateExecution(JSON.parse(data.executionConfig), data.requestMode)
      if (executionError) throw new Error(executionError)
      const successCondition = this.buildSuccessConditionConfig()
      if (this.tokenFailureMode === 'CUSTOM') this.validateApiConditionTree(this.tokenFailureConditionRoot, 'Token鉴权失败条件')
      this.validateApiConditionTree(successCondition, '请求成功条件')
      const exceptionCondition = this.buildExceptionConditionConfig()
      if (Object.keys(exceptionCondition).length) {
        this.validateApiConditionTree(exceptionCondition, '接口异常条件')
      }
      const retryCondition = this.buildRetryConditionConfig()
      if (Object.keys(retryCondition).length) {
        this.validateApiConditionTree(retryCondition, '业务响应重试条件')
      }
      if (this.billingConfig && this.billingConfig.mode === 'HIT') {
        this.validateApiConditionTree(
          this.buildBillingConditionConfig().condition,
          '查得条件'
        )
      }
      const jsonFields = {
        authApiConfig: '接口鉴权配置',
        cacheKeyConfig: '缓存键配置',
        successCondition: '请求成功条件',
        exceptionCondition: '接口异常条件',
        retryCondition: '业务响应重试条件',
        tokenFailureCondition: 'Token鉴权失败条件',
        billingCondition: '计费条件',
        fallbackValue: '兜底返回',
        payloadCaptureConfig: '报文留存配置',
      }
      Object.keys(jsonFields).forEach((key) => {
        data[key] = this.blankToNull(data[key])
        this.assertJson(data[key], jsonFields[key])
      })
      data.requestScript = this.blankToNull(data.requestScript)
      data.responseScript = this.blankToNull(data.responseScript)
      return data
    },
    validateActiveEditors() {
      if (this.$refs?.authEditor && !this.$refs.authEditor.validate()) return false
      for (const editor of this.$refs?.executionEditors || []) {
        if (!editor.validate()) return false
        editor.flushConfig()
      }
      return true
    },
    handleBack() {
      this.$router.push({
        path: '/datasource',
        query: {
          tab: 'api',
          ...(this.contextProjectId
            ? { projectId: this.contextProjectId }
            : {}),
        },
      })
    },
    handleSave() {
      if (this.initializing || this.initializationError) return
      this.$refs.form.validate(async (valid) => {
        if (!valid) return
        let data
        try {
          data = this.normalizeForm(this.form)
        } catch (e) {
          this.$message.error(e.message)
          return
        }
        this.saving = true
        const submittedFingerprint = this.configurationFingerprint
        try {
          let res
          if (data.id) {
            res = await updateApiConfig(data)
          } else {
            res = await createApiConfig(data)
          }
          await completeResourceChange(this, res)
          this.markConfigurationSaved(submittedFingerprint)
          if (!this.form.id && res.data?.resourceId) {
            this.form.id = res.data.resourceId
            await this.$router.replace('/datasource/api/' + this.form.id)
          }
        } finally {
          this.saving = false
        }
      })
    },
    endpointPlaceholder() {
      return this.isRuleEngineDatasource()
        ? '已发布规则编码，如 RC_PRICING_TABLE'
        : '/v1/report/query 或完整 URL'
    },
    endpointHelp() {
      return this.isRuleEngineDatasource()
        ? '内部规则引擎接口会按规则编码查找已发布版本，不发起外部 HTTP 请求。'
        : 'HTTP 接口可填写相对路径或完整 URL；完整 URL 会覆盖数据源基础地址。支持 ${appId}、${vars.appId} 和 ${input.channel} 占位。'
    },
    regenerateTestParams() {
      this.syncStructuredConfigToForm()
      this.invokeParamsText = this.buildApiInvokeParamTemplate(this.form)
    },
    loadSavedSample() { if (this.selectedSampleId) this.selectExecutionSample(); else this.regenerateTestParams() },
    selectExecutionSample() {
      const sample = this.executionSamples.find(item => item.id === this.selectedSampleId)
      if (sample) this.invokeParamsText = JSON.stringify(sample.params, null, 2)
    },
    async saveCurrentSample() {
      try {
        const parsed = this.parseTestParams()
        const spec = JSON.parse(this.form.executionConfig)
        if (!spec.samples) spec.samples = []
        const existing = spec.samples.find(sample => sample.id === this.selectedSampleId)
        if (existing) existing.params = parsed
        else { const sample = { id: newApiId(), name: '样例 ' + (spec.samples.length + 1), params: parsed }; spec.samples.push(sample); this.selectedSampleId = sample.id }
        this.form.executionConfig = JSON.stringify(spec)
        this.$message.success('样例已加入当前配置，请保存审批草稿后生效')
      } catch (error) { this.$message.error(error.message || '测试参数不是合法 JSON') }
    },
    buildApiInvokeParamTemplate(row) {
      const sample = {}
      const execution = JSON.parse(row.executionConfig)
      const auth = this.parseConfigForTemplate(row.authApiConfig)
      const paths = new Set(collectOperandInputPaths([execution.requestFields, execution.requestBranches, execution.steps, auth]))
      paths.forEach(path => setPathValue(sample, path, this.sampleValueForPath(path)))
      return this.stringifyJson(sample)
    },
    sampleValueForPath(path) {
      const normalized = String(path || '').replace(/^\$\./, '')
      const field = this.findFieldForSamplePath(normalized)
      return sampleValueForVarType(field && field.varType)
    },
    findFieldForSamplePath(path) { return this.apiPickerVars.find(field => field.varCode === String(path || '').replace(/^\$\./, '')) },
    captureTestSignature() {
      return JSON.stringify([this.form, this.invokeParamsText, this.apiAuthConfig, this.scriptVariableRows, this.cacheKeyRows,
        this.successConditionRoot, this.exceptionConditionRoot, this.retryConditionRoot, this.tokenFailureMode,
        this.tokenFailureConditionRoot, this.billingConfig, this.payloadCapture])
    },
    capturePreviewSignature() { return JSON.stringify([this.captureTestSignature(), this.previewToken || '']) },
    parseTestParams() {
      let params
      try { params = this.invokeParamsText?.trim() ? JSON.parse(this.invokeParamsText) : {} }
      catch { throw new Error('测试参数不是合法 JSON') }
      if (!params || typeof params !== 'object' || Array.isArray(params)) throw new Error('测试参数必须是 JSON 对象')
      return params
    },
    stopTrialPolling() {
      if (this.trialPollTimer) clearTimeout(this.trialPollTimer)
      this.trialPollTimer = null
    },
    invalidateTestRequests() {
      this.stopTrialPolling()
      this.invokeRequestId = (this.invokeRequestId || 0) + 1
      this.previewRequestId = (this.previewRequestId || 0) + 1
      this.trialRequestId = (this.trialRequestId || 0) + 1
      this.trialHistoryRequestId = (this.trialHistoryRequestId || 0) + 1
      this.invokeLoading = false
      this.previewLoading = false
      this.trialLoading = false
      this.trialHistoryLoading = false
      this.trialHistoryError = ''
      this.trial = null
      this.trialHistory = []
      this.trialRequestKey = ''
      this.trialSourceSignature = ''
      this.trialRecovered = false
      this.trialError = ''
      this.testFeedback = ''
      this.invokeSourceSignature = ''
      this.previewSourceSignature = ''
    },
    trialStatusLabel,
    trialStatusType,
    trialStatusHelp(status) {
      if (status === 'UNKNOWN') return '无法确定上次请求是否已完成，系统不会自动重发；请先核对供应商记录。'
      if (status === 'WAITING') return '会话正在等待后续结果，可继续试运行读取新结果或执行剩余步骤。'
      if (status === 'RUNNING') return '正在执行原配置，页面会自动查询状态；离开页面不会重复发起调用。'
      if (status === 'EXPIRED') return '本次会话已超过有效期，不能继续；原有记录仍会保留。'
      if (status === 'CANCELLED') return '已停止本次会话的后续处理，原有调用记录仍会保留。'
      return '本次结果来自开始时的配置和参数，不会自动保存或审批接口。'
    },
    formatTrialTime(value) {
      if (!['string', 'number'].includes(typeof value) || String(value).trim() === '') return '—'
      const raw = typeof value === 'string' && /^[+-]?\d+$/.test(value.trim()) ? Number(value) : value
      const date = new Date(raw)
      if (!Number.isFinite(date.getTime())) return '—'
      const parts = Object.fromEntries(new Intl.DateTimeFormat('zh-CN', {
        year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
      }).formatToParts(date).map(part => [part.type, part.value]))
      return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`
    },
    trialHistoryLabel(item) { return `${item.apiName || '未命名接口'} · ${this.trialStatusLabel(item.status)} · ${this.formatTrialTime(item.createdAt)}` },
    async loadTrialHistory() {
      const context = this.initializationId
      const requestId = this.trialHistoryRequestId = (this.trialHistoryRequestId || 0) + 1
      this.trialHistoryLoading = true
      this.trialHistoryError = ''
      try {
        const res = await listApiConfigTrials()
        if (context !== this.initializationId || requestId !== this.trialHistoryRequestId) return
        this.trialHistory = Array.isArray(res?.data) ? res.data : []
      } catch (error) {
        if (context === this.initializationId && requestId === this.trialHistoryRequestId) this.trialHistoryError = error.message || '最近试运行加载失败，请重试'
      } finally {
        if (context === this.initializationId && requestId === this.trialHistoryRequestId) this.trialHistoryLoading = false
      }
    },
    async runTrialRequest(loader, sourceSignature = this.trialSourceSignature, recovered = this.trialRecovered) {
      if (this.trialLoading) return null
      const context = this.initializationId
      const requestId = this.trialRequestId = (this.trialRequestId || 0) + 1
      this.stopTrialPolling()
      this.trialLoading = true
      this.trialError = ''
      try {
        const res = await loader()
        if (context !== this.initializationId || requestId !== this.trialRequestId) return null
        if (!res?.data?.trialId) throw new Error('服务端未返回试运行会话，请从最近会话查找或沿用原请求重试')
        this.trial = res.data
        this.trialSourceSignature = sourceSignature || ''
        this.trialRecovered = Boolean(recovered)
        this.invokeResultText = this.trial.result == null ? '' : JSON.stringify(this.trial.result, null, 2)
        return this.trial
      } catch (error) {
        if (context === this.initializationId && requestId === this.trialRequestId) this.trialError = error.message || '试运行请求未完成，请查询状态后重试'
        return null
      } finally {
        if (context === this.initializationId && requestId === this.trialRequestId) {
          this.trialLoading = false
          if (!this.trialError && this.trial?.status === 'RUNNING') {
            this.trialPollTimer = setTimeout(() => { this.trialPollTimer = null; this.refreshTrial() }, 1500)
          }
        }
      }
    },
    async selectTrial(trialId) {
      if (!trialId) return
      const sameTrial = this.trial?.trialId === trialId
      const selected = await this.runTrialRequest(() => getApiConfigTrial(trialId), sameTrial ? this.trialSourceSignature : '', sameTrial ? this.trialRecovered : true)
      if (selected && selected.requestKey === this.trialRequestKey) this.trialRequestKey = ''
    },
    refreshTrial() { return this.trial?.trialId ? this.runTrialRequest(() => getApiConfigTrial(this.trial.trialId)) : Promise.resolve(null) },
    async resumeTrial() {
      if (this.trial?.canResume !== true) return
      const id = this.trial.trialId
      const result = await this.runTrialRequest(() => resumeApiConfigTrial(id))
      if (result) this.loadTrialHistory()
    },
    async cancelTrial() {
      if (this.trial?.canCancel !== true) return
      const id = this.trial.trialId
      const result = await this.runTrialRequest(() => cancelApiConfigTrial(id))
      if (result) this.loadTrialHistory()
    },
    async runRequestPreview() {
      if (this.previewLoading || this.initializing || this.initializationError) return
      let params, config
      try { params = this.parseTestParams(); config = this.normalizeForm(this.form) }
      catch (error) { this.$message.error(error.message); return }
      const context = this.initializationId
      const requestId = this.previewRequestId = (this.previewRequestId || 0) + 1
      const signature = this.capturePreviewSignature()
      this.testFeedback = ''
      this.previewLoading = true
      try {
        const res = await previewApiConfigRequest(this.form.id || 0, {
          config,
          params,
          previewToken: this.previewToken || '',
        })
        if (context !== this.initializationId || requestId !== this.previewRequestId) return
        if (signature !== this.capturePreviewSignature()) { this.testFeedback = '配置或参数已变化，已忽略此前的预览，请重新生成'; return }
        this.requestPreviewText = JSON.stringify(res.data || {}, null, 2)
        this.previewSourceSignature = signature
        this.$message.success('请求预览已生成，未访问外部地址')
      } catch (error) {
        if (context === this.initializationId && requestId === this.previewRequestId) this.testFeedback = error.message || '请求预览失败，请重试'
      } finally {
        if (context === this.initializationId && requestId === this.previewRequestId) this.previewLoading = false
      }
    },
    async runInvokeApi() {
      if (this.invokeLoading || this.trialLoading || this.initializing || this.initializationError) return
      const requestedContext = this.initializationId
      const requestedForm = this.form
      if (!this.form.id) {
        if (this.$refs?.form?.validate) {
          try { if (await this.$refs.form.validate() === false) return } catch { return }
        }
        if (requestedContext !== this.initializationId || requestedForm !== this.form || this.invokeLoading || this.trialLoading) return
        if (!this.form.datasourceId || !String(this.form.apiCode || '').trim() || !String(this.form.apiName || '').trim() || !String(this.form.endpointUrl || '').trim()) {
          this.$message.error('请先填写数据源、接口编码、接口名称和接口地址')
          return
        }
      }
      let params, config
      try {
        params = this.parseTestParams()
        config = this.normalizeForm(this.form)
      } catch (error) { this.$message.error(error.message); return }
      const context = this.initializationId
      const requestId = this.invokeRequestId = (this.invokeRequestId || 0) + 1
      const signature = this.captureTestSignature()
      this.testFeedback = ''
      this.invokeLoading = true
      try {
        if (!this.form.id) {
          this.trialRequestKey = this.trialRequestKey || newApiId()
          const requestKey = this.trialRequestKey
          const trial = await this.runTrialRequest(() => createApiConfigTrial({ requestKey, config, params }), signature, false)
          if (trial && context === this.initializationId && requestId === this.invokeRequestId) {
            this.trialRequestKey = ''
            this.loadTrialHistory()
          }
          return
        }
        const res = await invokeApiConfigPreview(this.form.id, {
          config,
          params,
        })
        if (context !== this.initializationId || requestId !== this.invokeRequestId) return
        if (signature !== this.captureTestSignature()) { this.testFeedback = '配置或参数已变化，已忽略此前的调用结果，请重新测试'; return }
        this.invokeResultText = JSON.stringify(res.data || {}, null, 2)
        this.invokeSourceSignature = signature
        this.$message.success('调用完成')
      } catch (error) {
        if (context === this.initializationId && requestId === this.invokeRequestId) this.testFeedback = error.message || '调用失败，请检查后重试'
      } finally {
        if (context === this.initializationId && requestId === this.invokeRequestId) this.invokeLoading = false
      }
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
    parseJsonText(value, label) {
      if (value == null || String(value).trim() === '') return {}
      try {
        return JSON.parse(value)
      } catch (e) {
        throw new Error(label + '不是合法 JSON：' + e.message)
      }
    },
    parseJsonOrString(value) {
      if (value == null || String(value).trim() === '') return null
      try {
        return JSON.parse(value)
      } catch (e) {
        return value
      }
    },
    jsonTextOrBlank(value) {
      if (
        !value ||
        typeof value !== 'object' ||
        Object.keys(value).length === 0
      )
        return ''
      return this.stringifyJson(value)
    },
    stringifyJson(value) {
      return JSON.stringify(value, null, 2)
    },
    assertJson(value, label) {
      if (value == null || value === '') return
      try {
        JSON.parse(value)
      } catch (e) {
        throw new Error(label + '不是合法 JSON：' + e.message)
      }
    },
    blankToNull(value) {
      return value == null || String(value).trim() === '' ? null : value
    },
  },
}
</script>

<style lang="scss" scoped>
.api-detail-page {
  .detail-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 14px;
  }
  .detail-title {
    font-size: 20px;
    font-weight: 700;
    color: var(--tianshu-text-primary);
  }
  .detail-meta,
  .field-help,
  .panel-subtitle {
    color: var(--tianshu-text-tertiary);
  }
  .detail-meta {
    font-size: 13px;
    margin-top: 4px;
  }
  .detail-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .detail-form {
    background: var(--tianshu-bg-surface);
    border: 1px solid var(--tianshu-border-subtle);
    border-radius: 4px;
    padding: 16px;
  }
  .token-header-name-item :deep(.el-form-item__label) {
    white-space: nowrap;
  }
  .basic-panel {
    border-bottom: 1px solid var(--tianshu-border-subtle);
    padding-bottom: 8px;
    margin-bottom: 12px;
  }
  .config-group-bar {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
    margin: 14px 0 6px;
  }
  .config-group-bar button {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    padding: 11px 14px;
    border: 1px solid var(--tianshu-border-subtle);
    border-radius: 7px;
    background: var(--tianshu-bg-surface);
    color: var(--tianshu-text-tertiary);
    cursor: pointer;
    text-align: left;
  }
  .config-group-bar button:hover,
  .config-group-bar button.is-active {
    border-color: var(--tianshu-info-border);
    background: var(--tianshu-info-bg);
  }
  .config-group-bar strong {
    color: var(--tianshu-text-primary);
    font-size: 13px;
  }
  .config-group-bar small {
    margin-top: 3px;
  }
  .panel-heading,
  .section-toolbar {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 12px;
  }
  .panel-title,
  .section-title,
  .reference-title {
    color: var(--tianshu-text-primary);
    font-weight: 700;
  }
  .panel-subtitle,
  .field-help {
    font-size: 12px;
    line-height: 1.6;
    margin-top: 4px;
  }
  .config-tabs {
    margin-top: 6px;
  }
  .tab-section {
    padding: 12px 0 4px;
  }
  .query-toolbar {
    margin-top: 18px;
  }
  .compact {
    align-items: center;
  }
  .config-table {
    width: 100%;
  }
  .mapping-layout {
    display: flex;
    gap: 12px;
    min-width: 0;
    align-items: flex-start;
  }
  .mapping-main {
    flex: 1;
    min-width: 0;
    overflow: hidden;
  }
  .field-reference {
    width: clamp(260px, 30%, 360px);
    min-width: 0;
    max-height: min(52vh, 560px);
    overflow: auto;
    border: 1px solid var(--tianshu-border-subtle);
    border-radius: 4px;
    padding: 10px;
    background: var(--tianshu-bg-soft);
  }
  .reference-title {
    margin-bottom: 8px;
  }
  .empty-state {
    color: var(--tianshu-text-tertiary);
    font-size: 12px;
    line-height: 1.6;
    background: var(--tianshu-bg-soft);
    border: 1px dashed var(--tianshu-border);
    border-radius: 4px;
    padding: 10px 12px;
  }
  .response-condition-card {
    border: 1px solid var(--tianshu-border-subtle);
    border-radius: 4px;
    padding: 12px;
    margin-bottom: 12px;
    background: var(--tianshu-bg-surface);
  }
  .config-card {
    border: 1px solid var(--tianshu-border-subtle);
    border-radius: 4px;
    background: var(--tianshu-bg-soft);
    padding: 12px;
    margin-bottom: 14px;
  }
  .config-card > .field-help {
    margin-bottom: 10px;
  }
  .payload-capture-section {
    .payload-capture-grid {
      margin-top: 12px;
    }
    .payload-capture-card {
      height: 100%;
      box-sizing: border-box;
    }
    .payload-path-list {
      width: 100%;
    }
    .payload-path-row {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;
    }
    .payload-path-row .el-input {
      flex: 1;
      min-width: 0;
    }
    .payload-capture-warning {
      margin: -4px 0 12px;
      color: var(--tianshu-warning-text);
    }
  }
  .cache-key-row {
    display: grid;
    grid-template-columns: 28px minmax(220px, 1fr) auto auto auto;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }
  .cache-key-order {
    width: 24px;
    height: 24px;
    line-height: 24px;
    text-align: center;
    border-radius: 50%;
    background: var(--tianshu-bg-muted);
    color: var(--tianshu-text-secondary);
    font-size: 12px;
  }
  .response-condition-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 10px;
  }
  .response-condition-title {
    color: var(--tianshu-text-primary);
    font-weight: 700;
  }
  .response-condition-tree {
    border-top: 1px solid var(--tianshu-border-subtle);
    padding-top: 10px;
  }
  .test-toolbar {
    border-top: 1px solid var(--tianshu-border-subtle);
    padding-top: 14px;
    margin-top: 8px;
  }
  .api-trial-panel { padding: 12px; margin-bottom: 16px; border: 1px solid var(--tianshu-border-subtle); border-radius: 6px; background: var(--tianshu-bg-soft); }
  .api-trial-panel > .el-select { width: min(100%, 640px); margin-bottom: 8px; }
  .api-trial-panel .el-alert { margin: 8px 0; }
  .api-trial-heading, .api-trial-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 10px; }
  .api-trial-heading > span { color: var(--tianshu-text-secondary); overflow-wrap: anywhere; }
  .btn-delete {
    color: var(--tianshu-danger-text);
  }
  code {
    color: var(--tianshu-info-text);
    background: var(--tianshu-info-bg);
    border-radius: 3px;
    padding: 0 4px;
  }
  :deep(.el-tabs__content) {
    overflow: visible;
  }
}
@media (max-width: 1100px) {
  .api-detail-page {
    .mapping-layout {
      display: block;
    }
    .field-reference {
      width: auto;
      margin-top: 12px;
    }
    .cache-key-row {
      grid-template-columns: 28px minmax(0, 1fr) auto;
    }
  }
}
</style>
