import { apiBaseUrl } from "../config";

export type Subscription = {
  id: string;
  url: string;
  addedAt: string;
};

export type AddSubscriptionResult =
  | { kind: "success" }
  | { kind: "rejected"; message: string }
  | { kind: "unavailable" };

export const ADD_REJECTED_FALLBACK = "The subscription could not be added.";
export const ADD_UNAVAILABLE_MESSAGE =
  "Could not add the subscription. Please try again.";

const SUBSCRIPTIONS_PATH = "subscriptions";
const HTTP_CREATED = 201;
/** Invalid address (400) or duplicate (409): the server explains why in `detail`. */
const REJECTION_STATUSES = new Set([400, 409]);

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === "AbortError";
}

/** Reads `detail` from an RFC 9457 problem response, or `null` if absent or unreadable. */
async function readProblemDetail(response: Response): Promise<string | null> {
  try {
    const body: unknown = await response.json();
    if (typeof body === "object" && body !== null && "detail" in body) {
      const { detail } = body;
      return typeof detail === "string" && detail.length > 0 ? detail : null;
    }
    return null;
  } catch {
    return null;
  }
}

export function createSubscriptionsApi(
  baseUrl: string,
  fetchFn: typeof fetch = fetch,
) {
  const subscriptionsUrl = new URL(SUBSCRIPTIONS_PATH, baseUrl).toString();

  return {
    /** Returns the list, oldest first, or `null` when it cannot be loaded. */
    async getSubscriptions(
      signal?: AbortSignal,
    ): Promise<Subscription[] | null> {
      try {
        const response = await fetchFn(subscriptionsUrl, {
          headers: { Accept: "application/json" },
          signal,
        });
        if (!response.ok) {
          return null;
        }
        const body: unknown = await response.json();
        return Array.isArray(body) ? (body as Subscription[]) : null;
      } catch (error) {
        if (isAbortError(error)) {
          throw error;
        }
        return null;
      }
    },

    async addSubscription(url: string): Promise<AddSubscriptionResult> {
      try {
        const response = await fetchFn(subscriptionsUrl, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ url }),
        });
        if (response.status === HTTP_CREATED) {
          return { kind: "success" };
        }
        if (REJECTION_STATUSES.has(response.status)) {
          return {
            kind: "rejected",
            message:
              (await readProblemDetail(response)) ?? ADD_REJECTED_FALLBACK,
          };
        }
        return { kind: "unavailable" };
      } catch {
        return { kind: "unavailable" };
      }
    },
  };
}

export const subscriptionsApi = createSubscriptionsApi(apiBaseUrl);
