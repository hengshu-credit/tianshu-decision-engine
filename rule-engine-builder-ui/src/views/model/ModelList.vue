<template>
  <div class="uiue-list-page">
    <!-- 提示信息 -->
    <div class="linkage-hint">
      <el-icon><el-icon-info /></el-icon> 模型仅支持从 ONNX、PMML
      格式文件导入，用于在规则设计时调用机器学习模型进行预测。
    </div>

    <el-tabs v-model="activeTab" type="border-card" class="page-tabs">
      <el-tab-pane label="模型管理" name="list">
        <!-- 操作按钮栏 -->
        <div class="uiue-btn-bar">
          <div class="btn-right">
            <el-button
              size="small"
              type="primary"
              :icon="ElIconUpload2"
              @click="handleUpload"
              >上传模型</el-button
            >
          </div>
        </div>

        <!-- 筛选条件 -->
        <div class="uiue-search-container">
          <el-form :inline="true" size="small" @keyup.enter="handleQuery">
            <el-form-item label="作用范围">
              <el-select
                v-model="qp.scope"
                clearable
                filterable
                placeholder="全部"
                style="width: 100px"
                @change="handleQuery"
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
            <el-form-item label="模型大类">
              <el-select
                v-model="qp.modelType"
                clearable
                filterable
                placeholder="全部"
                style="width: 120px"
                @change="handleQuery"
              >
                <el-option label="LR（逻辑回归）" value="LR" />
                <el-option label="XGBoost" value="XGBOOST" />
                <el-option label="LightGBM" value="LIGHTGBM" />
                <el-option label="CatBoost" value="CATBOOST" />
                <el-option label="RandomForest" value="RANDOM_FOREST" />
                <el-option label="NeuralNet（神经网络）" value="NEURAL_NET" />
                <el-option label="SVM" value="SVM" />
              </el-select>
            </el-form-item>
            <el-form-item label="模型格式">
              <el-select
                v-model="qp.modelFormat"
                clearable
                filterable
                placeholder="全部"
                style="width: 110px"
                @change="handleQuery"
              >
                <el-option label="PMML" value="PMML" />
                <el-option label="ONNX" value="ONNX" />
              </el-select>
            </el-form-item>
            <el-form-item label="模型编码">
              <remote-filter-select
                v-model:value="qp.modelCode"
                :fetch-options="fetchModelCodeOptions"
                option-label-key="modelCode"
                option-value-key="modelCode"
                allow-free-input
                placeholder="输入筛选"
                style="width: 140px"
              />
            </el-form-item>
            <el-form-item label="模型名称">
              <remote-filter-select
                v-model:value="qp.modelName"
                :fetch-options="fetchModelNameOptions"
                option-label-key="modelName"
                option-value-key="modelName"
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
        </div>

        <!-- 模型列表 -->
        <el-table
          :data="models"
          border
          size="small"
          v-loading="loading"
          style="width: 100%"
        >
          <template v-slot:empty>
            <div class="tab-empty">暂无模型，点击「上传模型」添加</div>
          </template>
          <el-table-column label="作用范围" width="90" align="center">
            <template v-slot="{ row }">
              <el-tag
                :class="
                  row.scope === 'GLOBAL' ? 'el-tag--scope-global' : 'el-tag--scope-project'
                "
                size="small"
                >{{ row.scope === 'GLOBAL' ? '全局' : '项目级' }}</el-tag
              >
            </template>
          </el-table-column>
          <el-table-column
            label="项目名称"
            min-width="120"
            show-overflow-tooltip
          >
            <template v-slot="{ row }">{{ modelProjectName(row) }}</template>
          </el-table-column>
          <el-table-column
            prop="modelCode"
            label="模型编码"
            min-width="140"
            show-overflow-tooltip
          />
          <el-table-column
            prop="modelName"
            label="模型名称"
            min-width="160"
            show-overflow-tooltip
          />
          <el-table-column label="模型大类" min-width="90" align="center">
            <template v-slot="{ row }"
              ><el-tag size="small">{{
                modelTypeLabel(row.modelType)
              }}</el-tag></template
            >
          </el-table-column>
          <el-table-column label="模型格式" min-width="90" align="center">
            <template v-slot="{ row }"
              ><el-tag size="small" type="info">{{
                row.modelFormat
              }}</el-tag></template
            >
          </el-table-column>
          <el-table-column label="执行设备" min-width="90" align="center">
            <template v-slot="{ row }">
              <el-tag
                v-if="row.modelFormat === 'ONNX'"
                :type="
                  modelExecutionProvider(row) === 'CUDA' ? 'success' : 'info'
                "
                size="small"
              >
                {{ modelExecutionProvider(row) }}
              </el-tag>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="启动预加载" min-width="90" align="center">
            <template v-slot="{ row }">{{
              row.modelFormat === 'ONNX' && row.preloadOnStartup === 1
                ? '是'
                : '否'
            }}</template>
          </el-table-column>
          <el-table-column label="执行超时" min-width="90" align="center">
            <template v-slot="{ row }"
              >{{ row.executionTimeoutMs || 120000 }} ms</template
            >
          </el-table-column>
          <el-table-column label="字段数" min-width="80" align="center">
            <template v-slot="{ row }"
              >{{ row.inputFieldCount || 0 }}进 /
              {{ row.outputFieldCount || 0 }}出</template
            >
          </el-table-column>
          <el-table-column
            prop="currentVersion"
            label="设计版本"
            min-width="70"
            align="center"
          />
          <el-table-column
            prop="publishedVersion"
            label="发布版本"
            min-width="70"
            align="center"
          >
            <template v-slot="{ row }">{{
              row.publishedVersion || '-'
            }}</template>
          </el-table-column>
          <el-table-column
            prop="status"
            label="状态"
            min-width="60"
            align="center"
          >
            <template v-slot="{ row }"
              ><el-tag
                :type="row.status === 1 ? 'success' : 'info'"
                size="small"
                >{{ row.status === 1 ? '启用' : '停用' }}</el-tag
              ></template
            >
          </el-table-column>
          <el-table-column label="操作" min-width="230" align="center">
            <template v-slot="{ row }">
              <el-button
                link
                size="small"
                @click="$router.push('/model/' + row.id)"
                >详情</el-button
              >
              <el-button link size="small" @click="handleEdit(row)"
                >编辑</el-button
              >
              <el-button link size="small" @click="handlePublish(row)">{{
                row.publishedVersion ? '重新发布' : '发布'
              }}</el-button>
              <el-button
                v-if="row.publishedVersion"
                link
                size="small"
                @click="openImpact(row, 'OFFLINE')"
                >下线</el-button
              >
              <el-button
                v-if="row.scope === 'PROJECT'"
                link
                size="small"
                @click="handleToGlobal(row)"
                >转为全局</el-button
              >
              <el-button
                link
                size="small"
                class="btn-delete"
                @click="handleDelete(row)"
                >删除</el-button
              >
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          style="margin-top: 12px; text-align: right"
          :current-page="qp.pageNum"
          :page-size="qp.pageSize"
          :total="total"
          layout="total,sizes,prev,pager,next"
          :page-sizes="[10, 30, 50, 100]"
          @current-change="
            (p) => {
              qp.pageNum = p
              load()
            }
          "
          @size-change="
            (s) => {
              qp.pageSize = s
              qp.pageNum = 1
              load()
            }
          "
        />
      </el-tab-pane>
      <el-tab-pane label="模型执行日志" name="logs">
        <module-call-log module-type="MODEL" title="模型执行日志" />
      </el-tab-pane>
    </el-tabs>

    <!-- 上传模型对话框 -->
    <el-dialog
      title="上传模型"
      v-model="uploadVisible"
      width="700px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="uploadForm"
        :model="uploadForm"
        :rules="uploadRules"
        label-width="120px"
        size="small"
      >
        <el-form-item label="作用范围">
          <el-radio-group v-model="uploadForm.scope">
            <el-radio value="PROJECT">项目级</el-radio>
            <el-radio value="GLOBAL">全局</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="uploadForm.scope === 'PROJECT'"
          label="所属项目"
          prop="projectId"
        >
          <el-select
            v-model="uploadForm.projectId"
            style="width: 100%"
            filterable
            clearable
            placeholder="请选择项目"
          >
            <el-option
              v-for="p in projects"
              :key="p.id"
              :label="p.projectName"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="模型编码" prop="modelCode">
          <el-input
            v-model="uploadForm.modelCode"
            placeholder="英文编码，如 credit_score_model"
          />
        </el-form-item>
        <el-form-item label="模型名称" prop="modelName">
          <el-input
            v-model="uploadForm.modelName"
            placeholder="中文名称，如 信用评分模型"
          />
        </el-form-item>
        <el-form-item label="模型大类" prop="modelType">
          <el-select v-model="uploadForm.modelType" style="width: 100%">
            <el-option label="LR（逻辑回归）" value="LR" />
            <el-option label="XGBoost" value="XGBOOST" />
            <el-option label="LightGBM" value="LIGHTGBM" />
            <el-option label="CatBoost" value="CATBOOST" />
            <el-option label="RandomForest" value="RANDOM_FOREST" />
            <el-option label="NeuralNet（神经网络）" value="NEURAL_NET" />
            <el-option label="SVM" value="SVM" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型文件" prop="file">
          <el-upload
            ref="upload"
            action="#"
            :auto-upload="false"
            :limit="1"
            :accept="supportedModelAccept"
            :on-change="handleFileChange"
            :file-list="fileList"
          >
            <el-button size="small" type="primary">选择文件</el-button>
            <template v-slot:tip>
              <span class="el-upload__tip">仅支持 ONNX、PMML 格式</span>
            </template>
          </el-upload>
        </el-form-item>
        <template v-if="isOnnxFile">
          <el-form-item label="ONNX 推理任务" required>
            <el-select
              v-model="uploadForm.onnxTaskType"
              style="width: 100%"
              filterable
              placeholder="请选择该模型的推理任务"
              @change="onOnnxTaskChange"
            >
              <el-option
                v-for="task in onnxTasks"
                :key="task.value"
                :label="task.label"
                :value="task.value"
              >
                <span>{{ task.label }}</span>
                <span style="float: right; color: #909399; font-size: 12px">{{
                  task.description
                }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <template
            v-if="
              uploadForm.onnxTaskType === 'YUNET_FACE_DETECTION' ||
              uploadForm.onnxTaskType === 'SCRFD_FACE_DETECTION'
            "
          >
            <el-form-item label="置信度阈值">
              <el-input-number
                v-model="uploadForm.onnxConfig.confidenceThreshold"
                :min="0"
                :max="1"
                :step="0.05"
                :precision="2"
              />
            </el-form-item>
            <el-form-item label="NMS 阈值">
              <el-input-number
                v-model="uploadForm.onnxConfig.nmsThreshold"
                :min="0"
                :max="1"
                :step="0.05"
                :precision="2"
              />
            </el-form-item>
          </template>
          <template v-if="uploadForm.onnxTaskType === 'YUNET_FACE_DETECTION'">
            <el-form-item label="最小人脸尺寸">
              <el-input-number
                v-model="uploadForm.onnxConfig.minFaceSize"
                :min="1"
                :step="1"
              />
            </el-form-item>
            <el-form-item label="候选上限">
              <el-input-number
                v-model="uploadForm.onnxConfig.topK"
                :min="1"
                :step="100"
              />
            </el-form-item>
          </template>
          <template v-if="uploadForm.onnxTaskType === 'SCRFD_FACE_DETECTION'">
            <el-form-item label="模型输入尺寸">
              <el-input-number
                v-model="uploadForm.onnxConfig.inputWidth"
                :min="32"
                :step="32"
              />
              <span style="margin: 0 8px; color: #909399">×</span>
              <el-input-number
                v-model="uploadForm.onnxConfig.inputHeight"
                :min="32"
                :step="32"
              />
            </el-form-item>
          </template>
          <el-form-item v-if="uploadSupportsGpu" label="执行设备">
            <el-radio-group v-model="uploadForm.executionProvider">
              <el-radio value="CPU">CPU（默认）</el-radio>
              <el-radio value="CUDA">GPU（CUDA）</el-radio>
            </el-radio-group>
            <div
              class="runtime-hint"
              :class="{
                'runtime-hint-error':
                  uploadUsesCuda && !runtimeCapabilities.cudaAvailable,
              }"
            >
              {{ cudaRuntimeHint }}
            </div>
          </el-form-item>
          <template v-if="uploadSupportsGpu && uploadUsesCuda">
            <el-form-item label="GPU 设备号">
              <el-input-number
                v-model="uploadForm.cudaDeviceId"
                :min="0"
                :step="1"
                :precision="0"
              />
            </el-form-item>
            <el-form-item label="显存上限">
              <el-input-number
                v-model="uploadForm.cudaGpuMemLimitMb"
                :min="0"
                :step="1024"
                :precision="0"
              />
              <span class="runtime-unit">MB，0 表示不显式限制</span>
            </el-form-item>
            <el-form-item label="显存扩展策略">
              <el-select
                v-model="uploadForm.cudaArenaExtendStrategy"
                style="width: 260px"
              >
                <el-option label="按 2 的幂次扩展" value="kNextPowerOfTwo" />
                <el-option label="按实际申请量扩展" value="kSameAsRequested" />
              </el-select>
            </el-form-item>
            <el-form-item label="cuDNN 算法">
              <el-select
                v-model="uploadForm.cudaCudnnConvAlgoSearch"
                style="width: 260px"
              >
                <el-option label="EXHAUSTIVE（精确搜索）" value="EXHAUSTIVE" />
                <el-option label="HEURISTIC（启发式）" value="HEURISTIC" />
                <el-option label="DEFAULT（默认算法）" value="DEFAULT" />
              </el-select>
            </el-form-item>
            <el-form-item label="默认 CUDA 流">
              <el-switch
                v-model="uploadForm.cudaDoCopyInDefaultStream"
                active-text="启用"
                inactive-text="停用"
              />
            </el-form-item>
          </template>
        </template>
        <el-form-item v-if="isOnnxFile" label="启动预加载">
          <el-switch
            v-model="uploadForm.preloadOnStartup"
            :active-value="1"
            :inactive-value="0"
            active-text="是"
            inactive-text="否"
          />
          <div style="color: #909399; font-size: 11px; margin-top: 2px">
            服务启动时加载到内存，避免第一次推理再加载模型
          </div>
        </el-form-item>
        <el-form-item label="执行超时">
          <el-input-number
            v-model="uploadForm.executionTimeoutMs"
            :min="100"
            :max="1800000"
            :step="1000"
          />
          <span style="margin-left: 8px; color: #909399">毫秒</span>
        </el-form-item>
        <el-form-item label="变更说明">
          <el-input
            v-model="uploadForm.changeLog"
            type="textarea"
            :rows="2"
            placeholder="简要描述本次变更内容"
          />
        </el-form-item>
        <el-form-item label="测试参数">
          <monaco-editor
            v-model:value="uploadForm.testParams"
            language="json"
            height="130px"
          />
          <div style="color: #909399; font-size: 11px; margin-top: 2px">
            可选。用于在模型测试时自动填充默认参数（JSON 格式）
          </div>
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="uploadForm.description"
            type="textarea"
            :rows="2"
          />
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <div>
          <el-progress
            v-if="uploading"
            :percentage="uploadProgress"
            style="margin-bottom: 10px"
          />
          <el-button size="small" @click="uploadVisible = false"
            >取消</el-button
          >
          <el-button
            size="small"
            type="primary"
            @click="handleDoUpload"
            :loading="uploading"
            >上传</el-button
          >
        </div>
      </template>
    </el-dialog>

    <!-- 转为全局模型对话框 -->
    <el-dialog
      title="转为全局模型"
      v-model="toGlobalVisible"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="toGlobalForm"
        :model="toGlobalForm"
        :rules="toGlobalRules"
        label-width="110px"
        size="small"
      >
        <el-form-item label="当前模型">{{
          toGlobalModelInfo.modelName
        }}</el-form-item>
        <el-form-item label="当前编码">{{
          toGlobalModelInfo.modelCode
        }}</el-form-item>
        <el-form-item label="新全局编码" prop="modelCode">
          <el-input
            v-model="toGlobalForm.modelCode"
            placeholder="重新填写全局唯一编码"
          />
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <div>
          <el-button size="small" @click="toGlobalVisible = false"
            >取消</el-button
          >
          <el-button
            size="small"
            type="primary"
            @click="handleDoToGlobal"
            :loading="toGlobalLoading"
            >确认转换</el-button
          >
        </div>
      </template>
    </el-dialog>

    <!-- 编辑模型对话框 -->
    <el-dialog
      title="编辑模型"
      v-model="editVisible"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="editForm"
        :model="editForm"
        :rules="editRules"
        label-width="110px"
        size="small"
      >
        <el-form-item label="模型编码">{{ editForm.modelCode }}</el-form-item>
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="editForm.modelName" placeholder="模型中文名称" />
        </el-form-item>
        <el-form-item label="模型大类">{{
          modelTypeLabel(editForm.modelType)
        }}</el-form-item>
        <el-form-item label="模型格式">{{ editForm.modelFormat }}</el-form-item>
        <el-form-item label="作用范围">{{
          editForm.scope === 'GLOBAL' ? '全局' : '项目级'
        }}</el-form-item>
        <el-form-item v-if="editSupportsGpu" label="执行设备">
          <el-radio-group v-model="editForm.executionProvider">
            <el-radio value="CPU">CPU（默认）</el-radio>
            <el-radio value="CUDA">GPU（CUDA）</el-radio>
          </el-radio-group>
          <div
            class="runtime-hint"
            :class="{
              'runtime-hint-error':
                editUsesCuda && !runtimeCapabilities.cudaAvailable,
            }"
          >
            {{ cudaRuntimeHint }}
          </div>
        </el-form-item>
        <template v-if="editSupportsGpu && editUsesCuda">
          <el-form-item label="GPU 设备号">
            <el-input-number
              v-model="editForm.cudaDeviceId"
              :min="0"
              :step="1"
              :precision="0"
            />
          </el-form-item>
          <el-form-item label="显存上限">
            <el-input-number
              v-model="editForm.cudaGpuMemLimitMb"
              :min="0"
              :step="1024"
              :precision="0"
            />
            <span class="runtime-unit">MB，0 表示不显式限制</span>
          </el-form-item>
          <el-form-item label="显存扩展策略">
            <el-select
              v-model="editForm.cudaArenaExtendStrategy"
              style="width: 260px"
            >
              <el-option label="按 2 的幂次扩展" value="kNextPowerOfTwo" />
              <el-option label="按实际申请量扩展" value="kSameAsRequested" />
            </el-select>
          </el-form-item>
          <el-form-item label="cuDNN 算法">
            <el-select
              v-model="editForm.cudaCudnnConvAlgoSearch"
              style="width: 260px"
            >
              <el-option label="EXHAUSTIVE（精确搜索）" value="EXHAUSTIVE" />
              <el-option label="HEURISTIC（启发式）" value="HEURISTIC" />
              <el-option label="DEFAULT（默认算法）" value="DEFAULT" />
            </el-select>
          </el-form-item>
          <el-form-item label="默认 CUDA 流">
            <el-switch
              v-model="editForm.cudaDoCopyInDefaultStream"
              active-text="启用"
              inactive-text="停用"
            />
          </el-form-item>
        </template>
        <el-form-item v-if="editForm.modelFormat === 'ONNX'" label="启动预加载">
          <el-switch
            v-model="editForm.preloadOnStartup"
            :active-value="1"
            :inactive-value="0"
            active-text="是"
            inactive-text="否"
          />
        </el-form-item>
        <el-form-item label="执行超时">
          <el-input-number
            v-model="editForm.executionTimeoutMs"
            :min="100"
            :max="1800000"
            :step="1000"
          />
          <span style="margin-left: 8px; color: #909399">毫秒</span>
        </el-form-item>
        <el-form-item label="目标类别">
          <el-input
            v-model="editForm.targetCategories"
            placeholder="分类模型的目标变量类别数，如 2"
          />
        </el-form-item>
        <el-form-item label="模型版本">
          <el-input
            v-model="editForm.modelVersion"
            placeholder="模型自身版本号"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="3"
            placeholder="模型描述信息"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="editForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <div>
          <el-button size="small" @click="editVisible = false">取消</el-button>
          <el-button
            size="small"
            type="primary"
            @click="handleDoEdit"
            :loading="editLoading"
            >保存</el-button
          >
        </div>
      </template>
    </el-dialog>
    <model-impact-dialog
      v-if="pendingModel.id"
      v-model="impactVisible"
      :model-id="pendingModel.id"
      :action="pendingImpactAction"
      @confirmed="handleImpactConfirmed"
    />
  </div>
