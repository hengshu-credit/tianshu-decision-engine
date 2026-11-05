import { listLedgerTypes } from '@/api/ledger'
import { ledgerCategoryLabel } from '@/utils/ledger'

export default {
  data: () => ({ ledgerTypes: [], ledgerTypesError: '', ledgerTypesGeneration: 0 }),
  created() { this.ledgerTypesPromise = this.loadLedgerTypes() },
  beforeUnmount() { this.ledgerTypesGeneration++ },
  methods: {
    async loadLedgerTypes() {
      const generation = ++this.ledgerTypesGeneration
      this.ledgerTypesError = ''
      try { const response = await listLedgerTypes(); if (generation === this.ledgerTypesGeneration) this.ledgerTypes = response.data || [] }
      catch { if (generation === this.ledgerTypesGeneration) this.ledgerTypesError = '事件类型加载失败，请刷新重试' }
    },
    categoryLabel(value) { return this.ledgerTypes.find(item => item.value === String(value) || item.legacyCode === value)?.name || ledgerCategoryLabel(value) },
    categoryId(value) { return this.ledgerTypes.find(item => item.value === String(value) || item.legacyCode === value)?.value || value },
  },
}
