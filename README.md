# halo-plugin-fmimage

`halo-plugin-fmimage` 是一个 Halo 控制台插件，用来把 AI 文生图直接接入附件选择流程。当前精简版只保留 `AiHubMix` 一条链路，默认优先使用更省钱的豆包模型，减少试错成本。

## 当前版本

- 插件版本：`1.0.26`
- 版本规则：后续只递增最后一位，例如 `1.0.26 -> 1.0.27`

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
- 默认尺寸：`1K`
- 默认低成本档显示为：`720p / 1K（低成本）`
- 可选尺寸：
  - Doubao：`720p / 1K（低成本）`、`2K`、`auto`
  - Qwen：`1024x1024`
  - GPT Mini：`1024x1024`
- Doubao 固定返回：`url`
- Doubao 默认质量：`low`
- Doubao 固定张数：`1`

## 1.0.26 调整

- 新增并默认切换到官方模型：`doubao/doubao-seedream-5.0-lite`
- Doubao 默认走低成本档：`1K + quality=low`
- 前端文案把低成本档标成 `720p / 1K（低成本）`，避免误填上游不支持的尺寸值
- 修复保存到 Halo 时 `Invalid part of policyName`
- 上传附件改为通过 multipart 表单传 `policyName` 和 `groupName`
- 如果插件设置里填的是存储策略显示名，前端会自动匹配成 Halo 真正的策略名再上传

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
- 目前参考的 AiHubMix 文档里，Doubao 尺寸仍以 `1K / 2K / 4K / auto` 为主，所以这次没有把 `720p` 直接作为上游 size 值发送，避免再次触发 size 参数错误
- 你要的“720p”我这里按低成本档处理成 `1K + quality=low`，这是当前更稳的近似方案
