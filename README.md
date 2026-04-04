# halo-plugin-fmimage

`halo-plugin-fmimage` 是一个 Halo 控制台插件，用来把 AI 文生图直接接进附件选择流程。当前精简版只保留 `AiHubMix` 一条能力链路，默认模型为 `doubao/doubao-seedream-4-0`，主打更省钱、更少配置、更少试错。

## 当前版本

- 插件版本：`1.0.24`
- 版本规则：后续只递增最后一位，例如 `1.0.24 -> 1.0.25`

## 手动升级版本

每次发布前，把下面几个位置同步改成同一个版本号即可：

1. 根目录 `VERSION`
2. 根目录 `gradle.properties` 里的 `version`
3. `ui/package.json` 里的 `version`
4. `src/main/java/run/halo/fmimage/service/FmImageSettingsService.java` 里的摘要版本号
5. `ui/src/views/PluginStatusTab.vue` 里的前端兜底版本号
6. README 里的“当前版本”说明

## 当前默认方案

- 唯一厂商：`AiHubMix`
- 默认模型：`doubao/doubao-seedream-4-0`
- 可选模型：`doubao/doubao-seedream-4-0`、`qianfan/qwen-image`、`openai/gpt-image-1-mini`
- 默认尺寸：`1K`
- 可选尺寸：`1K`、`2K`、`auto`
- 返回格式：`base64_json`
- 水印：`false`
- 单次张数：固定 `1`

## 已做的关键收敛

- 只保留 `AiHubMix` 设置，移除了其它厂商配置项
- 默认改成更省成本的 `doubao/doubao-seedream-4-0`
- 默认尺寸改成 `1K`，降低生成成本
- Doubao 请求改为 `base64_json`，减少浏览器读取远程图片时的跨域问题
- 新增后端图片代理下载接口，保存到 Halo 时不再依赖浏览器直连上游图片地址
- 上传 Halo 附件时补上了 `X-XSRF-TOKEN` 请求头
- 生成接口错误现在会直接返回明确错误信息，不再只剩笼统的“服务器内部错误”
- `1.0.23` 已同步修正 `ProviderTypeTest`，让构建测试和当前“仅保留 AiHubMix”策略一致
- `1.0.24` 新增两个经过官方文档核对的低试错备选模型：`qianfan/qwen-image`、`openai/gpt-image-1-mini`

## 使用方式

1. 安装插件。
2. 在插件设置里填写 `AiHubMix` 的 `Base URL` 与 `API Key`。
3. 打开文章或页面编辑器。
4. 打开附件选择弹窗，切换到 `Ai_image`。
5. 输入提示词并生成预览。
6. 选择存储策略与附件分组。
7. 保存到 Halo 附件库，然后在弹窗中确认插入。

## 关键实现位置

- 后端入口：`src/main/java/run/halo/fmimage/FmImagePlugin.java`
- 插件接口：`/apis/fmimage.halo.run/v1alpha1`
- 前端入口：`ui/src/index.ts`
- 文生图请求适配：`src/main/java/run/halo/fmimage/provider/AiHubMixProviderAdapter.java`
- 上游响应解析：`src/main/java/run/halo/fmimage/provider/AbstractJsonProviderAdapter.java`
- 远程图片代理下载：`src/main/java/run/halo/fmimage/service/GeneratedImageContentService.java`
- 附件上传前端调用：`ui/src/api/halo.ts`

## 备注

- 如果 `https://aihubmix.com/v1` 在你的服务器上解析失败，可以直接改成 `https://api.aihubmix.com/v1`。
- 当前版本对 `doubao/doubao-seedream-4-0` 做了固定化处理，目的是减少试错成本，不建议随意改成别的模型名。
- 如果你只想省钱，优先用 `doubao/doubao-seedream-4-0 + 1K`；`qianfan/qwen-image` 更适合中文文字和海报字效，`openai/gpt-image-1-mini` 作为经济型 GPT 备选。
- `1K` 是当前已切好的低成本档；你前面提到的 `720P` 我这次没有放进去，是因为当前参考的 AiHubMix 文档里写的是 `1K / 2K / 4K / auto` 这类尺寸枚举。
