import { definePlugin, type AttachmentSelectProvider } from "@halo-dev/ui-shared";
import { markRaw } from "vue";
import AiImageSelector from "./extensions/AiImageSelector.vue";
import "./styles/main.css";

const attachmentSelectorProvider: AttachmentSelectProvider = {
  id: "fmimage-ai-image",
  label: "Ai_image",
  component: markRaw(AiImageSelector),
};

export default definePlugin({
  components: {},
  routes: [],
  ucRoutes: [],
  extensionPoints: {
    "attachment:selector:create": () => [attachmentSelectorProvider],
  },
});
