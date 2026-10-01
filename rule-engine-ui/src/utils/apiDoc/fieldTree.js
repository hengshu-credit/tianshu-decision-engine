import { escapeAttribute, escapeHtml } from './escape'

function displayExample(value) {
  if (value === undefined) return ''
  if (typeof value === 'string') return value
  return JSON.stringify(value)
}

export function buildFieldTreeRows(fields) {
  const roots = []
  const nodes = new Map()

  ;(fields || []).forEach(field => {
    const parts = String(field.path || '').split('.').filter(Boolean)
    let parent = null
    parts.forEach((name, depth) => {
      const path = parts.slice(0, depth + 1).join('.')
      let node = nodes.get(path)
      if (!node) {
        node = {
          id: path,
          name,
          path,
          depth,
          parentId: parent ? parent.id : '',
          type: 'OBJECT',
          required: false,
          label: '',
          exampleValue: undefined,
          children: []
        }
        nodes.set(path, node)
        if (parent) parent.children.push(node)
        else roots.push(node)
      }
      if (depth === parts.length - 1) {
        node.type = field.type || node.type
        node.required = Boolean(field.required)
        node.label = field.label || field.description || ''
        node.exampleValue = field.exampleValue
      }
      parent = node
    })
  })

  const rows = []
  function append(node) {
    rows.push({
      id: node.id,
      name: node.name,
      path: node.path,
      depth: node.depth,
      parentId: node.parentId,
      type: node.type,
      required: node.required,
      label: node.label,
      exampleValue: node.exampleValue,
      hasChildren: node.children.length > 0
    })
    node.children.forEach(append)
  }
  roots.forEach(append)
  return rows
}

export function renderFieldTreeRows(fields) {
  return buildFieldTreeRows(fields).map(row => {
    const toggle = row.hasChildren
      ? `<button class="field-toggle" type="button" data-field-toggle="${escapeAttribute(row.id)}" aria-expanded="true" aria-label="折叠 ${escapeAttribute(row.name)}"><span aria-hidden="true">⌄</span></button>`
      : '<span class="field-toggle-placeholder" aria-hidden="true"></span>'
    return `<tr data-field-row="${escapeAttribute(row.id)}" data-parent-id="${escapeAttribute(row.parentId)}" style="--field-depth:${row.depth}">
      <td><div class="field-name">${toggle}<code>${escapeHtml(row.name)}</code></div></td>
      <td>${escapeHtml(row.type || 'OBJECT')}</td>
      <td>${row.required ? '是' : '否'}</td>
      <td>${escapeHtml(row.label || '—')}</td>
      <td><code>${escapeHtml(displayExample(row.exampleValue))}</code></td>
    </tr>`
  }).join('')
}
