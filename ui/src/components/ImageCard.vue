<script setup lang="ts">
defineProps<{
  active: boolean;
  src: string;
  alt: string;
  meta?: string;
}>();
</script>

<template>
  <button class="fm-card" :class="{ 'is-active': active }" type="button">
    <img :src="src" :alt="alt" class="fm-card__image" />
    <span v-if="meta" class="fm-card__meta">{{ meta }}</span>
  </button>
</template>
