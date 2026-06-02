import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { ProjectsPage } from "./ProjectsPage";
import * as projectsApi from "../api/projects";

vi.mock("../api/projects");

describe("ProjectsPage", () => {
  beforeEach(() => vi.clearAllMocks());

  it("renders project cards", async () => {
    vi.mocked(projectsApi.listProjects).mockResolvedValue([
      { id: 1, name: "用户统计", description: "desc", owner_id: 1, status: 2, created_at: "2026-05-31" },
    ]);
    render(
      <MemoryRouter>
        <ProjectsPage />
      </MemoryRouter>
    );
    await waitFor(() => expect(screen.getByText("用户统计")).toBeInTheDocument());
    expect(screen.getByText("进行中")).toBeInTheDocument();
  });

  it("shows new project button", async () => {
    vi.mocked(projectsApi.listProjects).mockResolvedValue([]);
    render(
      <MemoryRouter>
        <ProjectsPage />
      </MemoryRouter>
    );
    await waitFor(() => expect(screen.getByText("+ 新建项目")).toBeInTheDocument());
  });
});
