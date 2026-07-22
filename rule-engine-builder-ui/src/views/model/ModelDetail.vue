<template>
  <div class="uiue-list-page uiue-compact-workbench">
    <!-- 页面头部 -->
    <div
      style="
        margin-bottom: 16px;
        display: flex;
        align-items: center;
        justify-content: space-between;
      "
    >
      <h2 style="margin: 0">{{ model.modelName || '模型详情' }}</h2>
      <div>
        <el-button size="small" :icon="ElIconTime" @click="openVersionDialog"
          >版本历史</el-button
        >
        <el-button
          size="small"
          type="primary"
          :icon="ElIconVideoPlay"
          @click="openTestDialog"
          >模型测试</el-button
        >
        <el-button
          size="small"
          :icon="ElIconBack"
          @click="$router.push('/model')"
          >返回</el-button
        >
      </div>
    </div>

    <!-- 基本信息 -->
    <el-descriptions
      :column="2"
      border
      size="small"
      style="margin-bottom: 16px"
      v-loading="loading"
    >
      <el-descriptions-item label="模型编码">{{
        model.modelCode
      }}</el-descriptions-item>
      <el-descriptions-item label="模型名称">{{
        model.modelName
      }}</el-descriptions-item>
      <el-descriptions-item label="模型大类">{{
        modelTypeLabel(model.modelType)
      }}</el-descriptions-item>
      <el-descriptions-item label="模型格式">{{
        model.modelFormat
      }}</el-descriptions-item>
      <el-descriptions-item label="作用范围">{{
        model.scope === 'GLOBAL' ? '全局' : '项目级'
      }}</el-descriptions-item>
      <el-descriptions-item label="所属项目">{{
        model.projectName || '—'
      }}</el-descriptions-item>
      <el-descriptions-item label="文件名">{{
        model.modelFileName
      }}</el-descriptions-item>
      <el-descriptions-item label="文件大小">{{
        formatFileSize(model.modelFileSize)
      }}</el-descriptions-item>
      <el-descriptions-item label="设计版本">{{
        model.currentVersion
      }}</el-descriptions-item>
      <el-descriptions-item label="发布版本">{{
        model.publishedVersion || '-'
      }}</el-descriptions-item>
      <el-descriptions-item
        v-if="model.modelFormat === 'ONNX'"
        label="ONNX 推理任务"
        >{{ onnxTaskLabel }}</el-descriptions-item
      >
      <el-descriptions-item
        v-if="model.modelFormat === 'ONNX'"
        label="ONNX 原始节点"
        >{{ onnxNodeSummary }}</el-descriptions-item
      >
      <el-descriptions-item
        v-if="model.modelFormat === 'ONNX'"
        label="执行设备"
      >
        <el-tag
          :type="onnxExecutionProvider === 'CUDA' ? 'success' : 'info'"
          size="small"
          >{{ onnxExecutionProvider }}</el-tag
        >
      </el-descriptions-item>
      <el-descriptions-item
        v-if="model.modelFormat === 'ONNX' && onnxExecutionProvider === 'CUDA'"
        label="GPU 参数"
      >
        {{ onnxGpuSummary }}
      </el-descriptions-item>
      <el-descriptions-item
        v-if="model.modelFormat === 'ONNX'"
        label="启动预加载"
        >{{ model.preloadOnStartup === 1 ? '是' : '否' }}</el-descriptions-item
      >
      <el-descriptions-item label="执行超时"
        >{{ model.executionTimeoutMs || 120000 }} ms</el-descriptions-item
      >
    </el-descriptions>

    <!-- 描述 -->
    <el-card
      v-if="model.description"
      shadow="never"
      style="margin-bottom: 16px"
    >
      <template v-slot:header>
        <div style="font-weight: 600">描述</div>
      </template>
      <div style="color: #606266; font-size: 14px; line-height: 1.6">
        {{ model.description }}
      </div>
    </el-card>

    <!-- 输入输出字段 -->
    <el-tabs type="border-card">
      <!-- 输入字段 tab -->
      <el-tab-pane>
        <template v-slot:label>
          <span
            ><el-icon><el-icon-arrow-down /></el-icon> 输入字段</span
          >
        </template>
        <div
          style="
            margin-bottom: 10px;
            display: flex;
            align-items: center;
            justify-content: space-between;
          "
        >
          <span style="color: #909399; font-size: 12px">
            共
            {{ model.inputFields ? model.inputFields.length : 0 }}
            个字段，请关联引擎变量
          </span>
          <el-button size="small" :icon="ElIconRefresh" @click="load"
            >刷新</el-button
          >
        </div>

        <el-table
          :data="pagedInputFields"
          border
          size="small"
          max-height="500"
          v-loading="loading"
          :row-class-name="inputRowClassName"
        >
          <!-- 序号 -->
          <el-table-column label="序号" width="60" align="center">
            <template v-slot="{ $index }">{{
              inputFieldOffset + $index + 1
            }}</template>
          </el-table-column>
          <!-- 字段名称 -->
          <el-table-column prop="fieldName" label="字段名称" min-width="130">
            <template v-slot="{ row }">
              <span style="font-weight: 500">{{ row.fieldName }}</span>
            </template>
          </el-table-column>
          <!-- 对应变量 -->
          <el-table-column label="对应变量" min-width="280">
            <template v-slot="{ row }">
              <div v-if="row._editing" class="var-picker-cell">
                <operand-picker
                  :vars="varPickerOptions"
                  :functions="projectFunctions"
                  :value="row.sourceOperand"
                  :allowed-kinds="valueOperandKinds"
                  :expected-type="row.fieldType"
                  placeholder="选择阈值、路径、字段或方法"
                  width="100%"
                  @input="
                    (operand) =>
                      onFieldOperandSelect(row, 'sourceOperand', operand)
                  "
                />
              </div>
              <div v-else>
                <operand-value-display
                  :operand="row.sourceOperand"
                  empty-text="未关联"
                />
              </div>
            </template>
          </el-table-column>
          <el-table-column label="默认值" min-width="230">
            <template v-slot:header>
              <span>默认值</span>
              <el-tooltip
                content="来源为空或未取到值时使用默认值；未配置则按空值传入模型。"
                placement="top"
              >
                <el-icon class="default-value-info"><el-icon-info /></el-icon>
              </el-tooltip>
            </template>
            <template v-slot="{ row }">
              <operand-picker
                v-if="row._editing"
                :value="row.defaultOperand"
                :vars="varPickerOptions"
                :functions="projectFunctions"
                :allowed-kinds="valueOperandKinds"
                :expected-type="row.fieldType"
                placeholder="选择默认阈值、路径或字段"
                width="100%"
                @input="
                  (operand) =>
                    onFieldOperandSelect(row, 'defaultOperand', operand)
                "
              />
              <operand-value-display v-else :operand="row.defaultOperand" />
            </template>
          </el-table-column>
          <!-- 字段类型 -->
          <el-table-column
            prop="fieldType"
            label="字段类型"
            width="100"
            align="center"
          >
            <template v-slot="{ row }">
              <el-tag size="small" type="info">{{
                row.fieldType || '-'
              }}</el-tag>
            </template>
          </el-table-column>
          <!-- 操作 -->
          <el-table-column
            label="操作"
            width="140"
            align="center"
            fixed="right"
          >
            <template v-slot="{ row, $index }">
              <template v-if="row._editing">
                <el-button
                  link
                  size="small"
                  style="color: #67c23a"
                  :loading="row._saving"
                  @click="saveInputField(row, $index)"
                  >保存</el-button
                >
                <el-button
                  link
                  size="small"
                  style="color: #909399"
                  @click="cancelEditInput(row)"
                  >取消</el-button
                >
              </template>
              <el-button
                v-else
                link
                size="small"
                @click="editInputField(row)"
              >
                <el-icon><el-icon-edit /></el-icon> 编辑
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="inputFieldNeedsPaging"
          style="margin-top: 12px; text-align: right"
          :current-page="inputFieldPage"
          :page-size="fieldPageSize"
          :total="inputFieldsTotal"
          layout="total,prev,pager,next"
          @current-change="inputFieldPage = $event"
        />
        <div
          v-if="!model.inputFields || model.inputFields.length === 0"
          style="text-align: center; padding: 40px 0; color: #909399"
        >
          暂无输入字段
        </div>
      </el-tab-pane>

      <!-- 输出字段 tab -->
      <el-tab-pane>
        <template v-slot:label>
          <span
            ><el-icon><el-icon-arrow-up /></el-icon> 输出字段</span
          >
        </template>
        <div
          style="
            margin-bottom: 10px;
            display: flex;
            align-items: center;
            justify-content: space-between;
          "
        >
          <span style="color: #909399; font-size: 12px">
            共
            {{ model.outputFields ? model.outputFields.length : 0 }}
            个字段，请关联引擎变量
          </span>
          <el-button size="small" :icon="ElIconRefresh" @click="load"
            >刷新</el-button
          >
        </div>

        <el-table
          :data="pagedOutputFields"
          border
          size="small"
          max-height="500"
          v-loading="loading"
          :row-class-name="outputRowClassName"
        >
          <!-- 序号 -->
          <el-table-column label="序号" width="60" align="center">
            <template v-slot="{ $index }">{{
              outputFieldOffset + $index + 1
            }}</template>
          </el-table-column>
          <!-- 字段名称 -->
          <el-table-column prop="fieldName" label="字段名称" min-width="130">
            <template v-slot="{ row }">
              <span style="font-weight: 500">{{ row.fieldName }}</span>
            </template>
          </el-table-column>
          <!-- 对应变量 -->
          <el-table-column label="对应变量" min-width="280">
            <template v-slot="{ row }">
              <div v-if="row._editing" class="var-picker-cell">
                <operand-picker
                  :vars="varPickerOptions"
                  :value="row.targetOperand"
                  :allowed-kinds="writeOperandKinds"
                  writable-only
                  placeholder="选择目标路径或字段"
                  width="100%"
                  @input="
                    (operand) =>
                      onFieldOperandSelect(row, 'targetOperand', operand)
                  "
                />
              </div>
              <div v-else>
                <operand-value-display
                  :operand="row.targetOperand"
                  empty-text="未关联"
                />
              </div>
            </template>
          </el-table-column>
          <!-- 字段类型 -->
          <el-table-column
            prop="fieldType"
            label="字段类型"
            width="100"
            align="center"
          >
            <template v-slot="{ row }">
              <el-tag size="small" type="info">{{
                row.fieldType || '-'
              }}</el-tag>
            </template>
          </el-table-column>
          <!-- 转换方法（可编辑） -->
          <el-table-column label="转换方法" min-width="420">
            <template v-slot="{ row }">
              <div v-if="row._editing" class="transform-editor">
                <el-select
                  :model-value="transformFunctionId(row)"
                  size="small"
                  style="width: 100%"
                  filterable
                  clearable
                  placeholder="选择转换函数"
                  @change="
                    (functionId) => onTransformFunctionSelect(row, functionId)
                  "
                >
                  <el-option
                    v-for="fn in projectFunctions"
                    :key="fn.id"
                    :label="functionLabel(fn)"
                    :value="fn.id"
                  />
                </el-select>
                <div
                  v-for="(param, paramIndex) in transformFunctionParams(row)"
                  :key="param.name || paramIndex"
                  class="transform-param-row"
                >
                  <span class="transform-param-label">{{
                    param.name || '参数 ' + (paramIndex + 1)
                  }}</span>
                  <operand-picker
                    :value="
                      row.transformOperand && row.transformOperand.args
                        ? row.transformOperand.args[paramIndex]
                        : null
                    "
                    :vars="varPickerOptions"
                    :functions="projectFunctions"
                    :allowed-kinds="transformArgKinds"
                    :expected-type="param.type || ''"
                    placeholder="选择参数"
                    width="100%"
                    @input="
                      (operand) =>
                        onTransformArgSelect(row, paramIndex, operand)
                    "
                  />
                </div>
                <code v-if="row.transformOperand" class="transform-formula">{{
                  transformFormula(row)
                }}</code>
              </div>
              <code v-else class="transform-formula">{{
                transformFormula(row)
              }}</code>
            </template>
          </el-table-column>
          <!-- 操作 -->
          <el-table-column
            label="操作"
            width="140"
            align="center"
            fixed="right"
          >
            <template v-slot="{ row, $index }">
              <template v-if="row._editing">
                <el-button
                  link
                  size="small"
                  style="color: #67c23a"
                  :loading="row._saving"
                  @click="saveOutputField(row, $index)"
                  >保存</el-button
                >
                <el-button
                  link
                  size="small"
                  style="color: #909399"
                  @click="cancelEditOutput(row)"
                  >取消</el-button
                >
              </template>
              <el-button
                v-else
                link
                size="small"
                @click="editOutputField(row)"
              >
                <el-icon><el-icon-edit /></el-icon> 编辑
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="outputFieldNeedsPaging"
          style="margin-top: 12px; text-align: right"
          :current-page="outputFieldPage"
          :page-size="fieldPageSize"
          :total="outputFieldsTotal"
          layout="total,prev,pager,next"
          @current-change="outputFieldPage = $event"
        />
        <div
          v-if="!model.outputFields || model.outputFields.length === 0"
          style="text-align: center; padding: 40px 0; color: #909399"
        >
          暂无输出字段
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      title="版本历史"
      v-model="versionVisible"
      width="960px"
      :close-on-click-modal="false"
    >
      <el-table
        :data="versionList"
        border
        size="small"
        v-loading="versionLoading"
        style="width: 100%"
      >
        <el-table-column prop="version" label="版本" width="80" align="center">
          <template v-slot="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column
          prop="changeLog"
          label="变更说明"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column prop="publishBy" label="发布人" width="110" />
        <el-table-column prop="publishTime" label="发布时间" width="170">
          <template v-slot="{ row }">{{
            row.publishTime ? String(row.publishTime).replace('T', ' ') : '-'
          }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="center">
          <template v-slot="{ row, $index }">
            <el-button
              link
              size="small"
              :disabled="$index >= versionList.length - 1"
              @click="compareWithNext(row, $index)"
              >对比上一版</el-button
            >
            <el-button link size="small" @click="rollbackVersion(row)"
              >回滚</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <div v-if="versionCompare" style="margin-top: 12px">
        <el-alert
          :title="
            'v' +
            versionCompare.left.version +
            ' 与 v' +
            versionCompare.right.version +
            ' 对比'
          "
          :type="
            versionCompare.modelContentChanged ||
            versionCompare.modelConfigChanged
              ? 'warning'
              : 'success'
          "
          :closable="false"
          show-icon
        />
        <div class="version-compare-grid">
          <div>
            <div class="version-compare-title">左侧模型配置</div>
            <pre>{{ formatVersionJson(versionCompare.left.modelConfig) }}</pre>
          </div>
          <div>
            <div class="version-compare-title">右侧模型配置</div>
            <pre>{{ formatVersionJson(versionCompare.right.modelConfig) }}</pre>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 模型测试对话框 -->
    <el-dialog
      title="模型测试"
      v-model="testVisible"
      width="900px"
      :close-on-click-modal="false"
    >
      <!-- 数据未就绪时显示加载中，防止旧数据闪烁 -->
      <div
        v-if="!testReady"
        style="padding: 40px; text-align: center; color: #909399"
      >
        正在加载...
      </div>
      <template v-else>
        <div
          style="
            margin-bottom: 12px;
            display: flex;
            align-items: center;
            gap: 8px;
            flex-wrap: wrap;
          "
        >
          <el-button
            size="small"
            type="primary"
            :icon="ElIconVideoPlay"
            :loading="testExecuting"
            @click="doTest"
            >执行测试</el-button
          >
          <el-button
            size="small"
            :icon="ElIconDocument"
            @click="handleSaveParams"
            >保存测试参数</el-button
          >
          <el-button size="small" :icon="ElIconDelete" @click="handleClearParams"
            >清空参数</el-button
          >
          <el-tooltip content="从输入字段自动生成表单填写" placement="top">
            <el-button
              size="small"
              :type="testMode === 'manual' ? 'primary' : ''"
              @click="switchToManualMode"
              >表单填写</el-button
            >
          </el-tooltip>
          <el-tooltip content="直接编辑 JSON 参数" placement="top">
            <el-button
              size="small"
              :type="testMode === 'json' ? 'primary' : ''"
              @click="switchToJsonMode"
              >JSON 编辑</el-button
            >
          </el-tooltip>
        </div>

        <el-alert
          v-if="model.modelFormat && !supportsOnlineExecution"
          :title="model.modelFormat + ' 格式暂不支持在线执行'"
          type="warning"
          :closable="false"
          style="margin-bottom: 12px"
        />
        <el-alert
          v-else
          :title="
            '模型执行超时 ' +
            (model.executionTimeoutMs || 120000) +
            ' ms；页面请求超时 ' +
            modelRequestTimeoutMs +
            ' ms'
          "
          type="info"
          :closable="false"
          style="margin-bottom: 12px"
        />

        <div v-if="testMode === 'manual'" class="test-form-wrapper">
          <div v-if="testFields.length > 0" class="test-form-grid">
            <div
              v-for="field in testFields"
              :key="field.fieldName"
              class="test-field-cell"
            >
              <div class="test-field-label">
                {{ field.fieldLabel || field.fieldName }}
              </div>
              <el-input-number
                v-if="
                  field.fieldType === 'NUMBER' ||
                  field.fieldType === 'DOUBLE' ||
                  field.fieldType === 'INTEGER'
                "
                v-model="testParams[field.fieldName]"
                placeholder="输入值"
                controls-position="right"
                :precision="field.fieldType === 'INTEGER' ? 0 : undefined"
                :step="field.fieldType === 'INTEGER' ? 1 : 0.01"
                clearable
                style="width: 100%"
              />
              <el-select
                v-else-if="
                  field.fieldType === 'ENUM' &&
                  field.validValues &&
                  field.validValues.length
                "
                v-model="testParams[field.fieldName]"
                style="width: 100%"
                clearable
                filterable
                placeholder="选择值"
              >
                <el-option
                  v-for="v in field.validValues"
                  :key="v"
                  :label="v"
                  :value="v"
                />
              </el-select>
              <el-select
                v-else-if="field.fieldType === 'BOOLEAN'"
                v-model="testParams[field.fieldName]"
                style="width: 100%"
              >
                <el-option label="true" :value="true" />
                <el-option label="false" :value="false" />
              </el-select>
              <el-date-picker
                v-else-if="field.fieldType === 'DATE'"
                v-model="testParams[field.fieldName]"
                type="date"
                placeholder="选择日期"
                style="width: 100%"
                format="yyyy-MM-dd"
                value-format="yyyy-MM-dd"
              />
              <el-input
                v-else
                v-model="testParams[field.fieldName]"
                placeholder="输入值"
              />
              <div class="test-field-hint">{{ field.fieldName }}</div>
            </div>
          </div>
          <div
            v-else
            style="text-align: center; padding: 30px 0; color: #909399"
          >
            暂无输入字段，请切换到 JSON 模式手动编辑参数
          </div>
        </div>

        <div v-else class="test-form-wrapper">
          <monaco-editor
            v-model:value="testJsonStr"
            language="json"
            height="300px"
            :key="testDialogKey"
            @change="onJsonInput"
          />
          <div
            v-if="jsonError"
            style="color: #f56c6c; font-size: 12px; margin-top: 4px"
          >
            {{ jsonError }}
          </div>
        </div>

        <div v-if="testResult" style="margin-top: 16px">
          <el-divider content-position="left">执行结果</el-divider>
          <el-alert
            :title="testResult.success ? '执行成功' : '执行失败'"
            :type="testResult.success ? 'success' : 'error'"
            :closable="false"
            show-icon
            style="margin-bottom: 8px"
          >
            <span v-if="testResult.executeTimeMs"
              >耗时 {{ testResult.executeTimeMs }} ms</span
            >
            <span v-if="testResult.modelType"
              >，模型类型：{{ testResult.modelType }}</span
            >
          </el-alert>
          <div
            v-if="testResult.errorMessage || testResult.error"
            style="color: #f56c6c; margin-bottom: 8px"
          >
            {{ testResult.errorMessage || testResult.error }}
          </div>
          <div
            v-if="testResult.message"
            style="color: #e6a23c; margin-bottom: 8px"
          >
            {{ testResult.message }}
          </div>
          <div
            v-if="testResult.note"
            style="color: #909399; font-size: 12px; margin-bottom: 8px"
          >
            {{ testResult.note }}
          </div>
          <pre
            v-if="testResult.hasOutput"
            style="
              background: #f5f7fa;
              padding: 12px;
              border-radius: 4px;
              font-size: 13px;
              max-height: 200px;
              overflow: auto;
            "
            >{{ formatResult(testResult.output) }}</pre
          >
        </div>
      </template>

      <template v-slot:footer>
        <div>
          <el-button size="small" @click="testVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import {
  ArrowDown as ElIconArrowDown,
  InfoFilled as ElIconInfo,
  Edit as ElIconEdit,
  ArrowUp as ElIconArrowUp,
  Clock as ElIconTime,
  VideoPlay as ElIconVideoPlay,
  Back as ElIconBack,
  Refresh as ElIconRefresh,
  Document as ElIconDocument,
  Delete as ElIconDelete,
} from '@element-plus/icons-vue'
import * as api from '@/api/model'
import { getOnnxTaskLabel } from '@/constants/onnxTasks'
import { getRuleTestSchema } from '@/api/definition'
import { listVariablesByProject, listVariables } from '@/api/variable'
import { getVariableTree } from '@/api/dataObject'
import { listAllFunctionsByProject } from '@/api/function'
import OperandPicker from '@/components/common/OperandPicker.vue'
import OperandValueDisplay from '@/components/common/OperandValueDisplay.vue'
import {
  compileOperand,
  createFunctionOperand,
  createLiteralOperand,
  operandDisplay,
  operandFromReferenceFields,
  syncOperandReference,
} from '@/utils/operand'
import { getExpressionContext } from '@/constants/expressionContexts'
import {
  buildDetailReferenceMap,
  buildDetailReferenceState,
  buildReferenceCatalog,
  resolveDetailReference,
} from '@/utils/referenceCatalog'
import { formatTestOutput, normalizeTestResult } from '@/utils/testResult'
import {
  normalizeTestSchema,
  schemaFieldsToTestFields,
  flattenSchemaSample,
  buildNestedSchemaParams,
  readParamPath,
} from '@/utils/testSchema'

