import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { TablesPage } from "./TablesPage";
import * as tablesApi from "../api/tables";

vi.mock("../api/tables");

describe("TablesPage", () => {
  beforeEach(() => vi.clearAllMocks());

  it("loads and displays public tables", async () => {
    vi.mocked(tablesApi.listTables).mockResolvedValue([
      { id: 1, name: "dw_order", description: "订单表", scope: 1, owner_id: null, created_at: "2026-05-31T00:00:00" },
    ]);
    render(<TablesPage />);
    await waitFor(() => expect(screen.getByText("dw_order")).toBeInTheDocument());
    expect(tablesApi.listTables).toHaveBeenCalledWith("public");
  });

  it("shows import button", async () => {
    vi.mocked(tablesApi.listTables).mockResolvedValue([]);
    render(<TablesPage />);
    await waitFor(() => expect(screen.getByText("+ 导入 CSV")).toBeInTheDocument());
  });
});
