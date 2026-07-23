<template>
  <div class="uiue-list-page billing-page">
    <div class="module-hint">
      <div class="hint-title">账单管理</div>
      <div class="hint-text">
        统一配置规则执行、外数 API
        和数据库查询的计费项，查看调用明细并按日生成汇总。
      </div>
    </div>

    <el-tabs v-model="activeTab" @tab-click="onTabChange">
      <el-tab-pane label="计费配置" name="config">
        <div class="uiue-search-container">
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
            <el-form-item label="计费对象">
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
              <el-input
                v-model="configQuery.billingCode"
                clearable
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
        </div>

        <div class="uiue-btn-bar">
          <div class="btn-right">
            <el-button
              type="primary"
              size="small"
              :icon="ElIconPlus"
              @click="handleCreateConfig"
              >新建计费项</el-button
            >
          </div>
        </div>

        <el-table
          :data="configList"
          border
          size="small"
          v-loading="configLoading"
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
          <el-table-column prop="status" label="状态" width="70" align="center">
            <template v-slot="{ row }">
              <el-tag
                :type="row.status === 1 ? 'success' : 'info'"
                size="small"
                >{{ row.status === 1 ? '启用' : '停用' }}</el-tag
              >
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" align="center">
            <template v-slot="{ row }">
              <el-button link size="small" @click="handleEditConfig(row)"
                >编辑</el-button
              >
              <el-button
                link
                size="small"
                class="btn-delete"
                @click="handleDeleteConfig(row)"
                >删除</el-button
              >
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          :current-page="configQuery.pageNum"
          :page-size="configQuery.pageSize"
          :total="configTotal"
          layout="total,sizes,prev,pager,next"
          :page-sizes="[10, 30, 50, 100, 200, 500]"
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

      <el-tab-pane label="计费明细" name="record">
        <div class="uiue-search-container">
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
              <el-input
                v-model="recordQuery.billingCode"
                clearable
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
            <el-form-item label="发生时间">
              <el-date-picker
                v-model="recordDateRange"
                type="daterange"
                value-format="yyyy-MM-dd"
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
        </div>

        <el-table
          :data="recordList"
          border
          size="small"
          v-loading="recordLoading"
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
          <el-table-column label="结果" width="70" align="center">
            <template v-slot="{ row }">
              <el-tag
                :type="row.success === 1 ? 'success' : 'danger'"
                size="small"
                >{{ row.success === 1 ? '成功' : '失败' }}</el-tag
              >
            </template>
          </el-table-column>
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
          <el-table-column
            prop="errorMessage"
            label="错误信息"
            min-width="180"
            show-overflow-tooltip
          />
        </el-table>
        <el-pagination
          :current-page="recordQuery.pageNum"
          :page-size="recordQuery.pageSize"
          :total="recordTotal"
          layout="total,sizes,prev,pager,next"
          :page-sizes="[10, 30, 50, 100, 200, 500]"
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

      <el-tab-pane label="计费汇总" name="summary">
        <div class="uiue-search-container">
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
              <el-input
                v-model="summaryQuery.billingCode"
                clearable
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
            <el-form-item label="汇总日期">
              <el-date-picker
                v-model="summaryDateRange"
                type="daterange"
                value-format="yyyy-MM-dd"
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
        </div>

        <div class="uiue-btn-bar">
          <div class="btn-right">
            <el-date-picker
              v-model="refreshDate"
              size="small"
              type="date"
              value-format="yyyy-MM-dd"
              placeholder="选择日期"
              style="width: 150px"
            />
            <el-button
              size="small"
              type="primary"
              :icon="ElIconRefresh"
              @click="handleRefreshSummary"
              >刷新汇总</el-button
            >
          </div>
        </div>

        <el-table
          :data="summaryList"
          border
          size="small"
          v-loading="summaryLoading"
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
            label="成功"
            width="80"
            align="right"
          />
          <el-table-column
            prop="failCount"
            label="失败"
            width="80"
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
          :current-page="summaryQuery.pageNum"
          :page-size="summaryQuery.pageSize"
          :total="summaryTotal"
          layout="total,sizes,prev,pager,next"
          :page-sizes="[10, 30, 50, 100, 200, 500]"
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
      :title="configForm.id ? '编辑计费项' : '新建计费项'"
      v-model="configDialogVisible"
      width="680px"
      append-to-body
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
              <el-select
                v-model="configForm.projectId"
                filterable
                placeholder="请选择项目"
                style="width: 100%"
                @change="onBillingProjectChange"
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
            <el-form-item label="计费编码" prop="billingCode">
              <el-input
                v-model="configForm.billingCode"
                placeholder="如 ENGINE_COUNT"
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
          <el-col :span="8">
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
          <el-col :span="8">
            <el-form-item label="对象ID">
              <el-select
                v-model="configForm.targetRefId"
                clearable
                filterable
                :loading="targetLoading"
                placeholder="请选择计费对象"
                style="width: 100%"
              >
                <el-option
                  v-for="item in targetOptions"
                  :key="item.id"
                  :label="targetOptionLabel(item)"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="计费方式">
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
          <el-col :span="8">
            <el-form-item label="单价">
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
            <el-form-item label="币种">
              <el-input v-model="configForm.currency" placeholder="CNY" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
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
                value-format="yyyy-MM-ddTHH:mm:ss"
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
                value-format="yyyy-MM-ddTHH:mm:ss"
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
          <el-button size="small" type="primary" @click="handleSaveConfig"
            >保存</el-button
          >
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import {
  Plus as ElIconPlus,
  Refresh as ElIconRefresh,
} from '@element-plus/icons-vue'
import {
  createBillingConfig,
  deleteBillingConfig,
  listBillingConfigs,
  listBillingRecords,
  listBillingSummaries,
  refreshBillingSummary,
  updateBillingConfig,
} from '@/api/billing'
import { listProjects } from '@/api/project'
import { listDefinitions } from '@/api/definition'
import { listApiConfigs } from '@/api/datasource'
import { listDbDatasources } from '@/api/database'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'