const MODEL_TYPE_LABELS = {
  LR: 'LR（逻辑回归）',
  XGBOOST: 'XGBoost',
  LIGHTGBM: 'LightGBM',
  CATBOOST: 'CatBoost',
  RANDOM_FOREST: 'RandomForest',
  NEURAL_NET: 'NeuralNet（神经网络）',
  SVM: 'SVM',
  CLASSIFICATION: '分类',
  REGRESSION: '回归',
  CLUSTERING: '聚类',
  ML: '机器学习',
}

export default {
  data() {
    return {
      loading: false,
      model: {},
      /** refType:id -> 字段对象映射（从变量管理/模型管理加载） */
      varMap: {},
      /** VarPicker 分层下拉选项（普通变量 / 常量 / 数据对象字段 / 模型） */
      varPickerGroups: [
        { label: '普通变量', options: [] },
        { label: '常量', options: [] },
        { label: '数据对象字段', options: [] },
        { label: '模型', options: [] },
      ],
      projectFunctions: [],
      valueOperandKinds: getExpressionContext('READ_EXPRESSION').allowedKinds,
      transformArgKinds: getExpressionContext('READ_EXPRESSION').allowedKinds,
      writeOperandKinds: getExpressionContext('WRITE_TARGET').allowedKinds,
      // 测试相关
      testVisible: false,
      testReady: false,
      testMode: 'manual',
      testFields: [],
      testParams: {},
      testJsonStr: '{}',
      testJsonSkeleton: '{}',
      jsonEdited: false,
      jsonError: '',
      testExecuting: false,
      testResult: null,
      testDialogKey: 1,
      fieldPageSize: 100,
      inputFieldPage: 1,
      outputFieldPage: 1,
      versionVisible: false,
      versionLoading: false,
      versionList: [],
      versionCompare: null,
      ElIconTime: markRaw(ElIconTime),
      ElIconVideoPlay: markRaw(ElIconVideoPlay),
      ElIconBack: markRaw(ElIconBack),
      ElIconRefresh: markRaw(ElIconRefresh),
      ElIconDocument: markRaw(ElIconDocument),
      ElIconDelete: markRaw(ElIconDelete),
    }
  },
  components: {
    OperandPicker,
    OperandValueDisplay,
    ElIconArrowDown,
    ElIconInfo,
    ElIconEdit,
    ElIconArrowUp,
  },
  name: 'ModelDetail',
  computed: {
    parsedModelConfig() {
      const config = this.model && this.model.modelConfig
      if (!config) return {}
      if (typeof config === 'object') return config
      try {
        return JSON.parse(config)
      } catch (e) {
        return {}
      }
    },
    supportsOnlineExecution() {
      return (
        this.model &&
        (this.model.modelFormat === 'PMML' || this.model.modelFormat === 'ONNX')
      )
    },
    onnxTaskLabel() {
      return getOnnxTaskLabel(this.parsedModelConfig.onnxTaskType)
    },
    onnxNodeSummary() {
      const metadata = this.parsedModelConfig.nodeMetadata || {}
      const inputs = metadata.inputs ? Object.keys(metadata.inputs).length : 0
      const outputs = metadata.outputs
        ? Object.keys(metadata.outputs).length
        : 0
      return inputs + ' 入 / ' + outputs + ' 出'
    },
    onnxExecutionProvider() {
      return String(
        this.parsedModelConfig.executionProvider || 'CPU'
      ).toUpperCase() === 'CUDA'
        ? 'CUDA'
        : 'CPU'
    },
    onnxGpuSummary() {
      const config = this.parsedModelConfig
      const deviceId = Number.isInteger(Number(config.cudaDeviceId))
        ? Number(config.cudaDeviceId)
        : 0
      const memory = Number.isFinite(Number(config.cudaGpuMemLimitMb))
        ? Number(config.cudaGpuMemLimitMb)
        : 0
      const arena = config.cudaArenaExtendStrategy || 'kNextPowerOfTwo'
      const algorithm = config.cudaCudnnConvAlgoSearch || 'EXHAUSTIVE'
      const defaultStream =
        config.cudaDoCopyInDefaultStream !== false ? '默认流' : '独立流'
      return (
        'GPU ' +
        deviceId +
        '，显存 ' +
        (memory > 0 ? memory + ' MB' : '不显式限制') +
        '，' +
        arena +
        '，' +
        algorithm +
        '，' +
        defaultStream
      )
    },
    modelRequestTimeoutMs() {
      return (this.model.executionTimeoutMs || 120000) + 5000
    },
    inputFieldsTotal() {
      return this.model && this.model.inputFields
        ? this.model.inputFields.length
        : 0
    },
    outputFieldsTotal() {
      return this.model && this.model.outputFields
        ? this.model.outputFields.length
        : 0
    },
    inputFieldNeedsPaging() {
      return this.inputFieldsTotal > this.fieldPageSize
    },
    outputFieldNeedsPaging() {
      return this.outputFieldsTotal > this.fieldPageSize
    },
    inputFieldOffset() {
      return this.inputFieldNeedsPaging
        ? (this.inputFieldPage - 1) * this.fieldPageSize
        : 0
    },
    outputFieldOffset() {
      return this.outputFieldNeedsPaging
        ? (this.outputFieldPage - 1) * this.fieldPageSize
        : 0
    },
    pagedInputFields() {
      const fields = (this.model && this.model.inputFields) || []
      if (!this.inputFieldNeedsPaging) return fields
      return fields.slice(
        this.inputFieldOffset,
        this.inputFieldOffset + this.fieldPageSize
      )
    },
    pagedOutputFields() {
      const fields = (this.model && this.model.outputFields) || []
      if (!this.outputFieldNeedsPaging) return fields
      return fields.slice(
        this.outputFieldOffset,
        this.outputFieldOffset + this.fieldPageSize
      )
    },
    /** 构建 VarPicker 需要的 vars 数组格式 */
    varPickerOptions() {
      const options = []
      this.varPickerGroups.forEach((group) => {
        group.options.forEach((v) => {
          options.push({
            varCode: v.varCode,
            varLabel: v.varLabelText || v.varCode,
            varType: v.varType,
            varObj: v.varObj,
            _varId: v.id,
            _refType: v.refType,
            refType: v.refType,
            _ref: {
              category:
                v.sourceType === 'dataObject'
                  ? 'object'
                  : v.sourceType === 'constant'
                  ? 'constant'
                  : v.sourceType === 'model'
                  ? 'model'
                  : 'standalone',
              objectCode: v.sourceCode || '',
              objectScriptName: v.sourceCode || '',
              objectLabel: v.sourceLabel || '',
            },
          })
        })
      })
      return options
    },
  },
  created() {
    this.load()
  },
  methods: {
    operandDisplay,
    parseOperand(value) {
      if (!value) return null
      if (typeof value === 'object') return JSON.parse(JSON.stringify(value))
      try {
        return JSON.parse(value)
      } catch (e) {
        return null
      }
    },
    onFieldOperandSelect(row, field, operand) {
      row[field] = operand || null
      if (field === 'defaultOperand') {
        row['defaultValue'] =
          operand && operand.kind === 'LITERAL' ? operand.value : ''
        return
      }
      row['varId'] = operand && operand.refId != null ? operand.refId : null
      row['refType'] = (operand && operand.refType) || ''
      const scriptName =
        operand && (operand.kind === 'PATH' || operand.kind === 'REFERENCE')
          ? operand.code || operand.value
          : row.fieldName
      row['scriptName'] = scriptName || ''
      row['fieldLabel'] =
        (operand && operand.label) || row.fieldLabel || row.fieldName || ''
    },
    transformFunctionId(row) {
      return row && row.transformOperand
        ? row.transformOperand.functionId
        : null
    },
    functionLabel(fn) {
      if (!fn) return ''
      const code = fn.funcCode || fn.functionCode || ''
      const name = fn.funcName || fn.functionName || code
      return code && code !== name ? name + ' (' + code + ')' : name
    },
    parseFunctionParams(fn) {
      if (!fn || !fn.paramsJson) return []
      try {
        const params = JSON.parse(fn.paramsJson)
        return Array.isArray(params) ? params : []
      } catch (e) {
        return []
      }
    },
    transformFunctionParams(row) {
      if (!row || !row.transformOperand) return []
      const fn = this.projectFunctions.find(
        (item) => String(item.id) === String(row.transformOperand.functionId)
      )
      return this.parseFunctionParams(fn)
    },
    onTransformFunctionSelect(row, functionId) {
      if (
        functionId === undefined ||
        functionId === null ||
        functionId === ''
      ) {
        row['transformOperand'] = null
        return
      }
      const fn = this.projectFunctions.find(
        (item) => String(item.id) === String(functionId)
      )
      if (!fn) {
        row['transformOperand'] = null
        return
      }
      const args = this.parseFunctionParams(fn).map(() => null)
      row['transformOperand'] = createFunctionOperand(fn, args)
    },
    onTransformArgSelect(row, index, operand) {
      if (!row || !row.transformOperand) return
      if (!Array.isArray(row.transformOperand.args))
        row.transformOperand['args'] = []
      row.transformOperand.args[index] = operand || null
    },
    transformFormula(row) {
      return row && row.transformOperand
        ? compileOperand(row.transformOperand) || '-'
        : '-'
    },
    async load() {
      const id = this.$route.params.id
      if (!id) return
      this.loading = true
      try {
        const res = await api.getModel(id)
        this.model = res.data || {}
        // 初始化 _editing 标志
        if (this.model.inputFields) {
          this.model.inputFields.forEach((f) => {
            f['_editing'] = false
            const sourceOperand =
              this.parseOperand(f.sourceOperand) ||
              operandFromReferenceFields(
                { ...f, valueType: f.fieldType },
                'scriptName',
                'varId',
                'refType'
              )
            if (sourceOperand && !sourceOperand.valueType)
              sourceOperand.valueType = f.fieldType || ''
            f['sourceOperand'] = sourceOperand
            f['defaultOperand'] =
              this.parseOperand(f.defaultOperand) ||
              (f.defaultValue
                ? createLiteralOperand(f.defaultValue, f.fieldType)
                : null)
          })
        }
        if (this.model.outputFields) {
          this.model.outputFields.forEach((f) => {
            f['_editing'] = false
            const targetOperand =
              this.parseOperand(f.targetOperand) ||
              operandFromReferenceFields(
                { ...f, valueType: f.fieldType },
                'scriptName',
                'varId',
                'refType'
              )
            if (targetOperand && !targetOperand.valueType)
              targetOperand.valueType = f.fieldType || ''
            f['targetOperand'] = targetOperand
            f['transformOperand'] = this.parseOperand(f.transformOperand)
          })
        }
        this.normalizeFieldPages()
        // 模型加载后加载变量库
        await this.loadVars()
      } catch (e) {
        this.$message.error(e.message || '加载模型详情失败')
      } finally {
        this.loading = false
      }
    },
    normalizeFieldPages() {
      const inputMax = Math.max(
        1,
        Math.ceil(this.inputFieldsTotal / this.fieldPageSize)
      )
      const outputMax = Math.max(
        1,
        Math.ceil(this.outputFieldsTotal / this.fieldPageSize)
      )
      if (this.inputFieldPage > inputMax) this.inputFieldPage = inputMax
      if (this.outputFieldPage > outputMax) this.outputFieldPage = outputMax
    },
    /** 加载当前项目下的所有变量，建立 id->变量 映射供关联使用 */
    async loadVars() {
      const projectId = this.model.projectId
      if (this.model.scope === 'GLOBAL') {
        // 全局模型以 scope 为准，避免历史残留 projectId 泄漏项目级资源
        await this.loadGlobalVars()
      } else if (projectId && projectId > 0) {
        // 项目级模型：加载项目变量 + 全局变量 + 常量 + 数据对象字段
        await this.loadVarsByProject(projectId)
      } else {
        // 全局模型（projectId 为 null/0）或 projectId 无效：加载全局变量 + 全局常量 + 全局数据对象字段
        await this.loadGlobalVars()
      }
    },
    async loadVarsByProject(projectId) {
      try {
        const [varsRes, constRes, treeRes, modelRes, functionRes] =
          await Promise.all([
            listVariablesByProject(projectId),
            listVariables({
              projectId,
              varSource: 'CONSTANT',
              pageNum: 1,
              pageSize: 5000,
            }),
            getVariableTree(projectId),
            api.listAllModelsByProject(projectId),
            listAllFunctionsByProject(projectId),
          ])
        const vars = Array.isArray(varsRes.data) ? varsRes.data : []
        const consts =
          constRes.data && Array.isArray(constRes.data.records)
            ? constRes.data.records
            : Array.isArray(constRes.data)
            ? constRes.data
            : []
        const tree = this.normalizeVariableTree(treeRes.data)
        const models = this.normalizeListResponse(modelRes)
        this.projectFunctions = this.normalizeListResponse(functionRes)
        this.buildVarOptions([...vars, ...consts], tree, models)
      } catch (e) {
        this.varMap = {}
        this.varPickerGroups.splice(
          0,
          this.varPickerGroups.length,
          ...[
            { label: '普通变量', options: [] },
            { label: '常量', options: [] },
            { label: '数据对象字段', options: [] },
            { label: '模型', options: [] },
          ]
        )
      }
    },
    async loadGlobalVars() {
      try {
        const [varsRes, constRes, treeRes, modelRes, functionRes] =
          await Promise.all([
            listVariables({ scope: 'GLOBAL', pageNum: 1, pageSize: 5000 }),
            listVariables({
              scope: 'GLOBAL',
              varSource: 'CONSTANT',
              pageNum: 1,
              pageSize: 5000,
            }),
            getVariableTree(0),
            api.listAllModelsByProject(0),
            listAllFunctionsByProject(0),
          ])
        const vars =
          varsRes.data && Array.isArray(varsRes.data.records)
            ? varsRes.data.records
            : Array.isArray(varsRes.data)
            ? varsRes.data
            : []
        const consts =
          constRes.data && Array.isArray(constRes.data.records)
            ? constRes.data.records
            : Array.isArray(constRes.data)
            ? constRes.data
            : []
        const tree = this.normalizeVariableTree(treeRes.data)
        const models = this.normalizeListResponse(modelRes)
        this.projectFunctions = this.normalizeListResponse(functionRes)
        this.buildVarOptions([...vars, ...consts], tree, models)
      } catch (e) {
        this.varMap = {}
        this.varPickerGroups.splice(
          0,
          this.varPickerGroups.length,
          ...[
            { label: '普通变量', options: [] },
            { label: '常量', options: [] },
            { label: '数据对象字段', options: [] },
            { label: '模型', options: [] },
          ]
        )
      }
    },
    normalizeVariableTree(data) {
      if (Array.isArray(data)) return data
      if (data && Array.isArray(data.tree)) return data.tree
      return []
    },
    normalizeListResponse(res) {
      const data = res && res.data ? res.data : res
      if (Array.isArray(data)) return data
      if (data && Array.isArray(data.records)) return data.records
      return []
    },
    flattenObjectVariables(vars) {
      const result = []
      const visit = (rows) => {
        const list = rows || []
        list.forEach((row) => {
          result.push(row)
          if (row.children && row.children.length) visit(row.children)
        })
      }
      visit(vars)
      return result
    },
    stripObjectPrefix(text, objectCode) {
      if (!text || !objectCode) return text || ''
      var prefix = objectCode + '.'
      return text.indexOf(prefix) === 0 ? text.substring(prefix.length) : text
    },
    refKey(id, refType) {
      if (!id || !refType) return ''
      return refType + ':' + id
    },
    getRowVarMap(row) {
      return resolveDetailReference(this.varMap, row)
    },
    bindingDisplay(row) {
      const item = this.getRowVarMap(row)
      if (!item) {
        return {
          label: row.fieldLabel || row.scriptName || '未关联',
          code: row.scriptName || '-',
          type: row.fieldType || '-',
          source: this.refTypeLabel(row.refType),
        }
      }
      return {
        label: item.varLabelText || item.varLabel || item.varCode,
        code: item.varCodeText || item.varCode || row.scriptName || '-',
        type: this.varTypeLabel(item.varType || row.fieldType),
        source: this.refTypeLabel(item.refType || row.refType),
      }
    },
    varTypeLabel(type) {
      return (
        {
          STRING: '字符串',
          NUMBER: '数值',
          INTEGER: '整数',
          DOUBLE: '小数',
          BOOLEAN: '布尔',
          DATE: '日期',
          ENUM: '枚举',
          OBJECT: '对象',
          LIST: '列表',
          MAP: '映射',
          MODEL: '模型',
        }[type] ||
        type ||
        '-'
      )
    },
    refTypeLabel(type) {
      return (
        {
          VARIABLE: '变量',
          CONSTANT: '常量',
          DATA_OBJECT: '对象字段',
          MODEL: '模型',
        }[type] ||
        type ||
        '变量'
      )
    },
    putVarMap(item) {
      const key = this.refKey(item.id, item.refType)
      if (key) this.varMap[key] = item
    },
    syncModelOperandReferences() {
      const options = this.varPickerOptions
      const syncField = (row, field) => {
        if (!row || !row[field]) return
        const result = syncOperandReference(row[field], options)
        if (result.changed) row[field] = result.operand
      }
      ;(this.model.inputFields || []).forEach((row) => {
        syncField(row, 'sourceOperand')
        syncField(row, 'defaultOperand')
      })
      ;(this.model.outputFields || []).forEach((row) => {
        syncField(row, 'targetOperand')
        syncField(row, 'transformOperand')
      })
    },
    buildVarOptions(vars, doTree, models = []) {
      const referenceModels = this.withCurrentModel(models)
      const state = buildDetailReferenceState(
        buildReferenceCatalog(vars, doTree, referenceModels)
      )
      if (state && state.items) {
        this.varMap = buildDetailReferenceMap(state)
        this.varPickerGroups.splice(
          0,
          this.varPickerGroups.length,
          ...state.groups.map((group) => ({
            label: group.label,
            options: group.options,
          }))
        )
        this.syncModelOperandReferences()
        return
      }
      this.varMap = {}
      const seenIds = new Set()
      const varOptions = []
      const constOptions = []
      const objOptions = []
      const modelOptions = []
      vars.forEach((v) => {
        const refType = v.varSource === 'CONSTANT' ? 'CONSTANT' : 'VARIABLE'
        const seenKey = this.refKey(v.id, refType)
        if (!v.id || seenIds.has(seenKey)) return
        seenIds.add(seenKey)
        const labelText = v.varLabel || ''
        const codeText = v.scriptName || v.varCode || ''
        const isConst = v.varSource === 'CONSTANT'
        const item = {
          id: v.id,
          refType,
          varCode: codeText,
          varLabel: labelText + (codeText ? ' ' + codeText : ''),
          varLabelText: labelText,
          varCodeText: codeText,
          varType: v.varType,
          varSource: v.varSource,
          sourceType: isConst ? 'constant' : 'variable',
          varObj: { ...v, refType },
        }
        this.putVarMap(item)
        if (isConst) {
          constOptions.push(item)
        } else {
          varOptions.push(item)
        }
      })
      doTree.forEach((group) => {
        const obj = group.object || {}
        const fields =
          group.flatVariables || this.flattenObjectVariables(group.variables)
        fields.forEach((f) => {
          const refType = 'DATA_OBJECT'
          const seenKey = this.refKey(f.id, refType)
          if (!f.id || seenIds.has(seenKey)) return
          seenIds.add(seenKey)
          const objCode = obj.scriptName || obj.objectCode || ''
          const labelText = this.stripObjectPrefix(f.varLabel || '', objCode)
          const codeText = f.scriptName || f.varCode || ''
          const objLabel = obj.objectLabel || obj.objectCode || '数据对象'
          const displayLabel = [objLabel, labelText].filter(Boolean).join('/')
          const item = {
            id: f.id,
            refType,
            varCode: codeText,
            varLabel: displayLabel + (codeText ? ' ' + codeText : ''),
            varLabelText: displayLabel,
            varCodeText: codeText,
            varType: f.varType,
            varSource: 'INPUT',
            sourceType: 'dataObject',
            sourceLabel: objLabel,
            sourceCode: objCode,
            varObj: { ...f, refType },
          }
          this.putVarMap(item)
          objOptions.push(item)
        })
      })
      models.forEach((m) => {
        const refType = 'MODEL'
        const seenKey = this.refKey(m.id, refType)
        if (!m.id || seenIds.has(seenKey)) return
        seenIds.add(seenKey)
        const codeText = m.modelCode || ''
        if (!codeText) return
        const labelText = m.modelName || codeText
        const item = {
          id: m.id,
          refType,
          varCode: codeText,
          varLabel: labelText + ' ' + codeText,
          varLabelText: labelText,
          varCodeText: codeText,
          varType: 'MODEL',
          varSource: 'MODEL',
          sourceType: 'model',
          varObj: {
            ...m,
            id: m.id,
            varCode: codeText,
            varLabel: labelText,
            scriptName: codeText,
            varType: 'MODEL',
            refType,
          },
        }
        this.putVarMap(item)
        modelOptions.push(item)
      })
      this.varPickerGroups.splice(
        0,
        this.varPickerGroups.length,
        ...[
          { label: '普通变量', options: varOptions },
          { label: '常量', options: constOptions },
          { label: '数据对象字段', options: objOptions },
          { label: '模型', options: modelOptions },
        ]
      )
      this.syncModelOperandReferences()
    },
    withCurrentModel(models) {
      const list = Array.isArray(models) ? models.slice() : []
      if (!this.model || !this.model.modelCode) return list
      const index = list.findIndex(
        (item) =>
          (item.id != null &&
            this.model.id != null &&
            String(item.id) === String(this.model.id)) ||
          item.modelCode === this.model.modelCode
      )
      if (index < 0) {
        list.push(this.model)
      } else if (
        !Array.isArray(list[index].outputFields) ||
        !list[index].outputFields.length
      ) {
        list.splice(index, 1, {
          ...list[index],
          outputFields: this.model.outputFields || [],
          inputFields: this.model.inputFields || [],
        })
      }
      return list
    },
    modelTypeLabel(t) {
      return MODEL_TYPE_LABELS[t] || t || '—'
    },
    formatFileSize(size) {
      if (!size) return '-'
      if (size < 1024) return size + ' B'
      if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
      return (size / 1024 / 1024).toFixed(2) + ' MB'
    },

    // ========== 输入字段编辑 ==========
    editInputField(row) {
      if (this.model.inputFields) {
        this.model.inputFields.forEach((f) => {
          if (f !== row) f['_editing'] = false
        })
      }
      row['_editing'] = true
      row['_origin'] = {
        varId: row.varId,
        refType: row.refType,
        fieldLabel: row.fieldLabel,
        scriptName: row.scriptName,
        sourceOperand: row.sourceOperand,
        defaultOperand: row.defaultOperand,
        defaultValue: row.defaultValue,
      }
    },
    async saveInputField(row) {
      row['_saving'] = true
      try {
        await api.updateModelInputField(row.id, {
          varId: row.varId,
          refType: row.refType,
          scriptName: row.scriptName,
          fieldLabel: row.fieldLabel,
          fieldType: row.fieldType,
          defaultValue: row.defaultValue,
          sourceOperand: row.sourceOperand
            ? JSON.stringify(row.sourceOperand)
            : null,
          defaultOperand: row.defaultOperand
            ? JSON.stringify(row.defaultOperand)
            : null,
          transformType: row.transformType,
          transformParams: row.transformParams,
          validValues: row.validValues,
        })
        row['_editing'] = false
        row['_saving'] = false
        this.$message.success('保存成功')
      } catch (e) {
        row['_saving'] = false
        this.$message.error('保存失败: ' + (e.message || e))
      }
    },
    cancelEditInput(row) {
      if (row._origin) {
        row['varId'] = row._origin.varId
        row['refType'] = row._origin.refType
        row['fieldLabel'] = row._origin.fieldLabel
        row['scriptName'] = row._origin.scriptName
        row['sourceOperand'] = row._origin.sourceOperand
        row['defaultOperand'] = row._origin.defaultOperand
        row['defaultValue'] = row._origin.defaultValue
      }
      row['_editing'] = false
    },
    inputRowClassName({ row }) {
      return row._editing ? 'editing-row' : ''
    },

    // ========== 输出字段编辑 ==========
    editOutputField(row) {
      if (this.model.outputFields) {
        this.model.outputFields.forEach((f) => {
          if (f !== row) f['_editing'] = false
        })
      }
      row['_editing'] = true
      row['_origin'] = {
        varId: row.varId,
        refType: row.refType,
        fieldLabel: row.fieldLabel,
        scriptName: row.scriptName,
        transformOperand: this.parseOperand(row.transformOperand),
        targetOperand: row.targetOperand,
      }
    },
    async saveOutputField(row) {
      row['_saving'] = true
      try {
        await api.updateModelOutputField(row.id, {
          varId: row.varId,
          refType: row.refType,
          scriptName: row.scriptName,
          fieldLabel: row.fieldLabel,
          fieldType: row.fieldType,
          targetField: row.targetField,
          targetOperand: row.targetOperand
            ? JSON.stringify(row.targetOperand)
            : null,
          transformOperand: row.transformOperand
            ? JSON.stringify(row.transformOperand)
            : null,
        })
        row['_editing'] = false
        row['_saving'] = false
        this.$message.success('保存成功')
      } catch (e) {
        row['_saving'] = false
        this.$message.error('保存失败: ' + (e.message || e))
      }
    },
    cancelEditOutput(row) {
      if (row._origin) {
        row['varId'] = row._origin.varId
        row['refType'] = row._origin.refType
        row['fieldLabel'] = row._origin.fieldLabel
        row['scriptName'] = row._origin.scriptName
        row['transformOperand'] = this.parseOperand(
          row._origin.transformOperand
        )
        row['targetOperand'] = row._origin.targetOperand
      }
      row['_editing'] = false
    },
    outputRowClassName({ row }) {
      return row._editing ? 'editing-row' : ''
    },

    // ========== 版本管理 ==========
    async openVersionDialog() {
      this.versionVisible = true
      this.versionCompare = null
      await this.loadVersions()
    },
    async loadVersions() {
      if (!this.model.id) return
      this.versionLoading = true
      try {
        const res = await api.listVersions(this.model.id)
        this.versionList = res.data || res || []
      } catch (e) {
        this.$message.error(e.message || '加载版本历史失败')
      } finally {
        this.versionLoading = false
      }
    },
    async compareWithNext(row, index) {
      const right = this.versionList[index + 1]
      if (!row || !right) return
      try {
        const res = await api.compareVersions(
          this.model.id,
          row.version,
          right.version
        )
        this.versionCompare = res.data || res
      } catch (e) {
        this.$message.error(e.message || '版本对比失败')
      }
    },
    async rollbackVersion(row) {
      if (!row) return
      try {
        await this.$confirm(
          '确定回滚模型到 v' +
            row.version +
            ' 吗？当前模型内容会被该版本快照覆盖。',
          '确认回滚',
          { type: 'warning' }
        )
        await api.rollbackVersion(this.model.id, row.version)
        this.$message.success('回滚成功')
        this.versionVisible = false
        await this.load()
      } catch (e) {
        if (e !== 'cancel') this.$message.error(e.message || '回滚失败')
      }
    },
    formatVersionJson(text) {
      if (!text) return ''
      try {
        return JSON.stringify(JSON.parse(text), null, 2)
      } catch (e) {
        return text
      }
    },

    // ========== 模型测试 ==========
    async openTestDialog() {
      // 1. 先打开弹窗，此时 testReady=false，内容显示"正在加载..."，旧数据被隐藏
      this.testVisible = true
      this.testReady = false
      this.testResult = null
      this.testMode = 'manual'
      this.jsonEdited = false
      this.jsonError = ''
      this.testDialogKey++ // 递增 key 强制重新挂载 MonacoEditor

      // 2. 异步获取最新模型数据（不阻塞弹窗打开）
      let freshModel = this.model
      try {
        const res = await api.getModel(this.model.id)
        if (res.data) freshModel = res.data
      } catch (e) {
        /* fallback to cached */
      }

      let schema = null
      try {
        schema = normalizeTestSchema(
          await getRuleTestSchema({
            targetType: 'MODEL',
            targetId: this.model.id,
          })
        )
      } catch (e) {
        /* compatibility fallback for older servers */
      }
      const hasSchema =
        schema &&
        (schema.inputs.length || Object.keys(schema.sampleParams).length)

      // 3. 初始化字段列表（解析 validValues）
      const testFields = hasSchema
        ? schemaFieldsToTestFields(schema.inputs)
        : (freshModel.inputFields || [])
            .filter((f) => f.status !== 0)
            .map((f) => {
              if (f.validValues && typeof f.validValues === 'string') {
                try {
                  f.validValues = JSON.parse(f.validValues)
                } catch {
                  f.validValues = []
                }
              }
              if (!f.validValues) f.validValues = []
              return f
            })

      // 4. 从服务端获取已保存的测试参数（最高优先级）
      let savedParams = null
      try {
        const res = await api.getTestParams(this.model.id)
        if (res.data) {
          savedParams =
            typeof res.data === 'string' ? JSON.parse(res.data) : res.data
        }
      } catch (e) {
        /* ignore */
      }

      // 5. 从上传时设置的样例初始化（modelConfig.testParams，次优先级）
      let configParams = null
      if (!savedParams) {
        try {
          const rawConfig = freshModel.modelConfig
          const config =
            typeof rawConfig === 'string'
              ? JSON.parse(rawConfig)
              : rawConfig || {}
          if (config && config.testParams) {
            configParams =
              typeof config.testParams === 'string'
                ? JSON.parse(config.testParams)
                : config.testParams
          }
        } catch (e) {
          /* ignore */
        }
      }

      // 6. 优先级：已保存参数 > 上传样例 > 空对象
      const initObj =
        savedParams || configParams || (hasSchema ? schema.sampleParams : {})

      // 7. 构建 testParams 和 testJsonStr
      //    数字字段默认 0（而非 null），避免 el-input-number 显示 0.000000
      const testParams = flattenSchemaSample(testFields, initObj)

      // 8. 构建初始 JSON（包含所有字段的当前值）
      const jsonObj = buildNestedSchemaParams(testFields, testParams)
      const testJsonStr = JSON.stringify(jsonObj, null, 2)
      const testJsonSkeleton = JSON.stringify({}, null, 2)

      // 9. 一次性设置所有数据，然后标记为就绪，触发重新渲染
      this.testFields = testFields
      this.testParams = testParams
      this.testJsonStr = testJsonStr
      this.testJsonSkeleton = testJsonSkeleton
      this.testReady = true // ✅ 内容切换：隐藏加载中，显示实际表单/JSON编辑器
    },
    /**
     * 切换到 JSON 编辑模式：同步 testParams → testJsonStr
     */
    switchToJsonMode() {
      if (this.testMode === 'json') return
      this.testMode = 'json'
      this.syncParamsToJson()
    },
    /**
     * 切换到表单填写模式：同步 testJsonStr → testParams
     * 仅更新 testParams 中原本为 undefined 或已为默认值（非用户填写）的字段，
     * 保留用户已手动填写的值不被覆盖。
     */
    switchToManualMode() {
      if (this.testMode === 'manual') return
      this.testMode = 'manual'
      this.syncJsonToParams()
    },
    syncParamsToJson() {
      const flat = {}
      this.testFields.forEach((f) => {
        const val = this.testParams[f.fieldName]
        if (val !== '' && val !== null) {
          flat[f.fieldName] = val
        } else {
          flat[f.fieldName] = null
        }
      })
      const obj = buildNestedSchemaParams(this.testFields, flat)
      this.testJsonStr = JSON.stringify(obj, null, 2)
      this.jsonEdited = false
      this.jsonError = ''
    },
    buildJsonStr() {
      const flat = {}
      Object.keys(this.testParams).forEach((k) => {
        const val = this.testParams[k]
        if (val !== '' && val !== null) flat[k] = val
        else flat[k] = null
      })
      const obj = buildNestedSchemaParams(this.testFields, flat)
      return JSON.stringify(obj, null, 2)
    },
    onJsonInput() {
      this.jsonEdited = true
      this.jsonError = ''
      try {
        JSON.parse(this.testJsonStr)
      } catch (e) {
        this.jsonError = 'JSON 格式错误: ' + e.message
      }
    },
    /**
     * 从 JSON 同步到表单：只更新 testParams 中原本为 undefined 的字段，
     * 保留用户已在表单中填写的值不被覆盖。
     */
    syncJsonToParams() {
      try {
        const obj = JSON.parse(this.testJsonStr)
        this.testFields.forEach((field) => {
          const value = readParamPath(obj, field.fieldName)
          if (value !== undefined) this.testParams[field.fieldName] = value
        })
        this.jsonError = ''
      } catch (e) {
        this.jsonError = 'JSON 格式错误: ' + e.message
      }
    },
    async doTest() {
      this.testResult = null
      this.testExecuting = true
      let params
      if (this.testMode === 'json') {
        try {
          params = JSON.parse(this.testJsonStr)
        } catch (e) {
          this.$message.error('JSON 格式错误: ' + e.message)
          this.testExecuting = false
          return
        }
      } else {
        params = buildNestedSchemaParams(this.testFields, this.testParams)
      }
      try {
        const res = await api.executeModel(
          this.model.id,
          params,
          this.modelRequestTimeoutMs
        )
        this.testResult = normalizeTestResult(res)
        if (this.testResult.success) {
          this.testJsonStr = JSON.stringify(params, null, 2)
          this.jsonEdited = true
        }
      } catch (e) {
        this.testResult = { success: false, error: e.message || '测试执行失败' }
      } finally {
        this.testExecuting = false
      }
    },
    async handleSaveParams() {
      let params
      if (this.testMode === 'json') {
        try {
          params = JSON.parse(this.testJsonStr)
        } catch (e) {
          this.$message.error('JSON 格式错误: ' + e.message)
          return
        }
      } else {
        params = buildNestedSchemaParams(this.testFields, this.testParams)
      }
      try {
        await api.saveTestParams(this.model.id, JSON.stringify(params))
        this.$message.success('测试参数已保存')
      } catch (e) {
        this.$message.error('保存失败: ' + (e.message || e))
      }
    },
    /**
     * 清空参数：数字字段重置为 0，布尔重置为 false，字符串重置为空
     */
    handleClearParams() {
      this.testParams = {}
      this.testFields.forEach((f) => {
        if (f.fieldType === 'BOOLEAN') this.testParams[f.fieldName] = false
        else if (
          f.fieldType === 'NUMBER' ||
          f.fieldType === 'DOUBLE' ||
          f.fieldType === 'INTEGER'
        )
          this.testParams[f.fieldName] = 0
        else this.testParams[f.fieldName] = ''
      })
      const jsonObj = buildNestedSchemaParams(this.testFields, this.testParams)
      this.testJsonStr = JSON.stringify(jsonObj, null, 2)
      this.jsonEdited = false
      this.testResult = null
      this.jsonError = ''
    },
    formatResult(outputs) {
      return formatTestOutput(outputs)
    },
  },
}
</script>

