import { describe, expect, it, vi } from "vitest";
import {
  ADD_REJECTED_FALLBACK,
  createSubscriptionsApi,
  type Subscription,
} from "./subscriptionsApi";

const BASE_URL = "http://api.test/api/";
const SUBSCRIPTIONS_URL = `${BASE_URL}subscriptions`;

const sample: Subscription = {
  id: "6f1c2b0e-3a4d-4c55-9a8e-2f7d1b0c9e11",
  url: "https://devblogs.microsoft.com/dotnet/feed/",
  addedAt: "2026-10-24T09:15:02.123Z",
};

function jsonResponse(
  status: number,
  body: unknown,
  contentType = "application/json",
) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": contentType },
  });
}

function apiWith(fetchFn: typeof fetch) {
  return createSubscriptionsApi(BASE_URL, fetchFn);
}

describe("getSubscriptions", () => {
  it("returns the array from a 200 response", async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse(200, [sample]));

    await expect(apiWith(fetchFn).getSubscriptions()).resolves.toEqual([
      sample,
    ]);
    expect(fetchFn).toHaveBeenCalledWith(SUBSCRIPTIONS_URL, expect.anything());
  });

  it("returns null on a server error", async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse(500, {}));

    await expect(apiWith(fetchFn).getSubscriptions()).resolves.toBeNull();
  });

  it("returns null when the network request fails", async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockRejectedValue(new TypeError("Failed to fetch"));

    await expect(apiWith(fetchFn).getSubscriptions()).resolves.toBeNull();
  });

  it("returns null when the body is not an array", async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse(200, { items: [] }));

    await expect(apiWith(fetchFn).getSubscriptions()).resolves.toBeNull();
  });
});

describe("addSubscription", () => {
  const problemJson = "application/problem+json";

  it("returns success on 201 and posts the url as JSON", async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse(201, sample));

    await expect(
      apiWith(fetchFn).addSubscription("https://example.com/feed"),
    ).resolves.toEqual({ kind: "success" });

    const [url, init] = fetchFn.mock.calls[0];
    expect(url).toBe(SUBSCRIPTIONS_URL);
    expect(init?.method).toBe("POST");
    expect(JSON.parse(String(init?.body))).toEqual({
      url: "https://example.com/feed",
    });
  });

  it.each([400, 409])(
    "returns rejected with the problem detail on %i",
    async (status) => {
      const fetchFn = vi
        .fn<typeof fetch>()
        .mockResolvedValue(
          jsonResponse(status, { detail: "Specific reason." }, problemJson),
        );

      await expect(apiWith(fetchFn).addSubscription("x")).resolves.toEqual({
        kind: "rejected",
        message: "Specific reason.",
      });
    },
  );

  it("falls back to a generic rejection when detail is missing", async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse(400, { title: "Bad" }, problemJson));

    await expect(apiWith(fetchFn).addSubscription("x")).resolves.toEqual({
      kind: "rejected",
      message: ADD_REJECTED_FALLBACK,
    });
  });

  it("falls back to a generic rejection when the body is not JSON", async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(new Response("<html>oops</html>", { status: 400 }));

    await expect(apiWith(fetchFn).addSubscription("x")).resolves.toEqual({
      kind: "rejected",
      message: ADD_REJECTED_FALLBACK,
    });
  });

  it("returns unavailable on a server error", async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse(500, {}, problemJson));

    await expect(apiWith(fetchFn).addSubscription("x")).resolves.toEqual({
      kind: "unavailable",
    });
  });

  it("returns unavailable when the network request fails", async () => {
    const fetchFn = vi
      .fn<typeof fetch>()
      .mockRejectedValue(new TypeError("Failed to fetch"));

    await expect(apiWith(fetchFn).addSubscription("x")).resolves.toEqual({
      kind: "unavailable",
    });
  });
});
