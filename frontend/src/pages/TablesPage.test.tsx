import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { TablesPage } from "./TablesPage";
import * as tablesApi from "../api/tables";

vi.mock("../api/tables");

const fakeTable = { id: 1, name: "dw_order", description: "订单表", scope: 1, owner_id: null, created_at: "2026-05-31T00:00:00" };
const fakePage = { content: [fakeTable], total: 1, page: 1, size: 20 };

describe("TablesPage", () => {
  beforeEach(() => vi.clearAllMocks());

  it("loads and displays public tables with actions", async () => {
    vi.mocked(tablesApi.listTablesPage).mockResolvedValue(fakePage);
    render(<TablesPage />);
    await waitFor(() => expect(screen.getByText("dw_order")).toBeInTheDocument());
    expect(tablesApi.listTablesPage).toHaveBeenCalledWith("public", "", 1, 20);
    expect(screen.getByText("查看")).toBeInTheDocument();
    expect(screen.getByText("编辑")).toBeInTheDocument();
    expect(screen.getByText("删除")).toBeInTheDocument();
  });
});
