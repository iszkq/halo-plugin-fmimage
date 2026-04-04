<script setup lang="ts">
import { inject, onMounted, ref, computed, type Ref } from "vue";
import type { Plugin } from "@halo-dev/ui-shared";
import { fetchPluginSummary, type PluginSummaryResponse } from "@/api/fmimage";

const loading = ref(true);
const errorMessage = ref("");
const summary = ref<PluginSummaryResponse | null>(null);
const plugin = inject<Ref<Plugin | undefined>>("plugin");

const pluginVersion = computed(() => plugin?.value?.spec.version || summary.value?.version || "1.0.22");
const pluginDisplayName = computed(() => plugin?.value?.spec.displayName || "FM Image");

async function loadSummary() {
  loading.value = true;
  errorMessage.value = "";
  try {
    summary.value = await fetchPluginSummary();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "加载状态失败";
  } finally {
    loading.value = false;
  }
}

onMounted(loadSummary);
</script>

<template>
  <div class="fm-shell">
    <div class="fm-panel">
      <div class="fm-panel__header">
        <div>
          <h3 class="fm-title">{{ pluginDisplayName }} 控制台</h3>
          <p class="fm-subtitle">这里展示当前插件状态，实际配置请在本插件的设置分组中填写。</p>
        </div>
      </div>

      <div v-if="loading" class="fm-state">正在加载插件状态…</div>
      <div v-else-if="errorMessage" class="fm-alert fm-alert--error">{{ errorMessage }}</div>
      <template v-else-if="summary">
        <div class="fm-kpis">
          <div class="fm-kpi">
            <span>版本</span>
            <strong>{{ pluginVersion }}</strong>
          </div>
          <div class="fm-kpi">
            <span>默认厂商</span>
            <strong>{{ summary.defaultProvider }}</strong>
          </div>
          <div class="fm-kpi">
            <span>默认尺寸</span>
            <strong>{{ summary.defaultSize }}</strong>
          </div>
          <div class="fm-kpi">
            <span>默认返回格式</span>
            <strong>{{ summary.defaultResponseFormat }}</strong>
          </div>
        </div>

        <div class="fm-provider-grid">
          <article v-for="item in summary.providers" :key="item.name" class="fm-provider-card">
            <div class="fm-provider-card__top">
              <h4>{{ item.displayName }}</h4>
              <span class="fm-pill" :class="item.supported ? 'is-supported' : 'is-unsupported'">
                {{ item.supported ? "已接入" : "研究中" }}
              </span>
            </div>
            <p class="fm-provider-card__meta">默认模型：{{ item.defaultModel || "未设置" }}</p>
            <p class="fm-provider-card__meta">启用状态：{{ item.enabled ? "已启用" : "未启用" }}</p>
            <p class="fm-provider-card__meta">配置状态：{{ item.configured ? "已配置" : "未配置" }}</p>
            <p v-if="item.note" class="fm-provider-card__note">{{ item.note }}</p>
          </article>
        </div>

        <div class="fm-callout">
          在 Halo 中打开附件选择器后，你会看到新的 <code>Ai_image</code> 标签页。先在本插件设置里补全至少一个厂商的 API Key，再去文章或页面编辑器中体验文生图与转存流程。
        </div>
      </template>
    </div>
  </div>
</template>
