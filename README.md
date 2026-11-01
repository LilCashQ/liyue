# 里悦（LiYue）· Minecraft 工具箱

Android 端的 Minecraft 工具箱：模组中心（Modrinth / CurseForge / MCPEDL 统一聚合）与种子地图（基于 cubiomes 的真实群系与结构计算）。

当前进度：**M2 结构层完成**。首页为卡片式工具箱入口；种子地图支持真实群系、结构图标与筛选、结构列表、最近结构、坐标跳转与下界换算。

## 产品边界

- 不内置、不托管任何模组文件，只做官方 API 的客户端与本地文件管理。
- 不提供盗版游戏、账号或绕过正版验证的能力。
- 不硬编码任何 API Key。CurseForge 的 Key 只能放在自建代理里，客户端不持有。
- MCPEDL 没有公开 API；接入前需确认 robots.txt 与服务条款，不允许则降级为打开站内页面，不做解析。
- 不绕过任何站点的反爬、登录校验或 Cloudflare 检查；所有下载内容保留作者署名与来源链接。
- 不申请 `MANAGE_EXTERNAL_STORAGE`，不静默写入用户目录。

## 当前能力

- 首页：卡片式工具箱入口（种子地图可用，模组中心 / 本地库 / 收藏夹 / 设置标注即将上线）。
- 种子地图：输入种子后异步渲染真实群系地图，支持双指缩放、拖拽、双击放大、惯性滑动；点击任意点显示坐标与群系，长按复制坐标。
- 版本：覆盖 1.7.2 – 1.21.3 的 50 个版本档位；同一世界生成家族的次版本映射到对应 cubiomes 算法，并在选择器里标注“群系算法同 x”。
- 交互：坐标标尺与网格、中心准星、比例尺、1:xxx 读数、缩放/回到原点按钮、群系图例。
- 结构层：村庄、海底神殿、林地府邸、前哨、废弃传送门、远古城市、试炼密室、下界要塞、堡垒遗迹、末地城、要塞等，按维度筛选；地图上以彩色圆点标记；1.18+ 额外做地表有效性校验，减少假标记。
- 额外图层：出生点标记、史莱姆区块（放大后显示）。
- 结构列表：按距地图中心排序，点条目直接跳转；点击地图任意点可查看最近结构距离，并在主世界/下界坐标间一键跳转（÷8 / ×8）。
- 图例高亮：点图例中的群系只保留该群系，其余淡化；再次点击或“清除高亮”恢复。
- `core:seedgen`：以固定快照内置 [cubiomes](https://github.com/Cubitect/cubiomes)（提交 `e61f90580cbdd883214a8054670dacae655e59c0`，MIT），通过 CMake + JNI 计算 Java 版群系。
- 群系采样：1.18+ 走 voronoi 精确路径（方块分辨率生成后按 4×4 格心降采样），缩小视图时按档位改用带降采样的粗采样。
- 能力矩阵见 [docs/capability-matrix.md](docs/capability-matrix.md)，数据由 `tools/capmatrix` 实测生成。
- 基岩版：cubiomes 官方只支持 Java 版；现有候选库均不满足要求，暂不支持（详见能力矩阵）。

## 技术栈

Kotlin 2.1 / Jetpack Compose + Material 3 / 单 Activity；Gradle Kotlin DSL + Version Catalog；Hilt；多模块 + MVVM + `StateFlow<UiState>`；Room / DataStore / Ktor / Coil / Paging 3（模组中心阶段接入）；地图计算走 NDK + CMake + JNI。

| 模块 | 职责 |
| --- | --- |
| `app` | 应用壳、主题与页面切换 |
| `core:seedgen` | cubiomes 快照、JNI 封装、种子解析与群系查询 |
| `feature:home` | 卡片式工具箱首页 |
| `feature:map` | 种子地图：相机、渲染器、图例与 Compose UI |

## 构建

Android Studio 打开工程即可（需 JDK 17、Android SDK 35、NDK 29.0.14206865、CMake 4.2.1）。命令行：

```bash
./gradlew :app:assembleDebug
./gradlew :core:seedgen:testDebugUnitTest
```

无图形界面的 Linux 环境（本项目在 aarch64 容器内验证过）：

```bash
tools/verify-seedgen.sh   # 构建主机侧 JNI 库并跑基准测试
```

## 目录

```
app/                    应用模块（Compose UI、Hilt）
core/seedgen/           cubiomes + JNI + Kotlin 封装
  src/main/cpp/         C 源码、CMakeLists、cubiomes 快照
  src/test/             主机侧 JNI 基准测试
feature/map/            种子地图：相机、渲染、图例、UI
feature/home/           卡片式首页
docs/                   能力矩阵与验证记录
tools/                  refgen / refgrid / capmatrix / 验证脚本
```

## 参考实现

种子地图的交互参考了 [Axolotl Launcher](https://github.com/Mystic-Stars/Axolotl) 的内置种子地图（GPL-3.0）。本项目仅参考其产品设计与接口思路（地表有效性校验、要塞/史莱姆区块/出生点等图层），未复制其源代码；底层同样基于 MIT 许可的 cubiomes。

## 许可

cubiomes 以 MIT 许可分发，副本见 `core/seedgen/src/main/cpp/cubiomes/LICENSE`。
