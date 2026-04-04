# halo-plugin-fmimage

`halo-plugin-fmimage` 是一个 Halo 控制台插件，用来把 AI 文生图直接接入附件选择流程。当前精简版只保留 `AiHubMix` 一条链路，默认优先使用更省钱的豆包模型，减少试错成本。

## 当前版本

- 插件版本：`1.0.28`
- 版本规则：后续只递增最后一位，例如 `1.0.28 -> 1.0.29`

## 手动升级版本

每次发布前，把下面几个位置同步改成同一个版本号即可：

1. 根目录 `VERSION`
2. 根目录 `gradle.properties` 里的 `version`
3. `ui/package.json` 里的 `version`
4. `src/main/java/run/halo/fmimage/service/FmImageSettingsService.java` 里的摘要版本号
5. `ui/src/views/PluginStatusTab.vue` 里的前端兜底版本号
6. `README.md` 里的“当前版本”说明

## 当前默认方案

- 唯一提供商：`AiHubMix`
- 默认模型：`doubao/doubao-seedream-5.0-lite`
- 保留模型：
  - `doubao/doubao-seedream-5.0-lite`
  - `doubao/doubao-seedream-4-0-250828`
  - `qianfan/qwen-image`
  - `openai/gpt-image-1-mini`
- 默认尺寸：`2k`
- 可选尺寸：
  - Seedream 5 Lite：`2k`、`3k`
  - Seedream 4：`1K`、`2K`、`auto`
  - Qwen：`1024x1024`
  - GPT Mini：`1024x1024`
- Doubao 固定返回：`url`
- Doubao 默认质量：`low`
- Doubao 固定张数：`1`

## 1.0.28 调整

- 修复 `doubao/doubao-seedream-5.0-lite` 的尺寸映射错误
- 这个模型不再发送 `1K` 或 `auto`，改为只发送它当前支持的 `2k`、`3k` 或显式 `WIDTHxHEIGHT`
- 前端尺寸选项按模型分开显示，避免 5.0-lite 继续选到无效尺寸
- 后端代码级默认尺寸也同步改成 `2k`，避免未保存设置时又回退到 `1K`

## 为什么之前会看到 502

- AiHubMix 真正返回的是参数错误，不是网络挂了
- 插件后端把上游错误统一包装成 `502 BAD_GATEWAY` 返回给前端
- 你这次的真正根因是：`doubao-seedream-5.0-lite` 不接受 `1K/auto`，它当前要求 `WIDTHxHEIGHT`、`2k` 或 `3k`

## 使用方式

1. 安装插件
2. 在插件设置里填写 `AiHubMix` 的 `Base URL` 和 `API Key`
3. 打开文章或页面编辑器
4. 打开附件选择弹窗，切换到 `Ai_image`
5. 输入提示词并生成预览
6. 选择存储策略和附件分组
7. 保存到 Halo 附件库，再确认插入

## 关键实现位置

- 后端入口：`src/main/java/run/halo/fmimage/FmImagePlugin.java`
- 插件接口：`/apis/fmimage.halo.run/v1alpha1`
- 文生图适配：`src/main/java/run/halo/fmimage/provider/AiHubMixProviderAdapter.java`
- 上游响应解析：`src/main/java/run/halo/fmimage/provider/AbstractJsonProviderAdapter.java`
- 图片抓取代理：`src/main/java/run/halo/fmimage/service/GeneratedImageContentService.java`
- 上传 Halo 附件前端调用：`ui/src/api/halo.ts`

## 备注

- 如果 `https://aihubmix.com/v1` 在你的服务器上解析失败，可以直接改成 `https://api.aihubmix.com/v1`
- 这次关于 `doubao-seedream-5.0-lite` 的尺寸规则，我按你实际拿到的 AiHubMix 返回错误做了收敛：有效值是 `WIDTHxHEIGHT`、`2k` 或 `3k`
- 你给的官方 curl 用的是 `2K`，插件里我统一规范成更稳的 `2k`
