<template>
  <section class="api-auth-editor">
    <p v-if="['NONE', 'INHERIT', 'API'].includes(type)" class="field-help">{{ type === 'NONE' ? '不附加鉴权信息。' : '使用所选来源的鉴权配置。' }}</p>
    <template v-else>
      <el-form-item label="缺失值处理"><el-select :model-value="value.nullPolicy" @update:model-value="set('nullPolicy', $event)"><el-option label="缺失时报错" value="FAIL" /><el-option label="省略缺失鉴权" value="OMIT" /><el-option label="发送空字符串" value="EMPTY" /></el-select></el-form-item>
      <template v-if="type === 'BASIC'"><el-form-item label="用户名"><api-value-editor :value="value.username" :vars="vars" :functions="functions" :modules="modules" @update:value="set('username', $event)" /></el-form-item><el-form-item label="密码"><api-value-editor :value="value.password" sensitive :vars="vars" :functions="functions" :modules="modules" @update:value="set('password', $event)" /></el-form-item></template>
      <el-form-item v-else-if="type === 'BEARER'" label="Bearer Token"><api-value-editor :value="value.token" sensitive :vars="vars" :functions="functions" :modules="modules" @update:value="set('token', $event)" /></el-form-item>
      <template v-else-if="type === 'API_KEY'">
        <el-form-item label="放置位置"><el-select :model-value="value.location" @update:model-value="set('location', $event)"><el-option label="请求头" value="HEADER" /><el-option label="Query 参数" value="QUERY" /></el-select></el-form-item>
        <el-form-item label="Key 名称"><el-input :model-value="value.name" @update:model-value="set('name', $event)" /></el-form-item>
        <el-form-item label="Key 取值"><api-value-editor :value="value.value" sensitive :vars="vars" :functions="functions" :modules="modules" @update:value="set('value', $event)" /></el-form-item>
      </template>
      <template v-else-if="['TOKEN_API', 'OAUTH2'].includes(type)">
        <el-form-item label="Token 地址"><el-input :model-value="value.tokenUrl" placeholder="/oauth/token 或完整 URL" @update:model-value="set('tokenUrl', $event)" /></el-form-item>
        <el-form-item label="方法"><el-select :model-value="value.method" @update:model-value="set('method', $event)"><el-option v-for="method in ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']" :key="method" :value="method" :label="method" /></el-select></el-form-item>
        <el-form-item label="Content-Type"><el-select :model-value="value.contentType" filterable allow-create @update:model-value="set('contentType', $event)"><el-option v-for="format in ['application/json', 'application/x-www-form-urlencoded', 'multipart/form-data', 'text/plain', 'application/xml']" :key="format" :value="format" :label="format" /></el-select></el-form-item>
        <el-form-item label="鉴权请求头"><api-object-value-editor ref="headers" :value="value.headers" :vars="vars" :functions="functions" :modules="modules" label="鉴权请求头" @update:value="set('headers', $event)" /></el-form-item>
        <el-form-item label="鉴权请求体">
          <api-value-editor v-if="rawBody || typeof value.body?.kind === 'string'" :value="typeof value.body?.kind === 'string' ? value.body : null" :vars="vars" :functions="functions" :modules="modules" :expected-type="rawBody ? 'STRING' : ''" @update:value="set('body', $event)" />
          <api-object-value-editor v-else ref="body" :value="value.body" :vars="vars" :functions="functions" :modules="modules" label="鉴权请求体" @update:value="set('body', $event)" />
          <p v-if="bodyError" role="alert">{{ bodyError }}</p>
        </el-form-item>
        <el-collapse><el-collapse-item title="响应样例与路径目录"><p class="field-help">粘贴鉴权响应 JSON 后提取字段结构，不发起请求。</p><monaco-editor v-model:value="sampleText" language="json" height="160px" /><el-button @click="applySample">导入鉴权响应样例</el-button><p v-if="sampleError" role="alert">{{ sampleError }}</p></el-collapse-item></el-collapse>
        <el-form-item label="Token 路径"><protocol-path-picker :value="value.tokenPath" :paths="responsePaths" context-label="鉴权响应" @update:value="set('tokenPath', $event)" /></el-form-item>
        <el-form-item label="过期秒路径"><protocol-path-picker :value="value.expiresInPath" :paths="responsePaths" context-label="鉴权响应" @update:value="set('expiresInPath', $event)" /></el-form-item>
        <el-form-item label="Token 放置方式"><el-select :model-value="value.tokenPlacement" @update:model-value="set('tokenPlacement', $event)"><el-option label="写入请求 Header" value="HEADER" /><el-option label="仅供请求脚本使用" value="SCRIPT_ONLY" /></el-select></el-form-item>
        <el-form-item label="Token Header"><el-input :model-value="value.tokenHeaderName" @update:model-value="set('tokenHeaderName', $event)" /></el-form-item>
        <el-form-item label="Token 前缀"><el-input :model-value="value.tokenPrefix" placeholder="如 Bearer 后跟空格，原样保留" @update:model-value="set('tokenPrefix', $event)" /></el-form-item>
        <el-collapse><el-collapse-item title="Token 响应处理脚本"><monaco-editor :value="value.tokenResponseScript" language="javascript" height="160px" @update:value="set('tokenResponseScript', $event)" /></el-collapse-item></el-collapse>
      </template>
    </template>
  </section>
</template>
<script>
import ApiValueEditor from './ApiValueEditor.vue'
import ApiObjectValueEditor from './ApiObjectValueEditor.vue'
import ProtocolPathPicker from './ProtocolPathPicker.vue'
import MonacoEditor from '@/components/MonacoEditor.vue'
import { samplePathOptions } from '@/utils/apiExecution'
export default {
  components: { ApiValueEditor, ApiObjectValueEditor, ProtocolPathPicker, MonacoEditor },
  props: { value: { type: Object, required: true }, type: { type: String, required: true }, vars: { type: Array, default: () => [] }, functions: { type: Array, default: () => [] }, modules: { type: Array, default: () => [] } },
  emits: ['update:value'],
  data() { return { sampleText: JSON.stringify(this.value.responseSample || {}, null, 2), sampleError: '', bodyError: '' } },
  computed: {
    rawBody() { return ['TOKEN_API', 'OAUTH2'].includes(this.type) && !/json|form/i.test(this.value.contentType || 'application/json') },
    responsePaths() { return [...(Object.hasOwn(this.value, 'responseSample') ? samplePathOptions(this.value.responseSample, 'body') : [{ value: 'body', label: '响应体', type: 'OBJECT' }]), { value: 'headers', label: '响应头', type: 'MAP' }, { value: 'httpStatus', label: 'HTTP 状态码', type: 'INTEGER' }] },
  },
  methods: {
    set(key, value) { this.$emit('update:value', { ...this.value, [key]: value }) },
    validate() {
      this.bodyError = this.rawBody && typeof this.value.body?.kind !== 'string' ? '请通过内容选择器配置完整的文本请求体' : ''
      return !this.bodyError && [this.$refs.headers, this.$refs.body].filter(Boolean).every(editor => editor.validate())
    },
    applySample() { try { this.set('responseSample', JSON.parse(this.sampleText)); this.sampleError = '' } catch { this.sampleError = '请输入合法 JSON 响应样例' } },
  },
}
</script>
<style scoped>
.api-auth-editor { width: 100%; min-width: 0; }
.api-auth-editor :deep(.api-object-value-editor) { width: 100%; }
.field-help { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