export default {
  data() {
    return {
      activeTab: 'config',
      projects: [],
      targetOptions: [],
      targetLoading: false,
      configList: [],
      configTotal: 0,
      configLoading: false,
      configDialogVisible: false,
      configQuery: {
        pageNum: 1,
        pageSize: 10,
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
      },
      recordList: [],
      recordTotal: 0,
      recordLoading: false,
      recordDateRange: [],
      recordQuery: {
        pageNum: 1,
        pageSize: 10,
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
      summaryDateRange: [],
      summaryQuery: {
        pageNum: 1,
        pageSize: 10,
        billingTarget: '',
        billingCode: '',
        projectCode: '',
        projectName: '',
        authType: '',
        authCode: '',
        beginDate: '',
        endDate: '',
      },
      refreshDate: '',
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
  components: { ProjectFilterSelect },
  created() {
    this.loadProjects()
    this.loadConfigs()
  },
  methods: {
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
    async loadProjects() {
      try {
        const res = await listProjects({ pageNum: 1, pageSize: 500, status: 1 })
        this.projects = (res.data && res.data.records) || []
      } catch (e) {
        this.projects = []
      }
    },
    async loadConfigs() {
      this.configLoading = true
      try {
        const res = await listBillingConfigs(
          this.cleanParams({ ...this.configQuery })
        )
        this.configList = (res.data && res.data.records) || []
        this.configTotal = (res.data && res.data.total) || 0
      } finally {
        this.configLoading = false
      }
    },
    async loadRecords() {
      this.recordLoading = true
      try {
        const res = await listBillingRecords(
          this.cleanParams({ ...this.recordQuery })
        )
        this.recordList = (res.data && res.data.records) || []
        this.recordTotal = (res.data && res.data.total) || 0
      } finally {
        this.recordLoading = false
      }
    },
    async loadSummaries() {
      this.summaryLoading = true
      try {
        const res = await listBillingSummaries(
          this.cleanParams({ ...this.summaryQuery })
        )
        this.summaryList = (res.data && res.data.records) || []
        this.summaryTotal = (res.data && res.data.total) || 0
      } finally {
        this.summaryLoading = false
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
      if (this.activeTab === 'summary') this.loadSummaries()
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
        beginDate: '',
        endDate: '',
      }
      this.loadSummaries()
    },
    handleCreateConfig() {
      this.configForm = this.emptyConfigForm()
      this.targetOptions = []
      this.loadTargetOptions()
      this.configDialogVisible = true
    },
    handleEditConfig(row) {
      this.configForm = { ...this.emptyConfigForm(), ...row }
      this.targetOptions = []
      this.loadTargetOptions()
      this.configDialogVisible = true
    },
    handleSaveConfig() {
      this.$refs.configForm.validate(async (valid) => {
        if (!valid) return
        const data = this.normalizeConfig(this.configForm)
        if (data.id) {
          await updateBillingConfig(data)
          this.$message.success('更新成功')
        } else {
          await createBillingConfig(data)
          this.$message.success('创建成功')
        }
        this.configDialogVisible = false
        this.loadConfigs()
      })
    },
    handleDeleteConfig(row) {
      this.$confirm('确定删除计费项「' + row.billingName + '」?', '确认', {
        type: 'warning',
      })
        .then(async () => {
          await deleteBillingConfig(row.id)
          this.$message.success('删除成功')
          this.loadConfigs()
        })
        .catch(() => {})
    },
    async handleRefreshSummary() {
      const res = await refreshBillingSummary({ summaryDate: this.refreshDate })
      const count = res.data && res.data.summaryCount
      this.$message.success('汇总已刷新，共生成 ' + (count || 0) + ' 条')
      this.loadSummaries()
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
    async loadTargetOptions() {
      if (!this.configForm) return
      this.targetLoading = true
      try {
        const baseParams = { pageNum: 1, pageSize: 500, status: 1 }
        if (this.configForm.scope === 'PROJECT' && this.configForm.projectId) {
          baseParams.projectId = this.configForm.projectId
        }
        let res
        if (this.configForm.billingTarget === 'ENGINE') {
          res = await listDefinitions(baseParams)
        } else if (this.configForm.billingTarget === 'API') {
          res = await listApiConfigs({ pageNum: 1, pageSize: 500, status: 1 })
        } else if (this.configForm.billingTarget === 'DB') {
          res = await listDbDatasources(baseParams)
        } else {
          res = { data: { records: [] } }
        }
        this.targetOptions = (res.data && res.data.records) || []
      } catch (e) {
        this.targetOptions = []
      } finally {
        this.targetLoading = false
      }
    },
    onRecordDateChange(val) {
      this.recordQuery.beginTime = val ? val[0] : ''
      this.recordQuery.endTime = val ? val[1] : ''
    },
    onSummaryDateChange(val) {
      this.summaryQuery.beginDate = val ? val[0] : ''
      this.summaryQuery.endDate = val ? val[1] : ''
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
    color: #475569;
    line-height: 1.5;
  }
}
</style>
