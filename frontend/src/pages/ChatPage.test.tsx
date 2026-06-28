import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { ChatPage } from "./ChatPage";
import * as convApi from "../api/conversations";

vi.mock("../api/conversations", () => ({
  createConversation: vi.fn(),
  listConversations: vi.fn(),
  sendChat: vi.fn(),
  getMessages: vi.fn(),
  getConversationTables: vi.fn().mockResolvedValue([]),
  confirmSchema: vi.fn(),
  confirmSteps: vi.fn(),
  updateConversation: vi.fn(),
  deleteConversation: vi.fn(),
}));
vi.mock("../api/sse");
vi.mock("../api/jobs", () => ({
  getConversationJob: vi.fn().mockResolvedValue({ job_id: null, steps: [] }),
}));

function renderChat() {
  return render(
    <MemoryRouter initialEntries={["/projects/1/chat"]}>
      <Routes>
        <Route path="/projects/:id/chat" element={<ChatPage />} />
      </Routes>
    </MemoryRouter>
  );
}

describe("ChatPage", () => {
  beforeEach(() => vi.clearAllMocks());

  it("loads conversations on mount", async () => {
    vi.mocked(convApi.listConversations).mockResolvedValue([
      { id: 7, project_id: 1, name: "对话 #7", state: 1, created_at: "2026-05-31", table_ids: [] },
    ]);
    vi.mocked(convApi.getMessages).mockResolvedValue([]);
    renderChat();
    await waitFor(() => expect(convApi.listConversations).toHaveBeenCalledWith(1));
    expect(await screen.findByText("对话 #7")).toBeInTheDocument();
  });

  it("shows new conversation button and SQL panel placeholder", async () => {
    vi.mocked(convApi.listConversations).mockResolvedValue([]);
    renderChat();
    await waitFor(() => expect(screen.getByText("+ 新对话")).toBeInTheDocument());
    expect(screen.getByText("SQL 将在生成后显示")).toBeInTheDocument();
  });
});
