function readApiBaseUrl(): string {
  const value = import.meta.env.VITE_API_BASE_URL;
  if (!value) {
    throw new Error("VITE_API_BASE_URL is not configured.");
  }
  return value;
}

export const apiBaseUrl = readApiBaseUrl();