<style scoped>
.script-name-text {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  color: #409eff;
}
.binding-display {
  min-width: 0;
}
.binding-name {
  color: #1f2937;
  font-weight: 600;
  font-size: 13px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.binding-meta {
  color: #64748b;
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.script-unbound {
  color: #c0c4cc;
  font-style: italic;
}
.default-value-info {
  margin-left: 4px;
  color: #94a3b8;
  cursor: help;
}
.transform-editor {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.transform-param-row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  align-items: center;
  gap: 6px;
}
.transform-param-label {
  overflow: hidden;
  color: #606266;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.transform-formula {
  display: block;
  overflow: hidden;
  color: #334155;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.var-picker-cell {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
}
.var-picker-cell :deep(.var-picker-wrap) {
  flex: 1;
  min-width: 0;
}
.custom-var-input {
  flex: 1;
  min-width: 0;
}
.var-switch-btn {
  padding: 4px 6px;
  color: #909399;
  flex-shrink: 0;
}
.var-switch-btn:hover {
  color: #1890ff;
}
.version-compare-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 12px;
}
.version-compare-title {
  color: #334155;
  font-weight: 700;
  font-size: 12px;
  margin-bottom: 6px;
}
.version-compare-grid pre {
  margin: 0;
  padding: 10px;
  min-height: 180px;
  max-height: 360px;
  overflow: auto;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
  font-family: Menlo, Monaco, Consolas, monospace;
}
:deep(.editing-row) {
  background-color: #f0f9eb;
}
:deep(.el-loading-mask.el-loading-fade-leave-active) {
  pointer-events: none;
}
:deep(.el-table .editing-row td) {
  background-color: #f0f9eb;
}
.test-form-wrapper {
  max-height: 420px;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 4px 0;
}
.test-form-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0;
  padding: 8px;
}
.test-field-cell {
  padding: 8px 12px;
  border-radius: 4px;
  transition: background-color 0.15s;
}
.test-field-cell:hover {
  background-color: #f5f7fa;
}
.test-field-label {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
  margin-bottom: 6px;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.test-field-hint {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 4px;
  font-family: 'Courier New', monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
