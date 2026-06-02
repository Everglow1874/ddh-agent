import { getToken } from "./client";
import type { SSEEvent } from "./types";

/**
 * Open an SSE stream to the conversation endpoint with the JWT header.
 * Calls onEvent for each parsed event. Returns an abort function.
 *
 * EventSource cannot send an Authorization header, so we use fetch with a
 * ReadableStream reader and parse the SSE wire format manually.
 */
export function streamConversation(
  conversationId: number,
  onEvent: (event: SSEEvent) => void,
  onError?: (err: Error) => void
): () => void {
  const controller = new AbortController();

  (async () => {
    try {
      const resp = await fetch(`/api/conversations/${conversationId}/stream`, {
        headers: { Authorization: `Bearer ${getToken() ?? ""}` },
        signal: controller.signal,
      });
      if (!resp.ok || !resp.body) {
        throw new Error(`Stream failed: ${resp.status}`);
      }
      const reader = resp.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const chunks = buffer.split("\n\n");
        buffer = chunks.pop() ?? "";
        for (const chunk of chunks) {
          const line = chunk.split("\n").find((l) => l.startsWith("data: "));
          if (!line) continue;
          const json = line.slice("data: ".length);
          try {
            onEvent(JSON.parse(json) as SSEEvent);
          } catch {
            // ignore malformed chunk
          }
        }
      }
    } catch (err) {
      if ((err as Error).name !== "AbortError") {
        onError?.(err as Error);
      }
    }
  })();

  return () => controller.abort();
}

/** Parse a raw SSE text buffer into events (exported for testing). */
export function parseSSEBuffer(buffer: string): SSEEvent[] {
  const events: SSEEvent[] = [];
  for (const chunk of buffer.split("\n\n")) {
    const line = chunk.split("\n").find((l) => l.startsWith("data: "));
    if (!line) continue;
    try {
      events.push(JSON.parse(line.slice("data: ".length)) as SSEEvent);
    } catch {
      // ignore
    }
  }
  return events;
}
