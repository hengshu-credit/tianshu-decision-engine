<template>
  <div class="uiue-list-page api-detail-page">
    <div class="detail-header">
      <div>
        <div class="detail-title">
          {{ isCreateMode ? '新建外数 API 接口' : '编辑外数 API 接口' }}
        </div>
        <div class="detail-meta">
          {{ form.apiName || '未命名接口' }} /
          {{ form.apiCode || '待填写编码' }}
        </div>
      </div>
      <div class="detail-actions">
        <el-button size="small" @click="handleBack">返回</el-button>
        <el-button
          size="small"
          type="primary"
          :loading="saving"
          @click="handleSave"
          >保存</el-button
        >
      </div>
    </div>

    <el-form
      ref="form"
      :model="form"
      :rules="rules"
      label-width="118px"
      size="small"
      class="detail-form"
    >
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
              <el-select
                v-model="form.datasourceId"
                filterable
                placeholder="请选择数据源"
                style="width: 100%"
                @change="onDatasourceChange"
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
          <el-col :lg="8" :md="24">
            <el-form-item label="请求对象">
              <el-select
                v-model="form.requestObjectId"
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
              <div class="field-help">用于生成请求字段参照和测试 JSON。</div>
            </el-form-item>
          </el-col>
          <el-col :lg="8" :md="24">
            <el-form-item label="响应对象">
              <el-select
                v-model="form.responseObjectId"
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
                用于生成响应字段参照，接口变量读取映射后的 body。
              </div>
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

      <el-tabs v-model="activeConfigTab" class="config-tabs">
        <el-tab-pane label="接口鉴权" name="auth">
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
                    active-text="401/403 刷新一次" /></el-form-item
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

            <div
              v-if="form.authMode === 'INHERIT' || form.authMode === 'NONE'"
              class="empty-state"
            >
              {{
                form.authMode === 'INHERIT'
                  ? '当前接口使用数据源默认鉴权配置。'
                  : '当前接口不附加鉴权信息。'
              }}
            </div>
            <el-row v-else-if="form.authMode === 'BASIC'" :gutter="12">
              <el-col :lg="12" :md="24">
                <el-form-item label="用户名">
                  <el-input
                    v-model="apiAuthConfig.username"
                    placeholder="支持 ${appId} 从测试参数取值"
                  />
                </el-form-item>
              </el-col>
              <el-col :lg="12" :md="24">
                <el-form-item label="密码">
                  <el-input
                    v-model="apiAuthConfig.password"
                    show-password
                    placeholder="支持 ${secret} 从测试参数取值"
                  />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item
              v-else-if="form.authMode === 'BEARER'"
              label="Bearer Token"
            >
              <el-input
                v-model="apiAuthConfig.token"
                type="textarea"
                :rows="3"
                placeholder="支持 ${token} 从测试参数取值"
              />
            </el-form-item>
            <el-row v-else-if="form.authMode === 'API_KEY'" :gutter="12">
              <el-col :lg="8" :md="24">
                <el-form-item label="放置位置">
                  <el-select
                    v-model="apiAuthConfig.location"
                    style="width: 100%"
                  >
                    <el-option label="请求头" value="HEADER" />
                    <el-option label="Query参数" value="QUERY" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :lg="8" :md="24">
                <el-form-item label="Key名称">
                  <el-input
                    v-model="apiAuthConfig.name"
                    placeholder="如 X-API-Key"
                  />
                </el-form-item>
              </el-col>
              <el-col :lg="8" :md="24">
                <el-form-item label="Key取值">
                  <el-input
                    v-model="apiAuthConfig.value"
                    placeholder="支持 ${apiKey}"
                  />
                </el-form-item>
              </el-col>
            </el-row>
            <div
              v-else-if="
                form.authMode === 'TOKEN_API' || form.authMode === 'OAUTH2'
              "
            >
              <el-row :gutter="12">
                <el-col :lg="10" :md="24">
                  <el-form-item label="Token地址">
                    <el-input
                      v-model="apiAuthConfig.tokenUrl"
                      placeholder="/oauth/token 或完整 URL"
                    />
                  </el-form-item>
                </el-col>
                <el-col :lg="5" :md="12">
                  <el-form-item label="方法">
                    <el-select
                      v-model="apiAuthConfig.method"
                      style="width: 100%"
                    >
                      <el-option
                        v-for="method in httpMethods"
                        :key="method"
                        :label="method"
                        :value="method"
                      />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :lg="9" :md="12">
                  <el-form-item label="Content-Type">
                    <el-select
                      v-model="apiAuthConfig.contentType"
                      filterable
                      allow-create
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
              <el-row :gutter="12">
                <el-col :lg="12" :md="24">
                  <el-form-item label="鉴权请求头">
                    <monaco-editor
                      v-model:value="apiAuthConfig.headers"
                      language="json"
                      height="120px"
                    />
                  </el-form-item>
                </el-col>
                <el-col :lg="12" :md="24">
                  <el-form-item label="鉴权请求体">
                    <monaco-editor
                      v-model:value="apiAuthConfig.body"
                      language="json"
                      height="120px"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="12">
                <el-col :lg="12" :md="24">
                  <el-form-item label="Token路径">
                    <el-input
                      v-model="apiAuthConfig.tokenPath"
                      placeholder="如 body.access_token 或 headers.Authorization"
                    />
                  </el-form-item>
                </el-col>
                <el-col :lg="12" :md="24">
                  <el-form-item label="过期秒路径">
                    <el-input
                      v-model="apiAuthConfig.expiresInPath"
                      placeholder="如 body.expires_in，可为空"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="12">
                <el-col :lg="8" :md="24">
                  <el-form-item label="Token放置方式">
                    <el-select
                      v-model="apiAuthConfig.tokenPlacement"
                      style="width: 100%"
                    >
                      <el-option label="写入请求Header" value="HEADER" />
                      <el-option label="仅供请求脚本使用" value="SCRIPT_ONLY" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :lg="8" :md="24">
                  <el-form-item label="Token Header名称">
                    <el-input
                      v-model="apiAuthConfig.tokenHeaderName"
                      placeholder="默认 Authorization；冰鉴填写 token_id"
                    />
                  </el-form-item>
                </el-col>
                <el-col :lg="8" :md="24">
                  <el-form-item label="Token前缀">
                    <el-input
                      v-model="apiAuthConfig.tokenPrefix"
                      placeholder="默认 Bearer；冰鉴留空"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
            </div>
            <el-form-item v-else label="自定义配置">
              <monaco-editor
                v-model:value="form.authApiConfig"
                language="json"
                height="180px"
              />
            </el-form-item>
          </div>
        </el-tab-pane>

        <el-tab-pane label="连接&流控" name="connection">
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
                      :min="0"
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

        <el-tab-pane label="脚本处理" name="scripts">
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
                  <el-input
                    v-model="row.value"
                    :type="row.sensitive ? 'password' : 'text'"
                    :show-password="row.sensitive"
                    autocomplete="new-password"
                  />
                </template>
              </el-table-column>
              <el-table-column label="敏感" width="90" align="center">
                <template v-slot="{ row }">
                  <el-switch v-model="row.sensitive" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template v-slot="{ $index }">
                  <el-button
                    link
                    size="small"
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
              state 共享仅本次调用有效的临时值。
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="请求头" name="headers">
          <div class="tab-section">
            <div class="section-toolbar">
              <div>
                <div class="section-title">Header 配置</div>
                <div class="field-help">
                  Header 只放 HTTP 头，例如认证、追踪号、渠道号；GET 的 URL
                  参数请在“请求体”页签配置。
                </div>
              </div>
              <el-button size="small" :icon="ElIconPlus" @click="addHeaderRow"
                >添加 Header</el-button
              >
            </div>
            <el-table
              :data="headerRows"
              border
              size="small"
              class="config-table"
            >
              <el-table-column label="Header名称" min-width="180">
                <template v-slot="{ row }">
                  <el-input v-model="row.name" placeholder="如 X-Request-Id" />
                </template>
              </el-table-column>
              <el-table-column label="取值" min-width="240">
                <template v-slot="{ row }">
                  <el-input v-model="row.value" placeholder="如 ${requestId}" />
                </template>
              </el-table-column>
              <el-table-column label="说明" min-width="180">
                <template v-slot="{ row }">
                  <el-input v-model="row.remark" placeholder="业务含义，可选" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template v-slot="{ $index }">
                  <el-button
                    link
                    size="small"
                    class="btn-delete"
                    @click="removeRow(headerRows, $index)"
                    >删除</el-button
                  >
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="请求参数" name="query">
          <div class="tab-section">
            <div class="section-toolbar">
              <div>
                <div class="section-title">URL Query 参数</div>
                <div class="field-help">
                  这里配置拼接到 URL 上的查询参数，GET/DELETE
                  的查询条件放这里，不会放入 Header 或
                  Body。取值支持固定值、<code>$.字段路径</code>、<code>${字段路径}</code>。
                </div>
              </div>
              <el-button size="small" :icon="ElIconPlus" @click="addQueryRow"
                >添加 Query</el-button
              >
            </div>
            <el-table :data="queryRows" border size="small" class="config-table">
              <el-table-column label="参数名" min-width="180">
                <template v-slot="{ row }">
                  <el-input v-model="row.name" placeholder="如 mobile_no" />
                </template>
              </el-table-column>
              <el-table-column label="取值" min-width="240">
                <template v-slot="{ row }">
                  <el-input v-model="row.value" placeholder="如 $.mobile_no" />
                </template>
              </el-table-column>
              <el-table-column label="说明" min-width="180">
                <template v-slot="{ row }">
                  <el-input v-model="row.remark" placeholder="业务含义，可选" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template v-slot="{ $index }">
                  <el-button
                    link
                    size="small"
                    class="btn-delete"
                    @click="removeRow(queryRows, $index)"
                    >删除</el-button
                  >
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="请求体" name="request">
          <div class="tab-section">
            <div class="section-toolbar">
              <div>
                <div class="section-title">请求参数生成方式</div>
                <div class="field-help">
                  表单适合业务配置；JSON
                  适合复制技术文档。两边会自动互相转换，保存时使用同一份映射。
                </div>
              </div>
              <el-radio-group v-model="requestBodyMode" size="small">
                <el-radio-button value="MAPPING">表单配置</el-radio-button>
                <el-radio-button value="JSON">JSON配置</el-radio-button>
              </el-radio-group>
            </div>

            <div v-if="requestBodyMode === 'MAPPING'" class="mapping-layout">
              <div class="mapping-main">
                <div class="section-toolbar compact">
                  <div class="field-help">
                    接口字段路径是发给外部 API
                    的字段；右侧入参路径是规则引擎已有变量。填写任意一边时，另一边会按同名字段自动补齐。
                  </div>
                  <div>
                    <el-button
                      size="small"
                      :disabled="requestFieldOptions.length === 0"
                      @click="fillRequestRowsFromRequestObject"
                      >按请求对象生成</el-button
                    >
                    <el-button
                      size="small"
                      :icon="ElIconPlus"
                      @click="addRequestMappingRow"
                      >添加字段</el-button
                    >
                  </div>
                </div>
                <el-table
                  :data="requestMappingRows"
                  border
                  size="small"
                  class="config-table"
                >
                  <el-table-column label="接口字段路径" min-width="190">
                    <template v-slot="{ row, $index }">
                      <el-input
                        v-model="row.targetPath"
                        placeholder="如 customer.certNo"
                        @update:model-value="onRequestTargetInput(row, $index)"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="引擎入参路径/固定值" min-width="240">
                    <template v-slot="{ row, $index }">
                      <el-input
                        v-model="row.sourcePath"
                        placeholder="如 $.customer.idNo 或 ONLINE"
                        @update:model-value="onRequestSourceInput(row, $index)"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="说明" min-width="180">
                    <template v-slot="{ row }">
                      <el-input
                        v-model="row.remark"
                        placeholder="业务含义，可选"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="80" align="center">
                    <template v-slot="{ $index }">
                      <el-button
                        link
                        size="small"
                        class="btn-delete"
                        @click="removeRow(requestMappingRows, $index)"
                        >删除</el-button
                      >
                    </template>
                  </el-table-column>
                </el-table>
              </div>
              <div class="field-reference">
                <div class="reference-title">请求对象字段</div>
                <div
                  v-if="requestFieldOptions.length === 0"
                  class="empty-state"
                >
                  选择请求对象后显示字段。
                </div>
                <el-table
                  v-else
                  :data="requestFieldOptions"
                  size="small"
                  height="260"
                >
                  <el-table-column
                    label="字段"
                    min-width="120"
                    show-overflow-tooltip
                  >
                    <template v-slot="{ row }">{{
                      fieldDisplayName(row)
                    }}</template>
                  </el-table-column>
                  <el-table-column
                    label="路径"
                    min-width="150"
                    show-overflow-tooltip
                  >
                    <template v-slot="{ row }">{{
                      fieldScriptPath(row)
                    }}</template>
                  </el-table-column>
                  <el-table-column label="操作" width="58" align="center">
                    <template v-slot="{ row }">
                      <el-button
                        link
                        size="small"
                        @click="useRequestField(row)"
                        >使用</el-button
                      >
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>

            <div v-else>
              <monaco-editor
                v-model:value="requestMappingJsonText"
                language="json"
                height="320px"
              />
              <div class="field-help">
                JSON 中左侧字段是接口字段，右侧可写
                <code>$.mobile_no</code> 或固定值。切回表单时会自动拆成字段行。
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="响应体" name="response">
          <div class="tab-section">
            <div class="section-toolbar">
              <div>
                <div class="section-title">响应映射</div>
                <div class="field-help">
                  表单适合配置稳定响应；条件分支用于同一接口不同返回结构；JSON
                  适合复制技术文档。接口变量读取映射后的
                  <code>body.输出字段</code>。
                </div>
              </div>
              <el-radio-group v-model="responseMappingMode" size="small">
                <el-radio-button value="MAPPING">表单配置</el-radio-button>
                <el-radio-button value="JSON">高级 JSON</el-radio-button>
              </el-radio-group>
            </div>

            <div
              v-if="responseMappingMode === 'MAPPING'"
              class="mapping-layout"
            >
              <div class="mapping-main">
                <div class="section-toolbar compact">
                  <div class="field-help">
                    来源路径从接口原始响应读取，常用
                    <code>body.data.score</code>，也可用逗号分隔多个兜底路径。
                  </div>
                  <div>
                    <el-button
                      size="small"
                      :disabled="responseFieldOptions.length === 0"
                      @click="fillResponseRowsFromResponseObject"
                      >按响应对象生成</el-button
                    >
                    <el-button
                      size="small"
                      :icon="ElIconPlus"
                      @click="addResponseMappingRow"
                      >添加字段</el-button
                    >
                  </div>
                </div>
                <el-table
                  :data="responseMappingRows"
                  border
                  size="small"
                  class="config-table"
                >
                  <el-table-column label="输出字段" min-width="160">
                    <template v-slot="{ row }">
                      <el-input
                        v-model="row.outputField"
                        placeholder="如 score"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="来源路径" min-width="240">
                    <template v-slot="{ row }">
                      <el-input
                        v-model="row.sourcePath"
                        placeholder="如 body.data.score"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="默认值" min-width="150">
                    <template v-slot="{ row }">
                      <el-input
                        v-model="row.defaultValue"
                        placeholder="可选，如 0"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="80" align="center">
                    <template v-slot="{ $index }">
                      <el-button
                        link
                        size="small"
                        class="btn-delete"
                        @click="removeRow(responseMappingRows, $index)"
                        >删除</el-button
                      >
                    </template>
                  </el-table-column>
                </el-table>

                <div class="section-toolbar compact condition-toolbar">
                  <div>
                    <div class="section-title">条件响应结构</div>
                    <div class="field-help">
                      按原始响应路径配置不同结构的读取规则；条件编辑器支持且/或嵌套，兜底分支放在最后。
                    </div>
                  </div>
                  <div>
                    <el-button
                      size="small"
                      :icon="ElIconPlus"
                      @click="addResponseConditionRow(false)"
                      >添加条件分支</el-button
                    >
                    <el-button
                      size="small"
                      @click="addResponseConditionRow(true)"
                      >添加兜底分支</el-button
                    >
                  </div>
                </div>
                <div
                  v-if="responseConditionRows.length === 0"
                  class="empty-state"
                >
                  没有条件分支时，仅使用上方基础响应映射。
                </div>
                <div
                  v-for="(row, index) in responseConditionRows"
                  :key="'response-condition-' + index"
                  class="response-condition-card"
                >
                  <div class="response-condition-head">
                    <div class="response-condition-title">
                      {{
                        row.fallback ? '兜底分支' : '条件分支 #' + (index + 1)
                      }}
                    </div>
                    <div>
                      <el-switch
                        v-model="row.fallback"
                        active-text="兜底"
                        inactive-text="条件"
                      />
                      <el-button
                        link
                        size="small"
                        class="btn-delete"
                        @click="removeRow(responseConditionRows, index)"
                        >删除</el-button
                      >
                    </div>
                  </div>
                  <el-row :gutter="12">
                    <el-col :lg="8" :md="24">
                      <el-form-item label="输出字段">
                        <el-input
                          v-model="row.outputField"
                          placeholder="如 score"
                        />
                      </el-form-item>
                    </el-col>
                    <el-col :lg="10" :md="24">
                      <el-form-item label="读取路径">
                        <el-input
                          v-model="row.sourcePath"
                          placeholder="如 body.data.score，可逗号分隔多个路径"
                        />
                      </el-form-item>
                    </el-col>
                    <el-col :lg="6" :md="24">
                      <el-form-item label="默认值">
                        <el-input
                          v-model="row.defaultValue"
                          placeholder="可选，如 0"
                        />
                      </el-form-item>
                    </el-col>
                  </el-row>
                  <div v-if="!row.fallback" class="response-condition-tree">
                    <condition-group-editor
                      :group="row.conditionRoot"
                      :vars="responseConditionVarOptions"
                      :allow-custom-var="true"
                    />
                  </div>
                </div>
              </div>
              <div class="field-reference">
                <div class="reference-title">响应对象字段</div>
                <div
                  v-if="responseFieldOptions.length === 0"
                  class="empty-state"
                >
                  选择响应对象后显示字段。
                </div>
                <el-table
                  v-else
                  :data="responseFieldOptions"
                  size="small"
                  height="260"
                >
                  <el-table-column
                    label="字段"
                    min-width="120"
                    show-overflow-tooltip
                  >
                    <template v-slot="{ row }">{{
                      fieldDisplayName(row)
                    }}</template>
                  </el-table-column>
                  <el-table-column
                    label="引擎读取"
                    min-width="150"
                    show-overflow-tooltip
                  >
                    <template v-slot="{ row }"
                      >body.{{ outputFieldName(row) }}</template
                    >
                  </el-table-column>
                  <el-table-column label="操作" width="58" align="center">
                    <template v-slot="{ row }">
                      <el-button
                        link
                        size="small"
                        @click="useResponseField(row)"
                        >使用</el-button
                      >
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
            <div v-else>
              <monaco-editor
                v-model:value="responseMappingJsonText"
                language="json"
                height="320px"
              />
              <div class="field-help">
                支持路径数组兜底和 cases 条件分支，保存前会校验 JSON。
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane
          v-if="form.requestMode === 'ASYNC'"
          label="异步回调"
          name="async"
        >
          <div class="tab-section">
            <el-row :gutter="12">
              <el-col :lg="8" :md="24">
                <el-form-item label="结果获取方式">
                  <el-radio-group v-model="form.asyncResultMode">
                    <el-radio-button value="POLL">引擎轮询</el-radio-button>
                    <el-radio-button value="CALLBACK">外部回调</el-radio-button>
                  </el-radio-group>
                </el-form-item>
              </el-col>
              <el-col :lg="8" :md="12">
                <el-form-item label="任务号路径">
                  <el-input
                    v-model="asyncShared.taskIdPath"
                    placeholder="如 body.taskId"
                  />
                </el-form-item>
              </el-col>
              <el-col :lg="8" :md="12">
                <el-form-item label="最终结果路径">
                  <el-input
                    v-model="form.asyncResultPath"
                    placeholder="如 body.data.result"
                  />
                </el-form-item>
              </el-col>
            </el-row>

            <div v-if="form.asyncResultMode === 'POLL'">
              <el-row :gutter="12">
                <el-col :lg="12" :md="24">
                  <el-form-item label="结果查询地址">
                    <el-input
                      v-model="asyncPollConfig.resultEndpointUrl"
                      placeholder="/v1/report/result/${taskId}"
                    />
                  </el-form-item>
                </el-col>
                <el-col :lg="4" :md="8">
                  <el-form-item label="查询方法">
                    <el-select
                      v-model="asyncPollConfig.requestMethod"
                      style="width: 100%"
                    >
                      <el-option
                        v-for="method in httpMethods"
                        :key="method"
                        :label="method"
                        :value="method"
                      />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :lg="4" :md="8">
                  <el-form-item label="间隔毫秒">
                    <el-input-number
                      v-model="asyncPollConfig.intervalMs"
                      :min="500"
                      :step="500"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
                <el-col :lg="4" :md="8">
                  <el-form-item label="最大次数">
                    <el-input-number
                      v-model="asyncPollConfig.maxAttempts"
                      :min="1"
                      :max="200"
                      style="width: 100%"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="12">
                <el-col :lg="8" :md="24">
                  <el-form-item label="状态路径">
                    <el-input
                      v-model="asyncPollConfig.statusPath"
                      placeholder="如 body.status"
                    />
                  </el-form-item>
                </el-col>
                <el-col :lg="8" :md="24">
                  <el-form-item label="成功值">
                    <el-input
                      v-model="asyncPollConfig.successValue"
                      placeholder="如 SUCCESS 或 1"
                    />
                  </el-form-item>
                </el-col>
                <el-col :lg="8" :md="24">
                  <el-form-item label="结果路径">
                    <el-input
                      v-model="asyncPollConfig.resultPath"
                      placeholder="如 body.data"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
            </div>

            <div v-else>
              <el-row :gutter="12">
                <el-col :lg="12" :md="24">
                  <el-form-item label="引擎回调地址">
                    <el-input
                      v-model="form.asyncCallbackUrl"
                      :placeholder="engineCallbackPlaceholder"
                    />
                    <div class="field-help">
                      把该地址配置给外部服务，外部服务完成后把任务号和结果通知给引擎。
                    </div>
                  </el-form-item>
                </el-col>
                <el-col :lg="12" :md="24">
                  <el-form-item label="回调任务号路径">
                    <el-input
                      v-model="asyncCallbackConfig.taskIdPath"
                      placeholder="如 body.taskId"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="12">
                <el-col :lg="8" :md="24">
                  <el-form-item label="回调状态路径">
                    <el-input
                      v-model="asyncCallbackConfig.statusPath"
                      placeholder="如 body.status"
                    />
                  </el-form-item>
                </el-col>
                <el-col :lg="8" :md="24">
                  <el-form-item label="成功值">
                    <el-input
                      v-model="asyncCallbackConfig.successValue"
                      placeholder="如 SUCCESS 或 1"
                    />
                  </el-form-item>
                </el-col>
                <el-col :lg="8" :md="24">
                  <el-form-item label="回调结果路径">
                    <el-input
                      v-model="asyncCallbackConfig.resultPath"
                      placeholder="如 body.data"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="12">
                <el-col :lg="12" :md="24">
                  <el-form-item label="签名Header">
                    <el-input
                      v-model="asyncCallbackConfig.signatureHeader"
                      placeholder="如 X-Signature，可为空"
                    />
                  </el-form-item>
                </el-col>
                <el-col :lg="12" :md="24">
                  <el-form-item label="签名密钥">
                    <el-input
                      v-model="asyncCallbackConfig.signatureSecret"
                      show-password
                      placeholder="用于验签，可为空"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="异常&重试" name="retry">
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

        <el-tab-pane label="缓存&计费" name="billing">
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
                    按下列顺序组合并生成脱敏摘要。任一字段缺失时绕过缓存，并记录
                    CACHE_KEY_INCOMPLETE。
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
                <el-select
                  v-model="row.path"
                  filterable
                  allow-create
                  default-first-option
                  size="small"
                  placeholder="如 customer.name"
                  style="width: 100%"
                >
                  <el-option
                    v-for="item in requestPathOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
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

        <el-tab-pane label="接口测试" name="test">
          <div class="tab-section">
            <div class="section-toolbar">
              <div>
                <div class="section-title">API 调用测试</div>
                <div class="field-help">
                  每个 API 只保存一份测试样例；生成测试 JSON
                  会根据请求对象字段类型创建空字符串、0、false、{}、[]
                  等默认值。
                </div>
              </div>
              <div>
                <el-button size="small" @click="regenerateTestParams"
                  >生成测试 JSON</el-button
                >
                <el-button size="small" @click="loadSavedSample"
                  >加载已存样例</el-button
                >
                <el-button
                  size="small"
                  :disabled="!form.id"
                  @click="saveCurrentSample"
                  >保存样例</el-button
                >
                <el-button
                  size="small"
                  type="success"
                  :disabled="!form.datasourceId"
                  :loading="previewLoading"
                  @click="runRequestPreview"
                  >生成请求预览</el-button
                >
                <el-button
                  size="small"
                  type="primary"
                  :disabled="!form.id"
                  :loading="invokeLoading"
                  @click="runInvokeApi"
                  >执行测试</el-button
                >
              </div>
            </div>
            <el-form-item label="预览Token">
              <el-input
                v-model="previewToken"
                placeholder="Token/OAuth接口预览可填占位值；不会请求Token地址"
              />
            </el-form-item>
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
            <div v-if="!form.id" class="empty-state">
              新接口需要先保存后才能执行真实调用。
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-form>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import { Plus as ElIconPlus } from '@element-plus/icons-vue'
import {
  createApiConfig,
  getApiConfig,
  invokeApiConfigPreview,
  listDatasources,
  previewApiConfigRequest,
  updateApiConfig,
} from '@/api/datasource'
import { getVariableTree, listDataObjects } from '@/api/dataObject'
import ResponseConditionTreeEditor from '@/components/common/ResponseConditionTreeEditor.vue'
import ConditionGroupEditor from '@/components/decision/ConditionGroupEditor.vue'
import MonacoEditor from '@/components/MonacoEditor'
import {
  createEmptyGroup,
  createEmptyLeaf,
} from '@/utils/decisionConditionTree'
import {
  collectReferencePaths,
  sampleValueForVarType,
  setPathValue,
} from '@/utils/testParamTemplate'

