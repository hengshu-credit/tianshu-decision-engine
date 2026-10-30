# 凭据存储与脚本隔离

控制台登录密码使用 BCrypt，不提供解密。仅用于校验的账户令牌使用摘要；需要实际调用外部服务的凭据使用 AES-GCM 加密。主密钥来自部署环境的 `RULE_AUTH_MASTER_KEY`，不写入业务数据库。

项目鉴权凭据、临时访问 token、治理草稿和历史版本的敏感载荷沿用现有密文存储。数据库/SSH 的密码、私钥与口令，SFTP 的密码、私钥、口令与 knownHosts，以及外数默认鉴权、接口鉴权、请求头、回调配置，现在在 MyBatis 字段映射层加密写入、解密读取。JSON 列保存密文对象，不再保存上述配置的原文。业务服务拿到解密后的值；已有页面脱敏、留空保留、填写替换和显式清除流程保持不变。

首次升级应停止旧版写入进程，再启动新版。启动流程在 schema/可选初始化之后、Mapper 与业务缓存加载之前，每批最多 200 行迁移历史明文，包含已停用或软删除的数据源。密码列扩为 LONGTEXT，避免加密膨胀导致截断。重复启动校验现有密文但不重复加密；未知密钥、损坏密文或迁移错误会阻止启动，不能回退到明文。必须保留原主密钥；旧备份中的历史明文不会被此次运行自动清理。手工导入旧样例或旧数据库后，需要重新执行新版启动迁移，不能在服务运行期间向敏感列直接导入明文。

新增敏感字段必须同步添加字段 TypeHandler、启动迁移及测试。直接 JDBC/SQL 和 UpdateWrapper 的字段赋值不自动经过实体 TypeHandler；不得用这些路径写入明文凭据。业务版本影响分析中的直接 SQL 已显式解密鉴权配置，保留原有 ID 依赖检查。

QL 规则、SCRIPT 函数和外数请求/响应脚本共用受限成员白名单；文件、进程、反射、类加载器、系统属性和环境变量不在允许范围。安全策略依据 [QLExpress 官方安全策略说明](https://github.com/alibaba/QLExpress/blob/main/README-source.adoc)。

Java/Spring Bean 函数属于宿主原生代码，QLExpress 的成员白名单不能约束其方法内部实现。现在除了精确匹配的平台内置方法，只有部署代码中带 `@ScriptCallable` 的公开方法可以绑定。管理端的方法目录、运行时绑定，以及 SDK 的远程/冻结函数加载均检查此授权；先检查方法，再创建 Java 实例或获取 Bean。账户的 `function:native` 权限不能替代此部署授权。

```java
import com.hengshucredit.rule.core.function.ScriptCallable;

public class BusinessFunctions {
    @ScriptCallable
    public double total(double amount, double fee) {
        return amount + fee;
    }
}
```

升级前需要审核自定义原生方法并在服务端和 SDK 部署相应实现。未声明的方法会被拒绝，已有依赖它的制品不会悄悄降级执行。注解是部署开发者对实现的授权，不是 JVM/操作系统级沙箱；不得把读取任意文件、执行命令、返回容器/密钥等能力标为可调用。凭据应填写在专用鉴权字段，不能硬编码进普通脚本、描述、SQL 或业务测试数据。

验证覆盖数据库原始列、实体保存/读取/局部更新/清除、密文随机性、错误密钥与篡改、真实 MySQL JSON 和多批次迁移、服务端与 SDK 原生绑定拒绝，以及 QL/外数脚本的宿主访问拦截。
