<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import type { AttachmentLike } from "@halo-dev/ui-shared";
import ImageCard from "@/components/ImageCard.vue";
import { fetchProviderCatalog, generateImages, type GeneratedItem, type ProviderCatalogResponse, type ProviderItem } from "@/api/fmimage";
import { imageSourceToFile, listGroups, listPolicies, toSelectedAttachment, uploadAttachment, type HaloGroup, type HaloPolicy } from "@/api/halo";

withDefaults(
  defineProps<{
    selected: AttachmentLike[];
  }>(),
  {
    selected: () => [],
  }
);

const emit = defineEmits<{
  (event: "update:selected", value: AttachmentLike[]): void;
}>();

const loading = ref(true);
const generating = ref(false);
const saving = ref(false);
const errorMessage = ref("");
const successMessage = ref("");

const catalog = ref<ProviderCatalogResponse | null>(null);
const policies = ref<HaloPolicy[]>([]);
const groups = ref<HaloGroup[]>([]);

const prompt = ref("");
const negativePrompt = ref("");
const provider = ref("");
const model = ref("");
const size = ref("1K");
const count = ref(1);
const responseFormat = ref("url");
const quality = ref("");
const style = ref("");
const policyName = ref("");
const groupName = ref("");

const results = ref<GeneratedItem[]>([]);
const selectedIndex = ref(0);

const availableProviders = computed(() =>
  (catalog.value?.items ?? []).filter((item) => item.supported && item.enabled && item.configured)
);

const hasConfiguredProvider = computed(() => availableProviders.value.length > 0);

const selectedProvider = computed<ProviderItem | undefined>(() =>
  availableProviders.value.find((item) => item.name === provider.value)
);

const selectedResult = computed(() => results.value[selectedIndex.value]);

const availableSizes = computed(() => {
  if (provider.value === "aihubmix") {
    const normalizedModel = model.value.toLowerCase();
    if (normalizedModel.startsWith("doubao/")) {
      return [
        { label: "1K", value: "1K" },
        { label: "2K (16:9)", value: "2K" },
        { label: "auto", value: "auto" },
      ];
    }
    if (normalizedModel.startsWith("qianfan/qwen-image")) {
      return [{ label: "1024x1024", value: "1024x1024" }];
    }
    if (normalizedModel === "openai/gpt-image-1-mini") {
      return [{ label: "1024x1024", value: "1024x1024" }];
    }
    return [{ label: "1K", value: "1K" }];
  }

  return [{ label: "1K", value: "1K" }];
});

const supportsResponseFormat = computed(() => provider.value !== "aihubmix");

const supportsStyle = computed(() => provider.value !== "aihubmix");

const availableCounts = computed(() => {
  if (provider.value === "aihubmix" && model.value.toLowerCase().startsWith("doubao/")) {
    return [1];
  }
  return [1, 2, 3, 4];
});

function syncModelOptions() {
  if (!availableSizes.value.some((item) => item.value === size.value)) {
    size.value = availableSizes.value[0]?.value || "1K";
  }
  if (!availableCounts.value.includes(count.value)) {
    count.value = availableCounts.value[0] || 1;
  }
  if (!supportsResponseFormat.value) {
    responseFormat.value = "url";
  }
  if (!supportsStyle.value) {
    style.value = "";
  }
}

watch(selectedProvider, (value) => {
  model.value = value?.defaultModel ?? "";
  syncModelOptions();
});

watch([provider, model], () => {
  syncModelOptions();
});

