# halo-plugin-fmimage

`halo-plugin-fmimage` 是一个 Halo 控制台插件，用来把 AI 文生图直接接入附件选择流程。当前精简版只保留 `AiHubMix` 一条链路，默认优先使用更省钱的豆包模型，减少试错成本。

## 当前版本

- 插件版本：`1.0.29`
- 版本规则：后续只递增最后一位，例如 `1.0.29 -> 1.0.30`

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

## 1.0.29 调整

- 修复 AList 存储下“附件已上传，但未返回可插入的访问地址”
- 上传成功后，如果首次响应没有 `permalink`，前端会自动回查附件详情并重试取 URL
- 附件 URL 提取兼容更多字段：`permalink`、`thumbnailPermalink`、`rawUrl`、`sharedUrl`、`downloadUrl`
- 如果最终仍然没有可插入地址，会给出更明确的 AList 升级提示

## 为什么 AList 容易出现这个问题

- Halo 附件插入依赖附件对象里的可访问 URL，文档里标准字段是 `status.permalink`
- AList 存储插件历史版本里修过两次相关问题：
  - `1.1.0`：解决上传成功后无法立即访问附件的问题
  - `1.1.2`：修复使用 `rawUrl` 时图片加载异常
- 所以如果你当前 AList 插件版本较老，最稳妥还是升级到 `1.1.3`

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
- `doubao-seedream-5.0-lite` 的尺寸规则，我按你实际拿到的 AiHubMix 返回错误做了收敛：有效值是 `WIDTHxHEIGHT`、`2k` 或 `3k`
- 如果你使用的是 AList 存储，建议确认插件版本至少为 `1.1.3`
