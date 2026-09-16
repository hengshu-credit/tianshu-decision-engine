<template>
  <div class="variable-import-help">
    <p>导入会生成审批草稿，完成审批后才会在列表中生效。同范围、同编码的资源按更新处理，数据对象中未出现在本次输入的旧字段会保留。</p>
    <details>
      <summary>格式说明与示例</summary>
      <p>{{ help.description }}</p>
      <pre>{{ help.sample }}</pre>
      <el-button size="small" :disabled="disabled" @click="$emit('use-example', help.sample)">填入示例</el-button>
      <span v-if="disabled">已填写内容时仅展示示例，不覆盖当前输入。</span>
    </details>
  </div>
</template>

<script>
const IMPORT_HELP = {
  'java-entity': {
    description: '粘贴实体类源码或上传 UTF-8 的 .java 文件。解析带访问修饰符的实例字段，保留类名、字段名及注释。支持基本类型、数组、List 和 Map；自定义类型需先导入并审批其数据对象。不会编译或执行 Java，record、继承字段及复杂泛型需改为明确的字段声明。',
    sample: 'public class RiskRequest {\n  private String User_ID;\n  private int age;\n  private String[] tags;\n}',
  },
  'json-object': {
    description: '填写对象编码，并提供实际 JSON 对象样本。嵌套对象和对象数组保留层级；数组从首个非 null 元素推断结构，请使用字段完整的同类型样本。空数组不猜测元素类型，导入后可编辑；null 标量暂按字符串识别。',
    sample: '{\n  "User_ID": "U001",\n  "profile": { "age": 30 },\n  "apps": [{ "app_code": "A", "enabled": true }]\n}',
  },
  'ddl-table': {
    description: '支持 MySQL 风格 CREATE TABLE，可粘贴多张表、一行多列或跨行列定义。表名和列名原样保留，COMMENT 用作名称，索引不生成字段。这里只解析结构，不连接数据库、不执行 DDL。',
    sample: "CREATE TABLE Risk_Request (\n  User_ID varchar(64) COMMENT '用户编号',\n  amount decimal(12,2) COMMENT '申请金额',\n  PRIMARY KEY (User_ID)\n);",
  },
  'java-const': {
    description: '解析 static final 常量，支持字符串、布尔、十进制数值，以及字面量数组、List.of、Arrays.asList 和 Map.of。常量编码保持原样；不执行方法、变量计算或字符串拼接，请改用确定的字面量值。',
    sample: 'public class RiskLimits {\n  public static final int MAX_AGE = 65;\n  public static final String CHANNEL = "web";\n  public static final String[] CODES = {"A", "B"};\n}',
  },
  'json-const': {
    description: 'JSON 顶层每个键生成一个常量。支持字符串、布尔、数值、数组（LIST）和对象（MAP）；嵌套内容作为完整常量值保留，不拆成子常量。允许空字符串，顶层 null 值会被拒绝。',
    sample: '{\n  "MAX_AGE": 65,\n  "ENABLED": true,\n  "CODES": ["A", "B"],\n  "LIMITS": { "max": 1000 }\n}',
  },
}

export default {
  name: 'VariableImportHelp',
  props: {
    mode: { type: String, required: true },
    disabled: { type: Boolean, default: false },
  },
  emits: ['use-example'],
  computed: {
    help() { return IMPORT_HELP[this.mode] },
  },
}
</script>

<style scoped>
.variable-import-help { margin-bottom: 16px; padding: 12px; border-radius: 4px; background: var(--tianshu-bg-soft); font-size: 12px; line-height: 1.6; }
.variable-import-help p { margin: 0 0 8px; color: var(--tianshu-text-secondary); }
.variable-import-help summary { cursor: pointer; color: var(--el-color-primary); }
.variable-import-help pre { max-height: 180px; overflow: auto; padding: 12px; border: 1px solid var(--tianshu-border-subtle); border-radius: 4px; background: var(--tianshu-bg-surface); }
.variable-import-help span { margin-left: 8px; color: var(--tianshu-text-tertiary); }
</style>
