import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { oneLight } from "react-syntax-highlighter/dist/esm/styles/prism";
import { message } from "antd";
import { BORDER, TEXT_PRIMARY, TEXT_SECONDARY } from "../../theme";

/**
 * 渲染助手消息为 Markdown（晴白风）：标题/列表/表格/引用，
 * 代码块带语言标签 + 复制按钮。
 *
 * 注意：react-markdown@9 已移除 `code` 组件的 `inline` 参数，
 * 因此用「是否带 language-* className」来区分块级代码与行内代码。
 */
export function MarkdownMessage({ content }: { content: string }) {
  return (
    <div className="markdown-body" style={{ lineHeight: 1.7, color: TEXT_PRIMARY, fontSize: 15 }}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          code({ className, children, ...props }: any) {
            const match = /language-(\w+)/.exec(className || "");
            // 无语言标记 → 当作行内代码（react-markdown@9 不再提供 inline 标志）
            if (!match) {
              return (
                <code
                  style={{ background: "#eef1ff", padding: "1px 6px", borderRadius: 4, fontSize: 13, color: "#3a4a6a" }}
                  {...props}
                >
                  {children}
                </code>
              );
            }
            const lang = match[1];
            const text = String(children).replace(/\n$/, "");
            return (
              <div style={{ margin: "10px 0", borderRadius: 8, overflow: "hidden", border: `1px solid ${BORDER}` }}>
                <div
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    background: "#f4f7ff",
                    color: TEXT_SECONDARY,
                    fontSize: 12,
                    padding: "5px 12px",
                    borderBottom: `1px solid ${BORDER}`,
                  }}
                >
                  <span>{lang}</span>
                  <a
                    onClick={() => {
                      navigator.clipboard?.writeText(text);
                      message.success("已复制");
                    }}
                    style={{ cursor: "pointer", color: TEXT_SECONDARY }}
                  >
                    复制
                  </a>
                </div>
                <SyntaxHighlighter
                  language={lang}
                  style={oneLight}
                  customStyle={{ margin: 0, fontSize: 13, background: "#fbfcff" }}
                >
                  {text}
                </SyntaxHighlighter>
              </div>
            );
          },
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
