import { useState, useEffect, useRef } from "react";
import { Layout, Input, Button, message as antdMessage, Spin } from "antd";
import { useParams, useNavigate } from "react-router-dom";
import {
  createConversation,
  listConversations,
  sendChat,
  getMessages,
  confirmSchema,
  confirmSteps,
} from "../api/conversations";
import { streamConversation } from "../api/sse";
import { SchemaConfirmCard } from "./chat/SchemaConfirmCard";
import { StepsConfirmCard } from "./chat/StepsConfirmCard";
import { SqlResultPanel, type GeneratedStep } from "./chat/SqlResultPanel";
import { ConversationSidebar } from "./chat/ConversationSidebar";
import { MarkdownMessage } from "./chat/MarkdownMessage";
import type { Conversation, Message, SchemaColumn, EtlStepProposal, SSEEvent } from "../api/types";

const { Sider, Content } = Layout;

interface ChatBubble {
  role: "user" | "assistant";
  content: string;
}

export function ChatPage() {
  const { id } = useParams<{ id: string }>();
  const projectId = Number(id);
  const navigate = useNavigate();

  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeId, setActiveId] = useState<number | null>(null);
  const [bubbles, setBubbles] = useState<ChatBubble[]>([]);
  const [input, setInput] = useState("");
  const [streaming, setStreaming] = useState(false);
  const [schemaProposal, setSchemaProposal] = useState<{ target_table: string; columns: SchemaColumn[] } | null>(null);
  const [stepsProposal, setStepsProposal] = useState<EtlStepProposal[] | null>(null);
  const [generatedSteps, setGeneratedSteps] = useState<GeneratedStep[]>([]);
  const [jobId, setJobId] = useState<number | null>(null);
  const abortRef = useRef<(() => void) | null>(null);

  useEffect(() => {
    (async () => {
      const convs = await listConversations(projectId);
      setConversations(convs);
      if (convs.length > 0) {
        selectConversation(convs[0].id);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId]);

  const selectConversation = async (cid: number) => {
    setActiveId(cid);
    setSchemaProposal(null);
    setStepsProposal(null);
    setGeneratedSteps([]);
    setJobId(null);
    const msgs: Message[] = await getMessages(cid);
    setBubbles(
      msgs
        .filter((m) => m.role === "user" || m.role === "assistant")
        .map((m) => ({ role: m.role as "user" | "assistant", content: m.content }))
    );
  };

  const onNewConversation = async () => {
    const conv = await createConversation(projectId);
    setConversations((prev) => [conv, ...prev]);
    selectConversation(conv.id);
  };

  const runStream = (cid: number) => {
    setStreaming(true);
    let bubbleOpen = false;
    abortRef.current = streamConversation(
      cid,
      (event: SSEEvent) => {
        switch (event.type) {
          case "token":
            setBubbles((prev) => {
              const next = [...prev];
              if (bubbleOpen && next.length && next[next.length - 1].role === "assistant") {
                next[next.length - 1] = {
                  role: "assistant",
                  content: next[next.length - 1].content + event.text,
                };
              } else {
                next.push({ role: "assistant", content: event.text });
              }
              return next;
            });
            bubbleOpen = true;
            break;
          case "turn_end":
            bubbleOpen = false;
            break;
          case "schema_proposal":
            setSchemaProposal({ target_table: event.target_table, columns: event.columns });
            break;
          case "steps_proposal":
            setStepsProposal(event.steps);
            break;
          case "step_generated":
            setGeneratedSteps((prev) => [
              ...prev,
              { step_order: event.step_order, step_name: event.step_name, sql: event.sql },
            ]);
            break;
          case "done":
            setJobId(event.job_id);
            antdMessage.success("ETL 作业已生成");
            break;
          case "error":
            antdMessage.error(event.message);
            break;
          case "stream_end":
          case "end":
            setStreaming(false);
            break;
        }
      },
      (err) => {
        antdMessage.error(err.message);
        setStreaming(false);
      }
    );
  };

  const onSend = async () => {
    if (!input.trim() || activeId === null) return;
    const text = input.trim();
    setInput("");
    setBubbles((prev) => [...prev, { role: "user", content: text }]);
    await sendChat(activeId, text);
    runStream(activeId);
  };

  const onConfirmSchema = async () => {
    if (!schemaProposal || activeId === null) return;
    await confirmSchema(activeId, schemaProposal.target_table, schemaProposal.columns);
    setSchemaProposal(null);
    runStream(activeId);
  };

  const onConfirmSteps = async () => {
    if (!stepsProposal || activeId === null) return;
    await confirmSteps(activeId, stepsProposal);
    setStepsProposal(null);
    runStream(activeId);
  };

  useEffect(() => () => abortRef.current?.(), []);

  return (
    <Layout style={{ height: "100vh" }}>
      <Sider width={200} style={{ background: "#161622" }}>
        <div style={{ padding: 8 }}>
          <Button type="link" onClick={() => navigate(`/projects/${projectId}`)} style={{ color: "#fff" }}>← 返回项目</Button>
        </div>
        <ConversationSidebar
          conversations={conversations}
          activeId={activeId}
          onSelect={selectConversation}
          onNew={onNewConversation}
        />
      </Sider>

      <Content style={{ display: "flex", flexDirection: "column", background: "#f8faff", padding: 16 }}>
        <div style={{ flex: 1, overflowY: "auto", marginBottom: 12 }}>
          {bubbles.map((b, i) => (
            <div
              key={i}
              style={{ display: "flex", justifyContent: b.role === "user" ? "flex-end" : "flex-start", marginBottom: 8 }}
            >
              <div
                style={{
                  maxWidth: "70%",
                  padding: "8px 12px",
                  borderRadius: 12,
                  background: b.role === "user" ? "#4361ee" : "#fff",
                  color: b.role === "user" ? "#fff" : "#1a2a4a",
                  border: b.role === "assistant" ? "1px solid #e8eef8" : undefined,
                }}
              >
                {b.role === "assistant" ? <MarkdownMessage content={b.content} /> : b.content}
              </div>
            </div>
          ))}
          {schemaProposal && (
            <SchemaConfirmCard
              targetTable={schemaProposal.target_table}
              columns={schemaProposal.columns}
              onConfirm={onConfirmSchema}
              onEdit={() => setSchemaProposal(null)}
              disabled={streaming}
            />
          )}
          {stepsProposal && (
            <StepsConfirmCard
              steps={stepsProposal}
              onConfirm={onConfirmSteps}
              onEdit={() => setStepsProposal(null)}
              disabled={streaming}
            />
          )}
          {streaming && <Spin style={{ display: "block", margin: "8px auto" }} />}
        </div>
        <Input.Search
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onSearch={onSend}
          enterButton="发送"
          placeholder="描述你的取数需求..."
          disabled={streaming || activeId === null}
        />
      </Content>

      <Sider width={320} style={{ background: "#fff", borderLeft: "1px solid #e8eef8", padding: 12 }}>
        <h3>SQL 结果</h3>
        <SqlResultPanel steps={generatedSteps} jobId={jobId} />
      </Sider>
    </Layout>
  );
}
