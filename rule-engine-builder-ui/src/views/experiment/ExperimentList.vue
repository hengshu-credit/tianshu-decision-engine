<template>
  <div class="uiue-list-page experiment-page uiue-compact-workbench">
    <div class="page-head">
      <div>
        <h2>分流实验</h2>
        <div class="page-subtitle">
          配置冠军挑战和测试组空跑，执行结果会返回实验标签并写入实验明细日志。
        </div>
        <div class="page-tip">
          冠军组和挑战组参与生产分流；测试组在生产组执行后空跑，可按条件命中、互斥执行，并可控制是否调用
          API 外数。
        </div>
      </div>
    </div>

    <div class="uiue-search-container uiue-filter-toolbar">
      <el-form :inline="true" size="small" @keyup.enter="handleQuery">
        <el-form-item label="项目编码">
          <project-filter-select
            v-model:value="query.projectCode"
            field="projectCode"
            placeholder="输入项目编码"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="项目名称">
          <project-filter-select
            v-model:value="query.projectName"
            field="projectName"
            placeholder="输入项目名称"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            clearable
            placeholder="全部"
            style="width: 110px"
          >
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="编码或名称"
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <div class="uiue-btn-bar">
        <div class="btn-right">
          <el-button
            v-permission="'experiment:edit'"
            size="small"
            type="primary"
            :icon="ElIconPlus"
            @click="handleCreate"
            >新建实验</el-button
          >
        </div>
      </div>
    </div>

    <el-table show-overflow-tooltip :data="experiments" border size="small" v-loading="loading">
      <el-table-column
        prop="experimentCode"
        label="实验编码"
        min-width="150"
        show-overflow-tooltip
      />
      <el-table-column
        prop="experimentName"
        label="实验名称"
        min-width="160"
        show-overflow-tooltip
      />
      <el-table-column
        prop="projectCode"
        label="项目编码"
        min-width="120"
        show-overflow-tooltip
      />
      <el-table-column label="冠军挑战" width="110" align="center">
        <template v-slot="{ row }">{{
          routeModeLabel(row.routingMode)
        }}</template>
      </el-table-column>
      <el-table-column label="测试分流" width="110" align="center">
        <template v-slot="{ row }">{{
          routeModeLabel(row.testRoutingMode || 'CONDITION')
        }}</template>
      </el-table-column>
      <el-table-column label="生产组" min-width="190">
        <template v-slot="{ row }">
          <el-tag
            v-for="g in productionGroups(row)"
            :key="g.groupCode"
            size="small"
            :type="g.groupType === 'CHAMPION' ? 'success' : 'warning'"
            class="group-tag"
          >
            {{ g.groupName || g.groupCode
            }}<span v-if="row.routingMode === 'RATIO'">
              {{ g.trafficRatio || 0 }}%</span
            >
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="测试组" min-width="160">
        <template v-slot="{ row }">
          <el-tag
            v-for="g in testGroups(row)"
            :key="g.groupCode"
            size="small"
            type="info"
            class="group-tag"
          >
            {{ g.groupName || g.groupCode
            }}<span v-if="(row.testRoutingMode || 'CONDITION') === 'RATIO'">
              {{ g.trafficRatio || 0 }}%</span
            >
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template v-slot="{ row }">
          <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{
            row.status === 1 ? '启用' : '停用'
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" width="190" align="center" fixed="right">
        <template v-slot="{ row }">
          <el-button
            v-permission="'experiment:edit'"
            link
            data-action="execute"
            size="small"
            type="success"
            @click="handleTest(row)"
            >验证执行</el-button
          >
          <el-button
            v-permission="'experiment:edit'"
            link
            data-action="detail"
            size="small"
            type="primary"
            @click="handleEdit(row)"
            >详情</el-button
          >
          <el-button
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
    <el-pagination
      :current-page="query.pageNum"
      :page-size="query.pageSize"
      :total="total"
      layout="total,sizes,prev,pager,next"
      :page-sizes="[10, 30, 50, 100]"
      @current-change="
        (p) => {
          query.pageNum = p
          loadExperiments()
        }
      "
      @size-change="
        (s) => {
          query.pageSize = s
          query.pageNum = 1
          loadExperiments()
        }
      "
    />

    <el-dialog
      :title="form.id ? '编辑分流实验' : '新建分流实验'"
      v-model="formVisible"
      width="1040px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form
        ref="form"
        :model="form"
        :rules="rules"
        label-width="110px"
        size="small"
      >
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="项目" prop="projectId">
              <el-select
                v-model="form.projectId"
                filterable
                placeholder="选择项目"
                style="width: 100%"
                @change="onProjectChange"
              >
                <el-option
                  v-for="p in projects"
                  :key="p.id"
                  :label="p.projectName"
                  :value="p.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="实验编码" prop="experimentCode">
              <el-input
                v-model="form.experimentCode"
                placeholder="EXP_RISK_CHAMPION"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="实验名称" prop="experimentName">
              <el-input
                v-model="form.experimentName"
                placeholder="冠军挑战实验"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="10">
            <el-form-item label="冠军挑战">
              <el-radio-group
                v-model="form.routingMode"
                @change="onRoutingModeChange"
              >
                <el-radio-button value="RATIO">比例分流</el-radio-button>
                <el-radio-button value="CONDITION">条件分流</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="测试组">
              <el-radio-group
                v-model="form.testRoutingMode"
                @change="onTestRoutingModeChange"
              >
                <el-radio-button value="RATIO">比例分流</el-radio-button>
                <el-radio-button value="CONDITION">条件分流</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item
              v-if="form.testRoutingMode === 'CONDITION'"
              label="测试互斥"
            >
              <el-switch
                v-model="form.testExclusive"
                :active-value="1"
                :inactive-value="0"
                active-text="互斥"
                inactive-text="非互斥"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="section-title">冠军挑战配置</div>
        <el-alert
          v-if="ratioTotal !== 100 && form.routingMode === 'RATIO'"
          type="warning"
          :closable="false"
          show-icon
          :title="'当前生产组比例合计 ' + ratioTotal + '%，必须等于 100%'"
        />
        <el-table
          :data="productionFormGroups"
          border
          size="small"
          class="group-table"
        >
          <el-table-column
            v-if="form.routingMode === 'CONDITION'"
            label="条件"
            min-width="360"
          >
            <template v-slot="{ row }">
              <div v-if="isFallbackGroup(row)" class="fallback-cell">
                兜底动作
              </div>
              <condition-group-editor
                v-else-if="row.conditionConfig"
                :group="row.conditionConfig"
                :vars="varPickerOptions"
                :functions="projectFunctions"
                :list-options="projectLists"
                :get-var-options-fn="getVarOptions"
                :selected-vars="selectedVarPickerOptions"
              />
              <monaco-editor
                v-else
                v-model:value="row.conditionExpression"
                language="ql"
                height="70px"
              />
            </template>
          </el-table-column>
          <el-table-column label="类型" width="120">
            <template v-slot="{ row }">
              <el-select v-model="row.groupType" style="width: 100%">
                <el-option label="冠军组" value="CHAMPION" />
                <el-option label="挑战组" value="CHALLENGER" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="组编码" width="150"
            ><template v-slot="{ row }"
              ><el-input v-model="row.groupCode" /></template
          ></el-table-column>
          <el-table-column label="组名称" width="150"
            ><template v-slot="{ row }"
              ><el-input v-model="row.groupName" /></template
          ></el-table-column>
          <el-table-column label="执行规则" min-width="220">
            <template v-slot="{ row }">
              <rule-execution-selector
                :rule-id="row.ruleId"
                :rule-code="row.ruleCode"
                :rules="rulesForProject"
                @select="(rule) => onGroupRuleSelect(row, rule)"
              />
            </template>
          </el-table-column>
          <el-table-column
            v-if="form.routingMode === 'RATIO'"
            label="比例%"
            width="120"
            ><template v-slot="{ row }"
              ><el-input-number
                v-model="row.trafficRatio"
                :min="0"
                :max="100"
                :precision="2"
                style="width: 100%" /></template
          ></el-table-column>
          <el-table-column fixed="right" class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" width="80" align="center">
            <template v-slot="{ row }">
              <el-button
                link
                data-action="delete"
                size="small"
                type="danger"
                @click="removeGroup(row)"
                >删除</el-button
              >
            </template>
          </el-table-column>
        </el-table>
        <div class="table-actions">
          <el-button size="small" :icon="ElIconPlus" @click="addChallenger"
            >添加生产组</el-button
          >
          <el-button
            v-if="form.routingMode === 'CONDITION'"
            size="small"
            @click="addProductionFallback"
            >添加兜底动作</el-button
          >
        </div>

        <div class="section-title">测试组配置</div>
        <el-alert
          v-if="
            testRatioTotal !== 100 &&
            form.testRoutingMode === 'RATIO' &&
            testFormGroups.length > 0
          "
          type="warning"
          :closable="false"
          show-icon
          :title="'当前测试组比例合计 ' + testRatioTotal + '%，必须等于 100%'"
        />
        <el-table :data="testFormGroups" border size="small" class="group-table">
          <el-table-column
            v-if="form.testRoutingMode === 'CONDITION'"
            label="条件"
            min-width="360"
          >
            <template v-slot="{ row }">
              <div v-if="isFallbackGroup(row)" class="fallback-cell">
                兜底动作
              </div>
              <condition-group-editor
                v-else-if="row.conditionConfig"
                :group="row.conditionConfig"
                :vars="varPickerOptions"
                :functions="projectFunctions"
                :list-options="projectLists"
                :get-var-options-fn="getVarOptions"
                :selected-vars="selectedVarPickerOptions"
              />
              <monaco-editor
                v-else
                v-model:value="row.conditionExpression"
                language="ql"
                height="70px"
              />
            </template>
          </el-table-column>
          <el-table-column label="组编码" width="150"
            ><template v-slot="{ row }"
              ><el-input v-model="row.groupCode" /></template
          ></el-table-column>
          <el-table-column label="组名称" width="150"
            ><template v-slot="{ row }"
              ><el-input v-model="row.groupName" /></template
          ></el-table-column>
          <el-table-column label="执行规则" min-width="220">
            <template v-slot="{ row }">
              <rule-execution-selector
                :rule-id="row.ruleId"
                :rule-code="row.ruleCode"
                :rules="rulesForProject"
                @select="(rule) => onGroupRuleSelect(row, rule)"
              />
            </template>
          </el-table-column>
          <el-table-column
            v-if="form.testRoutingMode === 'RATIO'"
            label="比例%"
            width="120"
            ><template v-slot="{ row }"
              ><el-input-number
                v-model="row.trafficRatio"
                :min="0"
                :max="100"
                :precision="2"
                style="width: 100%" /></template
          ></el-table-column>
          <el-table-column label="调用API外数" width="120" align="center"
            ><template v-slot="{ row }"
              ><el-switch
                v-model="row.invokeExternalSource"
                :active-value="1"
                :inactive-value="0" /></template
          ></el-table-column>
          <el-table-column fixed="right" class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" width="80" align="center"
            ><template v-slot="{ row }"
              ><el-button
                link
                data-action="delete"
                size="small"
                type="danger"
                @click="removeGroup(row)"
                >删除</el-button
              ></template
            ></el-table-column
          >
        </el-table>
        <div class="table-actions">
          <el-button size="small" :icon="ElIconPlus" @click="addTestGroup"
            >添加测试组</el-button
          >
          <el-button
            v-if="form.testRoutingMode === 'CONDITION'"
            size="small"
            @click="addTestFallback"
            >添加兜底动作</el-button
          >
        </div>
      </el-form>
      <template v-slot:footer>
        <div>
          <el-button size="small" @click="formVisible = false">取消</el-button>
          <el-button v-permission="'experiment:edit'" size="small" type="primary" @click="handleSave"
            >保存</el-button
          >
        </div>
      </template>
    </el-dialog>

    <el-dialog
      class="experiment-execution-dialog"
      title="验证执行分流实验"
      v-model="testVisible"
      width="900px"
      append-to-body
      @closed="closeTestDialog"
    >
      <div class="execution-identity">
        <div>
          <span class="execution-kicker">实验验证</span>
          <strong>{{ testExperiment && testExperiment.experimentName }}</strong>
          <span>{{ testExperiment && testExperiment.experimentCode }}</span>
        </div>
        <span>本次仅验证分流结果，不会改变线上流量配置。</span>
      </div>

      <div v-if="testLoadStatus === 'LOADING'" class="execution-load-state">
        <strong>正在准备验证执行</strong>
        <span>正在加载实验输入字段和推荐样例，请稍候。</span>
      </div>

      <div
        v-else-if="testLoadStatus === 'ERROR'"
        class="execution-load-state is-error"
      >
        <strong>实验验证初始化失败</strong>
        <span>{{ testLoadError }}</span>
        <el-button size="small" type="primary" @click="handleTest(testExperiment)">重新加载</el-button>
      </div>

      <template v-else-if="testReady">
        <section
          class="execution-readiness"
          :class="{ 'is-degraded': testLoadStatus === 'DEGRADED' }"
        >
          <div>
            <strong>执行准备状态</strong>
            <span v-if="testLoadStatus === 'DEGRADED'"
              >Schema 未完整加载，已降级为 JSON 高级模式。</span
            >
            <span v-else
              >已加载 {{ testFields.length }} 个输入字段，参数来源：{{
                testParamSource === 'SCHEMA_SAMPLE' ? 'Schema 样例' : '字段默认值'
              }}。</span
            >
          </div>
          <el-button
            v-if="testLoadStatus === 'DEGRADED'"
            link
            size="small"
            type="primary"
            @click="handleTest(testExperiment)"
            >重试加载</el-button
          >
        </section>
        <ul v-if="testLoadWarnings.length" class="execution-warnings">
          <li v-for="warning in testLoadWarnings" :key="warning">{{ warning }}</li>
        </ul>

        <section class="execution-section">
          <div class="execution-section-head">
            <div>
              <strong>分流上下文</strong>
              <span>用于稳定命中比例分流，并校验测试组名单的进件时点。</span>
            </div>
          </div>
          <el-form size="small" label-width="88px" class="execution-context-form">
            <el-form-item label="请求唯一键">
              <el-input
                v-model="testRequest.requestKey"
                placeholder="可选；为空时使用入参 requestId/orderNo"
              />
            </el-form-item>
            <el-form-item label="进件时间">
              <el-date-picker
                v-model="testRequest.requestTime"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm:ss"
                placeholder="用于测试组名单时点"
                style="width: 100%"
              />
            </el-form-item>
          </el-form>
        </section>

        <section class="execution-section">
          <div class="execution-section-head execution-params-head">
            <div>
              <strong>{{ testMode === 'manual' ? '字段化入参' : 'JSON 高级模式' }}</strong>
              <span>字段表单适合常规验证；复杂数组、对象可使用 JSON 文本编辑。</span>
            </div>
            <div class="execution-mode-actions">
              <el-button
                size="small"
                :type="testMode === 'manual' ? 'primary' : ''"
                :disabled="testFields.length === 0"
                @click="switchToManualMode"
                >字段化入参</el-button
              >
              <el-button
                size="small"
                :type="testMode === 'json' ? 'primary' : ''"
                @click="switchToJsonMode"
                >JSON 高级模式</el-button
              >
            </div>
          </div>

          <div v-if="testMode === 'manual'" class="execution-param-panel">
            <div v-if="testFields.length" class="execution-field-grid">
              <div
                v-for="field in testFields"
                :key="field.fieldName"
                class="execution-field-cell"
              >
                <div class="execution-field-label">{{ field.fieldLabel || field.fieldName }}</div>
                <el-select
                  v-if="field.validValues && field.validValues.length"
                  v-model="testParams[field.fieldName]"
                  clearable
                  filterable
                  style="width: 100%"
                >
                  <el-option
                    v-for="value in field.validValues"
                    :key="value"
                    :label="value"
                    :value="testFieldOptionValue(field, value)"
                  />
                </el-select>
                <el-input-number
                  v-else-if="isNumberTestField(field)"
                  v-model="testParams[field.fieldName]"
                  controls-position="right"
                  :precision="isIntegerTestField(field) ? 0 : undefined"
                  :step="isIntegerTestField(field) ? 1 : 0.01"
                  style="width: 100%"
                />
                <el-select
                  v-else-if="isBooleanTestField(field)"
                  v-model="testParams[field.fieldName]"
                  style="width: 100%"
                >
                  <el-option label="true" :value="true" />
                  <el-option label="false" :value="false" />
                </el-select>
                <el-date-picker
                  v-else-if="isDateTestField(field)"
                  v-model="testParams[field.fieldName]"
                  type="date"
                  value-format="YYYY-MM-DD"
                  style="width: 100%"
                />
                <el-input
                  v-else-if="isComplexTestField(field)"
                  :model-value="formatComplexTestField(field)"
                  type="textarea"
                  :rows="3"
                  placeholder="请输入合法 JSON"
                  @update:model-value="updateComplexTestField(field, $event)"
                />
                <el-input v-else v-model="testParams[field.fieldName]" placeholder="输入值" />
                <div
                  v-if="complexTestFieldErrors[field.fieldName]"
                  class="execution-json-error"
                >
                  {{ complexTestFieldErrors[field.fieldName] }}
                </div>
                <div class="execution-field-hint">{{ field.fieldName }}</div>
              </div>
            </div>
            <div v-else class="execution-empty-fields">
              暂无可字段化的输入项，请使用 JSON 高级模式录入参数。
            </div>
          </div>

          <div v-else class="execution-param-panel execution-json-panel">
            <monaco-editor
              v-model:value="testJson"
              language="json"
              height="260px"
              @change="onJsonInput"
            />
          </div>
          <div v-if="jsonError" class="execution-json-error">{{ jsonError }}</div>
        </section>
      </template>

      <div v-if="testing" class="execution-running-state">
        <strong>正在验证执行</strong>
        <span>正在依次计算生产组与测试组结果，请等待本次请求完成。</span>
      </div>

      <section v-if="testResult" class="execution-result">
        <div class="execution-section-head">
          <div>
            <strong>验证执行结果</strong>
            <span>生产组为主路径；测试组按配置进行空跑，不影响线上流量。</span>
          </div>
        </div>
        <el-alert
          :title="testResult.success === false ? '验证执行失败' : '验证执行完成'"
          :type="testResult.success === false ? 'error' : 'success'"
          :closable="false"
          show-icon
        >
          <span v-if="testResult.errorMessage">{{ testResult.errorMessage }}</span>
          <span v-else-if="testResult.executeTimeMs">耗时 {{ testResult.executeTimeMs }} ms</span>
        </el-alert>
        <div class="execution-trace">
          <span v-if="testResult.experimentTraceId">追踪 ID：{{ testResult.experimentTraceId }}</span>
          <span v-if="testResult.requestKey">请求唯一键：{{ testResult.requestKey }}</span>
          <span v-if="testResult.executeTimeMs">耗时：{{ testResult.executeTimeMs }} ms</span>
          <span v-if="testResult.tags && testResult.tags.length">实验标签：{{ testResult.tags.join('、') }}</span>
        </div>
        <div v-if="testResult.productionGroup" class="execution-production-card">
          <div>
            <span>生产组</span>
            <strong>{{ testResult.productionGroup.groupName || testResult.productionGroup.groupCode }}</strong>
          </div>
          <el-tag
            size="small"
            :type="testResult.productionGroup.success === false ? 'danger' : testResult.productionGroup.skipped ? 'info' : 'success'"
          >{{ testResult.productionGroup.success === false ? '执行失败' : testResult.productionGroup.skipped ? '未执行' : '执行成功' }}</el-tag>
          <p v-if="testResult.productionGroup.errorMessage">{{ testResult.productionGroup.errorMessage }}</p>
        </div>
        <div v-if="testResult.testGroups && testResult.testGroups.length" class="execution-test-groups">
          <div class="execution-group-title">测试组空跑结果</div>
          <div
            v-for="group in buildExecutionResultSummary(testResult).groups.filter((item) => item.stage === 'TEST')"
            :key="group.groupCode"
            class="execution-test-group"
          >
            <strong>{{ group.groupCode }}</strong>
            <el-tag size="small" :type="group.status === 'FAILED' ? 'danger' : group.status === 'SKIPPED' || group.status === 'NOT_MATCHED' ? 'info' : 'success'">{{ group.status === 'MATCHED' ? '命中并执行' : group.status === 'NOT_MATCHED' ? '未命中' : group.status === 'SKIPPED' ? '未执行' : group.status === 'FAILED' ? '执行失败' : '执行成功' }}</el-tag>
            <span v-if="group.errorMessage">{{ group.errorMessage }}</span>
          </div>
        </div>
        <el-collapse class="execution-raw-result">
          <el-collapse-item title="原始执行结果" name="raw-result">
            <pre>{{ formatJson(testResult) }}</pre>
          </el-collapse-item>
        </el-collapse>
      </section>
      <template v-slot:footer>
        <div class="execution-dialog-footer">
          <el-button size="small" @click="closeTestDialog">关闭</el-button>
          <el-button
            size="small"
            type="primary"
            :loading="testing"
            :disabled="testLoadStatus === 'ERROR' || !testReady || testing"
            @click="doExecute"
            >验证执行</el-button
          >
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import { Plus as ElIconPlus } from '@element-plus/icons-vue'
import { listProjects } from '@/api/project'
import { getRuleTestSchema, listProjectDefinitions } from '@/api/definition'
import {
  deleteExperiment,
  executeExperiment,
  listExperiments,
  saveExperiment,
} from '@/api/experiment'
import varPickerMixin from '@/mixins/varPickerMixin'
import ConditionGroupEditor from '@/components/decision/ConditionGroupEditor.vue'
import RuleExecutionSelector from '@/components/common/RuleExecutionSelector.vue'
import MonacoEditor from '@/components/MonacoEditor'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import {
  createEmptyGroup,
  createEmptyLeaf,
  walkConditionLeaves,
  hasUsableConditionLeaf,
  compileConditionTreeExpression,
  normalizeConditionTreeOperands,
} from '@/utils/decisionConditionTree'
import { collectOperandReferences, syncOperandReference } from '@/utils/operand'
import { normalizeRuleOptions } from '@/utils/ruleCallConfig'
import {
  buildNestedSchemaParams,
  flattenSchemaSample,
  normalizeTestSchema,
  readParamPath,
  schemaFieldsToTestFields,
} from '@/utils/testSchema'
import { routeProjectId } from '@/utils/projectContext'

export default {
  data() {
    return {
      loading: false,
      experiments: [],
      total: 0,
      projects: [],
      contextProjectId: null,
      rulesForProject: [],
      contentLoaded: true,
      query: {
        pageNum: 1,
        pageSize: 10,
        projectId: '',
        projectCode: '',
        projectName: '',
        status: '',
        keyword: '',
      },
      formVisible: false,
      form: this.emptyForm(),
      rules: {
        projectId: [
          { required: true, message: '请选择项目', trigger: 'change' },
        ],
        experimentCode: [
          { required: true, message: '请输入实验编码', trigger: 'blur' },
        ],
        experimentName: [
          { required: true, message: '请输入实验名称', trigger: 'blur' },
        ],
      },
      testVisible: false,
      testing: false,
      testExperiment: null,
      testReady: false,
      testLoadStatus: 'IDLE',
      testLoadError: '',
      testLoadWarnings: [],
      testParamSource: 'DEFAULTS',
      testSetupRequestId: 0,
      testExecutionRequestId: 0,
      testMode: 'manual',
      testFields: [],
      testParams: {},
      complexTestFieldDrafts: {},
      complexTestFieldErrors: {},
      testJson: '{}',
      jsonError: '',
      testRequest: { requestKey: '', requestTime: '' },
      testResult: null,
      ElIconPlus: markRaw(ElIconPlus),
    }
  },
  name: 'ExperimentList',
  components: {
    ConditionGroupEditor,
    RuleExecutionSelector,
    MonacoEditor,
    ProjectFilterSelect,
  },
  mixins: [varPickerMixin],
  computed: {
    productionFormGroups() {
      return this.form.groups.filter(
        (g) => g.groupType === 'CHAMPION' || g.groupType === 'CHALLENGER'
      )
    },
    testFormGroups() {
      return this.form.groups.filter((g) => g.groupType === 'TEST')
    },
    ratioTotal() {
      return Number(
        this.productionFormGroups
          .reduce((sum, g) => sum + Number(g.trafficRatio || 0), 0)
          .toFixed(2)
      )
    },
    testRatioTotal() {
      return Number(
        this.testFormGroups
          .reduce((sum, g) => sum + Number(g.trafficRatio || 0), 0)
          .toFixed(2)
      )
    },
  },
  created() {
    this.contextProjectId = routeProjectId(
      this.$route,
      this.$store && this.$store.state.currentProject
    )
    if (this.contextProjectId) this.query.projectId = this.contextProjectId
    this.loadProjects()
    this.loadExperiments()
  },
  methods: {
    emptyForm() {
      return {
        id: null,
        projectId: null,
        projectCode: '',
        experimentCode: '',
        experimentName: '',
        routingMode: 'RATIO',
        testRoutingMode: 'CONDITION',
        conditionRuleCode: '',
        requestKeyPath: '',
        testExclusive: 1,
        status: 1,
        groups: [this.newGroup('CHAMPION', 'champion', '冠军组', 100)],
      }
    },
    newGroup(type, code, name, ratio) {
      return {
        groupType: type,
        groupCode: code,
        groupName: name,
        ruleId: null,
        ruleCode: '',
        trafficRatio: ratio || 0,
        conditionValue: code,
        conditionExpression: '',
        conditionConfig: this.createConditionRoot(),
        invokeExternalSource: 1,
        status: 1,
        sortOrder: 0,
      }
    },
    createConditionRoot() {
      const root = createEmptyGroup('AND')
      root.children.push(createEmptyLeaf())
      return root
    },
    createFallbackConfig() {
      return { fallback: true }
    },
    normalizeGroupForEdit(group) {
      const copy = { ...group }
      if (copy.ruleId === undefined) copy.ruleId = null
      copy.conditionConfig = this.parseConditionConfig(copy.conditionConfig)
      if (!copy.conditionConfig && !copy.conditionExpression) {
        copy.conditionConfig = this.createConditionRoot()
      }
      if (
        copy.invokeExternalSource === null ||
        copy.invokeExternalSource === undefined
      ) {
        copy.invokeExternalSource = 1
      }
      if (copy.trafficRatio === null || copy.trafficRatio === undefined) {
        copy.trafficRatio = 0
      }
      return copy
    },
    parseConditionConfig(config) {
      if (!config) return null
      if (typeof config === 'object')
        return normalizeConditionTreeOperands(
          JSON.parse(JSON.stringify(config))
        )
      try {
        return normalizeConditionTreeOperands(JSON.parse(config))
      } catch (e) {
        return null
      }
    },
    async loadProjects() {
      const res = await listProjects({ pageNum: 1, pageSize: 500, status: 1 })
      this.projects = (res.data && res.data.records) || []
    },
    async loadRules(projectId) {
      if (!projectId) {
        this.rulesForProject = []
        return
      }
      const res = await listProjectDefinitions(projectId, {
        pageNum: 1,
        pageSize: 1000,
        status: 1,
      })
      const page = res && res.data ? res.data : res
      const rows = Array.isArray(page) ? page : (page && page.records) || []
      this.rulesForProject = normalizeRuleOptions(rows)
      this.repairLegacyGroupRuleRefs()
    },
    async loadExperiments() {
      this.loading = true
      try {
        const res = await listExperiments(this.cleanParams({ ...this.query }))
        this.experiments = (res.data && res.data.records) || []
        this.total = (res.data && res.data.total) || 0
      } finally {
        this.loading = false
      }
    },
    handleQuery() {
      this.query.pageNum = 1
      this.loadExperiments()
    },
    resetQuery() {
      this.query = {
        pageNum: 1,
        pageSize: this.query.pageSize,
        projectId: this.contextProjectId || '',
        projectCode: '',
        projectName: '',
        status: '',
        keyword: '',
      }
      this.loadExperiments()
    },
    async handleCreate() {
      this.$router.push({
        path: '/experiment/new',
        query: this.contextProjectId
          ? { projectId: this.contextProjectId }
          : {},
      })
    },
    async handleEdit(row) {
      this.$router.push('/experiment/detail/' + row.id)
    },
    async handleDelete(row) {
      await this.$confirm('确定删除实验 ' + row.experimentName + '？', '确认', {
        type: 'warning',
      })
      const response = await deleteExperiment(row.id)
      this.$message.success('分流实验删除已送审')
      if (response.data && response.data.id)
        this.$router.push('/approval/' + response.data.id)
    },
    onProjectChange(projectId) {
      const project = this.projects.find((p) => p.id === projectId)
      this.form.projectCode = project ? project.projectCode : ''
      this.form.groups.forEach((g) => {
        g.ruleId = null
        g.ruleCode = ''
      })
      this.loadRules(projectId)
      this.loadExperimentRefs(projectId)
    },
    onGroupRuleSelect(group, rule) {
      group['ruleId'] = rule ? rule.id : null
      group['ruleCode'] = rule ? rule.ruleCode : ''
    },
    repairLegacyGroupRuleRefs() {
      (this.form.groups || []).forEach((group) => {
        let rule = null
        if (group.ruleId != null) {
          rule = this.rulesForProject.find(
            (item) => String(item.id) === String(group.ruleId)
          )
        } else if (group.ruleCode) {
          const matches = this.rulesForProject.filter(
            (item) => String(item.ruleCode) === String(group.ruleCode)
          )
          if (matches.length === 1) rule = matches[0]
        }
        if (rule) this.onGroupRuleSelect(group, rule)
      })
    },
    async loadExperimentRefs(projectId) {
      if (!projectId) {
        this.projectIdForRefs = null
        this.projectRefs = []
        this.projectVars = []
        return
      }
      this.projectIdForRefs = projectId
      await this.refreshProjectRefs()
    },
    onRoutingModeChange(mode) {
      if (mode === 'RATIO' && this.ratioTotal === 0) {
        this.form.groups.forEach((g) => {
          if (g.groupType === 'CHAMPION') g.trafficRatio = 100
        })
      } else if (mode === 'CONDITION') {
        this.ensureFallbackGroup('PRODUCTION')
      }
    },
    onTestRoutingModeChange(mode) {
      if (
        mode === 'RATIO' &&
        this.testRatioTotal === 0 &&
        this.testFormGroups.length === 1
      ) {
        this.testFormGroups[0].trafficRatio = 100
      } else if (mode === 'CONDITION' && this.testFormGroups.length > 0) {
        this.ensureFallbackGroup('TEST')
      }
    },
    addChallenger() {
      const index = this.productionFormGroups.length
      this.insertBeforeFallback(
        this.newGroup('CHALLENGER', 'challenger_' + index, '挑战组' + index, 0),
        'PRODUCTION'
      )
    },
    addTestGroup() {
      const index = this.testFormGroups.length + 1
      this.insertBeforeFallback(
        this.newGroup('TEST', 'test_' + index, '测试组' + index, 0),
        'TEST'
      )
      if (this.form.testRoutingMode === 'CONDITION')
        this.ensureFallbackGroup('TEST')
    },
    addProductionFallback() {
      if (this.productionFormGroups.some((g) => this.isFallbackGroup(g))) return
      const index = this.productionFormGroups.length
      const group = this.newGroup(
        'CHALLENGER',
        'fallback_' + index,
        '兜底组',
        0
      )
      group.conditionExpression = ''
      group.conditionConfig = this.createFallbackConfig()
      this.form.groups.push(group)
    },
    addTestFallback() {
      if (this.testFormGroups.some((g) => this.isFallbackGroup(g))) return
      const index = this.testFormGroups.length + 1
      const group = this.newGroup(
        'TEST',
        'test_fallback_' + index,
        '测试兜底组',
        0
      )
      group.conditionExpression = ''
      group.conditionConfig = this.createFallbackConfig()
      this.form.groups.push(group)
    },
    ensureFallbackGroup(scope) {
      const groups =
        scope === 'TEST' ? this.testFormGroups : this.productionFormGroups
      if (groups.some((g) => this.isFallbackGroup(g))) return
      if (scope === 'TEST') {
        this.addTestFallback()
      } else {
        this.addProductionFallback()
      }
    },
    insertBeforeFallback(group, scope) {
      const targetGroups =
        scope === 'TEST' ? this.testFormGroups : this.productionFormGroups
      const fallback = targetGroups.find((g) => this.isFallbackGroup(g))
      const index = fallback ? this.form.groups.indexOf(fallback) : -1
      if (index >= 0) {
        this.form.groups.splice(index, 0, group)
      } else {
        this.form.groups.push(group)
      }
    },
    removeGroup(row) {
      const index = this.form.groups.indexOf(row)
      if (index >= 0) this.form.groups.splice(index, 1)
    },
    handleSave() {
      return new Promise((resolve) => {
        this.$refs.form.validate(async (valid) => {
          if (!valid) return resolve(false)
          const error = this.validateGroups()
          if (error) {
            this.$message.error(error)
            return resolve(false)
          }
          const data = { ...this.form, groups: this.prepareGroupsForSave() }
          const response = await saveExperiment(data)
          this.$message.success('分流实验变更已送审')
          this.formVisible = false
          if (response.data && response.data.id)
            this.$router.push('/approval/' + response.data.id)
          resolve(true)
        })
      })
    },
    validateGroups() {
      const champions = this.productionFormGroups.filter(
        (g) => g.groupType === 'CHAMPION'
      )
      if (champions.length !== 1) return '必须且只能配置一组冠军组'
      if (this.form.routingMode === 'RATIO' && this.ratioTotal !== 100)
        return '冠军组和挑战组分流比例之和必须为100%'
      if (
        this.form.testRoutingMode === 'RATIO' &&
        this.testFormGroups.length > 0 &&
        this.testRatioTotal !== 100
      )
        return '测试组分流比例之和必须为100%'
      const missing = this.form.groups.find(
        (g) => !g.groupCode || g.ruleId == null
      )
      if (missing) return '每个实验组都必须配置组编码和执行规则'
      const duplicateCode = this.findDuplicateGroupCode()
      if (duplicateCode) return '实验组编码不能重复: ' + duplicateCode
      if (this.form.routingMode === 'CONDITION') {
        const error = this.validateConditionGroups(
          this.productionFormGroups,
          '冠军挑战'
        )
        if (error) return error
      }
      if (
        this.form.testRoutingMode === 'CONDITION' &&
        this.testFormGroups.length > 0
      ) {
        const error = this.validateConditionGroups(
          this.testFormGroups,
          '测试组'
        )
        if (error) return error
      }
      return ''
    },
    validateConditionGroups(groups, label) {
      if (!groups.some((g) => this.isFallbackGroup(g)))
        return label + '条件分流必须配置兜底动作'
      const invalid = groups.find(
        (g) => !this.isFallbackGroup(g) && !this.hasCondition(g)
      )
      if (invalid) return label + '条件分流的非兜底规则必须配置条件'
      return ''
    },
    findDuplicateGroupCode() {
      const seen = {}
      for (const group of this.form.groups) {
        const code = group.groupCode
        if (!code) continue
        if (seen[code]) return code
        seen[code] = true
      }
      return ''
    },
    hasCondition(group) {
      if (group.conditionConfig)
        return hasUsableConditionLeaf(group.conditionConfig)
      return !!group.conditionExpression
    },
    isFallbackGroup(group) {
      return !!(
        group &&
        group.conditionConfig &&
        group.conditionConfig.fallback === true
      )
    },
    prepareGroupsForSave() {
      return this.form.groups.map((g, i) => {
        const copy = { ...g, sortOrder: i }
        if (this.isFallbackGroup(copy)) {
          copy.conditionExpression = ''
          copy.conditionConfig = JSON.stringify(this.createFallbackConfig())
          return copy
        }
        if (copy.conditionConfig) {
          if (this.isConditionModeForGroup(copy)) {
            copy.conditionExpression = compileConditionTreeExpression(
              copy.conditionConfig
            )
          } else if (!hasUsableConditionLeaf(copy.conditionConfig)) {
            copy.conditionExpression = ''
          }
          copy.conditionConfig = JSON.stringify(copy.conditionConfig)
        }
        return copy
      })
    },
    isConditionModeForGroup(group) {
      if (group.groupType === 'TEST')
        return this.form.testRoutingMode === 'CONDITION'
      return this.form.routingMode === 'CONDITION'
    },
    collectSelectedVarItems() {
      const result = []
      ;(this.form.groups || []).forEach((group) => {
        if (!group.conditionConfig || this.isFallbackGroup(group)) return
        walkConditionLeaves(group.conditionConfig, (leaf) => {
          [leaf.leftOperand, leaf.rightOperand].forEach((operand) => {
            collectOperandReferences(operand).forEach((reference) =>
              result.push({
                varCode: reference.code,
                _varId: reference.refId,
                _refType: reference.refType,
                varType: reference.valueType,
              })
            )
          })
        })
      })
      return result
    },
    _syncModelVarRefs() {
      let changed = false
      const sync = (leaf, field) => {
        const result = syncOperandReference(leaf[field], this.varPickerOptions)
        if (!result.changed) return
        leaf[field] = result.operand
        changed = true
      }
      ;(this.form.groups || []).forEach((group) => {
        if (!group.conditionConfig || this.isFallbackGroup(group)) return
        walkConditionLeaves(group.conditionConfig, (leaf) => {
          sync(leaf, 'leftOperand')
          sync(leaf, 'rightOperand')
        })
      })
      if (changed) this.$forceUpdate()
    },
    async handleTest(row) {
      const setupRequestId = ++this.testSetupRequestId
      ++this.testExecutionRequestId
      this.testExperiment = row
      this.testResult = null
      this.testRequest = { requestKey: '', requestTime: '' }
      this.testVisible = true
      this.testing = false
      this.testReady = false
      this.testLoadStatus = 'LOADING'
      this.testLoadError = ''
      this.testLoadWarnings = []
      this.testParamSource = 'DEFAULTS'
      this.testMode = 'manual'
      this.testFields = []
      this.testParams = {}
      this.complexTestFieldDrafts = {}
      this.complexTestFieldErrors = {}
      this.testJson = '{}'
      this.jsonError = ''
      try {
        const schema = normalizeTestSchema(
          await getRuleTestSchema({
            targetType: 'EXPERIMENT',
            targetId: row.id,
          })
        )
        if (!this.isCurrentTestSetup(setupRequestId)) return
        const fields = schemaFieldsToTestFields(schema.inputs)
        const warnings = this.normalizeTestWarnings(schema.diagnostics)
        if (fields.length === 0) {
          this.testFields = []
          this.testParams = {}
          this.testJson = '{}'
          this.testLoadWarnings = [
            ...warnings,
            '测试 Schema 未提供可用输入字段，无法生成字段化入参；请使用 JSON 高级模式继续验证。',
          ]
          this.testParamSource = 'EMPTY'
          this.testMode = 'json'
          this.testLoadStatus = 'DEGRADED'
          this.testReady = true
          return
        }
        const params = flattenSchemaSample(fields, schema.sampleParams)
        this.testFields = fields
        this.testParams = params
        this.testJson = JSON.stringify(
          buildNestedSchemaParams(fields, params),
          null,
          2
        )
        this.testLoadWarnings = warnings
        this.testParamSource = Object.keys(schema.sampleParams).length
          ? 'SCHEMA_SAMPLE'
          : 'FIELD_DEFAULTS'
        this.testMode = 'manual'
        this.testLoadStatus = 'READY'
        this.testReady = true
      } catch (e) {
        if (!this.isCurrentTestSetup(setupRequestId)) return
        this.testFields = []
        this.testParams = {}
        this.complexTestFieldDrafts = {}
        this.complexTestFieldErrors = {}
        this.testJson = '{}'
        this.testMode = 'json'
        this.testParamSource = 'EMPTY'
        this.testLoadStatus = 'ERROR'
        this.testLoadError = this.testErrorMessage(
          e,
          '无法加载实验测试 Schema，请确认实验包含有效规则和可执行上下文。'
        )
        this.testLoadWarnings = []
        this.testReady = false
      }
    },
    isCurrentTestSetup(setupRequestId) {
      return this.testSetupRequestId === setupRequestId
    },
    isCurrentTestExecution(executionRequestId, setupRequestId) {
      return (
        this.isCurrentTestSetup(setupRequestId) &&
        this.testExecutionRequestId === executionRequestId
      )
    },
    normalizeTestWarnings(diagnostics) {
      return (diagnostics || []).map((diagnostic) =>
        typeof diagnostic === 'string'
          ? diagnostic
          : diagnostic.message || JSON.stringify(diagnostic)
      )
    },
    testErrorMessage(error, fallback) {
      return (
        (error && error.response && error.response.data && error.response.data.message) ||
        (error && error.message) ||
        fallback
      )
    },
    testFieldType(field) {
      return String((field && (field.fieldType || field.valueType)) || 'STRING').toUpperCase()
    },
    isNumberTestField(field) {
      return ['INTEGER', 'INT', 'LONG', 'NUMBER', 'DOUBLE', 'FLOAT', 'DECIMAL', 'PROBABILITY'].includes(this.testFieldType(field))
    },
    isIntegerTestField(field) {
      return ['INTEGER', 'INT', 'LONG'].includes(this.testFieldType(field))
    },
    isBooleanTestField(field) {
      return ['BOOLEAN', 'BOOL'].includes(this.testFieldType(field))
    },
    isDateTestField(field) {
      return ['DATE', 'DATETIME', 'TIMESTAMP'].includes(this.testFieldType(field))
    },
    isComplexTestField(field) {
      return ['ARRAY', 'LIST', 'VECTOR', 'OBJECT', 'MAP'].includes(this.testFieldType(field))
    },
    testFieldOptionValue(field, value) {
      if (this.isNumberTestField(field)) {
        const number = Number(value)
        return Number.isNaN(number) ? value : number
      }
      if (this.isBooleanTestField(field)) {
        if (typeof value === 'boolean') return value
        const normalized = String(value).trim().toLowerCase()
        if (normalized === 'true' || normalized === '1') return true
        if (normalized === 'false' || normalized === '0') return false
      }
      return value
    },
    formatComplexTestField(field) {
      if (
        Object.prototype.hasOwnProperty.call(
          this.complexTestFieldDrafts,
          field.fieldName
        )
      )
        return this.complexTestFieldDrafts[field.fieldName]
      const value = this.testParams[field.fieldName]
      if (typeof value === 'string') return value
      return JSON.stringify(value === undefined ? (this.testFieldType(field) === 'OBJECT' || this.testFieldType(field) === 'MAP' ? {} : []) : value, null, 2)
    },
    updateComplexTestField(field, value) {
      this.complexTestFieldDrafts[field.fieldName] = value
      try {
        const parsed = JSON.parse(value)
        const error = this.testFieldValidationError(field, parsed)
        if (error) throw new Error(error)
        this.testParams[field.fieldName] = parsed
        delete this.complexTestFieldErrors[field.fieldName]
      } catch (e) {
        this.complexTestFieldErrors[field.fieldName] =
          'JSON 格式错误: ' + this.testErrorMessage(e, '请输入合法 JSON')
      }
    },
    hasComplexTestFieldErrors() {
      return Object.keys(this.complexTestFieldErrors).length > 0
    },
    testFieldValidationError(field, value) {
      if (!this.isComplexTestField(field) || value === undefined) return ''
      const type = this.testFieldType(field)
      if (['ARRAY', 'LIST', 'VECTOR'].includes(type) && !Array.isArray(value))
        return '字段 ' + field.fieldName + ' 请输入 JSON 数组'
      if (
        ['OBJECT', 'MAP'].includes(type) &&
        (!value || Array.isArray(value) || typeof value !== 'object')
      )
        return '字段 ' + field.fieldName + ' 请输入 JSON 对象'
      return ''
    },
    validateExecutionParams(params) {
      const invalidField = this.testFields.find((field) => {
        const path = field.scriptName || field.fieldName
        return this.testFieldValidationError(field, readParamPath(params, path))
      })
      if (!invalidField) {
        this.jsonError = ''
        return true
      }
      const path = invalidField.scriptName || invalidField.fieldName
      this.jsonError = this.testFieldValidationError(
        invalidField,
        readParamPath(params, path)
      )
      return false
    },
    buildManualTestParams() {
      const fields = this.testFields.filter((field) => {
        const path = field.scriptName || field.fieldName
        return Object.prototype.hasOwnProperty.call(this.testParams, path)
      })
      return buildNestedSchemaParams(fields, this.testParams)
    },
    switchToJsonMode() {
      if (this.testMode === 'json') return
      if (this.hasComplexTestFieldErrors()) {
        this.$message.error('请先修正复杂字段中的 JSON 错误')
        return
      }
      this.testMode = 'json'
      this.syncParamsToJson()
    },
    switchToManualMode() {
      if (this.testMode === 'manual') return
      if (this.syncJsonToParams()) this.testMode = 'manual'
    },
    syncParamsToJson() {
      this.testJson = JSON.stringify(
        buildNestedSchemaParams(this.testFields, this.testParams),
        null,
        2
      )
      this.jsonError = ''
    },
    syncJsonToParams() {
      try {
        const params = JSON.parse(this.testJson || '{}')
        if (!this.validateExecutionParams(params)) return false
        const nextParams = {}
        this.testFields.forEach((field) => {
          const path = field.scriptName || field.fieldName
          const value = readParamPath(params, path)
          if (value !== undefined) nextParams[path] = value
        })
        this.testParams = nextParams
        this.complexTestFieldDrafts = {}
        this.complexTestFieldErrors = {}
        this.jsonError = ''
        return true
      } catch (e) {
        this.jsonError = 'JSON 格式错误: ' + this.testErrorMessage(e, '未知错误')
        return false
      }
    },
    onJsonInput() {
      try {
        JSON.parse(this.testJson || '{}')
        this.jsonError = ''
      } catch (e) {
        this.jsonError = 'JSON 格式错误: ' + this.testErrorMessage(e, '未知错误')
      }
    },
    closeTestDialog() {
      ++this.testSetupRequestId
      ++this.testExecutionRequestId
      this.testVisible = false
      this.testing = false
      this.testReady = false
      this.testLoadStatus = 'IDLE'
      this.testLoadError = ''
      this.testResult = null
      this.complexTestFieldDrafts = {}
      this.complexTestFieldErrors = {}
      this.jsonError = ''
    },
    async doExecute() {
      if (!this.testReady || this.testing) return
      if (this.hasComplexTestFieldErrors()) {
        this.$message.error('请先修正复杂字段中的 JSON 错误')
        return
      }
      let params
      if (this.testMode === 'manual' && this.testFields.length > 0) {
        if (this.jsonError) {
          this.$message.error(this.jsonError)
          return
        }
        params = this.buildManualTestParams()
      } else {
        try {
          params = JSON.parse(this.testJson || '{}')
          this.jsonError = ''
        } catch (e) {
          this.jsonError =
            'JSON 格式错误: ' + this.testErrorMessage(e, '未知错误')
          this.$message.error(this.jsonError)
          return
        }
      }
      if (!this.validateExecutionParams(params)) {
        this.$message.error(this.jsonError)
        return
      }
      const setupRequestId = this.testSetupRequestId
      const executionRequestId = ++this.testExecutionRequestId
      this.testing = true
      this.testResult = null
      try {
        const res = await executeExperiment(this.testExperiment.experimentCode, {
          params,
          requestKey: this.testRequest.requestKey || '',
          requestTime: this.testRequest.requestTime || null,
        })
        if (this.isCurrentTestExecution(executionRequestId, setupRequestId))
          this.testResult = res.data
      } catch (e) {
        if (this.isCurrentTestExecution(executionRequestId, setupRequestId)) {
          const errorMessage = this.testErrorMessage(e, '实验执行失败')
          this.testResult = { success: false, errorMessage }
          this.$message.error('实验执行失败: ' + errorMessage)
        }
      } finally {
        if (this.isCurrentTestExecution(executionRequestId, setupRequestId))
          this.testing = false
      }
    },
    buildExecutionResultSummary(result) {
      const groupSummary = []
      const appendGroup = (group, stage) => {
        if (!group) return
        let status = 'SUCCESS'
        if (group.skipped) status = 'SKIPPED'
        else if (group.success === false) status = 'FAILED'
        else if (stage === 'TEST' && group.matched) status = 'MATCHED'
        else if (stage === 'TEST') status = 'NOT_MATCHED'
        const item = { groupCode: group.groupCode, stage, status }
        if (group.errorMessage) item.errorMessage = group.errorMessage
        groupSummary.push(item)
      }
      appendGroup(result && result.productionGroup, 'PRODUCTION')
      ;((result && result.testGroups) || []).forEach((group) =>
        appendGroup(group, 'TEST')
      )
      const overall = {
        status: result && result.success === false ? 'FAILED' : 'SUCCESS',
      }
      if (overall.status === 'FAILED' && result && result.errorMessage)
        overall.errorMessage = result.errorMessage
      return { overall, groups: groupSummary }
    },
    productionGroups(row) {
      return (row.groups || []).filter(
        (g) => g.groupType === 'CHAMPION' || g.groupType === 'CHALLENGER'
      )
    },
    testGroups(row) {
      return (row.groups || []).filter((g) => g.groupType === 'TEST')
    },
    routeModeLabel(mode) {
      return mode === 'RATIO' ? '随机分流' : '条件分流'
    },
    cleanParams(params) {
      Object.keys(params).forEach((k) => {
        if (params[k] === '' || params[k] === null || params[k] === undefined)
          delete params[k]
      })
      return params
    },
    formatJson(value) {
      return JSON.stringify(value, null, 2)
    },
  },
}
</script>

<style lang="scss" scoped>
.experiment-page {
  .page-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 14px;
  }

  h2 {
    margin: 0;
    font-size: 20px;
    font-weight: 700;
    color: var(--tianshu-text-primary);
  }

  .page-subtitle {
    margin-top: 4px;
    color: var(--tianshu-text-tertiary);
    font-size: 13px;
  }

  .page-tip {
    margin-top: 4px;
    color: var(--tianshu-text-tertiary);
    font-size: 12px;
  }

  .group-tag {
    margin-right: 4px;
    margin-bottom: 4px;
  }

  .section-title {
    margin: 16px 0 8px;
    font-size: 14px;
    font-weight: 700;
    color: var(--tianshu-text-primary);
  }

  .group-table {
    margin-bottom: 8px;
  }

  .table-actions {
    display: flex;
    gap: 8px;
    margin-bottom: 8px;
  }

  .fallback-cell {
    min-height: 34px;
    display: flex;
    align-items: center;
    padding: 0 10px;
    color: var(--tianshu-text-secondary);
    background: var(--tianshu-bg-muted);
    border: 1px solid var(--tianshu-border-subtle);
    border-radius: 4px;
  }

  :deep(.group-table .cg) {
    padding: 8px;
    border: 1px solid var(--tianshu-border-subtle);
    border-radius: 4px;
    background: var(--tianshu-bg-surface);
  }

}
</style>

