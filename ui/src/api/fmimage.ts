export interface ProviderItem {
  name: string;
  displayName: string;
  enabled: boolean;
  configured: boolean;
  supported: boolean;
  defaultModel: string;
  models: string[];
  note: string;
}

export interface ProviderCatalogResponse {
  defaultProvider: string;
  defaultSize: string;
  defaultCount: number;
  defaultResponseFormat: string;
  defaultPolicyName: string;
  defaultGroupName: string;
  allowPolicySwitch: boolean;
  allowGroupSwitch: boolean;
  items: ProviderItem[];
}

export interface GeneratedItem {
  previewUrl: string;
  sourceType: "url" | "b64_json" | string;
  mediaType: string;
  revisedPrompt: string;
  remoteId: string;
}

export interface ImageGenerationResponse {
  provider: string;
  displayName: string;
  model: string;
  prompt: string;
  size: string;
  createdAt: string;
  items: GeneratedItem[];
}

export interface PluginSummaryResponse {
  pluginName: string;
  version: string;
  defaultProvider: string;
  defaultSize: string;
  defaultResponseFormat: string;
  providers: Array<{
    name: string;
    displayName: string;
    enabled: boolean;
    configured: boolean;
    supported: boolean;
    defaultModel: string;
    note: string;
  }>;
}

function extractErrorMessage(raw: string, status: number): string {
  if (!raw) {
    return `Request failed: ${status}`;
  }

  try {
    const payload = JSON.parse(raw) as {
      detail?: string;
      title?: string;
      message?: string;
    };
    return payload.detail || payload.message || payload.title || raw;
  } catch {
    return raw;
  }
}

async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    credentials: "same-origin",
    ...init,
    headers: {
      ...(init?.headers ?? {}),
      "Content-Type": "application/json",
    },
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(extractErrorMessage(message, response.status));
  }

  return (await response.json()) as T;
}

export async function fetchProviderCatalog(): Promise<ProviderCatalogResponse> {
  return requestJson<ProviderCatalogResponse>("/apis/fmimage.halo.run/v1alpha1/providers", {
    method: "GET",
  });
}

export async function generateImages(payload: Record<string, unknown>): Promise<ImageGenerationResponse> {
  return requestJson<ImageGenerationResponse>("/apis/fmimage.halo.run/v1alpha1/images/generate", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function fetchPluginSummary(): Promise<PluginSummaryResponse> {
  return requestJson<PluginSummaryResponse>("/apis/fmimage.halo.run/v1alpha1/summary", {
    method: "GET",
  });
}
