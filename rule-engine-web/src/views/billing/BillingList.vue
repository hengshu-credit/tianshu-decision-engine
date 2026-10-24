<template>
  <div class="uiue-list-page billing-page management-list-page">
    <div class="module-hint">
      <div class="hint-title">账单管理</div>
      <div class="hint-text">
        统一配置规则执行、外数 API
        和数据库查询的计费项，查看调用明细并按日生成汇总。
      </div>
    </div>

    <el-tabs class="management-table-region" v-model="activeTab" @tab-click="onTabChange">
      <el-tab-pane class="management-table-region" label="计费配置" name="config">
        <el-alert
          title="计费配置变更先审批、后生效；审批通过前，当前计费口径保持不变。计费明细查询和汇总刷新仍可即时使用。"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
        />
        <div class="uiue-search-container uiue-filter-toolbar">
          <el-form :inline="true" size="small" @keyup.enter="handleConfigQuery">
            <el-form-item label="项目编码">
              <project-filter-select
                v-model:value="configQuery.projectCode"
                field="projectCode"
                placeholder="输入项目编码"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="项目名称">
              <project-filter-select
                v-model:value="configQuery.projectName"
                field="projectName"
                placeholder="输入项目名称"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="作用范围">
              <el-select
                v-model="configQuery.scope"
                clearable
                placeholder="全部"
                style="width: 110px"
              >
                <el-option label="全局" value="GLOBAL" />
                <el-option label="项目" value="PROJECT" />
              </el-select>
            </el-form-item>
            <el-form-item label="对象类型" prop="billingTarget">
              <el-select
                v-model="configQuery.billingTarget"
                clearable
                placeholder="全部"
                style="width: 120px"
              >
                <el-option
                  v-for="item in billingTargetOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="计费编码">
              <remote-filter-select
                v-model:value="configQuery.billingCode"
                :fetch-options="fetchConfigCodeOptions"
                option-label-key="billingCode"
                option-value-key="billingCode"
                allow-free-input
                placeholder="前缀筛选"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="状态">
              <el-select
                v-model="configQuery.status"
                clearable
                placeholder="全部"
                style="width: 100px"
              >
                <el-option label="启用" :value="1" />
                <el-option label="停用" :value="0" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleConfigQuery"
                >查询</el-button
              >
              <el-button @click="resetConfigQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <div class="uiue-btn-bar">
            <div class="btn-right">
          <table-column-settings :columns="billingColumns" storage-key="tianshu:table-columns:billing-list" />
              <governance-workspace-toolbar resource-type="BILLING_CONFIG" />
              <el-button
                v-permission="'billing:edit'"
                type="primary"
                size="small"
                :icon="ElIconPlus"
                @click="handleCreateConfig"
                >新建计费项</el-button
              >
            </div>
          </div>
        </div>

        <el-table class="management-table" show-overflow-tooltip
          :data="configList"
          border
          size="small"
          v-loading="configLoading"
          :empty-text="configError || '暂无数据'"
          style="width: 100%"
        >
          <el-table-column label="作用范围" width="90" align="center">
            <template v-slot="{ row }">
              <el-tag
                :type="row.scope === 'GLOBAL' ? 'warning' : 'success'"
                size="small"
                >{{ row.scope === 'GLOBAL' ? '全局' : '项目' }}</el-tag
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
            prop="billingCode"
            label="计费编码"
            min-width="140"
            show-overflow-tooltip
          />
          <el-table-column
            prop="billingName"
            label="计费名称"
            min-width="150"
            show-overflow-tooltip
          />
          <el-table-column label="对象" width="90" align="center">
            <template v-slot="{ row }"
              ><el-tag size="small">{{
                optionLabel(billingTargetOptions, row.billingTarget)
              }}</el-tag></template
            >
          </el-table-column>
          <el-table-column label="方式" width="100" align="center">
            <template v-slot="{ row }">{{
              optionLabel(chargeTypeOptions, row.chargeType)
            }}</template>
          </el-table-column>
          <el-table-column label="单价" width="110" align="right">
            <template v-slot="{ row }"
              >{{ row.currency || 'CNY' }} {{ row.unitPrice || 0 }}</template
            >
          </el-table-column>
          <el-table-column prop="status" label="状态" width="160" align="center">
            <template v-slot="{ row }">
              <governance-resource-status resource-type="BILLING_CONFIG" :resource-id="row.id" :status="row.status"><el-tag
                :type="row.status === 1 ? 'success' : 'info'"
                size="small"
                >{{ row.status === 1 ? '启用' : '停用' }}</el-tag
              ></governance-resource-status>
            </template>
          </el-table-column>
          <el-table-column class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" :width="operationColumnWidth(['编辑', '版本', '发布', '删除'])" align="center" fixed="right">
            <template v-slot="{ row }">
              <el-button
                v-permission="{ code: 'billing:edit', projectId: row.projectId, scope: row.scope }"
                link
                data-action="edit"
                size="small"
                type="warning"
                @click="editWorkspace('BILLING_CONFIG', row, handleEditConfig)"
                >编辑</el-button
              >
              <governance-publish-action resource-type="BILLING_CONFIG" :resource-id="row.id" />
              <el-button
                v-permission="{ code: 'billing:edit', projectId: row.projectId, scope: row.scope }"
                link
                data-action="delete"
                size="small"
                type="danger"
                class="btn-delete"
                @click="handleDeleteConfig(row)"
                >删除</el-button
              >
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-show="!configError"
          :disabled="configLoading"
          :current-page="configQuery.pageNum"
          :page-size="configQuery.pageSize"
          :total="configTotal"
          layout="total,sizes,prev,pager,next"
          :page-sizes="[10, 20, 30, 50, 100, 200, 500]"
          @current-change="
            (p) => {
              configQuery.pageNum = p
              loadConfigs()
            }
          "
          @size-change="
            (s) => {
              configQuery.pageSize = s
              configQuery.pageNum = 1
              loadConfigs()
            }
          "
        />
      </el-tab-pane>

      <el-tab-pane class="management-table-region" label="计费明细" name="record">
        <div class="uiue-search-container uiue-filter-toolbar">
          <el-form :inline="true" size="small" @keyup.enter="handleRecordQuery">
            <el-form-item label="项目编码">
              <project-filter-select
                v-model:value="recordQuery.projectCode"
                field="projectCode"
                placeholder="输入项目编码"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="项目名称">
              <project-filter-select
                v-model:value="recordQuery.projectName"
                field="projectName"
                placeholder="输入项目名称"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="计费对象">
              <el-select
                v-model="recordQuery.billingTarget"
                clearable
                placeholder="全部"
                style="width: 120px"
              >
                <el-option
                  v-for="item in billingTargetOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="计费编码">
              <remote-filter-select
                v-model:value="recordQuery.billingCode"
                :fetch-options="fetchRecordCodeOptions"
                option-label-key="billingCode"
                option-value-key="billingCode"
                allow-free-input
                placeholder="前缀筛选"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="鉴权方式">
              <el-select
                v-model="recordQuery.authType"
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
            <el-form-item label="鉴权编码"
              ><el-input
                v-model="recordQuery.authCode"
                clearable
                placeholder="鉴权编码"
                style="width: 145px"
            /></el-form-item>
            <el-form-item label="Token 编码"
              ><el-input
                v-model="recordQuery.tokenCode"
                clearable
                placeholder="Token 编码"
                style="width: 160px"
            /></el-form-item>
            <execution-version-filter v-model="recordQuery" />
            <el-form-item label="发生时间">
              <el-date-picker
                v-model="recordDateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                style="width: 240px"
                @change="onRecordDateChange"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleRecordQuery"
                >查询</el-button
              >
              <el-button @click="resetRecordQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <div class="uiue-btn-bar">
            <div class="btn-right">
              <table-column-settings :columns="recordColumns" storage-key="tianshu:table-columns:billing-records" />
            </div>
          </div>
        </div>

        <el-table class="management-table" show-overflow-tooltip
          :data="recordList"
          border
          size="small"
          v-loading="recordLoading"
          :empty-text="recordError || '暂无数据'"
          style="width: 100%"
        >
          <el-table-column prop="occurTime" label="发生时间" min-width="160" />
          <el-table-column
            prop="projectCode"
            label="项目编码"
            min-width="120"
            show-overflow-tooltip
          />
          <el-table-column
            prop="authCode"
            label="鉴权编码"
            min-width="135"
            show-overflow-tooltip
          />
          <el-table-column label="鉴权方式" min-width="105"
            ><template v-slot="{ row }">{{
              optionLabel(authTypeOptions, row.authType)
            }}</template></el-table-column
          >
          <el-table-column
            prop="tokenCode"
            label="Token 编码"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column
            prop="billingCode"
            label="计费编码"
            min-width="130"
            show-overflow-tooltip
          />
          <el-table-column prop="ruleVersion" label="执行版本" width="100" />
          <el-table-column prop="revisionId" label="执行修订" width="110" />
          <el-table-column prop="billingBasis" label="计费依据" min-width="130">
            <template v-slot="{ row }">{{ billingBasisLabel(row.billingBasis) }}</template>
          </el-table-column>
          <el-table-column prop="rootTraceId" label="关联 ID" min-width="300" show-overflow-tooltip />
          <el-table-column label="对象" width="90" align="center">
            <template v-slot="{ row }">{{
              optionLabel(billingTargetOptions, row.billingTarget)
            }}</template>
          </el-table-column>
          <el-table-column
            prop="ruleCode"
            label="规则编码"
            min-width="130"
            show-overflow-tooltip
          />
          <el-table-column
            prop="apiCode"
            label="接口编码"
            min-width="130"
            show-overflow-tooltip
          />
          <el-table-column
            prop="errorMessage"
            label="计费时提示"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column label="计费时执行情况" width="175" align="center">
            <template v-slot="{ row }">
              <el-tag
                :type="billingOutcomeType(row)"
                size="small"
                >{{ billingOutcomeLabel(row) }}</el-tag
              >
            </template>
          </el-table-column>
          <el-table-column label="当前试运行状态" width="145" fixed="right">
            <template v-slot="{ row }">
              <el-button
                v-if="row.billingBasis === 'DRAFT_TRIAL' && row.rootTraceId"
                :data-testid="`billing-trial-${row.rootTraceId}`"
                type="primary"
                link
                size="small"
                @click="openTrialDetail(row)"
              >查看当前状态</el-button>
              <span v-else>{{ row.billingBasis === 'DRAFT_TRIAL' ? '未记录关联 ID' : '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="unitPrice" label="单价" width="110" align="right" />
          <el-table-column
            prop="quantity"
            label="数量"
            width="90"
            align="right"
          />
          <el-table-column prop="amount" label="金额" width="110" align="right">
            <template v-slot="{ row }"
              >{{ row.currency || 'CNY' }} {{ row.amount || 0 }}</template
            >
          </el-table-column>
          <el-table-column
            prop="costTimeMs"
            label="耗时(ms)"
            width="100"
            align="right"
          />
        </el-table>
        <el-pagination
          v-show="!recordError"
          :disabled="recordLoading"
          :current-page="recordQuery.pageNum"
          :page-size="recordQuery.pageSize"
          :total="recordTotal"
          layout="total,sizes,prev,pager,next"
          :page-sizes="[10, 20, 30, 50, 100, 200, 500]"
          @current-change="
            (p) => {
              recordQuery.pageNum = p
              loadRecords()
            }
          "
          @size-change="
            (s) => {
              recordQuery.pageSize = s
              recordQuery.pageNum = 1
              loadRecords()
            }
          "
        />
      </el-tab-pane>

      <el-tab-pane class="management-table-region" label="计费汇总" name="summary">
        <el-alert
          data-testid="billing-summary-basis"
          title="成功和未成功按计费时记录统计，不代表试运行最终成功率；后续状态请在计费明细中查看。"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 12px"
        />
        <div class="uiue-search-container uiue-filter-toolbar">
          <el-form
            :inline="true"
            size="small"
            @keyup.enter="handleSummaryQuery"
          >
            <el-form-item label="项目编码">
              <project-filter-select
                v-model:value="summaryQuery.projectCode"
                field="projectCode"
                placeholder="输入项目编码"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="项目名称">
              <project-filter-select
                v-model:value="summaryQuery.projectName"
                field="projectName"
                placeholder="输入项目名称"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="计费对象">
              <el-select
                v-model="summaryQuery.billingTarget"
                clearable
                placeholder="全部"
                style="width: 120px"
              >
                <el-option
                  v-for="item in billingTargetOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="计费编码">
              <remote-filter-select
                v-model:value="summaryQuery.billingCode"
                :fetch-options="fetchSummaryCodeOptions"
                option-label-key="billingCode"
                option-value-key="billingCode"
                allow-free-input
                placeholder="前缀筛选"
                style="width: 150px"
              />
            </el-form-item>
            <el-form-item label="鉴权方式">
              <el-select
                v-model="summaryQuery.authType"
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
            <el-form-item label="鉴权编码"
              ><el-input
                v-model="summaryQuery.authCode"
                clearable
                placeholder="鉴权编码"
                style="width: 145px"
            /></el-form-item>
            <execution-version-filter v-model="summaryQuery" />
            <el-form-item label="汇总日期">
              <el-date-picker
                v-model="summaryDateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                style="width: 240px"
                @change="onSummaryDateChange"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSummaryQuery"
                >查询</el-button
              >
              <el-button @click="resetSummaryQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <div class="uiue-btn-bar">
            <div class="btn-right">
              <table-column-settings :columns="summaryColumns" storage-key="tianshu:table-columns:billing-summary" />
              <el-button
                v-permission="'billing:edit'"
                size="small"
                type="primary"
                :icon="ElIconRefresh"
                :loading="summaryRefreshSubmitting || summaryRefreshActive"
                :disabled="summaryRefreshSubmitting || summaryRefreshActive || !!summaryRefreshError"
                @click="handleRefreshSummary"
                >刷新汇总</el-button
              >
            </div>
          </div>
        </div>

        <el-alert v-if="summaryQuery.ruleId" title="当前按所选规则版本的执行明细实时汇总，无需刷新日汇总。" type="info" :closable="false" show-icon class="summary-refresh-status" />
        <el-alert v-if="summaryRefreshError" type="warning" :closable="false" show-icon class="summary-refresh-status">
          <span>{{ summaryRefreshError }}</span>
          <el-button size="small" @click="summaryRefreshTask ? pollSummaryRefresh() : loadSummaryRefreshTask()">重试查询</el-button>
        </el-alert>
        <el-alert v-else-if="summaryRefreshTask" :title="summaryRefreshTitle" :description="summaryRefreshHint"
          :type="summaryRefreshTask.status === 'FAILED' ? 'error' : summaryRefreshTask.status === 'SUCCEEDED' ? 'success' : 'info'"
          :closable="false" show-icon class="summary-refresh-status" data-testid="summary-refresh-status" />

        <el-table class="management-table" show-overflow-tooltip
          :data="summaryList"
          border
          size="small"
          v-loading="summaryLoading"
          :empty-text="summaryError || '暂无数据'"
          style="width: 100%"
        >
          <el-table-column prop="summaryDate" label="汇总日期" width="110" />
          <el-table-column
            prop="projectCode"
            label="项目编码"
            min-width="120"
            show-overflow-tooltip
          />
          <el-table-column
            prop="authCode"
            label="鉴权编码"
            min-width="135"
            show-overflow-tooltip
          />
          <el-table-column label="鉴权方式" min-width="105"
            ><template v-slot="{ row }">{{
              optionLabel(authTypeOptions, row.authType)
            }}</template></el-table-column
          >
          <el-table-column
            prop="billingCode"
            label="计费编码"
            min-width="130"
            show-overflow-tooltip
          />
          <el-table-column label="对象" width="90" align="center">
            <template v-slot="{ row }">{{
              optionLabel(billingTargetOptions, row.billingTarget)
            }}</template>
          </el-table-column>
          <el-table-column
            prop="totalCount"
            label="总次数"
            width="90"
            align="right"
          />
          <el-table-column
            prop="successCount"
            label="计费时成功"
            width="120"
            align="right"
          />
          <el-table-column
            prop="failCount"
            label="计费时未成功"
            width="130"
            align="right"
          />
          <el-table-column
            prop="totalQuantity"
            label="计费数量"
            width="110"
            align="right"
          />
          <el-table-column
            prop="totalAmount"
            label="总金额"
            width="120"
            align="right"
          >
            <template v-slot="{ row }"
              >{{ row.currency || 'CNY' }} {{ row.totalAmount || 0 }}</template
            >
          </el-table-column>
          <el-table-column
            prop="avgCostTimeMs"
            label="平均耗时(ms)"
            width="130"
            align="right"
          />
        </el-table>
        <el-pagination
          v-show="!summaryError"
          :disabled="summaryLoading"
          :current-page="summaryQuery.pageNum"
          :page-size="summaryQuery.pageSize"
          :total="summaryTotal"
          layout="total,sizes,prev,pager,next"
          :page-sizes="[10, 20, 30, 50, 100, 200, 500]"
          @current-change="
            (p) => {
              summaryQuery.pageNum = p
              loadSummaries()
            }
          "
          @size-change="
            (s) => {
              summaryQuery.pageSize = s
              summaryQuery.pageNum = 1
              loadSummaries()
            }
          "
        />
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="trialDialogVisible"
      title="试运行与计费记录"
      width="min(720px, calc(100vw - 32px))"
      append-to-body
      @close="closeTrialDetail"
    >
      <div v-if="trialBillingRecord" class="billing-trial-detail" data-testid="billing-trial-detail">
        <el-alert type="info" :closable="false" show-icon>
          计费时记录不代表当前试运行状态。后续完成、取消或过期不会改写已发生的费用、单价和时间。
        </el-alert>
        <h3>计费时记录</h3>
        <dl class="billing-trial-facts">
          <dt>计费依据</dt><dd>草稿试运行</dd>
          <dt>关联 ID</dt><dd class="billing-trial-id">{{ trialBillingRecord.rootTraceId }}</dd>
          <dt>计费编码</dt><dd>{{ trialBillingRecord.billingCode || '—' }}</dd>
          <dt>接口编码</dt><dd>{{ trialBillingRecord.apiCode || '—' }}</dd>
          <dt>发生时间</dt><dd>{{ (trialBillingRecord.occurTime || '—').replace('T', ' ') }}</dd>
          <dt>当时执行情况</dt><dd><el-tag :type="billingOutcomeType(trialBillingRecord)">{{ billingOutcomeLabel(trialBillingRecord) }}</el-tag></dd>
          <dt>当时提示</dt><dd>{{ trialBillingRecord.errorMessage || '—' }}</dd>
          <dt>单价 / 数量</dt><dd>{{ trialBillingRecord.currency || 'CNY' }} {{ trialBillingRecord.unitPrice ?? '—' }} / {{ trialBillingRecord.quantity ?? '—' }}</dd>
          <dt>金额</dt><dd>{{ trialBillingRecord.currency || 'CNY' }} {{ trialBillingRecord.amount ?? '—' }}</dd>
        </dl>
        <h3>当前试运行状态</h3>
        <p class="billing-trial-help">仅可查询本人且有外数查看权限的会话。查询不会再次调用供应商；状态以最近一次查询为准。</p>
        <p v-if="trialStatusLoading" role="status">正在查询当前试运行状态…</p>
        <el-alert v-else-if="trialStatusError" type="warning" :closable="false" show-icon>
          无法查看当前试运行状态，计费记录仍有效。{{ trialStatusError }}
        </el-alert>
        <div v-else-if="trialCurrentStatus" class="billing-trial-current">
          <el-tag :type="trialStatusType(trialCurrentStatus)">{{ trialStatusLabel(trialCurrentStatus) }}</el-tag>
          <span>查询于 {{ trialStatusCheckedAt }}</span>
        </div>
        <p class="billing-trial-help">关联 ID 即本次试运行会话 ID，可复制到外数调用日志的 Trace ID 筛选中核对调用记录。</p>
      </div>
      <template #footer>
        <el-button data-testid="billing-trial-close" @click="closeTrialDetail">关闭</el-button>
        <el-button data-testid="billing-trial-refresh" type="primary" :loading="trialStatusLoading" @click="refreshTrialStatus">刷新当前状态</el-button>
      </template>
    </el-dialog>

    <el-dialog
      :title="configForm.id ? '编辑计费配置' : '新建计费配置'"
      v-model="configDialogVisible"
      width="680px"
      append-to-body
      @close="closeConfigDialog"
    >
      <el-form
        ref="configForm"
        :model="configForm"
        :rules="configRules"
        label-width="110px"
        size="small"
      >
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="作用范围">
              <el-select
                v-model="configForm.scope"
                style="width: 100%"
                :disabled="!!configForm.id"
                @change="onConfigScopeChange"
              >
                <el-option label="全局" value="GLOBAL" />
                <el-option label="项目级" value="PROJECT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item
              v-if="configForm.scope === 'PROJECT'"
              label="所属项目"
              prop="projectId"
            >
              <project-resource-select
                v-model:value="configForm.projectId"
                :disabled="!!configForm.id"
                style="width: 100%"
                @change="onBillingProjectChange"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="计费编码" prop="billingCode">
              <el-input
                v-model="configForm.billingCode"
                placeholder="如 ENGINE_COUNT"
                :disabled="!!configForm.id"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计费名称" prop="billingName">
              <el-input
                v-model="configForm.billingName"
                placeholder="如 规则执行计费"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="计费对象">
              <el-select
                v-model="configForm.billingTarget"
                style="width: 100%"
                @change="onBillingTargetChange"
              >
                <el-option
                  v-for="item in billingTargetOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计费方式" prop="chargeType">
              <el-select v-model="configForm.chargeType" style="width: 100%">
                <el-option
                  v-for="item in chargeTypeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="24">
            <el-form-item label="指定对象">
              <remote-filter-select
                :key="`${configForm.billingTarget}:${configForm.scope}:${configForm.projectId}`"
                v-model:value="configForm.targetRefId"
                :fetch-options="loadTargetOptions"
                :content-type="({ ENGINE: 'RULE', API: 'EXTERNAL_API', DB: 'DATABASE' })[configForm.billingTarget]"
                :selected-option="selectedTargetOption"
                option-value-key="id"
                option-label-key="displayName"
                placeholder="不选则作用于该类型全部对象"
                style="width: 100%"
              />
              <div v-if="targetError" role="alert" class="target-load-error">
                <span>{{ targetError }}</span>
                <el-button size="small" @click="loadTargetOptions">重试</el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="单价" prop="unitPrice">
              <el-input-number
                v-model="configForm.unitPrice"
                :min="0"
                :precision="6"
                :step="0.01"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="币种" prop="currency">
              <el-input v-model="configForm.currency" placeholder="CNY" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item v-if="configForm.id" label="状态">
              <el-switch
                v-model="configForm.status"
                :active-value="1"
                :inactive-value="0"
                active-text="启用"
                inactive-text="停用"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="生效时间">
              <el-date-picker
                v-model="configForm.effectiveTime"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm:ss"
                placeholder="不限制"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="失效时间">
              <el-date-picker
                v-model="configForm.expireTime"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm:ss"
                placeholder="不限制"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="说明">
          <el-input
            v-model="configForm.description"
            type="textarea"
            :rows="2"
          />
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <div>
          <el-button size="small" @click="configDialogVisible = false"
            >取消</el-button
          >
          <el-button
            v-permission="'billing:edit'"
            size="small"
            type="primary"
            :loading="configSaving"
            @click="handleSaveConfig"
            >保存草稿</el-button
          >
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import ExecutionVersionFilter from '@/components/common/ExecutionVersionFilter.vue'
import GovernancePublishAction from '@/components/GovernancePublishAction.vue'
import GovernanceWorkspaceToolbar from '@/components/GovernanceWorkspaceToolbar.vue'
import GovernanceResourceStatus from '@/components/GovernanceResourceStatus.vue'
import { completeResourceChange } from '@/utils/governanceWorkspace'
import governanceWorkspaceMixin from '@/mixins/governanceWorkspaceMixin'
import { markRaw } from 'vue'
import {
  Plus as ElIconPlus,
  Refresh as ElIconRefresh,
} from '@element-plus/icons-vue'
import {
  createBillingConfigDraft,
  deleteBillingConfigDraft,
  listBillingConfigs,
  listBillingRecords,
  listBillingSummaries,
  refreshBillingSummary,
  getBillingSummaryRefreshTask,
  getLatestBillingSummaryRefresh,
  updateBillingConfigDraft,
  changeBillingConfigStatusDraft,
} from '@/api/billing'
import { listDefinitions, getDefinition } from '@/api/definition'
import { listApiConfigs, getApiConfig, getApiConfigTrial } from '@/api/datasource'
import { listDbDatasources, getDbDatasource } from '@/api/database'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import ProjectResourceSelect from '@/components/ProjectResourceSelect.vue'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import TableColumnSettings from '@/components/common/TableColumnSettings.vue'
import { trialStatusLabel, trialStatusType } from '@/utils/apiTrialStatus'
import { operationColumnWidth as getOperationColumnWidth } from '@/utils/operationColumnWidth'

export default {
  mixins: [governanceWorkspaceMixin],
  data() {
    return {
      billingColumns: [
        { key: 'scope', label: '作用范围' }, { key: 'projectName', label: '项目名称' },
        { key: 'billingCode', label: '计费编码' }, { key: 'billingName', label: '计费名称' },
        { key: 'billingTarget', label: '对象' }, { key: 'chargeType', label: '方式' },
        { key: 'unitPrice', label: '单价' }, { key: 'status', label: '状态' },
      ],
      summaryColumns: [
        { key: 'summaryDate', label: '汇总日期' }, { key: 'projectCode', label: '项目编码' },
        { key: 'authCode', label: '鉴权编码' }, { key: 'totalCount', label: '调用次数' },
        { key: 'successCount', label: '计费时成功' }, { key: 'failCount', label: '计费时未成功' }, { key: 'amount', label: '金额' },
      ],
      recordColumns: [
        { key: 'occurTime', label: '发生时间' }, { key: 'projectCode', label: '项目编码' },
        { key: 'authCode', label: '鉴权编码' }, { key: 'authType', label: '鉴权方式' },
        { key: 'tokenCode', label: 'Token 编码' }, { key: 'billingCode', label: '计费编码' },
        { key: 'billingTarget', label: '对象' }, { key: 'ruleCode', label: '规则编码' },
        { key: 'billingBasis', label: '计费依据' }, { key: 'rootTraceId', label: '关联 ID' },
        { key: 'apiCode', label: '接口编码' }, { key: 'errorMessage', label: '计费时提示' },
        { key: 'success', label: '计费时执行情况' }, { key: 'trialStatus', label: '当前试运行状态' },
        { key: 'unitPrice', label: '单价' }, { key: 'quantity', label: '数量' },
        { key: 'amount', label: '金额' }, { key: 'costTimeMs', label: '耗时(ms)' },
      ],
      activeTab: 'config',
      targetOptions: [],
      targetKnownOptions: {},
      targetLoading: false,
      targetRequestId: 0,
      targetError: '',
      configList: [],
      configTotal: 0,
      configLoading: false,
      configRequestId: 0,
      configError: '',
      configDialogVisible: false,
      configOriginalStatus: null,
      configSaving: false,
      configQuery: {
        pageNum: 1,
        pageSize: 20,
        projectCode: '',
        projectName: '',
        scope: '',
        billingTarget: '',
        billingCode: '',
        status: '',
      },
      configForm: this.emptyConfigForm(),
      configRules: {
        billingCode: [
          { required: true, message: '请输入计费编码', trigger: 'blur' },
        ],
        billingName: [
          { required: true, message: '请输入计费名称', trigger: 'blur' },
        ],
        projectId: [
          { required: true, message: '请选择所属项目', trigger: 'change' },
        ],
        billingTarget: [
          { required: true, message: '请选择对象类型', trigger: 'change' },
        ],
        chargeType: [
          { required: true, message: '请选择计费方式', trigger: 'change' },
        ],
        unitPrice: [
          { required: true, type: 'number', min: 0, message: '单价不能小于0' },
        ],
        currency: [
          { required: true, message: '请输入币种', trigger: 'blur' },
        ],
      },
      recordList: [],
      recordTotal: 0,
      recordLoading: false,
      recordRequestId: 0,
      recordError: '',
      trialDialogVisible: false,
      trialBillingRecord: null,
      trialCurrentStatus: '',
      trialStatusCheckedAt: '',
      trialStatusLoading: false,
      trialStatusError: '',
      trialStatusRequestId: 0,
      recordDateRange: [],
      recordQuery: {
        pageNum: 1,
        pageSize: 20,
        billingTarget: '',
        billingCode: '',
        projectCode: '',
        projectName: '',
        authType: '',
        authCode: '',
        tokenCode: '',
        beginTime: '',
        endTime: '',
      },
      summaryList: [],
      summaryTotal: 0,
      summaryLoading: false,
      summaryRequestId: 0,
      summaryError: '',
      summaryDateRange: [],
      summaryQuery: {
        pageNum: 1,
        pageSize: 20,
        billingTarget: '',
        billingCode: '',
        projectCode: '',
        projectName: '',
        authType: '',
        authCode: '',
        ruleId: null,
        versionMode: 'ALL',
        ruleVersion: null,
        revisionId: null,
        beginDate: '',
        endDate: '',
      },
      refreshDate: new Date().toISOString().slice(0, 10),
      summaryRefreshTask: null,
      summaryRefreshSubmitting: false,
      summaryRefreshError: '',
      summaryRefreshRequestId: 0,
      summaryRefreshTimer: null,
      summaryRefreshNotify: false,
      billingTargetOptions: [
        { label: '规则引擎', value: 'ENGINE' },
        { label: '外数 API', value: 'API' },
        { label: '数据库查询', value: 'DB' },
      ],
      chargeTypeOptions: [
        { label: '按次', value: 'COUNT' },
        { label: '成功计费', value: 'SUCCESS' },
        { label: '按耗时', value: 'DURATION' },
        { label: '固定金额', value: 'FIXED' },
      ],
      authTypeOptions: [
        { label: '兼容令牌', value: 'LEGACY_TOKEN' },
        { label: '账号密码', value: 'BASIC' },
        { label: 'API Key', value: 'API_KEY' },
        { label: 'HMAC-SHA256', value: 'HMAC_SHA256' },
      ],
      ElIconPlus: markRaw(ElIconPlus),
      ElIconRefresh: markRaw(ElIconRefresh),
    }
  },
  name: 'BillingList',
  components: { ExecutionVersionFilter, GovernancePublishAction, GovernanceWorkspaceToolbar,
    GovernanceResourceStatus, ProjectFilterSelect, ProjectResourceSelect, RemoteFilterSelect, TableColumnSettings },
  computed: {
    selectedTargetOption() {
      const item = this.targetKnownOptions?.[`${this.configForm?.billingTarget}:${this.configForm?.targetRefId}`]
      return item ? { ...item, displayName: this.targetOptionLabel(item) } : null
    },
    summaryRefreshActive() { return ['PENDING', 'RUNNING'].includes(this.summaryRefreshTask?.status) },
    summaryRefreshTitle() {
      const task = this.summaryRefreshTask
      const labels = { PENDING: '等待汇总', RUNNING: '汇总中', SUCCEEDED: '汇总已完成', FAILED: '汇总失败' }
      return task ? `${task.summaryDate} · ${labels[task.status]}` : ''
    },
    summaryRefreshHint() {
      const task = this.summaryRefreshTask
      if (!task) return ''
      if (task.status === 'FAILED') return task.errorMessage || '汇总处理失败，原汇总保留，可重新刷新'
      if (task.status === 'SUCCEEDED') return `已汇总 ${task.recordCount ?? 0} 条明细，生成 ${task.summaryCount ?? 0} 条汇总。后续到达的明细可再次刷新。`
      return '后台处理期间保留上次成功的汇总。离开页面后任务仍会继续，再次进入可查看进度。'
    },
  },
  watch: {
    refreshDate() { if (this.activeTab === 'summary') this.loadSummaryRefreshTask() },
  },
  created() {
    this.loadConfigs()
  },
  beforeUnmount() {
    this.stopSummaryRefresh()
    this.configRequestId++
    this.recordRequestId++
    this.summaryRequestId++
    this.closeConfigDialog()
    this.closeTrialDetail()
  },
  deactivated() { this.stopSummaryRefresh() },
  activated() { if (this.activeTab === 'summary') this.loadSummaryRefreshTask() },
  methods: {
    operationColumnWidth(labels) { return getOperationColumnWidth(labels) },
    trialStatusLabel,
    trialStatusType,
    billingBasisLabel(basis) {
      return { DRAFT_TRIAL: '草稿试运行', EXECUTION_SNAPSHOT: '执行时配置', CURRENT_CONFIG: '当前计费配置', LEGACY_REPLAY: '历史重放' }[basis] || basis || '未记录'
    },
    billingOutcomeLabel(row) {
      if (row.success === 1) return '成功'
      if (row.success !== 0) return '未记录'
      return row.billingBasis === 'DRAFT_TRIAL' ? '当时未完成或未成功' : '失败'
    },
    billingOutcomeType(row) {
      if (row.success === 1) return 'success'
      if (row.success !== 0) return 'info'
      return row.billingBasis === 'DRAFT_TRIAL' ? 'warning' : 'danger'
    },
    openTrialDetail(row) {
      if (row.billingBasis !== 'DRAFT_TRIAL' || !row.rootTraceId) return
      this.trialBillingRecord = { ...row }
      this.trialDialogVisible = true
      this.refreshTrialStatus()
    },
    closeTrialDetail() {
      this.trialStatusRequestId++
      this.trialDialogVisible = false
      this.trialBillingRecord = null
      this.trialCurrentStatus = ''
      this.trialStatusCheckedAt = ''
      this.trialStatusError = ''
      this.trialStatusLoading = false
    },
    async refreshTrialStatus() {
      if (!this.trialDialogVisible || !this.trialBillingRecord?.rootTraceId) return
      const trialId = this.trialBillingRecord.rootTraceId
      const requestId = ++this.trialStatusRequestId
      this.trialCurrentStatus = ''
      this.trialStatusCheckedAt = ''
      this.trialStatusError = ''
      this.trialStatusLoading = true
      try {
        const response = await getApiConfigTrial(trialId)
        if (requestId !== this.trialStatusRequestId) return
        if (response?.data?.trialId !== trialId || !response.data.status) throw new Error('返回的会话信息不完整或不一致，请重试。')
        this.trialCurrentStatus = response.data.status
        this.trialStatusCheckedAt = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'medium', hourCycle: 'h23' }).format(new Date())
      } catch (error) {
        if (requestId === this.trialStatusRequestId) this.trialStatusError = '请确认会话属于本人、具有外数查看权限，或稍后重试。'
      } finally {
        if (requestId === this.trialStatusRequestId) this.trialStatusLoading = false
      }
    },
    fetchConfigCodeOptions({ query, pageNum, pageSize }) {
      return listBillingConfigs(this.cleanParams({ ...this.configQuery, billingCode: query, pageNum, pageSize }))
    },
    fetchRecordCodeOptions({ query, pageNum, pageSize }) {
      return listBillingRecords(this.cleanParams({ ...this.recordQuery, billingCode: query, pageNum, pageSize }))
    },
    fetchSummaryCodeOptions({ query, pageNum, pageSize }) {
      return listBillingSummaries(this.cleanParams({ ...this.summaryQuery, billingCode: query, pageNum, pageSize }))
    },
    emptyConfigForm() {
      return {
        id: null,
        scope: 'PROJECT',
        projectId: null,
        billingCode: '',
        billingName: '',
        billingTarget: 'ENGINE',
        targetRefId: null,
        chargeType: 'COUNT',
        unitPrice: 0,
        currency: 'CNY',
        effectiveTime: '',
        expireTime: '',
        description: '',
        status: 1,
      }
    },
    async loadConfigs() {
      const requestId = ++this.configRequestId
      this.configLoading = true
      this.configError = ''
      this.configList = []
      try {
        const res = await listBillingConfigs(
          this.cleanParams({ ...this.configQuery })
        )
        if (requestId !== this.configRequestId) return
        this.configList = (res.data && res.data.records) || []
        this.configTotal = (res.data && res.data.total) || 0
      } catch {
        if (requestId === this.configRequestId) this.configError = '计费配置加载失败，请重新查询'
      } finally {
        if (requestId === this.configRequestId) this.configLoading = false
      }
    },
    async loadRecords() {
      const requestId = ++this.recordRequestId
      this.recordLoading = true
      this.recordError = ''
      this.recordList = []
      try {
        const res = await listBillingRecords(
          this.cleanParams({ ...this.recordQuery })
        )
        if (requestId !== this.recordRequestId) return
        this.recordList = (res.data && res.data.records) || []
        this.recordTotal = (res.data && res.data.total) || 0
      } catch {
        if (requestId === this.recordRequestId) this.recordError = '计费明细加载失败，请重新查询'
      } finally {
        if (requestId === this.recordRequestId) this.recordLoading = false
      }
    },
    async loadSummaries() {
      const requestId = ++this.summaryRequestId
      this.summaryLoading = true
      this.summaryError = ''
      this.summaryList = []
      try {
        const res = await listBillingSummaries(
          this.cleanParams({ ...this.summaryQuery })
        )
        if (requestId !== this.summaryRequestId) return
        this.summaryList = (res.data && res.data.records) || []
        this.summaryTotal = (res.data && res.data.total) || 0
      } catch {
        if (requestId === this.summaryRequestId) this.summaryError = '计费汇总加载失败，请重新查询'
      } finally {
        if (requestId === this.summaryRequestId) this.summaryLoading = false
      }
    },
    onTabChange(tab) {
      const paneName =
        tab && (tab.paneName ?? tab.name ?? (tab.props && tab.props.name))
      this.activeTab =
        paneName && paneName.value !== undefined
          ? paneName.value
          : paneName || this.activeTab
      if (this.activeTab === 'config') this.loadConfigs()
      if (this.activeTab === 'record') this.loadRecords()
      if (this.activeTab === 'summary') { this.loadSummaries(); this.loadSummaryRefreshTask() }
      else this.stopSummaryRefresh()
    },
    handleConfigQuery() {
      this.configQuery.pageNum = 1
      this.loadConfigs()
    },
    resetConfigQuery() {
      this.configQuery = {
        pageNum: 1,
        pageSize: this.configQuery.pageSize,
        projectCode: '',
        projectName: '',
        scope: '',
        billingTarget: '',
        billingCode: '',
        status: '',
      }
      this.loadConfigs()
    },
    handleRecordQuery() {
      this.recordQuery.pageNum = 1
      this.loadRecords()
    },
    resetRecordQuery() {
      this.recordDateRange = []
      this.recordQuery = {
        pageNum: 1,
        pageSize: this.recordQuery.pageSize,
        billingTarget: '',
        billingCode: '',
        projectCode: '',
        projectName: '',
        authType: '',
        authCode: '',
        tokenCode: '',
        beginTime: '',
        endTime: '',
      }
      this.loadRecords()
    },
    handleSummaryQuery() {
      this.summaryQuery.pageNum = 1
      this.loadSummaries()
    },
    resetSummaryQuery() {
      this.summaryDateRange = []
      this.summaryQuery = {
        pageNum: 1,
        pageSize: this.summaryQuery.pageSize,
        billingTarget: '',
        billingCode: '',
        projectCode: '',
        projectName: '',
        authType: '',
        authCode: '',
        ruleId: null,
        versionMode: 'ALL',
        ruleVersion: null,
        revisionId: null,
        beginDate: '',
        endDate: '',
      }
      this.loadSummaries()
    },
    handleCreateConfig() {
      this.configForm = this.emptyConfigForm()
      this.configOriginalStatus = null
      this.targetOptions = []
      this.loadTargetOptions()
      this.configDialogVisible = true
    },
    handleEditConfig(row) {
      this.configForm = { ...this.emptyConfigForm(), ...row }
      this.configOriginalStatus = row.status
      this.targetOptions = []
      this.loadTargetOptions()
      this.configDialogVisible = true
    },
    handleSaveConfig() {
      this.$refs.configForm.validate(async (valid) => {
        if (!valid) return
        const data = this.normalizeConfig(this.configForm)
        if (
          data.effectiveTime &&
          data.expireTime &&
          new Date(data.effectiveTime) > new Date(data.expireTime)
        ) {
          this.$message.warning('生效时间不能晚于失效时间')
          return
        }
        this.configSaving = true
        try {
          let response
          if (!data.id) {
            response = await createBillingConfigDraft(data)
          } else if (data.status !== this.configOriginalStatus) {
            response = await changeBillingConfigStatusDraft(data, data.status)
          } else {
            response = await updateBillingConfigDraft(data)
          }
          this.configDialogVisible = false
          await completeResourceChange(this, response, () => this.loadConfigs())
        } catch (error) {
          this.$message.error(
            '生成审批草稿失败: ' + (error.message || error)
          )
        } finally {
          this.configSaving = false
        }
      })
    },
    handleDeleteConfig(row) {
      return this.$confirm(
        `确定删除计费配置「${row.billingName}」？待审批的新增草稿须先驳回；已生效内容需要删除审批。`,
        '确认删除',
        { type: 'warning' }
      )
        .then(async () => {
          const response = await deleteBillingConfigDraft(row)
          await completeResourceChange(this, response, () => this.loadConfigs())
        })
        .catch(() => {})
    },
    async handleRefreshSummary() {
      if (this.summaryRefreshSubmitting || this.summaryRefreshActive || this.summaryRefreshError) return
      this.stopSummaryRefresh()
      this.summaryRefreshTask = null
      const requestId = this.summaryRefreshRequestId
      this.summaryRefreshSubmitting = true
      this.summaryRefreshError = ''
      this.summaryRefreshNotify = true
      try {
        const res = await refreshBillingSummary({ summaryDate: this.refreshDate })
        if (requestId !== this.summaryRefreshRequestId) return
        await this.acceptSummaryRefreshTask(res.data, true)
      } catch {
        if (requestId === this.summaryRefreshRequestId) this.summaryRefreshError = '提交结果未确认，请查询刷新状态后重试'
      } finally {
        if (requestId === this.summaryRefreshRequestId) this.summaryRefreshSubmitting = false
      }
    },
    stopSummaryRefresh() {
      this.summaryRefreshRequestId++
      clearTimeout(this.summaryRefreshTimer)
      this.summaryRefreshTimer = null
      this.summaryRefreshSubmitting = false
    },
    async loadSummaryRefreshTask() {
      this.stopSummaryRefresh()
      const requestId = this.summaryRefreshRequestId
      this.summaryRefreshTask = null
      this.summaryRefreshNotify = false
      this.summaryRefreshError = ''
      try {
        const res = await getLatestBillingSummaryRefresh({ summaryDate: this.refreshDate })
        if (requestId !== this.summaryRefreshRequestId) return
        if (res?.data != null) await this.acceptSummaryRefreshTask(res.data)
      } catch {
        if (requestId === this.summaryRefreshRequestId) this.summaryRefreshError = '刷新进度读取失败，请重试查询'
      }
    },
    async pollSummaryRefresh() {
      clearTimeout(this.summaryRefreshTimer)
      this.summaryRefreshTimer = null
      const taskId = this.summaryRefreshTask?.taskId
      if (!taskId) return this.loadSummaryRefreshTask()
      const requestId = ++this.summaryRefreshRequestId
      try {
        const res = await getBillingSummaryRefreshTask(taskId)
        if (requestId !== this.summaryRefreshRequestId) return
        if (res.data?.taskId !== taskId) throw new Error('汇总任务不一致')
        await this.acceptSummaryRefreshTask(res.data, true)
      } catch {
        if (requestId === this.summaryRefreshRequestId) this.summaryRefreshError = '刷新进度读取失败，后台任务状态尚未确认，请重试查询'
      }
    },
    async acceptSummaryRefreshTask(task, refreshOnSuccess = false) {
      if (!task?.taskId || !task.summaryDate || !['PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED'].includes(task.status)) throw new Error('汇总任务信息不完整')
      this.summaryRefreshTask = task
      this.summaryRefreshError = ''
      if (['PENDING', 'RUNNING'].includes(task.status)) {
        this.summaryRefreshTimer = setTimeout(() => this.pollSummaryRefresh(), 1500)
      } else {
        if (task.status === 'SUCCEEDED' && refreshOnSuccess) {
          if (this.summaryRefreshNotify) this.$message.success(`${task.summaryDate} 汇总已完成，共生成 ${task.summaryCount ?? 0} 条`)
          await this.loadSummaries()
        }
        this.summaryRefreshNotify = false
      }
    },
    onConfigScopeChange(scope) {
      if (scope === 'GLOBAL') this.configForm.projectId = 0
      this.resetTargetRefAndLoadOptions()
    },
    onBillingTargetChange() {
      this.resetTargetRefAndLoadOptions()
    },
    onBillingProjectChange() {
      this.resetTargetRefAndLoadOptions()
    },
    resetTargetRefAndLoadOptions() {
      if (!this.configForm) return
      this.configForm.targetRefId = null
      this.loadTargetOptions()
    },
    closeConfigDialog() {
      this.targetRequestId++
      this.targetLoading = false
      this.targetOptions = []
      this.targetKnownOptions = {}
      this.targetError = ''
    },
    async loadTargetOptions({ query = '', pageNum = 1, pageSize = 20 } = {}) {
      if (!this.configForm) return
      const requestId = ++this.targetRequestId
      this.targetLoading = true
      this.targetOptions = []
      this.targetError = ''
      try {
        const baseParams = { pageNum, pageSize, status: 1 }
        if (this.configForm.scope === 'PROJECT' && this.configForm.projectId) {
          baseParams.projectId = this.configForm.projectId
        }
        let res
        if (this.configForm.billingTarget === 'ENGINE') {
          res = await listDefinitions({ ...baseParams, ...(query ? { keyword: query } : {}) })
        } else if (this.configForm.billingTarget === 'API') {
          res = await listApiConfigs({ pageNum, pageSize, status: 1, ...(query ? { apiName: query } : {}) })
        } else if (this.configForm.billingTarget === 'DB') {
          res = await listDbDatasources({ ...baseParams, ...(query ? { datasourceName: query } : {}) })
        } else {
          res = { data: { records: [] } }
        }
        if (requestId !== this.targetRequestId) return
        this.targetOptions = (res.data && res.data.records) || []
        if (!this.targetKnownOptions) this.targetKnownOptions = {}
        const type = this.configForm.billingTarget
        for (const item of this.targetOptions) this.targetKnownOptions[`${type}:${item.id}`] = item
        const selectedId = this.configForm.targetRefId
        if (selectedId && !this.targetKnownOptions[`${type}:${selectedId}`]) {
          const loader = { ENGINE: getDefinition, API: getApiConfig, DB: getDbDatasource }[type]
          const selected = loader ? await loader(selectedId) : null
          if (requestId !== this.targetRequestId) return
          if (selected?.data?.id != null) this.targetKnownOptions[`${type}:${selectedId}`] = selected.data
        }
        return { data: { ...res.data, records: this.targetOptions.map(item => ({ ...item, displayName: this.targetOptionLabel(item) })) } }
      } catch {
        if (requestId === this.targetRequestId) this.targetError = '计费对象加载失败'
      } finally {
        if (requestId === this.targetRequestId) this.targetLoading = false
      }
    },
    onRecordDateChange(val) {
      this.recordQuery.beginTime = val ? val[0] : ''
      this.recordQuery.endTime = val ? val[1] : ''
    },
    onSummaryDateChange(val) {
      this.summaryQuery.beginDate = val ? val[0] : ''
      this.summaryQuery.endDate = val ? val[1] : ''
      this.stopSummaryRefresh()
      this.summaryRefreshTask = null
      this.summaryRefreshError = ''
    },
    normalizeConfig(form) {
      const data = { ...form }
      if (data.scope === 'GLOBAL') data.projectId = 0
      if (!data.targetRefId) data.targetRefId = null
      if (!data.effectiveTime) data.effectiveTime = null
      if (!data.expireTime) data.expireTime = null
      return data
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
    optionLabel(options, value) {
      const item = options.find((opt) => opt.value === value)
      return item ? item.label : value || '—'
    },
    targetOptionLabel(item) {
      const code = item.ruleCode || item.apiCode || item.datasourceCode || ''
      const name = item.ruleName || item.apiName || item.datasourceName || ''
      return (
        (name || code || String(item.id)) +
        (code && name !== code ? ' / ' + code : '')
      )
    },
  },
}
</script>

<style lang="scss" scoped>
.summary-refresh-status {
  flex-shrink: 0;
  margin-bottom: 12px;
  .el-button { margin-left: 12px; }
}
.target-load-error {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-color-danger);
}
.billing-trial-detail {
  color: var(--tianshu-text-primary);

  h3 { margin: 20px 0 12px; font-size: 15px; }
}
.billing-trial-facts {
  display: grid;
  grid-template-columns: 100px minmax(0, 1fr);
  gap: 10px 16px;
  margin: 0;
  line-height: 1.6;

  dt { color: var(--tianshu-text-secondary); }
  dd { margin: 0; overflow-wrap: anywhere; }
}
.billing-trial-id { user-select: all; }
.billing-trial-help { color: var(--tianshu-text-secondary); line-height: 1.6; font-size: 13px; }
.billing-trial-current { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.billing-page {
  .module-hint {
    background: #fff7ed;
    border: 1px solid #fed7aa;
    border-radius: 4px;
    padding: 12px 14px;
    margin-bottom: 14px;
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .hint-title {
    color: #c2410c;
    font-weight: 700;
    white-space: nowrap;
  }

  .hint-text {
    color: var(--tianshu-text-secondary);
    line-height: 1.5;
  }
}
</style>