<style lang="scss">
.experiment-execution-dialog {
  max-width: calc(100vw - 24px);

  .execution-identity {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    padding: 12px 16px;
    margin-bottom: 16px;
    border: 1px solid var(--tianshu-info-border);
    border-radius: 8px;
    background: var(--tianshu-info-bg);

    > div {
      display: flex;
      align-items: baseline;
      flex-wrap: wrap;
      gap: 8px;
    }

    strong {
      color: var(--tianshu-text-primary);
      font-size: 15px;
    }

    > span,
    div > span:last-child {
      color: var(--tianshu-text-tertiary);
      font-size: 12px;
    }
  }

  .execution-kicker {
    color: var(--el-color-primary);
    font-size: 12px;
    font-weight: 600;
  }

  .execution-load-state {
    display: flex;
    min-height: 180px;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;
    color: var(--tianshu-text-secondary);
    text-align: center;

    strong {
      color: var(--tianshu-text-primary);
      font-size: 15px;
    }

    span {
      color: var(--tianshu-text-tertiary);
      font-size: 12px;
    }

    &.is-error {
      strong,
      span {
        color: var(--tianshu-danger-text);
      }
    }
  }

  .execution-readiness,
  .execution-section,
  .execution-result,
  .execution-running-state {
    margin-bottom: 16px;
    border: 1px solid var(--tianshu-border-subtle);
    border-radius: 8px;
  }

  .execution-readiness {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 12px 16px;
    border-color: var(--tianshu-success-border);
    background: var(--tianshu-success-bg);

    > div {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    strong {
      color: var(--tianshu-success-text);
      font-size: 13px;
    }

    span {
      color: var(--tianshu-text-tertiary);
      font-size: 12px;
      line-height: 1.5;
    }

    &.is-degraded {
      border-color: var(--tianshu-warning-border);
      background: var(--tianshu-warning-bg);

      strong {
        color: var(--tianshu-warning-text);
      }
    }
  }

  .execution-warnings {
    margin: -8px 0 16px;
    padding: 8px 16px 8px 34px;
    color: var(--tianshu-warning-text);
    font-size: 12px;
    line-height: 1.5;
  }

  .execution-section,
  .execution-result {
    padding: 16px;
  }

  .execution-section-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 12px;

    > div:first-child {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    strong,
    .execution-group-title {
      color: var(--tianshu-text-primary);
      font-size: 14px;
      font-weight: 700;
    }

    span {
      color: var(--tianshu-text-tertiary);
      font-size: 12px;
      line-height: 1.5;
    }
  }

  .execution-context-form {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0 16px;

    .el-form-item {
      margin-bottom: 0;
    }
  }

  .execution-mode-actions {
    display: flex;
    flex-shrink: 0;
    gap: 8px;
  }

  .execution-param-panel {
    max-height: 320px;
    overflow-y: auto;
    border: 1px solid var(--tianshu-border-subtle);
    border-radius: 8px;
    background: var(--tianshu-bg-surface);
  }

  .execution-field-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
    padding: 12px;
  }

  .execution-field-cell {
    min-width: 0;
    padding: 8px;
    border-radius: 4px;

    &:hover {
      background: var(--tianshu-bg-soft);
    }
  }

  .execution-field-label {
    margin-bottom: 4px;
    overflow: hidden;
    color: var(--tianshu-text-secondary);
    font-size: 13px;
    font-weight: 500;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .execution-field-hint {
    margin-top: 4px;
    overflow: hidden;
    color: var(--tianshu-text-disabled);
    font-family: Consolas, monospace;
    font-size: 11px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .execution-empty-fields {
    padding: 24px;
    color: var(--tianshu-text-tertiary);
    font-size: 13px;
    text-align: center;
  }

  .execution-json-panel {
    padding: 8px;
  }

  .execution-json-error {
    margin-top: 8px;
    color: var(--tianshu-danger-text);
    font-size: 12px;
  }

  .execution-running-state {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 12px 16px;
    border-color: var(--tianshu-info-border);
    background: var(--tianshu-info-bg);

    strong {
      color: var(--tianshu-info-text);
      font-size: 13px;
    }

    span {
      color: var(--tianshu-text-tertiary);
      font-size: 12px;
    }
  }

  .execution-result .el-alert {
    margin-bottom: 12px;
  }

  .execution-trace {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 16px;
    margin-bottom: 12px;
    color: var(--tianshu-text-tertiary);
    font-size: 12px;
  }

  .execution-production-card {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 8px 12px;
    padding: 12px;
    margin-bottom: 12px;
    border: 1px solid var(--tianshu-info-border);
    border-radius: 8px;
    background: var(--tianshu-info-bg);

    > div {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    span,
    p {
      color: var(--tianshu-text-tertiary);
      font-size: 12px;
    }

    strong {
      color: var(--tianshu-text-primary);
      font-size: 14px;
    }

    p {
      grid-column: 1 / -1;
      margin: 0;
      color: var(--tianshu-danger-text);
    }
  }

  .execution-test-groups {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-bottom: 12px;
  }

  .execution-test-group {
    display: grid;
    grid-template-columns: minmax(120px, 1fr) auto minmax(0, 2fr);
    align-items: center;
    gap: 12px;
    padding: 8px 12px;
    border-radius: 4px;
    background: var(--tianshu-bg-soft);

    strong {
      overflow: hidden;
      color: var(--tianshu-text-secondary);
      font-size: 13px;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    span {
      color: var(--tianshu-danger-text);
      font-size: 12px;
    }
  }

  .execution-raw-result {
    min-width: 0;
    max-width: 100%;
  }

  .execution-raw-result pre {
    box-sizing: border-box;
    max-width: 100%;
    max-height: 220px;
    overflow-x: auto;
    overflow-wrap: anywhere;
    white-space: pre-wrap;
  }

  .execution-dialog-footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }

  .el-dialog__body {
    max-height: calc(100vh - 150px);
    overflow-y: auto;
  }

  @media (max-width: 720px) {
    .execution-identity,
    .execution-readiness,
    .execution-section-head {
      flex-direction: column;
    }

    .execution-context-form,
    .execution-field-grid {
      grid-template-columns: minmax(0, 1fr);
    }

    .execution-test-group {
      grid-template-columns: minmax(0, 1fr) auto;

      span {
        grid-column: 1 / -1;
      }
    }
  }
}
</style>
