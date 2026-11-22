<template>
  <div class="faker-arguments">
    <div v-if="lengthMode || rangeMode" class="argument-fields">
      <el-form-item v-if="lengthMode" label="字符串长度">
        <el-input type="number" min="0" max="4096" :model-value="lengthValue" :disabled="!canEditFields"
          placeholder="使用默认长度" @update:model-value="setLength" />
      </el-form-item>
      <template v-else>
        <el-form-item v-for="(label, key) in bounds" :key="key" :label="label">
          <el-input :model-value="rangeValue(key)" :disabled="!canEditFields" placeholder="使用默认值"
            @update:model-value="setBound(key, $event)" />
        </el-form-item>
      </template>
    </div>
    <el-form-item label="方法参数">
      <el-input type="textarea" :rows="3" :model-value="modelValue" :placeholder="example"
        aria-label="Faker 方法参数 JSON" @update:model-value="$emit('update:modelValue', $event)" />
      <el-button link type="primary" @click="$emit('update:modelValue', JSON.stringify(method.exampleArgs, null, 2))">填入示例参数</el-button>
      <div class="argument-help">
        <code>{{ method.key }}({{ method.parameters.join(', ') }})</code>。按顺序填写 JSON 数组，留空使用内置示例。
        <a :href="'https://fakerjs.dev/api/' + method.module + '#' + method.name.toLowerCase()" target="_blank" rel="noopener noreferrer">参数说明</a>
      </div>
      <div v-if="callbackHint" class="argument-help">{{ callbackHint }}</div>
      <div v-if="error" role="alert" class="argument-error">{{ error }}</div>
    </el-form-item>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { parseExactJson, stringifyExactJson } from '@/utils/exactJson'

const props = defineProps({ method: { type: Object, required: true }, modelValue: { type: String, required: true } })
const emit = defineEmits(['update:modelValue'])
const bounds = { min: '最小值', max: '最大值' }
const lengthMode = computed(() => {
  if (['string.alpha', 'string.alphanumeric', 'string.binary', 'string.hexadecimal', 'string.numeric', 'string.octal', 'internet.password'].includes(props.method.key)) return 'options'
  if (['string.nanoid', 'string.sample', 'string.symbol'].includes(props.method.key)) return 'position'
  return ''
})
const rangeMode = computed(() => ['number.int', 'number.float', 'number.bigInt'].includes(props.method.key))
const example = computed(() => JSON.stringify(props.method.exampleArgs))
const callbackHint = computed(() => ['helpers.multiple', 'helpers.maybe', 'helpers.uniqueArray', 'helpers.mustache'].includes(props.method.key)
  ? '回调可填写 {"$faker":"string.alphanumeric","args":[8]}。' : '')
const parsed = computed(() => {
  try {
    const value = JSON.parse(props.modelValue || '[]')
    return Array.isArray(value) ? value : null
  } catch { return null }
})
const error = computed(() => !parsed.value ? '请输入合法的 JSON 参数数组' : parsed.value.length > props.method.parameters.length ? '参数数量超过方法签名' : '')
const first = computed(() => parsed.value?.[0])
const canEditFields = computed(() => !error.value)
const lengthValue = computed(() => {
  const value = typeof first.value === 'object' ? first.value?.length : first.value
  return typeof value === 'number' ? value : ''
})
function rangeValue(key) {
  const opt = first.value
  return typeof opt === 'object' ? opt?.[key] ?? '' : key === 'max' ? opt ?? '' : ''
}
function setLength(text) {
  const args = parseExactJson(props.modelValue || '[]')
  if (lengthMode.value === 'position') return emit('update:modelValue', text === '' ? '' : JSON.stringify([Number(text)]))
  const options = first.value && typeof first.value === 'object' ? args[0] : {}
  if (text === '') delete options.length
  else options.length = Number(text)
  emit('update:modelValue', Object.keys(options).length ? stringifyExactJson([options]) : '')
}
function setBound(key, text) {
  const args = parseExactJson(props.modelValue || '[]')
  const options = first.value && typeof first.value === 'object' ? args[0] : args.length ? { max: args[0] } : {}
  if (text === '') delete options[key]
  else if (props.method.key === 'number.bigInt') options[key] = text
  else {
    try { options[key] = parseExactJson(text) }
    catch { options[key] = text }
  }
  emit('update:modelValue', Object.keys(options).length ? stringifyExactJson([options]) : '')
}
</script>

<style scoped>
.faker-arguments { min-width: 0; }
.argument-fields { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 160px), 1fr)); gap: 0 12px; }
.argument-help, .argument-error { width: 100%; margin-top: 4px; font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }
.argument-help { color: var(--tianshu-text-secondary); }
.argument-error { color: var(--tianshu-danger-text); }
</style>
