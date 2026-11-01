# 「里悦」Minecraft 工具箱 · Android 开发提示词

> 用法：把「主提示词」整段发给 AI 编程助手，然后按 M0 → M4 逐个里程碑推进，每次只做一个。每完成一个里程碑先跑验收再进入下一个。

---

## 一、主提示词（整段复制）

### 1. 角色

你是一名资深 Android 工程师，同时熟悉 Minecraft Java 版与基岩版的世界生成机制、模组生态和启动器目录结构。你的交付标准是「能上架、能长期维护的 App」，不是演示原型。

### 2. 目标

从零开发 Android 应用「里悦」（LiYue），一个 Minecraft 工具箱，包含两大核心能力：

1. **模组中心**：通过统一下载接口聚合 Modrinth、CurseForge、MCPEDL 三个来源，浏览、搜索、筛选、下载 Java 版模组与基岩版附加包，管理本地下载与收藏。
2. **种子地图**：输入种子与版本，渲染可缩放拖拽的生物群系地图，列出结构坐标，功能对标 Chunkbase，同时支持 Java 版与基岩版。

包名 `com.liyue.mctoolbox`，应用名「里悦」，界面文案中文优先。

### 3. 技术栈（固定，不要替换）

- Kotlin 2.x，Jetpack Compose + Material 3，单 Activity。
- Gradle Kotlin DSL + Version Catalog（`libs.versions.toml`）。
- 架构：多模块 + MVVM + 单向数据流，UI 层只用 `StateFlow<UiState>`，不直接持有 Repository。
- 依赖注入 Hilt；网络 Ktor Client 或 Retrofit + kotlinx.serialization；本地缓存 Room；偏好设置 DataStore。
- 图片 Coil；分页 Paging 3。
- 地图计算走 NDK：CMake + `externalNativeBuild`，JNI 封装 C 算法库。
- minSdk 26，targetSdk 35，compileSdk 35，Kotlin 协程全异步。
- 静态检查 detekt + ktlint，CI 至少跑 `assembleDebug`、`testDebugUnitTest`、`detekt`。

### 4. 产品边界（必须写进 README 并在 UI 中体现）

- **不内置、不托管任何模组文件**，只做官方 API 的客户端与本地文件管理。
- **不提供盗版游戏、账号或绕过正版验证的能力**。
- **不硬编码任何 API Key**。Android APK 可被反编译，密钥等于公开。CurseForge 的 API Key 只能放在自建代理里，客户端不持有。
- **MCPEDL 没有公开 API**，接入前先确认其 robots.txt 与服务条款；若不允许抓取，降级为「打开站内页面」，不做解析。
- 不绕过任何站点的反爬、登录校验或 Cloudflare 检查。
- 所有下载内容保留作者署名与来源链接。

### 5. 功能规格

#### 5.1 首页

- 顶部搜索框可直接跳转模组搜索或种子地图。
- 「最近使用的种子」卡片列表，可一键回到上次地图状态（种子、版本、维度、缩放、中心坐标）。
- 「下载中」入口，显示队列进度。

#### 5.2 模组中心

三个来源统一实现第 6 节的 `ModRepository`，UI 默认混合展示并给每条结果打来源徽标，可按来源筛选。单源失败只影响该源，不阻塞整体结果。

**Java 版（Modrinth + CurseForge）**

- Modrinth 走公开 API v2，无需 Key。
- CurseForge 走自建代理，客户端只认代理地址；代理负责持 Key、限流、字段裁剪与短期缓存。
- 两个源共用同一套「游戏版本 / 加载器 / 排序」筛选，能力不支持时降级并在 UI 置灰，不发请求再报错。
- 搜索：关键词、排序（相关度/下载量/最新/更新时间）、分页。
- 筛选：游戏版本、加载器（Fabric / Forge / NeoForge / Quilt）、客户端/服务端、开源协议。
- 详情页：图标、简介、正文（Markdown 渲染）、作者、下载量、关注数、外链、画廊。
- 版本列表：按游戏版本 + 加载器过滤，展示文件名、大小、发布时间、下载量。
- 依赖提示：读取 version 的 `dependencies`，区分 required / optional / incompatible，required 依赖提供一键加入下载队列。
- 下载：显示进度与速度，支持暂停/继续/取消，完成后校验 `sha1` 或 `sha512`，失败即删除临时文件并提示。
- 导出：支持把多个模组打包成 zip，或生成 `.mrpack` 供启动器导入。

