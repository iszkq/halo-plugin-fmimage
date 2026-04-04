import type { AttachmentSimple } from "@halo-dev/ui-shared";

export interface HaloListResponse<T> {
  items: T[];
}

export interface HaloPolicy {
  metadata?: {
    name?: string;
  };
  spec?: {
    displayName?: string;
  };
}

export interface HaloGroup {
  metadata?: {
    name?: string;
  };
  spec?: {
    displayName?: string;
  };
}

export interface HaloAttachment {
  metadata?: {
    name?: string;
  };
  spec?: {
    displayName?: string;
    mediaType?: string;
    size?: number;
    groupName?: string;
    url?: string;
    rawUrl?: string;
  };
  status?: {
    permalink?: string;
    thumbnailPermalink?: string;
    rawUrl?: string;
    sharedUrl?: string;
    downloadUrl?: string;
    url?: string;
  };
  permalink?: string;
  url?: string;
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    credentials: "same-origin",
    ...init,
  });

  if (!response.ok) {
    throw new Error(extractErrorMessage(await response.text(), response.status));
  }

  return (await response.json()) as T;
}

function getCsrfToken(): string {
  if (typeof document === "undefined") {
    return "";
  }
  const matched = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return matched ? decodeURIComponent(matched[1]) : "";
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
      error?: string;
    };
    return payload.detail || payload.message || payload.title || payload.error || raw;
  } catch {
    return raw;
  }
}

function buildRequestHeaders(headers?: Record<string, string>): Record<string, string> {
  const csrfToken = getCsrfToken();
  if (!csrfToken) {
    return headers ?? {};
  }

  return {
    ...(headers ?? {}),
    "X-XSRF-TOKEN": csrfToken,
  };
}

export async function listPolicies(): Promise<HaloPolicy[]> {
  const data = await request<HaloListResponse<HaloPolicy>>(
    "/apis/storage.halo.run/v1alpha1/policies?page=0&size=200&sort=metadata.creationTimestamp,desc"
  );
  return data.items ?? [];
}

export async function listGroups(): Promise<HaloGroup[]> {
  const data = await request<HaloListResponse<HaloGroup>>(
    "/apis/storage.halo.run/v1alpha1/groups?page=0&size=200&sort=metadata.creationTimestamp,desc"
  );
  return data.items ?? [];
}

export async function uploadAttachment(file: File, policyName: string, groupName?: string): Promise<HaloAttachment> {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("policyName", policyName.trim());
  if (groupName?.trim()) {
    formData.append("groupName", groupName.trim());
  }

  const response = await fetch("/apis/api.console.halo.run/v1alpha1/attachments/upload", {
    method: "POST",
    credentials: "same-origin",
    headers: buildRequestHeaders(),
    body: formData,
  });

  if (!response.ok) {
    throw new Error(extractErrorMessage(await response.text(), response.status));
  }

  const attachment = (await response.json()) as HaloAttachment;
  return await resolveUploadedAttachment(attachment);
}

async function fetchAttachment(name: string): Promise<HaloAttachment> {
  try {
    return await request<HaloAttachment>(`/apis/storage.halo.run/v1alpha1/attachments/${encodeURIComponent(name)}`, {
      method: "GET",
    });
  } catch {
    return await request<HaloAttachment>(`/apis/api.console.halo.run/v1alpha1/attachments/${encodeURIComponent(name)}`, {
      method: "GET",
    });
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    globalThis.setTimeout(resolve, ms);
  });
}

function extractAttachmentUrl(attachment: HaloAttachment): string {
  return (
    attachment.status?.permalink ||
    attachment.status?.thumbnailPermalink ||
    attachment.status?.rawUrl ||
    attachment.status?.sharedUrl ||
    attachment.status?.downloadUrl ||
    attachment.status?.url ||
    attachment.spec?.rawUrl ||
    attachment.spec?.url ||
    attachment.permalink ||
    attachment.url ||
    ""
  );
}

async function resolveUploadedAttachment(attachment: HaloAttachment): Promise<HaloAttachment> {
  if (extractAttachmentUrl(attachment)) {
    return attachment;
  }

  const attachmentName = attachment.metadata?.name;
  if (!attachmentName) {
    return attachment;
  }

  let latestAttachment = attachment;
  for (let attempt = 0; attempt < 5; attempt += 1) {
    await sleep(800);
    try {
      latestAttachment = await fetchAttachment(attachmentName);
      if (extractAttachmentUrl(latestAttachment)) {
        return latestAttachment;
      }
    } catch {
      // Ignore transient lookup errors and continue retrying.
    }
  }

  return latestAttachment;
}

export function toSelectedAttachment(attachment: HaloAttachment): AttachmentSimple {
  return {
    url: extractAttachmentUrl(attachment),
    mediaType: attachment.spec?.mediaType,
    alt: attachment.spec?.displayName || attachment.metadata?.name,
  };
}

export async function imageSourceToFile(sourceUrl: string, mediaType: string, fileName: string): Promise<File> {
  const response = sourceUrl.startsWith("http://") || sourceUrl.startsWith("https://")
    ? await fetch("/apis/fmimage.halo.run/v1alpha1/images/fetch", {
        method: "POST",
        credentials: "same-origin",
        headers: buildRequestHeaders({
          "Content-Type": "application/json",
        }),
        body: JSON.stringify({
          sourceUrl,
          mediaType,
        }),
      })
    : await fetch(sourceUrl);

  if (!response.ok) {
    throw new Error(extractErrorMessage(await response.text(), response.status));
  }
  const blob = await response.blob();
  return new File([blob], fileName, { type: blob.type || mediaType || "image/png" });
}
