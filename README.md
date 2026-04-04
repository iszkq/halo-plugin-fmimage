# halo-plugin-fmimage

`halo-plugin-fmimage` 是一个 Halo 控制台插件，用来把 AI 文生图直接接入附件选择流程。当前精简版只保留 `AiHubMix` 一条链路，优先减少试错成本，并补了更稳的免费模型兜底。

## 当前版本

- 插件版本：`1.0.31`
- 版本规则：后续只递增最后一位，例如 `1.0.31 -> 1.0.32`

## 手动升级版本

每次发布前，把下面几个位置同步改成同一个版本号即可：

1. 根目录 `VERSION`
2. 根目录 `gradle.properties` 里的 `version`
3. `ui/package.json` 里的 `version`
4. `src/main/java/run/halo/fmimage/service/FmImageSettingsService.java` 里的摘要版本号
5. `ui/src/views/PluginStatusTab.vue` 里的前端兜底版本号
6. `README.md` 里的“当前版本”说明

## 当前模型

- 唯一提供商：`AiHubMix`
- 默认模型：`doubao/doubao-seedream-5.0-lite`
- 已内置模型：
  - `gemini-3.1-flash-image-preview-free`
  - `doubao/doubao-seedream-5.0-lite`
  - `doubao/doubao-seedream-4-0-250828`
  - `qianfan/qwen-image`
  - `openai/gpt-image-1-mini`

## 模型规则

- `gemini-3.1-flash-image-preview-free`
  - 走 AiHubMix 的 OpenAI 兼容 `chat/completions`
  - 免费模型
  - 前端显示为宽高比：`16:9`、`1:1`、`9:16`、`4:3`、`3:4`、`3:2`、`2:3`、`21:9`
  - 当前这一版先支持文生图
- `doubao/doubao-seedream-5.0-lite`
  - 默认尺寸：`2k`
  - 可选：`2k`、`3k`
- `doubao/doubao-seedream-4-0-250828`
  - 可选：`1K`、`2K`、`auto`
- `qianfan/qwen-image`
  - 固定：`1024x1024`
- `openai/gpt-image-1-mini`
  - 固定：`1024x1024`

## 1.0.31 调整

- 上游限流不再统一伪装成 `502`
- 现在会尽量识别 AiHubMix 返回的 `429 / rate limited`，前端会看到更准确的限流提示
- 免费 Gemini 模型被限流时，会明确提示“稍后重试或切换其他模型”

## 说明

- 你后面补充的 Gemini 图片编辑示例，和这次接入用的是同一套响应结构
- 当前插件前端还没有单独做“上传原图再编辑”的入口，这一版先把免费文生图模型接通
- 如果后面要继续做“以图改图”，可以在现有这条 `chat/completions + multi_mod_content` 链路上继续扩

## 使用方式

1. 安装插件
2. 在插件设置里填写 `AiHubMix` 的 `Base URL` 和 `API Key`
3. 打开文章或页面编辑器
4. 打开附件选择弹窗，切换到 `Ai_image`
5. 选择模型并输入提示词
6. 生成预览
7. 选择存储策略和附件分组
8. 保存到 Halo 附件库，再确认插入

## 关键实现位置

- 后端入口：`src/main/java/run/halo/fmimage/FmImagePlugin.java`
- 插件接口：`/apis/fmimage.halo.run/v1alpha1`
- 文生图适配：`src/main/java/run/halo/fmimage/provider/AiHubMixProviderAdapter.java`
- 上游响应解析：`src/main/java/run/halo/fmimage/provider/AbstractJsonProviderAdapter.java`
- 图片抓取代理：`src/main/java/run/halo/fmimage/service/GeneratedImageContentService.java`
- 上传 Halo 附件前端调用：`ui/src/api/halo.ts`

## 备注

- 如果 `https://aihubmix.com/v1` 在你的服务器上解析失败，可以直接改成 `https://api.aihubmix.com/v1`
- `doubao-seedream-5.0-lite` 的尺寸规则，这一版按你实际拿到的 AiHubMix 返回错误做了收敛：有效值是 `WIDTHxHEIGHT`、`2k` 或 `3k`
- 如果你使用的是 AList 存储，建议确认插件版本至少为 `1.1.3`