async function loadInitialData() {
  loading.value = true;
  errorMessage.value = "";

  try {
    const [providerCatalog, policyItems, groupItems] = await Promise.all([
      fetchProviderCatalog(),
      listPolicies(),
      listGroups(),
    ]);

    catalog.value = providerCatalog;
    policies.value = policyItems;
    groups.value = groupItems;

    provider.value =
      availableProviders.value.find((item) => item.name === providerCatalog.defaultProvider)?.name ||
      availableProviders.value[0]?.name ||
      "";
    model.value = availableProviders.value.find((item) => item.name === provider.value)?.defaultModel || "";
    size.value = providerCatalog.defaultSize || "1K";
    count.value = providerCatalog.defaultCount || 1;
    responseFormat.value = providerCatalog.defaultResponseFormat || "url";
    syncModelOptions();

    const preferredPolicy = providerCatalog.defaultPolicyName || policyItems[0]?.metadata?.name || "";
    policyName.value = preferredPolicy;
    groupName.value = providerCatalog.defaultGroupName || "";
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "加载插件配置失败";
  } finally {
    loading.value = false;
  }
}

function resetFeedback() {
  errorMessage.value = "";
  successMessage.value = "";
}

async function handleGenerate() {
  resetFeedback();
  if (!prompt.value.trim()) {
    errorMessage.value = "请输入要生成的图片描述。";
    return;
  }
  if (!provider.value) {
    errorMessage.value = "请先选择一个可用厂商。";
    return;
  }

  generating.value = true;
  try {
    const response = await generateImages({
      provider: provider.value,
      model: model.value,
      prompt: prompt.value,
      negativePrompt: negativePrompt.value,
      size: size.value,
      count: count.value,
      quality: quality.value || undefined,
      style: supportsStyle.value ? style.value || undefined : undefined,
      responseFormat: supportsResponseFormat.value ? responseFormat.value : undefined,
    });

    results.value = response.items ?? [];
    selectedIndex.value = 0;
    if (!results.value.length) {
      errorMessage.value = "当前请求没有返回图片结果。";
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "生成失败";
  } finally {
    generating.value = false;
  }
}

function guessFileName(item: GeneratedItem) {
  const suffix = item.mediaType?.includes("jpeg") ? "jpg" : "png";
  return `fmimage-${Date.now()}.${suffix}`;
}

async function handleSave() {
  resetFeedback();
  if (!selectedResult.value) {
    errorMessage.value = "请先选择一张生成结果。";
    return;
  }
  if (!policyName.value) {
    errorMessage.value = "请选择存储策略。";
    return;
  }

  saving.value = true;
  try {
    const item = selectedResult.value;
    const file = await imageSourceToFile(item.previewUrl, item.mediaType, guessFileName(item));
    const attachment = await uploadAttachment(file, policyName.value, groupName.value || undefined);
    const selectedAttachment = toSelectedAttachment(attachment);
    if (!selectedAttachment.url) {
      throw new Error("附件已上传，但未返回可插入的访问地址。");
    }
    emit("update:selected", [selectedAttachment]);
    successMessage.value = "图片已保存到 Halo 附件库，点击弹窗确认即可插入。";
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "保存到 Halo 失败";
  } finally {
    saving.value = false;
  }
}

onMounted(loadInitialData);
</script>

<template>
  <div class="fm-shell">
    <div class="fm-panel">
      <div class="fm-panel__header">
        <div>
          <h3 class="fm-title">Ai_image</h3>
          <p class="fm-subtitle">在附件选择器中完成提示词输入、预览与转存到 Halo。</p>
        </div>
      </div>

      <div v-if="loading" class="fm-state">正在加载插件配置…</div>

      <template v-else>
        <div v-if="!hasConfiguredProvider" class="fm-alert fm-alert--error">
          请先在插件设置里填写至少一组可用的 API 配置。
        </div>

        <div class="fm-grid">
          <label class="fm-field fm-field--wide">
            <span>图片描述</span>
            <textarea v-model="prompt" rows="4" placeholder="例如：一间带木质书架的安静书房，阳光洒在桌面上，写实摄影风格"></textarea>
          </label>

          <label class="fm-field fm-field--wide">
            <span>负面提示词</span>
            <textarea v-model="negativePrompt" rows="2" placeholder="例如：模糊、低清、扭曲手指"></textarea>
          </label>

          <label v-if="availableProviders.length > 1" class="fm-field">
            <span>厂商</span>
            <select v-model="provider" :disabled="!hasConfiguredProvider">
              <option v-for="item in availableProviders" :key="item.name" :value="item.name">
                {{ item.displayName }}
              </option>
            </select>
          </label>

          <label v-if="(selectedProvider?.models?.length ?? 0) > 1" class="fm-field">
            <span>模型</span>
            <select v-model="model" :disabled="!hasConfiguredProvider">
              <option v-for="item in selectedProvider?.models ?? []" :key="item" :value="item">
                {{ item }}
              </option>
            </select>
          </label>

          <label class="fm-field">
            <span>尺寸</span>
            <select v-model="size">
              <option v-for="item in availableSizes" :key="item.value" :value="item.value">
                {{ item.label }}
              </option>
            </select>
          </label>

          <label v-if="availableCounts.length > 1" class="fm-field">
            <span>张数</span>
            <select v-model="count">
              <option v-for="item in availableCounts" :key="item" :value="item">{{ item }}</option>
            </select>
          </label>

          <label class="fm-field" v-if="supportsResponseFormat">
            <span>返回格式</span>
            <select v-model="responseFormat">
              <option value="b64_json">Base64</option>
              <option value="url">URL</option>
            </select>
          </label>

          <label v-if="provider !== 'aihubmix'" class="fm-field">
            <span>质量</span>
            <input v-model="quality" type="text" placeholder="可选" />
          </label>

          <label v-if="model === 'openai/gpt-image-1-mini'" class="fm-field">
            <span>质量</span>
            <input v-model="quality" type="text" placeholder="默认 low" />
          </label>

          <label class="fm-field" v-if="supportsStyle">
            <span>风格</span>
            <input v-model="style" type="text" placeholder="可选" />
          </label>
        </div>

        <div class="fm-actions">
          <button class="fm-button fm-button--primary" type="button" :disabled="generating || !hasConfiguredProvider" @click="handleGenerate">
            {{ generating ? "生成中…" : "生成预览" }}
          </button>
        </div>

        <div v-if="errorMessage" class="fm-alert fm-alert--error">{{ errorMessage }}</div>
        <div v-if="successMessage" class="fm-alert fm-alert--success">{{ successMessage }}</div>

        <div class="fm-divider"></div>

        <div class="fm-results">
          <div class="fm-results__header">
            <h4>生成结果</h4>
            <span v-if="results.length">{{ results.length }} 张</span>
          </div>
          <div v-if="!results.length" class="fm-state">生成后的图片会显示在这里。</div>
          <div v-else class="fm-results__grid">
            <div v-for="(item, index) in results" :key="item.remoteId || item.previewUrl" @click="selectedIndex = index">
              <ImageCard
                :active="selectedIndex === index"
                :src="item.previewUrl"
                :alt="`generated-image-${index + 1}`"
                :meta="selectedIndex === index ? '已选中' : ''"
              />
            </div>
          </div>
        </div>

        <div class="fm-toolbar">
          <label class="fm-field">
            <span>存储策略</span>
            <select v-model="policyName" :disabled="catalog && !catalog.allowPolicySwitch">
              <option v-for="item in policies" :key="item.metadata?.name" :value="item.metadata?.name">
                {{ item.spec?.displayName || item.metadata?.name }}
              </option>
            </select>
          </label>

          <label class="fm-field">
            <span>附件分组</span>
            <select v-model="groupName" :disabled="catalog && !catalog.allowGroupSwitch">
              <option value="">不分组</option>
              <option v-for="item in groups" :key="item.metadata?.name" :value="item.metadata?.name">
                {{ item.spec?.displayName || item.metadata?.name }}
              </option>
            </select>
          </label>

          <button class="fm-button fm-button--accent" type="button" :disabled="saving || !selectedResult" @click="handleSave">
            {{ saving ? "保存中…" : "保存到 Halo" }}
          </button>
        </div>
      </template>
    </div>
  </div>
</template>
