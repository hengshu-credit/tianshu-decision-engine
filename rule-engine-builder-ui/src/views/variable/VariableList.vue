<template>
  <div class="uiue-list-page">
    <div class="linkage-hint">
      <i class="el-icon-info" /> 变量在规则设计时使用，<router-link to="/test">规则测试</router-link>中可加载项目变量作为入参。支持从 Java 实体类、JSON、建表 DDL 批量导入。
    </div>

    <div class="uiue-btn-bar">
      <div class="btn-right">
        <el-dropdown trigger="click" @command="handleImportCmd">
          <el-button size="small" type="primary" icon="el-icon-upload2">批量导入 <i class="el-icon-arrow-down el-icon--right" /></el-button>
          <el-dropdown-menu slot="dropdown">
            <el-dropdown-item command="java-entity" icon="el-icon-document">导入 Java 实体类</el-dropdown-item>
            <el-dropdown-item command="json-object" icon="el-icon-tickets">导入 JSON 对象</el-dropdown-item>
            <el-dropdown-item command="ddl-table" icon="el-icon-s-grid">导入 DDL 建表语句</el-dropdown-item>
            <el-dropdown-item command="java-const" icon="el-icon-coin" divided>导入 Java 常量类</el-dropdown-item>
            <el-dropdown-item command="json-const" icon="el-icon-price-tag">导入 JSON 常量</el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>
        <el-button size="small" icon="el-icon-plus" @click="handlePrimaryCreate">{{ primaryCreateLabel }}</el-button>
        <el-button size="small" icon="el-icon-video-play" type="warning" style="margin-left: 0;" @click="handleBatchValidate" :loading="validating">验证规则</el-button>
      </div>
    </div>

    <!-- Tabs -->
    <el-tabs v-model="activeTab" type="border-card" class="var-tabs" @tab-click="onTabClick">

      <!-- Tab 1: Variable List -->
      <el-tab-pane label="变量列表" name="list">
        <div class="tab-filter-row" @keyup.enter="handleQuery">
          <el-select v-model="qp.scope" clearable filterable placeholder="作用范围" size="mini" style="width:100px;"
            @change="handleQuery">
            <el-option label="全局" value="GLOBAL" />
            <el-option label="项目级" value="PROJECT" />
          </el-select>
          <remote-filter-select v-model="qp.projectCode" :fetch-options="fetchProjectCodeOptions" option-label-key="projectCode" option-value-key="projectCode" placeholder="项目编码" size="mini" style="width:110px;" />
          <remote-filter-select v-model="qp.projectName" :fetch-options="fetchProjectNameOptions" option-label-key="projectName" option-value-key="projectName" placeholder="项目名称" size="mini" style="width:130px;" />
          <el-select v-model="qp.varSource" clearable filterable placeholder="来源" size="mini" style="width:100px;"
            @change="handleQuery">
            <el-option label="输入参数" value="INPUT" />
            <el-option label="数据库查询" value="DB" />
            <el-option label="接口调用" value="API" />
            <el-option label="名单查询" value="LIST" />
            <el-option label="计算得出" value="COMPUTED" />
            <el-option label="常量" value="CONSTANT" />
          </el-select>
          <el-select v-model="qp.varType" clearable filterable placeholder="数据类型" size="mini" style="width:100px;"
            @change="handleQuery">
            <el-option v-for="opt in varTypeFilterOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <remote-filter-select v-model="qp.varCode" :fetch-options="fetchVarCodeOptions" option-label-key="varCode" option-value-key="varCode" allow-free-input placeholder="变量编码" size="mini" style="width:120px;" />
          <remote-filter-select v-model="qp.varLabel" :fetch-options="fetchVarLabelOptions" option-label-key="varLabel" option-value-key="varLabel" allow-free-input placeholder="变量名称" size="mini" style="width:120px;" />
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </div>

        <!-- 1. 普通变量（系统新增） -->
        <div v-if="standaloneVars.length > 0" class="var-list-section">
          <el-table :data="standaloneVars" border size="small" v-loading="loading" style="width:100%;">
            <el-table-column label="作用范围" width="90" align="center">
              <template slot-scope="{ row }">
                <el-tag :type="row.scope === 'GLOBAL' ? 'scope-global' : 'scope-project'" size="mini">{{ scopeTagLabel(row.scope) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="项目名称" min-width="120" show-overflow-tooltip>
              <template slot-scope="{ row }">{{ row.projectName || (row.scope === 'GLOBAL' ? '—' : '—') }}</template>
            </el-table-column>
            <el-table-column prop="varCode" label="变量编码" min-width="130" show-overflow-tooltip />
            <el-table-column prop="varLabel" label="变量名称" min-width="120" show-overflow-tooltip />
            <el-table-column label="脚本名称" min-width="130">
              <template slot-scope="{row}">
                <el-input v-model="row.scriptName" size="mini" placeholder="脚本名称" @blur="onVarScriptNameChange(row)" />
              </template>
            </el-table-column>
            <el-table-column prop="varType" label="类型" min-width="80" align="center">
              <template slot-scope="{ row }"><el-tag size="mini" :type="typeTagColor(row.varType)">{{ typeLabel(row.varType) }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="varSource" label="来源" min-width="80" align="center">
              <template slot-scope="{ row }">
                <el-tag size="mini" :type="sourceTagColor(row.varSource)">{{ sourceLabel(row.varSource) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="defaultValue" label="默认值" min-width="90" show-overflow-tooltip />
            <el-table-column prop="valueRange" label="取值范围" min-width="120" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" min-width="60" align="center">
              <template slot-scope="{ row }"><el-tag :type="row.status===1?'success':'info'" size="mini">{{ row.status===1?'启用':'停用' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" min-width="190" align="center">
              <template slot-scope="{ row }">
                <el-button type="text" size="small" @click="handleEdit(row)">编辑</el-button>
                <el-button v-if="isTestableSource(row)" type="text" size="small" @click="handleViewSourceDetail(row)">详情</el-button>
                <el-button v-if="isTestableSource(row)" type="text" size="small" @click="handleTestVariable(row)">测试</el-button>
                <el-button type="text" size="small" @click="handleOptions(row)" v-if="row.varType==='ENUM'">选项</el-button>
                <el-button type="text" size="small" class="btn-delete" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination style="margin-top:12px;text-align:right;" :current-page="qp.pageNum" :page-size="qp.pageSize" :total="standaloneTotal"
            layout="total,sizes,prev,pager,next" :page-sizes="[10,30,50,100,200,500]"
            @current-change="p=>{qp.pageNum=p;loadData()}" @size-change="s=>{qp.pageSize=s;qp.pageNum=1;loadData()}" />
        </div>

        <!-- 空状态 -->
        <div v-if="!loading && standaloneVars.length===0" class="tab-empty">
          暂无变量，可点击「新建变量」或「批量导入」添加
        </div>
      </el-tab-pane>

      <!-- Tab 2: Data Objects -->
      <el-tab-pane label="数据对象" name="objects">
        <div class="tab-filter-row" @keyup.enter="onObjFilterChange">
          <el-select v-model="objQp.scope" clearable filterable placeholder="作用范围" size="mini" style="width:100px;" @change="onObjFilterChange">
            <el-option label="全局" value="GLOBAL" />
            <el-option label="项目级" value="PROJECT" />
          </el-select>
          <remote-filter-select v-model="objQp.projectCode" :fetch-options="fetchProjectCodeOptions" option-label-key="projectCode" option-value-key="projectCode" placeholder="项目编码" size="mini" style="width:110px;" />
          <remote-filter-select v-model="objQp.projectName" :fetch-options="fetchProjectNameOptions" option-label-key="projectName" option-value-key="projectName" placeholder="项目名称" size="mini" style="width:130px;" />
          <el-select v-model="objQp.sourceType" clearable filterable placeholder="来源" size="mini" style="width:100px;" @change="onObjFilterChange">
            <el-option label="Java 实体" value="JAVA" />
            <el-option label="JSON" value="JSON" />
            <el-option label="DDL" value="DDL" />
            <el-option label="手动" value="MANUAL" />
          </el-select>
          <remote-filter-select v-model="objQp.objectCode" :fetch-options="fetchObjectCodeOptions" option-label-key="objectCode" option-value-key="objectCode" allow-free-input placeholder="对象名称" size="mini" style="width:130px;" />
          <el-button type="primary" @click="onObjFilterChange">查询</el-button>
          <el-button @click="resetObjQuery">重置</el-button>
        </div>
        <div v-if="filteredObjectTree.length===0 && !objLoading" class="tab-empty">暂无数据对象，点击「新建对象」或「批量导入」添加</div>
        <div v-else v-loading="objLoading">
          <div v-for="node in paginatedObjectTree" :key="node.object.id" class="var-group-card">
            <div class="var-group-header" @click="toggleObjectExpand(node)">
              <i :class="node._expanded ? 'el-icon-arrow-down' : 'el-icon-arrow-right'" class="expand-icon" />
              <span class="var-group-code">{{ node.object.objectCode }}</span>
              <span v-if="node.object.objectLabel && node.object.objectLabel !== node.object.objectCode" class="var-group-label">{{ node.object.objectLabel }}</span>
              <el-tag size="mini" :type="node.object.scope === 'GLOBAL' ? 'scope-global' : 'scope-project'" style="margin-left:4px;">{{ node.object.scope === 'GLOBAL' ? '全局' : '项目' }}</el-tag>
              <span v-if="getProjectName(node.object.projectId)" style="font-size:12px;color:#888;margin-left:2px;">{{ getProjectName(node.object.projectId) }}</span>
            </div>
            <div class="var-group-toolbar">
              <el-input v-model="node.object.scriptName" size="mini" placeholder="脚本名称" style="width:130px;margin-left:6px;" @blur="onObjectScriptNameChange(node.object)" @click.native.stop />
              <el-select v-model="node.object.objectType" size="mini" style="width:100px;" @change="onObjectTypeChange(node.object)" @click.native.stop>
                <el-option label="输入对象" value="INPUT" /><el-option label="输出对象" value="OUTPUT" /><el-option label="输入输出" value="INOUT" />
              </el-select>
              <el-tag size="mini" :type="objTypeColor(node.object.objectType)">{{ objTypeLabel(node.object.objectType) }}</el-tag>
              <el-tag size="mini" type="info" v-if="node.object.sourceType">{{ node.object.sourceType }}</el-tag>
              <span class="var-group-count">{{ countObjectFields(node.variables) }} 个字段</span>
              <el-button type="text" size="small" icon="el-icon-edit" @click.stop="handleEditObject(node.object)">编辑</el-button>
              <el-button type="text" size="small" icon="el-icon-plus" style="margin-left:auto;" @click.stop="handleAddObjectField(node)">添加字段</el-button>
              <el-button type="text" size="small" icon="el-icon-delete" class="btn-delete" @click.stop="handleDeleteObject(node.object)" />
            </div>
            <div v-show="node._expanded" class="var-group-body">
              <el-table
                :data="paginatedObjectFields(node)"
                size="mini"
                border
                row-key="id"
                :tree-props="{ children: 'children' }"
                style="width:100%;"
              >
                <el-table-column prop="varCode" label="字段编码" min-width="140" show-overflow-tooltip />
                <el-table-column prop="varLabel" label="名称" min-width="120" show-overflow-tooltip />
                <el-table-column label="脚本名称" min-width="140">
                  <template slot-scope="{row}">
                    <el-input v-model="row.scriptName" size="mini" placeholder="脚本名称" @blur="onObjectFieldScriptNameBlur(row)" />
                  </template>
                </el-table-column>
                <el-table-column prop="varType" label="类型" min-width="80" align="center">
                  <template slot-scope="{row}"><el-tag size="mini" :type="typeTagColor(row.varType)">{{ typeLabel(row.varType) }}</el-tag></template>
                </el-table-column>
                <el-table-column prop="refObjectCode" label="引用对象" min-width="110" show-overflow-tooltip>
                  <template slot-scope="{row}">
                    <span v-if="row.refObjectId && objectIdMap[row.refObjectId]" class="badge badge-obj">{{ objectIdMap[row.refObjectId] }}</span>
                    <span v-else-if="row.refObjectCode" class="badge badge-obj">{{ row.refObjectCode }}</span>
                    <span v-else style="color:#ccc;">—</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="140" align="center">
                  <template slot-scope="{ row }">
                    <el-button type="text" size="small" @click="handleEditObjectField(row, node)">编辑</el-button>
                    <el-button type="text" size="small" @click="handleOptions(row, true)" v-if="row.varType==='ENUM'">选项</el-button>
                    <el-button type="text" size="small" class="btn-delete" @click="handleDeleteObjectField(row)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-pagination
                v-if="objectFieldNeedsPaging(node)"
                style="margin-top:8px;text-align:right;"
                :current-page="objectFieldPage(node)"
                :page-size="objectFieldPageSize"
                :total="objectFieldTotal(node)"
                layout="total,prev,pager,next"
                @current-change="p => handleObjectFieldPageChange(node, p)"
              />
            </div>
          </div>
          <el-pagination style="margin-top:12px;text-align:right;" :current-page="objPageNum" :page-size="objPageSize" :total="filteredObjectTree.length"
            layout="total,prev,pager,next" @current-change="handleObjPageChange" />
        </div>
      </el-tab-pane>

      <!-- Tab 3: 常量列表（与变量列表相同分页模型，必须有默认值） -->
      <el-tab-pane label="常量列表" name="constants">
        <div class="tab-filter-row" @keyup.enter="handleConstQuery">
          <el-select v-model="constQp.scope" clearable filterable placeholder="作用范围" size="mini" style="width:100px;" @change="handleConstQuery">
            <el-option label="全局" value="GLOBAL" />
            <el-option label="项目级" value="PROJECT" />
          </el-select>
          <remote-filter-select v-model="constQp.projectCode" :fetch-options="fetchProjectCodeOptions" option-label-key="projectCode" option-value-key="projectCode" placeholder="项目编码" size="mini" style="width:110px;" />
          <remote-filter-select v-model="constQp.projectName" :fetch-options="fetchProjectNameOptions" option-label-key="projectName" option-value-key="projectName" placeholder="项目名称" size="mini" style="width:130px;" />
          <el-select v-model="constQp.varType" clearable filterable placeholder="数据类型" size="mini" style="width:100px;" @change="handleConstQuery">
            <el-option v-for="opt in varTypeFilterOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <remote-filter-select v-model="constQp.varCode" :fetch-options="fetchConstCodeOptions" option-label-key="varCode" option-value-key="varCode" allow-free-input placeholder="常量编码" size="mini" style="width:120px;" />
          <remote-filter-select v-model="constQp.varLabel" :fetch-options="fetchConstLabelOptions" option-label-key="varLabel" option-value-key="varLabel" allow-free-input placeholder="常量名称" size="mini" style="width:120px;" />
          <el-button type="primary" @click="handleConstQuery">查询</el-button>
          <el-button @click="resetConstQuery">重置</el-button>
        </div>
        <el-table v-loading="constLoading" :data="constantRows" border size="small" style="width:100%;" empty-text="暂无可用常量">
          <el-table-column label="作用范围" width="90" align="center">
            <template slot-scope="{ row }">
              <el-tag :type="row.scope === 'GLOBAL' ? 'scope-global' : 'scope-project'" size="mini">{{ row.scope === 'GLOBAL' ? '全局' : '项目级' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="项目名称" min-width="120" show-overflow-tooltip>
            <template slot-scope="{ row }">{{ getProjectName(row.projectId) || (row.scope === 'GLOBAL' ? '—' : '—') }}</template>
          </el-table-column>
          <el-table-column prop="varCode" label="常量编码" min-width="130" show-overflow-tooltip />
          <el-table-column prop="varLabel" label="常量名称" min-width="120" show-overflow-tooltip />
          <el-table-column label="脚本名称" min-width="130">
            <template slot-scope="{row}"><code>{{ row.scriptName || row.varCode }}</code></template>
          </el-table-column>
          <el-table-column prop="varType" label="类型" min-width="80" align="center">
            <template slot-scope="{ row }"><el-tag size="mini" :type="typeTagColor(row.varType)">{{ typeLabel(row.varType) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="常量值（默认）" min-width="160">
            <template slot-scope="{row}"><code>{{ formatConstantValue(row.defaultValue, row.varType) }}</code></template>
          </el-table-column>
          <el-table-column prop="status" label="状态" min-width="60" align="center">
            <template slot-scope="{ row }"><el-tag :type="row.status===1?'success':'info'" size="mini">{{ row.status===1?'启用':'停用' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" min-width="120" align="center">
            <template slot-scope="{ row }">
              <el-button type="text" size="small" @click="handleEdit(row)">编辑</el-button>
              <el-button type="text" size="small" class="btn-delete" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination style="margin-top:12px;text-align:right;" :current-page="constQp.pageNum" :page-size="constQp.pageSize" :total="constantTotal"
          layout="total,sizes,prev,pager,next" :page-sizes="[10,30,50,100]"
          @current-change="p=>{constQp.pageNum=p;loadConstants()}" @size-change="s=>{constQp.pageSize=s;constQp.pageNum=1;loadConstants()}" />
      </el-tab-pane>
    </el-tabs>

    <!-- Create/Edit Variable Dialog -->
    <el-dialog :title="variableDialogTitle" :visible.sync="dialogVisible" :width="form.varSource === 'LIST' ? '760px' : '600px'" :close-on-click-modal="false">
      <el-form ref="form" :model="form" :rules="rules" label-width="120px" size="small">
        <el-form-item v-if="!form.id && isObjectField && objectFieldParentId" label="所属数据对象">
          <span class="text-muted">{{ getObjectCode(objectFieldParentId) }}</span>
        </el-form-item>
        <el-form-item v-if="!isObjectField" label="作用范围">
          <el-select v-model="form.scope" placeholder="选择作用范围" style="width:100%;" @change="onVarScopeChange">
            <el-option label="🌐 全局（所有项目可用）" value="GLOBAL" />
            <el-option label="📁 项目级" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isObjectField && form.scope === 'PROJECT'" label="项目名称" prop="projectId">
          <el-select v-model="form.projectId" placeholder="请选择项目" style="width:100%;" filterable clearable @change="onVariableProjectChange">
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="变量编码" prop="varCode">
          <el-input v-model="form.varCode" placeholder="英文标识，如 taxAmount" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="变量名称" prop="varLabel">
          <el-input v-model="form.varLabel" placeholder="中文名称，如 应纳税额" />
        </el-form-item>
        <el-form-item label="数据类型" prop="varType">
          <el-select v-model="form.varType" style="width:100%;" popper-append-to-body>
            <el-option v-for="opt in varTypeFormOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!isObjectField" label="来源">
          <el-select v-model="form.varSource" :disabled="isConstantCreate" clearable placeholder="可选" style="width:100%;" @change="onVarSourceChange">
            <el-option label="输入参数" value="INPUT" /><el-option label="数据库查询" value="DB" />
            <el-option label="接口调用" value="API" /><el-option label="名单查询" value="LIST" />
            <el-option label="计算得出" value="COMPUTED" /><el-option label="常量" value="CONSTANT" />
          </el-select>
        </el-form-item>
        <template v-if="!isObjectField && form.varSource === 'API'">
          <el-form-item label="接口配置">
            <el-select v-model="form.apiConfigId" filterable clearable placeholder="选择接口" style="width:100%;">
              <el-option
                v-for="api in apiConfigOptions"
                :key="api.id"
                :label="api.apiName ? api.apiName + ' / ' + api.apiCode : api.apiCode"
                :value="api.id"
              />
            </el-select>
            <div class="field-help">接口入参由外数 API 的“请求参数/请求体”统一配置，这里只选择要调用的 API。</div>
          </el-form-item>
          <el-form-item label="结果路径">
            <el-input v-model="form.apiResultPath" placeholder="body.data.score" />
            <div class="field-help">从 API 映射后的返回中读取，例如 <code>body.score</code>；读取全部映射结果可填 <code>body</code>。</div>
          </el-form-item>
          <el-form-item label="异常策略">
            <el-select v-model="form.apiExceptionStrategy" style="width:180px;">
              <el-option label="抛出异常" value="ERROR" />
              <el-option label="返回默认值" value="RETURN_DEFAULT" />
              <el-option label="跳过补值" value="SKIP" />
            </el-select>
            <el-switch v-model="form.apiForceRefresh" active-text="强制刷新" style="margin-left:12px;" />
          </el-form-item>
          <el-form-item v-if="form.apiExceptionStrategy === 'RETURN_DEFAULT'" label="兜底值">
            <el-input v-model="form.apiFallbackValue" placeholder="可为空" />
          </el-form-item>
        </template>
        <template v-if="!isObjectField && form.varSource === 'DB'">
          <el-form-item label="数据库源">
            <el-select v-model="form.dbDatasourceId" filterable clearable placeholder="选择数据库" style="width:100%;">
              <el-option
                v-for="db in dbDatasourceOptions"
                :key="db.id"
                :label="db.datasourceName ? db.datasourceName + ' / ' + db.datasourceCode : db.datasourceCode"
                :value="db.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="查询 SQL">
            <monaco-editor v-model="form.dbSql" language="sql" theme="rule-sql-light" height="130px" />
          </el-form-item>
          <el-form-item label="SQL 参数">
            <monaco-editor v-model="form.dbParams" language="json" height="90px" />
          </el-form-item>
          <el-form-item label="结果路径">
            <el-input v-model="form.dbResultPath" placeholder="0.score" />
          </el-form-item>
          <el-form-item label="异常策略">
            <el-input-number v-model="form.dbMaxRows" :min="1" :max="500" />
            <el-select v-model="form.dbExceptionStrategy" style="width:180px;margin-left:8px;">
              <el-option label="抛出异常" value="ERROR" />
              <el-option label="返回默认值" value="RETURN_DEFAULT" />
              <el-option label="跳过补值" value="SKIP" />
            </el-select>
            <el-switch v-model="form.dbForceRefresh" active-text="强制刷新" style="margin-left:12px;" />
          </el-form-item>
          <el-form-item v-if="form.dbExceptionStrategy === 'RETURN_DEFAULT'" label="兜底值">
            <el-input v-model="form.dbFallbackValue" placeholder="可为空" />
          </el-form-item>
        </template>
        <template v-if="!isObjectField && form.varSource === 'LIST'">
          <el-form-item label="名单库（多选）">
            <el-select v-model="form.listIds" multiple collapse-tags filterable clearable placeholder="选择一个或多个名单库" style="width:100%;">
              <el-option
                v-for="item in listLibraryOptions"
                :key="item.id"
                :label="item.listName ? item.listName + ' / ' + item.listCode : item.listCode"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="查询表达式">
            <div v-for="(operand, index) in form.listQueryOperands" :key="index" class="list-query-row">
              <operand-picker
                :value="operand"
                :vars="listReferenceOptions"
                :functions="listFunctionOptions"
                :list-options="listLibraryOptions"
                :allowed-kinds="listQueryOperandKinds"
                context="LIST_QUERY_VALUE"
                :placeholder="'选择第 ' + (index + 1) + ' 个查询字段或表达式'"
                editor-title="配置名单查询表达式"
                @input="value => setListQueryOperand(index, value)"
              />
              <el-button type="text" class="list-query-remove" :disabled="form.listQueryOperands.length <= 1" @click="removeListQueryOperand(index)">删除</el-button>
            </div>
            <el-button type="text" icon="el-icon-plus" @click="addListQueryOperand">增加查询表达式</el-button>
            <div class="field-help">每一项都可组合字段、函数、阈值和运算符；引用按字段 ID 保存。</div>
          </el-form-item>
          <el-form-item label="组合模式">
            <el-select v-model="form.listCombinationMode" style="width:100%;">
              <el-option v-for="opt in listCombinationModeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <div class="field-help">{{ listCombinationDescription }}</div>
          </el-form-item>
          <el-form-item label="名单匹配">
            <el-select v-model="form.listMatchMode" style="width:100%;">
              <el-option v-for="opt in listMatchModeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="内容类型">
            <el-select v-model="form.listItemTypes" multiple clearable collapse-tags placeholder="不选表示任意类型" style="width:100%;">
              <el-option v-for="opt in listItemTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="返回模式">
            <el-radio-group v-model="form.listReturnMode" @change="onListReturnModeChange">
              <el-radio-button label="NUMBER">命中 1 / 未命中 0</el-radio-button>
              <el-radio-button label="BOOLEAN">true / false</el-radio-button>
            </el-radio-group>
            <div class="field-help">返回模式会同步当前变量的数据类型，避免运行时类型不一致。</div>
          </el-form-item>
        </template>
        <el-form-item v-if="!isObjectField" label="默认值">
          <el-input v-model="form.defaultValue" :placeholder="form.varSource==='CONSTANT' ? '常量必填' : '可选'" />
        </el-form-item>
        <el-form-item v-if="isObjectField && (form.varType==='OBJECT' || form.varType==='LIST')" label="引用对象编码">
          <el-input v-model="form.refObjectCode" placeholder="如嵌套对象编码" />
        </el-form-item>
        <el-form-item v-if="!isObjectField" label="取值范围"><el-input v-model="form.valueRange" placeholder="如：0~100、A/B/C" /></el-form-item>
        <el-form-item v-if="!isObjectField" label="示例值"><el-input v-model="form.exampleValue" placeholder="如：15000.50" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" :max="9999" /></el-form-item>
        <el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" /></el-form-item>
        <el-form-item v-if="!isObjectField" label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="dialogVisible=false">取消</el-button>
        <el-button size="small" type="primary" @click="handleSubmit">确定</el-button>
      </div>
    </el-dialog>

    <!-- Create/Edit Data Object Dialog -->
    <el-dialog :title="objectDialogTitle" :visible.sync="objectDialogVisible" width="560px" :close-on-click-modal="false">
      <el-form ref="objForm" :model="objectForm" :rules="objectRules" label-width="110px" size="small">
        <el-form-item label="对象编码" prop="objectCode">
          <el-input v-model="objectForm.objectCode" placeholder="英文标识，如 TaxRequest" :disabled="!!objectForm.id" />
        </el-form-item>
        <el-form-item label="对象名称" prop="objectLabel">
          <el-input v-model="objectForm.objectLabel" placeholder="中文名称，如 税务请求" />
        </el-form-item>
        <el-form-item label="脚本名称" prop="scriptName">
          <el-input v-model="objectForm.scriptName" placeholder="QLExpress 脚本中的引用名，如 taxRequest" />
        </el-form-item>
        <el-form-item label="作用范围">
          <el-select v-model="objectForm.scope" style="width:100%;" @change="onObjScopeChange">
            <el-option label="🌐 全局（所有项目可用）" value="GLOBAL" />
            <el-option label="📁 项目级" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="objectForm.scope === 'PROJECT'" label="所属项目" prop="projectId">
          <el-select v-model="objectForm.projectId" placeholder="请选择项目" style="width:100%;" filterable>
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象类型">
          <el-radio-group v-model="objectForm.objectType">
            <el-radio label="INPUT">输入对象</el-radio>
            <el-radio label="OUTPUT">输出对象</el-radio>
            <el-radio label="INOUT">输入输出</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="来源类型">
          <el-select v-model="objectForm.sourceType" style="width:100%;">
            <el-option label="Java 实体" value="JAVA" />
            <el-option label="JSON" value="JSON" />
            <el-option label="DDL" value="DDL" />
            <el-option label="手动" value="MANUAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="objectForm.description" type="textarea" :rows="2" placeholder="对象说明" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="objectDialogVisible=false">取消</el-button>
        <el-button size="small" type="primary" @click="handleObjectSubmit">确定</el-button>
      </div>
    </el-dialog>

    <!-- Enum Options Dialog -->
    <el-dialog title="枚举选项管理" :visible.sync="optionDialogVisible" width="600px" :close-on-click-modal="false">
      <div style="margin-bottom:12px;">
        <span style="font-weight:bold;">{{ currentVar ? currentVar.varLabel : '' }}</span>
        <span style="color:#999;margin-left:8px;">{{ currentVar ? currentVar.varCode : '' }}</span>
      </div>
      <el-table :data="optionList" border size="small" style="width:100%;">
        <el-table-column label="选项值" min-width="160">
          <template slot-scope="{row}"><el-input v-model="row.optionValue" size="mini" placeholder="选项值" /></template>
        </el-table-column>
        <el-table-column label="选项标签（中文）" min-width="180">
          <template slot-scope="{row}"><el-input v-model="row.optionLabel" size="mini" placeholder="中文标签" /></template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template slot-scope="{$index}"><el-button type="text" size="small" style="color:#F76E6C;" @click="optionList.splice($index,1)">移除</el-button></template>
        </el-table-column>
      </el-table>
      <el-button type="text" size="small" icon="el-icon-plus" style="margin-top:8px;" @click="optionList.push({optionValue:'',optionLabel:'',sortOrder:optionList.length})">添加选项</el-button>
      <div slot="footer">
        <el-button size="small" @click="optionDialogVisible=false">取消</el-button>
        <el-button size="small" type="primary" @click="handleSaveOptions">保存选项</el-button>
      </div>
    </el-dialog>

    <el-dialog title="变量取数测试" :visible.sync="variableTestVisible" width="760px" :close-on-click-modal="false">
      <div v-if="testTarget" class="test-target">
        <el-tag size="mini" :type="sourceTagColor(testTarget.varSource)">{{ sourceLabel(testTarget.varSource) }}</el-tag>
        <span>{{ testTarget.varLabel || testTarget.varCode }}</span>
        <code>{{ testTarget.scriptName || testTarget.varCode }}</code>
      </div>
      <el-alert
        :title="testDialogHint"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom:12px;"
      />
      <monaco-editor v-model="variableTestParamsText" language="json" height="220px" />
      <div v-if="variableTestResult" class="test-result-block">
        <div class="test-result-title">测试结果</div>
        <pre class="test-result-pre">{{ formatJson(variableTestResult) }}</pre>
      </div>
      <div slot="footer">
        <el-button size="small" @click="variableTestVisible=false">关闭</el-button>
        <el-button size="small" type="primary" :loading="variableTesting" @click="doTestVariable">执行测试</el-button>
      </div>
    </el-dialog>

    <el-dialog title="变量取数详情" :visible.sync="sourceDetailVisible" width="800px" :close-on-click-modal="false">
      <template v-if="sourceDetailTarget">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="变量编码">{{ sourceDetailTarget.varCode }}</el-descriptions-item>
          <el-descriptions-item label="变量名称">{{ sourceDetailTarget.varLabel }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ sourceLabel(sourceDetailTarget.varSource) }}</el-descriptions-item>
          <el-descriptions-item label="脚本名">{{ sourceDetailTarget.scriptName || sourceDetailTarget.varCode }}</el-descriptions-item>
        </el-descriptions>
        <div class="source-summary-card">
          <div class="source-summary-title">{{ sourceBusinessTitle(sourceDetailTarget) }}</div>
          <div class="source-summary-desc">{{ sourceBusinessDesc(sourceDetailTarget) }}</div>
          <div class="source-summary-grid">
            <div v-for="item in sourceSummaryRows(sourceDetailTarget)" :key="item.label" class="source-summary-item">
              <div class="source-summary-label">{{ item.label }}</div>
              <div class="source-summary-value">{{ item.value }}</div>
            </div>
          </div>
        </div>
        <div class="source-detail-section">
          <div class="source-detail-title">依赖输入字段</div>
          <el-table :data="sourceInputFields(sourceDetailTarget)" border size="small" empty-text="未配置依赖输入字段">
            <el-table-column prop="field" label="输入字段" min-width="160" show-overflow-tooltip />
            <el-table-column prop="usage" label="业务用途" min-width="150" show-overflow-tooltip />
            <el-table-column prop="expression" label="取值来源" min-width="220" show-overflow-tooltip />
          </el-table>
        </div>
        <el-collapse class="source-tech-collapse">
          <el-collapse-item title="技术配置（排查时查看）" name="raw">
            <monaco-editor :value="formatJson(parseJson(sourceDetailTarget.sourceConfig, {}))" language="json" height="220px" read-only />
          </el-collapse-item>
        </el-collapse>
      </template>
      <div slot="footer">
        <el-button size="small" @click="sourceDetailVisible=false">关闭</el-button>
      </div>
    </el-dialog>

    <!-- Java Entity Import Dialog -->
    <el-dialog title="导入 Java 实体类" :visible.sync="importJavaEntityVisible" width="700px" :close-on-click-modal="false">
      <el-form size="small" label-width="100px">
        <el-form-item label="作用范围">
          <el-radio-group v-model="importForm.scope">
            <el-radio label="GLOBAL">全局（所有项目可用）</el-radio>
            <el-radio label="PROJECT">项目级</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="importForm.scope === 'PROJECT'" label="选择项目" required>
          <el-select v-model="importForm.projectId" placeholder="请选择项目" style="width:100%;" filterable>
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象类型">
          <el-radio-group v-model="importForm.objectType">
            <el-radio label="INPUT">输入对象</el-radio><el-radio label="OUTPUT">输出对象</el-radio><el-radio label="INOUT">输入输出</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="Java 源码">
          <monaco-editor v-model="importForm.javaSource" language="java" height="320px" />
        </el-form-item>
        <el-form-item label="或上传文件">
          <el-upload action="" :before-upload="handleJavaFileSelect" :show-file-list="false" accept=".java">
            <el-button size="small" icon="el-icon-upload">选择 .java 文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="importJavaEntityVisible=false">取消</el-button>
        <el-button size="small" type="primary" :loading="importing" @click="doImportJavaEntity">导入</el-button>
      </div>
    </el-dialog>

    <!-- JSON Object Import Dialog -->
    <el-dialog title="导入 JSON 对象" :visible.sync="importJsonObjectVisible" width="700px" :close-on-click-modal="false">
      <el-form size="small" label-width="100px">
        <el-form-item label="作用范围">
          <el-radio-group v-model="importForm.scope">
            <el-radio label="GLOBAL">全局（所有项目可用）</el-radio>
            <el-radio label="PROJECT">项目级</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="importForm.scope === 'PROJECT'" label="选择项目" required>
          <el-select v-model="importForm.projectId" placeholder="请选择项目" style="width:100%;" filterable>
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象编码"><el-input v-model="importForm.objectCode" placeholder="如 TaxRequest" /></el-form-item>
        <el-form-item label="对象类型">
          <el-radio-group v-model="importForm.objectType">
            <el-radio label="INPUT">输入对象</el-radio><el-radio label="OUTPUT">输出对象</el-radio><el-radio label="INOUT">输入输出</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="JSON 样本">
          <monaco-editor v-model="importForm.jsonContent" language="json" height="320px" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="importJsonObjectVisible=false">取消</el-button>
        <el-button size="small" type="primary" :loading="importing" @click="doImportJsonObject">导入</el-button>
      </div>
    </el-dialog>

    <!-- DDL Import Dialog -->
    <el-dialog title="导入 DDL 建表语句" :visible.sync="importDdlVisible" width="720px" :close-on-click-modal="false">
      <el-form size="small" label-width="100px">
        <el-form-item label="作用范围">
          <el-radio-group v-model="importForm.scope">
            <el-radio label="GLOBAL">全局（所有项目可用）</el-radio>
            <el-radio label="PROJECT">项目级</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="importForm.scope === 'PROJECT'" label="选择项目" required>
          <el-select v-model="importForm.projectId" placeholder="请选择项目" style="width:100%;" filterable>
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象类型">
          <el-radio-group v-model="importForm.objectType">
            <el-radio label="INPUT">输入对象</el-radio><el-radio label="OUTPUT">输出对象</el-radio><el-radio label="INOUT">输入输出</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="建表 DDL">
          <monaco-editor v-model="importForm.ddlSource" language="sql" theme="rule-sql-light" height="320px" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="importDdlVisible=false">取消</el-button>
        <el-button size="small" type="primary" :loading="importing" @click="doImportDdl">导入</el-button>
      </div>
    </el-dialog>

    <!-- Java Constants Import Dialog -->
    <el-dialog title="导入 Java 常量类" :visible.sync="importJavaConstVisible" width="700px" :close-on-click-modal="false">
      <el-form size="small" label-width="100px">
        <el-form-item label="作用范围">
          <el-radio-group v-model="importForm.scope">
            <el-radio label="GLOBAL">全局（所有项目可用）</el-radio>
            <el-radio label="PROJECT">项目级</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="importForm.scope === 'PROJECT'" label="选择项目" required>
          <el-select v-model="importForm.projectId" placeholder="请选择项目" style="width:100%;" filterable>
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Java 源码">
          <monaco-editor v-model="importForm.javaSource" language="java" height="320px" />
        </el-form-item>
        <el-form-item label="或上传文件">
          <el-upload action="" :before-upload="handleJavaFileSelect" :show-file-list="false" accept=".java">
            <el-button size="small" icon="el-icon-upload">选择 .java 文件</el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="importJavaConstVisible=false">取消</el-button>
        <el-button size="small" type="primary" :loading="importing" @click="doImportJavaConst">导入</el-button>
      </div>
    </el-dialog>

    <!-- JSON Constants Import Dialog -->
    <el-dialog title="导入 JSON 常量" :visible.sync="importJsonConstVisible" width="700px" :close-on-click-modal="false">
      <el-form size="small" label-width="100px">
        <el-form-item label="作用范围">
          <el-radio-group v-model="importForm.scope">
            <el-radio label="GLOBAL">全局（所有项目可用）</el-radio>
            <el-radio label="PROJECT">项目级</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="importForm.scope === 'PROJECT'" label="选择项目" required>
          <el-select v-model="importForm.projectId" placeholder="请选择项目" style="width:100%;" filterable>
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="JSON 数据">
          <monaco-editor v-model="importForm.jsonContent" language="json" height="320px" />
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="importJsonConstVisible=false">取消</el-button>
        <el-button size="small" type="primary" :loading="importing" @click="doImportJsonConst">导入</el-button>
      </div>
    </el-dialog>

    <!-- Validation Results Dialog -->
    <el-dialog title="规则验证结果" :visible.sync="validateVisible" width="700px">
      <el-table :data="validateResults" size="small" border>
        <el-table-column prop="ruleName" label="规则名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="ruleCode" label="规则编码" min-width="120" show-overflow-tooltip />
        <el-table-column prop="modelType" label="模型" min-width="70" align="center" />
        <el-table-column label="编译" min-width="60" align="center">
          <template slot-scope="{row}"><el-tag :type="row.compileOk?'success':'danger'" size="mini">{{ row.compileOk?'通过':'失败' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="执行" min-width="60" align="center">
          <template slot-scope="{row}"><el-tag :type="row.executeOk?'success':'danger'" size="mini">{{ row.executeOk?'通过':'失败' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="错误信息" min-width="200" show-overflow-tooltip />
      </el-table>
      <div slot="footer"><el-button size="small" @click="validateVisible=false">关闭</el-button></div>
    </el-dialog>

    <!-- Batch Validate Dialog -->
    <el-dialog title="验证规则" :visible.sync="validateDialogVisible" width="500px" :close-on-click-modal="false">
      <el-form size="small" label-width="100px">
        <el-form-item label="选择项目">
          <el-select v-model="validateProjectId" placeholder="请选择项目（不选择则验证所有规则）" style="width:100%;" filterable clearable>
            <el-option v-for="p in projects" :key="p.id" :label="p.projectName" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <span style="color:#909399;font-size:12px;">不选择项目则验证所有规则</span>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="validateDialogVisible=false">取消</el-button>
        <el-button size="small" type="primary" :loading="validating" @click="doBatchValidate">开始验证</el-button>
      </div>
    </el-dialog>

    <!-- Import Result Dialog -->
    <el-dialog title="导入结果" :visible.sync="importResultVisible" width="500px">
      <div class="import-result-body">
        <i class="el-icon-success" style="font-size:48px;color:#67C23A;" />
        <h3>导入完成</h3>
        <p v-if="importResult.objectCount != null">创建/更新 <b>{{ importResult.objectCount }}</b> 个数据对象，<b>{{ importResult.variableCount }}</b> 个变量</p>
        <p v-if="importResult.constantCount != null">创建/更新 <b>{{ importResult.constantCount }}</b> 个常量</p>
      </div>
      <div slot="footer">
        <el-button size="small" type="primary" icon="el-icon-video-play" @click="importResultVisible=false;validateProjectId=importForm.scope==='PROJECT'?importForm.projectId:null;validateDialogVisible=true">验证项目规则</el-button>
        <el-button size="small" @click="importResultVisible=false">关闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listVariables, listVariablesByProject, createVariable, updateVariable, deleteVariable, testVariable, getVariableOptions, saveVariableOptions, importJavaConstants, importJsonConstants } from '@/api/variable'
import { listProjects } from '@/api/project'
import { getRuleTestSchema } from '@/api/definition'
import request from '@/api/request'
import { importJavaEntity, importJsonObject, importDdlTable, updateObjectType, updateObjectScriptName, deleteDataObject, batchValidateRules, createDataObjectField, updateDataObjectField, deleteDataObjectField, getDataObjectFieldOptions, saveDataObjectFieldOptions, createOrUpdateDataObject, getVariableTree } from '@/api/dataObject'
import { listApiConfigs } from '@/api/datasource'
import { listDbDatasources } from '@/api/database'
import { listLibraries } from '@/api/ruleList'
import { listAllFunctionsByProject } from '@/api/function'
import { listAllModelsByProject } from '@/api/model'
import { VAR_TYPE_FILTER_OPTIONS, VAR_TYPE_FORM_OPTIONS, varTypeLabel, varTypeTagColor } from '@/constants/varTypes'
import { LIST_COMBINATION_MODES, LIST_ITEM_TYPES, LIST_MATCH_MODES, listCombinationMode } from '@/constants/listMatchModes'
import { getExpressionContext } from '@/constants/expressionContexts'
import { formatConstantValue, hasConstantValue } from '@/utils/constantValue'
import { clearPageState, restorePageState, savePageState } from '@/utils/pageStateCache'
import { collectReferencePaths, collectReferencePathsFromText, sampleValueForVarType, setPathValue } from '@/utils/testParamTemplate'
import { normalizeTestSchema } from '@/utils/testSchema'
import { normalizeTestResult } from '@/utils/testResult'
import { cloneOperand, collectOperandReferences, operandDisplay, validateOperand } from '@/utils/operand'
import { buildPickerOptions, buildReferenceCatalog } from '@/utils/referenceCatalog'
import MonacoEditor from '@/components/MonacoEditor'
import RemoteFilterSelect from '@/components/RemoteFilterSelect.vue'
import OperandPicker from '@/components/common/OperandPicker.vue'

export default {
  name: 'VariableList',
  components: { MonacoEditor, OperandPicker, RemoteFilterSelect },
  data() {
    return {
      activeTab: 'list',
      currentProjectId: '',
      projects: [],
      loading: false,
      tableData: [],
      total: 0,
      qp: { pageNum: 1, pageSize: 10, projectId: '', varType: '', varSource: '', keyword: '', scope: '', projectCode: '', projectName: '', varCode: '', varLabel: '' },

      projectList: [],
      projectListLoading: false,
      filteredProjectCodes: [],
      filteredProjectNames: [],

      // 变量编码/名称远程搜索
      varCodeLoading: false,
      varLabelLoading: false,
      allVarCodes: [],
      allVarLabels: [],
      filteredVarCodes: [],
      filteredVarLabels: [],

      // 常量编码/名称远程搜索
      constCodeLoading: false,
      constLabelLoading: false,
      allConstCodes: [],
      allConstLabels: [],
      filteredConstCodes: [],
      filteredConstLabels: [],

      // 数据对象名称远程搜索
      objectNameLoading: false,
      allObjectNames: [],
      filteredObjectNames: [],

        dialogVisible: false,
        form: this.initForm(),
        apiConfigOptions: [],
        dbDatasourceOptions: [],
        listLibraryOptions: [],
        listReferenceOptions: [],
        listFunctionOptions: [],
        listReferenceProjectId: null,
        sourceOptionsLoaded: { API: false, DB: false, LIST: false },
        listItemTypeOptions: LIST_ITEM_TYPES,
        listCombinationModeOptions: LIST_COMBINATION_MODES,
        listMatchModeOptions: LIST_MATCH_MODES,
        listQueryOperandKinds: getExpressionContext('LIST_QUERY_VALUE').allowedKinds,
        rules: {
        varCode: [{ required: true, message: '请输入变量编码', trigger: 'blur' }],
        varLabel: [{ required: true, message: '请输入变量名称', trigger: 'blur' }],
        varType: [{ required: true, message: '请选择数据类型', trigger: 'change' }]
      },

      optionDialogVisible: false,
      currentVar: null,
      optionList: [],
      variableTestVisible: false,
      variableTesting: false,
      testTarget: null,
      variableTestParamsText: '{}',
      variableTestResult: null,
      sourceDetailVisible: false,
      sourceDetailTarget: null,

      // Data objects
      objLoading: false,
      objectTree: [],
      objectMap: {},

      // 常量列表（分页，与变量接口相同）
      constLoading: false,
      constQp: { pageNum: 1, pageSize: 10, keyword: '', varType: '', scope: '', projectCode: '', projectName: '', varCode: '', varLabel: '' },
      constantRows: [],
      constantTotal: 0,

      /** 弹窗：编辑数据对象字段（非 rule_variable） */
      isObjectField: false,
      objectFieldParentId: null,
      /** 从常量 Tab 打开新建，锁定来源为 CONSTANT */
      isConstantCreate: false,
      /** 枚举选项弹窗：当前为对象字段上的 ENUM */
      optionTargetIsField: false,

      // Data Object Dialog
      objectDialogVisible: false,
      objectForm: { id: null, objectCode: '', objectLabel: '', scriptName: '', scope: 'PROJECT', projectId: '', objectType: 'INPUT', sourceType: 'MANUAL', description: '' },
      objectRules: {
        objectCode: [{ required: true, message: '请输入对象编码', trigger: 'blur' }],
        objectLabel: [{ required: true, message: '请输入对象名称', trigger: 'blur' }],
        projectId: [{ required: true, message: '请选择所属项目', trigger: 'change' }]
      },

      // Import
      importing: false,
      importForm: { objectType: 'INPUT', javaSource: '', jsonContent: '', ddlSource: '', objectCode: '', scope: 'GLOBAL', projectId: '' },
      importJavaEntityVisible: false,
      importJsonObjectVisible: false,
      importDdlVisible: false,
      importJavaConstVisible: false,
      importJsonConstVisible: false,
      importResultVisible: false,
      importResult: {},

      // Validation
      validating: false,
      validateVisible: false,
      validateResults: [],
      validateDialogVisible: false,
      validateProjectId: '',

      varTypeFilterOptions: VAR_TYPE_FILTER_OPTIONS,
      varTypeFormOptions: VAR_TYPE_FORM_OPTIONS,

      // 数据对象 tab 分页与展开
      objPageNum: 1,
      objPageSize: 10,
      objExpanded: {},
      objectFieldPageMap: {},
      objectFieldPageSize: 100,
      objQp: { scope: '', projectCode: '', projectName: '', sourceType: '', objectCode: '' },
      /** 铁律四：id → objectCode 映射，供 refObjectId 展示引用对象名 */
      objectIdMap: {},

    }
  },
  created() {
    this.restoreCachedState()
    this.loadProjects()
  },
  mounted() {
    this.loadData()
    this.loadObjectTree()
    this.loadConstants()
  },
  computed: {
    standaloneVars() {
      return this.tableData || []
    },
    standaloneTotal() {
      return this.total
    },
    paginatedObjectTree() {
      const list = this.filteredObjectTree || []
      const start = (this.objPageNum - 1) * this.objPageSize
      return list.slice(start, start + this.objPageSize).map(n => ({
        ...n,
        _expanded: this.objExpanded[n.object.id] === true
      }))
    },
    filteredObjectTree() {
      const { scope, projectCode, projectName, sourceType, objectCode } = this.objQp
      return (this.objectTree || []).filter(node => {
        const obj = node.object
        if (scope && obj.scope !== scope) return false
        if (projectCode && obj.projectCode && !obj.projectCode.toLowerCase().includes(projectCode.toLowerCase())) return false
        if (projectName && obj.projectName && !obj.projectName.toLowerCase().includes(projectName.toLowerCase())) return false
        if (sourceType && obj.sourceType !== sourceType) return false
        if (objectCode && obj.objectCode && !obj.objectCode.toLowerCase().includes(objectCode.toLowerCase())) return false
        return true
      })
    },
    primaryCreateLabel() {
      if (this.activeTab === 'constants') return '新建常量'
      if (this.activeTab === 'objects') return '新建对象'
      return '新建变量'
    },
    /** 变量/对象字段/常量 弹窗标题 */
    variableDialogTitle() {
      if (this.isObjectField) return this.form.id ? '编辑对象字段' : '添加对象字段'
      if (this.form.id) return '编辑变量'
      if (this.isConstantCreate) return '新建常量'
      return '新建变量'
    },
    /** 数据对象弹窗标题 */
    objectDialogTitle() {
      return this.objectForm.id ? '编辑数据对象' : '新建数据对象'
    },
    testDialogHint() {
      if (this.testTarget && this.testTarget.varSource === 'API') {
        return '已优先加载外数 API 中保存的测试样例；没有样例时会根据接口请求参数生成完整入参 JSON。'
      }
      if (this.testTarget && this.testTarget.varSource === 'DB') {
        return '请输入数据库查询变量依赖的上下文字段，系统会按 SQL 参数顺序取值。'
      }
      if (this.testTarget && this.testTarget.varSource === 'LIST') {
        return '请输入名单查询字段对应的上下文值，系统会按名单匹配方式返回命中结果。'
      }
      return '请输入该变量取数所需的上下文字段。'
    },
    listCombinationDescription() {
      return listCombinationMode(this.form.listCombinationMode).description
    }
  },
  methods: {
    onTabClick(tab) {
      this.saveCachedState()
      if (tab.name === 'objects') this.loadObjectTree()
      if (tab.name === 'constants') this.loadConstants()
    },
    restoreCachedState() {
      const state = restorePageState('VariableList')
      if (state.activeTab) this.activeTab = state.activeTab
      if (state.qp) this.qp = { ...this.qp, ...state.qp }
      if (state.constQp) this.constQp = { ...this.constQp, ...state.constQp }
      if (state.objQp) this.objQp = { ...this.objQp, ...state.objQp }
      if (state.objPageNum) this.objPageNum = state.objPageNum
      if (state.objPageSize) this.objPageSize = state.objPageSize
      if (state.objectFieldPageMap) this.objectFieldPageMap = state.objectFieldPageMap
      if (state.objExpanded) this.objExpanded = state.objExpanded
    },
    saveCachedState() {
      savePageState('VariableList', {
        activeTab: this.activeTab,
        qp: this.qp,
        constQp: this.constQp,
        objQp: this.objQp,
        objPageNum: this.objPageNum,
        objPageSize: this.objPageSize,
        objectFieldPageMap: this.objectFieldPageMap,
        objExpanded: this.objExpanded
      })
    },
    initForm() {
      return {
        id: null,
        scope: 'GLOBAL',
        projectId: '',
          varCode: '',
          varLabel: '',
          varType: 'STRING',
          varSource: 'INPUT',
          sourceConfig: '',
          apiConfigId: '',
          apiParamMapping: '{}',
          apiResultPath: 'body',
          apiForceRefresh: false,
          apiExceptionStrategy: 'ERROR',
          apiFallbackValue: '',
          dbDatasourceId: '',
          dbSql: '',
          dbParams: '[]',
          dbResultPath: '',
          dbMaxRows: 1,
          dbForceRefresh: false,
          dbExceptionStrategy: 'ERROR',
          dbFallbackValue: '',
          listIds: [],
          listQueryOperands: [null],
          listCombinationMode: 'ANY_FIELD_ANY_LIST',
          listMatchMode: 'IN_LIST',
          listItemTypes: [],
          listReturnMode: 'NUMBER',
          refObjectCode: '',
          defaultValue: '',
          valueRange: '',
          exampleValue: '',
        sortOrder: 0,
        status: 1,
        description: ''
        }
      },
      async onVarSourceChange(source) {
        if (source === 'LIST') this.onListReturnModeChange(this.form.listReturnMode)
        await this.loadVariableSourceOptions(source)
      },
      async onVariableProjectChange() {
        this.listReferenceProjectId = null
        if (this.form.varSource === 'LIST') await this.loadListExpressionOptions()
      },
      async loadVariableSourceOptions(source) {
        if (source === 'API' && !this.sourceOptionsLoaded.API) {
          try {
            const res = await listApiConfigs({ pageNum: 1, pageSize: 1000, status: 1 })
            this.apiConfigOptions = (res.data && res.data.records) || []
            this.sourceOptionsLoaded.API = true
          } catch (e) {
            this.apiConfigOptions = []
          }
        }
        if (source === 'DB' && !this.sourceOptionsLoaded.DB) {
          try {
            const res = await listDbDatasources({ pageNum: 1, pageSize: 1000, status: 1 })
            this.dbDatasourceOptions = (res.data && res.data.records) || []
            this.sourceOptionsLoaded.DB = true
          } catch (e) {
            this.dbDatasourceOptions = []
          }
        }
        if (source === 'LIST' && !this.sourceOptionsLoaded.LIST) {
          try {
            const res = await listLibraries({ pageNum: 1, pageSize: 1000, status: 1 })
            this.listLibraryOptions = (res.data && res.data.records) || []
            this.sourceOptionsLoaded.LIST = true
          } catch (e) {
            this.listLibraryOptions = []
          }
        }
        if (source === 'LIST') await this.loadListExpressionOptions()
      },
      normalizeSourceList(res) {
        const data = res && res.data !== undefined ? res.data : res
        if (Array.isArray(data)) return data
        return data && Array.isArray(data.records) ? data.records : []
      },
      async loadListExpressionOptions() {
        const projectId = this.form.scope === 'GLOBAL' ? 0 : this.form.projectId
        if (projectId === '' || projectId == null) {
          this.listReferenceOptions = []
          this.listFunctionOptions = []
          return
        }
        if (String(this.listReferenceProjectId) === String(projectId) && this.listReferenceOptions.length) return
        try {
          const [variableRes, objectRes, functionRes, modelRes] = await Promise.all([
            listVariablesByProject(projectId),
            getVariableTree(projectId),
            listAllFunctionsByProject(projectId),
            listAllModelsByProject(projectId)
          ])
          const objectData = objectRes && objectRes.data !== undefined ? objectRes.data : objectRes
          const objectTree = Array.isArray(objectData) ? objectData : ((objectData && objectData.tree) || [])
          const catalog = buildReferenceCatalog(
            this.normalizeSourceList(variableRes),
            objectTree,
            this.normalizeSourceList(modelRes)
          )
          this.listReferenceOptions = buildPickerOptions(catalog).filter(option => {
            return !(this.form.id != null && option._refType === 'VARIABLE' && String(option._varId) === String(this.form.id))
          })
          this.listFunctionOptions = this.normalizeSourceList(functionRes)
          this.listReferenceProjectId = projectId
        } catch (e) {
          this.listReferenceOptions = []
          this.listFunctionOptions = []
          this.$message.warning('名单查询字段加载失败，请检查项目后重试')
        }
      },
      applySourceConfigToForm() {
        const config = this.parseJsonSafe(this.form.sourceConfig, {})
        if (this.form.varSource === 'API') {
          this.form.apiConfigId = config.apiConfigId || ''
          this.form.apiParamMapping = this.stringifyConfig(config.paramMapping || {})
          this.form.apiResultPath = config.resultPath || 'body'
          this.form.apiForceRefresh = config.forceRefresh === true
          this.form.apiExceptionStrategy = config.exceptionStrategy || 'ERROR'
          this.form.apiFallbackValue = config.fallbackValue != null ? String(config.fallbackValue) : ''
        } else if (this.form.varSource === 'DB') {
          this.form.dbDatasourceId = config.datasourceId || ''
          this.form.dbSql = config.sql || ''
          this.form.dbParams = this.stringifyConfig(config.params || [])
          this.form.dbResultPath = config.resultPath || ''
          this.form.dbMaxRows = config.maxRows || 1
          this.form.dbForceRefresh = config.forceRefresh === true
          this.form.dbExceptionStrategy = config.exceptionStrategy || 'ERROR'
          this.form.dbFallbackValue = config.fallbackValue != null ? String(config.fallbackValue) : ''
        } else if (this.form.varSource === 'LIST') {
          this.form.listIds = Array.isArray(config.listIds) ? config.listIds.slice() : []
          this.form.listQueryOperands = Array.isArray(config.queryOperands) && config.queryOperands.length
            ? config.queryOperands.map(cloneOperand)
            : [null]
          this.form.listCombinationMode = config.combinationMode || 'ANY_FIELD_ANY_LIST'
          this.form.listMatchMode = config.matchMode || 'IN_LIST'
          this.form.listItemTypes = Array.isArray(config.itemTypes) ? config.itemTypes.slice() : []
          this.form.listReturnMode = config.returnMode || 'NUMBER'
          this.onListReturnModeChange(this.form.listReturnMode)
        }
      },
      parseJsonSafe(text, fallback) {
        if (!text || !String(text).trim()) return fallback
        try {
          return JSON.parse(text)
        } catch (e) {
          return fallback
        }
      },
      stringifyConfig(value) {
        return JSON.stringify(value == null ? {} : value, null, 2)
      },
      buildVariablePayload() {
        const payload = { ...this.form }
        if (payload.varSource === 'API') {
          if (!payload.apiConfigId) {
            this.$message.warning('请选择接口配置')
            return null
          }
          payload.sourceConfig = JSON.stringify({
            apiConfigId: payload.apiConfigId,
            resultPath: payload.apiResultPath || 'body',
            forceRefresh: payload.apiForceRefresh === true,
            exceptionStrategy: payload.apiExceptionStrategy || 'ERROR',
            fallbackValue: payload.apiFallbackValue || null
          })
        } else if (payload.varSource === 'DB') {
          if (!payload.dbDatasourceId) {
            this.$message.warning('请选择数据库源')
            return null
          }
          if (!payload.dbSql || !payload.dbSql.trim()) {
            this.$message.warning('请输入查询 SQL')
            return null
          }
          const params = this.parseSourceJson(payload.dbParams, 'SQL 参数', [])
          if (params == null) return null
          if (!Array.isArray(params)) {
            this.$message.warning('SQL 参数必须是 JSON 数组')
            return null
          }
          payload.sourceConfig = JSON.stringify({
            datasourceId: payload.dbDatasourceId,
            sql: payload.dbSql,
            params,
            resultPath: payload.dbResultPath || '',
            maxRows: payload.dbMaxRows || 1,
            forceRefresh: payload.dbForceRefresh === true,
            exceptionStrategy: payload.dbExceptionStrategy || 'ERROR',
            fallbackValue: payload.dbFallbackValue || null
          })
        } else if (payload.varSource === 'LIST') {
          if (!Array.isArray(payload.listIds) || !payload.listIds.length) {
            this.$message.warning('请至少选择一个名单库')
            return null
          }
          if (!Array.isArray(payload.listQueryOperands) || !payload.listQueryOperands.length) {
            this.$message.warning('请至少配置一个名单查询表达式')
            return null
          }
          for (let index = 0; index < payload.listQueryOperands.length; index++) {
            const errors = validateOperand(payload.listQueryOperands[index], { allowedKinds: this.listQueryOperandKinds })
            if (errors.length) {
              this.$message.warning('第 ' + (index + 1) + ' 个名单查询表达式：' + errors[0].message)
              return null
            }
          }
          payload.sourceConfig = JSON.stringify({
            listIds: payload.listIds.slice(),
            queryOperands: payload.listQueryOperands.map(cloneOperand),
            combinationMode: payload.listCombinationMode || 'ANY_FIELD_ANY_LIST',
            matchMode: payload.listMatchMode || 'IN_LIST',
            itemTypes: payload.listItemTypes || [],
            returnMode: payload.listReturnMode || 'NUMBER'
          })
          payload.varType = payload.listReturnMode === 'BOOLEAN' ? 'BOOLEAN' : 'NUMBER'
        } else {
          payload.sourceConfig = null
        }
        this.removeSourceFormFields(payload)
        return payload
      },
      parseSourceJson(text, label, fallback = {}) {
        if (!text || !String(text).trim()) return fallback
        try {
          return JSON.parse(text)
        } catch (e) {
          this.$message.warning(label + '必须是合法 JSON')
          return null
        }
    },
    removeSourceFormFields(payload) {
      const sourceFormFields = ['apiConfigId', 'apiParamMapping', 'apiResultPath', 'apiForceRefresh', 'apiExceptionStrategy', 'apiFallbackValue',
        'dbDatasourceId', 'dbSql', 'dbParams', 'dbResultPath', 'dbMaxRows', 'dbForceRefresh', 'dbExceptionStrategy', 'dbFallbackValue',
        'listIds', 'listQueryOperands', 'listCombinationMode', 'listMatchMode', 'listItemTypes', 'listReturnMode'
      ]
      sourceFormFields.forEach(key => { delete payload[key] })
    },
    setListQueryOperand(index, operand) {
      this.$set(this.form.listQueryOperands, index, cloneOperand(operand))
    },
    addListQueryOperand() {
      this.form.listQueryOperands.push(null)
    },
    removeListQueryOperand(index) {
      if (this.form.listQueryOperands.length <= 1) return
      this.form.listQueryOperands.splice(index, 1)
    },
    onListReturnModeChange(mode) {
      this.form.varType = mode === 'BOOLEAN' ? 'BOOLEAN' : 'NUMBER'
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
    getProjectName(pid) {
      if (!pid) return ''
      const p = this.projectList.find(x => x.id === pid)
      return p ? p.projectName : ''
    },
    fetchProjectCodeOptions({ query, pageNum, pageSize }) {
      return listProjects({ pageNum, pageSize, projectCode: query || '' })
    },
    fetchProjectNameOptions({ query, pageNum, pageSize }) {
      return listProjects({ pageNum, pageSize, projectName: query || '' })
    },
    fetchVarCodeOptions({ query, pageNum, pageSize }) {
      return listVariables({ ...this.qp, pageNum, pageSize, standaloneOnly: true, varCode: query || '' })
    },
    fetchVarLabelOptions({ query, pageNum, pageSize }) {
      return listVariables({ ...this.qp, pageNum, pageSize, standaloneOnly: true, varLabel: query || '' })
    },
    fetchConstCodeOptions({ query, pageNum, pageSize }) {
      return listVariables({ ...this.constQp, pageNum, pageSize, varSource: 'CONSTANT', varCode: query || '' })
    },
    fetchConstLabelOptions({ query, pageNum, pageSize }) {
      return listVariables({ ...this.constQp, pageNum, pageSize, varSource: 'CONSTANT', varLabel: query || '' })
    },
    fetchObjectCodeOptions({ query, pageNum, pageSize }) {
      return request.get('/rule/dataobject/page', {
        params: {
          ...this.objQp,
          pageNum,
          pageSize,
          objectCode: query || ''
        }
      })
    },
    queryProjectCode(query) {
      if (!query) {
        this.filteredProjectCodes = this.projectList.slice(0, 20)
        return
      }
      this.filteredProjectCodes = this.projectList.filter(p =>
        p.projectCode && p.projectCode.toLowerCase().includes(query.toLowerCase())
      ).slice(0, 20)
    },
    queryProjectName(query) {
      if (!query) {
        this.filteredProjectNames = this.projectList.slice(0, 20)
        return
      }
      this.filteredProjectNames = this.projectList.filter(p =>
        p.projectName && p.projectName.toLowerCase().includes(query.toLowerCase())
      ).slice(0, 20)
    },
    queryVarCode(query) {
      this.varCodeLoading = true
      if (!query) {
        this.filteredVarCodes = this.allVarCodes.slice(0, 20)
        this.varCodeLoading = false
        return
      }
      this.filteredVarCodes = this.allVarCodes.filter(v => v && v.toLowerCase().includes(query.toLowerCase())).slice(0, 20)
      this.varCodeLoading = false
    },
    queryVarLabel(query) {
      this.varLabelLoading = true
      if (!query) {
        this.filteredVarLabels = this.allVarLabels.slice(0, 20)
        this.varLabelLoading = false
        return
      }
      this.filteredVarLabels = this.allVarLabels.filter(v => v && v.toLowerCase().includes(query.toLowerCase())).slice(0, 20)
      this.varLabelLoading = false
    },
    queryConstCode(query) {
      this.constCodeLoading = true
      if (!query) {
        this.filteredConstCodes = this.allConstCodes.slice(0, 20)
        this.constCodeLoading = false
        return
      }
      this.filteredConstCodes = this.allConstCodes.filter(v => v && v.toLowerCase().includes(query.toLowerCase())).slice(0, 20)
      this.constCodeLoading = false
    },
    queryConstLabel(query) {
      this.constLabelLoading = true
      if (!query) {
        this.filteredConstLabels = this.allConstLabels.slice(0, 20)
        this.constLabelLoading = false
        return
      }
      this.filteredConstLabels = this.allConstLabels.filter(v => v && v.toLowerCase().includes(query.toLowerCase())).slice(0, 20)
      this.constLabelLoading = false
    },
    queryObjectName(query) {
      this.objectNameLoading = true
      if (!query) {
        this.filteredObjectNames = this.allObjectNames.slice(0, 20)
        this.objectNameLoading = false
        return
      }
      this.filteredObjectNames = this.allObjectNames.filter(o => o && o.toLowerCase().includes(query.toLowerCase())).slice(0, 20)
      this.objectNameLoading = false
    },
    toggleObjectExpand(node) {
      this.$set(this.objExpanded, node.object.id, !this.objExpanded[node.object.id])
      this.saveCachedState()
    },
    handleObjPageChange(p) { this.objPageNum = p; this.saveCachedState() },
    objectFieldTotal(node) {
      return this.countObjectFields(node && node.variables)
    },
    objectFieldNeedsPaging(node) {
      return this.objectFieldTotal(node) > this.objectFieldPageSize
    },
    objectFieldPage(node) {
      const id = node && node.object ? node.object.id : null
      return (id && this.objectFieldPageMap[id]) || 1
    },
    paginatedObjectFields(node) {
      const rows = node && Array.isArray(node.variables) ? node.variables : []
      if (!this.objectFieldNeedsPaging(node)) return rows
      const page = this.objectFieldPage(node)
      const start = (page - 1) * this.objectFieldPageSize
      return this.sliceObjectFieldTree(rows, start, start + this.objectFieldPageSize)
    },
    sliceObjectFieldTree(rows, start, end) {
      let index = 0
      const visit = list => {
        return (list || []).map(row => {
          const current = index++
          const children = visit(row.children || [])
          const includeSelf = current >= start && current < end
          if (!includeSelf && !children.length) return null
          return {
            ...row,
            children
          }
        }).filter(Boolean)
      }
      return visit(rows)
    },
    handleObjectFieldPageChange(node, page) {
      const id = node && node.object ? node.object.id : null
      if (!id) return
      this.$set(this.objectFieldPageMap, id, page)
      this.saveCachedState()
    },
    normalizeObjectFieldPages() {
      const next = {}
      const tree = this.objectTree || []
      tree.forEach(node => {
        const id = node && node.object ? node.object.id : null
        if (!id) return
        const total = this.objectFieldTotal(node)
        const max = Math.max(1, Math.ceil(total / this.objectFieldPageSize))
        const page = this.objectFieldPageMap[id] || 1
        next[id] = Math.min(Math.max(page, 1), max)
      })
      this.objectFieldPageMap = next
    },
    countObjectFields(rows) {
      if (!Array.isArray(rows)) return 0
      return rows.reduce((sum, row) => sum + 1 + this.countObjectFields(row.children), 0)
    },
    async loadData() {
      this.loading = true
      try {
        this.saveCachedState()
        const params = { ...this.qp, standaloneOnly: true }
        if (!params.projectId) delete params.projectId
        if (!params.varType) delete params.varType
        if (!params.keyword) delete params.keyword
        if (!params.scope) delete params.scope
        if (!params.projectCode) delete params.projectCode
        if (!params.projectName) delete params.projectName
        if (!params.varSource) delete params.varSource
        if (!params.varCode) delete params.varCode
        if (!params.varLabel) delete params.varLabel
        const res = await listVariables(params)
        const data = res.data || {}
        this.tableData = data.records || []
        this.total = data.total != null ? data.total : 0
        // 加载变量编码/名称列表供筛选下拉
        const codeSet = new Set(), labelSet = new Set()
        ;(data.records || []).forEach(r => { if (r.varCode) codeSet.add(r.varCode); if (r.varLabel) labelSet.add(r.varLabel) })
        this.allVarCodes = Array.from(codeSet)
        this.allVarLabels = Array.from(labelSet)
        this.filteredVarCodes = this.allVarCodes.slice(0, 20)
        this.filteredVarLabels = this.allVarLabels.slice(0, 20)
      } catch (err) {
        this.$message.error('加载变量列表失败')
        this.tableData = []; this.total = 0
      } finally { this.loading = false }
    },
    handleQuery() { this.qp.pageNum = 1; this.loadData() },
    resetQuery() { this.qp = { pageNum: 1, pageSize: this.qp.pageSize, projectId: '', varType: '', varSource: '', keyword: '', scope: '', projectCode: '', projectName: '', varCode: '', varLabel: '' }; clearPageState('VariableList'); this.loadData() },

    // ── Objects ──
    async loadObjectTree() {
      this.objLoading = true
      try {
        this.saveCachedState()
        const buildParams = () => {
          const p = {}
          const { scope, projectCode, projectName, sourceType, objectCode } = this.objQp
          if (scope) p.scope = scope
          if (projectCode) p.projectCode = projectCode
          if (projectName) p.projectName = projectName
          if (sourceType) p.sourceType = sourceType
          if (objectCode) p.objectCode = objectCode
          return p
        }
        let res = await request.get('/rule/dataobject/tree', { params: buildParams() })
        // 铁律四：API 返回 { tree, objectIdMap } 结构
        let rawData = res.data || {}
        let tree = rawData.tree || []
        // 铁律四：构建 id→objectCode 映射，供前端展示 refObjectId 对应的对象名
        this.objectIdMap = rawData.objectIdMap || {}
        this.objectTree = tree
        this.normalizeObjectFieldPages()
        this.objectMap = {}
        this.objectTree.forEach(n => { this.objectMap[n.object.id] = n.object })
        // 加载对象名称列表供筛选下拉
        const nameSet = new Set()
        tree.forEach(n => { if (n.object.objectCode) nameSet.add(n.object.objectCode) })
        this.allObjectNames = Array.from(nameSet)
        this.filteredObjectNames = this.allObjectNames.slice(0, 20)
      } catch (e) { this.objectTree = []; this.objectIdMap = {} }
      finally { this.objLoading = false }
    },
    async onObjectTypeChange(obj) {
      await updateObjectType(obj.id, obj.objectType)
      this.$message.success('对象类型已更新')
    },
    async onObjectScriptNameChange(obj) {
      await updateObjectScriptName(obj.id, obj.scriptName)
    },
    /**
     * 列表行内修改脚本名：变量走 variable 接口；数据对象字段走 dataobject field 接口。
     */
    async onVarScriptNameChange(row) {
      if (row.objectField) {
        await this.onObjectFieldScriptNameBlur(row)
        return
    }
      await updateVariable(row)
    },
    /**
     * 数据对象表格中字段脚本名失焦保存。
     */
    async onObjectFieldScriptNameBlur(row) {
      await updateDataObjectField({
        id: row.id,
        projectId: row.projectId,
        objectId: row.objectId,
        varCode: row.varCode,
        varLabel: row.varLabel,
        scriptName: row.scriptName,
        varType: row.varType,
        refObjectCode: row.refObjectCode || null,
        refObjectId: row.refObjectId || null,
        genericType: row.genericType || null,
        parentFieldId: row.parentFieldId || null,
        sortOrder: row.sortOrder,
        status: row.status
      })
    },
    async handleDeleteObject(obj) {
      await this.$confirm(`确定删除对象「${obj.objectCode}」及其所有变量？`, '确认删除', { type: 'warning' })
      await deleteDataObject(obj.id)
      this.$message.success('删除成功')
      this.objPageNum = 1
      this.loadObjectTree()
      this.loadData()
    },
    /** 新建数据对象（从工具栏按钮） */
    handleCreateObject() {
      this.objectForm = { id: null, objectCode: '', objectLabel: '', scriptName: '', scope: this.currentProjectId ? 'PROJECT' : 'GLOBAL', projectId: this.currentProjectId || '', objectType: 'INPUT', sourceType: 'MANUAL', description: '' }
      this.objectDialogVisible = true
      this.$nextTick(() => { if (this.$refs.objForm) this.$refs.objForm.clearValidate() })
    },
    /** 编辑数据对象（从行内操作） */
    handleEditObject(obj) {
      this.objectForm = { id: obj.id, objectCode: obj.objectCode, objectLabel: obj.objectLabel || '', scriptName: obj.scriptName || '', scope: obj.scope || 'PROJECT', projectId: obj.projectId || '', objectType: obj.objectType || 'INPUT', sourceType: obj.sourceType || 'MANUAL', description: obj.description || '' }
      this.objectDialogVisible = true
      this.$nextTick(() => { if (this.$refs.objForm) this.$refs.objForm.clearValidate() })
    },
    /** 切换数据对象 scope 时清空项目 */
    onObjScopeChange(val) {
      if (val === 'GLOBAL') this.objectForm.projectId = ''
    },
    /** 提交数据对象表单 */
    handleObjectSubmit() {
      this.$refs.objForm.validate(async (valid) => {
        if (!valid) return
        if (this.objectForm.scope === 'PROJECT' && !this.objectForm.projectId) {
          this.$message.warning('请选择所属项目')
          return
        }
        try {
          await createOrUpdateDataObject(this.objectForm)
          this.$message.success('操作成功')
          this.objectDialogVisible = false
          this.objPageNum = 1
          this.loadObjectTree()
        } catch (e) {
          this.$message.error('保存失败: ' + (e.message || ''))
        }
      })
    },
    /**
     * 在指定数据对象下新建字段（写入 rule_data_object_field）。
     */
    handleAddObjectField(node) {
      const obj = node.object
      const nextOrder = (node.variables && node.variables.length) ? node.variables.length : 0
      this.isObjectField = true
      this.isConstantCreate = false
      this.objectFieldParentId = obj.id
      this.form = {
        id: null,
        projectId: this.currentProjectId,
        varCode: '',
        varLabel: '',
        scriptName: '',
        varType: 'STRING',
        refObjectCode: '',
        sortOrder: nextOrder,
        status: 1
      }
      this.dialogVisible = true
      this.$nextTick(() => { if (this.$refs.form) this.$refs.form.clearValidate() })
    },
    /**
     * 编辑数据对象下的字段行。
     */
    handleEditObjectField(row, node) {
      this.isObjectField = true
      this.isConstantCreate = false
      this.objectFieldParentId = node.object.id
      this.form = {
        id: row.id,
        projectId: row.projectId,
        varCode: row.varCode,
        varLabel: row.varLabel,
        scriptName: row.scriptName,
        varType: row.varType || 'STRING',
        refObjectCode: row.refObjectCode || '',
        refObjectId: row.refObjectId || null,
        genericType: row.genericType || '',
        parentFieldId: row.parentFieldId || null,
        sortOrder: row.sortOrder != null ? row.sortOrder : 0,
        status: row.status != null ? row.status : 1
      }
      this.dialogVisible = true
      this.$nextTick(() => { if (this.$refs.form) this.$refs.form.clearValidate() })
    },
    /**
     * 删除数据对象字段。
     */
    async handleDeleteObjectField(row) {
      await this.$confirm(`确定删除字段「${row.varLabel || row.varCode}」？`, '确认删除', { type: 'warning' })
      await deleteDataObjectField(row.id)
      this.$message.success('删除成功')
      this.loadObjectTree()
    },
    getObjectCode(objectId) {
      const obj = this.objectMap[objectId]
      return obj ? obj.objectCode : objectId
    },

    // ── 常量列表（分页） ──
    async loadConstants() {
      this.constLoading = true
      try {
        this.saveCachedState()
        const buildParams = (extra) => {
          const p = { pageNum: this.constQp.pageNum, pageSize: this.constQp.pageSize, varSource: 'CONSTANT', ...extra }
          const { scope, projectCode, projectName, keyword, varType, varCode, varLabel } = this.constQp
          if (scope) p.scope = scope
          if (keyword) p.keyword = keyword
          if (varType) p.varType = varType
          if (varCode) p.varCode = varCode
          if (varLabel) p.varLabel = varLabel
          if (projectCode) p.projectCode = projectCode
          if (projectName) p.projectName = projectName
          return p
        }
        let res
        if (this.currentProjectId) {
          res = await listVariables(buildParams({ projectId: this.currentProjectId }))
        } else {
          res = await request.get('/rule/variable/list', { params: buildParams() })
        }
        const data = res.data || {}
        this.constantRows = data.records || []
        this.constantTotal = data.total != null ? data.total : 0
        // 加载常量编码/名称列表供筛选下拉
        const codeSet = new Set(), labelSet = new Set()
        ;(data.records || []).forEach(r => { if (r.varCode) codeSet.add(r.varCode); if (r.varLabel) labelSet.add(r.varLabel) })
        this.allConstCodes = Array.from(codeSet)
        this.allConstLabels = Array.from(labelSet)
        this.filteredConstCodes = this.allConstCodes.slice(0, 20)
        this.filteredConstLabels = this.allConstLabels.slice(0, 20)
      } catch (e) {
        this.constantRows = []
        this.constantTotal = 0
      } finally { this.constLoading = false }
    },
    handleConstQuery() { this.constQp.pageNum = 1; this.loadConstants() },
    resetConstQuery() {
      this.constQp = { pageNum: 1, pageSize: this.constQp.pageSize, keyword: '', varType: '', scope: '', projectCode: '', projectName: '', varCode: '', varLabel: '' }
      this.saveCachedState()
      this.loadConstants()
    },
    onObjFilterChange() {
      this.objPageNum = 1
      this.saveCachedState()
      this.loadObjectTree()
    },
    resetObjQuery() {
      this.objQp = { scope: '', projectCode: '', projectName: '', sourceType: '', objectCode: '' }
      this.objPageNum = 1
      this.saveCachedState()
      this.loadObjectTree()
    },

    // ── CRUD ──
    /**
     * 顶部「新建」：变量列表新建变量；常量列表新建常量；数据对象列表新建对象。
     */
    handlePrimaryCreate() {
      if (this.activeTab === 'constants') {
        this.isConstantCreate = true
        this.isObjectField = false
        this.objectFieldParentId = null
        this.form = { ...this.initForm(), varSource: 'CONSTANT', status: 1 }
        // 如果当前选中了项目，默认项目级范围，否则全局
        if (this.currentProjectId) {
          this.form.scope = 'PROJECT'
          this.form.projectId = this.currentProjectId
        } else {
          this.form.scope = 'GLOBAL'
          this.form.projectId = ''
        }
        this.dialogVisible = true
        this.$nextTick(() => { if (this.$refs.form) this.$refs.form.clearValidate() })
        return
      }
      if (this.activeTab === 'objects') {
        this.handleCreateObject()
        return
      }
      this.handleCreate()
    },
    handleCreate() {
      this.isConstantCreate = false
      this.isObjectField = false
      this.objectFieldParentId = null
      this.form = this.initForm()
      // 如果当前选中了项目，则 projectId 默认填充，scope 保持 initForm 的 PROJECT
      if (this.currentProjectId) {
        this.form.projectId = this.currentProjectId
      }
      this.dialogVisible = true
      this.$nextTick(() => { if (this.$refs.form) this.$refs.form.clearValidate() })
    },
      handleEdit(row) {
        this.isObjectField = false
        this.isConstantCreate = false
        this.objectFieldParentId = null
        this.form = { ...this.initForm(), ...row }
        this.form.scope = this.form.scope || 'PROJECT'
        this.applySourceConfigToForm()
        this.loadVariableSourceOptions(this.form.varSource)
        this.dialogVisible = true
        this.$nextTick(() => { if (this.$refs.form) this.$refs.form.clearValidate() })
      },
    onVarScopeChange(val) {
      if (val === 'GLOBAL') {
        this.form.projectId = 0
      }
      this.onVariableProjectChange()
    },
    handleSubmit() {
      this.$refs.form.validate(async (valid) => {
        if (!valid) return
        if (this.isObjectField) {
          if (!this.form.projectId) this.form.projectId = this.currentProjectId
          const payload = {
            id: this.form.id,
            projectId: this.form.projectId,
            objectId: this.objectFieldParentId,
            varCode: this.form.varCode,
            varLabel: this.form.varLabel,
            scriptName: this.form.scriptName,
            varType: this.form.varType,
            refObjectCode: this.form.refObjectCode || null,
            refObjectId: this.form.refObjectId || null,
            genericType: this.form.genericType || null,
            parentFieldId: this.form.parentFieldId || null,
            sortOrder: this.form.sortOrder,
            status: this.form.status
          }
          if (this.form.id) {
            await updateDataObjectField(payload)
          } else {
            await createDataObjectField(this.objectFieldParentId, payload)
          }
          this.$message.success('操作成功')
          this.dialogVisible = false
          this.isObjectField = false
          this.loadObjectTree()
          return
        }
        if (!this.form.projectId) this.form.projectId = this.currentProjectId
        // 处理 scope 逻辑：全局函数 projectId 设为 0
        if (this.form.scope === 'GLOBAL') {
          this.form.projectId = 0
        }
        if (this.form.scope === 'PROJECT' && !this.form.projectId) {
          this.$message.warning('请选择所属项目')
          return
        }
        if (this.form.varSource === 'CONSTANT') {
          if (!hasConstantValue(this.form.defaultValue, this.form.varType)) {
            this.$message.warning('常量必须填写默认值')
            return
          }
        }
          const payload = this.buildVariablePayload()
          if (!payload) return
          const wasConstant = payload.varSource === 'CONSTANT' || this.isConstantCreate
          if (payload.id) { await updateVariable(payload) } else { await createVariable(payload) }
        this.$message.success('操作成功')
        this.dialogVisible = false
        this.isConstantCreate = false
        this.loadData()
        if (wasConstant) this.loadConstants()
      })
    },
    handleDelete(row) {
      this.$confirm(`确定删除「${row.varLabel}」？`, '确认删除', { type: 'warning' })
        .then(async () => {
          await deleteVariable(row.id)
          this.$message.success('删除成功')
          this.loadData()
          if (row.varSource === 'CONSTANT') this.loadConstants()
        }).catch(() => {})
    },
    isTestableSource(row) {
      return row && ['API', 'DB', 'LIST'].indexOf(row.varSource) >= 0
    },
    async handleViewSourceDetail(row) {
      await this.loadVariableSourceOptions(row && row.varSource)
      this.sourceDetailTarget = row
      this.sourceDetailVisible = true
    },
    sourceInputFields(row) {
      const config = this.parseJson(row && row.sourceConfig, {})
      const rows = []
      if (row && row.varSource === 'API') {
        const mapping = config.paramMapping || this.apiInputConfig(config.apiConfigId)
        Object.keys(mapping).forEach(key => {
          const expression = mapping[key]
          const paths = collectReferencePaths(expression, { allowBarePath: true })
          rows.push({ field: paths.length ? paths.join(', ') : key, usage: 'API入参 ' + key, expression: String(expression || '') })
        })
        if (rows.length === 0 && config.apiConfigId) {
          rows.push({ field: '由API配置决定', usage: 'API入参', expression: '外数 API 请求参数/请求体中未识别到 $. 或 ${} 引用' })
        }
      } else if (row && row.varSource === 'DB') {
        const params = Array.isArray(config.params) ? config.params : []
        params.forEach((item, index) => {
          const paths = collectReferencePaths(item, { allowBarePath: true })
          rows.push({ field: paths.length ? paths.join(', ') : ('参数' + (index + 1)), usage: 'SQL占位参数 #' + (index + 1), expression: String(item || '') })
        })
      } else if (row && row.varSource === 'LIST') {
        (config.queryOperands || []).forEach((operand, index) => {
          const refs = collectOperandReferences(operand)
          rows.push({
            field: refs.length ? refs.map(ref => ref.code || ref.path).join(', ') : '无字段依赖',
            usage: '名单查询表达式 #' + (index + 1),
            expression: operandDisplay(operand)
          })
        })
      }
      return rows
    },
    async handleTestVariable(row) {
      await this.loadVariableSourceOptions(row && row.varSource)
      this.testTarget = row
      let schema = null
      try {
        schema = normalizeTestSchema(await getRuleTestSchema({ targetType: 'VARIABLE', targetId: row.id }))
      } catch (e) { /* compatibility fallback for older servers */ }
      this.variableTestParamsText = schema && (schema.inputs.length || Object.keys(schema.sampleParams).length)
        ? this.formatJson(schema.sampleParams)
        : this.buildTestParamTemplate(row)
      this.variableTestResult = null
      this.variableTestVisible = true
    },
    buildTestParamTemplate(row) {
      const config = this.parseJson(row && row.sourceConfig, {})
      const sample = {}
      if (row && row.varSource === 'API') {
        const savedSample = this.apiSavedSample(config.apiConfigId)
        if (savedSample) return this.formatJson(savedSample)
        const mapping = { ...this.apiInputConfig(config.apiConfigId), ...(config.paramMapping || {}) }
        Object.keys(mapping).forEach(key => {
          const paths = collectReferencePaths(mapping[key], { allowBarePath: true })
          if (paths.length) paths.forEach(path => setPathValue(sample, path, this.sampleValueForPath(path)))
          else sample[key] = this.sampleValueForPath(key)
        })
      } else if (row && row.varSource === 'DB') {
        const params = Array.isArray(config.params) ? config.params : []
        params.forEach(item => {
          collectReferencePaths(item, { allowBarePath: true }).forEach(path => setPathValue(sample, path, this.sampleValueForPath(path)))
        })
      } else if (row && row.varSource === 'LIST') {
        (config.queryOperands || []).forEach(operand => {
          collectOperandReferences(operand).forEach(ref => {
            const path = ref.code || ref.path
            if (path) setPathValue(sample, path, this.sampleValueForPath(path))
          })
        })
      }
      return this.formatJson(sample)
    },
    sampleValueForPath(path) {
      const ref = this.findRefForPath(path)
      if (ref && ref.exampleValue !== undefined && ref.exampleValue !== null && ref.exampleValue !== '') {
        return ref.exampleValue
      }
      if (ref && ref.defaultValue !== undefined && ref.defaultValue !== null && ref.defaultValue !== '') {
        return ref.defaultValue
      }
      return sampleValueForVarType(ref && (ref.varType || ref.fieldType))
    },
    findRefForPath(path) {
      const text = String(path || '').replace(/^\$\./, '')
      if (!text) return null
      const parts = text.split('.').filter(Boolean)
      const candidates = [text, parts[parts.length - 1]]
      const vars = this.tableData || []
      for (let i = 0; i < vars.length; i++) {
        const v = vars[i]
        if (candidates.indexOf(v.scriptName) >= 0 || candidates.indexOf(v.varCode) >= 0) return v
      }
      return this.findObjectFieldForPath(this.objectTree || [], candidates)
    },
    findObjectFieldForPath(rows, candidates) {
      for (let i = 0; i < rows.length; i++) {
        const row = rows[i]
        if (candidates.indexOf(row.scriptName) >= 0 || candidates.indexOf(row.varCode) >= 0 || candidates.indexOf(row.fieldName) >= 0) {
          return row
        }
        const child = this.findObjectFieldForPath(row.children || [], candidates)
        if (child) return child
      }
      return null
    },
    apiOption(apiConfigId) {
      return (this.apiConfigOptions || []).find(item => String(item.id) === String(apiConfigId)) || null
    },
    apiSavedSample(apiConfigId) {
      const api = this.apiOption(apiConfigId)
      if (!api || !api.testSampleParams) return null
      const parsed = this.parseJsonSafe(api.testSampleParams, null)
      return parsed && typeof parsed === 'object' ? parsed : null
    },
    apiInputConfig(apiConfigId) {
      const api = this.apiOption(apiConfigId)
      const merged = {}
      if (!api) return merged
      ;['headerConfig', 'queryConfig', 'requestMapping', 'bodyTemplate', 'authApiConfig'].forEach(key => {
        const parsed = this.parseJson(api[key], null)
        this.collectApiReferenceConfig(parsed == null ? api[key] : parsed, merged)
      })
      return merged
    },
    collectApiReferenceConfig(value, target) {
      collectReferencePaths(value, { allowBarePath: false }).forEach(path => {
        target[path] = '$.' + path
      })
    },
    sourceBusinessTitle(row) {
      if (!row) return '取数配置'
      return { API: '接口取数', DB: '数据库查询', LIST: '名单匹配' }[row.varSource] || '取数配置'
    },
    sourceBusinessDesc(row) {
      const config = this.parseJson(row && row.sourceConfig, {})
      if (row && row.varSource === 'API') {
        const api = this.apiOption(config.apiConfigId)
        return '执行规则时调用「' + (api ? (api.apiName || api.apiCode) : '已选接口') + '」，并从返回结果中读取变量值。'
      }
      if (row && row.varSource === 'DB') {
        return '执行规则时按配置的查询条件读取数据库结果，再将结果写入当前变量。'
      }
      if (row && row.varSource === 'LIST') {
        return '执行规则时计算一个或多个查询表达式，并按所选组合模式到一个或多个名单库中匹配。'
      }
      return '当前变量使用普通输入或计算方式。'
    },
    sourceSummaryRows(row) {
      const config = this.parseJson(row && row.sourceConfig, {})
      if (row && row.varSource === 'API') {
        const api = this.apiOption(config.apiConfigId)
        return [
          { label: '调用接口', value: api ? (api.apiName || api.apiCode) : (config.apiConfigId ? '接口ID ' + config.apiConfigId : '未配置') },
          { label: '取值位置', value: config.resultPath || 'body' },
          { label: '失败处理', value: this.exceptionStrategyLabel(config.exceptionStrategy) },
          { label: '测试样例', value: this.apiSavedSample(config.apiConfigId) ? '已保存，可直接加载测试' : '未保存，按请求参数生成' }
        ]
      }
      if (row && row.varSource === 'DB') {
        const db = (this.dbDatasourceOptions || []).find(item => String(item.id) === String(config.datasourceId))
        return [
          { label: '数据库源', value: db ? (db.datasourceName || db.datasourceCode) : (config.datasourceId ? '数据库ID ' + config.datasourceId : '未配置') },
          { label: '返回位置', value: config.resultPath || '第一列/首行结果' },
          { label: '最多返回', value: (config.maxRows || 1) + ' 行' },
          { label: '失败处理', value: this.exceptionStrategyLabel(config.exceptionStrategy) }
        ]
      }
      if (row && row.varSource === 'LIST') {
        const listNames = (config.listIds || []).map(listId => {
          const library = (this.listLibraryOptions || []).find(item => String(item.id) === String(listId))
          return library ? (library.listName || library.listCode) : ('名单ID ' + listId)
        })
        return [
          { label: '名单库', value: listNames.length ? listNames.join('、') : '未配置' },
          { label: '查询表达式', value: (config.queryOperands || []).map(operandDisplay).join('；') || '未配置' },
          { label: '组合模式', value: listCombinationMode(config.combinationMode).label },
          { label: '匹配方式', value: this.listMatchModeLabel(config.matchMode) },
          { label: '返回结果', value: config.returnMode === 'BOOLEAN' ? 'true / false' : '命中 1 / 未命中 0' }
        ]
      }
      return []
    },
    exceptionStrategyLabel(value) {
      return { ERROR: '失败时报错', RETURN_DEFAULT: '失败时返回默认值', RETURN_NULL: '失败时返回空值' }[value || 'ERROR'] || value || '失败时报错'
    },
    listMatchModeLabel(value) {
      const found = this.listMatchModeOptions.find(item => item.value === value)
      return found ? found.label : (value || '在名单内（精确匹配）')
    },
    async doTestVariable() {
      if (!this.testTarget) return
      let params
      try {
        params = this.variableTestParamsText ? JSON.parse(this.variableTestParamsText) : {}
      } catch (e) {
        this.$message.error('测试入参不是合法 JSON')
        return
      }
      this.variableTesting = true
      this.variableTestResult = null
      try {
        const res = await testVariable(this.testTarget.id, params)
        this.variableTestResult = normalizeTestResult(res)
      } catch (e) {
        this.variableTestResult = { success: false, errorMessage: e.message || '测试失败' }
      } finally {
        this.variableTesting = false
      }
    },
    parseJson(text, fallback) {
      if (!text) return fallback
      try {
        return JSON.parse(text)
      } catch (e) {
        return fallback
      }
    },
    pathFromMapping(value) {
      return collectReferencePathsFromText(value, { allowBarePath: true })[0] || ''
    },
    setPathValue(target, path, value) {
      setPathValue(target, path, value)
    },
    formatJson(value) {
      if (value == null || value === '') return ''
      try {
        if (typeof value === 'string') return JSON.stringify(JSON.parse(value), null, 2)
        return JSON.stringify(value, null, 2)
      } catch (e) {
        return String(value)
      }
    },
    /**
     * @param {boolean} isField - 是否为数据对象字段上的枚举
     */
    async handleOptions(row, isField) {
      this.currentVar = row
      this.optionTargetIsField = !!isField
      try {
        const res = isField ? await getDataObjectFieldOptions(row.id) : await getVariableOptions(row.id)
        this.optionList = res.data || []
      } catch (e) { this.optionList = [] }
      this.optionDialogVisible = true
    },
    async handleSaveOptions() {
      const invalid = this.optionList.some(o => !o.optionValue || !o.optionLabel)
      if (invalid) { this.$message.warning('请填写完整的选项值和标签'); return }
      if (this.optionTargetIsField) {
        await saveDataObjectFieldOptions(this.currentVar.id, this.optionList)
      } else {
        await saveVariableOptions(this.currentVar.id, this.optionList)
      }
      this.$message.success('保存成功')
      this.optionDialogVisible = false
    },

    // ── Import ──
    handleImportCmd(cmd) {
      this.importForm = { objectType: 'INPUT', javaSource: '', jsonContent: '', ddlSource: '', objectCode: '', scope: 'GLOBAL', projectId: '' }
      if (cmd === 'java-entity') this.importJavaEntityVisible = true
      else if (cmd === 'json-object') this.importJsonObjectVisible = true
      else if (cmd === 'ddl-table') this.importDdlVisible = true
      else if (cmd === 'java-const') this.importJavaConstVisible = true
      else if (cmd === 'json-const') this.importJsonConstVisible = true
    },
    handleJavaFileSelect(file) {
      const reader = new FileReader()
      reader.onload = (e) => { this.importForm.javaSource = e.target.result }
      reader.readAsText(file)
      return false
    },
    async doImportJavaEntity() {
      if (!this.importForm.javaSource.trim()) { this.$message.warning('请输入或上传 Java 源码'); return }
      if (this.importForm.scope === 'PROJECT' && !this.importForm.projectId) {
        this.$message.warning('导入为「项目级」时请选择项目，或将作用范围改为「全局」'); return
      }
      this.importing = true
      try {
        const projectId = this.importForm.scope === 'GLOBAL' ? null : this.importForm.projectId
        const res = await importJavaEntity(projectId, this.importForm.objectType, this.importForm.javaSource, this.importForm.scope)
        this.importResult = res.data || {}
        this.importJavaEntityVisible = false
        this.importResultVisible = true
        this.loadData(); this.loadObjectTree()
      } catch (e) { this.$message.error('导入失败: ' + (e.message || '')) }
      finally { this.importing = false }
    },
    async doImportJsonObject() {
      if (!this.importForm.objectCode.trim()) { this.$message.warning('请输入对象编码'); return }
      if (!this.importForm.jsonContent.trim()) { this.$message.warning('请输入 JSON 内容'); return }
      if (this.importForm.scope === 'PROJECT' && !this.importForm.projectId) {
        this.$message.warning('导入为「项目级」时请选择项目，或将作用范围改为「全局」'); return
      }
      this.importing = true
      try {
        const projectId = this.importForm.scope === 'GLOBAL' ? null : this.importForm.projectId
        const res = await importJsonObject(projectId, this.importForm.objectType, this.importForm.objectCode, this.importForm.jsonContent, this.importForm.scope)
        this.importResult = res.data || {}
        this.importJsonObjectVisible = false
        this.importResultVisible = true
        this.loadData(); this.loadObjectTree()
      } catch (e) { this.$message.error('导入失败: ' + (e.message || '')) }
      finally { this.importing = false }
    },
    /** 从 CREATE TABLE DDL 导入数据对象（COMMENT → 变量名称） */
    async doImportDdl() {
      if (!this.importForm.ddlSource || !this.importForm.ddlSource.trim()) { this.$message.warning('请输入建表 DDL'); return }
      if (this.importForm.scope === 'PROJECT' && !this.importForm.projectId) {
        this.$message.warning('导入为「项目级」时请选择项目，或将作用范围改为「全局」'); return
      }
      this.importing = true
      try {
        const projectId = this.importForm.scope === 'GLOBAL' ? null : this.importForm.projectId
        const res = await importDdlTable(projectId, this.importForm.objectType, this.importForm.ddlSource, this.importForm.scope)
        this.importResult = res.data || {}
        this.importDdlVisible = false
        this.importResultVisible = true
        this.loadData(); this.loadObjectTree()
      } catch (e) { this.$message.error('导入失败: ' + (e.message || '')) }
      finally { this.importing = false }
    },
    async doImportJavaConst() {
      if (!this.importForm.javaSource.trim()) { this.$message.warning('请输入或上传 Java 源码'); return }
      if (this.importForm.scope === 'PROJECT' && !this.importForm.projectId) {
        this.$message.warning('导入为「项目级」时请选择项目，或将作用范围改为「全局」'); return
      }
      this.importing = true
      try {
        const projectId = this.importForm.scope === 'GLOBAL' ? null : this.importForm.projectId
        const res = await importJavaConstants(this.importForm.javaSource, this.importForm.scope, projectId)
        this.importResult = res.data || {}
        this.importJavaConstVisible = false
        this.importResultVisible = true
        this.loadData(); this.loadConstants()
      } catch (e) { this.$message.error('导入失败: ' + (e.message || '')) }
      finally { this.importing = false }
    },
    async doImportJsonConst() {
      if (!this.importForm.jsonContent.trim()) { this.$message.warning('请输入 JSON 内容'); return }
      if (this.importForm.scope === 'PROJECT' && !this.importForm.projectId) {
        this.$message.warning('导入为「项目级」时请选择项目，或将作用范围改为「全局」'); return
      }
      this.importing = true
      try {
        const projectId = this.importForm.scope === 'GLOBAL' ? null : this.importForm.projectId
        const res = await importJsonConstants(this.importForm.jsonContent, this.importForm.scope, projectId)
        this.importResult = res.data || {}
        this.importJsonConstVisible = false
        this.importResultVisible = true
        this.loadData(); this.loadConstants()
      } catch (e) { this.$message.error('导入失败: ' + (e.message || '')) }
      finally { this.importing = false }
    },

    // ── Batch Validate ──
    handleBatchValidate() {
      this.validateProjectId = this.currentProjectId || ''
      this.validateDialogVisible = true
    },
    async doBatchValidate() {
      this.validating = true
      try {
        const res = await batchValidateRules(this.validateProjectId || null)
        this.validateResults = res.data || []
        this.validateDialogVisible = false
        this.validateVisible = true
      } catch (e) { this.$message.error('验证失败: ' + (e.message || '')) }
      finally { this.validating = false }
    },

    // ── Helpers ──
    typeLabel: varTypeLabel,
    typeTagColor: varTypeTagColor,
    formatConstantValue,
    scopeTagLabel(scope) {
      return { GLOBAL: '全局', PROJECT: '项目级' }[scope] || '项目级'
    },
    sourceLabel(s) { return { INPUT:'输入', COMPUTED:'计算', CONSTANT:'常量', DB:'数据库', API:'接口', LIST:'名单' }[s] || s },
    sourceTagColor(s) { return { INPUT:'', COMPUTED:'warning', CONSTANT:'success', DB:'info', API:'info', LIST:'danger' }[s] || '' },
    objTypeLabel(t) { return { INPUT:'输入对象', OUTPUT:'输出对象', INOUT:'输入输出' }[t] || t },
    objTypeColor(t) { return { INPUT:'', OUTPUT:'success', INOUT:'warning' }[t] || '' }
  }
}
</script>

<style scoped>
.linkage-hint { font-size:12px; color:#909399; margin-bottom:12px; padding:8px 12px; background:#f5f7fa; border-radius:4px; }
.linkage-hint a { color:#1890ff; text-decoration:none; }
.linkage-hint a:hover { text-decoration:underline; }

.var-tabs { margin-bottom:16px; }
.tab-filter-row { display:flex; gap:8px; align-items:center; margin-bottom:12px; flex-wrap:wrap; }
.tab-empty { text-align:center; padding:48px 0; color:#c0c4cc; font-size:14px; }
.text-muted { color:#909399; font-size:13px; }
.field-help { color:#64748b; font-size:12px; line-height:1.6; margin-top:4px; }
.field-help code { color:#1e40af; background:#eff6ff; border-radius:3px; padding:0 4px; }
.list-query-row { display:flex; align-items:center; gap:8px; margin-bottom:8px; }
.list-query-row .operand-picker { flex:1; min-width:0; }
.list-query-remove { flex:none; color:#ef6b73 !important; }

/* 变量列表分区样式 */
.var-list-section { margin-bottom:24px; }
.var-list-section .section-title { font-size:14px; font-weight:600; color:#303133; margin-bottom:10px; padding-bottom:6px; border-bottom:1px solid #ebeef5; }
.var-group-card { border:1px solid #ebeef5; border-radius:6px; margin-bottom:10px; overflow:hidden; }
.var-group-header { display:flex; align-items:center; gap:8px; padding:10px 14px; background:#fafafa; border-bottom:1px solid #ebeef5; cursor:pointer; flex-wrap:wrap; }
.var-group-header:hover { background:#f0f2f5; }
.var-group-header .expand-icon { font-size:14px; color:#909399; margin-right:4px; transition:transform 0.2s; }
.var-group-toolbar { display:flex; align-items:center; gap:8px; padding:8px 14px; background:#f5f7fa; border-bottom:1px solid #ebeef5; flex-wrap:wrap; }
.var-group-code { font-weight:bold; font-size:14px; color:#303133; font-family:Consolas,monospace; }
.var-group-label { color:#909399; font-size:13px; }
.var-group-count { font-size:12px; color:#909399; }
.var-group-body { padding:10px; background:#fff; }

/* Object cards */
.obj-card { border:1px solid #ebeef5; border-radius:6px; margin-bottom:12px; overflow:hidden; }
.obj-card-header { display:flex; align-items:center; gap:8px; padding:10px 14px; background:#fafafa; border-bottom:1px solid #ebeef5; flex-wrap:wrap; }
.obj-code { font-weight:bold; font-size:14px; color:#303133; font-family:Consolas,monospace; }
.obj-label { color:#909399; font-size:13px; }
.obj-var-count { font-size:12px; color:#909399; }

/* Constant cards */
.const-toolbar { margin-bottom:12px; }
.const-group-card { border:1px solid #ebeef5; border-radius:6px; margin-bottom:10px; overflow:hidden; }
.const-group-header { display:flex; align-items:center; gap:8px; padding:10px 14px; background:#fafafa; border-bottom:1px solid #ebeef5; cursor:pointer; flex-wrap:wrap; }
.const-group-header:hover { background:#f0f2f5; }
.const-group-code { font-weight:bold; font-size:14px; color:#303133; font-family:Consolas,monospace; }
.const-group-label { color:#909399; font-size:13px; }
.const-count { font-size:12px; color:#909399; }
.const-group-body { padding:8px; }

/* Badges */
.badge { display:inline-block; padding:1px 6px; border-radius:3px; font-size:11px; font-family:Consolas,monospace; }
.badge-obj { background:#e6f7ff; color:#1890ff; border:1px solid #91d5ff; }
.badge-const { background:#f6ffed; color:#52c41a; border:1px solid #b7eb8f; }

.test-target { display:flex; align-items:center; gap:8px; margin-bottom:12px; color:#303133; }
.test-target code { color:#64748b; background:#f8fafc; border:1px solid #e2e8f0; border-radius:3px; padding:1px 6px; }
.json-input ::v-deep textarea { font-family:Menlo, Monaco, Consolas, monospace; font-size:12px; line-height:1.5; }
.test-result-block { margin-top:12px; }
.test-result-title { font-weight:700; color:#334155; margin-bottom:6px; }
.test-result-pre { margin:0; padding:10px; border:1px solid #e2e8f0; border-radius:4px; background:#f8fafc; max-height:260px; overflow:auto; font-size:12px; line-height:1.5; }
.source-detail-section { margin-top:14px; }
.source-detail-title { font-weight:700; color:#334155; margin-bottom:8px; }
.source-summary-card { margin-top:14px; padding:14px; border:1px solid #dbeafe; background:#f8fbff; border-radius:6px; }
.source-summary-title { color:#1e3a8a; font-weight:700; margin-bottom:4px; }
.source-summary-desc { color:#475569; font-size:13px; line-height:1.6; margin-bottom:12px; }
.source-summary-grid { display:grid; grid-template-columns:repeat(2, minmax(0, 1fr)); gap:10px; }
.source-summary-item { background:#fff; border:1px solid #e2e8f0; border-radius:4px; padding:8px 10px; min-width:0; }
.source-summary-label { color:#64748b; font-size:12px; margin-bottom:4px; }
.source-summary-value { color:#0f172a; font-size:13px; font-weight:600; line-height:1.4; word-break:break-all; }
.source-tech-collapse { margin-top:14px; }

/* Import result */
.import-result-body { text-align:center; padding:20px 0; }
.import-result-body h3 { margin:12px 0 8px; }
.import-result-body p { color:#606266; font-size:14px; margin:4px 0; }

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

/* type="text" 按钮：始终保持纯文字样式，覆盖所有 active/focus 状态 */
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
.el-button[type="text"]:hover {
  color: #1890ff !important;
}
</style>
