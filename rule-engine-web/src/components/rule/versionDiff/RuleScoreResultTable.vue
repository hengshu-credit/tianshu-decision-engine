<template>
  <div class="rule-score-result-table-wrap">
    <div class="rule-score-result-table-title">
      <strong>{{ sideLabel }}</strong>
      <span>完整配置</span>
    </div>
    <section
      v-for="section in sections"
      :key="section.key"
      class="rule-score-result-section"
      :data-section="section.key"
    >
      <div class="rule-score-result-section-head">
        <span>{{ sectionIcon(section.key) }}</span>
        <strong>{{ section.title }}</strong>
        <small>{{ section.lanes.length }} 项</small>
      </div>
      <table class="sc-table rule-score-result-table">
        <thead>
          <tr>
            <th>评估维度</th>
            <th>规则条件</th>
            <th>得分 / 结果</th>
            <th>权重 / 备注</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in sectionRows(section)"
            :key="row.key"
            class="rule-diff-lane rule-score-result-row"
            :class="'is-' + row.status"
          >
            <td :class="cellClass(row, 'title')">
              <strong>{{ row.title }}</strong>
              <span v-if="row.marker" class="rule-score-result-marker">{{ row.marker }}</span>
            </td>
            <td :class="cellClass(row, 'condition')">{{ row.condition.text }}</td>
            <td :class="cellClass(row, 'result')">{{ row.result.text }}</td>
            <td :class="cellClass(row, 'weight')">{{ row.weight.text }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="!sectionRows(section).length" class="rule-score-result-empty">
        暂无{{ section.title }}
      </div>
    </section>
  </div>
</template>

<script>
export default {
  name: 'RuleScoreResultTable',
  props: {
    sections: { type: Array, default: () => [] },
    side: { type: String, required: true },
    sideLabel: { type: String, required: true },
  },
  methods: {
    sectionIcon(key) {
      return key === 'thresholds' ? '档' : key === 'settings' ? '配' : '分'
    },
    sectionRows(section) {
      if (!section || !section.lanes) return []
      if (section.key === 'settings') return this.settingRows(section)
      return section.lanes.flatMap((lane, index) => this.laneRows(lane, `${section.key}-${index}`, section.key))
    },
    settingRows(section) {
      const lane = section.lanes[0]
      if (!lane) return []
      return (lane.fields || []).map((field, index) => {
        const value = this.fieldCell(lane, field.key, '配置')
        return {
          key: `${section.key}-${field.key}-${index}`,
          title: field.label,
          status: field.status,
          marker: this.marker(field.status),
          condition: value,
          result: this.emptyCell(),
          weight: this.emptyCell(),
        }
      })
    },
    laneRows(lane, key, sectionKey) {
      const rows = []
      const node = lane && lane[this.side]
      const condition = sectionKey === 'thresholds'
        ? this.combinedFields(lane, ['min', 'max'], '—')
        : this.conditionCell(lane)
      const result = sectionKey === 'thresholds'
        ? this.fieldCell(lane, 'result', '—')
        : this.fieldCell(lane, 'score', '—')
      const weight = sectionKey === 'thresholds'
        ? this.emptyCell()
        : this.fieldCell(lane, 'weight', '—')
      rows.push({
        key,
        title: node ? node.title : '此版本无对应内容',
        status: lane.status,
        marker: this.marker(lane.status),
        condition,
        result,
        weight,
      })
      ;(lane.children || []).forEach((child, index) => {
        rows.push(...this.laneRows(child, `${key}-${index}`, sectionKey))
      })
      return rows
    },
    conditionCell(lane) {
      const condition = this.fieldCell(lane, 'condition', '')
      if (condition.text) return condition
      const left = this.fieldCell(lane, 'leftOperand', '未配置')
      const operator = this.fieldCell(lane, 'operator', '')
      const right = this.fieldCell(lane, 'rightOperand', '未配置')
      const statuses = [left.status, operator.status, right.status]
      return {
        text: [left.text, operator.text, right.text].filter(Boolean).join(' ') || '未配置',
        status: statuses.find(status => status !== 'unchanged') || 'unchanged',
      }
    },
    combinedFields(lane, keys, fallback) {
      const cells = keys.map(key => this.fieldCell(lane, key, fallback))
      return {
        text: cells.map(cell => cell.text).join(' / '),
        status: cells.find(cell => cell.status !== 'unchanged')?.status || 'unchanged',
      }
    },
    fieldCell(lane, key, fallback) {
      const field = (lane && lane.fields || []).find(item => item.key === key)
      if (!field) return this.emptyCell(fallback)
      const missing = field.status === 'added' && this.side === 'left' ||
        field.status === 'removed' && this.side === 'right'
      return {
        text: missing ? '—' : (this.side === 'left' ? field.leftText : field.rightText) || fallback,
        status: field.status,
      }
    },
    emptyCell(text = '—') {
      return { text, status: 'unchanged' }
    },
    cellClass(row, key) {
      const cell = row[key]
      return [`is-${cell.status}`, cell.status === 'unchanged' ? '' : 'is-cell-change']
    },
    marker(status) {
      return { added: '+ 新增', removed: '- 删除', modified: '~ 修改' }[status] || ''
    },
  },
}
</script>

<style scoped>
.rule-score-result-table-wrap {
  min-width: 0;
}

.rule-score-result-table-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 2px 2px 10px;
  border-bottom: 1px solid var(--tianshu-border-subtle);
}

.rule-score-result-table-title strong {
  color: var(--tianshu-text-primary);
  font-size: 15px;
}

.rule-score-result-table-title span {
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
}

.rule-score-result-section + .rule-score-result-section {
  margin-top: 16px;
}

.rule-score-result-section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0 8px;
}

.rule-score-result-section-head > span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  background: #f4f0ff;
  color: #6f42c1;
  font-size: 12px;
  font-weight: 700;
}

.rule-score-result-section-head strong {
  color: var(--tianshu-text-primary);
  font-size: 14px;
}

.rule-score-result-section-head small {
  color: var(--tianshu-text-tertiary);
  font-size: 12px;
}

.rule-score-result-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.rule-score-result-table th {
  background: var(--tianshu-bg-muted);
  color: var(--tianshu-text-secondary);
  font-weight: 600;
  text-align: left;
}

.rule-score-result-table th,
.rule-score-result-table td {
  padding: 8px 10px;
  border: 1px solid var(--tianshu-border-subtle);
  vertical-align: top;
}

.rule-score-result-table td {
  color: var(--tianshu-text-primary);
}

.rule-score-result-table td strong {
  display: inline-block;
  margin-right: 6px;
}

.rule-score-result-marker {
  color: var(--tianshu-warning-text);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.rule-score-result-row.is-added td,
.rule-score-result-row td.is-added {
  background: var(--tianshu-success-bg);
}

.rule-score-result-row.is-removed td,
.rule-score-result-row td.is-removed {
  background: var(--tianshu-danger-bg);
}

.rule-score-result-row.is-modified td,
.rule-score-result-row td.is-modified {
  background: var(--tianshu-warning-bg);
}

.rule-score-result-row td.is-cell-change {
  font-weight: 600;
}

.rule-score-result-row td.is-empty {
  color: var(--tianshu-text-tertiary);
}

.rule-score-result-empty {
  padding: 16px;
  border: 1px dashed var(--tianshu-border);
  color: var(--tianshu-text-tertiary);
  text-align: center;
  font-size: 13px;
}
</style>
