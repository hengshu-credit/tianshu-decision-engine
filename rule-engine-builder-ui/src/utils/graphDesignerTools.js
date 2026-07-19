function text(value) {
  return value == null ? '' : String(value)
}

function elementLabel(element) {
  var properties = element && element.properties ? element.properties : {}
  return properties.nodeName || properties.conditionName || element.text || element.id || ''
}

function refPairs(value) {
  var pairs = []
  if (!value || typeof value !== 'object' || Array.isArray(value)) return pairs
  if (Object.prototype.hasOwnProperty.call(value, 'refId') || Object.prototype.hasOwnProperty.call(value, 'refType')) {
    pairs.push({ idKey: 'refId', typeKey: 'refType', code: value.code || value.value || '' })
  }
  if (Object.prototype.hasOwnProperty.call(value, '_varId') || Object.prototype.hasOwnProperty.call(value, '_refType')) {
    pairs.push({ idKey: '_varId', typeKey: '_refType', code: value.varCode || value.code || '' })
  }
  Object.keys(value).forEach(function (key) {
    if (!/VarId$/.test(key)) return
    var typeKey = key.replace(/VarId$/, 'RefType')
    var codeKey = key.replace(/VarId$/, 'Var')
    if (Object.prototype.hasOwnProperty.call(value, key) || Object.prototype.hasOwnProperty.call(value, typeKey)) {
      pairs.push({ idKey: key, typeKey: typeKey, code: value[codeKey] || value.varCode || '' })
    }
  })
  return pairs
}

function walk(value, visit) {
  if (!value || typeof value !== 'object') return
  if (Array.isArray(value)) {
    value.forEach(function (item) { walk(item, visit) })
    return
  }
  visit(value)
  Object.keys(value).forEach(function (key) { walk(value[key], visit) })
}

function collectReferences(element, baseType) {
  var references = []
  var seen = {}
  walk(element && element.properties ? element.properties : element, function (value) {
    refPairs(value).forEach(function (pair) {
      var refId = value[pair.idKey]
      var refType = value[pair.typeKey]
      if (refId == null || refId === '' || !refType) return
      var key = text(refType) + ':' + text(refId) + ':' + text(pair.code)
      if (seen[key]) return
      seen[key] = true
      references.push({
        kind: 'REFERENCE',
        elementId: element.id,
        baseType: baseType,
        refId: refId,
        refType: refType,
        code: pair.code,
        label: '引用：' + (pair.code || refType + '#' + refId)
      })
    })
  })
  return references
}

export function buildGraphNavigationItems(graph, keyword) {
  var normalized = text(keyword).trim().toLowerCase()
  var nodes = graph && Array.isArray(graph.nodes) ? graph.nodes : []
  var edges = graph && Array.isArray(graph.edges) ? graph.edges : []
  var items = []
  nodes.forEach(function (node) {
    var label = elementLabel(node)
    items.push({ kind: 'NODE', elementId: node.id, baseType: 'node', label: label, searchText: [label, node.id, node.type].join(' ') })
  })
  edges.forEach(function (edge) {
    var label = elementLabel(edge)
    items.push({ kind: 'EDGE', elementId: edge.id, baseType: 'edge', label: label || edge.id, searchText: [label, edge.id, edge.sourceNodeId, edge.targetNodeId].join(' ') })
  })
  nodes.forEach(function (node) { items.push.apply(items, collectReferences(node, 'node')) })
  edges.forEach(function (edge) { items.push.apply(items, collectReferences(edge, 'edge')) })
  return items.filter(function (item) {
    if (!normalized) return true
    return [item.label, item.searchText, item.code, item.refType, item.refId].map(text).join(' ').toLowerCase().indexOf(normalized) >= 0
  })
}

function referenceIssues(element, baseType) {
  var issues = []
  var seen = {}
  walk(element && element.properties ? element.properties : element, function (value) {
    refPairs(value).forEach(function (pair) {
      var hasId = value[pair.idKey] != null && value[pair.idKey] !== ''
      var hasType = Boolean(value[pair.typeKey])
      if (hasId === hasType) return
      var key = pair.idKey + ':' + pair.typeKey
      if (seen[key]) return
      seen[key] = true
      issues.push({ elementId: element.id, baseType: baseType, message: '字段引用必须同时包含 ID + ref_type' })
    })
  })
  return issues
}

export function collectGraphConfigurationIssues(graph, modelType) {
  var nodes = graph && Array.isArray(graph.nodes) ? graph.nodes : []
  var edges = graph && Array.isArray(graph.edges) ? graph.edges : []
  var issues = []
  nodes.forEach(function (node) {
    var name = elementLabel(node)
    if (node.type === 'exclusive-gateway') {
      var outCount = edges.filter(function (edge) { return edge.sourceNodeId === node.id }).length
      if (outCount < 2) issues.push({ elementId: node.id, baseType: 'node', message: '条件节点「' + name + '」至少需要两个出口' })
    }
    if (node.type === 'script-task') {
      var actions = node.properties && node.properties.actionData
      if (!Array.isArray(actions) || actions.length === 0) issues.push({ elementId: node.id, baseType: 'node', message: '动作节点「' + name + '」未配置动作' })
    }
    if (modelType === 'TREE' && node.type === 'join-gateway') {
      issues.push({ elementId: node.id, baseType: 'node', message: '决策树不能配置聚合节点' })
    }
    issues.push.apply(issues, referenceIssues(node, 'node'))
  })
  edges.forEach(function (edge) {
    if (!nodes.some(function (node) { return node.id === edge.sourceNodeId }) || !nodes.some(function (node) { return node.id === edge.targetNodeId })) {
      issues.push({ elementId: edge.id, baseType: 'edge', message: '连线存在无效的起点或终点' })
    }
    issues.push.apply(issues, referenceIssues(edge, 'edge'))
  })
  return issues
}
