export default {
  methods: {
    isCredentialCleared(key) { return (this.form.clearCredentials || []).includes(key) },
    hasStoredCredential(key) {
      return !this.isCredentialCleared(key) &&
        (this.form._secretConfigured?.[`/${key}`] ?? this.form.configuredSecrets?.[key]) === true
    },
    setCredentialCleared(key, cleared) {
      const fields = new Set(this.form.clearCredentials || [])
      if (cleared) { fields.add(key); this.form[key] = '' } else { fields.delete(key) }
      this.form.clearCredentials = [...fields]
    },
    applySavedCredentialState(submitted) {
      const flags = { ...(this.form._secretConfigured || {}) }
      for (const key of ['password', 'sshPassword', 'sshPrivateKey', 'sshPassphrase', 'privateKey', 'passphrase', 'knownHosts']) {
        if ((submitted.clearCredentials || []).includes(key)) flags[`/${key}`] = false
        else if (typeof submitted[key] === 'string' && submitted[key].trim()) flags[`/${key}`] = true
      }
      this.form._secretConfigured = flags
    },
    credentialHint(key, fallback = '尚未配置，可按连接方式填写') {
      if (this.isCredentialCleared(key)) return '已选择清除；试连按清除后的配置执行，发布审批后生效'
      return this.hasStoredCredential(key) ? '已配置，留空保留；输入新值替换' : fallback
    },
    credentialPlaceholder(key, fallback) { return this.credentialHint(key, fallback) },
  },
}