</template>

<script>
import { markRaw } from 'vue'
import {
  InfoFilled as ElIconInfo,
  Upload as ElIconUpload2,
} from '@element-plus/icons-vue'
import * as api from '@/api/model'
import { listProjects } from '@/api/project'
import {
  clearPageState,
  restorePageState,
  savePageState,
} from '@/utils/pageStateCache'
import ModuleCallLog from '@/components/common/ModuleCallLog.vue'
import MonacoEditor from '@/components/MonacoEditor'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import ProjectFilterSelect from '@/components/ProjectFilterSelect.vue'
import ModelImpactDialog from '@/components/model/ModelImpactDialog.vue'
import {
  ONNX_TASKS,
  createOnnxConfig,
  createOnnxRuntimeConfig,
  onnxRuntimePayload,
  parseOnnxModelConfig,
} from '@/constants/onnxTasks'

const MODEL_TYPE_LABELS = {
  LR: 'LR（逻辑回归）',
  XGBOOST: 'XGBoost',
  LIGHTGBM: 'LightGBM',
  CATBOOST: 'CatBoost',
  RANDOM_FOREST: 'RandomForest',
  NEURAL_NET: 'NeuralNet（神经网络）',
  SVM: 'SVM',
}

const createUploadForm = () => ({
  projectId: '',
  scope: 'PROJECT',
  modelCode: '',
  modelName: '',
  modelType: 'LR',
  description: '',
  changeLog: '',
  testParams: '',
  onnxTaskType: '',
  onnxConfig: {},
  preloadOnStartup: 0,
  executionTimeoutMs: 120000,
  ...createOnnxRuntimeConfig(),
})

