<template>
  <el-button v-if="canAccounts || canPolicy" size="small" @click="open">引擎 SFTP 服务配置</el-button>
  <el-dialog v-model="visible" title="引擎 SFTP 服务配置" width="880px" append-to-body destroy-on-close
    :before-close="close" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
    <el-tabs v-model="tab" :before-leave="leaveTab" @tab-change="load">
      <el-tab-pane v-if="canAccounts" label="上传账户" name="accounts" />
      <el-tab-pane v-if="canPolicy" label="限流与熔断" name="policy" />
      <el-tab-pane v-if="canPolicy" label="封禁记录" name="bans" />
    </el-tabs>
    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon>
      <el-button link @click="load">重新加载</el-button>
    </el-alert>
    <section v-if="tab === 'accounts'" :aria-busy="loading">
      <div class="uiue-search-container uiue-filter-toolbar">
        <el-form inline size="small" @submit.prevent="searchAccounts">
          <el-form-item label="账户"><el-input v-model="search" clearable placeholder="登录名或显示名称" /></el-form-item>
          <el-form-item><el-button native-type="submit" type="primary">查询</el-button></el-form-item>
        </el-form>
        <el-button size="small" @click="load">刷新</el-button>
      </div>
      <el-table :data="accounts" border size="small">
        <el-table-column label="控制台账户" min-width="120" prop="username" />
        <el-table-column label="显示名称" min-width="110" prop="displayName" />
        <el-table-column label="SFTP 登录名" min-width="100"><template #default="{ row }">{{ row.mode ? 'u' + row.id : '—' }}</template></el-table-column>
        <el-table-column label="文件权限" min-width="100"><template #default="{ row }">{{ modes[row.mode] || '未开通' }}{{ row.active ? '' : '（账户未启用）' }}</template></el-table-column>
        <el-table-column label="操作" width="90" fixed="right"><template #default="{ row }">
          <el-button v-if="mayEdit(row)" link type="primary" @click="edit(row)">{{ row.mode ? '管理' : '开通' }}</el-button>
        </template></el-table-column>
      </el-table>
    </section>
    <el-form v-if="tab === 'policy' && policy" label-position="top" size="small" :disabled="saving || loading" class="policy-grid">
      <el-form-item v-for="field in fields" :key="field.key" :label="field.label">
        <el-input-number v-model="policy[field.key]" :min="1" :max="field.max" :precision="0" controls-position="right" />
      </el-form-item>
      <p class="help">策略适用于随包部署的独立 SFTP 服务。认证请求按来源 IP 跨连接计数。失败窗口内 IP 累计失败、账户连续失败任一达到阈值即封禁；完整登录成功会清零该账户的失败次数。文件读写按上传账户汇总，达到上限后等待下一秒继续。</p>
    </el-form>
    <section v-if="tab === 'bans'" :aria-busy="loading">
      <div class="ban-toolbar"><span>到期后自动解封。账户和来源 IP 分别计数。</span><el-button size="small" @click="load">刷新</el-button></div>
      <el-table :data="bans" border size="small" empty-text="当前没有生效的封禁">
        <el-table-column label="封禁对象" min-width="160"><template #default="{ row }">{{ subjectName(row.subject) }}</template></el-table-column>
        <el-table-column label="原因" min-width="110"><template #default="{ row }">{{ row.reason === 'RATE' ? '登录频率超限' : '认证失败达到阈值' }}</template></el-table-column>
        <el-table-column prop="failures" label="失败次数" width="85" />
        <el-table-column label="自动解封时间" min-width="158"><template #default="{ row }">{{ new Date(row.blockedUntil).toLocaleString() }}</template></el-table-column>
        <el-table-column label="操作" width="75" fixed="right"><template #default="{ row }">
          <el-button link type="primary" :disabled="saving" @click="release(row)">解封</el-button>
        </template></el-table-column>
      </el-table>
    </section>
    <el-pagination v-if="tab !== 'policy' && total > 20" :current-page="page" :page-size="20" :total="total"
      layout="prev,pager,next,total" @current-change="changePage" />
    <template #footer>
      <el-button :disabled="saving" @click="close()">关闭</el-button>
      <el-button v-if="tab === 'policy'" :disabled="saving || loading" @click="reloadPolicy">重新加载策略</el-button>
      <el-button v-if="tab === 'policy'" type="primary" :disabled="!dirty || loading || !!error" :loading="saving" @click="savePolicy">保存策略</el-button>
    </template>
  </el-dialog>
  <el-dialog v-model="editing" :title="selected?.mode ? '管理 SFTP 上传账户' : '开通 SFTP 上传账户'" width="520px" append-to-body
    :close-on-click-modal="false" :show-close="!saving" :close-on-press-escape="!saving" :before-close="closeEditor">
    <el-form label-position="top" size="small" :disabled="saving">
      <el-form-item label="控制台账户">{{ selected?.username }}</el-form-item>
      <el-form-item label="账户权限"><el-select v-model="mode">
        <el-option v-for="(label, value) in modes" :key="value" :label="label" :value="value" />
        <el-option v-if="selected?.mode" label="关闭并撤销公钥" value="REVOKE" />
      </el-select></el-form-item>
      <el-form-item v-if="mode !== 'REVOKE'" label="上传方 SSH 公钥">
        <el-input v-model="publicKey" type="textarea" :rows="3" autocomplete="off" :placeholder="selected?.mode ? '留空保留现有公钥，填写新公钥可换钥' : '填写单行 OpenSSH 公钥'" />
      </el-form-item>
      <p class="help">个人登录名为 u{{ selected?.id }}，项目登录名加上 -p项目ID。每个账户只能读写自己的目录，只读账户只能下载。</p>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="closeEditor()">取消</el-button><el-button type="primary" :loading="saving" :disabled="!!selected?.mode && mode === selected.mode && !publicKey.trim()" @click="saveAccount">保存</el-button></template>
  </el-dialog>
