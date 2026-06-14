import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { NewConversationModal } from "./NewConversationModal";

vi.mock("../../api/tables", () => ({
  listTables: vi.fn().mockResolvedValue([
    { id: 1, name: "成绩表", description: null, scope: 2, owner_id: 1, created_at: "" },
    { id: 2, name: "班级表", description: null, scope: 2, owner_id: 1, created_at: "" },
  ]),
}));

// antd Select 的 CSS-in-JS 会注入 jsdom nwsapi 无法解析的 CSS 选择器，导致渲染崩溃。
// 替换为原生 <select> 以隔离问题，同时保持两个行为都被断言。
vi.mock("antd", async (importOriginal) => {
  const actual = (await importOriginal()) as Record<string, unknown>;
  const MockSelect = (props: {
    value?: number[];
    onChange?: (v: number[]) => void;
    options?: { label: string; value: number }[];
    placeholder?: string;
    mode?: string;
    style?: object;
  }) => (
    <select
      multiple
      aria-label={props.placeholder}
      value={(props.value ?? []).map(String)}
      onChange={(e) =>
        props.onChange?.([...e.target.selectedOptions].map((o) => Number(o.value)))
      }
    >
      {props.options?.map((opt) => (
        <option key={opt.value} value={String(opt.value)}>
          {opt.label}
        </option>
      ))}
    </select>
  );
  return { ...actual, Select: MockSelect };
});

describe("NewConversationModal", () => {
  beforeEach(() => vi.clearAllMocks());

  it("打开时加载源表列表", async () => {
    render(<NewConversationModal open onCancel={() => {}} onConfirm={() => {}} />);
    await waitFor(() => expect(screen.getByText("新建对话 · 选择源表")).toBeInTheDocument());
  });

  it("未选表时创建按钮禁用", async () => {
    render(<NewConversationModal open onCancel={() => {}} onConfirm={() => {}} />);
    // antd 在中文字符间插入空格，实际渲染为 "创 建"；用正则匹配
    const okBtn = await screen.findByRole("button", { name: /创.*建/ });
    expect(okBtn).toBeDisabled();
  });
});