const createEditForm = () => ({
  id: null,
  modelCode: '',
  modelName: '',
  modelType: '',
  modelFormat: '',
  scope: '',
  targetCategories: '',
  modelVersion: '',
  description: '',
  status: 1,
  preloadOnStartup: 0,
  executionTimeoutMs: 120000,
  onnxTaskType: '',
  originalModelConfig: {},
  ...createOnnxRuntimeConfig(),
})

export default {
  data() {
    return {
      loading: false,
      pendingModel: {},
      pendingImpactAction: 'DELETE',
      impactVisible: false,
      activeTab: 'list',
      models: [],
      total: 0,
      projects: [],
      projectList: [],
      supportedModelAccept: '.onnx,.pmml',
      filteredProjectCodes: [],
      filteredProjectNames: [],
      qp: {
        pageNum: 1,
        pageSize: 10,
        scope: '',
        modelType: '',
        modelFormat: '',
        modelCode: '',
        modelName: '',
        projectCode: '',
        projectName: '',
      },
      allModelCodes: [],
      allModelNames: [],
      filteredModelCodes: [],
      filteredModelNames: [],
      // 上传
      uploadVisible: false,
      uploading: false,
      uploadProgress: 0,
      onnxTasks: ONNX_TASKS,
      runtimeCapabilities: {
        onnxRuntimeVersion: '',
        availableProviders: ['CPU'],
        cudaAvailable: false,
        cudaError: '',
        cpuFallbackEnabled: true,
        activeCpuFallbackCount: 0,
      },
      uploadForm: createUploadForm(),
      uploadRules: {
        modelCode: [
          {
            required: true,
            message: '请输入模型编码',
            trigger: 'blur',
          },
          {
            validator: (rule, value, callback) => {
              if (!value) {
                callback()
                return
              }
              const pid =
                this.uploadForm.scope === 'PROJECT'
                  ? this.uploadForm.projectId || undefined
                  : undefined
              api
                .checkModelCode(
                  value.trim(),
                  this.uploadForm.scope,
                  pid,
                  undefined
                )
                .then((res) => {
                  if (res.data === true) {
                    callback(
                      new Error(
                        this.uploadForm.scope === 'GLOBAL'
                          ? '该编码已被其他全局模型使用'
                          : '该编码在当前项目内已存在，或与某个全局编码冲突'
                      )
                    )
                  } else {
                    callback()
                  }
                })
                .catch(() => callback())
            },
            trigger: 'blur',
          },
        ],
        modelName: [
          { required: true, message: '请输入模型名称', trigger: 'blur' },
        ],
        modelType: [
          { required: true, message: '请选择模型大类', trigger: 'change' },
        ],
      },
      fileList: [],
      selectedFile: null,
      selectedFileName: '',
      // 转为全局模型
      toGlobalVisible: false,
      toGlobalLoading: false,
      toGlobalModelInfo: { id: null, modelCode: '', modelName: '' },
      toGlobalForm: { modelCode: '' },
      toGlobalRules: {
        modelCode: [
          {
            required: true,
            message: '请填写新的全局模型编码',
            trigger: 'blur',
          },
          {
            validator: (rule, value, callback) => {
              if (!value) {
                callback()
                return
              }
              api
                .checkModelCode(
                  value.trim(),
                  'GLOBAL',
                  undefined,
                  this.toGlobalModelInfo.id || undefined
                )
                .then((res) => {
                  if (res.data === true) {
                    callback(new Error('该编码已被其他全局模型使用'))
                  } else {
                    callback()
                  }
                })
                .catch(() => callback())
            },
            trigger: 'blur',
          },
        ],
      },
      // 编辑模型
      editVisible: false,
      editLoading: false,
      editForm: createEditForm(),
      editRules: {
        modelName: [
          { required: true, message: '请输入模型名称', trigger: 'blur' },
        ],
      },
      ElIconUpload2: markRaw(ElIconUpload2),
    }
  },
  components: {
    ModuleCallLog,
    MonacoEditor,
    RemoteFilterSelect,
    ProjectFilterSelect,
    ModelImpactDialog,
    ElIconInfo,
  },
  name: 'ModelList',
  computed: {
    isOnnxFile() {
      const name =
        (this.selectedFile && this.selectedFile.name) || this.selectedFileName
      return !!(name && name.toLowerCase().endsWith('.onnx'))
    },
    uploadSupportsGpu() {
      const task = this.onnxTasks.find(
        (item) => item.value === this.uploadForm.onnxTaskType
      )
      return this.isOnnxFile && !!(task && task.supportsGpu)
    },
    editSupportsGpu() {
      const task = this.onnxTasks.find(
        (item) => item.value === this.editForm.onnxTaskType
      )
      return (
        this.editForm.modelFormat === 'ONNX' &&
        this.editForm.modelType === 'NEURAL_NET' &&
        !!(task && task.supportsGpu)
      )
    },
    uploadUsesCuda() {
      return this.uploadForm.executionProvider === 'CUDA'
    },
    editUsesCuda() {
      return this.editForm.executionProvider === 'CUDA'
    },
    cudaRuntimeHint() {
      const activeFallbackCount =
        Number(this.runtimeCapabilities.activeCpuFallbackCount) || 0
      if (activeFallbackCount > 0) {
        return (
          '当前服务已有 ' +
          activeFallbackCount +
          ' 个模型的 CUDA 运行配置已自动回退 CPU，正在通过 CPU 执行；' +
          '其他 GPU 模型初始化或推理失败时也会自动回退 CPU，首次回退可能增加耗时；修复 GPU 环境后请重启服务。'
        )
      }
      if (this.runtimeCapabilities.cudaAvailable) {
        return (
          '当前服务已检测到 CUDA Execution Provider（ONNX Runtime ' +
          (this.runtimeCapabilities.onnxRuntimeVersion || '-') +
          '）。GPU 初始化或推理失败时将自动回退 CPU；首次回退可能增加耗时，修复 GPU 环境后请重启服务。'
        )
      }
      return (
        '当前服务 CUDA 环境不可用：' +
        (this.runtimeCapabilities.cudaError ||
          '请检查 CUDA、cuDNN 与 ONNX Runtime GPU 依赖') +
        '。模型运行时将自动回退 CPU；首次回退可能增加耗时，修复 GPU 环境后请重启服务。'
      )
    },
  },
  created() {
    this.restoreCachedState()
    this.loadProjects()
    this.loadRuntimeCapabilities()
  },
  mounted() {
    this.load()
  },
  methods: {
    restoreCachedState() {
      const state = restorePageState('ModelList')
      if (state.qp) this.qp = { ...this.qp, ...state.qp }
    },
    saveCachedState() {
      savePageState('ModelList', { qp: this.qp })
    },
    async loadProjects() {
      try {
        const res = await listProjects({ pageNum: 1, pageSize: 1000 })
        const list = res.data && res.data.records ? res.data.records : []
        this.projects = list
        this.projectList = list
        this.filteredProjectCodes = list.slice(0, 20)
        this.filteredProjectNames = list.slice(0, 20)
      } catch (e) {
        this.projects = []
        this.projectList = []
      }
    },
    fetchModelCodeOptions({ query, pageNum, pageSize }) {
      return api.listModels({
        ...this.qp,
        pageNum,
        pageSize,
        modelCode: query || '',
      })
    },
    fetchModelNameOptions({ query, pageNum, pageSize }) {
      return api.listModels({
        ...this.qp,
        pageNum,
        pageSize,
        modelName: query || '',
      })
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
    queryModelCode(q) {
      const query = (q || '').toLowerCase()
      this.filteredModelCodes = query
        ? this.allModelCodes
            .filter((c) => c && c.toLowerCase().includes(query))
            .slice(0, 20)
        : this.allModelCodes.slice(0, 20)
    },
    queryModelName(q) {
      const query = (q || '').toLowerCase()
      this.filteredModelNames = query
        ? this.allModelNames
            .filter((n) => n && n.toLowerCase().includes(query))
            .slice(0, 20)
        : this.allModelNames.slice(0, 20)
    },
    async load() {
      this.loading = true
      try {
        this.saveCachedState()
        const params = { ...this.qp }
        Object.keys(params).forEach((k) => {
          if (!params[k]) delete params[k]
        })
        const res = await api.listModels(params)
        const data = res.data || {}
        this.models = data.records || []
        this.total = data.total || 0
        const codeSet = new Set(),
          nameSet = new Set()
        ;(data.records || []).forEach((r) => {
          if (r.modelCode) codeSet.add(r.modelCode)
          if (r.modelName) nameSet.add(r.modelName)
        })
        this.allModelCodes = Array.from(codeSet)
        this.allModelNames = Array.from(nameSet)
        this.filteredModelCodes = this.allModelCodes.slice(0, 20)
        this.filteredModelNames = this.allModelNames.slice(0, 20)
      } catch (err) {
        this.$message.error('加载模型列表失败')
      } finally {
        this.loading = false
      }
    },
    handleQuery() {
      this.qp.pageNum = 1
      this.load()
    },
    resetQuery() {
      this.qp = {
        pageNum: 1,
        pageSize: this.qp.pageSize,
        scope: '',
        modelType: '',
        modelFormat: '',
        modelCode: '',
        modelName: '',
        projectCode: '',
        projectName: '',
      }
      clearPageState('ModelList')
      this.load()
    },
    modelTypeLabel(t) {
      return MODEL_TYPE_LABELS[t] || t || '—'
    },
    modelProjectName(row) {
      return row && row.scope === 'GLOBAL'
        ? '—'
        : (row && row.projectName) || '—'
    },
    modelExecutionProvider(row) {
      return createOnnxRuntimeConfig(
        parseOnnxModelConfig(row && row.modelConfig)
      ).executionProvider
    },
    async loadRuntimeCapabilities() {
      try {
        const res = await api.getRuntimeCapabilities()
        this.runtimeCapabilities = {
          ...this.runtimeCapabilities,
          ...(res.data || {}),
        }
      } catch (e) {
        this.runtimeCapabilities = {
          ...this.runtimeCapabilities,
          cudaAvailable: false,
          cudaError: e.message || '运行时能力查询失败',
        }
      }
    },

    handleUpload() {
      this.uploadForm = createUploadForm()
      this.uploadProgress = 0
      this.fileList = []
      this.selectedFile = null
      this.selectedFileName = ''
      this.uploadVisible = true
      this.$nextTick(() => {
        if (this.$refs.uploadForm) this.$refs.uploadForm.clearValidate()
      })
    },
    handleFileChange(file, files) {
      const fileName = file.name || (file.raw && file.raw.name) || ''
      const lowerName = fileName.toLowerCase()
      if (!lowerName.endsWith('.onnx') && !lowerName.endsWith('.pmml')) {
        this.selectedFile = null
        this.selectedFileName = ''
        this.fileList = []
        if (this.$refs.upload && this.$refs.upload.clearFiles)
          this.$refs.upload.clearFiles()
        this.$message.error('仅支持 ONNX、PMML 格式的模型文件')
        return
      }
      this.selectedFile = file.raw
      this.selectedFileName = fileName
      this.fileList = files.slice(-1)
      if (this.isOnnxFile) this.uploadForm.modelType = 'NEURAL_NET'
      if (!this.isOnnxFile) {
        this.uploadForm.onnxTaskType = ''
        this.uploadForm.onnxConfig = {}
      }
    },
    onOnnxTaskChange(taskType) {
      this.uploadForm.onnxTaskType = taskType
      this.uploadForm.onnxConfig = createOnnxConfig(taskType)
    },
    handleDoUpload() {
      this.$refs.uploadForm.validate(async (valid) => {
        if (!valid) return
        if (!this.selectedFile) {
          this.$message.warning('请选择模型文件')
          return
        }
        if (this.uploadForm.scope === 'PROJECT' && !this.uploadForm.projectId) {
          this.$message.warning('请选择所属项目')
          return
        }
        if (this.isOnnxFile && !this.uploadForm.onnxTaskType) {
          this.$message.warning('请选择 ONNX 推理任务')
          return
        }
        this.uploading = true
        this.uploadProgress = 0
        try {
          const formData = new FormData()
          formData.append('file', this.selectedFile)
          // 修复: projectId 为 null 时不添加到 FormData，避免被转成字符串 "null" 导致后端 Long 类型转换失败
          if (this.uploadForm.scope !== 'GLOBAL' && this.uploadForm.projectId) {
            formData.append('projectId', this.uploadForm.projectId)
          }
          formData.append('scope', this.uploadForm.scope)
          formData.append('modelCode', this.uploadForm.modelCode)
          formData.append('modelName', this.uploadForm.modelName)
          formData.append('modelType', this.uploadForm.modelType)
          formData.append('description', this.uploadForm.description || '')
          formData.append('changeLog', this.uploadForm.changeLog || '')
          formData.append('testParams', this.uploadForm.testParams || '')
          formData.append('preloadOnStartup', this.uploadForm.preloadOnStartup)
          formData.append(
            'executionTimeoutMs',
            this.uploadForm.executionTimeoutMs
          )
          if (this.isOnnxFile) {
            formData.append('onnxTaskType', this.uploadForm.onnxTaskType)
            formData.append(
              'onnxConfig',
              JSON.stringify({
                ...(this.uploadForm.onnxConfig || {}),
                ...onnxRuntimePayload(this.uploadForm),
              })
            )
          }
          await api.uploadModel(formData, (event) => {
            if (event && event.total)
              this.uploadProgress = Math.round(
                (event.loaded * 100) / event.total
              )
          })
          this.$message.success('上传成功')
          this.uploadVisible = false
          this.load()
        } catch (e) {
          this.$message.error(e.message || '上传失败')
        } finally {
          this.uploading = false
        }
      })
    },

    async handlePublish(row) {
      try {
        await this.$confirm('确定发布模型「' + row.modelName + '」？')
        await api.publishModel(row.id)
        this.$message.success('发布成功')
        this.load()
      } catch (e) {
        if (e !== 'cancel') this.$message.error(e.message || '发布失败')
      }
    },
    handleDelete(row) {
      this.openImpact(row, 'DELETE')
    },
    openImpact(row, action) {
      this.pendingModel = row
      this.pendingImpactAction = action
      this.impactVisible = true
    },
    async handleImpactConfirmed({ action, impactToken }) {
      try {
        if (action === 'DELETE') await api.deleteModel(this.pendingModel.id, impactToken)
        else if (action === 'OFFLINE') await api.unpublishModel(this.pendingModel.id, impactToken)
        this.impactVisible = false
        this.$message.success(action === 'DELETE' ? '模型已逻辑删除' : '模型已下线')
        await this.load()
      } catch (error) {
        this.$message.error(error.message || '模型操作失败')
      }
    },
    handleEdit(row) {
      const originalModelConfig = parseOnnxModelConfig(row.modelConfig)
      this.editForm = {
        id: row.id,
        modelCode: row.modelCode,
        modelName: row.modelName,
        modelType: row.modelType,
        modelFormat: row.modelFormat,
        scope: row.scope,
        targetCategories: row.targetCategories || '',
        modelVersion: row.modelVersion || '',
        description: row.description || '',
        status: row.status == null ? 1 : row.status,
        preloadOnStartup: row.preloadOnStartup === 1 ? 1 : 0,
        executionTimeoutMs: row.executionTimeoutMs || 120000,
        onnxTaskType: originalModelConfig.onnxTaskType || '',
        originalModelConfig,
        ...createOnnxRuntimeConfig(originalModelConfig),
      }
      this.editVisible = true
      this.$nextTick(() => {
        if (this.$refs.editForm) this.$refs.editForm.clearValidate()
      })
    },
    handleDoEdit() {
      this.$refs.editForm.validate(async (valid) => {
        if (!valid) return
        this.editLoading = true
        try {
          const payload = {
            id: this.editForm.id,
            modelName: this.editForm.modelName,
            description: this.editForm.description,
            targetCategories: this.editForm.targetCategories || null,
            modelVersion: this.editForm.modelVersion || null,
            status: this.editForm.status,
            preloadOnStartup: this.editForm.preloadOnStartup,
            executionTimeoutMs: this.editForm.executionTimeoutMs,
          }
          if (this.editSupportsGpu) {
            payload.modelConfig = JSON.stringify({
              ...this.editForm.originalModelConfig,
              ...onnxRuntimePayload(this.editForm),
            })
          }
          await api.updateModel(payload)
          this.$message.success('保存成功')
          this.editVisible = false
          this.load()
        } catch (e) {
          this.$message.error(e.message || '保存失败')
        } finally {
          this.editLoading = false
        }
      })
    },
    handleToGlobal(row) {
      this.toGlobalModelInfo = {
        id: row.id,
        modelCode: row.modelCode,
        modelName: row.modelName,
      }
      this.toGlobalForm.modelCode = ''
      this.toGlobalVisible = true
      this.$nextTick(() => {
        if (this.$refs.toGlobalForm) this.$refs.toGlobalForm.clearValidate()
      })
    },
    handleDoToGlobal() {
      this.$refs.toGlobalForm.validate(async (valid) => {
        if (!valid) return
        this.toGlobalLoading = true
        try {
          await api.toGlobalModel(
            this.toGlobalModelInfo.id,
            this.toGlobalForm.modelCode.trim()
          )
          this.$message.success('转换成功，该模型已转为全局模型')
          this.toGlobalVisible = false
          this.load()
        } catch (e) {
          this.$message.error(e.message || '转换失败')
        } finally {
          this.toGlobalLoading = false
        }
      })
    },
  },
}
</script>

<style scoped>
.linkage-hint {
  font-size: 12px;
  color: #909399;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
}
.tab-filter-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.tab-empty {
  text-align: center;
  padding: 48px 0;
  color: #c0c4cc;
  font-size: 14px;
}
.runtime-hint {
  margin-top: 4px;
  color: #909399;
  font-size: 11px;
  line-height: 1.5;
}
.runtime-hint-error {
  color: #e6a23c;
}
.runtime-unit {
  margin-left: 8px;
  color: #909399;
}
.tab-filter-row .el-button:not(.el-button--primary):focus,
.tab-filter-row .el-button:not(.el-button--primary):focus-visible,
.tab-filter-row .el-button:not(.el-button--primary):active,
.tab-filter-row .el-button:not(.el-button--primary).is-plain:focus,
.tab-filter-row .el-button:not(.el-button--primary).is-plain:active {
  outline: none !important;
  box-shadow: none !important;
  background-color: transparent !important;
  border-color: #dcdfe6 !important;
}
.el-button.is-link,
.el-button.is-link:hover,
.el-button.is-link:focus,
.el-button.is-link:active,
.el-button.is-link:focus-visible {
  color: #606266 !important;
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
}
.el-button.is-link:hover {
  color: #1890ff !important;
}
</style>
