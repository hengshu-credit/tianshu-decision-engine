# Java HTTP 接入包交付说明

本交付包包含天枢 Java HTTP SDK、公共 DTO、示例服务与运行依赖。项目代码按 Apache-2.0 提供，见 LICENSE。

不包含 rule-engine-core、rule-engine-server、JPMML、QLExpress、Redis 或 Kafka 运行库，也不包含引擎规则、模型、数据库快照或任何真实凭据。打包脚本检查依赖名称和类文件路径；dependencies.txt 记录实际版本，SHA256SUMS 用于完整性校验。

第三方 JAR 保持原样，内部 LICENSE / NOTICE 不删除；可提取的声明另存于 licenses/。依赖主要包括 Spring/Spring Boot、Tomcat、Jackson、Micrometer、OkHttp、Okio、Kotlin、Fastjson、MyBatis、MyBatis-Plus 注解、SLF4J、Logback 和注解 API。最终发布应结合 dependencies.txt 和各依赖原始许可完成复核。本文件不对第三方组件重新授权。

本包的拆分不改变引擎服务端的许可证义务；引擎包含 JPMML 时仍按引擎仓库 THIRD_PARTY_LICENSES.md 执行。

模式为 plain：没有机器绑定、到期许可证或防反编译保证。项目凭据、权限、有效期等由引擎服务端鉴权配置控制，不等同于软件包许可证。只有执行接口访问权限的部署应在 API 网关限制到 /api/rule/auth/token 和 /api/rule/sync/execute/*，不应开放规则同步和管理接口。
