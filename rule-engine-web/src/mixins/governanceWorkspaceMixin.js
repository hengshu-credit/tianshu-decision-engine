import { editableResource } from '@/api/governance'

export default {
  data: () => ({ workspaceEditSequence: 0 }),
  beforeUnmount() { this.workspaceEditSequence++ },
  methods: {
    async editWorkspace(type, row, open) {
      const sequence = ++this.workspaceEditSequence
      const path = this.$route.fullPath
      try {
        const draft = await editableResource(type, row.id, row)
        if (sequence !== this.workspaceEditSequence || this.$route.fullPath !== path) return
        open(draft)
      } catch (error) {
        if (sequence === this.workspaceEditSequence && !error?.requestErrorNotified) this.$message.error(error.message || '草稿读取失败，请刷新后重试')
      }
    },
  },
}