</template>

<script setup>
import { computed, onDeactivated, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCurrentUser, hasPermission } from '@/security/permissionState'
import { listEngineSftpAccounts, saveEngineSftpAccount, getEngineSftpPolicy, saveEngineSftpPolicy, listEngineSftpBans, unblockEngineSftp } from '@/api/sftp'

const canAccounts = computed(() => !!getCurrentUser()?.userId && hasPermission('account:manage'))
const canPolicy = computed(() => hasPermission('system:edit'))
const visible = ref(false), editing = ref(false), loading = ref(false), saving = ref(false)
const tab = ref('accounts'), search = ref(''), page = ref(1), total = ref(0), error = ref('')
const accounts = ref([]), bans = ref([]), policy = ref(null), baseline = ref('')
const selected = ref(null), mode = ref('READ_WRITE'), publicKey = ref('')
const modes = { READ_WRITE: '读写', READ_ONLY: '只读', SUSPENDED: '暂停登录' }
const fields = [
  { key: 'ipAttemptsPerMinute', label: '每个 IP 的认证请求（次/分钟）', max: 60000 },
  { key: 'fileRequestsPerSecond', label: '每个上传账户的文件读写请求（次/秒）', max: 100000 },
  { key: 'failureThreshold', label: '封禁前允许尝试的失败次数', max: 1000 },
  { key: 'failureWindowSeconds', label: '认证失败统计窗口（秒）', max: 86400 },
  { key: 'banSeconds', label: '认证失败封禁时长（秒）', max: 604800 },
]
const dirty = computed(() => policy.value && JSON.stringify(policy.value) !== baseline.value)
let generation = 0

