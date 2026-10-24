<template>
  <div class="uiue-list-page">
    <div v-if="loading" data-testid="rule-detail-loading" role="status">正在加载规则详情，请稍候…</div>
    <el-alert v-else-if="loadError" data-testid="rule-detail-error" title="规则详情加载失败" type="error" :closable="false" show-icon>
      <span>{{ loadError }}</span>
      <el-button size="small" @click="load">重新加载</el-button>
    </el-alert>
    <div data-testid="rule-detail-content" :inert="loading || !!loadError" :aria-busy="loading">
    <!-- 页面头部 -->
    <div class="uiue-detail-header rule-detail-header">
      <div class="uiue-detail-header__title">
        <el-button
          size="small"
          :icon="ElIconBack"
          class="uiue-detail-back"
          aria-label="返回"
          title="返回规则列表"
          @click="$router.push('/rule')"
        />
        <h2 style="margin: 0">{{ rule.ruleName || '规则详情' }}</h2>
      </div>
      <div class="uiue-detail-header__actions rule-detail-actions">
        <el-button v-if="hasEditableWorkspace && !canEditDraft" size="small" @click="showWorkspaceFields">返回工作区草稿</el-button>
        <el-button
          v-permission="'rule:edit'"
          size="small"
          :icon="ElIconEdit"
          :disabled="!canEditDraft"
          @click="openBaseEditDialog"
          >编辑基本信息</el-button
        >
        <el-button
          v-permission="'rule:edit'"
          size="small"
          type="primary"
          :icon="ElIconVideoPlay"
          @click="openTestDialog"
          >规则测试</el-button
        >
        <resource-version-manager
          v-if="rule.id && detailActive && String(rule.id) === String($route.params.id)"
          :show-trigger="false"
          resource-type="RULE"
          :resource-id="rule.id"
          has-legacy
          @legacy="openVersionDialog"
          @changed="load"
        />
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
      <el-descriptions-item label="规则编码">{{
        rule.ruleCode
      }}</el-descriptions-item>
      <el-descriptions-item label="规则名称">{{
        rule.ruleName
      }}</el-descriptions-item>
      <el-descriptions-item label="决策模型">{{
        modelTypeLabel(rule.modelType)
      }}</el-descriptions-item>
      <el-descriptions-item label="作用范围">{{
        rule.scope === 'GLOBAL' ? '全局' : '项目级'
      }}</el-descriptions-item>
      <el-descriptions-item label="所属项目">{{
        rule.projectName || '—'
      }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag size="small" :type="statusType(rule.status)">{{
          statusLabel(rule.status)
        }}</el-tag>
      </el-descriptions-item>
    </el-descriptions>

    <!-- 描述 -->
    <el-card v-if="rule.description" shadow="never" style="margin-bottom: 16px">
      <template v-slot:header>
        <div style="font-weight: 600">描述</div>
      </template>
      <div style="color: var(--tianshu-text-secondary); font-size: 14px; line-height: 1.6">
        {{ rule.description }}
      </div>
    </el-card>

    <el-card v-if="selectedRevisionReadonly" shadow="never" class="release-progress-card" data-testid="rule-revision-status">
      <div class="release-progress-header">
        <div><strong>{{ lifecycleRevision.id ? lifecycleStateLabel(lifecycleRevision.state) + '修订' : '历史发布记录' }}</strong>
          <span class="release-progress-summary">{{ lifecycleRevision.id ? revisionOptionLabel(lifecycleRevision) : '没有对应的修订制品记录' }}</span>
        </div>
        <el-button v-if="lifecycleRevision.id" size="small" @click="showRevisionFields">查看修订字段</el-button>
      </div>
      <p class="revision-scope-note">发布与审批状态来自版本记录。当前页面的管理测试不作为该修订的测试证据。</p>
    </el-card>
    <config-layer-guide
      v-else
      title="工作区发布准备"
      :description="releaseProgressSummary"
      aria-label="工作区发布准备"
      test-id="rule-release-progress"
      item-test-id-prefix="rule-release-step"
      :items="releaseChecklist"
      show-progress
      progress-label="已完成"
      ready-state-label="已完成"
      interactive
      @select="goToChecklistItem"
    />

    <el-dialog
      title="编辑规则基本信息"
      v-model="baseEditVisible"
      width="560px"
      :close-on-click-modal="false"
    >
      <el-form :model="baseForm" label-width="90px" size="small">
        <el-form-item label="规则编码">
          <el-input :model-value="rule.ruleCode" disabled />
        </el-form-item>
        <el-form-item label="规则名称" required>
          <el-input v-model="baseForm.ruleName" placeholder="请输入规则名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="baseForm.description"
            type="textarea"
            :rows="4"
            placeholder="请输入规则功能描述"
          />
        </el-form-item>
      </el-form>
      <template v-slot:footer>
        <div>
          <el-button size="small" @click="baseEditVisible = false"
            >取消</el-button
          >
          <el-button
            size="small"
            type="primary"
            :loading="baseSaving"
            @click="saveBaseInfo"
            >保存</el-button
          >
        </div>
      </template>
    </el-dialog>

    <!-- 规则详情 Tab：生命周期是配置完成后的首要下一步 -->
    <el-tabs v-model="activeDetailTab" type="border-card">
      <el-tab-pane name="lifecycle">
        <template v-slot:label>
          <span><el-icon><el-icon-connection /></el-icon> 生命周期</span>
        </template>

    <div
      v-if="revisions.length"
      class="governance-revision-selector"
    >
      <span>生命周期修订</span>
      <el-select
        v-model="lifecycleRevisionId"
        data-testid="lifecycle-revision-selector"
        size="small"
      >
        <el-option
          v-for="item in revisions"
          :key="item.id"
          :label="revisionOptionLabel(item)"
          :value="item.id"
        />
      </el-select>
      <span class="governance-revision-state">{{
        lifecycleStateLabel(lifecycleRevision.state)
      }}</span>
    </div>
    <rule-lifecycle-panel
      v-if="lifecycleRevision.id"
      class="governance-section"
      :lifecycle-revision="lifecycleRevision"
      :validation-report="preflightReport"
      :online-artifact-digest="publishedRevision.artifactDigest || ''"
      :action-loading="forkingDesignerSource"
      :approval-request-id="lifecycleRevision.governanceRequestId"
      @action="handleLifecycleAction"
    />
    <el-card
      v-if="!loading && !revisions.length"
      data-testid="legacy-governance"
      shadow="never"
      class="governance-section"
    >
      <template #header><div style="font-weight: 600">设计与版本</div></template>
      <el-alert
        title="尚未保存规则版本，可以直接进入设计。编辑时不自动创建草稿，点击暂存后才保存。"
        type="warning"
        :closable="false"
        show-icon
      />
      <div class="action-row">
        <el-button
          size="small"
          :loading="repairPreviewLoading"
          v-if="rule.currentVersion"
          @click="loadRepairPreview"
          >查看修复预览</el-button
        >
        <el-button
          size="small"
          type="primary"
          @click="createLegacyDraft"
          >进入设计</el-button
        >
      </div>
      <div v-if="repairPreview.previewDigest" class="legacy-repair-preview">
        <div>
          可恢复引用：{{ (repairPreview.recoverableReferenceKeys || []).length }}；
          未解析输入：{{ (repairPreview.unresolvedInputs || []).length }}
        </div>
        <el-button
          v-if="
            repairPreview.sourceRevisionId &&
            !(repairPreview.unresolvedInputs || []).length
          "
          size="small"
          type="primary"
          :loading="repairSaving"
          @click="openRevisionDesigner({ id: repairPreview.sourceRevisionId })"
          >在设计页处理引用</el-button
        >
      </div>
    </el-card>
    <el-card v-if="preflightReport" shadow="never" class="governance-section">
      <template #header><div style="font-weight: 600">发布前校验</div></template>
      <rule-validation-report :report="preflightReport" :locatable="!!lifecycleRevision.id" @locate="locatePreflightIssue" />
    </el-card>
    <el-card shadow="never" class="governance-section">
      <template #header>
        <div class="governance-card-header">
          <span>审计时间线</span>
          <el-upload :auto-upload="false" :show-file-list="false" :on-change="handleArtifactFile">
            <el-button size="small">导入跨环境制品</el-button>
          </el-upload>
        </div>
      </template>
      <rule-lifecycle-timeline :events="lifecycleEvents" />
    </el-card>
      </el-tab-pane>

    <!-- 输入输出字段 -->
      <!-- 输入字段 tab -->
      <el-tab-pane name="inputs">
        <template v-slot:label>
          <span
            ><el-icon><el-icon-arrow-down /></el-icon> 输入字段</span
          >
        </template>
        <rule-revision-fields-panel
          v-if="detailDefinitionId && fieldViewMode === 'revision' && activeDetailTab === 'inputs' && !loading && !loadError"
          v-model="lifecycleRevisionId" :definition-id="detailDefinitionId" :revisions="revisions" direction="input"
          :workspace-available="hasEditableWorkspace" @workspace="showWorkspaceFields"
        />
        <template v-if="fieldViewMode === 'workspace'">
        <div class="field-workspace-notice">
          <span>{{ revisions.length ? '当前工作区字段配置；修订快照可单独只读查看。' : '当前管理字段；此历史规则没有可核对的修订快照。' }}</span>
          <el-button v-if="revisions.length" size="small" @click="showRevisionFields">查看修订字段</el-button>
        </div>
        <div
          style="
            margin-bottom: 10px;
            display: flex;
            align-items: center;
            justify-content: space-between;
          "
        >
          <span style="color: var(--tianshu-text-tertiary); font-size: 12px">
            共
            {{ rule.inputFieldsJson ? rule.inputFieldsJson.length : 0 }}
            个字段，请关联引擎变量
          </span>
          <el-button size="small" :icon="ElIconRefresh" @click="load"
            >刷新</el-button
          >
        </div>

        <el-table
          :data="pagedRuleInputFields"
          border
          size="small"
          max-height="500"
          v-loading="loading"
          :row-class-name="inputRowClassName"
        >
          <el-table-column label="序号" width="60" align="center">
            <template v-slot="{ $index }">{{
              inputFieldOffset + $index + 1
            }}</template>
          </el-table-column>
          <el-table-column label="字段层级" min-width="320">
            <template v-slot="{ row }">
              <rule-field-hierarchy-display
                :field="fieldHierarchyDisplay(row)"
                :data-testid="'input-field-hierarchy-' + row.id"
              />
            </template>
          </el-table-column>
          <el-table-column
            prop="fieldType"
            label="类型"
            width="90"
            align="center"
          >
            <template v-slot="{ row }">
              <el-tag size="small" type="info">{{
                typeLabel(row.fieldType)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="默认值" min-width="130">
            <template v-slot="{ row }">
              <span v-if="row.defaultValue" style="color: var(--tianshu-text-secondary)">{{
                row.defaultValue
              }}</span>
              <span v-else style="color: var(--tianshu-text-tertiary)">—</span>
            </template>
          </el-table-column>
          <el-table-column label="取值范围" min-width="130">
            <template v-slot="{ row }">
              <span v-if="row.validValues" style="color: var(--tianshu-text-secondary)">{{
                row.validValues
              }}</span>
              <span v-else style="color: var(--tianshu-text-tertiary)">—</span>
            </template>
          </el-table-column>
          <el-table-column label="字段校验" min-width="240">
            <template v-slot="{ row }">
              <template v-if="row._editing">
                <el-select
                  v-model="row.validationRuleIdList"
                  multiple
                  collapse-tags
                  clearable
                  placeholder="选择一个或多个校验规则"
                  style="width: 100%"
                >
                  <el-option
                    v-for="item in fieldValidationOptions"
                    :key="item.id"
                    :label="fieldValidationOptionLabel(item)"
                    :value="item.id"
                  />
                </el-select>
                <el-button
                  v-if="row.validationOverride === 1"
                  link
                  size="small"
                  @click="restoreInheritedValidation(row)"
                  >恢复子规则配置</el-button
                >
              </template>
              <template v-else>
                <span
                  v-if="!(row.validationRuleIdList || []).length"
                  style="color: var(--tianshu-text-tertiary)"
                  >未配置</span
                >
                <template v-else>
                  <el-tag
                    v-for="item in selectedFieldValidations(row)"
                    :key="item.id"
                    size="small"
                    type="info"
                    style="margin-right: 4px"
                    >{{ item.validationName }}</el-tag
                  >
                </template>
                <el-tag
                  v-if="
                    row.validationOverride !== 1 &&
                    (row.validationRuleIdList || []).length
                  "
                  size="small"
                  type="success"
                  >继承子规则</el-tag
                >
              </template>
            </template>
          </el-table-column>
          <el-table-column label="修改时间" width="140" align="center">
            <template v-slot="{ row }">
              <span v-if="row.updateTime">{{
                row.updateTime.replace('T', ' ')
              }}</span>
              <span v-else-if="row.createTime">{{
                row.createTime.replace('T', ' ')
              }}</span>
              <span v-else style="color: var(--tianshu-text-tertiary)">—</span>
            </template>
          </el-table-column>
          <el-table-column class-name="table-operation-column" :show-overflow-tooltip="false"
            label="操作"
            :width="operationColumnWidth(['保存', '取消', '编辑'])"
            align="center"
            fixed="right"
          >
            <template v-slot="{ row, $index }">
              <template v-if="row._editing">
                <el-button
                  link
                  size="small"
                  type="success"
                  :loading="row._saving"
                  @click="saveInputField(row, $index)"
                  >保存</el-button
                >
                <el-button
                  link
                  size="small"
                  type="info"
                  @click="cancelEditInput(row)"
                  >取消</el-button
                >
              </template>
              <el-button
                v-else
                link
                size="small"
                type="warning"
                :disabled="!canEditDraft"
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
          v-if="!rule.inputFieldsJson || rule.inputFieldsJson.length === 0"
          style="text-align: center; padding: 40px 0; color: var(--tianshu-text-tertiary)"
        >
          暂无输入字段
        </div>
        </template>
      </el-tab-pane>

      <!-- 输出字段 tab -->
      <el-tab-pane name="outputs">
        <template v-slot:label>
          <span
            ><el-icon><el-icon-arrow-up /></el-icon> 输出字段</span
          >
        </template>
        <rule-revision-fields-panel
          v-if="detailDefinitionId && fieldViewMode === 'revision' && activeDetailTab === 'outputs' && !loading && !loadError"
          v-model="lifecycleRevisionId" :definition-id="detailDefinitionId" :revisions="revisions" direction="output"
          :workspace-available="hasEditableWorkspace" @workspace="showWorkspaceFields"
        />
        <template v-if="fieldViewMode === 'workspace'">
        <div class="field-workspace-notice">
          <span>{{ revisions.length ? '当前工作区字段配置；修订快照可单独只读查看。' : '当前管理字段；此历史规则没有可核对的修订快照。' }}</span>
          <el-button v-if="revisions.length" size="small" @click="showRevisionFields">查看修订字段</el-button>
        </div>
        <div
          style="
            margin-bottom: 10px;
            display: flex;
            align-items: center;
            justify-content: space-between;
          "
        >
          <span style="color: var(--tianshu-text-tertiary); font-size: 12px">
            共
            {{ rule.outputFieldsJson ? rule.outputFieldsJson.length : 0 }}
            个字段，请关联引擎变量
          </span>
          <el-button size="small" :icon="ElIconRefresh" @click="load"
            >刷新</el-button
          >
        </div>

        <el-table show-overflow-tooltip
          :data="pagedRuleOutputFields"
          border
          size="small"
          max-height="500"
          v-loading="loading"
          :row-class-name="outputRowClassName"
        >
          <el-table-column label="序号" width="60" align="center">
            <template v-slot="{ $index }">{{
              outputFieldOffset + $index + 1
            }}</template>
          </el-table-column>
          <el-table-column label="字段层级" min-width="320">
            <template v-slot="{ row }">
              <rule-field-hierarchy-display
                :field="fieldHierarchyDisplay(row)"
                :data-testid="'output-field-hierarchy-' + row.id"
              />
            </template>
          </el-table-column>
          <el-table-column
            prop="fieldType"
            label="类型"
            width="90"
            align="center"
          >
            <template v-slot="{ row }">
              <el-tag size="small" type="info">{{
                typeLabel(row.fieldType)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="默认值" min-width="120">
            <span style="color: var(--tianshu-text-tertiary)">—</span>
          </el-table-column>
          <el-table-column label="取值范围" min-width="130">
            <template v-slot="{ row }">
              <span v-if="row.validValues" style="color: var(--tianshu-text-secondary)">{{
                row.validValues
              }}</span>
              <span v-else style="color: var(--tianshu-text-tertiary)">—</span>
            </template>
          </el-table-column>
          <el-table-column label="修改时间" width="140" align="center">
            <template v-slot="{ row }">
              <span v-if="row.updateTime">{{
                row.updateTime.replace('T', ' ')
              }}</span>
              <span v-else-if="row.createTime">{{
                row.createTime.replace('T', ' ')
              }}</span>
              <span v-else style="color: var(--tianshu-text-tertiary)">—</span>
            </template>
          </el-table-column>
          <el-table-column class-name="table-operation-column" :show-overflow-tooltip="false"
            label="操作"
            :width="operationColumnWidth(['保存', '取消', '编辑'])"
            align="center"
            fixed="right"
          >
            <template v-slot="{ row, $index }">
              <template v-if="row._editing">
                <el-button
                  link
                  size="small"
                  type="success"
                  :loading="row._saving"
                  @click="saveOutputField(row, $index)"
                  >保存</el-button
                >
                <el-button
                  link
                  size="small"
                  type="info"
                  @click="cancelEditOutput(row)"
                  >取消</el-button
                >
              </template>
              <el-button
                v-else
                link
                size="small"
                type="warning"
                :disabled="!canEditDraft"
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
          v-if="!rule.outputFieldsJson || rule.outputFieldsJson.length === 0"
          style="text-align: center; padding: 40px 0; color: var(--tianshu-text-tertiary)"
        >
          暂无输出字段
        </div>
        </template>
      </el-tab-pane>

    <el-tab-pane name="execution">
      <template v-slot:label>
        <span><el-icon><el-icon-setting /></el-icon> 执行配置</span>
      </template>
      <div class="open-api-panel execution-policy-panel">
        <p class="field-workspace-notice" data-testid="workspace-config-scope">
          当前工作区配置，不是所选修订的冻结配置。
          <span v-if="canEditDraft">修改后仅保存到工作区草稿。</span>
          <span v-else-if="hasEditableWorkspace">当前只读；请返回工作区草稿后编辑。</span>
          <span v-else>当前没有可编辑草稿，仅供查看。</span>
        </p>
        <div class="open-api-toolbar">
          <div>
            <div class="open-api-title">规则执行策略</div>
            <div class="open-api-help">
              幂等配置同时作用于同步 HTTP、完整 SDK 和开放接口；恢复会沿用同一 trace_id，配置变化后只重新执行受影响步骤。
            </div>
          </div>
          <el-button
            size="small"
            type="primary"
            :loading="openApiSaving"
            :disabled="!canEditDraft"
            @click="saveOpenApiConfig"
            >保存执行配置</el-button
          >
        </div>
        <div class="open-api-section">
          <div class="open-api-section-head">
            <div>
              <div class="open-api-title">规则幂等</div>
              <div class="open-api-help">
                每个组件独立计算；空值组件会跳过。至少配置一个稳定的客户入参字段，不能只用固定常量。
              </div>
            </div>
            <el-switch :disabled="!canEditDraft"
              v-model="openApiForm.idempotency.enabled"
              active-text="启用幂等"
              @change="onIdempotencyEnabledChange"
            />
          </div>
          <div v-if="openApiForm.idempotency.enabled" class="idempotency-component-list">
            <div
              v-for="(component, index) in openApiForm.idempotency.components"
              :key="component._uid || index"
              class="idempotency-component-row"
            >
              <span class="idempotency-component-index">{{ index + 1 }}</span>
              <operand-picker
                v-if="canEditDraft"
                :value="component.operand"
                :vars="openIdempotencyOptions"
                :functions="idempotencyFunctions"
                :allowed-kinds="idempotencyOperandKinds"
                context="READ_EXPRESSION"
                expected-type="STRING"
                placeholder="选择字段或配置组合表达式"
                editor-title="配置幂等键组件"
                @input="updateIdempotencyOperand(component, $event)"
              />
              <code v-else class="idempotency-readonly-formula">{{ idempotencyFormulaPreview[index]?.formula || '未配置' }}</code>
              <el-button
                link
                type="danger"
                :disabled="!canEditDraft || openApiForm.idempotency.components.length <= 1"
                @click="removeIdempotencyComponent(index)"
                >删除</el-button
              >
            </div>
            <el-button :disabled="!canEditDraft" size="small" :icon="ElIconPlus" @click="addIdempotencyComponent">
              添加字段组件
            </el-button>
            <el-alert
              v-if="idempotencyFormulaPreview.length"
              class="idempotency-preview"
              type="info"
              :closable="false"
              title="幂等键预览"
            >
              <div v-for="item in idempotencyFormulaPreview" :key="item.index">
                组件 {{ item.index }}：<code>{{ item.formula || '未配置' }}</code>
              </div>
              <div class="open-api-help">请求未传入的组件会被跳过，组件顺序会参与规范化键计算。</div>
            </el-alert>
          </div>
          <el-empty v-else description="幂等未启用" :image-size="50" />
          <el-form-item label="保留时间" label-width="110px">
            <el-input-number :key="canEditDraft ? 'editable' : 'readonly'" :disabled="!canEditDraft" v-model="openApiForm.idempotency.ttlSeconds" :min="60" :max="604800" :step="60" />
            <span class="open-api-help" style="margin-left: 8px">秒</span>
          </el-form-item>
        </div>
      </div>
    </el-tab-pane>

    <el-tab-pane name="open-api">
        <template v-slot:label>
          <span
            ><el-icon><el-icon-connection /></el-icon> 开放接口</span
          >
        </template>
        <div class="open-api-panel">
        <p class="field-workspace-notice" data-testid="workspace-config-scope">
          当前工作区配置，不是所选修订的冻结配置。
          <span v-if="canEditDraft">修改后仅保存到工作区草稿。</span>
          <span v-else-if="hasEditableWorkspace">当前只读；请返回工作区草稿后编辑。</span>
          <span v-else>当前没有可编辑草稿，仅供查看。</span>
        </p>
          <div class="open-api-toolbar">
            <div>
              <div class="open-api-title">对外规则契约</div>
              <div class="open-api-help">
                调用地址
                <code>POST /api/rule/openapi/{{ rule.ruleCode }}</code
                >，业务 JSON 直接作为请求体，并携带项目鉴权与
                <code>X-Auth-Code</code>。
              </div>
            </div>
            <div class="open-api-actions">
              <el-switch :disabled="!canEditDraft"
                v-model="openApiForm.enabled"
                active-text="启用开放接口"
              />
              <el-button
                size="small"
                :icon="ElIconView"
                @click="previewOpenApiConfig"
                >校验并预览</el-button
              >
              <el-button
                size="small"
                type="primary"
                :loading="openApiSaving"
                :disabled="!canEditDraft"
                @click="saveOpenApiConfig"
                >保存契约</el-button
              >
            </div>
          </div>
          <el-alert
            title="契约随规则版本发布；保存只更新草稿，发布后下游才会使用新配置。正常和异常共用同一个平台外层结构，只有 data 节点内容不同。"
            type="info"
            :closable="false"
            show-icon
          />

          <div class="open-api-section">
            <div class="open-api-section-head">
              <div>
                <div class="open-api-title">请求映射</div>
                <div class="open-api-help">
                  目标字段通过变量/模型稳定 ID 关联；来源只允许受限 JSONPath 或
                  Header 名称。
                </div>
              </div>
              <el-button :disabled="!canEditDraft"
                size="small"
                :icon="ElIconPlus"
                @click="addOpenRequestMapping"
                >添加映射</el-button
              >
            </div>
            <el-alert
              v-if="openInputOptions.length === 0"
              data-testid="open-api-input-empty"
              class="open-api-field-empty"
              title="暂无可映射的规则入参。请先进入规则设计器引用并保存变量，再返回本页配置请求映射。"
              type="warning"
              :closable="false"
              show-icon
            />
            <el-collapse><el-collapse-item title="导入请求样例辅助选择来源路径">
              <monaco-editor v-model:value="openRequestSampleText" language="json" height="160px" :read-only="!canEditDraft" />
              <el-button :disabled="!canEditDraft" @click="importOpenRequestSample">生成请求路径目录</el-button>
              <p v-if="openRequestSampleError" role="alert">{{ openRequestSampleError }}</p>
            </el-collapse-item></el-collapse>
            <el-table
              :data="openApiForm.requestMappings"
              border
              size="small"
              empty-text="尚未配置请求映射"
            >
              <el-table-column label="规则入参" min-width="280">
                <template v-slot="{ row }">
                  <var-picker
                    :value="row.targetKey"
                    :vars="openInputPickerItems"
                    value-key="reference"
                    :allow-custom="false"
                    :disabled="!canEditDraft"
                    placeholder="选择规则入参"
                    @update:value="row.targetKey = $event"
                  />
                </template>
              </el-table-column>
              <el-table-column label="来源" width="110">
                <template v-slot="{ row }"
                  ><el-select :disabled="!canEditDraft" v-model="row.sourceType" style="width: 100%"
                    ><el-option label="请求体" value="BODY" /><el-option
                      label="请求头"
                      value="HEADER" /></el-select
                ></template>
              </el-table-column>
              <el-table-column label="来源路径 / Header" min-width="220">
                <template v-slot="{ row }"
                  ><protocol-path-picker
                    :value="row.sourcePath"
                    :paths="row.sourceType === 'HEADER' ? openApiHeaderPathOptions : openApiBodyPathOptions"
                    :context-label="row.sourceType === 'HEADER' ? '开放 API 请求 Header' : '开放 API 请求体'"
                    :disabled="!canEditDraft"
                    :placeholder="row.sourceType === 'HEADER' ? '选择或输入 Header 名称' : '选择或输入 JSONPath'"
                    @update:value="row.sourcePath = $event"
                  /></template>
              </el-table-column>
              <el-table-column label="目标类型" width="120">
                <template v-slot="{ row }"
                  ><el-select :disabled="!canEditDraft" v-model="row.targetType" style="width: 100%"
                    ><el-option
                      v-for="type in openTargetTypes"
                      :key="type"
                      :label="type"
                      :value="type" /></el-select
                ></template>
              </el-table-column>
              <el-table-column label="必填" width="70" align="center"
                ><template v-slot="{ row }"
                  ><el-switch :disabled="!canEditDraft" v-model="row.required" /></template
              ></el-table-column>
              <el-table-column label="默认值" min-width="120"
                ><template v-slot="{ row }"
                  ><el-input :disabled="!canEditDraft" v-model="row.defaultValue" /></template
              ></el-table-column>
              <el-table-column fixed="right" class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" :width="operationColumnWidth(['删除'])" align="center"
                ><template v-slot="{ $index }"
                  ><el-button :disabled="!canEditDraft"
                    link
                    size="small"
                    type="danger"
                    class="btn-delete"
                    @click="removeOpenRequestMapping($index)"
                    >删除</el-button
                  ></template
                ></el-table-column
              >
            </el-table>
          </div>

          <div class="open-api-section">
            <div class="open-api-section-head">
              <div>
                <div class="open-api-title">响应字段映射</div>
                <div class="open-api-help">
                  通过稳定字段 ID
                  关联引擎输出；对外字段名默认与内部脚本字段名一致，可按下游契约修改。
                </div>
              </div>
              <div>
                <el-button :disabled="!canEditDraft" size="small" @click="resetOpenResponseMappings"
                  >恢复默认映射</el-button
                >
                <el-button :disabled="!canEditDraft"
                  size="small"
                  :icon="ElIconPlus"
                  @click="addOpenResponseMapping"
                  >添加映射</el-button
                >
              </div>
            </div>
            <el-alert
              v-if="openOutputOptions.length === 0"
              data-testid="open-api-output-empty"
              class="open-api-field-empty"
              title="暂无可映射的规则输出。请先进入规则设计器配置并保存输出字段，再返回本页配置响应映射。"
              type="warning"
              :closable="false"
              show-icon
            />
            <el-table
              :data="openApiForm.responseMappings"
              border
              size="small"
              empty-text="尚未配置响应映射"
            >
              <el-table-column label="引擎内部输出字段" min-width="300">
                <template v-slot="{ row }">
                  <var-picker
                    :value="row.sourceKey"
                    :vars="openOutputPickerItems"
                    value-key="reference"
                    :allow-custom="false"
                    :disabled="!canEditDraft"
                    placeholder="选择引擎输出字段"
                    @update:value="row.sourceKey = $event"
                    @select="onOpenResponseSourceChange(row)"
                  />
                </template>
              </el-table-column>
              <el-table-column label="对外 API 字段" min-width="220">
                <template v-slot="{ row }"
                  ><el-input :disabled="!canEditDraft"
                    v-model="row.targetField"
                    placeholder="如 credit_score_v1"
                /></template>
              </el-table-column>
              <el-table-column fixed="right" class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" :width="operationColumnWidth(['删除'])" align="center"
                ><template v-slot="{ $index }"
                  ><el-button :disabled="!canEditDraft"
                    link
                    size="small"
                    type="danger"
                    class="btn-delete"
                    @click="removeOpenResponseMapping($index)"
                    >删除</el-button
                  ></template
                ></el-table-column
              >
            </el-table>
          </div>

          <div class="open-api-section">
            <div class="open-api-title">统一响应契约</div>
            <div class="open-api-help">
              响应外层固定为 code、message、success、detail、traceId 和 data，结果码与描述由平台统一提供。下方可定制 data 内容和业务响应头。
            </div>
            <el-form
              :model="openApiForm"
              label-position="top"
              class="open-api-response-form"
            >
              <el-row :gutter="12" class="open-api-editors">
                <el-col :span="24">
                  <el-form-item label="响应 Header JSON"
                    ><monaco-editor
                      :value="openResponseHeadersText"
                      :read-only="!canEditDraft"
                      @update:value="updateOpenApiEditor('openResponseHeadersText', $event)"
                      language="json"
                      height="168px"
                  /></el-form-item>
                </el-col>
              </el-row>
              <div class="open-api-help open-output-help">
                映射结果占位符：<code>${response}</code>；内部输出占位符：<code
                  v-for="item in openOutputPlaceholders"
                  :key="item"
                  >{{ item }}</code
                ><span v-if="!openOutputPlaceholders.length">暂无输出字段</span>
              </div>
              <div class="open-api-help">
                字段校验异常可用占位符：<code>${error.message}</code>、<code>${error.field}</code>、<code>${error.validationCode}</code>、<code>${error.validationName}</code>。
              </div>
              <el-row :gutter="12" class="open-api-editors">
                <el-col :lg="12" :md="24"
                  ><el-form-item label="成功 data JSON"
                    ><monaco-editor
                      :value="openSuccessDataText"
                      :read-only="!canEditDraft"
                      @update:value="updateOpenApiEditor('openSuccessDataText', $event)"
                      language="json"
                      height="220px" /></el-form-item
                ></el-col>
                <el-col :lg="12" :md="24"
                  ><el-form-item label="异常 data JSON"
                    ><monaco-editor
                      :value="openErrorDataText"
                      :read-only="!canEditDraft"
                      @update:value="updateOpenApiEditor('openErrorDataText', $event)"
                      language="json"
                      height="220px" /></el-form-item
                ></el-col>
              </el-row>
            </el-form>
          </div>

          <div class="open-api-section open-api-switches">
            <el-switch :disabled="!canEditDraft"
              v-model="openApiForm.recordTrace"
              active-text="记录调用链"
            />
            <el-switch :disabled="!canEditDraft"
              v-model="openApiForm.returnTrace"
              active-text="允许响应模板引用 trace"
            />
            <span class="open-api-help"
              >关闭记录可减少日志体积；只有需要下游查看明细时才开启返回。</span
            >
          </div>

        </div>
      </el-tab-pane>

      <el-tab-pane name="api-scenarios">
        <template v-slot:label>
          <span
            ><el-icon><el-icon-document-checked /></el-icon> API 测试用例</span
          >
        </template>
        <api-scenario-panel v-if="rule.id" :rule="rule" :execution-source="selectedTestExecutionSource" :execution-source-name="lifecycleRevision.id ? revisionOptionLabel(lifecycleRevision) : ''" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      title="开放接口响应预览"
      v-model="openApiPreviewVisible"
      width="900px"
      :close-on-click-modal="false"
    >
      <el-alert
        title="预览使用示例占位值，不会执行规则或发起外数请求。成功与异常响应始终使用同一个平台外层结构。"
        type="success"
        :closable="false"
        show-icon
      />
      <el-row :gutter="16" class="open-api-preview-grid">
        <el-col :md="12" :sm="24">
          <div class="open-api-preview-title">正常响应（HTTP 200）</div>
          <pre>{{ formatOpenJson(openApiSuccessPreview) }}</pre>
        </el-col>
        <el-col :md="12" :sm="24">
          <div class="open-api-preview-title">异常响应（HTTP 400）</div>
          <pre>{{ formatOpenJson(openApiErrorPreview) }}</pre>
        </el-col>
      </el-row>
      <template v-slot:footer>
        <span
          ><el-button
            size="small"
            type="primary"
            @click="openApiPreviewVisible = false"
            >关闭</el-button
          ></span
        >
      </template>
    </el-dialog>

    <!-- 规则测试对话框 -->
    <el-dialog
      title="规则测试"
      v-model="testVisible"
      width="900px"
      :close-on-click-modal="false"
    >
      <el-alert v-if="testExecutionSource" :title="executionSourceLabel(testExecutionSource, testExecutionLabel || testSchemaIdentity)" type="info" :closable="false" show-icon style="margin-bottom: 12px" />
      <el-alert v-if="testSourceError" :title="testSourceError" type="error" :closable="false" show-icon>
        <el-button size="small" @click="openTestDialog">重新加载测试字段</el-button>
      </el-alert>
      <div
        v-else-if="!testReady"
        style="padding: 40px; text-align: center; color: var(--tianshu-text-tertiary)"
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
          <span style="color: var(--tianshu-text-secondary)">页面请求超时</span>
          <el-input-number
            v-model="requestTimeoutMs"
            :min="1000"
            :max="1800000"
            :step="1000"
            size="small"
            style="width: 150px"
          />
          <span style="color: var(--tianshu-text-tertiary)">毫秒</span>
          <el-button
            size="small"
            type="primary"
            :icon="ElIconVideoPlay"
            :loading="testExecuting"
            @click="doTest"
            >执行测试</el-button
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

        <div v-if="testMode === 'manual'" class="test-form-wrapper">
          <div v-if="testFields.length > 0" class="test-form-grid">
            <div
              v-for="field in testFields"
              :key="fieldParamKey(field)"
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
                v-model="testParams[fieldParamKey(field)]"
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
                v-model="testParams[fieldParamKey(field)]"
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
                v-model="testParams[fieldParamKey(field)]"
                style="width: 100%"
              >
                <el-option label="true" :value="true" />
                <el-option label="false" :value="false" />
              </el-select>
              <el-date-picker
                v-else-if="field.fieldType === 'DATE'"
                v-model="testParams[fieldParamKey(field)]"
                type="date"
                placeholder="选择日期"
                style="width: 100%"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
              />
              <el-input
                v-else
                v-model="testParams[fieldParamKey(field)]"
                placeholder="输入值"
              />
              <div class="test-field-hint">{{ fieldParamKey(field) }}</div>
            </div>
          </div>
          <div
            v-else
            style="text-align: center; padding: 30px 0; color: var(--tianshu-text-tertiary)"
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
            style="color: var(--tianshu-danger-text); font-size: 12px; margin-top: 4px"
          >
            {{ jsonError }}
          </div>
        </div>

        <div v-if="testResult" style="margin-top: 16px">
          <el-alert v-if="testResultInputChanged" title="输入已修改，以下结果来自上次提交的参数，请重新测试当前输入。" type="warning" :closable="false" show-icon style="margin-bottom: 8px" />
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
          </el-alert>
          <div
            v-if="testResult.errorMessage || testResult.error"
            style="color: var(--tianshu-danger-text); margin-bottom: 8px"
          >
            {{ testResult.errorMessage || testResult.error }}
          </div>
          <div
            v-if="testResult.message"
            style="color: var(--tianshu-warning-text); margin-bottom: 8px"
          >
            {{ testResult.message }}
          </div>
          <pre
            v-if="testResult.hasOutput"
            style="
              background: var(--tianshu-bg-muted);
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

    <el-dialog
      title="版本历史"
      v-model="versionVisible"
      width="96%"
      top="4vh"
      custom-class="version-history-dialog"
      :close-on-click-modal="false"
    >
      <el-table show-overflow-tooltip
        :data="versions"
        border
        size="small"
        v-loading="versionLoading"
        max-height="240"
      >
        <el-table-column prop="version" label="版本" width="140" align="center">
          <template v-slot="{ row }">{{ versionDisplayLabel(row) }}</template>
        </el-table-column>
        <el-table-column prop="changeLog" label="变更说明" min-width="180">
          <template v-slot="{ row }">{{ row.changeLog || '-' }}</template>
        </el-table-column>
        <el-table-column prop="publishBy" label="发布人" width="120">
          <template v-slot="{ row }">{{ row.publishBy || '-' }}</template>
        </el-table-column>
        <el-table-column prop="publishTime" label="发布时间" width="170">
          <template v-slot="{ row }">{{
            formatVersionTime(row.publishTime)
          }}</template>
        </el-table-column>
        <el-table-column fixed="right" class-name="table-operation-column" :show-overflow-tooltip="false" label="操作" :width="operationColumnWidth(['对比上一版', '恢复并编辑', '查看设计'])" align="center">
          <template v-slot="{ row, $index }">
            <el-button
              link
              size="small"
              type="info"
              :disabled="$index === versions.length - 1"
              @click="compareVersion(row, versions[$index + 1])"
              >对比上一版</el-button
            >
            <el-button
              link
              size="small"
              type="warning"
              :loading="forkingDesignerSource"
              :disabled="forkingDesignerSource"
              @click="restoreHistoricalVersion(row)"
              >恢复并编辑</el-button
            >
            <el-button
              link
              size="small"
              type="info"
              :disabled="forkingDesignerSource"
              @click="openVersionDesigner(row)"
              >查看设计</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <div v-if="versions.length >= 2" class="version-compare-toolbar">
        <span class="version-compare-toolbar-label">基准版本</span>
        <el-select
          v-model="leftVersionNumber"
          size="small"
          @change="loadSelectedVersionCompare"
        >
          <el-option
            v-for="item in versions"
            :key="'left-' + item.version"
            :label="versionDisplayLabel(item)"
            :value="item.version"
            :disabled="item.version === rightVersionNumber"
          />
        </el-select>
        <el-button
          size="small"
          :icon="ElIconSort"
          :disabled="versionCompareLoading"
          @click="swapVersionCompare"
          >交换版本</el-button
        >
        <span class="version-compare-toolbar-label">对比版本</span>
        <el-select
          v-model="rightVersionNumber"
          size="small"
          @change="loadSelectedVersionCompare"
        >
          <el-option
            v-for="item in versions"
            :key="'right-' + item.version"
            :label="versionDisplayLabel(item)"
            :value="item.version"
            :disabled="item.version === leftVersionNumber"
          />
        </el-select>
        <span class="version-compare-toolbar-tip"
          >默认按旧版在左、新版在右展示</span
        >
      </div>
      <el-alert
        v-else-if="!versionLoading"
        title="至少需要两个已发布版本才能进行对比"
        type="info"
        :closable="false"
        show-icon
        class="version-compare-empty"
      />
      <div v-loading="versionCompareLoading" class="version-compare-content">
        <rule-version-diff
          v-if="versionCompare && versionCompare.left && versionCompare.right"
          :model-type="rule.modelType"
          :left-version="versionCompare.left"
          :right-version="versionCompare.right"
            :references="ruleVersionReferences"
            :functions="idempotencyFunctions"
        />
        <div
          v-else-if="versions.length >= 2 && !versionCompareLoading"
          class="version-compare-placeholder"
        >
          请选择两个版本查看业务配置差异
        </div>
      </div>
      <div v-if="versionCompare && versionCompare.left && versionCompare.right">
        <el-collapse class="version-tech-collapse">
          <el-collapse-item title="技术内容（原始 JSON / 编译脚本）" name="raw">
            <div class="version-compare-grid">
              <div>
                <div class="version-compare-title">左侧模型 JSON</div>
                <pre>{{
                  formatVersionJson(versionCompare.left.modelJson)
                }}</pre>
              </div>
              <div>
                <div class="version-compare-title">右侧模型 JSON</div>
                <pre>{{
                  formatVersionJson(versionCompare.right.modelJson)
                }}</pre>
              </div>
              <div>
                <div class="version-compare-title">左侧编译脚本</div>
                <pre>{{ versionCompare.left.compiledScript || '' }}</pre>
              </div>
              <div>
                <div class="version-compare-title">右侧编译脚本</div>
                <pre>{{ versionCompare.right.compiledScript || '' }}</pre>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
      <template v-slot:footer>
        <div>
          <el-button size="small" @click="versionVisible = false"
            >关闭</el-button
          >
        </div>
      </template>
    </el-dialog>
    <artifact-deployment-dialog
      v-if="importedArtifact.artifactId"
      v-model="deploymentVisible"
      :artifact-id="importedArtifact.artifactId"
      :binding-component-ids="importedArtifact.requiredBindingComponentIds || []"
      @deploy="handleArtifactDeploy"
    />
    </div>
  </div>
</template>

<script>
import { loadPagedCatalog } from '@/utils/pagedCatalog'
import { PLATFORM_CODES } from '@/utils/platformResponse'

import { chooseVersionRestore, versionDisplayLabel } from '@/utils/versionRestore'
import workspaceTabTitleMixin from '@/mixins/workspaceTabTitleMixin'
import { markRaw } from 'vue'
import {
  ArrowDown as ElIconArrowDown,
  Edit as ElIconEdit,
  ArrowUp as ElIconArrowUp,
  Connection as ElIconConnection,
  DocumentChecked as ElIconDocumentChecked,
  VideoPlay as ElIconVideoPlay,
  Back as ElIconBack,
  Refresh as ElIconRefresh,
  View as ElIconView,
  Plus as ElIconPlus,
  Delete as ElIconDelete,
  Sort as ElIconSort,
  Setting as ElIconSetting,
} from '@element-plus/icons-vue'
import * as api from '@/api/definition'
import { isRequestErrorNotified } from '@/api/request'
import {
  listVariablesByProject,
  listVariables,
  listAvailableFieldValidations,
} from '@/api/variable'
import { getVariableTree } from '@/api/dataObject'
import { getModel, listAllModelsByProject } from '@/api/model'
import { sampleValueForVarType, setPathValue } from '@/utils/testParamTemplate'
import {
  buildDetailReferenceMap,
  buildDetailReferenceState,
  buildReferenceCatalog,
  resolveDetailReference,
} from '@/utils/referenceCatalog'
import { formatTestOutput, normalizeTestResult } from '@/utils/testResult'
import { revisionTestSource, executionSourceKey, executionSourceLabel, requireExecutionIdentity } from '@/utils/ruleTestExecutionSource'
import {
  normalizeTestSchema,
  schemaFieldsToTestFields,
  flattenSchemaSample,
} from '@/utils/testSchema'
import ApiScenarioPanel from '@/components/rule/ApiScenarioPanel'
import RuleVersionDiff from '@/components/rule/versionDiff/RuleVersionDiff.vue'
import RuleLifecyclePanel from '@/components/rule/RuleLifecyclePanel.vue'
import RuleValidationReport from '@/components/rule/RuleValidationReport.vue'
import RuleLifecycleTimeline from '@/components/rule/RuleLifecycleTimeline.vue'
import RuleFieldHierarchyDisplay from '@/components/rule/RuleFieldHierarchyDisplay.vue'
import RuleRevisionFieldsPanel from '@/components/rule/RuleRevisionFieldsPanel.vue'
import ConfigLayerGuide from '@/components/common/ConfigLayerGuide.vue'
import VarPicker from '@/components/common/VarPicker.vue'
import ProtocolPathPicker from '@/components/common/ProtocolPathPicker.vue'
import { samplePathOptions } from '@/utils/apiExecution'
import { isRestrictedJsonPath } from '@/utils/restrictedJsonPath'
import ArtifactDeploymentDialog from '@/components/artifact/ArtifactDeploymentDialog.vue'
import ResourceVersionManager from '@/components/common/ResourceVersionManager.vue'
import { operationColumnWidth as getOperationColumnWidth } from '@/utils/operationColumnWidth'
import OperandPicker from '@/components/common/OperandPicker.vue'
import { OPERAND_KINDS, collectOperandReferences, validateOperand } from '@/utils/operand'
import { formatExpressionFormula } from '@/utils/expressionDisplay'
import * as artifactApi from '@/api/artifact'
import { resolveDesignerRouteName } from '@/utils/ruleDesignerNavigation'

const IDEMPOTENCY_PURE_FUNCTIONS = [
  ['strTrim', '去除首尾空白', [{ name: 'text', type: 'STRING', example: '  A  ' }]],
  ['strUpper', '转大写', [{ name: 'text', type: 'STRING', example: 'a' }]],
  ['strLower', '转小写', [{ name: 'text', type: 'STRING', example: 'A' }]],
  ['strReplace', '正则替换', [
    { name: 'text', type: 'STRING', example: 'A-1' },
    { name: 'regex', type: 'STRING', example: '-' },
    { name: 'replacement', type: 'STRING', example: '' },
  ]],
  ['strSubstring', '截取文本', [
    { name: 'text', type: 'STRING', example: 'ABC' },
    { name: 'start', type: 'NUMBER', example: 0 },
    { name: 'end', type: 'NUMBER', example: 2 },
  ]],
  ['toJson', '对象转 JSON', [{ name: 'value', type: 'OBJECT', example: {} }]],
  ['md5', 'MD5 摘要', [{ name: 'text', type: 'STRING', example: 'A' }]],
  ['sha256', 'SHA-256 摘要', [{ name: 'text', type: 'STRING', example: 'A' }]],
].map(([functionCode, functionName, params]) => ({
  functionCode,
  funcCode: functionCode,
  functionName,
  funcName: functionName,
  params,
  parameterCount: params.length,
  parameterTypes: params.map(item => item.type),
}))

let idempotencyComponentSequence = 0

function createIdempotencyComponent(operand = null) {
  return {
    _uid: `idempotency-component-${++idempotencyComponentSequence}`,
    operand: operand || null,
  }
}

const MODEL_TYPE_LABELS = {
  TABLE: '决策表',
  TREE: '决策树',
  FLOW: '决策流',
  RULE_SET: '规则集',
  CROSS: '交叉表',
  SCORE: '评分卡',
  CROSS_ADV: '复杂交叉表',
  SCORE_ADV: '复杂评分卡',
  SCRIPT: 'QL 脚本',
}

const LIFECYCLE_ACTIONS_BY_STATE = {
  DRAFT: ['preflight', 'submit'],
  REVIEW: ['reject', 'approve'],
  APPROVED: ['publish', 'download', 'migration-download'],
  PUBLISHED: ['download', 'migration-download', 'offline'],
  OFFLINE: ['download', 'migration-download'],
}

function normalizeDefinitionId(value) {
  const id = String(value ?? '').trim()
  return /^[1-9]\d*$/.test(id) ? value : null
}


function createDefaultOpenApiContract() {
  return {
    enabled: false,
    recordTrace: false,
    returnTrace: false,
    requestMappings: [],
    responseMappings: [],
    successDataTemplate: '${response}',
    errorDataTemplate: {
      errorCode: '${status.code}',
      errorMessage: '${status.message}',
    },
    responseHeaders: {},
    idempotency: {
      enabled: false,
      operand: null,
      components: [],
      ttlSeconds: 86400,
    },
  }
}

export default {
  data() {
    return {
      openRequestSampleText: '{}',
      openRequestSamplePaths: [],
      openRequestSampleError: '',
      loading: false,
      loadError: '',
      detailLoadRequestId: 0,
      detailActive: true,
      detailRoutePath: '',
      rule: {},
      ruleContent: {},
      activeDetailTab: 'lifecycle',
      fieldViewMode: 'workspace',
      revisions: [],
      activeRevision: {},
      lifecycleRevisionId: null,
      publishedRevision: {},
      preflightReport: null,
      preflightRequestId: 0,
      lifecycleEvents: [],
      repairPreview: {},
      repairPreviewLoading: false,
      repairSaving: false,
      forkingDesignerSource: false,
      designerForkRequestId: 0,
      designerForkActive: true,
      importedArtifact: {},
      deploymentVisible: false,
      openApiSaving: false,
      openApiPreviewVisible: false,
      openApiSuccessPreview: {},
      openApiErrorPreview: {},
      openApiForm: createDefaultOpenApiContract(),
      openSuccessDataText: '',
      openErrorDataText: '',
      openResponseHeadersText: '',
      openTargetTypes: [
        'STRING',
        'NUMBER',
        'DECIMAL',
        'INTEGER',
        'INT',
        'LONG',
        'DOUBLE',
        'BOOLEAN',
        'BOOL',
        'DATE',
        'DATETIME',
        'OBJECT',
        'LIST',
        'ARRAY',
        'MAP',
      ],
      fieldValidationOptions: [],
      /** varId -> 变量对象映射 */
      varMap: {},
      /** VarPicker 分层下拉选项（普通变量 / 常量 / 数据对象字段） */
      varPickerGroups: [
        { label: '普通变量', options: [] },
        { label: '常量', options: [] },
        { label: '数据对象字段', options: [] },
        { label: '模型', options: [] },
      ],
      baseEditVisible: false,
      baseSaving: false,
      baseForm: {
        ruleName: '',
        description: '',
      },
      fieldPageSize: 20,
      inputFieldPage: 1,
      outputFieldPage: 1,
      // 测试相关
      testVisible: false,
      testReady: false,
      testSourceError: '',
      testExecutionSource: null,
      testSchemaIdentity: null,
      testExecutionLabel: '',
      testExecutedInputJson: '',
      testSchemaRequestId: 0,
      testExecutionRequestId: 0,
      testMode: 'manual',
      testFields: [],
      testParams: {},
      testFieldKeyMap: {},
      testJsonStr: '{}',
      jsonEdited: false,
      jsonError: '',
      testExecuting: false,
      testResult: null,
      testDialogKey: 1,
      requestTimeoutMs: 180000,
      versionVisible: false,
      versionLoading: false,
      versions: [],
      versionCompare: null,
      leftVersionNumber: null,
      rightVersionNumber: null,
      versionCompareLoading: false,
      versionCompareRequestId: 0,
      ElIconEdit: markRaw(ElIconEdit),
      ElIconVideoPlay: markRaw(ElIconVideoPlay),
      ElIconBack: markRaw(ElIconBack),
      ElIconRefresh: markRaw(ElIconRefresh),
      ElIconView: markRaw(ElIconView),
      ElIconPlus: markRaw(ElIconPlus),
      ElIconDelete: markRaw(ElIconDelete),
      ElIconSort: markRaw(ElIconSort),
      ElIconSetting: markRaw(ElIconSetting),
    }
  },
  components: {
    ApiScenarioPanel,
    RuleVersionDiff,
    RuleLifecyclePanel,
    RuleValidationReport,
    RuleLifecycleTimeline,
    RuleFieldHierarchyDisplay,
    RuleRevisionFieldsPanel,
    ConfigLayerGuide,
    VarPicker,
    ProtocolPathPicker,
    ArtifactDeploymentDialog,
    ResourceVersionManager,
    OperandPicker,
    ElIconSetting,
    ElIconArrowDown,
    ElIconEdit,
    ElIconArrowUp,
    ElIconConnection,
    ElIconDocumentChecked,
  },
  name: 'RuleDetail',
  mixins: [workspaceTabTitleMixin(vm => vm.rule.ruleName)],
  activated() {
    const resuming = !this.detailActive
    this.detailActive = true
    if (resuming) this.syncDetailTabFromRoute()
    if (resuming && this.loading) this.load()
  },
  deactivated() {
    this.detailActive = false
    this.detailLoadRequestId++
    this.preflightRequestId++
    this.invalidateTestRequests()
  },
  beforeUnmount() {
    this.detailActive = false
    this.detailLoadRequestId++
    this.preflightRequestId++
    this.designerForkActive = false
    this.designerForkRequestId++
    this.invalidateTestRequests()
  },
  computed: {
    detailDefinitionId() {
      const id = this.$route?.params?.id
      if (!this.detailActive || (this.detailRoutePath && this.detailRoutePath !== this.$route?.path)) return null
      return normalizeDefinitionId(id)
    },
    selectedTestExecutionSource() {
      return revisionTestSource(this.lifecycleRevision) || { sourceType: 'WORKSPACE' }
    },
    testSelectionKey() {
      return JSON.stringify([this.rule.id, executionSourceKey(this.selectedTestExecutionSource)])
    },
    testResultInputChanged() {
      if (!this.testResult || !this.testExecutedInputJson) return false
      try {
        const current = this.testMode === 'json' ? JSON.parse(this.testJsonStr) : this.buildNestedTestParams(this.testFields, this.testParams)
        return JSON.stringify(current) !== this.testExecutedInputJson
      } catch { return true }
    },
    lifecycleRevision() {
      return (
        this.revisions.find((item) => String(item.id) === String(this.lifecycleRevisionId)) || {}
      )
    },
    selectedRevisionReadonly() {
      return this.lifecycleRevision.id
        ? this.lifecycleRevision.state !== 'DRAFT' || String(this.lifecycleRevision.id) !== String(this.activeRevision.id)
        : this.rule.status === 1
    },
    hasEditableWorkspace() {
      return !this.loading && !this.loadError && Boolean(this.activeRevision.id && this.activeRevision.state === 'DRAFT')
    },
    canEditDraft() {
      return this.hasEditableWorkspace && String(this.lifecycleRevisionId) === String(this.activeRevision.id)
    },
    inputFieldsTotal() {
      return this.rule && this.rule.inputFieldsJson
        ? this.rule.inputFieldsJson.length
        : 0
    },
    outputFieldsTotal() {
      return this.rule && this.rule.outputFieldsJson
        ? this.rule.outputFieldsJson.length
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
    pagedRuleInputFields() {
      const fields = (this.rule && this.rule.inputFieldsJson) || []
      if (!this.inputFieldNeedsPaging) return fields
      return fields.slice(
        this.inputFieldOffset,
        this.inputFieldOffset + this.fieldPageSize
      )
    },
    pagedRuleOutputFields() {
      const fields = (this.rule && this.rule.outputFieldsJson) || []
      if (!this.outputFieldNeedsPaging) return fields
      return fields.slice(
        this.outputFieldOffset,
        this.outputFieldOffset + this.fieldPageSize
      )
    },
    openInputOptions() {
      return ((this.rule && this.rule.inputFieldsJson) || [])
        .map((field) => {
          const refType = field.refType || 'VARIABLE'
          const varId = field.varId
          return {
            value: varId ? refType + ':' + varId : '',
            refId: varId,
            label: this.openApiFieldOptionLabel(field),
            ...this.openApiFieldDisplay(field),
            scriptName: field.scriptName || '',
            externalName:
              field.fieldName ||
              String(field.scriptName || '')
                .split('.')
                .pop(),
            targetType: this.openMappingTargetType(field.fieldType),
          }
        })
        .filter((item) => item.value)
    },
    openInputPickerItems() {
      return this.openApiPickerItems(this.rule && this.rule.inputFieldsJson)
    },
    openIdempotencyOptions() {
      return this.openInputOptions.map((item) => ({
        ...item,
        id: item.refId,
        refType: String(item.value).split(':')[0],
        varCode: item.scriptName,
        varLabel: item.label,
        varType: item.targetType,
      }))
    },
    idempotencyFunctions() {
      return IDEMPOTENCY_PURE_FUNCTIONS
    },
    ruleVersionReferences() {
      return Object.values(this.varMap || {})
    },
    idempotencyFormulaPreview() {
      return (this.openApiForm.idempotency?.components || []).map((item, index) => ({
        index: index + 1,
        formula: item && item.operand
          ? formatExpressionFormula(item.operand)
          : '',
      }))
    },
    idempotencyOperandKinds() {
      return [
        OPERAND_KINDS.LITERAL,
        OPERAND_KINDS.REFERENCE,
        OPERAND_KINDS.OPERATION,
        OPERAND_KINDS.ACCESS,
        OPERAND_KINDS.CAST,
        OPERAND_KINDS.ARRAY,
      ]
    },
    openOutputOptions() {
      return ((this.rule && this.rule.outputFieldsJson) || [])
        .map((field) => {
          const refType = field.refType || 'VARIABLE'
          const varId = field.varId
          return {
            value: varId ? refType + ':' + varId : '',
            label: this.openApiFieldOptionLabel(field),
            ...this.openApiFieldDisplay(field),
            scriptName: field.scriptName || '',
          }
        })
        .filter((item) => item.value)
    },
    openOutputPickerItems() {
      return this.openApiPickerItems(this.rule && this.rule.outputFieldsJson)
    },
    openApiBodyPathOptions() {
      return this.openRequestSamplePaths
    },
    openApiHeaderPathOptions() {
      return [...new Set((this.openApiForm.requestMappings || []).filter(item => item.sourceType === 'HEADER').map(item => item.sourcePath).filter(Boolean))].map(value => ({ value, label: value, type: 'STRING' }))
    },
    openOutputPlaceholders() {
      return ((this.rule && this.rule.outputFieldsJson) || [])
        .map((field) => {
          const refType = field.refType || 'VARIABLE'
          return field.varId
            ? '${output.' + refType + '.' + field.varId + '}'
            : ''
        })
        .filter(Boolean)
    },
    releaseChecklist() {
      const inputs = (this.rule && this.rule.inputFieldsJson) || []
      const outputs = (this.rule && this.rule.outputFieldsJson) || []
      const hasBindings = inputs.length > 0 && outputs.length > 0
        && inputs.every(field => field && field.varId)
        && outputs.every(field => field && field.varId)
      const hasDesign = Boolean(
        (this.activeRevision && this.activeRevision.modelJson)
        || (this.ruleContent && this.ruleContent.modelJson)
      )
      const preflightPassed = Boolean(this.activeRevision.id && this.activeRevision.state === 'DRAFT'
        && this.preflightReport?.valid && String(this.preflightReport.revisionId) === String(this.activeRevision.id)
        && this.preflightReport.lockVersion != null && this.activeRevision.lockVersion != null
        && String(this.preflightReport.lockVersion) === String(this.activeRevision.lockVersion))
      const tested = this.testResult?.executionIdentity
      const testPassed = Boolean(this.testResult?.success && tested?.sourceType === 'REVISION'
        && String(tested.definitionId) === String(this.rule.id)
        && String(tested.sourceId) === String(this.activeRevision.id)
        && tested.lockVersion != null && String(tested.lockVersion) === String(this.activeRevision.lockVersion))
      const published = this.lifecycleRevision && this.lifecycleRevision.state === 'PUBLISHED'
      const steps = [
        { key: 'base', label: '基础信息', done: Boolean(this.rule.id && this.rule.ruleCode && this.rule.ruleName), detail: '规则编码、名称和模型类型' },
        { key: 'fields', label: '输入输出', done: hasBindings, detail: `${inputs.length} 个输入 / ${outputs.length} 个输出均已绑定` },
        { key: 'design', label: '设计内容', done: hasDesign, detail: hasDesign ? '已保存当前修订内容' : '进入设计器完成条件和动作' },
        { key: 'preflight', label: '发布前校验', done: preflightPassed, detail: preflightPassed ? '依赖、Schema、编译均已通过' : '执行发布前校验并修复阻断项' },
        { key: 'test', label: '测试样例', done: testPassed, detail: testPassed ? '最近一次测试执行成功' : '执行一次规则测试并核对结果' },
        { key: 'publish', label: '审批发布', done: published, detail: published ? `线上版本 v${this.rule.publishedVersion || ''}` : '提交审批、批准后发布' },
      ]
      return steps.map((item, index) => ({ ...item, index: index + 1, status: item.done ? 'READY' : 'PENDING' }))
    },
    releaseProgressCompleted() {
      return this.releaseChecklist.filter(item => item.done).length
    },
    releaseProgressSummary() {
      const next = this.releaseChecklist.find(item => !item.done)
      return next ? `下一步：${next.label}` : '当前规则已具备发布条件'
    },
  },
  created() {
    this.detailRoutePath = this.$route.path || ''
    this.syncDetailTabFromRoute()
    this.load()
  },
  watch: {
    testSelectionKey() {
      this.invalidateTestRequests()
      this.testResult = null
      if (this.testVisible) this.openTestDialog()
    },
    testVisible(visible) {
      if (!visible) this.invalidateTestRequests()
    },
    '$route.params.id'(id, oldId) {
      if (this.ownsDetailRoute() && id && id !== oldId) {
        this.inputFieldPage = 1
        this.outputFieldPage = 1
        this.load()
      }
    },
    '$route.query.focus'() {
      if (this.ownsDetailRoute()) this.syncDetailTabFromRoute()
    },
    lifecycleRevisionId() {
      this.preflightRequestId++
      this.preflightReport = null
      if (this.lifecycleRevision.id && this.selectedRevisionReadonly) this.fieldViewMode = 'revision'
    },
  },
  methods: {
    importOpenRequestSample() {
      try {
        const sample = JSON.parse(this.openRequestSampleText)
        this.openRequestSamplePaths = samplePathOptions(sample, '$').map(path => ({ ...path, disabled: !isRestrictedJsonPath(path.value) }))
        this.openRequestSampleError = this.openRequestSamplePaths.some(path => path.disabled) ? '部分字段名不符合开放接口的受限 JSONPath，已保留展示并禁用选择。' : ''
      } catch { this.openRequestSampleError = '请输入合法 JSON 请求样例' }
    },
    operationColumnWidth(labels) { return getOperationColumnWidth(labels) },
    versionDisplayLabel,
    executionSourceLabel,
    invalidateTestRequests() {
      this.testSchemaRequestId++
      this.testExecutionRequestId++
      this.testExecuting = false
    },
    showRevisionFields() {
      if (!this.lifecycleRevisionId) this.lifecycleRevisionId = this.activeRevision.id || this.revisions[0]?.id || null
      this.fieldViewMode = 'revision'
      if (!['inputs', 'outputs'].includes(this.activeDetailTab)) this.activeDetailTab = 'inputs'
    },
    showWorkspaceFields() {
      if (!this.hasEditableWorkspace) return
      this.lifecycleRevisionId = this.activeRevision.id
      this.fieldViewMode = 'workspace'
    },
    async locatePreflightIssue(issue) {
      const revisionId = (this.preflightReport && this.preflightReport.revisionId) || this.lifecycleRevision.id
      if (!revisionId || (issue.revisionId && String(issue.revisionId) !== String(revisionId))) {
        this.$message.warning('校验结果不属于当前修订，请重新校验后定位')
        return
      }
      const routeName = this.designerRouteName(this.rule.modelType)
      if (!routeName) { this.$message.warning('当前类型没有可用设计器，请根据校验说明修复配置'); return }
      await this.$router.push({ name: routeName, params: { id: String(this.rule.id) }, query: {
        sourceType: 'REVISION', sourceId: String(revisionId),
        validationSourceId: String(revisionId),
        validationLockVersion: String(this.preflightReport?.lockVersion ?? this.lifecycleRevision.lockVersion ?? ''),
        validationPath: issue.path || '$', validationMessage: issue.message || '请核对规则配置',
      } })
    },
    syncDetailTabFromRoute() {
      const focus = this.$route && this.$route.query && this.$route.query.focus
      if (focus === 'lifecycle') this.activeDetailTab = 'lifecycle'
    },
    openApiFieldDisplay(field) {
      return {
        fieldName:
          field.fieldLabel ||
          field.fieldName ||
          field.scriptName ||
          '未命名字段',
        fieldCode: field.scriptName || field.fieldName || '—',
        fieldType: field.fieldType || 'OBJECT',
      }
    },
    openApiPickerItems(fields) {
      return (fields || [])
        .filter((field) => field && field.varId)
        .map((field) => {
          const refType = field.refType || 'VARIABLE'
          const category = {
            CONSTANT: 'constant',
            DATA_OBJECT: 'object',
            DATA_OBJECT_FIELD: 'object',
            MODEL: 'model',
            MODEL_OUTPUT: 'model',
            EXTERNAL_API: 'external',
          }[String(refType).toUpperCase()] || 'standalone'
          const display = this.openApiFieldDisplay(field)
          return {
            ...field,
            id: field.varId,
            _varId: field.varId,
            _refType: refType,
            varCode: display.fieldCode,
            varLabel: display.fieldName,
            varLabelText: display.fieldName,
            varType: display.fieldType,
            _ref: {
              ...(field._ref || {}),
              category,
              refType,
            },
          }
        })
    },
    openApiFieldOptionLabel(field) {
      const display = this.openApiFieldDisplay(field)
      return `${display.fieldName}（${display.fieldCode}） · ${display.fieldType}`
    },
    openApiFieldOption(options, value) {
      return (options || []).find((item) => item.value === value) || null
    },
    ownsDetailRoute() {
      return this.detailActive && (!this.detailRoutePath || this.detailRoutePath === this.$route.path)
    },
    isCurrentDetailLoad(requestId, definitionId = this.$route.params.id) {
      return this.ownsDetailRoute() && requestId === this.detailLoadRequestId && String(definitionId) === String(this.$route.params.id)
    },
    async load() {
      const id = normalizeDefinitionId(this.$route?.params?.id)
      if (!id || !this.ownsDetailRoute()) return
      const requestId = ++this.detailLoadRequestId
      this.preflightRequestId++
      this.preflightReport = null
      this.testResult = null
      this.loading = true
      this.loadError = ''
      try {
        // 注意：request 拦截器已展开 R.data，生产环境 res 是对象本身；测试环境 mock 返回 {data: {...}} 需要 .data
        const res = await api.getDefinitionDetail(id)
        if (!this.isCurrentDetailLoad(requestId, id)) return
        const nextRule = (res.data !== undefined ? res.data : res) || {}
        if (nextRule.inputFieldsJson) {
          nextRule.inputFieldsJson.forEach((f) => {
            f._editing = false
            f.validationRuleIdList = this.parseValidationRuleIds(
              f.validationRuleIds
            )
          })
        }
        if (nextRule.outputFieldsJson) {
          nextRule.outputFieldsJson.forEach((f) => {
            f._editing = false
          })
        }
        this.rule = nextRule
        const contentRes = await api.getContent(id)
        if (!this.isCurrentDetailLoad(requestId, id)) return
        this.ruleContent =
          (contentRes && contentRes.data !== undefined
            ? contentRes.data
            : contentRes) || {}
        this.loadOpenApiConfig(this.ruleContent.openApiConfigJson)
        this.normalizeFieldPages()
        await this.loadLifecycle(id, requestId)
        if (!this.isCurrentDetailLoad(requestId, id)) return
        if (this.activeRevision.state === 'DRAFT' || !this.revisions.length) {
          await Promise.all([this.loadVars(requestId), this.loadFieldValidationOptions(requestId)])
        } else {
          this.varMap = {}
          this.varPickerGroups.splice(0)
          this.fieldValidationOptions = []
        }
      } catch (e) {
        if (this.isCurrentDetailLoad(requestId, id)) this.loadError = e.message || '加载规则详情失败'
      } finally {
        if (this.isCurrentDetailLoad(requestId, id)) this.loading = false
      }
    },
    async loadLifecycle(definitionId, requestId = this.detailLoadRequestId) {
      const [revisionsResponse, timelineResponse] = await Promise.all([
        api.listRuleRevisions(definitionId),
        api.getRuleLifecycleTimeline(definitionId),
      ])
      if (!this.isCurrentDetailLoad(requestId, definitionId)) return
      const revisionData = this.unwrapData(revisionsResponse)
      const revisions = Array.isArray(revisionData)
        ? revisionData.filter(Boolean)
        : []
      this.revisions = revisions
      this.activeRevision =
        revisions.find((item) => item.state === 'DRAFT') ||
        revisions.find((item) => item.state === 'REVIEW') ||
        revisions.find((item) => item.state === 'APPROVED') ||
        revisions.find((item) => item.state === 'PUBLISHED') ||
        revisions.find((item) => item.state === 'OFFLINE') ||
        revisions[0] ||
        {}
      this.lifecycleRevisionId = this.activeRevision.id || null
      this.fieldViewMode = this.activeRevision.id && this.activeRevision.state !== 'DRAFT' ? 'revision' : 'workspace'
      this.publishedRevision =
        revisions.find((item) => item.state === 'PUBLISHED') || {}
      this.lifecycleEvents = this.unwrapData(timelineResponse) || []
      this.preflightReport = null
      this.activateDraftRevision(this.activeRevision, false)
    },
    unwrapData(response) {
      return response && response.data !== undefined ? response.data : response
    },
    lifecycleStateLabel(state) {
      return (
        {
          DRAFT: '草稿',
          REVIEW: '评审中',
          APPROVED: '已批准',
          REJECTED: '已驳回',
          PUBLISHED: '已发布',
          OFFLINE: '已下线',
        }[state] ||
        state ||
        '—'
      )
    },
    revisionOptionLabel(revision) {
      return (
        'v' +
        (revision.revisionNo || '—') +
        ' · ' +
        this.lifecycleStateLabel(revision.state)
      )
    },
    replaceRevision(revision) {
      if (!revision || !revision.id) return
      const index = this.revisions.findIndex((item) => item.id === revision.id)
      if (index >= 0) this.revisions.splice(index, 1, revision)
      else this.revisions.unshift(revision)
      if (revision.state === 'DRAFT') this.activeRevision = revision
      if (revision.state === 'PUBLISHED') this.publishedRevision = revision
    },
    syncDraftContent(revision) {
      if (!revision || revision.state !== 'DRAFT') return
      const nextContent = { ...this.ruleContent }
      if (Object.prototype.hasOwnProperty.call(revision, 'modelJson')) {
        nextContent.modelJson = revision.modelJson || '{}'
      }
      if (Object.prototype.hasOwnProperty.call(revision, 'openApiConfigJson')) {
        nextContent.openApiConfigJson = revision.openApiConfigJson
      }
      this.ruleContent = nextContent
      this.loadOpenApiConfig(nextContent.openApiConfigJson)
    },
    activateDraftRevision(revision, selectLifecycle = true) {
      if (!revision || revision.state !== 'DRAFT') return
      this.replaceRevision(revision)
      this.activeRevision = revision
      this.syncDraftContent(revision)
      if (selectLifecycle) this.lifecycleRevisionId = revision.id
    },
    showDraftDiagnostics(issues, compileSuccess) {
      const normalizedIssues = Array.isArray(issues) ? issues : []
      if (compileSuccess) {
        this.preflightReport = null
        this.$message.success('草稿已保存并解析')
        return
      }
      this.preflightReport = {
        valid: false,
        errors: normalizedIssues.filter((item) => item.severity !== 'WARNING'),
        warnings: normalizedIssues.filter((item) => item.severity === 'WARNING'),
      }
      this.$message.warning('草稿已保存，但解析存在问题，请查看校验报告')
    },
    async runPreflight() {
      const revision = this.lifecycleRevision
      if (!revision.id) return
      const requestId = ++this.preflightRequestId
      this.preflightReport = null
      const definitionId = this.rule.id, revisionId = revision.id, lockVersion = revision.lockVersion
      const current = () => this.detailActive && requestId === this.preflightRequestId
        && String(this.rule.id) === String(definitionId) && String(this.lifecycleRevisionId) === String(revisionId)
        && this.lifecycleRevision.lockVersion === lockVersion
      const accept = report => {
        if (report?.revisionId == null || String(report.revisionId) !== String(revisionId)
          || report.lockVersion == null || String(report.lockVersion) !== String(lockVersion)) throw new Error('校验结果与所选修订或锁版本不一致，请刷新后重试')
        this.preflightReport = report
      }
      try {
        const response = await api.preflightRuleRevision(definitionId, revisionId)
        if (!current()) return
        const report = this.unwrapData(response)
        accept(report)
      } catch (error) {
        if (!current()) return
        if (error && error.response && error.response.data && error.response.data.data) {
          const report = error.response.data.data
          accept(report)
        } else {
          throw error
        }
      }
    },
    async handleLifecycleAction(payload) {
      const revision = this.lifecycleRevision
      if (!revision.id) {
        this.$message.warning('请先选择要操作的规则修订')
        return
      }
      const action = payload && payload.action
      if (action === 'view-design') {
        await this.openRevisionDesigner(revision)
        return
      }
      if (action === 'edit-design') {
        await this.forkDesignerSource('REVISION', revision.id)
        return
      }
      if (action === 'go-approval' && revision.governanceRequestId) {
        this.$router.push(`/approval/${revision.governanceRequestId}`)
        return
      }
      const allowedActions = LIFECYCLE_ACTIONS_BY_STATE[revision.state] || []
      if (
        !allowedActions.includes(action) ||
        (['download', 'migration-download'].includes(action) && !revision.artifactId)
      ) {
        this.$message.warning('当前修订状态不支持该生命周期操作')
        return
      }
      const revisionId = revision.id
      try {
        if (action === 'preflight') await this.runPreflight()
        else if (action === 'submit') {
          const response = await api.submitRuleRevision(this.rule.id, revisionId, payload)
          const approvalRequestId = response && response.data
            ? response.data.governanceRequestId
            : null
          if (approvalRequestId) {
            this.$message.success('已提交统一审批')
            this.$router.push(`/approval/${approvalRequestId}`)
            return
          }
        }
        else if (action === 'reject') await api.rejectRuleRevision(this.rule.id, revisionId, payload)
        else if (action === 'approve') await api.approveRuleRevision(this.rule.id, revisionId, payload)
        else if (action === 'publish') await api.publishRuleRevision(this.rule.id, revisionId, payload)
        else if (action === 'offline') {
          const response = await api.offlineRuleRevision(this.rule.id, revisionId, payload)
          if (response && response.data && response.data.id) {
            this.$message.success('下线审批草稿已创建')
            this.$router.push(`/approval/${response.data.id}`)
            return
          }
        }
        else if (action === 'migration-download') {
          await this.downloadArtifactMigration(revision.artifactId)
          return
        }
        else if (action === 'download') {
          await this.downloadDecisionArtifact(revision.artifactId)
          return
        }
        if (action !== 'preflight') {
          const messages = {
            submit: '已提交评审',
            reject: '本次申请已驳回，规则继续使用当前生效版本',
            approve: '审批已通过，决策制品已固化',
            publish: '制品已发布，线上版本已更新',
            offline: '线上制品已下线',
          }
          this.$message.success(messages[action] || '生命周期操作已完成')
          await this.loadLifecycle(this.rule.id)
        }
      } catch (error) {
        if (!isRequestErrorNotified(error)) {
          this.$message.error(error.message || '生命周期操作失败')
        }
      }
    },
    designerRouteName(modelType) {
      return resolveDesignerRouteName(modelType)
    },
    async openRevisionDesigner(revision, invalidatePendingFork = true) {
      if (!revision || revision.id === undefined || revision.id === null) return
      return this.openDesignerSource('REVISION', revision.id, invalidatePendingFork)
    },
    async openVersionDesigner(version, invalidatePendingFork = true) {
      if (!version || version.id === undefined || version.id === null) return
      return this.openDesignerSource('VERSION', version.id, invalidatePendingFork)
    },
    async restoreHistoricalVersion(row) {
      const mode = await chooseVersionRestore(row.businessVersion || row.version)
      if (mode) await this.openDesignerSource('VERSION', row.id, true, mode)
    },
    async openDesignerSource(sourceType, sourceId, invalidatePendingFork = true, restoreMode) {
      if (invalidatePendingFork) {
        this.designerForkRequestId++
        this.forkingDesignerSource = false
      }
      const routeName = this.designerRouteName(this.rule.modelType)
      if (!routeName) {
        this.$message.error('当前规则模型类型没有可用的设计器入口')
        return
      }
      await this.$router.push({
        name: routeName,
        params: { id: String(this.rule.id) },
        query: { sourceType, sourceId: String(sourceId), ...(restoreMode ? { restoreMode } : {}) },
      })
      this.versionVisible = false
    },
    async forkDesignerSource(sourceType, sourceId) {
      // 进入编辑只切换到所选版本，暂存由设计页显式触发。
      if (this.forkingDesignerSource) return
      this.forkingDesignerSource = true
      const requestId = ++this.designerForkRequestId
      try {
        return await this.openDesignerSource(sourceType, sourceId, false)
      } finally {
        if (requestId === this.designerForkRequestId) this.forkingDesignerSource = false
      }
    },
    async goToChecklistItem(item) {
      if (!item || item.done) return
      if (item.key === 'fields') {
        this.activeDetailTab = 'inputs'
        return
      }
      if (item.key === 'publish' || item.key === 'preflight') {
        this.activeDetailTab = 'lifecycle'
        return
      }
      if (item.key === 'test') {
        this.openTestDialog()
        return
      }
      const routeName = this.designerRouteName(this.rule.modelType)
      if (routeName) await this.openRevisionDesigner(this.lifecycleRevision)
    },
    async loadRepairPreview() {
      this.repairPreviewLoading = true
      try {
        const response = await api.getRuleRevisionRepairPreview(this.rule.id)
        this.repairPreview = this.unwrapData(response) || {}
      } catch (error) {
        this.$message.error(error.message || '加载历史修复预览失败')
      } finally {
        this.repairPreviewLoading = false
      }
    },
    async createLegacyDraft() {
      const name = this.designerRouteName(this.rule.modelType)
      if (name) await this.$router.push({ name, params: { id: String(this.rule.id) } })
    },
    async repairLegacyRevision() {
      const unresolvedInputs = this.repairPreview.unresolvedInputs
      if (
        !this.repairPreview.sourceRevisionId ||
        !String(this.repairPreview.previewDigest || '').trim() ||
        !Array.isArray(unresolvedInputs) ||
        unresolvedInputs.length
      ) {
        this.$message.warning('修复预览不完整或仍有未解析输入，不能执行修复')
        return
      }
      this.repairSaving = true
      try {
        const response = await api.repairRuleRevision(this.rule.id, {
          sourceRevisionId: this.repairPreview.sourceRevisionId,
          previewDigest: this.repairPreview.previewDigest,
        })
        const draft = this.unwrapData(response)
        this.activateDraftRevision(draft)
        this.$message.success('历史引用已修复并创建治理草稿')
      } catch (error) {
        this.$message.error(error.message || '历史规则修复失败')
      } finally {
        this.repairSaving = false
      }
    },
    async downloadDecisionArtifact(artifactId) {
      const response = await artifactApi.downloadArtifact(artifactId)
      const url = window.URL.createObjectURL(response.data)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `decision-artifact-${artifactId}.zip`
      anchor.click()
      window.URL.revokeObjectURL(url)
      const digest = response.headers && response.headers['x-artifact-digest']
      this.$message.success(digest ? `制品已下载：${digest}` : '制品已下载')
    },
    async downloadArtifactMigration(artifactId) {
      const response = await artifactApi.downloadArtifactMigration(artifactId, true)
      const url = window.URL.createObjectURL(response.data)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `tianshu-resource-transfer-${artifactId}.zip`
      anchor.click()
      window.URL.revokeObjectURL(url)
      this.$message.success('配置迁移包已下载，可在离线迁移页面继续选择内容和关联目标')
    },
    async handleArtifactFile(uploadFile) {
      try {
        const response = await artifactApi.importArtifact(uploadFile.raw)
        this.importedArtifact = this.unwrapData(response) || {}
        this.deploymentVisible = true
      } catch (error) {
        this.$message.error(error.message || '制品导入失败')
      }
    },
    async handleArtifactDeploy(request) {
      try {
        await artifactApi.deployArtifact(request)
        this.deploymentVisible = false
        this.$message.success('制品部署成功')
        await this.loadLifecycle(this.rule.id)
      } catch (error) {
        this.$message.error(error.message || '制品部署失败')
      }
    },
    loadOpenApiConfig(value) {
      let parsed = createDefaultOpenApiContract()
      try {
        const stored =
          typeof value === 'string' && value.trim()
            ? JSON.parse(value)
            : value || {}
        parsed = { ...parsed, ...stored }
        parsed.idempotency = {
          ...createDefaultOpenApiContract().idempotency,
          ...(stored.idempotency || {}),
        }
        const storedIdempotency = stored.idempotency || {}
        const storedComponents = Array.isArray(storedIdempotency.components)
          ? storedIdempotency.components
          : []
        // 兼容旧版单 operand 和后端 components 结构，页面内部只保留一种协议。
        if (storedComponents.length) {
          parsed.idempotency.components = storedComponents.map(item =>
            createIdempotencyComponent(item && item.operand ? item.operand : item)
          )
        } else if (storedIdempotency.operand) {
          parsed.idempotency.components = [
            createIdempotencyComponent(storedIdempotency.operand),
          ]
        } else {
          parsed.idempotency.components = []
        }
        parsed.idempotency.operand = null
        if (!Array.isArray(stored.requestMappings))
          parsed.requestMappings = this.defaultOpenRequestMappings()
        if (!Array.isArray(stored.responseMappings))
          parsed.responseMappings = this.defaultOpenResponseMappings()
      } catch (e) {
        this.$message.error('开放接口配置不是合法 JSON：' + e.message)
        parsed.requestMappings = this.defaultOpenRequestMappings()
        parsed.responseMappings = this.defaultOpenResponseMappings()
      }
      parsed.requestMappings = (parsed.requestMappings || []).map((item) => ({
        ...item,
        targetKey:
          item.targetRefType && item.targetVarId
            ? item.targetRefType + ':' + item.targetVarId
            : '',
        sourceType: item.sourceType || 'BODY',
        targetType: item.targetType || 'STRING',
        required: item.required === true,
      }))
      parsed.responseMappings = (parsed.responseMappings || []).map((item) => ({
        ...item,
        sourceKey:
          item.sourceRefType && item.sourceVarId
            ? item.sourceRefType + ':' + item.sourceVarId
            : '',
        targetField: item.targetField || '',
      }))
      this.openApiForm = parsed
      this.openSuccessDataText = this.formatOpenJson(parsed.successDataTemplate)
      this.openErrorDataText = this.formatOpenJson(parsed.errorDataTemplate)
      this.openResponseHeadersText = this.formatOpenJson(
        parsed.responseHeaders || {}
      )
    },
    formatOpenJson(value) {
      return JSON.stringify(value, null, 2)
    },
    parseOpenJson(value, label) {
      try {
        return JSON.parse(value)
      } catch (e) {
        throw new Error(label + '不是合法 JSON：' + e.message)
      }
    },
    addOpenRequestMapping() {
      if (!this.canEditDraft) return
      if (!this.openInputOptions.length) {
        this.$message.warning(
          '当前规则没有可映射的入参，请先进入规则设计器引用并保存变量'
        )
        return
      }
      this.openApiForm.requestMappings.push({
        targetKey: '',
        sourceType: 'BODY',
        sourcePath: '$.',
        required: false,
        defaultValue: '',
        targetType: 'STRING',
      })
    },
    removeOpenRequestMapping(index) {
      if (!this.canEditDraft) return
      this.openApiForm.requestMappings.splice(index, 1)
    },
    updateIdempotencyOperand(component, value) {
      if (!this.canEditDraft || !this.openApiForm.idempotency.components.includes(component)) return
      component.operand = value || null
    },
    updateOpenApiEditor(field, value) {
      if (!this.canEditDraft) return
      this[field] = value
    },
    addIdempotencyComponent() {
      if (!this.canEditDraft) return
      this.openApiForm.idempotency.components.push(createIdempotencyComponent())
    },
    onIdempotencyEnabledChange(enabled) {
      if (!this.canEditDraft) return
      // 开启后立即给出一个可编辑组件，避免用户看到空白配置而不知道下一步。
      if (enabled && !this.openApiForm.idempotency.components.length) {
        this.addIdempotencyComponent()
      }
    },
    removeIdempotencyComponent(index) {
      if (!this.canEditDraft) return
      if (this.openApiForm.idempotency.components.length <= 1) return
      this.openApiForm.idempotency.components.splice(index, 1)
    },
    defaultOpenRequestMappings() {
      return this.openInputOptions.map((item) => {
        const separator = item.value.indexOf(':')
        return {
          targetRefType: item.value.substring(0, separator),
          targetVarId: item.value.substring(separator + 1),
          sourceType: 'BODY',
          sourcePath: item.externalName ? (/^[A-Za-z_][A-Za-z0-9_]*$/.test(item.externalName) ? '$.' + item.externalName : '$[' + JSON.stringify(item.externalName) + ']') : '$.',
          required: false,
          defaultValue: '',
          targetType: item.targetType,
        }
      })
    },
    defaultOpenResponseMappings() {
      return this.openOutputOptions.map((item) => {
        const separator = item.value.indexOf(':')
        return {
          sourceRefType: item.value.substring(0, separator),
          sourceVarId: item.value.substring(separator + 1),
          targetField: item.scriptName,
        }
      })
    },
    openMappingTargetType(fieldType) {
      const type = String(fieldType || 'STRING').toUpperCase()
      return this.openTargetTypes.includes(type) ? type : 'OBJECT'
    },
    resetOpenResponseMappings() {
      if (!this.canEditDraft) return
      this.openApiForm['responseMappings'] = this.defaultOpenResponseMappings()
    },
    addOpenResponseMapping() {
      if (!this.canEditDraft) return
      if (!this.openOutputOptions.length) {
        this.$message.warning(
          '当前规则没有可映射的输出，请先进入规则设计器配置并保存输出字段'
        )
        return
      }
      const used = new Set(
        (this.openApiForm.responseMappings || []).map((item) => item.sourceKey)
      )
      const option = this.openOutputOptions.find(
        (item) => !used.has(item.value)
      )
      this.openApiForm.responseMappings.push({
        sourceKey: option ? option.value : '',
        targetField: option ? option.scriptName : '',
      })
    },
    removeOpenResponseMapping(index) {
      if (!this.canEditDraft) return
      this.openApiForm.responseMappings.splice(index, 1)
    },
    onOpenResponseSourceChange(row) {
      if (!this.canEditDraft) return
      if (String(row.targetField || '').trim()) return
      const option = this.openOutputOptions.find(
        (item) => item.value === row.sourceKey
      )
      if (option) row['targetField'] = option.scriptName
    },
    buildOpenApiContract() {
      const resourceId = value => Number.isSafeInteger(Number(value)) ? Number(value) : value
      const mappings = (this.openApiForm.requestMappings || []).map(
        (item, index) => {
          const targetKey = String(item.targetKey || '')
          const separator = targetKey.indexOf(':')
          if (separator <= 0)
            throw new Error('请求映射第 ' + (index + 1) + ' 行未选择规则入参')
          if (!String(item.sourcePath || '').trim())
            throw new Error('请求映射第 ' + (index + 1) + ' 行未填写来源路径')
          return {
            targetRefType: targetKey.substring(0, separator),
            targetVarId: resourceId(targetKey.substring(separator + 1)),
            sourceType: item.sourceType || 'BODY',
            sourcePath: String(item.sourcePath).trim(),
            required: item.required === true,
            defaultValue:
              item.defaultValue == null || item.defaultValue === ''
                ? null
                : String(item.defaultValue),
            targetType: item.targetType || 'STRING',
          }
        }
      )
      const responseMappings = (this.openApiForm.responseMappings || []).map(
        (item, index) => {
          const sourceKey = String(item.sourceKey || '')
          const separator = sourceKey.indexOf(':')
          if (separator <= 0)
            throw new Error(
              '响应映射第 ' + (index + 1) + ' 行未选择引擎输出字段'
            )
          const targetField = String(item.targetField || '').trim()
          if (!targetField)
            throw new Error('响应映射第 ' + (index + 1) + ' 行未填写对外字段')
          return {
            sourceRefType: sourceKey.substring(0, separator),
            sourceVarId: resourceId(sourceKey.substring(separator + 1)),
            targetField,
          }
        }
      )
      return {
        enabled: this.openApiForm.enabled === true,
        recordTrace: this.openApiForm.recordTrace === true,
        returnTrace: this.openApiForm.returnTrace === true,
        requestMappings: mappings,
        responseMappings,
        successDataTemplate: this.parseOpenJson(
          this.openSuccessDataText,
          '成功 data'
        ),
        errorDataTemplate: this.parseOpenJson(
          this.openErrorDataText,
          '异常 data'
        ),
        responseHeaders: this.parseOpenJson(
          this.openResponseHeadersText,
          '响应 Header'
        ),
        idempotency: {
          enabled: this.openApiForm.idempotency.enabled === true,
          components: (this.openApiForm.idempotency.components || [])
            .map(item => item && item.operand ? { operand: item.operand } : null)
            .filter(Boolean),
          ttlSeconds: Number(this.openApiForm.idempotency.ttlSeconds || 86400),
        },
      }
    },
    validateOpenApiContract(contract) {
      if (contract.idempotency && contract.idempotency.enabled) {
        const operands = Array.isArray(contract.idempotency.components)
          ? contract.idempotency.components.map(item => item && item.operand ? item.operand : item).filter(Boolean)
          : []
        if (!operands.length) throw new Error('幂等键表达式不能为空')
        for (const operand of operands) {
          const errors = validateOperand(operand, { allowedKinds: this.idempotencyOperandKinds })
          if (errors.length) throw new Error('幂等键：' + errors[0].message)
        }
        if (contract.enabled) {
          const mapped = new Set((contract.requestMappings || []).map(item =>
            String(item.targetRefType || '').toUpperCase() + ':' + item.targetVarId
          ))
          const references = operands.flatMap(item => collectOperandReferences(item))
          references.forEach(reference => {
            const key = String(reference.refType || '').toUpperCase() + ':' + reference.refId
            if (String(reference.refType || '').toUpperCase() !== 'CONSTANT' && !mapped.has(key)) {
              throw new Error('幂等键字段未配置请求映射：' + key)
            }
          })
        }
      }
      // 开放接口未启用时仍校验幂等表达式，但允许请求/响应映射暂存不完整，
      // 这样编辑器可以先保存草稿再开启接口完成映射。
      if (!contract.enabled) return
      const targets = {}
      const headerName = /^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/
      ;(contract.requestMappings || []).forEach((item, index) => {
        const target = item.targetRefType + ':' + item.targetVarId
        if (targets[target])
          throw new Error('请求映射第 ' + (index + 1) + ' 行目标字段重复')
        targets[target] = true
        if (item.sourceType === 'BODY' && !isRestrictedJsonPath(item.sourcePath)) {
          throw new Error('请求映射第 ' + (index + 1) + ' 行不是受限 JSONPath')
        }
        if (item.sourceType === 'HEADER' && !headerName.test(item.sourcePath)) {
          throw new Error('请求映射第 ' + (index + 1) + ' 行 Header 名称不合法')
        }
      })
      const responseTargets = {}
      const responseField = /^[A-Za-z_][A-Za-z0-9_-]{0,127}$/
      ;(contract.responseMappings || []).forEach((item, index) => {
        if (!item.sourceRefType || !item.sourceVarId) {
          throw new Error('响应映射第 ' + (index + 1) + ' 行未选择引擎输出字段')
        }
        if (!responseField.test(item.targetField)) {
          throw new Error('响应映射第 ' + (index + 1) + ' 行对外字段名不合法')
        }
        if (responseTargets[item.targetField]) {
          throw new Error('响应映射第 ' + (index + 1) + ' 行对外字段重复')
        }
        responseTargets[item.targetField] = true
      })
      if (
        !contract.responseHeaders ||
        Array.isArray(contract.responseHeaders) ||
        typeof contract.responseHeaders !== 'object'
      ) {
        throw new Error('响应 Header 必须是 JSON 对象')
      }
      Object.keys(contract.responseHeaders).forEach((name) => {
        if (!headerName.test(name))
          throw new Error('响应 Header 名称不合法：' + name)
      })
    },
    renderOpenTemplate(template, context) {
      if (Array.isArray(template))
        return template.map((item) => this.renderOpenTemplate(item, context))
      if (template && typeof template === 'object') {
        return Object.keys(template).reduce((result, key) => {
          result[key] = this.renderOpenTemplate(template[key], context)
          return result
        }, {})
      }
      if (typeof template !== 'string') return template
      const exact = template.match(/^\$\{([A-Za-z0-9_.-]+)\}$/)
      if (exact) return context[exact[1]]
      return template.replace(/\$\{([A-Za-z0-9_.-]+)\}/g, (match, key) => {
        const value = context[key]
        return value == null ? '' : String(value)
      })
    },
    openPreviewContext(success) {
      const status = success ? PLATFORM_CODES.SUCCESS : PLATFORM_CODES.PARAMETER_INVALID
      const context = {
        'status.success': success,
        'status.code': status.code,
        'status.message': status.message,
        'status.httpStatus': status.httpStatus,
        'status.detail': success ? null : '字段值不满足校验逻辑',
        traceId: 'AP-P-PREVIEW',
        result: { sample: true },
        trace: [{ node: 'preview' }],
        'error.message': success ? '' : '字段值不满足校验逻辑',
        'error.field': success ? '' : 'request.mobile',
        'error.validationCode': success ? '' : 'mobile_check',
        'error.validationName': success ? '' : '手机号格式',
      }
      this.openOutputPlaceholders.forEach((placeholder) => {
        const key = placeholder.slice(2, -1)
        context[key] =
          '<' + key.substring('output.'.length).replace('.', ':') + '>'
      })
      context.response = (this.openApiForm.responseMappings || []).reduce(
        (result, mapping) => {
          const key = String(mapping.sourceKey || '').replace(':', '.')
          result[mapping.targetField] = context['output.' + key]
          return result
        },
        {}
      )
      return context
    },
    previewOpenApiConfig() {
      try {
        const contract = this.buildOpenApiContract()
        this.validateOpenApiContract(contract)
        const successContext = this.openPreviewContext(true)
        const errorContext = this.openPreviewContext(false)
        successContext.data = this.renderOpenTemplate(
          contract.successDataTemplate,
          successContext
        )
        errorContext.data = this.renderOpenTemplate(
          contract.errorDataTemplate,
          errorContext
        )
        this.openApiSuccessPreview = {}
        this.openApiErrorPreview = {}
        for (const [body, context] of [[this.openApiSuccessPreview, successContext], [this.openApiErrorPreview, errorContext]]) {
          Object.assign(body, {
            code: context['status.code'], message: context['status.message'], detail: context['status.detail'],
            success: context['status.success'], traceId: context.traceId, data: context.data,
          })
        }
        this.openApiPreviewVisible = true
        this.$message.success('开放接口契约校验通过')
      } catch (e) {
        this.$message.error(e.message)
      }
    },
    async saveOpenApiConfig() {
      if (!this.requireEditableDraft()) return
      let contract
      try {
        contract = this.buildOpenApiContract()
        // 幂等配置即使在开放接口关闭时也会作用于 HTTP/SDK 同步执行，
        // 因此保存时始终校验，避免把错误配置拖到发布阶段才暴露。
        this.validateOpenApiContract(contract)
      } catch (e) {
        this.$message.error(e.message)
        return
      }
      this.openApiSaving = true
      try {
        const openApiConfigJson = JSON.stringify(contract)
        const saved = await api.saveContent({
          definitionId: this.rule.id,
          revisionId: this.activeRevision.id,
          lockVersion: this.activeRevision.lockVersion,
          modelJson: this.ruleContent.modelJson || '{}',
          openApiConfigJson,
          updateOpenApiConfig: true,
        })
        const saveResult = this.unwrapData(saved) || {}
        this.activateDraftRevision(saveResult.revision, false)
        if (saveResult.designVersion !== undefined) {
          this.rule.currentVersion = saveResult.designVersion
        }
        if (
          !saveResult.revision ||
          !Object.prototype.hasOwnProperty.call(
            saveResult.revision,
            'openApiConfigJson'
          )
        ) {
          this.ruleContent['openApiConfigJson'] = openApiConfigJson
          this.loadOpenApiConfig(openApiConfigJson)
        }
        this.showDraftDiagnostics(saveResult.issues, saveResult.compileSuccess)
      } catch (e) {
        this.$message.error(e.message || '保存开放接口契约失败')
      } finally {
        this.openApiSaving = false
      }
    },
    requireEditableDraft() {
      if (this.canEditDraft) return true
      if (this.hasEditableWorkspace) {
        this.$message.warning('当前查看的是只读修订，请返回工作区草稿后编辑')
        return false
      }
      this.$message.warning('当前没有可编辑草稿，请先创建或退回草稿')
      return false
    },
    openBaseEditDialog() {
      if (!this.requireEditableDraft()) return
      this.baseForm = {
        ruleName: this.rule.ruleName || '',
        description: this.rule.description || '',
      }
      this.baseEditVisible = true
    },
    async saveBaseInfo() {
      if (!this.requireEditableDraft()) return
      const ruleName = (this.baseForm.ruleName || '').trim()
      if (!ruleName) {
        this.$message.warning('规则名称不能为空')
        return
      }
      this.baseSaving = true
      try {
        await api.updateDefinition({
          id: this.rule.id,
          projectId: this.rule.projectId,
          ruleName,
          description: this.baseForm.description || '',
          status: this.rule.status,
        })
        this.rule['ruleName'] = ruleName
        this.rule['description'] = this.baseForm.description || ''
        this.baseEditVisible = false
        this.$message.success('规则基本信息已更新')
      } catch (e) {
        this.$message.error(e.message || '保存规则基本信息失败')
      } finally {
        this.baseSaving = false
      }
    },
    async loadVars(requestId = this.detailLoadRequestId) {
      const projectId = this.rule.projectId
      if (projectId && projectId > 0) {
        await this.loadVarsByProject(projectId, requestId)
      } else {
        await this.loadGlobalVars(requestId)
      }
    },
    async loadVarsByProject(projectId, requestId = this.detailLoadRequestId) {
      try {
        const [varsRes, constRes, treeRes, modelRes] = await Promise.all([
          listVariablesByProject(projectId),
          loadPagedCatalog(listVariables, {
            projectId,
            varSource: 'CONSTANT',
            }),
          getVariableTree(projectId),
          listAllModelsByProject(projectId),
        ])
        if (!this.isCurrentDetailLoad(requestId)) return
        const vars = Array.isArray(varsRes.data) ? varsRes.data : []
        const consts =
          constRes.data && Array.isArray(constRes.data.records)
            ? constRes.data.records
            : Array.isArray(constRes.data)
            ? constRes.data
            : []
        const tree = this.normalizeVariableTree(treeRes.data)
        const models = this.normalizeListResponse(modelRes)
        this.buildVarOptions([...vars, ...consts], tree, models)
      } catch (e) {
        if (!this.isCurrentDetailLoad(requestId)) return
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
    async loadGlobalVars(requestId = this.detailLoadRequestId) {
      try {
        const [varsRes, constRes, treeRes, modelRes] = await Promise.all([
          loadPagedCatalog(listVariables, { scope: 'GLOBAL', }),
          loadPagedCatalog(listVariables, {
            scope: 'GLOBAL',
            varSource: 'CONSTANT',
            }),
          getVariableTree(0),
          listAllModelsByProject(0),
        ])
        if (!this.isCurrentDetailLoad(requestId)) return
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
        this.buildVarOptions([...vars, ...consts], tree, models)
      } catch (e) {
        if (!this.isCurrentDetailLoad(requestId)) return
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
    getFieldVarMap(row) {
      return resolveDetailReference(this.varMap, row)
    },
    fieldDisplayLabel(row) {
      const item = this.getFieldVarMap(row)
      return (
        (item && (item.varLabelText || item.varLabel)) || row.fieldLabel || '—'
      )
    },
    fieldHierarchyDisplay(row) {
      const item = this.getFieldVarMap(row)
      const refType = row.refType || row._refType || ''
      const isDataObject = refType === 'DATA_OBJECT'
      const objectLabel = isDataObject
        ? (item && (item.objectLabel || item.sourceLabel)) || '未解析数据对象'
        : ''
      const mappedLabel = (item && (item.varLabelText || item.varLabel)) || ''
      const objectLabelPrefix = objectLabel ? objectLabel + '/' : ''
      const fieldLabel =
        isDataObject && mappedLabel.indexOf(objectLabelPrefix) === 0
          ? mappedLabel.substring(objectLabelPrefix.length)
          : mappedLabel || row.fieldLabel || '—'
      return {
        isDataObject,
        objectLabel,
        objectCode: isDataObject
          ? (item && (item.objectCode || item.sourceCode)) || '—'
          : '',
        fieldLabel,
        fieldPath:
          (item && (item.varCodeText || item.scriptName)) ||
          row.scriptName ||
          '—',
      }
    },
    buildVarOptions(vars, doTree, models = []) {
      const state = buildDetailReferenceState(
        buildReferenceCatalog(vars, doTree, models)
      )
      this.varMap = buildDetailReferenceMap(state)
      this.varPickerGroups.splice(
        0,
        this.varPickerGroups.length,
        ...state.groups.map((group) => ({
          label: group.label,
          options: group.options,
        }))
      )
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
    onVarClear(row) {
      row['varId'] = null
      row['_varId'] = null
      row['refType'] = ''
      row['fieldLabel'] = ''
      row['scriptName'] = ''
    },
    onVarChange(row, varId) {
      if (!varId) return
      // 从 varPickerGroups 所有选项中查找
      let opt = null
      for (const group of this.varPickerGroups) {
        const found = group.options.find(
          (o) => o.id === varId && (!row.refType || o.refType === row.refType)
        )
        if (found) {
          opt = found
          break
        }
      }
      if (!opt) return
      row['varId'] = opt.id
      row['fieldLabel'] = opt.varLabel
      row['scriptName'] = opt.varCode
      row['varSource'] = opt.sourceType
      row['refType'] = opt.refType || ''
    },
    modelTypeLabel(t) {
      return MODEL_TYPE_LABELS[t] || t || '—'
    },
    statusLabel(s) {
      return { 0: '未发布', 1: '已发布', 2: '已下线' }[s] || '—'
    },
    statusType(s) {
      return { 0: 'info', 1: 'success', 2: 'warning' }[s] || 'info'
    },
    typeLabel(t) {
      return (
        {
          NUMBER: '数字',
          INTEGER: '整数',
          DOUBLE: '浮点',
          STRING: '字符串',
          BOOLEAN: '布尔',
          ENUM: '枚举',
          DATE: '日期',
          OBJECT: '对象',
          LIST: '列表',
        }[t] ||
        t ||
        '—'
      )
    },
    fieldParamKey(field) {
      const rawKey = (field && (field.scriptName || field.fieldName)) || ''
      return (this.testFieldKeyMap && this.testFieldKeyMap[rawKey]) || rawKey
    },

    // ========== 输入字段编辑 ==========
    editInputField(row) {
      if (!this.requireEditableDraft()) return
      if (this.rule.inputFieldsJson) {
        this.rule.inputFieldsJson.forEach((f) => {
          if (f !== row) f['_editing'] = false
        })
      }
      row['_editing'] = true
      row['_varId'] = row.varId
      row['_origin'] = {
        varId: row.varId,
        _varId: row.varId,
        missingValue: row.missingValue,
        validationRuleIdList: [...(row.validationRuleIdList || [])],
        validationOverride: row.validationOverride,
      }
    },
    inputFieldPayload(row, validationOverride, validationRuleIds) {
      return {
        varId: row.varId,
        refType: row.refType,
        scriptName: row.scriptName,
        fieldLabel: row.fieldLabel,
        fieldType: row.fieldType,
        missingValue: row.missingValue,
        defaultValue: row.defaultValue,
        transformType: row.transformType,
        transformParams: row.transformParams,
        validValues: row.validValues,
        validationRuleIds: JSON.stringify(validationRuleIds || []),
        validationOverride,
      }
    },
    async saveInputField(row) {
      if (!this.requireEditableDraft()) return
      row['_saving'] = true
      try {
        const validationRuleIds = row.validationRuleIdList || []
        await api.updateInputField(
          row.id,
          this.inputFieldPayload(row, 1, validationRuleIds)
        )
        row['validationRuleIds'] = JSON.stringify(validationRuleIds)
        row['validationOverride'] = 1
        row['_editing'] = false
        row['_saving'] = false
        this.$message.success('规则草稿字段已保存，发布审批通过后生效')
      } catch (e) {
        row['_saving'] = false
        this.$message.error('保存失败: ' + (e.message || e))
      }
    },
    cancelEditInput(row) {
      if (row._origin) {
        row['varId'] = row._origin.varId
        row['_varId'] = row._origin._varId
        row['missingValue'] = row._origin.missingValue
        row['validationRuleIdList'] = [...row._origin.validationRuleIdList]
        row['validationOverride'] = row._origin.validationOverride
      }
      row['_editing'] = false
    },
    async restoreInheritedValidation(row) {
      if (!this.requireEditableDraft()) return
      row['_saving'] = true
      try {
        await api.updateInputField(row.id, this.inputFieldPayload(row, 0, []))
        this.$message.success('已恢复子规则的字段校验配置')
        await this.load()
      } catch (e) {
        this.$message.error('恢复失败: ' + (e.message || e))
      } finally {
        row['_saving'] = false
      }
    },
    inputRowClassName({ row }) {
      return row._editing ? 'editing-row' : ''
    },

    // ========== 输出字段编辑 ==========
    editOutputField(row) {
      if (!this.requireEditableDraft()) return
      if (this.rule.outputFieldsJson) {
        this.rule.outputFieldsJson.forEach((f) => {
          if (f !== row) f['_editing'] = false
        })
      }
      row['_editing'] = true
      row['_varId'] = row.varId
      row['_origin'] = {
        varId: row.varId,
        _varId: row.varId,
        transformType: row.transformType,
      }
    },
    async saveOutputField(row) {
      if (!this.requireEditableDraft()) return
      row['_saving'] = true
      try {
        await api.updateOutputField(row.id, {
          varId: row.varId,
          refType: row.refType,
          scriptName: row.scriptName,
          fieldLabel: row.fieldLabel,
          fieldType: row.fieldType,
          transformType: row.transformType,
          transformParams: row.transformParams,
        })
        row['_editing'] = false
        row['_saving'] = false
        this.$message.success('规则草稿字段已保存，发布审批通过后生效')
      } catch (e) {
        row['_saving'] = false
        this.$message.error('保存失败: ' + (e.message || e))
      }
    },
    cancelEditOutput(row) {
      if (row._origin) {
        row['varId'] = row._origin.varId
        row['_varId'] = row._origin._varId
        row['transformType'] = row._origin.transformType
      }
      row['_editing'] = false
    },
    outputRowClassName({ row }) {
      return row._editing ? 'editing-row' : ''
    },

    // ========== 发布版本对比 ==========
    async openVersionDialog() {
      this.versionVisible = true
      this.versionCompare = null
      this.leftVersionNumber = null
      this.rightVersionNumber = null
      this.versionCompareRequestId++
      await this.loadVersions()
    },
    async loadVersions() {
      if (!this.rule.id) return
      this.versionLoading = true
      try {
        const res = await api.listVersions(this.rule.id)
        const rows =
          res && Array.isArray(res.data)
            ? res.data
            : Array.isArray(res)
            ? res
            : []
        this.versions = rows
          .slice()
          .sort((left, right) => Number(right.version) - Number(left.version))
        this.leftVersionNumber = null
        this.rightVersionNumber = null
        this.versionCompare = null
        if (this.versions.length >= 2) {
          this.leftVersionNumber = this.versions[1].version
          this.rightVersionNumber = this.versions[0].version
          await this.loadSelectedVersionCompare()
        }
      } catch (e) {
        this.versions = []
        this.$message.error(e.message || '加载版本历史失败')
      } finally {
        this.versionLoading = false
      }
    },
    async loadFieldValidationOptions(requestId = this.detailLoadRequestId) {
      try {
        const res = await listAvailableFieldValidations(this.rule.projectId)
        if (!this.isCurrentDetailLoad(requestId)) return
        const data = res && res.data !== undefined ? res.data : res
        this.fieldValidationOptions = Array.isArray(data) ? data : []
      } catch (e) {
        if (!this.isCurrentDetailLoad(requestId)) return
        this.fieldValidationOptions = []
        this.$message.error('加载字段校验规则失败: ' + (e.message || e))
      }
    },
    parseValidationRuleIds(value) {
      if (Array.isArray(value)) return value.map(Number).filter(Number.isFinite)
      if (typeof value !== 'string' || !value.trim()) return []
      try {
        const parsed = JSON.parse(value)
        return Array.isArray(parsed)
          ? parsed.map(Number).filter(Number.isFinite)
          : []
      } catch (e) {
        return []
      }
    },
    selectedFieldValidations(row) {
      const ids = (row && row.validationRuleIdList) || []
      return ids
        .map((id) =>
          this.fieldValidationOptions.find(
            (item) => Number(item.id) === Number(id)
          )
        )
        .filter(Boolean)
    },
    fieldValidationTypeLabel(type) {
      return (
        {
          REQUIRED: '必填',
          REGEX: '正则',
          MIN_VALUE: '最小值',
          MAX_VALUE: '最大值',
          MIN_LENGTH: '最小长度',
          MAX_LENGTH: '最大长度',
          IN: '允许值',
          NOT_IN: '禁用值',
        }[type] || type
      )
    },
    fieldValidationOptionLabel(item) {
      if (!item) return ''
      const prefix = item.builtIn ? '【系统内置】' : ''
      return `${prefix}${item.validationName}（${this.fieldValidationTypeLabel(
        item.validationType
      )}）`
    },
    async selectVersionPair(leftVersion, rightVersion) {
      this.leftVersionNumber = leftVersion
      this.rightVersionNumber = rightVersion
      return this.loadSelectedVersionCompare()
    },
    async loadSelectedVersionCompare() {
      if (this.leftVersionNumber == null || this.rightVersionNumber == null)
        return
      if (this.leftVersionNumber === this.rightVersionNumber) {
        this.$message.warning('请选择两个不同的发布版本')
        return
      }
      const requestId = ++this.versionCompareRequestId
      this.versionCompareLoading = true
      this.versionCompare = null
      try {
        const res = await api.compareVersions(
          this.rule.id,
          this.leftVersionNumber,
          this.rightVersionNumber
        )
        if (requestId === this.versionCompareRequestId)
          this.versionCompare = res && res.data ? res.data : res
      } catch (e) {
        if (requestId === this.versionCompareRequestId)
          this.$message.error(e.message || '版本对比失败')
      } finally {
        if (requestId === this.versionCompareRequestId)
          this.versionCompareLoading = false
      }
    },
    async compareVersion(left, right) {
      if (!left || !right) return
      return this.selectVersionPair(right.version, left.version)
    },
    async swapVersionCompare() {
      const left = this.leftVersionNumber
      this.leftVersionNumber = this.rightVersionNumber
      this.rightVersionNumber = left
      return this.loadSelectedVersionCompare()
    },
    formatVersionTime(value) {
      return value ? String(value).replace('T', ' ') : '-'
    },
    formatVersionJson(value) {
      if (!value) return ''
      try {
        return JSON.stringify(JSON.parse(value), null, 2)
      } catch (e) {
        return value
      }
    },
    async openTestDialog() {
      this.invalidateTestRequests()
      const requestId = ++this.testSchemaRequestId
      const definitionId = this.rule.id
      const context = this.testSelectionKey
      const source = { ...this.selectedTestExecutionSource }
      const current = () => this.detailActive && this.testVisible && requestId === this.testSchemaRequestId && context === this.testSelectionKey
      this.testVisible = true
      this.testReady = false
      this.testSourceError = ''
      this.testExecutedInputJson = ''
      this.testExecutionSource = source
      this.testExecutionLabel = this.lifecycleRevision.id ? this.revisionOptionLabel(this.lifecycleRevision) : ''
      this.testSchemaIdentity = null
      this.testResult = null
      this.testMode = 'manual'
      this.jsonEdited = false
      this.jsonError = ''
      this.testDialogKey++

      const freshRule = this.rule
      let schema = null
      try {
        schema = normalizeTestSchema(
          await api.getRuleTestSchema({
            targetType: 'RULE',
            targetId: definitionId,
            executionSource: source,
          })
        )
        if (!current()) return
        requireExecutionIdentity(schema.executionIdentity, definitionId, source)
        this.testSchemaIdentity = schema.executionIdentity
      } catch (e) {
        if (!current()) return
        if (source.sourceType !== 'WORKSPACE') {
          this.testSourceError = e.message || '所选版本的测试字段读取失败'
          return
        }
      }
      if (!current()) return
      const hasSchema = Boolean(schema)
      this.testFieldKeyMap = {}
      const testFields = hasSchema
        ? schemaFieldsToTestFields(schema.inputs)
        : (freshRule.inputFieldsJson || [])
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
      const testParams = hasSchema
        ? flattenSchemaSample(testFields, schema.sampleParams)
        : this.buildFlatTestParams(testFields)
      const nestedParams = hasSchema
        ? schema.sampleParams
        : this.buildNestedTestParams(testFields, testParams)
      const testJsonStr = JSON.stringify(nestedParams, null, 2)

      this.testFields = testFields
      this.testParams = testParams
      this.testJsonStr = testJsonStr
      this.testReady = true
    },
    switchToJsonMode() {
      if (this.testMode === 'json') return
      this.testMode = 'json'
      this.syncParamsToJson()
    },
    switchToManualMode() {
      if (this.testMode === 'manual') return
      this.testMode = 'manual'
      this.syncJsonToParams()
    },
    syncParamsToJson() {
      const obj = this.buildNestedTestParams(this.testFields, this.testParams)
      this.testJsonStr = JSON.stringify(obj, null, 2)
      this.jsonEdited = false
      this.jsonError = ''
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
    syncJsonToParams() {
      try {
        const obj = JSON.parse(this.testJsonStr)
        this.testFields.forEach((f) => {
          const key = this.fieldParamKey(f)
          const value = this.readParamPath(obj, key)
          if (value !== undefined) this.testParams[key] = value
        })
        this.jsonError = ''
      } catch (e) {
        this.jsonError = 'JSON 格式错误: ' + e.message
      }
    },
    async doTest() {
      if (!this.testReady || this.testSourceError || this.testExecuting) return
      const requestId = ++this.testExecutionRequestId
      const definitionId = this.rule.id
      const source = { ...this.testExecutionSource }
      const context = this.testSelectionKey
      const current = () => this.detailActive && this.testVisible && requestId === this.testExecutionRequestId && context === this.testSelectionKey
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
        params = this.buildNestedTestParams(this.testFields, this.testParams)
      }
      const inputSnapshot = JSON.stringify(params)
      this.testExecutedInputJson = inputSnapshot
      try {
        const res = await api.executeRule(
          { definitionId, executionSource: source, params },
          this.requestTimeoutMs
        )
        if (!current()) return
        const result = normalizeTestResult(res)
        requireExecutionIdentity(result.executionIdentity, definitionId, source)
        this.testResult = result
      } catch (e) {
        if (current()) this.testResult = { success: false, error: e.message || '测试执行失败' }
      } finally {
        if (current()) this.testExecuting = false
      }
    },
    handleClearParams() {
      this.testParams = this.buildFlatTestParams(this.testFields)
      this.testJsonStr = JSON.stringify(
        this.buildNestedTestParams(this.testFields, this.testParams),
        null,
        2
      )
      this.jsonEdited = false
      this.testResult = null
      this.jsonError = ''
    },
    async buildModelInputFieldKeyMap(rule) {
      const map = {}
      const currentRule = rule || this.rule
      if (!currentRule || !currentRule.id) return map
      const modelTexts = []
      let modelText = currentRule.modelJson || ''
      try {
        const contentRes = await api.getContent(currentRule.id)
        const content =
          contentRes && contentRes.data !== undefined
            ? contentRes.data
            : contentRes
        if (content && content.modelJson) modelText = content.modelJson
      } catch (e) {
        /* ignore */
      }
      if (modelText) modelTexts.push(modelText)
      const ruleIds = this.collectRuleCallIds(this.parseJsonObject(modelText))
      for (let r = 0; r < ruleIds.length; r++) {
        try {
          const contentRes = await api.getContent(ruleIds[r])
          const content =
            contentRes && contentRes.data !== undefined
              ? contentRes.data
              : contentRes
          if (content && content.modelJson) modelTexts.push(content.modelJson)
        } catch (e) {
          /* ignore */
        }
      }
      modelText = modelTexts.join('\n')
      if (!modelText) return map
      const projectId = currentRule.projectId || 0
      try {
        const res = await listAllModelsByProject(projectId)
        const data = res && res.data !== undefined ? res.data : res
        const models = Array.isArray(data)
          ? data
          : data && data.records
          ? data.records
          : []
        for (let i = 0; i < models.length; i++) {
          const model = models[i]
          const modelCode = model && model.modelCode
          if (!modelCode || modelText.indexOf(modelCode) < 0) continue
          const detailRes = await getModel(model.id)
          const detail =
            detailRes && detailRes.data !== undefined
              ? detailRes.data
              : detailRes
          const fields =
            detail && Array.isArray(detail.inputFields)
              ? detail.inputFields
              : []
          fields.forEach((field) => {
            if (!field || field.status === 0) return
            const scriptName = field.scriptName || field.fieldName
            if (scriptName && !map[scriptName]) {
              map[scriptName] = modelCode + '_fields.' + scriptName
            }
          })
        }
      } catch (e) {
        /* ignore */
      }
      return map
    },
    collectRuleCallIds(value, out) {
      const ids = out || []
      if (!value || typeof value !== 'object') return ids
      if (Array.isArray(value)) {
        value.forEach((item) => this.collectRuleCallIds(item, ids))
        return ids
      }
      if (
        value.type === 'rule-call' &&
        value.ruleId &&
        ids.indexOf(value.ruleId) < 0
      ) {
        ids.push(value.ruleId)
      }
      Object.keys(value).forEach((key) =>
        this.collectRuleCallIds(value[key], ids)
      )
      return ids
    },
    parseJsonObject(text) {
      if (!text) return null
      try {
        return JSON.parse(text)
      } catch (e) {
        return null
      }
    },
    buildFlatTestParams(fields) {
      const params = {}
      ;(fields || []).forEach((f) => {
        const key = this.fieldParamKey(f)
        if (key) params[key] = this.sampleValueForField(f)
      })
      return params
    },
    buildNestedTestParams(fields, flatParams) {
      const obj = {}
      ;(fields || []).forEach((f) => {
        const key = this.fieldParamKey(f)
        if (!key) return
        setPathValue(obj, key, flatParams[key])
      })
      return obj
    },
    sampleValueForField(field) {
      if (
        field &&
        field.exampleValue !== undefined &&
        field.exampleValue !== null &&
        field.exampleValue !== ''
      ) {
        return field.exampleValue
      }
      if (
        field &&
        field.defaultValue !== undefined &&
        field.defaultValue !== null &&
        field.defaultValue !== ''
      ) {
        return field.defaultValue
      }
      return sampleValueForVarType(field && field.fieldType)
    },
    readParamPath(target, path) {
      const parts = String(path || '')
        .split('.')
        .map((item) => item.trim())
        .filter(Boolean)
      if (!parts.length) return undefined
      let current = target
      for (let i = 0; i < parts.length; i++) {
        if (!current || typeof current !== 'object' || !(parts[i] in current))
          return undefined
        current = current[parts[i]]
      }
      return current
    },
    formatResult(outputs) {
      return formatTestOutput(outputs)
    },
  },
}
</script>

<style scoped>
.rule-detail-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 8px; }
.rule-detail-actions :deep(.el-button + .el-button) { margin-left: 0; }
.field-workspace-notice { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; margin-bottom: 12px; color: var(--tianshu-text-secondary); line-height: 1.6; }
.idempotency-readonly-formula { min-width: 0; white-space: pre-wrap; overflow-wrap: anywhere; color: var(--tianshu-text-secondary); }
.revision-scope-note { margin: 12px 0 0; color: var(--tianshu-text-secondary); line-height: 1.6; }
.release-progress-card { margin-bottom: 16px; }
.release-progress-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.release-progress-header > div { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
.release-progress-summary { color: var(--tianshu-text-secondary); font-size: 13px; }
.script-unbound {
  color: var(--tianshu-text-tertiary);
  font-style: italic;
}
.open-api-panel {
  color: var(--tianshu-text-primary);
}
.open-api-toolbar,
.open-api-section-head,
.open-api-actions,
.open-api-switches {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.open-api-toolbar {
  margin-bottom: 12px;
}
.open-api-actions,
.open-api-switches {
  justify-content: flex-start;
}
.open-api-section {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 4px;
  background: var(--tianshu-bg-surface);
}
.open-api-field-empty {
  margin-top: 12px;
}
.idempotency-component-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.idempotency-component-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.idempotency-component-row .operand-picker {
  flex: 1;
  min-width: 0;
}
.idempotency-component-index {
  width: 24px;
  color: var(--tianshu-text-tertiary);
  text-align: center;
}
.idempotency-preview {
  margin-top: 8px;
}
.idempotency-preview code {
  word-break: break-word;
}
.open-api-title {
  color: var(--tianshu-text-primary);
  font-weight: 700;
}
.open-api-help {
  margin-top: 4px;
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
  line-height: 1.6;
}
.open-api-help code {
  margin-right: 6px;
  padding: 1px 4px;
  border-radius: 3px;
  color: var(--tianshu-info-text);
  background: var(--tianshu-info-bg);
}
.open-api-editors {
  margin-top: 12px;
}
.open-output-help {
  margin: 4px 0 8px;
}
.open-api-preview-grid {
  margin-top: 16px;
}
.open-api-preview-title {
  margin-bottom: 8px;
  color: var(--tianshu-text-primary);
  font-weight: 700;
}
.open-api-preview-grid pre {
  min-height: 240px;
  max-height: 440px;
  padding: 14px;
  overflow: auto;
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 4px;
  color: var(--tianshu-text-primary);
  background: var(--tianshu-bg-soft);
  white-space: pre-wrap;
  word-break: break-all;
}
.btn-delete {
  color: var(--tianshu-danger-text);
}
:deep(.version-history-dialog) {
  min-width: 1040px;
}
:deep(.version-history-dialog .el-dialog__body) {
  max-height: 82vh;
  overflow: auto;
  padding-top: 16px;
}
.version-compare-toolbar {
  position: sticky;
  top: -16px;
  z-index: 4;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 12px 16px;
  border: 1px solid var(--tianshu-border);
  border-radius: 4px;
  background: var(--tianshu-bg-soft);
}
.version-compare-toolbar-label {
  color: var(--tianshu-text-secondary);
  font-size: 13px;
  font-weight: 600;
}
.version-compare-toolbar-tip {
  margin-left: auto;
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
}
.version-compare-toolbar :deep(.el-select) {
  width: 140px;
}
.version-compare-content {
  min-height: 72px;
  overflow-x: auto;
}
.version-compare-placeholder {
  margin-top: 12px;
  padding: 24px;
  border: 1px dashed var(--tianshu-border);
  color: var(--tianshu-text-tertiary);
  text-align: center;
  font-size: 13px;
}
.version-compare-empty {
  margin-top: 12px;
}
.version-tech-collapse {
  margin-top: 12px;
}
:deep(.editing-row) {
  background-color: var(--tianshu-success-bg);
}
:deep(.el-table .editing-row td) {
  background-color: var(--tianshu-success-bg);
}
.test-form-wrapper {
  max-height: 420px;
  overflow-y: auto;
  border: 1px solid var(--tianshu-border-subtle);
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
  background-color: var(--tianshu-bg-muted);
}
.test-field-label {
  font-size: 13px;
  color: var(--tianshu-text-primary);
  font-weight: 500;
  margin-bottom: 6px;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.test-field-hint {
  font-size: 11px;
  color: var(--tianshu-text-tertiary);
  margin-top: 4px;
  font-family: 'Courier New', monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.version-compare-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 12px;
}
.version-compare-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--tianshu-text-primary);
  margin-bottom: 6px;
}
.version-compare-grid pre {
  margin: 0;
  padding: 10px;
  height: 220px;
  overflow: auto;
  background: var(--tianshu-bg-muted);
  border: 1px solid var(--tianshu-border-subtle);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
}
.governance-section {
  margin-bottom: 16px;
}
.governance-revision-selector {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  color: var(--tianshu-text-secondary);
  font-size: 13px;
}
.governance-revision-selector :deep(.el-select) {
  width: 240px;
}
.governance-revision-state {
  color: var(--el-color-primary);
  font-weight: 600;
}
.action-row {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}
.legacy-repair-preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 16px;
  color: var(--tianshu-text-secondary);
  font-size: 13px;
}
.governance-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  font-weight: 600;
}
</style>