**基岩版（MCPEDL + CurseForge）**

- MCPEDL 适配器：解析搜索页与详情页，抽取标题、作者、适用版本、下载入口。页面结构变化会失效，解析层必须可替换并配契约测试。
- MCPEDL 不提供 hash，下载后如实标记「未校验」，不要伪造校验结果。
- 搜索 `.mcaddon` / `.mcpack` / `.mcworld` 资源，展示适用版本与类型（行为包/资源包/地图/皮肤）。
- 下载完成后用 FileProvider + `ACTION_VIEW` 交给 Minecraft 打开安装，MIME 用 `application/octet-stream`；同时提供「分享到其他应用」。
- 明确提示：基岩版附加包无需解压，由游戏自行导入。

**本地库**

- 已下载列表，可查看文件大小、来源、删除、重新分享。
- 收藏夹只存在本地（Room），不做账号体系。

**Java 版安装的现实约束**

- Android 上没有 Java 运行环境，**不做「一键安装到整合包」的承诺**。
- 提供两条路径：SAF 选择目录（`ACTION_OPEN_DOCUMENT_TREE` + 持久化 URI 权限）把文件复制到用户指定的 `.minecraft/mods`；或直接导出到「下载」目录由用户自行处理。
- 不要申请 `MANAGE_EXTERNAL_STORAGE`，上架会被拒。

#### 5.3 种子地图（核心功能）

**输入区**

- 种子输入框：Java 版接受十进制/负数 long 或任意文本（文本按 Java `String.hashCode()` 折叠为 long）；基岩版种子是字符串，走基岩版自身的解析规则。两者不可混用，UI 必须按当前版本给出对应提示。
- 版本选择器：只列出算法库真实支持的版本，**不允许自行编造版本号**。
- 维度切换：主世界 / 下界 / 末地（末地只显示有限内容并标注）。
- 中心坐标输入，支持直接粘贴 `X Z` 或 `/tp` 指令。

**地图区**

- 双指缩放、单指拖拽、双击放大、惯性滑动。
- 群系按 cubiomes 调色板上色，水域与陆地有明确视觉区分；图例可展开。
- 结构图标分层显示，分类开关（村庄/要塞/海底神殿/林地府邸/掠夺者前哨/废弃传送门/远古城市/试炼密室/堡垒遗迹/下界要塞/末地城/史莱姆区块等）。
- 点击地图任意点，底部弹出信息卡：坐标、群系名称、该点最近的结构与直线距离。
- 长按复制坐标；主世界与下界坐标一键换算（÷8 / ×8）。
- 地图中心十字准星 + 比例尺 + 当前缩放级别对应的方块跨度。
- 导出当前视口为 PNG，写入相册并在通知中给出查看入口。

**结构列表页**

- 按距离排序，显示名称、坐标、距离、所在群系；点击跳转地图并高亮。
- 支持「只显示 N 格范围内」筛选。

**准确性要求（最重要）**

- 群系与结构坐标必须来自真实的 MC 算法库，**严禁用噪声函数、哈希色块或随机散布伪造地图**。
- 每个版本的支持程度必须如实展示：完整支持 / 部分支持 / 仅地形不支持结构。启动时展示能力矩阵，数据来自代码中的静态表，不能靠猜。
- 已知差异要写进说明：1.18 起 Java 与基岩版同种子地形群系一致，但结构与植被不同；基岩版结构算法覆盖度低于 Java 版。

#### 5.4 设置

- 主题：跟随系统 / 浅色 / 深色；Material You 动态取色可关闭。
- 下载：并发数、仅 Wi-Fi 下载、缓存上限与清理。
- 地图：默认版本、默认维度、结构图标大小、是否显示网格与坐标。
- 关于：开源库许可清单（cubiomes 等）、数据来源说明、版本号。

### 6. 模组源统一下载层（ModSource）

