import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { ChatPage } from "./ChatPage";
import * as convApi from "../api/conversations";
import * as projectsApi from "../api/projects";

vi.mock("../api/conversations");
vi.mock("../api/projects");
vi.mock("../api/sse");

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
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(projectsApi.getProjectTablesWithDetails).mockResolvedValue([]);
  });

  it("loads conversations on mount", async () => {
    vi.mocked(convApi.listConversations).mockResolvedValue([
      { id: 7, project_id: 1, state: 1, created_at: "2026-05-31", table_ids: [] },
    ]);
    vi.mocked(convApi.getMessages).mockResolvedValue([]);
    vi.mocked(convApi.getConversationTables).mockResolvedValue([]);
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
