import { describe, it, expect } from "vitest";
import { parseSSEBuffer } from "./sse";

describe("parseSSEBuffer", () => {
  it("parses multiple data events", () => {
    const buffer =
      'data: {"type": "token", "text": "hello"}\n\n' +
      'data: {"type": "done", "job_id": 5}\n\n';
    const events = parseSSEBuffer(buffer);
    expect(events).toHaveLength(2);
    expect(events[0]).toEqual({ type: "token", text: "hello" });
    expect(events[1]).toEqual({ type: "done", job_id: 5 });
  });

  it("ignores malformed json", () => {
    const buffer = "data: not-json\n\n";
    expect(parseSSEBuffer(buffer)).toHaveLength(0);
  });

  it("parses data lines without a space after the colon (Spring SseEmitter format)", () => {
    const buffer =
      'data:{"type": "token", "text": "hi"}\n\n' +
      'data:{"type": "done", "job_id": 7}\n\n';
    const events = parseSSEBuffer(buffer);
    expect(events).toHaveLength(2);
    expect(events[0]).toEqual({ type: "token", text: "hi" });
    expect(events[1]).toEqual({ type: "done", job_id: 7 });
  });

  it("parses schema_proposal event", () => {
    const buffer = 'data: {"type": "schema_proposal", "target_table": "t", "columns": [{"name": "id", "type": "INT"}]}\n\n';
    const events = parseSSEBuffer(buffer);
    expect(events[0]).toMatchObject({ type: "schema_proposal", target_table: "t" });
  });
});