目标：上层 UI 与下载器只依赖统一模型，代码里不出现 `when (source)` 分支。

**模块划分**

- `core:modsource`：领域模型、`ModRepository` 接口、聚合器、统一错误类型。
- `modsource:modrinth`、`modsource:curseforge`、`modsource:mcpedl`：各自实现，用 Hilt 多绑定注入 `Set<ModRepository>`。
- `core:download`：下载队列、断点续传、校验、通知，与来源无关。

**统一模型**

```kotlin
@JvmInline value class ModId(val raw: String) // "modrinth:AABBCC" / "curseforge:12345" / "mcpedl:some-slug"

enum class ModSource { MODRINTH, CURSEFORGE, MCPEDL }
enum class ModLoader { FABRIC, FORGE, NEOFORGE, QUILT, BEDROCK_ADDON, UNKNOWN }
enum class SortOrder { RELEVANCE, DOWNLOADS, UPDATED, NEWEST }

data class SearchQuery(
    val text: String = "",
    val source: ModSource? = null,
    val gameVersion: String? = null,
    val loader: ModLoader? = null,
    val sort: SortOrder = SortOrder.RELEVANCE,
    val cursor: String? = null,
)

data class SearchPage(val items: List<ModSummary>, val nextCursor: String?)
```

还需要 `ModSummary`、`ModDetail`、`ModVersion`、`ModDependency`、`DownloadSpec`（url、fileName、sizeBytes、hashes、requiredHeaders）。`cursor` 用 Base64 包住各源自己的 offset/page/index，上层不感知。

**接口**

```kotlin
interface ModRepository {
    val source: ModSource
    suspend fun search(query: SearchQuery): Result<SearchPage>
    suspend fun detail(id: ModId): Result<ModDetail>
    suspend fun versions(id: ModId, gameVersion: String?, loader: ModLoader?): Result<List<ModVersion>>
    suspend fun downloadSpec(version: ModVersion): Result<DownloadSpec>
}
```

**聚合规则**

- 并发请求各源（`channelFlow` + `supervisorScope`），按源逐个 emit，哪个源先返回就先展示。
- 单源失败转成 `SourceError(source, message)` 数据项交给 UI，不抛异常、不清空已有结果。
- 不跨源合并条目：同一模组在不同源是不同发布，合并会误导用户。只做同源去重。
- 分页互相独立：每个源维护自己的 cursor，滚动到底只对未耗尽的源继续加载。
- 支持能力写成代码里的矩阵（例如 MCPEDL 不支持按加载器筛选），不支持时直接隐藏或置灰，不靠请求失败来发现。

**下载器**

- `DownloadSpec` 是唯一输入，来源适配器不直接操作文件。
- 队列持久化在 Room，WorkManager 调度，进度用 `Flow<DownloadProgress>` 回传。
- HTTP Range 断点续传；服务端不支持 Range 时退化为整文件重下并提示。
- 校验顺序 sha512 → sha1 → 无 hash 标记「未校验」。失败删除临时文件并保留原因。
- 并发默认 2，设置里可调；「仅 Wi-Fi」生效时暂停队列而不是判失败。
- 下载文件落在应用私有目录，导出时再走 MediaStore 或 SAF。

**三个适配器的差异**

| 维度 | Modrinth | CurseForge | MCPEDL |
| --- | --- | --- | --- |
| 鉴权 | 无需 Key | 代理持 Key | 无 |
| 分页 | offset/limit | index/pageSize | 页码 |
| hash | sha1/sha512 | 通常有 | 无 |
| 稳定性 | 高 | 高（经代理） | 依赖 HTML 结构 |
| 合规 | 遵守速率限制与 UA 要求 | 遵守 API 条款，不再分发 | 先确认 robots 与条款 |

**MCPEDL 解析层要求**

- 解析器接口化：`McpedlParser` 输入 HTML、输出领域模型，便于整层替换。
- 用离线样本做单测，样本放 `test/resources/mcpedl/`，覆盖搜索页、详情页、下载页三种结构。
- 请求间隔 ≥ 1s，带正常 UA，不并发抓取，失败重试不超过 2 次。
- 站点改版时抛 `ParseFailure` 并提示「该来源暂不可用」，不能返回空列表冒充「无结果」。