export default {
  data() {
    return {
      datasourceOptions: [],
      dataObjectOptions: [],
      dataObjectTree: [],
      saving: false,
      invokeLoading: false,
      previewLoading: false,
      previewToken: '',
      invokeParamsText: '{}',
      invokeResultText: '',
      requestPreviewText: '',
      form: this.emptyForm(),
      activeConfigTab: 'auth',
      requestBodyMode: 'MAPPING',
      responseMappingMode: 'MAPPING',
      requestMappingJsonText: '{}',
      responseMappingJsonText: '{}',
      syncingMapping: false,
      headerRows: [this.emptyNameValueRow()],
      queryRows: [this.emptyNameValueRow()],
      requestMappingRows: [this.emptyRequestMappingRow()],
      responseMappingRows: [this.emptyResponseMappingRow()],
      responseConditionRows: [],
      apiAuthConfig: this.emptyAuthConfig('INHERIT'),
      scriptVariableRows: [this.emptyScriptVariableRow()],
      cacheKeyRows: [this.emptyCacheKeyRow()],
      successConditionRoot: this.emptyApiConditionRoot(
        'httpStatus',
        'starts_with',
        '2'
      ),
      retryConditionRoot: this.emptyApiConditionRoot(),
      billingConfig: this.emptyBillingConfig(),
      asyncShared: this.emptyAsyncShared(),
      asyncPollConfig: this.emptyAsyncPollConfig(),
      asyncCallbackConfig: this.emptyAsyncCallbackConfig(),
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
        { label: '返回默认值', value: 'RETURN_DEFAULT' },
        { label: '忽略异常', value: 'IGNORE' },
        { label: '使用缓存', value: 'USE_CACHE' },
      ],
      ElIconPlus: markRaw(ElIconPlus),
    }
  },
  name: 'ApiDetail',
  components: {
    ConditionGroupEditor,
    MonacoEditor,
    ResponseConditionTreeEditor,
  },
  computed: {
    isCreateMode() {
      return !this.$route.params.id || this.$route.params.id === 'new'
    },
    requestFieldOptions() {
      return this.fieldsForObject(this.form.requestObjectId)
    },
    responseFieldOptions() {
      return this.fieldsForObject(this.form.responseObjectId)
    },
    responseConditionVarOptions() {
      const common = [
        { varCode: 'success', varLabel: '调用是否成功', varType: 'BOOLEAN' },
        {
          varCode: 'body.response_code',
          varLabel: '供应商响应编码',
          varType: 'STRING',
        },
        {
          varCode: 'body.response_msg',
          varLabel: '供应商响应消息',
          varType: 'STRING',
        },
        {
          varCode: 'body.trace_id',
          varLabel: '供应商追踪号',
          varType: 'STRING',
        },
        { varCode: 'body.code', varLabel: '响应编码', varType: 'STRING' },
        { varCode: 'body.status', varLabel: '响应状态', varType: 'STRING' },
        { varCode: 'body.message', varLabel: '响应消息', varType: 'STRING' },
      ]
      const fields = this.responseFieldOptions
        .map((field) => {
          const path = this.stripSelectedObjectPrefix(
            this.fieldScriptPath(field),
            this.form.responseObjectId
          )
          const code = path ? 'body.' + path : ''
          return {
            ...field,
            varCode: code,
            varLabel: this.fieldDisplayName(field),
            varType: field.varType || 'STRING',
          }
        })
        .filter((item) => item.varCode)
      const seen = {}
      return common.concat(fields).filter((item) => {
        if (seen[item.varCode]) return false
        seen[item.varCode] = true
        return true
      })
    },
    requestPathOptions() {
      const common = [
        { label: '姓名 name', value: 'name' },
        { label: '身份证号 idCard', value: 'idCard' },
        { label: '手机号 mobile', value: 'mobile' },
      ]
      const fields = this.requestFieldOptions
        .map((field) => {
          const path = this.stripSelectedObjectPrefix(
            this.fieldScriptPath(field),
            this.form.requestObjectId
          )
          return {
            label: this.fieldDisplayName(field) + ' ' + path,
            value: path,
          }
        })
        .filter((item) => item.value)
      return this.uniquePathOptions(common.concat(fields))
    },
    responsePathOptions() {
      const common = [
        { label: 'HTTP 状态码', value: 'httpStatus' },
        { label: '供应商响应编码 response_code', value: 'body.response_code' },
        { label: '供应商响应消息 response_msg', value: 'body.response_msg' },
        { label: '供应商追踪号 trace_id', value: 'body.trace_id' },
        { label: '响应编码', value: 'body.code' },
        { label: '响应状态', value: 'body.status' },
        { label: '响应消息', value: 'body.message' },
      ]
      const fields = this.responseFieldOptions
        .map((field) => {
          const path = this.stripSelectedObjectPrefix(
            this.fieldScriptPath(field),
            this.form.responseObjectId
          )
          return {
            label: this.fieldDisplayName(field) + ' body.' + path,
            value: path ? 'body.' + path : '',
          }
        })
        .filter((item) => item.value)
      return this.uniquePathOptions(common.concat(fields))
    },
    engineCallbackPlaceholder() {
      const code = this.form.apiCode || '{apiCode}'
      return '/rule/datasource/api-callback/' + code
    },
  },
  watch: {
    'form.requestMode'(value) {
      if (value === 'ASYNC' && !this.form.asyncResultMode) {
        this.form.asyncResultMode = 'POLL'
      }
      if (value !== 'ASYNC' && this.activeConfigTab === 'async') {
        this.activeConfigTab = 'retry'
      }
    },
    requestBodyMode(value, oldValue) {
      if (this.syncingMapping || value === oldValue) return
      if (value === 'JSON') {
        this.syncRequestJsonFromRows()
      } else {
        this.syncRequestRowsFromJson()
      }
    },
    responseMappingMode(value, oldValue) {
      if (this.syncingMapping || value === oldValue) return
      if (value === 'JSON') {
        this.syncResponseJsonFromRows()
      } else {
        this.syncResponseRowsFromJson()
      }
    },
    requestMappingJsonText(value) {
      if (this.syncingMapping || this.requestBodyMode !== 'JSON') return
      this.syncRequestRowsFromJson(value, true)
    },
    responseMappingJsonText(value) {
      if (this.syncingMapping || this.responseMappingMode !== 'JSON') return
      this.syncResponseRowsFromJson(value, true)
    },
    '$route.fullPath': async function (value, oldValue) {
      if (value === oldValue) return
      await this.initializeRoute()
    },
  },
  async created() {
    await this.loadDatasourceOptions()
    await this.initializeRoute()
  },
  methods: {
    async initializeRoute() {
      this.form = this.emptyForm()
      this.activeConfigTab = 'auth'
      this.invokeResultText = ''
      this.requestPreviewText = ''
      this.previewToken = ''
      if (this.isCreateMode) {
        if (this.$route.query.datasourceId) {
          this.form.datasourceId = Number(this.$route.query.datasourceId)
          await this.loadDataObjectOptions(
            this.resolveDatasourceProjectId(this.form.datasourceId)
          )
        } else {
          await this.loadDataObjectOptions(0)
        }
        this.syncEditableRowsFromForm()
        this.regenerateTestParams()
        return
      }
      await this.loadDetail()
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
        requestObjectId: null,
        responseObjectId: null,
        headerConfig: '',
        queryConfig: '',
        requestMapping: '',
        responseMapping: '',
        bodyTemplate: '',
        requestScript: '',
        responseScript: '',
        authMode: 'INHERIT',
        authApiConfig: '',
        tokenCacheSeconds: 0,
        timeoutMs: 3000,
        retryCount: 0,
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
        retryCondition: '',
        exceptionStrategy: 'FAIL_FAST',
        fallbackValue: '',
        asyncResultMode: 'POLL',
        asyncPollConfig: '',
        asyncCallbackConfig: '',
        asyncCallbackUrl: '',
        asyncResultPath: '',
        billingItemCode: '',
        billingCondition: '',
        unitPrice: 0,
        description: '',
        testSampleParams: '',
        status: 1,
      }
    },
    emptyNameValueRow() {
      return { name: '', value: '', remark: '' }
    },
    emptyRequestMappingRow() {
      return { targetPath: '', sourcePath: '', remark: '' }
    },
    emptyResponseMappingRow() {
      return { outputField: '', sourcePath: '', defaultValue: '' }
    },
    emptyResponseConditionRow(fallback) {
      return {
        outputField: '',
        conditionRoot: this.createResponseConditionRoot(),
        sourcePath: '',
        defaultValue: '',
        fallback: fallback === true,
      }
    },
    createResponseConditionRoot() {
      const root = createEmptyGroup('AND')
      root.children.push(createEmptyLeaf())
      return root
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
        tokenPlacement: 'HEADER',
        tokenHeaderName: 'Authorization',
        tokenPrefix: 'Bearer ',
        headers: '{}',
        body: '{"grant_type":"client_credentials"}',
      }
      if (type === 'API_KEY') common.name = 'X-API-Key'
      return common
    },
    emptyScriptVariableRow() {
      return { name: '', value: '', sensitive: true }
    },
    emptyAsyncShared() {
      return { taskIdPath: 'body.taskId' }
    },
    emptyAsyncPollConfig() {
      return {
        resultEndpointUrl: '',
        requestMethod: 'GET',
        intervalMs: 3000,
        maxAttempts: 20,
        taskIdPath: 'body.taskId',
        statusPath: 'body.status',
        successValue: 'SUCCESS',
        resultPath: 'body.data',
      }
    },
    emptyAsyncCallbackConfig() {
      return {
        taskIdPath: 'body.taskId',
        statusPath: 'body.status',
        successValue: 'SUCCESS',
        resultPath: 'body.data',
        signatureHeader: '',
        signatureSecret: '',
      }
    },
    async loadDatasourceOptions() {
      const res = await listDatasources({ pageNum: 1, pageSize: 500 })
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
      try {
        const treeRes = await getVariableTree(projectId || 0)
        const treeData = treeRes && treeRes.data ? treeRes.data : treeRes
        this.dataObjectTree = Array.isArray(treeData)
          ? treeData
          : Array.isArray(treeData && treeData.tree)
          ? treeData.tree
          : []
      } catch (e) {
        this.dataObjectTree = []
      }
    },
    async loadDetail() {
      const res = await getApiConfig(this.$route.params.id)
      const data = res && res.data ? res.data : res
      this.form = { ...this.emptyForm(), ...data }
      await this.loadDataObjectOptions(
        this.resolveDatasourceProjectId(this.form.datasourceId)
      )
      this.syncEditableRowsFromForm()
      this.loadSavedSample()
    },
    onDatasourceChange(datasourceId) {
      this.form.requestObjectId = null
      this.form.responseObjectId = null
      this.loadDataObjectOptions(this.resolveDatasourceProjectId(datasourceId))
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
      this.headerRows = this.rowsFromNameValueConfig(this.form.headerConfig)
      this.queryRows = this.rowsFromNameValueConfig(this.form.queryConfig)
      this.requestBodyMode = 'MAPPING'
      this.requestMappingRows = this.rowsFromRequestMapping(
        this.form.requestMapping
      )
      this.requestMappingJsonText = this.stringifyJson(
        this.buildRequestMappingConfig()
      )
      this.responseMappingMode = 'MAPPING'
      this.responseMappingRows = this.rowsFromResponseMapping(
        this.form.responseMapping
      )
      this.responseConditionRows = this.rowsFromConditionalResponseMapping(
        this.form.responseMapping
      )
      this.responseMappingJsonText = this.stringifyJson(
        this.buildResponseMappingConfig()
      )
      this.syncAuthConfigFromForm()
      this.syncAsyncConfigFromForm()
      this.syncCacheKeyConfigFromForm()
      this.syncSuccessConditionFromForm()
      this.syncRetryConditionFromForm()
      this.syncBillingConfigFromForm()
    },
    syncAuthConfigFromForm() {
      const type = this.form.authMode
      const base = this.emptyAuthConfig(type)
      this.scriptVariableRows = [this.emptyScriptVariableRow()]
      if (!this.form.authApiConfig) {
        this.apiAuthConfig = base
        return
      }
      try {
        const parsed = JSON.parse(this.form.authApiConfig)
        if (
          Array.isArray(parsed.scriptVariables) &&
          parsed.scriptVariables.length
        ) {
          this.scriptVariableRows = parsed.scriptVariables.map((item) => ({
            name: item && item.name != null ? String(item.name) : '',
            value: item && item.value != null ? item.value : '',
            sensitive: !item || item.sensitive !== false,
          }))
        }
        if (type === 'CUSTOM') {
          this.apiAuthConfig = base
          return
        }
        const merged = { ...base, ...parsed }
        if (parsed.headers && typeof parsed.headers !== 'string')
          merged.headers = this.stringifyJson(parsed.headers)
        if (parsed.body && typeof parsed.body !== 'string')
          merged.body = this.stringifyJson(parsed.body)
        this.apiAuthConfig = merged
      } catch (e) {
        this.apiAuthConfig = base
      }
    },
    syncAsyncConfigFromForm() {
      this.asyncShared = this.emptyAsyncShared()
      this.asyncPollConfig = this.mergeJsonConfig(
        this.emptyAsyncPollConfig(),
        this.form.asyncPollConfig
      )
      this.asyncCallbackConfig = this.mergeJsonConfig(
        this.emptyAsyncCallbackConfig(),
        this.form.asyncCallbackConfig
      )
      this.asyncShared.taskIdPath =
        this.asyncPollConfig.taskIdPath ||
        this.asyncCallbackConfig.taskIdPath ||
        'body.taskId'
      if (!this.form.asyncResultMode) this.form.asyncResultMode = 'POLL'
    },
    mergeJsonConfig(base, text) {
      if (!text) return { ...base }
      try {
        const parsed = typeof text === 'string' ? JSON.parse(text) : text
        return { ...base, ...parsed }
      } catch (e) {
        return { ...base }
      }
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
    rowsFromNameValueConfig(text) {
      const config = this.parseConfigForTemplate(text)
      if (!config || typeof config !== 'object' || Array.isArray(config))
        return [this.emptyNameValueRow()]
      const rows = Object.keys(config).map((name) => ({
        name,
        value: this.valueToEditableText(config[name]),
        remark: '',
      }))
      return rows.length ? rows : [this.emptyNameValueRow()]
    },
    rowsFromRequestMapping(text) {
      const config = this.parseConfigForTemplate(text)
      if (!config || typeof config !== 'object' || Array.isArray(config))
        return [this.emptyRequestMappingRow()]
      const rows = []
      this.flattenMappingRows(config, '', rows)
      return rows.length ? rows : [this.emptyRequestMappingRow()]
    },
    rowsFromResponseMapping(text) {
      const config = this.parseConfigForTemplate(text)
      if (!config || typeof config !== 'object' || Array.isArray(config))
        return [this.emptyResponseMappingRow()]
      const rows = Object.keys(config)
        .filter((outputField) => {
          const value = config[outputField]
          return !(
            value &&
            typeof value === 'object' &&
            !Array.isArray(value) &&
            Array.isArray(value.cases)
          )
        })
        .map((outputField) => {
          const value = config[outputField]
          const row = { outputField, sourcePath: '', defaultValue: '' }
          if (typeof value === 'string') {
            row.sourcePath = value
          } else if (Array.isArray(value)) {
            row.sourcePath = value.join(', ')
          } else if (value && typeof value === 'object') {
            if (Array.isArray(value.paths))
              row.sourcePath = value.paths.join(', ')
            else if (value.path) row.sourcePath = value.path
            if (Object.prototype.hasOwnProperty.call(value, 'default'))
              row.defaultValue = this.valueToEditableText(value.default)
          }
          return row
        })
      return rows.length ? rows : [this.emptyResponseMappingRow()]
    },
    rowsFromConditionalResponseMapping(text) {
      const config = this.parseConfigForTemplate(text)
      if (!config || typeof config !== 'object' || Array.isArray(config))
        return []
      const rows = []
      Object.keys(config).forEach((outputField) => {
        const value = config[outputField]
        if (
          !value ||
          typeof value !== 'object' ||
          Array.isArray(value) ||
          !Array.isArray(value.cases)
        )
          return
        value.cases.forEach((item) => {
          const caseConfig = item && typeof item === 'object' ? item : {}
          rows.push({
            outputField,
            conditionRoot: this.normalizeResponseConditionRoot(caseConfig.when),
            sourcePath:
              caseConfig.path ||
              (Array.isArray(caseConfig.paths)
                ? caseConfig.paths.join(', ')
                : ''),
            defaultValue: this.valueToEditableText(caseConfig.default),
            fallback: !caseConfig.when,
          })
        })
        if (
          rows.length &&
          Object.prototype.hasOwnProperty.call(value, 'default')
        ) {
          const last = rows[rows.length - 1]
          if (last.outputField === outputField && !last.defaultValue)
            last.defaultValue = this.valueToEditableText(value.default)
        }
      })
      return rows
    },
    normalizeResponseConditionRoot(condition) {
      if (!condition || typeof condition !== 'object')
        return this.createResponseConditionRoot()
      if (condition.type === 'group') {
        return JSON.parse(JSON.stringify(condition))
      }
      if (condition.type === 'leaf') {
        const root = createEmptyGroup('AND')
        root.children.push(JSON.parse(JSON.stringify(condition)))
        return root
      }
      const root = createEmptyGroup('AND')
      const leaf = createEmptyLeaf()
      leaf.varCode =
        condition.path || condition.field || condition.varCode || ''
      leaf.varLabel = leaf.varCode
      leaf.operator = condition.operator || '=='
      leaf.value = this.valueToEditableText(condition.value)
      leaf.varType = condition.varType || 'STRING'
      root.children.push(leaf)
      return root
    },
    hasComplexResponseMapping(text) {
      const config = this.parseConfigForTemplate(text)
      if (!config || typeof config !== 'object' || Array.isArray(config))
        return false
      return Object.keys(config).some((key) => {
        const value = config[key]
        return (
          value &&
          typeof value === 'object' &&
          !Array.isArray(value) &&
          value.cases
        )
      })
    },
    flattenMappingRows(value, prefix, rows) {
      Object.keys(value).forEach((key) => {
        const nextPath = prefix ? prefix + '.' + key : key
        const item = value[key]
        if (item && typeof item === 'object' && !Array.isArray(item)) {
          this.flattenMappingRows(item, nextPath, rows)
        } else {
          rows.push({
            targetPath: nextPath,
            sourcePath: this.valueToEditableText(item),
            remark: '',
          })
        }
      })
    },
    valueToEditableText(value) {
      if (value == null) return ''
      if (typeof value === 'string') return value
      return JSON.stringify(value)
    },
    syncStructuredConfigToForm() {
      this.form.authApiConfig = this.buildApiAuthConfig()
      this.form.headerConfig = this.jsonTextOrBlank(
        this.buildNameValueConfig(this.headerRows)
      )
      this.form.queryConfig = this.jsonTextOrBlank(
        this.buildNameValueConfig(this.queryRows)
      )
      if (this.requestBodyMode === 'JSON') {
        const requestJson = this.hasEditableJson(this.requestMappingJsonText)
          ? this.requestMappingJsonText
          : this.form.requestMapping
        this.syncRequestRowsFromJson(requestJson, false)
      }
      this.form.requestMapping = this.jsonTextOrBlank(
        this.buildRequestMappingConfig()
      )
      this.form.bodyTemplate = ''
      if (this.responseMappingMode === 'JSON') {
        const responseJson = this.hasEditableJson(this.responseMappingJsonText)
          ? this.responseMappingJsonText
          : this.form.responseMapping
        this.syncResponseRowsFromJson(responseJson, false)
      }
      this.form.responseMapping = this.jsonTextOrBlank(
        this.buildResponseMappingConfig()
      )
      this.form.cacheKeyConfig = this.jsonTextOrBlank(
        this.buildCacheKeyConfig()
      )
      this.form.successCondition = this.jsonTextOrBlank(
        this.buildSuccessConditionConfig()
      )
      this.form.retryCondition = this.jsonTextOrBlank(
        this.buildRetryConditionConfig()
      )
      this.form.billingCondition = this.jsonTextOrBlank(
        this.buildBillingConditionConfig()
      )
      if (this.form.requestMode === 'ASYNC') {
        this.form.asyncPollConfig =
          this.form.asyncResultMode === 'POLL'
            ? this.jsonTextOrBlank({
                ...this.asyncPollConfig,
                taskIdPath: this.asyncShared.taskIdPath,
              })
            : ''
        this.form.asyncCallbackConfig =
          this.form.asyncResultMode === 'CALLBACK'
            ? this.jsonTextOrBlank({
                ...this.asyncCallbackConfig,
                taskIdPath: this.asyncShared.taskIdPath,
              })
            : ''
      } else {
        this.form.asyncResultMode = null
        this.form.asyncPollConfig = ''
        this.form.asyncCallbackConfig = ''
        this.form.asyncCallbackUrl = ''
        this.form.asyncResultPath = ''
      }
    },
    hasEditableJson(text) {
      if (!text || !String(text).trim()) return false
      const normalized = String(text).trim()
      return normalized !== '{}' && normalized !== '[]'
    },
    buildApiAuthConfig() {
      const type = this.form.authMode
      let config = {}
      if (type === 'BASIC') {
        config = {
          username: this.apiAuthConfig.username,
          password: this.apiAuthConfig.password,
        }
      }
      if (type === 'BEARER') config = { token: this.apiAuthConfig.token }
      if (type === 'API_KEY') {
        config = {
          location: this.apiAuthConfig.location,
          name: this.apiAuthConfig.name,
          value: this.apiAuthConfig.value,
        }
      }
      if (type === 'TOKEN_API' || type === 'OAUTH2') {
        const headers = this.parseJsonText(
          this.apiAuthConfig.headers,
          '鉴权请求头'
        )
        const body = this.parseJsonText(this.apiAuthConfig.body, '鉴权请求体')
        config = {
          tokenUrl: this.apiAuthConfig.tokenUrl,
          method: this.apiAuthConfig.method,
          contentType: this.apiAuthConfig.contentType,
          headers,
          body,
          tokenPath: this.apiAuthConfig.tokenPath,
          expiresInPath: this.apiAuthConfig.expiresInPath,
          tokenPlacement: this.apiAuthConfig.tokenPlacement,
          tokenHeaderName: this.apiAuthConfig.tokenHeaderName,
          tokenPrefix: this.apiAuthConfig.tokenPrefix,
        }
      }
      if (type === 'CUSTOM' && this.form.authApiConfig) {
        config = this.parseJsonText(this.form.authApiConfig, '接口鉴权配置')
      }
      const scriptVariables = this.buildScriptVariables()
      if (scriptVariables.length) config.scriptVariables = scriptVariables
      else delete config.scriptVariables
      return Object.keys(config).length ? this.stringifyJson(config) : ''
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
    buildNameValueConfig(rows) {
      const result = {}
      const list = rows || []
      list.forEach((row) => {
        if (row && row.name && String(row.name).trim()) {
          result[String(row.name).trim()] = row.value
        }
      })
      return result
    },
    buildRequestMappingConfig() {
      const result = {}
      const rows = this.requestMappingRows || []
      rows.forEach((row) => {
        if (!row || !row.targetPath || !String(row.targetPath).trim()) return
        setPathValue(result, String(row.targetPath).trim(), row.sourcePath)
      })
      return result
    },
    buildResponseMappingConfig() {
      const result = {}
      const rows = this.responseMappingRows || []
      rows.forEach((row) => {
        if (!row || !row.outputField || !String(row.outputField).trim()) return
        const field = String(row.outputField).trim()
        const paths = String(row.sourcePath || '')
          .split(',')
          .map((item) => item.trim())
          .filter(Boolean)
        const hasDefault =
          row.defaultValue != null && String(row.defaultValue).trim() !== ''
        if (paths.length > 1 || hasDefault) {
          const config = {}
          if (paths.length > 1) config.paths = paths
          else config.path = paths[0] || ''
          if (hasDefault)
            config.default = this.parseJsonOrString(row.defaultValue)
          result[field] = config
        } else {
          result[field] = paths[0] || ''
        }
      })
      const conditionRows = this.responseConditionRows || []
      conditionRows.forEach((row) => {
        if (!row || !row.outputField || !String(row.outputField).trim()) return
        const field = String(row.outputField).trim()
        const target =
          result[field] &&
          typeof result[field] === 'object' &&
          !Array.isArray(result[field])
            ? result[field]
            : {}
        if (!Array.isArray(target.cases)) target.cases = []
        const caseConfig = {}
        if (!row.fallback && row.conditionRoot) {
          caseConfig.when = JSON.parse(JSON.stringify(row.conditionRoot))
        }
        const paths = String(row.sourcePath || '')
          .split(',')
          .map((item) => item.trim())
          .filter(Boolean)
        if (paths.length > 1) caseConfig.paths = paths
        else if (paths.length === 1) caseConfig.path = paths[0]
        if (
          row.defaultValue != null &&
          String(row.defaultValue).trim() !== ''
        ) {
          caseConfig.default = this.parseJsonOrString(row.defaultValue)
          if (!Object.prototype.hasOwnProperty.call(target, 'default'))
            target.default = caseConfig.default
        }
        target.cases.push(caseConfig)
        result[field] = target
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
    syncRequestJsonFromRows() {
      this.syncingMapping = true
      this.requestMappingJsonText = this.stringifyJson(
        this.buildRequestMappingConfig()
      )
      this.syncingMapping = false
    },
    syncRequestRowsFromJson(value, silent) {
      try {
        const text = value == null ? this.requestMappingJsonText : value
        const parsed = this.parseJsonText(text, '请求映射JSON')
        this.syncingMapping = true
        this.requestMappingRows = this.rowsFromRequestMapping(parsed)
        this.syncingMapping = false
        return true
      } catch (e) {
        this.syncingMapping = false
        if (!silent) throw e
        return false
      }
    },
    syncResponseJsonFromRows() {
      this.syncingMapping = true
      this.responseMappingJsonText = this.stringifyJson(
        this.buildResponseMappingConfig()
      )
      this.syncingMapping = false
    },
    syncResponseRowsFromJson(value, silent) {
      try {
        const text = value == null ? this.responseMappingJsonText : value
        const parsed = this.parseJsonText(text, '响应映射JSON')
        this.syncingMapping = true
        this.responseMappingRows = this.rowsFromResponseMapping(parsed)
        this.responseConditionRows =
          this.rowsFromConditionalResponseMapping(parsed)
        this.syncingMapping = false
        return true
      } catch (e) {
        this.syncingMapping = false
        if (!silent) throw e
        return false
      }
    },
    onRequestTargetInput(row) {
      if (!row) return
      const target = String(row.targetPath || '').trim()
      if (target && !String(row.sourcePath || '').trim()) {
        row.sourcePath = '$.' + target.replace(/^\$\./, '')
      }
      if (this.requestBodyMode === 'MAPPING') this.syncRequestJsonFromRows()
    },
    onRequestSourceInput(row) {
      if (!row) return
      const source = String(row.sourcePath || '').trim()
      if (
        source &&
        !String(row.targetPath || '').trim() &&
        source.indexOf('$.') === 0
      ) {
        row.targetPath = source.substring(2)
      }
      if (this.requestBodyMode === 'MAPPING') this.syncRequestJsonFromRows()
    },
    fillRequestRowsFromRequestObject() {
      const rows = this.requestFieldOptions
        .map((field) => ({
          targetPath: this.stripSelectedObjectPrefix(
            this.fieldScriptPath(field),
            this.form.requestObjectId
          ),
          sourcePath: '$.' + this.fieldScriptPath(field),
          remark: this.fieldDisplayName(field),
        }))
        .filter((row) => row.targetPath)
      this.requestMappingRows = rows.length
        ? rows
        : [this.emptyRequestMappingRow()]
      this.syncRequestJsonFromRows()
    },
    fillResponseRowsFromResponseObject() {
      const rows = this.responseFieldOptions
        .map((field) => {
          const sourcePath = this.stripSelectedObjectPrefix(
            this.fieldScriptPath(field),
            this.form.responseObjectId
          )
          return {
            outputField: this.outputFieldName(field),
            sourcePath: sourcePath ? 'body.' + sourcePath : '',
            defaultValue: '',
          }
        })
        .filter((row) => row.outputField)
      this.responseMappingRows = rows.length
        ? rows
        : [this.emptyResponseMappingRow()]
      this.syncResponseJsonFromRows()
    },
    useRequestField(field) {
      this.requestMappingRows.push({
        targetPath: this.stripSelectedObjectPrefix(
          this.fieldScriptPath(field),
          this.form.requestObjectId
        ),
        sourcePath: '$.' + this.fieldScriptPath(field),
        remark: this.fieldDisplayName(field),
      })
      this.syncRequestJsonFromRows()
    },
    useResponseField(field) {
      const sourcePath = this.stripSelectedObjectPrefix(
        this.fieldScriptPath(field),
        this.form.responseObjectId
      )
      this.responseMappingRows.push({
        outputField: this.outputFieldName(field),
        sourcePath: sourcePath ? 'body.' + sourcePath : '',
        defaultValue: '',
      })
      this.syncResponseJsonFromRows()
    },
    addHeaderRow() {
      this.headerRows.push(this.emptyNameValueRow())
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
    addQueryRow() {
      this.queryRows.push(this.emptyNameValueRow())
    },
    addRequestMappingRow() {
      this.requestMappingRows.push(this.emptyRequestMappingRow())
    },
    addResponseMappingRow() {
      this.responseMappingRows.push(this.emptyResponseMappingRow())
    },
    addResponseConditionRow(fallback) {
      this.responseConditionRows.push(this.emptyResponseConditionRow(fallback))
    },
    removeRow(rows, index) {
      rows.splice(index, 1)
      if (rows.length === 0) {
        if (rows === this.headerRows || rows === this.queryRows)
          rows.push(this.emptyNameValueRow())
        if (rows === this.requestMappingRows)
          rows.push(this.emptyRequestMappingRow())
        if (rows === this.responseMappingRows)
          rows.push(this.emptyResponseMappingRow())
      }
      if (rows === this.requestMappingRows) this.syncRequestJsonFromRows()
      if (
        rows === this.responseMappingRows ||
        rows === this.responseConditionRows
      )
        this.syncResponseJsonFromRows()
    },
    normalizeForm(form) {
      this.syncStructuredConfigToForm()
      const data = { ...form }
      const cacheConfig = this.buildCacheKeyConfig()
      if (
        Number(data.responseCacheSeconds) > 0 &&
        !Array.isArray(cacheConfig.components)
      ) {
        throw new Error('启用响应缓存时必须配置缓存键字段')
      }
      const successCondition = this.buildSuccessConditionConfig()
      this.validateApiConditionTree(successCondition, '请求成功条件')
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
      if (!data.requestObjectId) data.requestObjectId = null
      if (!data.responseObjectId) data.responseObjectId = null
      const jsonFields = {
        headerConfig: 'Header配置',
        queryConfig: 'Query配置',
        requestMapping: '入参映射',
        responseMapping: '响应映射',
        authApiConfig: '接口鉴权配置',
        bodyTemplate: '请求体模板',
        asyncPollConfig: '异步轮询配置',
        asyncCallbackConfig: '异步回调配置',
        cacheKeyConfig: '缓存键配置',
        successCondition: '请求成功条件',
        retryCondition: '业务响应重试条件',
        billingCondition: '计费条件',
        fallbackValue: '兜底返回',
        testSampleParams: '测试样例',
      }
      Object.keys(jsonFields).forEach((key) => {
        data[key] = this.blankToNull(data[key])
        this.assertJson(data[key], jsonFields[key])
      })
      data.requestScript = this.blankToNull(data.requestScript)
      data.responseScript = this.blankToNull(data.responseScript)
      return data
    },
    handleBack() {
      this.$router.push({ path: '/datasource', query: { tab: 'api' } })
    },
    handleSave() {
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
        try {
          let res
          if (data.id) {
            res = await updateApiConfig(data)
            this.$message.success('更新成功')
          } else {
            res = await createApiConfig(data)
            this.$message.success('创建成功')
          }
          const saved = res && res.data ? res.data : null
          if (saved && saved.id) {
            this.form = { ...this.emptyForm(), ...saved }
            if (this.$route.params.id !== String(saved.id)) {
              this.$router.replace('/datasource/api/' + saved.id)
            }
            this.syncEditableRowsFromForm()
          }
        } finally {
          this.saving = false
        }
      })
    },
    applyTemplate(type) {
      if (type === 'RULE_ENGINE') {
        this.form.requestMethod = 'POST'
        this.form.contentType = 'application/json'
        this.form.endpointUrl = this.form.endpointUrl || 'RC_PRICING_TABLE'
        this.form.requestMapping = this.stringifyJson({
          ruleCode: this.form.endpointUrl || 'RC_PRICING_TABLE',
          params: {
            customerType: '$.customerType',
            productLine: '$.productLine',
          },
        })
        this.form.responseMapping = this.stringifyJson({
          decision: 'body.decision',
          rate: 'body.rate',
          score: 'body.score',
        })
        this.form.bodyTemplate = ''
        this.syncEditableRowsFromForm()
        return
      }
      this.form.requestMethod = 'POST'
      this.form.contentType = 'application/json'
      this.headerRows = [
        { name: 'X-Request-Id', value: '${requestId}', remark: '请求流水号' },
      ]
      this.requestMappingRows = [
        { targetPath: 'idNo', sourcePath: '$.customer.idNo', remark: '证件号' },
        {
          targetPath: 'mobile',
          sourcePath: '$.customer.mobile',
          remark: '手机号',
        },
        { targetPath: 'name', sourcePath: '$.customer.name', remark: '姓名' },
      ]
      this.responseMappingRows = [
        {
          outputField: 'score',
          sourcePath: 'body.data.score',
          defaultValue: '',
        },
        {
          outputField: 'riskLevel',
          sourcePath: 'body.data.riskLevel',
          defaultValue: '',
        },
        {
          outputField: 'hitReason',
          sourcePath: 'body.data.reason',
          defaultValue: '',
        },
      ]
      this.form.bodyTemplate = ''
      this.requestBodyMode = 'MAPPING'
      this.responseMappingMode = 'MAPPING'
      this.syncStructuredConfigToForm()
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
    dataObjectLabel(item) {
      const code = item.scriptName || item.objectCode || ''
      const name = item.objectName || item.objectLabel || ''
      const type = item.objectType ? '[' + item.objectType + '] ' : ''
      return type + (name || code) + (code && name !== code ? ' / ' + code : '')
    },
    fieldsForObject(objectId) {
      if (!objectId) return []
      const node = this.dataObjectTree.find((item) => {
        const object = item.object || {}
        return (
          String(object.id || item.id || item.objectId) === String(objectId)
        )
      })
      if (!node) return []
      const fields = node.flatVariables || node.variables || node.children || []
      return this.flattenObjectVariables(fields)
    },
    flattenObjectVariables(rows) {
      const result = []
      const visit = (list) => {
        const current = list || []
        current.forEach((row) => {
          if (this.fieldScriptPath(row)) result.push(row)
          if (row.children) visit(row.children)
        })
      }
      visit(rows)
      return result
    },
    fieldScriptPath(field) {
      return field && (field.scriptName || field.varCode || '')
        ? String(field.scriptName || field.varCode)
        : ''
    },
    uniquePathOptions(options) {
      const seen = {}
      return (options || []).filter((item) => {
        if (!item || !item.value || seen[item.value]) return false
        seen[item.value] = true
        return true
      })
    },
    fieldDisplayName(field) {
      if (!field) return ''
      return field.varLabel || field.varCode || this.fieldScriptPath(field)
    },
    outputFieldName(field) {
      const path = this.stripSelectedObjectPrefix(
        this.fieldScriptPath(field),
        this.form.responseObjectId
      )
      return this.leafName(path || this.fieldScriptPath(field))
    },
    stripSelectedObjectPrefix(path, objectId) {
      const object = this.dataObjectOptions.find(
        (item) => String(item.id) === String(objectId)
      )
      const prefix = object && (object.scriptName || object.objectCode)
      if (!path || !prefix) return path || ''
      let result = path
      while (result.indexOf(prefix + '.') === 0) {
        result = result.substring(prefix.length + 1)
      }
      return result
    },
    leafName(path) {
      if (!path) return ''
      const text = String(path)
      const index = text.lastIndexOf('.')
      return index >= 0 ? text.substring(index + 1) : text
    },
    regenerateTestParams() {
      this.syncStructuredConfigToForm()
      this.invokeParamsText = this.buildApiInvokeParamTemplate(this.form)
    },
    loadSavedSample() {
      if (this.form.testSampleParams) {
        this.invokeParamsText = this.stringifyJson(
          this.parseConfigForTemplate(this.form.testSampleParams) || {}
        )
        return
      }
      this.regenerateTestParams()
    },
    async saveCurrentSample() {
      try {
        const parsed = this.invokeParamsText
          ? JSON.parse(this.invokeParamsText)
          : {}
        this.form.testSampleParams = this.stringifyJson(parsed)
        if (!this.form.id) {
          this.$message.warning('新接口需要先保存后才能保存测试样例')
          return
        }
        const data = this.normalizeForm(this.form)
        const res = await updateApiConfig(data)
        const saved = res && res.data ? res.data : res
        if (saved && saved.id) {
          this.form = { ...this.emptyForm(), ...saved }
          this.syncEditableRowsFromForm()
          this.loadSavedSample()
        }
        this.$message.success('测试样例已覆盖保存')
      } catch (e) {
        this.$message.error(e.message || '测试参数不是合法 JSON')
      }
    },
    buildApiInvokeParamTemplate(row) {
      const sample = {}
      const paths = []
      const addPath = (path) => {
        if (path && paths.indexOf(path) < 0) paths.push(path)
      }
      const addPaths = (value) => {
        collectReferencePaths(this.parseConfigForTemplate(value), {
          allowBarePath: false,
        }).forEach((path) => {
          addPath(path)
        })
      }

      const requestObjectId = row && row.requestObjectId
      this.fieldsForObject(requestObjectId).forEach((field) => {
        addPath(
          this.stripSelectedObjectPrefix(
            this.fieldScriptPath(field),
            requestObjectId
          )
        )
      })

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
        addPaths(authConfig.username)
        addPaths(authConfig.password)
        addPaths(authConfig.token)
        addPaths(authConfig.value)
      }

      paths.forEach((path) =>
        setPathValue(sample, path, this.sampleValueForPath(path))
      )
      return this.stringifyJson(sample)
    },
    sampleValueForPath(path) {
      const normalized = String(path || '').replace(/^\$\./, '')
      const field = this.findFieldForSamplePath(normalized)
      return sampleValueForVarType(field && field.varType)
    },
    findFieldForSamplePath(path) {
      const normalized = String(path || '').replace(/^\$\./, '')
      const requestFields = this.fieldsForObject(this.form.requestObjectId)
      const candidates = requestFields.concat(this.flattenAllObjectFields())
      const selectedObject = this.dataObjectOptions.find(
        (item) => String(item.id) === String(this.form.requestObjectId)
      )
      const selectedPrefix =
        selectedObject &&
        (selectedObject.scriptName || selectedObject.objectCode)
      return candidates.find((item) => {
        const scriptName = this.fieldScriptPath(item)
        const stripped = this.stripSelectedObjectPrefix(
          scriptName,
          this.form.requestObjectId
        )
        const leaf = this.leafName(scriptName)
        return (
          scriptName === normalized ||
          stripped === normalized ||
          (selectedPrefix
            ? selectedPrefix + '.' + normalized === scriptName
            : false) ||
          (normalized.indexOf('.') < 0 && leaf === normalized)
        )
      })
    },
    flattenAllObjectFields() {
      const result = []
      const tree = this.dataObjectTree || []
      tree.forEach((node) => {
        result.push.apply(
          result,
          node.flatVariables || this.flattenObjectVariables(node.variables)
        )
      })
      return result
    },
    async runRequestPreview() {
      let params
      try {
        params = this.invokeParamsText ? JSON.parse(this.invokeParamsText) : {}
      } catch (e) {
        this.$message.error('测试参数不是合法 JSON')
        return
      }
      this.previewLoading = true
      try {
        const config = this.normalizeForm(this.form)
        const res = await previewApiConfigRequest(this.form.id || 0, {
          config,
          params,
          previewToken: this.previewToken || '',
        })
        this.requestPreviewText = JSON.stringify(res.data || {}, null, 2)
        this.$message.success('请求预览已生成，未访问外部地址')
      } finally {
        this.previewLoading = false
      }
    },
    async runInvokeApi() {
      if (!this.form.id) {
        this.$message.warning('请先保存接口后再执行测试')
        return
      }
      let params
      try {
        params = this.invokeParamsText ? JSON.parse(this.invokeParamsText) : {}
      } catch (e) {
        this.$message.error('测试参数不是合法 JSON')
        return
      }
      this.invokeLoading = true
      try {
        const config = this.normalizeForm(this.form)
        const res = await invokeApiConfigPreview(this.form.id, {
          config,
          params,
        })
        this.invokeResultText = JSON.stringify(res.data || {}, null, 2)
        this.$message.success('调用完成')
      } finally {
        this.invokeLoading = false
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
    color: #1f2937;
  }
  .detail-meta,
  .field-help,
  .panel-subtitle {
    color: #64748b;
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
    background: #fff;
    border: 1px solid #e5e7eb;
    border-radius: 4px;
    padding: 16px;
  }
  .basic-panel {
    border-bottom: 1px solid #e5e7eb;
    padding-bottom: 8px;
    margin-bottom: 12px;
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
    color: #1f2937;
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
    align-items: flex-start;
  }
  .mapping-main {
    flex: 1;
    min-width: 0;
  }
  .field-reference {
    width: 360px;
    border: 1px solid #e5e7eb;
    border-radius: 4px;
    padding: 10px;
    background: #f8fafc;
  }
  .reference-title {
    margin-bottom: 8px;
  }
  .empty-state {
    color: #64748b;
    font-size: 12px;
    line-height: 1.6;
    background: #f8fafc;
    border: 1px dashed #cbd5e1;
    border-radius: 4px;
    padding: 10px 12px;
  }
  .response-condition-card {
    border: 1px solid #e5e7eb;
    border-radius: 4px;
    padding: 12px;
    margin-bottom: 12px;
    background: #fff;
  }
  .config-card {
    border: 1px solid #e5e7eb;
    border-radius: 4px;
    background: #f8fafc;
    padding: 12px;
    margin-bottom: 14px;
  }
  .config-card > .field-help {
    margin-bottom: 10px;
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
    background: #e2e8f0;
    color: #475569;
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
    color: #1f2937;
    font-weight: 700;
  }
  .response-condition-tree {
    border-top: 1px solid #e5e7eb;
    padding-top: 10px;
  }
  .test-toolbar {
    border-top: 1px solid #e5e7eb;
    padding-top: 14px;
    margin-top: 8px;
  }
  .btn-delete {
    color: #dc2626;
  }
  code {
    color: #1e40af;
    background: #eff6ff;
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
