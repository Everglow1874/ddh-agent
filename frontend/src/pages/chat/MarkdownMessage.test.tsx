import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { MarkdownMessage } from "./MarkdownMessage";

describe("MarkdownMessage", () => {
  it("renders markdown headings and list items", () => {
    render(<MarkdownMessage content={"# 标题\n\n- 项目1\n- 项目2"} />);
    expect(screen.getByText("标题")).toBeInTheDocument();
    expect(screen.getByText("项目1")).toBeInTheDocument();
    expect(screen.getByText("项目2")).toBeInTheDocument();
  });

  it("renders a code block with copy button", () => {
    const { container } = render(<MarkdownMessage content={"```sql\nSELECT 1;\n```"} />);
    expect(screen.getByText("复制")).toBeInTheDocument();
    expect(screen.getByText("sql")).toBeInTheDocument();
    // 代码被高亮拆成多个 token span，断言整体文本包含 SQL 内容
    expect(container.textContent).toContain("SELECT 1");
  });

  it("renders inline code as inline (no code-block chrome)", () => {
    // 回归：react-markdown@9 移除了 inline 参数，行内代码不应渲染成带「复制」的代码块
    const { container } = render(<MarkdownMessage content={"用 `SELECT` 关键字"} />);
    expect(screen.queryByText("复制")).toBeNull();
    expect(container.querySelector("code")?.textContent).toBe("SELECT");
  });

  it("renders GFM tables", () => {
    const md = "| 列A | 列B |\n| --- | --- |\n| 1 | 2 |";
    const { container } = render(<MarkdownMessage content={md} />);
    expect(container.querySelector("table")).not.toBeNull();
    expect(screen.getByText("列A")).toBeInTheDocument();
  });
});