### 7. 种子地图技术方案

**算法库**

- Java 版：`Cubitect/cubiomes`（MIT，C 库），提供 `setupGenerator`、`applySeed`、`genBiomes`、`getBiomeAt` 以及 `finders` 中的结构定位函数。
- 基岩版：先做调研再定，候选为 `reedacartwright/cubiomes` 的 `bedrock` 分支、`Earthcomputer/bedrockified`、`bedrock-dev/MCBEStructureFinder`。**不要假设某个 fork 一定准确**，按第 8 节 M0 的方法验证后再接入。
- 以 `Cubitect/cubiomes-viewer` 作为结果对照基准。

**集成方式**

- 用 git submodule 或固定 commit 的源码拷贝放入 `core/seedgen/src/main/cpp/`，不要依赖系统包管理器。
- CMake 编译为静态库，JNI 层薄封装，只暴露必要接口。
- 群系批量结果用 `IntArray` 或直接写入 `DirectByteBuffer`，避免逐点 JNI 调用。
- 所有计算在 `Dispatchers.Default` 的受控线程池执行，JNI 调用不占主线程；取消时通过原子标志位中断，避免 ANR。

**渲染**

- 视口按 512×512 群系格（1 格 = 4 方块）分块生成，LRU 缓存已生成的 tile（建议上限 64 块）。
- 群系 id → ARGB 的颜色表在 Kotlin 侧维护，与 C 库的 biome id 枚举对齐；不匹配时打印明确日志，不静默使用默认色。
- 拖动时先缩放上一帧 tile 保证流畅，停止 150ms 后再计算新 tile。
- 缩放级别限制在 1:16 到 1:2048（方块/像素），越界时拒绝而不是继续采样。

**测试**

- 在 `core/seedgen` 写仪器无关的 JVM 单测：固定种子 + 坐标 → 期望群系 id，期望值从 cubiomes-viewer 导出。
- 至少覆盖 3 个版本档位（如 1.16.5、1.20.1、最新支持版本）× 主世界/下界。
- 结构坐标用同样方式做基准测试，允许 ±1 chunk 误差并注明原因。

### 8. 开工顺序（不要跳步）

**M0 可行性 spike（必须先做，1 天内出结论）**

- 建空工程 + NDK 模板，编译 cubiomes 并跑通一个 JNI 调用。
- 写一个命令行或单元测试，输出指定种子在 (0,0) 附近 256×256 区域的群系 id 矩阵。
- 与 cubiomes-viewer 的截图/导出结果逐点比对，记录一致率。
- 基岩版同样做一次，能跑通则记录支持版本与结构覆盖；跑不通就明确写「暂不支持」，不要硬接。
- 产出 `docs/capability-matrix.md`：版本 × 维度 × 群系 × 结构 × 支持状态。
- 这一步不通过，不要开始画 UI。

**M1 地图 MVP**：种子输入、主世界群系渲染、缩放拖拽、坐标查询、版本切换。
**M2 结构层**：结构图层、结构列表、最近结构、坐标跳转、下界坐标换算。
**M3 模组中心**：先落 `ModRepository` 抽象与 Modrinth 适配器，再接 CurseForge 代理与 MCPEDL 适配器；统一搜索聚合、下载队列、校验、本地库与分享。三个适配器分批提交，每个都能单独开关。
**M4 打磨发布**：能力矩阵展示、导出 PNG、性能优化、无障碍、深色模式、许可清单、Release 签名配置说明。

### 9. 验收标准

**正确性**

- 固定种子在支持版本内的群系一致率 ≥ 99%（以 cubiomes-viewer 为基准）。
- 结构坐标误差 ≤ 1 chunk，且不出现「列表有、地图无」的不一致。
- 不支持的版本在 UI 上被禁用并说明原因，而不是返回错误结果。

**多源聚合**

- 三个源中任一源超时或报错，其余源结果照常展示，出错源有独立重试入口。
- 同一关键词在三个源的分页均可连续加载，不重复、不丢项。
- 下载任务杀进程后可恢复，断点续传生效，校验失败自动删除临时文件。
- CurseForge 代理不可用时代码路径整体降级，不崩溃、不空屏。

