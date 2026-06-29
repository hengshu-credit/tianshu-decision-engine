<template>
  <div class="uiue-list-page">
    <!-- 提示信息 -->
    <div class="linkage-hint">
      <i class="el-icon-info" /> 模型支持从 PMML、PICKLE、DILL、ONNX 等格式文件导入，用于在规则设计时调用机器学习模型进行预测。
    </div>

    <!-- 操作按钮栏 -->
    <div class="uiue-btn-bar">
      <div class="btn-right">
        <el-button size="small" type="primary" icon="el-icon-upload2" @click="handleUpload">上传模型</el-button>
      </div>
    </div>

    <!-- 筛选条件 -->
    <div class="uiue-search-container">
      <el-form :inline="true" size="small">
        <el-form-item label="作用范围">
          <el-select v-model="qp.scope" clearable filterable placeholder="全部" style="width:100px;" @change="handleQuery">
            <el-option label="全局" value="GLOBAL" />
            <el-option label="项目" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目编码">
          <el-select v-model="qp.projectCode" clearable filterable remote reserve-keyword placeholder="输入筛选" style="width:140px;"
            :remote-method="queryProjectCode" :loading="projectListLoading">
            <el-option v-for="p in filteredProjectCodes" :key="p.projectCode" :label="p.projectCode" :value="p.projectCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目名称">
          <el-select v-model="qp.projectName" clearable filterable remote reserve-keyword placeholder="输入筛选" style="width:140px;"
            :remote-method="queryProjectName" :loading="projectListLoading">
            <el-option v-for="p in filteredProjectNames" :key="p.projectName" :label="p.projectName" :value="p.projectName" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型大类">
          <el-select v-model="qp.modelType" clearable filterable placeholder="全部" style="width:120px;" @change="handleQuery">
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
          <el-select v-model="qp.modelFormat" clearable filterable placeholder="全部" style="width:110px;" @change="handleQuery">
            <el-option label="PMML" value="PMML" />
            <el-option label="PICKLE" value="PICKLE" />
            <el-option label="DILL" value="DILL" />
            <el-option label="ONNX" value="ONNX" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型编码">
          <el-select v-model="qp.modelCode" clearable filterable remote reserve-keyword placeholder="输入筛选" style="width:140px;"
            :remote-method="queryModelCode" :loading="modelCodeLoading">
            <el-option v-for="c in filteredModelCodes" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型名称">
          <el-select v-model="qp.modelName" clearable filterable remote reserve-keyword placeholder="输入筛选" style="width:140px;"
            :remote-method="queryModelName" :loading="modelNameLoading">
            <el-option v-for="n in filteredModelNames" :key="n" :label="n" :value="n" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 模型列表 -->
    <el-table :data="models" border size="small" v-loading="loading" style="width:100%;">
      <div slot="empty" class="tab-empty">暂无模型，点击「上传模型」添加</div>
      <el-table-column label="作用范围" width="90" align="center">
          <template slot-scope="{ row }">
            <el-tag :type="row.scope === 'GLOBAL' ? 'scope-global' : 'scope-project'" size="mini">{{ row.scope === 'GLOBAL' ? '全局' : '项目级' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="项目名称" min-width="120" show-overflow-tooltip>
          <template slot-scope="{ row }">{{ row.projectName || (row.scope === 'GLOBAL' ? '—' : '—') }}</template>
        </el-table-column>
        <el-table-column prop="modelCode" label="模型编码" min-width="140" show-overflow-tooltip />
        <el-table-column prop="modelName" label="模型名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="模型大类" min-width="90" align="center">
          <template slot-scope="{ row }"><el-tag size="mini">{{ modelTypeLabel(row.modelType) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="模型格式" min-width="90" align="center">
          <template slot-scope="{ row }"><el-tag size="mini" type="info">{{ row.modelFormat }}</el-tag></template>
        </el-table-column>
        <el-table-column label="字段数" min-width="80" align="center">
          <template slot-scope="{ row }">{{ row.inputFieldCount || 0 }}进 / {{ row.outputFieldCount || 0 }}出</template>
        </el-table-column>
        <el-table-column prop="currentVersion" label="设计版本" min-width="70" align="center" />
        <el-table-column prop="publishedVersion" label="发布版本" min-width="70" align="center">
          <template slot-scope="{ row }">{{ row.publishedVersion || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="60" align="center">
          <template slot-scope="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'" size="mini">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" min-width="230" align="center">
          <template slot-scope="{ row }">
            <el-button type="text" size="small" @click="$router.push('/model/' + row.id)">详情</el-button>
            <el-button type="text" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="text" size="small" @click="handlePublish(row)">{{ row.publishedVersion ? '重新发布' : '发布' }}</el-button>
            <el-button v-if="row.scope === 'PROJECT'" type="text" size="small" @click="handleToGlobal(row)">转为全局</el-button>
            <el-button type="text" size="small" class="btn-delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top:12px;text-align:right;" :current-page="qp.pageNum" :page-size="qp.pageSize" :total="total"
        layout="total,sizes,prev,pager,next" :page-sizes="[10,30,50,100]"
        @current-change="p=>{qp.pageNum=p;load()}" @size-change="s=>{qp.pageSize=s;qp.pageNum=1;load()}" />

    <!-- 上传模型对话框 -->
    <el-dialog title="上传模型" :visible.sync="uploadVisible" width="700px" :close-on-click-modal="false">
      <el-form ref="uploadForm" :model="uploadForm" :rules="uploadRules" label-width="120px" size="small">
        <el-form-item label="作用范围">
          <el-radio-group v-model="uploadForm.scope">
            <el-radio label="PROJECT">项目级</el-radio>
            <el-radio label="GLOBAL">全局</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="uploadForm.scope === 'PROJECT'" label="所属项目" prop="projectId">
          <el-select v-model="uploadForm.projectId" style="width:100%;" filterable clearable placeholder="请选择项目">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型编码" prop="modelCode">
          <el-input v-model="uploadForm.modelCode" placeholder="英文编码，如 credit_score_model" />
        </el-form-item>
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="uploadForm.modelName" placeholder="中文名称，如 信用评分模型" />
        </el-form-item>
        <el-form-item label="模型大类" prop="modelType">
          <el-select v-model="uploadForm.modelType" style="width:100%;">
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
          <el-upload ref="upload" action="#" :auto-upload="false" :limit="1" accept=".pmml,.xml,.onnx,.pb,.pkl,.pickle,.dill"
              :on-change="handleFileChange" :file-list="fileList">
            <el-button size="small" type="primary">选择文件</el-button>
            <span slot="tip" class="el-upload__tip">支持 PMML、PICKLE、DILL、ONNX 格式</span>
          </el-upload>
        </el-form-item>
        <el-form-item label="变更说明">
          <el-input v-model="uploadForm.changeLog" type="textarea" :rows="2" placeholder="简要描述本次变更内容" />
        </el-form-item>
        <el-form-item label="测试参数">
          <el-input v-model="uploadForm.testParams" type="textarea" :rows="3"
            placeholder='输入 JSON 测试参数，如 {"age": 30, "income": 5000}&#10;创建后可在此模型的测试对话框中自动加载' />
          <div style="color:#909399;font-size:11px;margin-top:2px;">可选。用于在模型测试时自动填充默认参数（JSON 格式）</div>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="uploadForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="uploadVisible=false">取消</el-button>
        <el-button size="small" type="primary" @click="handleDoUpload" :loading="uploading">上传</el-button>
      </div>
    </el-dialog>

    <!-- 转为全局模型对话框 -->
    <el-dialog title="转为全局模型" :visible.sync="toGlobalVisible" width="500px" :close-on-click-modal="false">
      <el-form ref="toGlobalForm" :model="toGlobalForm" :rules="toGlobalRules" label-width="110px" size="small">
        <el-form-item label="当前模型">{{ toGlobalModelInfo.modelName }}</el-form-item>
        <el-form-item label="当前编码">{{ toGlobalModelInfo.modelCode }}</el-form-item>
        <el-form-item label="新全局编码" prop="modelCode">
          <el-input v-model="toGlobalForm.modelCode" placeholder="重新填写全局唯一编码" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="toGlobalVisible=false">取消</el-button>
        <el-button size="small" type="primary" @click="handleDoToGlobal" :loading="toGlobalLoading">确认转换</el-button>
      </div>
    </el-dialog>

    <!-- 编辑模型对话框 -->
    <el-dialog title="编辑模型" :visible.sync="editVisible" width="600px" :close-on-click-modal="false">
      <el-form ref="editForm" :model="editForm" :rules="editRules" label-width="110px" size="small">
        <el-form-item label="模型编码">{{ editForm.modelCode }}</el-form-item>
        <el-form-item label="模型名称" prop="modelName">
          <el-input v-model="editForm.modelName" placeholder="模型中文名称" />
        </el-form-item>
        <el-form-item label="模型大类">{{ modelTypeLabel(editForm.modelType) }}</el-form-item>
        <el-form-item label="模型格式">{{ editForm.modelFormat }}</el-form-item>
        <el-form-item label="作用范围">{{ editForm.scope === 'GLOBAL' ? '全局' : '项目级' }}</el-form-item>
        <el-form-item label="目标类别">
          <el-input v-model="editForm.targetCategories" placeholder="分类模型的目标变量类别数，如 2" />
        </el-form-item>
        <el-form-item label="模型版本">
          <el-input v-model="editForm.modelVersion" placeholder="模型自身版本号" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="模型描述信息" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="editForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="editVisible=false">取消</el-button>
        <el-button size="small" type="primary" @click="handleDoEdit" :loading="editLoading">保存</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import * as api from '@/api/model'
import { listProjects } from '@/api/project'
import { clearPageState, restorePageState, savePageState } from '@/utils/pageStateCache'

const MODEL_TYPE_LABELS = {
  LR: 'LR（逻辑回归）',
  XGBOOST: 'XGBoost',
  LIGHTGBM: 'LightGBM',
  CATBOOST: 'CatBoost',
  RANDOM_FOREST: 'RandomForest',
  NEURAL_NET: 'NeuralNet（神经网络）',
  SVM: 'SVM'
}

export default {
  name: 'ModelList',
  data() {
    return {
      loading: false,
      models: [],
      total: 0,
      projects: [],
      projectList: [],
      projectListLoading: false,
      filteredProjectCodes: [],
      filteredProjectNames: [],

      qp: {
        pageNum: 1, pageSize: 10, scope: '', modelType: '', modelFormat: '',
        modelCode: '', modelName: '', projectCode: '', projectName: ''
      },

      // 模型编码/名称远程搜索
      modelCodeLoading: false, filteredModelCodes: [], allModelCodes: [],
      modelNameLoading: false, filteredModelNames: [], allModelNames: [],

      // 上传
      uploadVisible: false, uploading: false,
      uploadForm: { projectId: '', scope: 'PROJECT', modelCode: '', modelName: '', modelType: 'LR', description: '', changeLog: '', testParams: '' },
      uploadRules: {
        modelCode: [{
          required: true,
          message: '请输入模型编码',
          trigger: 'blur'
        }, {
          validator: (rule, value, callback) => {
            if (!value) { callback(); return }
            const pid = this.uploadForm.scope === 'PROJECT' ? (this.uploadForm.projectId || undefined) : undefined
            api.checkModelCode(value.trim(), this.uploadForm.scope, pid, undefined).then(res => {
              if (res.data === true) {
                callback(new Error(this.uploadForm.scope === 'GLOBAL' ? '该编码已被其他全局模型使用' : '该编码在当前项目内已存在，或与某个全局编码冲突'))
              } else {
                callback()
              }
            }).catch(() => callback())
          },
          trigger: 'blur'
        }],
        modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }],
        modelType: [{ required: true, message: '请选择模型大类', trigger: 'change' }]
      },
      fileList: [], selectedFile: null,

      // 转为全局模型
      toGlobalVisible: false, toGlobalLoading: false,
      toGlobalModelInfo: { id: null, modelCode: '', modelName: '' },
      toGlobalForm: { modelCode: '' },
      toGlobalRules: {
        modelCode: [{
          required: true,
          message: '请填写新的全局模型编码',
          trigger: 'blur'
        }, {
          validator: (rule, value, callback) => {
            if (!value) { callback(); return }
            api.checkModelCode(value.trim(), 'GLOBAL', undefined, this.toGlobalModelInfo.id || undefined).then(res => {
              if (res.data === true) {
                callback(new Error('该编码已被其他全局模型使用'))
              } else {
                callback()
              }
            }).catch(() => callback())
          },
          trigger: 'blur'
        }]
      },

      // 编辑模型
      editVisible: false, editLoading: false,
      editForm: { id: null, modelCode: '', modelName: '', modelType: '', modelFormat: '', scope: '', targetCategories: '', modelVersion: '', description: '', status: 1 },
      editRules: {
        modelName: [{ required: true, message: '请输入模型名称', trigger: 'blur' }]
      }
    }
  },
  created() {
    this.restoreCachedState()
    this.loadProjects()
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
        const list = (res.data && res.data.records) ? res.data.records : []
        this.projects = list
        this.projectList = list
        this.filteredProjectCodes = list.slice(0, 20)
        this.filteredProjectNames = list.slice(0, 20)
      } catch (e) { this.projects = []; this.projectList = [] }
    },
    queryProjectName(query) {
      if (!query) { this.filteredProjectNames = this.projectList.slice(0, 20); return }
      this.filteredProjectNames = this.projectList.filter(p =>
        p.projectName && p.projectName.toLowerCase().includes(query.toLowerCase())
      ).slice(0, 20)
    },
    queryProjectCode(query) {
      if (!query) { this.filteredProjectCodes = this.projectList.slice(0, 20); return }
      this.filteredProjectCodes = this.projectList.filter(p =>
        p.projectCode && p.projectCode.toLowerCase().includes(query.toLowerCase())
      ).slice(0, 20)
    },
    async load() {
      this.loading = true
      try {
        this.saveCachedState()
        const params = { ...this.qp }
        Object.keys(params).forEach(k => { if (!params[k]) delete params[k] })
        const res = await api.listModels(params)
        const data = res.data || {}
        this.models = data.records || []
        this.total = data.total || 0
        const codeSet = new Set(), nameSet = new Set()
        ;(data.records || []).forEach(r => {
          if (r.modelCode) codeSet.add(r.modelCode)
          if (r.modelName) nameSet.add(r.modelName)
        })
        this.allModelCodes = Array.from(codeSet); this.filteredModelCodes = this.allModelCodes.slice(0, 20)
        this.allModelNames = Array.from(nameSet); this.filteredModelNames = this.allModelNames.slice(0, 20)
      } catch (err) { this.$message.error('加载模型列表失败') } finally { this.loading = false }
    },
    handleQuery() { this.qp.pageNum = 1; this.load() },
    resetQuery() {
      this.qp = { pageNum: 1, pageSize: this.qp.pageSize, scope: '', modelType: '', modelFormat: '', modelCode: '', modelName: '', projectCode: '', projectName: '' }
      clearPageState('ModelList')
      this.load()
    },
    queryModelCode(q) {
      this.modelCodeLoading = true
      this.filteredModelCodes = q ? this.allModelCodes.filter(c => c && c.toLowerCase().includes(q.toLowerCase())).slice(0, 20) : this.allModelCodes.slice(0, 20)
      this.modelCodeLoading = false
    },
    queryModelName(q) {
      this.modelNameLoading = true
      this.filteredModelNames = q ? this.allModelNames.filter(n => n && n.toLowerCase().includes(q.toLowerCase())).slice(0, 20) : this.allModelNames.slice(0, 20)
      this.modelNameLoading = false
    },

    modelTypeLabel(t) { return MODEL_TYPE_LABELS[t] || t || '—' },

    handleUpload() {
      this.uploadForm = { projectId: '', scope: 'PROJECT', modelCode: '', modelName: '', modelType: 'LR', description: '', changeLog: '', testParams: '' }
      this.fileList = []; this.selectedFile = null
      this.uploadVisible = true
      this.$nextTick(() => { if (this.$refs.uploadForm) this.$refs.uploadForm.clearValidate() })
    },
    handleFileChange(file, files) {
      this.selectedFile = file.raw
      this.fileList = files.slice(-1)
    },
    handleDoUpload() {
      this.$refs.uploadForm.validate(async valid => {
        if (!valid) return
        if (!this.selectedFile) { this.$message.warning('请选择模型文件'); return }
        if (this.uploadForm.scope === 'PROJECT' && !this.uploadForm.projectId) { this.$message.warning('请选择所属项目'); return }
        this.uploading = true
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
          await api.uploadModel(formData)
          this.$message.success('上传成功')
          this.uploadVisible = false
          this.load()
        } catch (e) { this.$message.error(e.message || '上传失败') } finally { this.uploading = false }
      })
    },

    async handlePublish(row) {
      try {
        await this.$confirm('确定发布模型「' + row.modelName + '」？')
        await api.publishModel(row.id)
        this.$message.success('发布成功')
        this.load()
      } catch (e) { if (e !== 'cancel') this.$message.error(e.message || '发布失败') }
    },
    async handleDelete(row) {
      try {
        await this.$confirm('确定删除模型「' + row.modelName + '」？', '确认删除', { type: 'warning' })
        await api.deleteModel(row.id)
        this.$message.success('删除成功')
        this.load()
      } catch (e) { if (e !== 'cancel') this.$message.error(e.message || '删除失败') }
    },
    handleEdit(row) {
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
        status: row.status || 1
      }
      this.editVisible = true
      this.$nextTick(() => { if (this.$refs.editForm) this.$refs.editForm.clearValidate() })
    },
    handleDoEdit() {
      this.$refs.editForm.validate(async valid => {
        if (!valid) return
        this.editLoading = true
        try {
          await api.updateModel({
            id: this.editForm.id,
            modelName: this.editForm.modelName,
            description: this.editForm.description,
            targetCategories: this.editForm.targetCategories || null,
            modelVersion: this.editForm.modelVersion || null,
            status: this.editForm.status
          })
          this.$message.success('保存成功')
          this.editVisible = false
          this.load()
        } catch (e) { this.$message.error(e.message || '保存失败') } finally { this.editLoading = false }
      })
    },
    handleToGlobal(row) {
      this.toGlobalModelInfo = { id: row.id, modelCode: row.modelCode, modelName: row.modelName }
      this.toGlobalForm.modelCode = ''
      this.toGlobalVisible = true
      this.$nextTick(() => { if (this.$refs.toGlobalForm) this.$refs.toGlobalForm.clearValidate() })
    },
    handleDoToGlobal() {
      this.$refs.toGlobalForm.validate(async valid => {
        if (!valid) return
        this.toGlobalLoading = true
        try {
          await api.toGlobalModel(this.toGlobalModelInfo.id, this.toGlobalForm.modelCode.trim())
          this.$message.success('转换成功，该模型已转为全局模型')
          this.toGlobalVisible = false
          this.load()
        } catch (e) { this.$message.error(e.message || '转换失败') } finally { this.toGlobalLoading = false }
      })
    }
  }
}
</script>

<style scoped>
.linkage-hint { font-size:12px; color:#909399; margin-bottom:12px; padding:8px 12px; background:#f5f7fa; border-radius:4px; }
.tab-filter-row { display:flex; gap:8px; align-items:center; margin-bottom:12px; flex-wrap:wrap; }
.tab-empty { text-align:center; padding:48px 0; color:#c0c4cc; font-size:14px; }

/* 重置按钮点击后去除 focus 高亮 */
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

/* type="text" 按钮：始终保持纯文字样式 */
.el-button[type="text"],
.el-button[type="text"]:hover,
.el-button[type="text"]:focus,
.el-button[type="text"]:active,
.el-button[type="text"]:focus-visible {
  color: #606266 !important;
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
}
.el-button[type="text"]:hover { color: #1890ff !important; }
</style>
