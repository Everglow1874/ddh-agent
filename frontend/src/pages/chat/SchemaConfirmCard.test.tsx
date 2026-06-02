import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SchemaConfirmCard } from "./SchemaConfirmCard";

describe("SchemaConfirmCard", () => {
  const columns = [{ name: "user_id", type: "VARCHAR(64)", comment: "用户ID" }];

  it("renders target table and columns", () => {
    render(<SchemaConfirmCard targetTable="result_tbl" columns={columns} onConfirm={() => {}} onEdit={() => {}} />);
    expect(screen.getByText(/result_tbl/)).toBeInTheDocument();
    expect(screen.getByText("user_id")).toBeInTheDocument();
  });

  it("calls onConfirm when confirm clicked", async () => {
    const onConfirm = vi.fn();
    render(<SchemaConfirmCard targetTable="t" columns={columns} onConfirm={onConfirm} onEdit={() => {}} />);
    await userEvent.click(screen.getByText("✓ 确认"));
    expect(onConfirm).toHaveBeenCalled();
  });
});
