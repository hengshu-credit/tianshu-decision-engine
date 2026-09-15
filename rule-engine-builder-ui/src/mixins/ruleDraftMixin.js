import { listRuleRevisions } from '@/api/definition'
import * as definitionApi from '@/api/definition'
import RuleDesignerStatus from '@/components/rule/RuleDesignerStatus.vue'
import RuleDesignerDialogs from '@/components/rule/RuleDesignerDialogs.vue'
import {
  clearDraftRecovery,
  createDraftFingerprint,
} from '@/utils/ruleDesignerDraft'
import { registerDesignerLeaveGuard } from '@/utils/designerLeaveGuard'
import { hasPermission } from '@/security/permissionState'
import { graphIssueTarget, validationPathLabel, validationRepairHint } from '@/utils/validationIssueLocation'

function unwrap(response) {
  return response && response.data !== undefined ? response.data : response
}

function isSourceId(value) {
  return /^[1-9]\d*$/.test(String(value || ''))
}

function sourceKey(source) {
  return source ? `${source.sourceType}:${source.sourceId}` : ''
}

const REVISION_STATE_LABELS = {
  DRAFT: '待修改',
  REVIEW: '评审中',
  REJECTED: '已驳回',
  APPROVED: '已批准',
  PUBLISHED: '已发布',
  OFFLINE: '已下线',
  DELETED: '已删除',
}

