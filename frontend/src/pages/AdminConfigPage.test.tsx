import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { AdminConfigPage } from "./AdminConfigPage";
import * as adminApi from "../api/admin";

vi.mock("../api/admin");

describe("AdminConfigPage", () => {
  beforeEach(() => vi.clearAllMocks());

  it("loads and displays current config", async () => {
    vi.mocked(adminApi.getConfig).mockResolvedValue({ provider: "claude", model: "claude-sonnet-4-6" });
    render(<AdminConfigPage />);
    await waitFor(() => expect(adminApi.getConfig).toHaveBeenCalled());
    expect(await screen.findByDisplayValue("claude-sonnet-4-6")).toBeInTheDocument();
  });
});
