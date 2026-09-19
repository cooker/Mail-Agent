# Mail Agent 协作约定

本文件适用于整个仓库。目标是完成用户要求的可运行变更，并保留已有邮件数据与分享访问边界。使用中文沟通，界面文案保持中文。

## 按需定位

- 运行方式、配置和产品行为：查阅 `README.md`；实际依赖与配置以 `pom.xml`、`src/main/resources/application.yml` 为准。
- 收取与发送：查看 `MailPollingService`、`DeliveryService`、`ForwardComposer` 及相关测试。
- 分享与邮件展示：查看 `ShareService`、相关 Controller、`MailContentService` 和对应 Thymeleaf 模板。
- 数据结构变更：查看实体和 `src/main/resources/db/migration/`。
- 只读取当前任务需要的文件。仅在技能确实适用于任务时加载它，避免按宽泛关键词引入无关工作流。

## 项目约定

- Java 21、Spring Boot、Maven Wrapper、Thymeleaf、JPA、H2 和 Flyway；通常沿用现有结构即可，不为局部需求引入新的框架或服务。
- 业务匹配和访问范围放在服务层，Controller 与模板复用同一逻辑。
- 数据库变更新增递增版本的 Flyway 迁移，不修改已经发布的迁移；考虑现有记录的默认值和升级后的行为。
- 修改用户可见行为或配置时，同步更新对应说明。不要恢复已取消的 `MAIL_MASTER_KEY` 必填要求或管理员环境变量必填校验，除非用户明确要求。

## 需要保留的行为

- IMAP 只读处理 INBOX；来源唯一标识为账号、文件夹、UIDVALIDITY 和 UID。结果与任务提交成功后才推进游标。
- 多规则命中时合并并去重目标；已创建任务的目标不随规则编辑改变。
- 发送结果未知的任务不能自动重发；认证失败和 UIDVALIDITY 变化应保留暂停与人工处理机制。
- 转发保留 MIME 正文、附件及内嵌图片；网页只能展示经过清洗的 HTML。不要把原始邮件直接交给 `th:utext`。
- 分享的列表、详情与附件必须使用一致的有效期、启停和筛选约束。多主题组内 OR、多收件人组内 OR、不同条件组之间 AND。
- 编辑分享保持令牌不变；轮换令牌使旧链接失效；删除分享不删除来源邮件。
- 邮箱凭据和可恢复分享令牌使用现有本地密钥加密；公开链接仍通过令牌哈希校验。

## 本地工作边界

- 可以自主完成所请求的源码、模板、迁移、文档修改，以及隔离的本地验证；修复本次变更造成的失败，直到请求的行为得到验证。
- `.env`、`data/` 及自定义 `DATA_DIR` 可能包含真实账号、邮件和密钥。不要将其用于测试、提交到仓库或输出到日志，不覆盖 `.credentials.key`。
- 集成测试使用 GreenMail、本地随机端口和测试数据，可直接运行；不需要为每次修复和重跑重复询问。工具或环境要求的权限审批仍须遵守。
- 预览使用独立数据目录并关闭调度，例如直接运行 JAR 时传入 `--app.data-dir=./target/preview-data --spring.datasource.url=jdbc:h2:file:./target/preview-data/mail-agent --app.scheduling-enabled=false --server.port=18082`。`scripts/run.sh` 会加载真实 `.env`，启动前注意这一差别。
- 对真实邮箱发送邮件、修改线上数据或部署，需要用户对该操作的明确授权；不要把普通实现请求当作此类授权。

## 验证与交付

在项目根目录使用以下入口；`scripts/java-env.sh` 优先使用已有 `JAVA_HOME`，未设置时在 macOS 查找 JDK 21。若本机 jenv 失效，设置本次命令的有效 `JAVA_HOME`，不要修改全局 Java 配置。

```sh
# 完整测试并生成 target/mail-agent-1.0.0.jar
./scripts/build.sh

# 开发期间仅运行受影响的测试类
./scripts/build.sh -Dtest=MailAgentIntegrationTest
```

- 验证范围与变更风险相称。修复缺陷时优先覆盖可复现的行为；权限、发送恢复或迁移变更应覆盖相应边界。
- 涉及页面时检查模板实际渲染；布局或交互变更需要时再使用浏览器验证。
- 交付可运行 JAR 前执行完整构建。纯文档修改检查内容、路径和格式即可，不必重跑邮件集成测试。
- 相关检查通过后即可交付，不无故重复测试或扩大改动。若验证受阻，说明未完成的检查及原因，不将历史测试结果当作本次结果。
- 最终简述实际变化、验证结果与必要限制；提供用户需要的文件或产物路径。

## 维护本约定

只增加能够解决重复出现问题的项目知识或边界；删除过时、重复或与当前任务无关的常驻指令。详细操作指南留在 README 或专项文档中。

编写参考：[Rethinking skills and prompts for GPT-6 Astra](https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra)。本文将其精简上下文、按需读取、明确完成范围的建议应用到本仓库；具体业务约束来自项目实现。
