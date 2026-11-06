# 里悦（LiYue）· Minecraft 工具箱

Android 端的 Minecraft 工具箱：模组中心（Modrinth / CurseForge / MCPEDL 统一聚合）与种子地图（基于 cubiomes 的真实群系与结构计算）。

当前进度：**M4 移植层完成**。首页为卡片式工具箱入口；种子地图支持真实群系、结构图标与筛选、结构列表、最近结构、坐标跳转与下界换算，并已按手机竖屏重排（地图占主体，控制项收进底部工具条与底部面板）；结构查找页按种子直接搜最近的结构、群系、要塞、史莱姆区块与出生点；矿石图层可扫视野内钻石/铁/铜/金/红石/青金石/煤/残骸等；点结构图标可看布局详情（朝向、包围盒、片段列表、战利品表）；地下剖面可看指定 Y 层的实心方块、洞穴与峡谷切片；版本支持到 26.3。

## 产品边界

- 不内置、不托管任何模组文件，只做官方 API 的客户端与本地文件管理。
- 不提供盗版游戏、账号或绕过正版验证的能力。
- 不硬编码任何 API Key。CurseForge 的 Key 只能放在自建代理里，客户端不持有。
- MCPEDL 没有公开 API；接入前需确认 robots.txt 与服务条款，不允许则降级为打开站内页面，不做解析。
- 不绕过任何站点的反爬、登录校验或 Cloudflare 检查；所有下载内容保留作者署名与来源链接。
- 不申请 `MANAGE_EXTERNAL_STORAGE`，不静默写入用户目录。

## 当前能力

- 首页：卡片式工具箱入口（种子地图、结构查找可用，模组中心 / 本地库 / 收藏夹 / 设置标注即将上线）。
- 种子地图：输入种子后异步渲染真实群系地图，支持双指缩放、拖拽、双击放大、惯性滑动；点击任意点显示坐标与群系，长按复制坐标。
- 版本：覆盖 1.7.2 – 1.21.3 的 50 个版本档位；同一世界生成家族的次版本映射到对应 cubiomes 算法，并在选择器里标注“群系算法同 x”。
- 交互：坐标标尺与网格、中心准星、比例尺、1:xxx 读数、缩放/回到原点按钮、群系图例。
- 结构层：村庄、海底神殿、林地府邸、前哨、废弃传送门、远古城市、试炼密室、下界要塞、堡垒遗迹、末地城、要塞等，按维度筛选；地图上以彩色圆点标记；1.18+ 额外做地表有效性校验，减少假标记。
- 额外图层：出生点标记、史莱姆区块（放大后显示）。
- 结构列表：按距地图中心排序，点条目直接跳转；点击地图任意点可查看最近结构距离，并在主世界/下界坐标间一键跳转（÷8 / ×8）。
- 图例高亮：点图例中的群系只保留该群系，其余淡化；再次点击或“清除高亮”恢复。
- 结构查找：与 Chunkbase 的 app 分页对应，分「结构 / 群系 / 要塞 / 史莱姆区块 / 出生点」五类。输入种子、中心坐标与半径后，按距离返回最近的若干结果，每条都能一键在地图上打开对应维度与位置。
  - 结构：26 种结构按版本过滤（版本里不存在的类型不会出现在下拉框），搜索范围按原版结构间距上限收敛，1.18+ 仍做地表有效性校验。
  - 群系：先用 16 方块粗采样定位候选，再在候选附近用 4 方块精度细化，结果按距离排序并做 128 方块去重。群系名按版本解释，并在界面上给出中文名。
  - 要塞 / 史莱姆区块 / 出生点：分别走 cubiomes 的要塞迭代、史莱姆区块判定与出生点估计。
  - 结果校验：`FinderEngineTest` 与独立实现的 `tools/refgen/refstruct` 逐点比对（seed 0 / 1.21.11 / 村庄，最近 3 个位置完全一致）。
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

Android Studio 打开工程即可（需 JDK 17、Android SDK 35）。原生库已预编译进 `core/seedgen/src/main/jniLibs/arm64-v8a/`，常规构建不需要 NDK。命令行：

```bash
./gradlew :app:assembleDebug
./gradlew :core:seedgen:testDebugUnitTest
```

无图形界面的 Linux 环境（本项目在 aarch64 容器内验证过）：

```bash
tools/verify-seedgen.sh        # 构建主机侧 JNI 库并跑基准测试
tools/build-android-so.sh      # 用 NDK sysroot 交叉编译 arm64-v8a 的 .so
```

`tools/build-android-so.sh` 面向 aarch64 宿主：NDK 只提供 linux-x86_64 的宿主工具链，所以脚本用系统 clang 配 NDK 的 sysroot、compiler-rt 和 `-rtlib=compiler-rt` 直接产出 `libliyue_seedgen.so`。CMakeLists.txt 依旧是源文件清单的唯一来源。

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