**性能**

- 冷启动到首页可交互 < 2s（中端机）。
- 拖动与缩放稳定 60fps，无掉帧卡顿。
- 首次生成 512×512 tile < 300ms，1024×1024 < 1.2s。
- 连续浏览 5 分钟后内存稳定，无 OOM，tile 缓存命中率可观测。

**稳定性**

- 旋转屏幕、切后台、断网、下载中断、存储不足均有明确状态，不崩溃。
- 所有网络错误映射为可读中文提示，不暴露堆栈。

**工程质量**

- 无 `GlobalScope`，无主线程 IO，无硬编码密钥，无 `!!` 滥用。
- 每个 feature 模块有 ViewModel 单测；核心算法有基准测试。
- `./gradlew detekt` 与单测全绿。

### 10. 禁止事项

- 不得用假数据填充地图，不得把「预测」包装成「精确」。
- 不得抓取 Chunkbase 或其他站点数据作为后端。
- 不得内置或转存模组文件，不得绕过 Modrinth/CurseForge 的 API 条款与速率限制。
- 不得把 CurseForge 的 Key 写进 APK、Gradle 属性或公开仓库；不得对 MCPEDL 高频抓取或绕过其反爬。
- 不得把 MCPEDL 的页面解析做成唯一实现，解析层必须可替换、可关闭。
- 不得申请 `MANAGE_EXTERNAL_STORAGE`，不得静默写入用户目录。
- 不得为通过编译而删除测试、关闭 lint 或吞掉异常。

### 11. 输出要求

每次回复按以下结构：先给本次改动计划与涉及文件，再实现，最后列出执行的验证命令与结果。不要贴整份文件内容，只说明关键改动位置。遇到库不支持的能力，直接说明并给替代方案，不要编造 API。

---

## 二、每次追加给 AI 的短指令

按里程碑推进时，把下面这句接在主提示词后面：

> 本轮只做 M1，交付可运行的地图 MVP。开始前先用不超过 10 行确认你对 cubiomes 的接入方式和 tile 渲染方案，再写代码。完成后给出：改动文件清单、我需要在 Android Studio 里点的按钮、以及一条能复现的验证命令。

---

## 三、一句话版（贴到其他工具用）

> 用 Kotlin + Compose 做一个 Android 应用「里悦」：Modrinth、CurseForge、MCPEDL 三源统一下载接口 + 仿 Chunkbase 的种子地图。种子地图必须用 NDK 编译 cubiomes（Java 版）和基岩版移植库做真实群系/结构计算，不许用假数据；先在 M0 用 cubiomes-viewer 做一致性验证并产出能力矩阵，再依次做地图、结构层、模组中心。不硬编码 API Key，不内置模组文件，不申请全盘存储权限。

---

## 四、可直接替换的变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| 应用名 | 里悦 | 桌面图标与启动页文案 |
| 包名 | `com.liyue.mctoolbox` | 上架后不可改 |
| 最低版本 | Android 8.0 / API 26 | 覆盖绝大多数设备 |
| 模组源 | Modrinth + CurseForge（代理）+ MCPEDL | MCPEDL 需先确认条款，不允许则改为跳转 |
| 地图计算 | NDK + cubiomes | 也可先用 WASM 在 WebView 里验证 |
| 首发范围 | Java 版优先，基岩版标注支持度 | 基岩版算法覆盖不全时不要强行上线 |

## 五、参考资料

- cubiomes（Java 版群系与结构算法）：https://github.com/Cubitect/cubiomes
- cubiomes-viewer（结果对照基准）：https://github.com/Cubitect/cubiomes-viewer
- 基岩版 cubiomes 分支：https://github.com/reedacartwright/cubiomes/tree/bedrock
- Bedrockified：https://github.com/Earthcomputer/bedrockified
- MCBEStructureFinder：https://github.com/bedrock-dev/MCBEStructureFinder
- Modrinth API 文档：https://docs.modrinth.com/api/
- CurseForge API 文档：https://docs.curseforge.com/
- MCPEDL：https://mcpedl.com/