export default {
  components: { RuleDesignerStatus, RuleDesignerDialogs },
  data() {
    return {
      draftRevision: null,
      viewRevision: null,
      viewExactRevisionId: '',
      draftGuardLoaded: false,
      draftGuardPromise: null,
      draftIssues: [],
      draftGuardError: null,
      draftGuardNeedsRefresh: false,
      draftGuardActive: true,
      draftGuardDefinitionId: null,
      draftGuardRouteKey: '',
      viewRefreshToken: 0,
      designerRevisions: [],
      designerVersions: [],
      designerSourcesLoading: false,
      designerActionState: 'CLEAN',
      designerBaselineFingerprint: '',
      designerCurrentFingerprint: '',
      designerCheckedFingerprint: '',
      designerValidationReport: null,
      designerCompileResult: null,
      designerRecoveryCandidate: null,
      designerDraftTrackingReady: false,
      designerCaptureQueued: false,
      designerRestoringRecovery: false,
      designerLeaveUnregister: null,
      designerLeaveApproved: false,
      designerBusy: false,
      designerChoice: null,
      designerChoiceResolve: null,
      designerSaveRequest: null,
    }
  },
  computed: {
    incomingValidationIssue() {
      const query = this.$route && this.$route.query || {}
      if (!query.validationPath || query.sourceType !== 'REVISION' || query.validationSourceId !== query.sourceId) return null
      if (query.validationLockVersion !== undefined && query.validationLockVersion !== '' && this.viewRevision &&
        String(this.viewRevision.lockVersion) !== String(query.validationLockVersion)) {
        return { path: '$', message: '校验后配置已变化，请返回生命周期重新校验', revisionId: query.sourceId, stale: true }
      }
      return { path: String(query.validationPath), message: String(query.validationMessage || ''), revisionId: query.sourceId }
    },
    requestedSource() {
      const query = this.$route?.query || {}
      const sourceType = query.sourceType
      if (!['REVISION', 'VERSION'].includes(sourceType) || !isSourceId(query.sourceId)) {
        return null
      }
      return { sourceType, sourceId: String(query.sourceId) }
    },
    viewRevisionLabel() {
      if (!this.viewRevision) return ''
      if (this.viewRevision.state === 'DELETED') return '草稿已删除，不可保存'
      if (this.viewRevision.sourceType === 'LEGACY_CONTENT') {
        return '当前设计内容'
      }
      const prefix = this.viewRevision.state === 'VERSION' ? '版本' : this.viewRevision.state === 'DRAFT' ? '草稿' : '修订'
      return `${prefix} ${this.viewRevision.revisionNo || ''}`.trim()
    },
    canEditDraft() {
      return (
        this.draftGuardLoaded && !this.draftGuardError && !!this.viewRevision && this.viewRevision.state !== 'DELETED' && hasPermission('rule:edit')
      )
    },
    canForkViewRevision() {
      return ['VERSION', 'APPROVED', 'PUBLISHED', 'OFFLINE', 'LEGACY'].includes(
        this.viewRevision?.state
      )
    },
    hasPendingDraft() {
      return this.draftRevision?.state === 'DRAFT'
    },
    selectedDesignerSource() {
      if (this.viewRevision?.state === 'VERSION') return `VERSION:${this.viewRevision.sourceId || this.viewRevision.id}`
      if (!this.viewRevision || !isSourceId(this.viewRevision.id)) return ''
      return `REVISION:${String(this.viewRevision.id)}`
    },
    designerSourceOptions() {
      const revisions = this.designerRevisions
        .filter((item) => item && isSourceId(item.id) && item.state !== 'DELETED')
        .map((item) => ({
          value: `REVISION:${String(item.id)}`,
          label: item.state === 'DRAFT' ? `草稿 · ${item.revisionNo || item.id}${item.updateTime ? ' / ' + item.updateTime : ''}` : `${REVISION_STATE_LABELS[item.state] || '已保存'} · ${item.revisionNo || item.id}`,
          group: 'REVISION',
          id: String(item.id), state: item.state, lockVersion: item.lockVersion,
          sourceLabel: item.sourceId ? `基于${item.sourceType === 'VERSION' ? '版本快照' : '修订'} ${item.sourceId}` : '',
        }))
      const versions = this.designerVersions
        .filter((item) => item && isSourceId(item.id))
        .map((item) => ({
          value: `VERSION:${String(item.id)}`,
          label: `发布版本 v${item.version || '—'}`,
          group: 'VERSION',
        }))

      if (this.viewRevision && !['LEGACY', 'DELETED'].includes(this.viewRevision.state)) {
        const selectedValue = this.selectedDesignerSource
        const group = selectedValue.startsWith('VERSION:') ? 'VERSION' : 'REVISION'
        const options = group === 'VERSION' ? versions : revisions
        if (selectedValue && !options.some((item) => item.value === selectedValue)) {
          options.push({
            value: selectedValue,
            label:
              group === 'VERSION'
                ? `发布版本 v${this.viewRevision.revisionNo || '—'}`
                : `${REVISION_STATE_LABELS[this.viewRevision.state] || '生命周期'} · ${this.viewRevision.revisionNo || this.viewRevision.id}`,
            group,
          })
        }
      }
      return [...versions, ...revisions]
    },
    designerCanTest() {
      return this.draftGuardLoaded && !this.draftGuardError && !!this.viewRevision && this.viewRevision.state !== 'DELETED' && hasPermission('rule:edit')
    },
    designerHasUnsavedChanges() {
      return (
        this.designerActionState === 'SAVING' ||
        (
        this.designerDraftTrackingReady &&
        Boolean(this.designerCurrentFingerprint) &&
        this.designerCurrentFingerprint !== this.designerBaselineFingerprint
        )
      )
    },
  },
  watch: {
    '$route.query'() {
      if (!this.draftGuardActive || !this.isOwnDesignerRoute()) return
      const routeKey = this.currentDesignerRouteKey()
      if (!routeKey || routeKey === this.draftGuardRouteKey) return
      this.draftGuardRouteKey = routeKey
      this.startViewedRevisionRefresh(true)
    },
  },
  created() {
    this.draftGuardDefinitionId = String(this.$route?.params?.id || '')
    this.draftGuardRouteKey = this.currentDesignerRouteKey()
    this.startViewedRevisionRefresh(false)
  },
  mounted() {
    if (typeof window === 'undefined') return
    window.addEventListener('keydown', this.handleDesignerSaveShortcut)
    window.addEventListener('beforeunload', this.handleDesignerBeforeUnload)
    this.registerDesignerLeaveProtection()
  },
  updated() {
    this.queueDesignerDraftCapture()
  },
  beforeUnmount() {
    this.resolveDesignerChoice({ action: 'cancel' })
    this.viewRefreshToken++
    if (typeof window !== 'undefined') {
      window.removeEventListener('keydown', this.handleDesignerSaveShortcut)
      window.removeEventListener('beforeunload', this.handleDesignerBeforeUnload)
    }
    if (this.designerLeaveUnregister) this.designerLeaveUnregister()
  },
  beforeRouteLeave(to, _from, next) {
    if (this.designerLeaveApproved) {
      this.designerLeaveApproved = false
      next()
      return
    }
    if (this.isOwnExpressionRoute(to)) {
      next()
      return
    }
    this.confirmDesignerLeave({ discardRecovery: true }).then((confirmed) => {
      if (confirmed) next()
      else next(false)
    })
  },
  activated() {
    this.draftGuardActive = true
    this.registerDesignerLeaveProtection()
    if (!this.draftGuardNeedsRefresh || !this.isOwnDesignerRoute()) return
    this.draftGuardNeedsRefresh = false
    this.startViewedRevisionRefresh(true)
  },
  deactivated() {
    this.draftGuardActive = false
    this.draftGuardNeedsRefresh = !this.isOwnExpressionRoute()
  },
  methods: {
    async locateDesignerIssue(issue) {
      if (issue && issue.stale) { this.$message.warning('校验后配置已变化，请返回生命周期重新校验'); return }
      if (issue && issue.revisionId && String(issue.revisionId) !== String(this.viewRevision && this.viewRevision.id)) {
        this.$message.warning('校验结果不属于正在查看的修订，请重新校验')
        return
      }
      if (this.designerHasUnsavedChanges) {
        this.$message.warning('配置已有未保存修改，请先保存并检查，避免按旧位置定位')
        return
      }
      const path = String(issue && issue.path || '$')
      let model
      try { model = JSON.parse(this.viewRevision && this.viewRevision.modelJson || '{}') } catch (e) { model = {} }
      const graphTarget = graphIssueTarget(model, path)
      if (graphTarget && typeof this.locateGraphElement === 'function') {
        this.locateGraphElement(graphTarget)
        return
      }
      await this.$nextTick()
      const elements = this.$el && this.$el.querySelectorAll ? this.$el.querySelectorAll('[data-validation-path]') : []
      const target = Array.from(elements).find(element => path === element.dataset.validationPath || path.startsWith(element.dataset.validationPath + '.'))
      if (target) {
        target.scrollIntoView({ block: 'center', behavior: 'smooth' })
        target.setAttribute('tabindex', '-1')
        target.focus({ preventScroll: true })
      } else {
        this.$message.info(`${validationPathLabel(path)}（${path}）：${validationRepairHint(issue)}`)
      }
    },
    registerDesignerLeaveProtection() {
      if (!this.isOwnDesignerRoute()) return
      const path = this.$route?.fullPath || this.$route?.path
      if (!path) return
      if (this.designerLeaveUnregister) this.designerLeaveUnregister()
      this.designerLeaveUnregister = registerDesignerLeaveGuard(path, () =>
        this.confirmDesignerLeave({ discardRecovery: true })
      )
    },
    designerSessionStorage() {
      try {
        return typeof window !== 'undefined' ? window.sessionStorage : null
      } catch {
        return null
      }
    },
    designerDraftIdentity(revision = this.draftRevision) {
      return {
        definitionId: this.definitionId || this.$route?.params?.id,
        revisionId: revision?.id,
        lockVersion: revision?.lockVersion,
      }
    },
    initializeDesignerDraftTracking(modelJson) {
      if (this.designerRestoringRecovery) return
      if (!this.canEditDraft) {
        this.designerDraftTrackingReady = false
        this.designerRecoveryCandidate = null
        return
      }
      const serialized =
        typeof modelJson === 'string'
          ? modelJson
          : this.serializeDesignerDraft?.()
      if (typeof serialized !== 'string') return
      const fingerprint = createDraftFingerprint(serialized)
      this.designerBaselineFingerprint = fingerprint
      this.designerCurrentFingerprint = fingerprint
      this.designerCheckedFingerprint = ''
      this.designerValidationReport = null
      this.designerCompileResult = null
      this.designerActionState = 'CLEAN'
      this.designerDraftTrackingReady = true
      this.designerRecoveryCandidate = null
    },
    queueDesignerDraftCapture() {
      if (
        this.designerCaptureQueued ||
        !this.designerDraftTrackingReady ||
        typeof this.serializeDesignerDraft !== 'function'
      ) {
        return
      }
      this.designerCaptureQueued = true
      Promise.resolve().then(() => {
        this.designerCaptureQueued = false
        this.captureDesignerDraftState()
      })
    },
    captureDesignerDraftState() {
      if (
        !this.designerDraftTrackingReady ||
        !this.canEditDraft ||
        typeof this.serializeDesignerDraft !== 'function'
      ) {
        return
      }
      let modelJson
      try {
        modelJson = this.serializeDesignerDraft?.() || modelJson
      } catch {
        return
      }
      if (typeof modelJson !== 'string') return
      const fingerprint = createDraftFingerprint(modelJson)
      if (fingerprint === this.designerCurrentFingerprint) return
      this.designerCurrentFingerprint = fingerprint
      if (fingerprint === this.designerBaselineFingerprint) {
        this.designerActionState =
          this.designerCheckedFingerprint === fingerprint
            ? 'READY_TO_TEST'
            : 'CLEAN'
        this.designerRecoveryCandidate = null
        return
      }
      if (this.designerActionState !== 'SAVE_CONFLICT') {
        this.designerActionState = 'DIRTY'
      }
      this.designerCheckedFingerprint = ''
      this.designerValidationReport = null
      this.designerCompileResult = null
      // 普通编辑只更新内存状态，不自动创建服务端或浏览器草稿。
    },
    markDesignerDraftSaved(modelJson) {
      const fingerprint = createDraftFingerprint(modelJson)
      this.designerBaselineFingerprint = fingerprint
      this.designerCurrentFingerprint = typeof this.serializeDesignerDraft === 'function' ? createDraftFingerprint(this.serializeDesignerDraft()) : fingerprint
      this.designerCheckedFingerprint = ''
      this.designerValidationReport = null
      this.designerCompileResult = null
      this.designerDraftTrackingReady = true
      this.designerActionState = this.designerCurrentFingerprint === fingerprint ? 'SAVED_UNCHECKED' : 'DIRTY'
      clearDraftRecovery(
        this.designerSessionStorage(),
        this.designerDraftIdentity()
      )
      this.designerRecoveryCandidate = null
    },
    async restoreDesignerRecovery() {
      const recovery = this.designerRecoveryCandidate
      if (!recovery || typeof this.loadContent !== 'function') return
      const revision = this.viewRevision
      const serverModelJson = revision?.modelJson
      this.designerRestoringRecovery = true
      try {
        if (revision) revision.modelJson = recovery.modelJson
        await this.loadContent()
        await this.$nextTick()
      } finally {
        if (revision) revision.modelJson = serverModelJson
        this.designerRestoringRecovery = false
      }
      this.designerCurrentFingerprint = recovery.fingerprint
      this.designerActionState = 'DIRTY'
      this.designerCheckedFingerprint = ''
      this.designerValidationReport = null
      this.designerRecoveryCandidate = null
    },
    discardDesignerRecovery() {
      clearDraftRecovery(
        this.designerSessionStorage(),
        this.designerDraftIdentity()
      )
      this.designerRecoveryCandidate = null
    },
    async confirmDesignerLeave(options = {}) {
      this.captureDesignerDraftState()
      if (!this.designerHasUnsavedChanges) return true
      try {
        await this.$confirm(
          '当前设计有未保存修改，放弃修改并离开吗？',
          '未保存提醒',
          {
            type: 'warning',
            confirmButtonText: '放弃并离开',
            cancelButtonText: '继续编辑',
          }
        )
        if (options.discardRecovery) {
          this.discardDesignerRecovery()
          this.designerLeaveApproved = true
        }
        return true
      } catch {
        return false
      }
    },
    handleDesignerBeforeUnload(event) {
      this.captureDesignerDraftState()
      if (!this.designerHasUnsavedChanges) return
      event.preventDefault()
      event.returnValue = ''
    },
    handleDesignerSaveShortcut(event) {
      if (
        !this.canEditDraft ||
        !this.draftGuardActive ||
        !(event.ctrlKey || event.metaKey) ||
        String(event.key || '').toLowerCase() !== 's'
      ) {
        return
      }
      event.preventDefault()
      if (!this.designerBusy) this.runDesignerAction(() => this.handleSave?.())
    },
    ensureDesignerReadyForTest() {
      this.captureDesignerDraftState()
      if (this.designerCanTest) return true
      this.$message.warning('当前内容未加载或没有规则执行权限')
      return false
    },
    currentDesignerRouteKey() {
      if (!this.isOwnDesignerRoute()) return ''
      return `${this.draftGuardDefinitionId}:${sourceKey(this.requestedSource)}`
    },
    isOwnDesignerRoute(route = this.$route) {
      const routeId = route?.params?.id
      return (
        routeId != null &&
        String(routeId) === String(this.draftGuardDefinitionId || '')
      )
    },
    isOwnExpressionRoute(route = this.$route) {
      return (
        route?.name === 'ExpressionEditor' &&
        String(route?.params?.ruleId || '') ===
          String(this.draftGuardDefinitionId || '')
      )
    },
    startViewedRevisionRefresh(reloadContent) {
      if (!this.isOwnDesignerRoute()) return Promise.resolve(null)
      const refreshPromise = this.refreshViewedRevision()
      this.draftGuardPromise = refreshPromise
      if (reloadContent) {
        this.runDesignerAction(() => refreshPromise.then((result) => {
          if (
            result &&
            this.isCurrentSource(result.sourceKey, result.refreshToken) &&
            this.viewRevision &&
            typeof this.loadContent === 'function'
          ) {
            return this.loadContent()
          }
          return null
        }))
      }
      return refreshPromise
    },
    isCurrentSource(requestedSourceKey, refreshToken) {
      return (
        refreshToken === this.viewRefreshToken &&
        requestedSourceKey === sourceKey(this.requestedSource)
      )
    },
    isCurrentViewAction(action) {
      return (
        this.draftGuardActive && this.isOwnDesignerRoute() &&
        this.isCurrentSource(action.sourceKey, action.refreshToken) &&
        String(this.viewRevision?.id) === action.viewId
      )
    },
    isCurrentDraftAction(action) {
      return (
        this.isCurrentViewAction(action) &&
        String(this.draftRevision?.id) === action.draftId
      )
    },
    async loadDraftRevision() {
      return this.startViewedRevisionRefresh(false)
    },
    async refreshViewedRevision() {
      const definitionId = this.definitionId || this.$route.params.id
      const source = this.requestedSource
      const requestedSourceKey = sourceKey(source)
      const refreshToken = ++this.viewRefreshToken
      this.draftGuardLoaded = false
      this.draftRevision = null
      this.viewRevision = null
      this.viewExactRevisionId = source?.sourceType === 'REVISION' ? source.sourceId : ''
      this.draftIssues = []
      this.draftGuardError = null
      this.designerSourcesLoading = true
      const listPromise = Promise.resolve().then(() => listRuleRevisions(definitionId))
      const versionsPromise = Promise.resolve().then(() =>
        definitionApi.listPublishedVersions(definitionId)
      )
      this.loadDesignerVersions(
        versionsPromise,
        requestedSourceKey,
        refreshToken
      )
      try {
        if (source) {
          const exactSourcePromise = Promise.resolve().then(() =>
            source.sourceType === 'REVISION'
              ? definitionApi.getRuleRevision(definitionId, source.sourceId)
              : definitionApi.getVersionById(definitionId, source.sourceId)
          )
          const [listResult, sourceResult] = await Promise.allSettled([
            listPromise,
            exactSourcePromise,
          ])
          if (!this.isCurrentSource(requestedSourceKey, refreshToken)) return null

          if (listResult.status === 'fulfilled') {
            const data = unwrap(listResult.value)
            const revisions = Array.isArray(data) ? [...data] : []
            revisions.sort(
              (left, right) =>
                Number(right.revisionNo || 0) - Number(left.revisionNo || 0)
            )
            this.draftRevision =
              revisions.find((item) => item.state === 'DRAFT') || null
            this.designerRevisions = revisions
          } else {
            this.designerRevisions = []
          }
          if (sourceResult.status !== 'fulfilled') {
            this.viewRevision = null
            this.draftGuardError = sourceResult.reason
            return null
          }

          const sourceData = unwrap(sourceResult.value)
          if (!sourceData) throw new Error('当前节点不存在')
          this.viewRevision =
            source.sourceType === 'VERSION'
              ? {
                  ...sourceData,
                  state: 'VERSION',
                  revisionNo: sourceData.businessVersion || sourceData.version,
                  sourceType: 'VERSION',
                  sourceId: source.sourceId,
                }
              : sourceData
          return { refreshToken, sourceKey: requestedSourceKey }
        }

        const response = await listPromise
        if (!this.isCurrentSource(requestedSourceKey, refreshToken)) return null
        const data = unwrap(response)
        const revisions = Array.isArray(data) ? [...data] : []
        revisions.sort(
          (left, right) =>
            Number(right.revisionNo || 0) - Number(left.revisionNo || 0)
        )
        this.draftRevision =
          revisions.find((item) => item.state === 'DRAFT') || null
        this.designerRevisions = revisions
        const versionResponse = await versionsPromise
        if (!this.isCurrentSource(requestedSourceKey, refreshToken)) return null
        const versions = this.normalizeDesignerVersions(versionResponse)
        if (versions.length) {
          const versionId = String(versions[0].id)
          const version = unwrap(await definitionApi.getVersionById(definitionId, versionId))
          if (!this.isCurrentSource(requestedSourceKey, refreshToken)) return null
          if (!version) throw new Error('最新版本不存在或无法读取')
          this.viewRevision = { ...version, state: 'VERSION', revisionNo: version.businessVersion || version.version, sourceType: 'VERSION', sourceId: versionId }
          return { refreshToken, sourceKey: requestedSourceKey }
        }
        this.viewRevision = revisions[0] || null
        if (!this.viewRevision) {
          const contentResponse = await definitionApi.getContent(definitionId)
          if (!this.isCurrentSource(requestedSourceKey, refreshToken)) return null
          const content = unwrap(contentResponse)
          const modelJson = content?.modelJson
          if (typeof modelJson !== 'string' || !modelJson.trim()) {
            throw new Error('当前规则没有可查看的历史内容')
          }
          try {
            JSON.parse(modelJson)
          } catch {
            throw new Error('当前规则的历史内容不是有效 JSON')
          }
          this.viewRevision = {
            id: `legacy-content:${definitionId}`,
            definitionId,
            state: 'LEGACY',
            sourceType: 'LEGACY_CONTENT',
            sourceId: String(definitionId),
            modelJson,
          }
        }
        return { refreshToken, sourceKey: requestedSourceKey }
      } catch (error) {
        if (!this.isCurrentSource(requestedSourceKey, refreshToken)) return null
        this.viewRevision = null
        this.draftGuardError = error
        return null
      } finally {
        if (this.isCurrentSource(requestedSourceKey, refreshToken)) {
          this.draftGuardLoaded = true
        }
      }
    },
    loadDesignerVersions(versionsPromise, requestedSourceKey, refreshToken) {
      return Promise.resolve(versionsPromise)
        .then((response) => {
          if (!this.isCurrentSource(requestedSourceKey, refreshToken)) return
          this.designerVersions = this.normalizeDesignerVersions(response)
        })
        .catch(() => {
          if (!this.isCurrentSource(requestedSourceKey, refreshToken)) return
          this.designerVersions = []
        })
        .finally(() => {
          if (!this.isCurrentSource(requestedSourceKey, refreshToken)) return
          this.designerSourcesLoading = false
        })
    },
    normalizeDesignerVersions(response) {
      const data = unwrap(response)
      return (Array.isArray(data) ? [...data] : []).sort(
        (left, right) => Number(right.version || 0) - Number(left.version || 0)
      )
    },
    async runDesignerAction(action) {
      try {
        return await action()
      } catch (error) {
        if (!error?.requestErrorNotified) {
          this.$message.error(error?.message || '操作失败，请重试')
          if (error && typeof error === 'object') error.requestErrorNotified = true
        }
        return false
      }
    },
    requestDesignerChoice(kind) {
      if (this.designerChoice) return Promise.resolve({ action: 'cancel' })
      this.designerChoice = { kind, canOverwrite: this.viewRevision?.state === 'DRAFT', versions: this.designerVersions }
      return new Promise(resolve => { this.designerChoiceResolve = resolve })
    },
    resolveDesignerChoice(choice = { action: 'cancel' }) {
      const resolve = this.designerChoiceResolve
      this.designerChoice = null
      this.designerChoiceResolve = null
      if (resolve) resolve(choice)
    },
    designerSourcePayload() {
      const view = this.viewRevision
      if (!view || view.state === 'LEGACY') return {}
      if (view.state === 'VERSION') return { sourceType: 'VERSION', sourceId: String(view.sourceId || view.id) }
      const exactId = this.viewExactRevisionId || String(view.id)
      return { sourceType: 'REVISION', sourceId: exactId }
    },
    designerActionSnapshot() {
      return { sourceKey: sourceKey(this.requestedSource), refreshToken: this.viewRefreshToken, viewId: String(this.viewRevision?.id) }
    },
    designerConfigurationMatches(action, modelJson) {
      return this.isCurrentViewAction(action) && createDraftFingerprint(this.serializeDesignerDraft()) === createDraftFingerprint(modelJson)
    },
    switchDesignerSource(value) {
      return this.runDesignerAction(async () => {
        const match = /^(REVISION|VERSION):([1-9]\d*)$/.exec(String(value || ''))
        if (!match || value === this.selectedDesignerSource || this.designerBusy) return false
        this.designerBusy = true
        try {
          this.captureDesignerDraftState()
          if (this.designerHasUnsavedChanges) {
            const action = this.designerActionSnapshot()
            const choice = await this.requestDesignerChoice('switch')
            if (choice.action === 'cancel' || !this.isCurrentViewAction(action)) return false
            if (choice.action === 'save') {
              const modelJson = this.serializeDesignerDraft()
              const result = await this.saveDraftModel(modelJson, { saveMode: choice.saveMode, stayOnSource: true })
              if (!result || !this.isCurrentViewAction({ ...action, viewId: String(result.revision.id) })) return false
              if (this.designerHasUnsavedChanges) throw new Error('保存期间配置又有修改，请再次选择切换方式')
            }
          }
          await this.$router.replace({ query: { ...(this.$route?.query || {}), sourceType: match[1], sourceId: match[2] } })
          return true
        } finally {
          this.designerBusy = false
        }
      })
    },
    async forkViewRevision() {
      return { revision: this.viewRevision }
    },
    async saveDraftModel(modelJson, extra = {}) {
      if (this.draftGuardPromise) await this.draftGuardPromise
      if (!this.canEditDraft) throw new Error('当前规则没有可编辑内容、已被删除或没有编辑权限')
      if (this.designerActionState === 'SAVING') throw new Error('正在保存，请稍候')
      const definitionId = String(this.definitionId || this.$route.params.id)
      const action = this.designerActionSnapshot()
      const currentDraft = this.viewRevision.state === 'DRAFT' ? this.viewRevision : null
      const fingerprint = createDraftFingerprint(modelJson)
      if (currentDraft && this.designerDraftTrackingReady && fingerprint === this.designerBaselineFingerprint && !extra.updateOpenApiConfig) {
        return { revision: currentDraft, compileSuccess: currentDraft.compileSuccess !== false, issues: this.draftIssues }
      }
      let saveMode = currentDraft ? extra.saveMode : 'NEW'
      if (!saveMode) {
        const choice = await this.requestDesignerChoice('save')
        if (choice.action !== 'save' || !this.isCurrentViewAction(action)) return false
        saveMode = choice.saveMode
        // 弹窗期间仍可能编辑，保存确认时的当前配置。
        modelJson = this.serializeDesignerDraft?.() || modelJson
      }
      if (!['NEW', 'OVERWRITE'].includes(saveMode)) throw new Error('请选择保存方式')
      const body = { modelJson, saveMode, ...this.designerSourcePayload() }
      if (saveMode === 'OVERWRITE') {
        if (!currentDraft) throw new Error('只有当前草稿可以覆盖')
        body.revisionId = body.sourceId
        body.lockVersion = currentDraft.lockVersion
      }
      ;['openApiConfigJson', 'updateOpenApiConfig'].forEach(field => {
        if (Object.prototype.hasOwnProperty.call(extra, field)) body[field] = extra[field]
      })
      const signature = JSON.stringify({ definitionId, ...body })
      if (this.designerSaveRequest?.signature !== signature) {
        const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16))
        this.designerSaveRequest = { signature, requestId: Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('') }
      }
      body.requestId = this.designerSaveRequest.requestId
      this.designerActionState = 'SAVING'
      let result
      try {
        result = unwrap(await definitionApi.saveDesignerDraft(definitionId, body))
        if (!result?.revision || result.revision.state !== 'DRAFT' || result.compileSuccess === false) throw new Error(result?.compileMessage || '草稿保存响应无效，页面修改仍保留')
      } catch (error) {
        if (this.isCurrentViewAction(action)) {
          this.designerActionState = error?.response?.status === 409 ? 'SAVE_CONFLICT' : 'DIRTY'
          this.captureDesignerDraftState()
        }
        throw error
      }
      this.designerSaveRequest = null
      if (!this.isCurrentViewAction(action)) return false
      this.draftRevision = result.revision
      this.viewRevision = result.revision
      this.viewExactRevisionId = String(result.revision.id)
      this.draftIssues = Array.isArray(result.issues) ? result.issues : []
      this.designerRevisions = [result.revision, ...this.designerRevisions.filter(item => String(item.id) !== String(result.revision.id))]
      this.markDesignerDraftSaved(body.modelJson)
      if (!extra.stayOnSource) {
        const sourceId = String(result.revision.id)
        this.draftGuardRouteKey = `${this.draftGuardDefinitionId}:REVISION:${sourceId}`
        await this.$router.replace({ query: { ...(this.$route?.query || {}), sourceType: 'REVISION', sourceId } })
      }
      if (this.draftIssues.some(issue => issue.severity !== 'WARNING')) this.$message.warning('草稿已保存，但发布前检查存在阻断项')
      return result
    },
    async compileDesignerDraft() {
      if (!this.canEditDraft) throw new Error('当前内容不可编译或没有编辑权限')
      const action = this.designerActionSnapshot()
      const modelJson = this.serializeDesignerDraft()
      const result = unwrap(await definitionApi.compileDesignerModel(String(this.definitionId || this.$route.params.id), { modelJson, ...this.designerSourcePayload() }))
      this.captureDesignerDraftState()
      if (!this.designerConfigurationMatches(action, modelJson)) return false
      this.designerCompileResult = result
      this.designerValidationReport = result?.preflightReport || null
      this.designerCheckedFingerprint = result?.compileSuccess ? createDraftFingerprint(modelJson) : ''
      this.designerActionState = result?.compileSuccess && result?.preflightReport?.valid ? 'READY_TO_TEST' : 'CHECK_FAILED'
      if (!result?.compileSuccess) this.$message.error(result?.compileMessage || '编译失败')
      else if (!result?.preflightReport?.valid) this.$message.warning('编译成功，但发布前检查存在阻断项')
      else this.$message.success('编译与发布前检查通过')
      return result
    },
    executeDesignerPreview(params, modelType) {
      return this.runDesignerAction(() => definitionApi.executeRule({
        definitionId: String(this.definitionId || this.$route.params.id),
        projectId: this.projectIdForRefs,
        modelType,
        modelJson: this.serializeDesignerDraft(),
        params,
      }))
    },
    handlePublish() {
      return this.runDesignerAction(async () => {
        if (!hasPermission('rule:edit') || !hasPermission('rule:submit')) throw new Error('没有提交规则发布审批的权限')
        if (this.designerBusy) return false
        this.designerBusy = true
        try {
          let compiled = await this.compileDesignerDraft()
          if (!compiled?.compileSuccess || !compiled.preflightReport?.valid) return false
          let saved = await this.saveDraftModel(this.serializeDesignerDraft())
          if (!saved) return false
          this.designerVersions = this.normalizeDesignerVersions(await definitionApi.listPublishedVersions(String(this.definitionId || this.$route.params.id)))
          const action = this.designerActionSnapshot()
          const modelJson = this.serializeDesignerDraft()
          const choice = await this.requestDesignerChoice('publish')
          if (choice.action !== 'publish' || !this.isCurrentViewAction(action)) return false
          if (!this.designerConfigurationMatches(action, modelJson)) {
            compiled = await this.compileDesignerDraft()
            if (!compiled?.compileSuccess || !compiled.preflightReport?.valid) return false
            saved = await this.saveDraftModel(this.serializeDesignerDraft())
            if (!saved) return false
          }
          if (this.designerHasUnsavedChanges) throw new Error('配置又有修改，请重新发布')
          const body = { revisionId: String(saved.revision.id), lockVersion: saved.revision.lockVersion, publishMode: choice.publishMode, comment: choice.comment || '' }
          if (choice.publishMode === 'OVERWRITE') {
            const target = this.designerVersions.find(version => String(version.bindingId) === String(choice.targetVersionId))
            if (!target) throw new Error('请选择要覆盖的已发布版本')
            body.targetVersionId = String(target.bindingId)
            body.targetGeneration = target.generation
          }
          const submitAction = this.designerActionSnapshot()
          const result = unwrap(await definitionApi.publishDesignerDraft(String(this.definitionId || this.$route.params.id), body))
          if (!this.isCurrentViewAction(submitAction)) return result
          if (result?.revision) {
            this.viewRevision = result.revision
            this.designerRevisions = [result.revision, ...this.designerRevisions.filter(item => String(item.id) !== String(result.revision.id))]
            if (String(this.draftRevision?.id) === String(result.revision.id)) this.draftRevision = null
          }
          this.$message.success('已提交发布审批，审批通过后生效')
          return result
        } finally {
          this.designerBusy = false
        }
      })
    },
    deleteDesignerSource(item) {
      return this.runDesignerAction(async () => {
        if (item?.state !== 'DRAFT' || this.designerBusy) return false
        if (!hasPermission('rule:edit')) throw new Error('没有删除草稿的权限')
        const current = item.value === this.selectedDesignerSource
        const action = this.designerActionSnapshot()
        this.designerBusy = true
        try {
          try {
            await this.$confirm(current ? '删除当前草稿？页面未保存的修改也会放弃。' : '删除所选草稿？其他草稿和已发布版本保持不变。', '删除草稿', { type: 'warning', confirmButtonText: '删除草稿', cancelButtonText: '取消' })
          } catch { return false }
          await definitionApi.deleteDesignerDraft(String(this.definitionId || this.$route.params.id), String(item.id), item.lockVersion)
          this.designerRevisions = this.designerRevisions.filter(revision => String(revision.id) !== String(item.id))
          if (current && this.isCurrentViewAction(action)) {
            const versions = this.normalizeDesignerVersions(await definitionApi.listPublishedVersions(String(this.definitionId || this.$route.params.id)))
            const draft = this.designerRevisions.find(revision => revision.state === 'DRAFT')
            const query = { ...(this.$route?.query || {}) }
            delete query.sourceType
            delete query.sourceId
            if (versions.length || draft) {
              query.sourceType = versions.length ? 'VERSION' : 'REVISION'
              query.sourceId = String(versions.length ? versions[0].id : draft.id)
            }
            this.designerDraftTrackingReady = false
            await this.$router.replace({ query })
          }
          this.$message.success('草稿已删除，审计记录保留')
          return true
        } finally {
          this.designerBusy = false
        }
      })
    },
    goRuleLifecycle() {
      this.$router.push({
        name: 'RuleDetail',
        params: { id: this.definitionId || this.$route.params.id },
        query: { focus: 'lifecycle' },
      })
    },
  },
}