function mayEdit(row) {
  return hasPermission('account:password-reset') && (String(row.id) !== String(getCurrentUser()?.userId) || hasPermission('account:password-change'))
}
function subjectName(value) {
  if (value === 'SERVICE') return '引擎服务账户'
  return value.startsWith('USER:') ? '上传账户 u' + value.slice(5) : value
}
function open() {
  tab.value = canAccounts.value ? 'accounts' : 'policy'
  page.value = 1
  search.value = ''
  accounts.value = []
  bans.value = []
  total.value = 0
  visible.value = true
  load()
}
async function confirm(text, title) {
  return ElMessageBox.confirm(text, title, { confirmButtonText: '确定', cancelButtonText: '取消' }).then(() => true, () => false)
}
async function close(done) {
  if (saving.value || (dirty.value && !await confirm('放弃未保存的策略修改？', '关闭服务配置'))) return
  generation++
  policy.value = null
  visible.value = false
  if (typeof done === 'function') done()
}
async function leaveTab() {
  if (saving.value || (dirty.value && !await confirm('放弃未保存的策略修改？', '切换配置'))) return false
  page.value = 1
  policy.value = null
  return true
}
async function load() {
  const id = ++generation
  const name = tab.value
  error.value = ''
  loading.value = true
  try {
    const params = { page: page.value, size: 20 }
    const response = name === 'policy' ? await getEngineSftpPolicy() : name === 'bans'
      ? await listEngineSftpBans(params) : await listEngineSftpAccounts({ ...params, search: search.value })
    if (id !== generation) return
    if (name === 'policy') {
      policy.value = { ...response.data }
      baseline.value = JSON.stringify(policy.value)
    } else {
      const result = response.data
      if (name === 'bans') bans.value = result.records
      else accounts.value = result.records
      total.value = result.total
      const last = Math.max(1, Math.ceil(result.total / 20))
      if (!result.records.length && page.value > last) { page.value = last; await load() }
    }
  } catch (e) {
    if (id === generation) { error.value = e.message || '加载失败'; accounts.value = []; bans.value = []; policy.value = null; total.value = 0 }
  } finally { if (id === generation) loading.value = false }
}
function searchAccounts() { page.value = 1; load() }
function changePage(value) { page.value = value; load() }
function edit(row) {
  selected.value = row
  mode.value = row.mode || 'READ_WRITE'
  publicKey.value = ''
  editing.value = true
}
async function closeEditor(done) {
  const changed = publicKey.value.trim() || mode.value !== (selected.value?.mode || 'READ_WRITE')
  if (saving.value || (changed && !await confirm('放弃未保存的账户修改？', '关闭账户设置'))) return
  editing.value = false
  publicKey.value = ''
  if (typeof done === 'function') done()
}
async function saveAccount() {
  if (saving.value) return
  const row = selected.value
  const revoke = mode.value === 'REVOKE'
  if (!row.mode && !publicKey.value.trim()) return ElMessage.warning('首次开通请填写公钥')
  if (revoke && !await confirm('撤销该账户的 SFTP 公钥并关闭登录？', '撤销上传权限')) return
  saving.value = true
  try {
    await saveEngineSftpAccount(row.id, { mode: revoke ? null : mode.value, publicKey: revoke ? null : publicKey.value.trim(), revoke, revision: row.revision })
    editing.value = false
    publicKey.value = ''
    ElMessage.success('SFTP 账户权限已保存')
    await load()
  } catch (e) { if (!e.requestErrorNotified) ElMessage.error(e.message) }
  finally { saving.value = false }
}
async function reloadPolicy() {
  if (!dirty.value || await confirm('放弃未保存的策略修改并读取最新配置？', '重新加载策略')) await load()
}
async function savePolicy() {
  if (saving.value) return
  if (fields.some(f => !Number.isInteger(policy.value[f.key]) || policy.value[f.key] < 1 || policy.value[f.key] > f.max)) return ElMessage.warning('请填写范围内的整数')
  saving.value = true
  try {
    const response = await saveEngineSftpPolicy(policy.value)
    policy.value = response.data
    baseline.value = JSON.stringify(policy.value)
    ElMessage.success('SFTP 防护策略已保存')
  } catch (e) { if (!e.requestErrorNotified) ElMessage.error(e.message) }
  finally { saving.value = false }
}
async function release(row) {
  if (saving.value || !await confirm('解除 ' + subjectName(row.subject) + ' 的本次封禁并重置对应计数？', '解除封禁')) return
  saving.value = true
  try { await unblockEngineSftp({ subject: row.subject, blockedUntil: row.blockedUntil }); ElMessage.success('已解封'); await load() }
  catch (e) { if (!e.requestErrorNotified) ElMessage.error(e.message) }
  finally { saving.value = false }
}
onDeactivated(() => { generation++; visible.value = false; editing.value = false; publicKey.value = ''; policy.value = null })
</script>

<style scoped>
.policy-grid { display: grid; grid-template-columns: repeat(auto-fit,minmax(min(100%,250px),1fr)); gap: 4px 20px; }
.policy-grid .help { grid-column: 1 / -1; }
.help,.ban-toolbar { color: var(--el-text-color-secondary); font-size: 13px; }
.ban-toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
</style>
