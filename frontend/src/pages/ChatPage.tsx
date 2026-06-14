import { useState, useEffect, useRef } from "react";
import { Layout, Input, Button, message as antdMessage, Spin } from "antd";
import { TableOutlined } from "@ant-design/icons";
import { useParams, useNavigate } from "react-router-dom";
import {
  createConversation,
  listConversations,
  sendChat,
  getMessages,
  confirmSchema,
  confirmSteps,
  getConversationTables,
} from "../api/conversations";
import { getProjectTablesWithDetails } from "../api/projects";
import { streamConversation } from "../api/sse";
import { SchemaConfirmCard } from "./chat/SchemaConfirmCard";
import { StepsConfirmCard } from "./chat/StepsConfirmCard";
import { SqlResultPanel, type GeneratedStep } from "./chat/SqlResultPanel";
import { ConversationSidebar } from "./chat/ConversationSidebar";
import { TableSelectModal } from "./chat/TableSelectModal";
import { SourceTableGraph } from "./chat/SourceTableGraph";
import type { Conversation, Message, SchemaColumn, EtlStepProposal, SSEEvent, TableDetailOut } from "../api/types";

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

  // Project-level tables (all tables associated with this project)
  const [projectTables, setProjectTables] = useState<TableDetailOut[]>([]);
  const [projectTablesLoading, setProjectTablesLoading] = useState(false);

  // Tables selected for the active conversation
  const [convTables, setConvTables] = useState<TableDetailOut[]>([]);

  // Modal states
  const [tableSelectOpen, setTableSelectOpen] = useState(false);
  const [graphOpen, setGraphOpen] = useState(false);

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

  const loadProjectTables = async () => {
    setProjectTablesLoading(true);
    try {
      const tables = await getProjectTablesWithDetails(projectId);
      setProjectTables(tables);
    } finally {
      setProjectTablesLoading(false);
    }
  };

  const selectConversation = async (cid: number) => {
    setActiveId(cid);
    setSchemaProposal(null);
    setStepsProposal(null);
    setGeneratedSteps([]);
    setJobId(null);
    const [msgs, tables] = await Promise.all([
      getMessages(cid),
      getConversationTables(cid),
    ]);
    setBubbles(
      msgs
        .filter((m) => m.role === "user" || m.role === "assistant")
        .map((m) => ({ role: m.role as "user" | "assistant", content: m.content }))
    );
    setConvTables(tables);
  };

  // Open table-select modal and lazy-load project tables
  const onNewConversationClick = async () => {
    if (projectTables.length === 0) {
      await loadProjectTables();
    }
    setTableSelectOpen(true);
  };

  const onTableSelectConfirm = async (selectedIds: number[]) => {
    setTableSelectOpen(false);
    const conv = await createConversation(projectId, selectedIds);
    setConversations((prev) => [conv, ...prev]);
    // conv.table_ids is the persisted list; fetch full details
    const tables = await getConversationTables(conv.id);
    setConvTables(tables);
    setActiveId(conv.id);
    setSchemaProposal(null);
    setStepsProposal(null);
    setGeneratedSteps([]);
    setJobId(null);
    setBubbles([]);
  };

  const runStream = (cid: number) => {
    setStreaming(true);
    let assistantText = "";
    abortRef.current = streamConversation(
      cid,
      (event: SSEEvent) => {
        switch (event.type) {
          case "token":
            assistantText += event.text;
            setBubbles((prev) => {
              const next = [...prev];
              if (next.length && next[next.length - 1].role === "assistant") {
                next[next.length - 1] = { role: "assistant", content: assistantText };
              } else {
                next.push({ role: "assistant", content: assistantText });
              }
              return next;
            });
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
          onNew={onNewConversationClick}
        />
      </Sider>

      <Content style={{ display: "flex", flexDirection: "column", background: "#f8faff", padding: 16 }}>
        {/* Toolbar: show source tables button */}
        <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 8 }}>
          <Button
            icon={<TableOutlined />}
            onClick={() => setGraphOpen(true)}
            disabled={activeId === null}
          >
            显示源表
            {convTables.length > 0 && (
              <span style={{ marginLeft: 6, color: "#4361ee", fontWeight: 600 }}>({convTables.length})</span>
            )}
          </Button>
        </div>

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
                {b.content}
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

      {/* Table selection modal (shown when creating a new conversation) */}
      <TableSelectModal
        open={tableSelectOpen}
        projectTables={projectTables}
        loading={projectTablesLoading}
        initialSelected={[]}
        onConfirm={onTableSelectConfirm}
        onCancel={() => setTableSelectOpen(false)}
      />

      {/* UAM relationship graph modal */}
      <SourceTableGraph
        open={graphOpen}
        tables={convTables}
        onClose={() => setGraphOpen(false)}
      />
    </Layout>
  );
}
