import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  ADD_UNAVAILABLE_MESSAGE,
  subscriptionsApi,
  type Subscription,
} from "../api/subscriptionsApi";
import "./SubscriptionsPage.css";

type LoadState = "loading" | "loaded" | "failed";

export function SubscriptionsPage() {
  const [newUrl, setNewUrl] = useState("");
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  // State updates are async; the ref blocks a second submit fired before re-render (FR-012).
  const inFlight = useRef(false);

  useEffect(() => {
    const controller = new AbortController();
    subscriptionsApi
      .getSubscriptions(controller.signal)
      .then((list) => {
        if (list === null) {
          setLoadState("failed");
        } else {
          setSubscriptions(list);
          setLoadState("loaded");
        }
      })
      .catch(() => {
        // Aborted on unmount; nothing to update.
      });
    return () => controller.abort();
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (inFlight.current) {
      return;
    }
    inFlight.current = true;
    setIsSubmitting(true);
    setErrorMessage(null);
    try {
      const result = await subscriptionsApi.addSubscription(newUrl);
      switch (result.kind) {
        case "success": {
          setNewUrl("");
          // The API is the source of truth: re-read the list instead of appending locally.
          const latest = await subscriptionsApi.getSubscriptions();
          if (latest !== null) {
            setSubscriptions(latest);
            setLoadState("loaded");
          }
          break;
        }
        case "rejected":
          // Keep the typed text so the user can correct it (FR-004).
          setErrorMessage(result.message);
          break;
        case "unavailable":
          setErrorMessage(ADD_UNAVAILABLE_MESSAGE);
          break;
      }
    } finally {
      inFlight.current = false;
      setIsSubmitting(false);
    }
  }

  return (
    <main>
      <h1>Subscriptions</h1>

      <form className="add-form" onSubmit={handleSubmit}>
        <label htmlFor="feed-url">Feed URL</label>
        <input
          id="feed-url"
          type="text"
          value={newUrl}
          onChange={(event) => setNewUrl(event.target.value)}
          placeholder="Paste a feed URL"
          autoComplete="off"
        />
        <button type="submit" disabled={isSubmitting}>
          Add
        </button>
      </form>

      {errorMessage !== null && (
        <p role="alert" className="error">
          {errorMessage}
        </p>
      )}

      <SubscriptionList loadState={loadState} subscriptions={subscriptions} />
    </main>
  );
}

function SubscriptionList({
  loadState,
  subscriptions,
}: {
  loadState: LoadState;
  subscriptions: Subscription[];
}) {
  if (loadState === "loading") {
    return <p>Loading…</p>;
  }
  if (loadState === "failed") {
    return <p>Could not load subscriptions.</p>;
  }
  if (subscriptions.length === 0) {
    return <p>No subscriptions yet</p>;
  }
  return (
    <ul className="subscription-list">
      {subscriptions.map((subscription) => (
        <li key={subscription.id}>{subscription.url}</li>
      ))}
    </ul>
  );
}
