# halo-plugin-fmimage

`halo-plugin-fmimage` 是一个面向 [Halo](https://www.halo.run/) 的 AI 文生图插件。

它的目标很直接：把“写提示词 -> 生成图片 -> 保存到 Halo 附件库 -> 插入文章”这条流程放进 Halo 编辑器里，尽量少折腾配置，尽量少试错，尽量少浪费额度。

当前版本已经收敛为单一提供商 `AiHubMix`，重点围绕“能稳定生成、能保存、能插入、成本可控”做了多轮优化。

## 项目简介

这个插件主要解决的是 Halo 里“AI 生成图片后还要手动下载、再手动上传、再手动插入”的麻烦流程。

装好之后，你可以直接在 Halo 的附件选择器里完成：

1. 输入提示词
2. 选择模型
3. 生成图片
4. 保存到附件库
5. 插入到文章或页面

## 功能亮点

- 在 Halo 附件选择器中直接生成 AI 图片，不需要离开编辑器
- 只保留 `AiHubMix` 一套配置，减少多厂商维护成本
- 内置多种模型，兼顾免费、低成本和稳定性
- 生成结果可直接保存到 Halo 附件库
- 支持默认存储策略、默认附件分组
- 上传前自动压缩图片，目标 `300KB` 以内
- 对 AList 等第三方存储做了额外兼容处理
- 对 AiHubMix 上游错误做了更明确的提示
- 对免费模型限流做了 `429` 识别，不再误报成普通 `502`

## 适用场景

- 文章配图
- 页面头图
- 封面图
- 社媒插图
- 简单海报
- 快速占位图

## 当前支持的模型

当前插件只保留 `AiHubMix` 提供商，内置模型如下：

- `gemini-3.1-flash-image-preview-free`
  说明：免费模型，适合临时使用或备用
- `doubao/doubao-seedream-5.0-lite`
  说明：当前默认模型，偏重性价比
- `doubao/doubao-seedream-4-0-250828`
  说明：旧版豆包备用模型
- `qianfan/qwen-image`
  说明：适合中文文字、海报字效一类场景
- `openai/gpt-image-1-mini`
  说明：经济型 GPT 图片模型备用

## 模型规则

不同模型的可用参数并不完全一样，插件已经按模型做了兼容收敛：

- `gemini-3.1-flash-image-preview-free`
  - 调用方式：AiHubMix OpenAI 兼容 `chat/completions`
  - 前端显示项：宽高比
  - 可选比例：`16:9`、`1:1`、`9:16`、`4:3`、`3:4`、`3:2`、`2:3`、`21:9`
  - 当前支持：文生图
- `doubao/doubao-seedream-5.0-lite`
  - 默认尺寸：`2k`
  - 可选尺寸：`2k`、`3k`
  - 默认质量：`low`
- `doubao/doubao-seedream-4-0-250828`
  - 可选尺寸：`1K`、`2K`、`auto`
- `qianfan/qwen-image`
  - 固定尺寸：`1024x1024`
- `openai/gpt-image-1-mini`
  - 固定尺寸：`1024x1024`

## 已实现能力

- Halo 控制台插件入口
- 插件设置页
- 附件选择器中的 AI 图片页签
- 文生图请求代理
- 上游返回结果解析
- 远程图片后端抓取
- 保存到 Halo 附件库
- 默认策略/分组回填
- 存储策略名称自动纠正
- AList 等存储下的附件 URL 回查
- 上传前自动压缩

## 使用流程

1. 安装插件
2. 在插件设置中填写 `AiHubMix` 的 `Base URL` 与 `API Key`
3. 打开文章或页面编辑器
4. 打开附件选择器
5. 切换到 `Ai_image`
6. 选择模型
7. 输入提示词
8. 生成预览
9. 选择存储策略与附件分组
10. 保存到 Halo 附件库
11. 在弹窗中确认插入

## 插件设置说明

### 基础设置

- 默认厂商
  当前固定为 `AiHubMix`
- 默认尺寸
  会作为未手动指定时的默认值
- 默认生成数量
  当前实际已限制为单次 1 张，避免浪费额度
- 默认存储策略
  留空时由前端选择当前可用策略
- 默认附件分组
  可选，留空则不强制分组

### AiHubMix 设置

- 是否启用
- `Base URL`
  默认：`https://aihubmix.com/v1`
- `API Key`
- 默认模型
- 模型列表

## 存储与上传能力

插件在“保存到 Halo”这一步做了几层增强：

- 如果上游返回的是远程图片 URL，会先由后端抓取图片内容
- 上传时会补齐 `X-XSRF-TOKEN`
- 上传前会自动压缩图片
- 首次上传响应没有可插入地址时，会自动回查附件详情
- 对 `permalink`、`thumbnailPermalink`、`rawUrl`、`sharedUrl`、`downloadUrl` 等字段做了兼容

这意味着它对本地存储、对象存储和 AList 这类外部存储都更稳一些。

## 图片压缩策略

生成图片保存到 Halo 前会自动压缩：

- 目标体积：`300KB` 以内
- 压缩方式：逐步降低质量和分辨率
- 优先格式：`webp` / `jpeg`
- 目的：降低外链体积、节省存储与带宽、提高插入成功率

## 错误处理优化

这个项目前面做过不少“对用户更友好”的错误收敛，当前包含：

- AiHubMix DNS 失败时自动尝试备用域名
- 上游 JSON 解析失败时给出明确提示
- 免费 Gemini 模型限流时识别为 `429`
- 不再把所有上游错误都混成一个笼统的 `502`
- 保存附件失败时会给出更具体的原因

## 已处理过的典型问题

- AiHubMix `response_format` 参数不兼容
- 豆包模型尺寸参数不兼容
- AiHubMix 任务轮询返回结构不统一
- 保存到 Halo 时浏览器跨域抓图失败
- `policyName` 参数格式错误
- AList 存储上传后没有立即返回可插入 URL
- 免费模型限流误显示为普通网关错误

## 项目结构

核心文件大致如下：

- 后端入口：`src/main/java/run/halo/fmimage/FmImagePlugin.java`
- 控制器：`src/main/java/run/halo/fmimage/controller/FmImageController.java`
- 异常处理：`src/main/java/run/halo/fmimage/controller/FmImageExceptionHandler.java`
- 配置汇总：`src/main/java/run/halo/fmimage/service/FmImageSettingsService.java`
- 文生图服务：`src/main/java/run/halo/fmimage/service/ImageGenerationService.java`
- AiHubMix 适配：`src/main/java/run/halo/fmimage/provider/AiHubMixProviderAdapter.java`
- 通用响应解析：`src/main/java/run/halo/fmimage/provider/AbstractJsonProviderAdapter.java`
- 图片抓取服务：`src/main/java/run/halo/fmimage/service/GeneratedImageContentService.java`
- 前端选择器：`ui/src/extensions/AiImageSelector.vue`
- Halo 上传接口：`ui/src/api/halo.ts`

## 开发说明

本项目是 Halo 插件项目，包含：

- Java 后端
- Vue 3 前端
- Gradle 构建
- pnpm / Vite 前端构建

主要构建流程：

1. Gradle 负责编译后端
2. `pnpm build` 构建前端
3. 前端产物同步到 `src/main/resources/console`
4. 最终一起打包为 Halo 插件

## 当前限制

- 当前只保留 `AiHubMix`，没有多厂商切换
- 免费 Gemini 模型会被上游限流，不适合作为唯一主力模型
- 当前还没有独立的“以图改图”前端入口
- 多图批量生成没有开放，默认限制为单次 1 张

## 后续可扩展方向

- 以图改图入口
- 局部重绘 / 蒙版编辑
- 生成历史记录
- 提示词模板
- 批量生成
- 自定义压缩阈值
- 可选是否保留原图

## 安装与配置建议

- `Base URL` 优先使用：`https://aihubmix.com/v1`
- 如果服务器对该域名解析不稳定，可改成：`https://api.aihubmix.com/v1`
- 如果使用 AList 存储，建议插件版本至少为 `1.1.3`
- 如果想控制成本，可优先使用：
  - `doubao/doubao-seedream-5.0-lite`
  - `gemini-3.1-flash-image-preview-free` 作为备用

## 版本维护

现在真正需要手动同步版本号的文件只剩这几个：

1. `VERSION`
2. `gradle.properties`
3. `ui/package.json`
4. `src/main/java/run/halo/fmimage/service/FmImageSettingsService.java`

像 `README.md` 和前端状态页里的版本号硬编码，我已经帮你去掉了，后面改版本会轻松很多。

## 仓库信息

- 作者：恪勤
- 仓库地址：<https://github.com/iszkq/halo-plugin-fmimage/>

## License

MIT
