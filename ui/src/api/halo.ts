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

const MAX_UPLOAD_IMAGE_BYTES = 300 * 1024;
const COMPRESSION_QUALITIES = [0.88, 0.8, 0.72, 0.64, 0.56, 0.48, 0.4, 0.32, 0.24, 0.18, 0.14];
const COMPRESSION_SCALES = [1, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.32, 0.24, 0.18, 0.14];

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
  const preparedFile = await compressImageForUpload(file);
  const formData = new FormData();
  formData.append("file", preparedFile);
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

function isCompressibleImage(file: File): boolean {
  return file.type.startsWith("image/");
}

function replaceFileExtension(fileName: string, nextExtension: string): string {
  const normalizedExtension = nextExtension.startsWith(".") ? nextExtension : `.${nextExtension}`;
  const index = fileName.lastIndexOf(".");
  if (index <= 0) {
    return `${fileName}${normalizedExtension}`;
  }
  return `${fileName.slice(0, index)}${normalizedExtension}`;
}

function extensionForMimeType(mimeType: string): string {
  return mimeType === "image/webp" ? ".webp" : ".jpg";
}

async function loadImageDimensions(file: File): Promise<HTMLImageElement> {
  return await new Promise((resolve, reject) => {
    const objectUrl = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      URL.revokeObjectURL(objectUrl);
      resolve(image);
    };
    image.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      reject(new Error("图片压缩前读取失败"));
    };
    image.src = objectUrl;
  });
}

async function canvasToBlob(canvas: HTMLCanvasElement, mimeType: string, quality: number): Promise<Blob | null> {
  return await new Promise((resolve) => {
    canvas.toBlob(resolve, mimeType, quality);
  });
}

async function compressImageForUpload(file: File): Promise<File> {
  if (!isCompressibleImage(file) || file.size <= MAX_UPLOAD_IMAGE_BYTES) {
    return file;
  }

  const image = await loadImageDimensions(file);
  const candidateMimeTypes = ["image/webp", "image/jpeg"];
  let bestBlob: Blob | null = null;
  let bestMimeType = file.type || "image/webp";

  for (const scale of COMPRESSION_SCALES) {
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(image.naturalWidth * scale));
    canvas.height = Math.max(1, Math.round(image.naturalHeight * scale));

    const context = canvas.getContext("2d");
    if (!context) {
      break;
    }

    context.clearRect(0, 0, canvas.width, canvas.height);
    context.drawImage(image, 0, 0, canvas.width, canvas.height);

    for (const mimeType of candidateMimeTypes) {
      for (const quality of COMPRESSION_QUALITIES) {
        const blob = await canvasToBlob(canvas, mimeType, quality);
        if (!blob) {
          continue;
        }

        if (bestBlob == null || blob.size < bestBlob.size) {
          bestBlob = blob;
          bestMimeType = mimeType;
        }

        if (blob.size <= MAX_UPLOAD_IMAGE_BYTES) {
          return new File(
            [blob],
            replaceFileExtension(file.name, extensionForMimeType(mimeType)),
            { type: mimeType }
          );
        }
      }
    }
  }

  if (bestBlob != null) {
    return new File(
      [bestBlob],
      replaceFileExtension(file.name, extensionForMimeType(bestMimeType)),
      { type: bestMimeType }
    );
  }

  return file;
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
