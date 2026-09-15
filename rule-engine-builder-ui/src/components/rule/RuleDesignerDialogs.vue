<template>
  <el-dialog :model-value="Boolean(choice)" :title="title" width="480px" :close-on-click-modal="false" append-to-body :before-close="cancel">
    <template v-if="choice">
      <template v-if="choice.kind === 'publish'">
        <p>提交审批后，审批通过才会更新生效版本。</p>
        <el-radio-group v-model="publishMode" aria-label="发布方式">
          <el-radio value="NEW">新增正式版本</el-radio>
          <el-radio value="OVERWRITE" :disabled="!choice.versions.length">覆盖已有版本</el-radio>
        </el-radio-group>
        <el-select v-if="publishMode === 'OVERWRITE'" v-model="targetVersionId" placeholder="选择覆盖版本" aria-label="覆盖版本">
          <el-option v-for="version in choice.versions" :key="String(version.bindingId)" :value="String(version.bindingId)" :label="`版本 v${version.version}`" />
        </el-select>
        <el-input v-model="comment" type="textarea" placeholder="变更说明" aria-label="变更说明" />
      </template>
      <template v-else>
        <p>{{ choice.kind === 'switch' ? '当前配置有未保存修改，请选择保存后切换或直接放弃修改。' : '请选择如何保存当前配置。' }}</p>
        <el-radio-group v-model="saveMode" aria-label="草稿保存方式">
          <el-radio value="NEW">新增草稿</el-radio>
          <el-radio v-if="choice.canOverwrite" value="OVERWRITE">覆盖当前草稿</el-radio>
        </el-radio-group>
        <p v-if="!choice.canOverwrite">将创建独立草稿，保留原版本。</p>
      </template>
    </template>
    <template #footer>
      <el-button data-action="cancel-choice" @click="resolve({ action: 'cancel' })">取消</el-button>
      <el-button v-if="choice?.kind === 'switch'" data-action="discard-switch" @click="resolve({ action: 'discard' })">不保存直接切换</el-button>
      <el-button v-if="choice?.kind === 'publish'" type="primary" data-action="confirm-publish" :disabled="publishMode === 'OVERWRITE' && !targetVersionId" @click="resolve({ action: 'publish', publishMode, targetVersionId, comment })">提交发布审批</el-button>
      <el-button v-else type="primary" data-action="confirm-save" @click="resolve({ action: 'save', saveMode })">{{ choice?.kind === 'switch' ? '保存并切换' : '保存草稿' }}</el-button>
    </template>
  </el-dialog>
</template>

<script>
export default {
  name: 'RuleDesignerDialogs',
  props: { choice: { type: Object, default: null } },
  emits: ['resolve'],
  data: () => ({ saveMode: 'NEW', publishMode: 'NEW', targetVersionId: '', comment: '' }),
  computed: { title() { return this.choice?.kind === 'publish' ? '发布规则' : this.choice?.kind === 'switch' ? '切换版本' : '保存草稿' } },
  watch: { choice() { this.saveMode = 'NEW'; this.publishMode = 'NEW'; this.targetVersionId = ''; this.comment = '' } },
  methods: {
    resolve(choice) { if (this.choice) this.$emit('resolve', choice) },
    cancel(done) { this.resolve({ action: 'cancel' }); done() },
  },
}
</script>

<style scoped>
p { color: var(--tianshu-text-primary); }
.el-radio-group { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.el-select { width: 100%; margin-bottom: 16px; }
</style>
