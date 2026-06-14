import { useState, useEffect } from "react";
import { Tabs, Button, Table, Space, Card } from "antd";
import { useParams, useNavigate } from "react-router-dom";
import { getProject } from "../api/projects";
import { listConversations } from "../api/conversations";
import { listJobs } from "../api/jobs";
import type { Project, EtlJob, Conversation } from "../api/types";

export function ProjectDetailPage() {
  const { id } = useParams<{ id: string }>();
  const projectId = Number(id);
  const navigate = useNavigate();
  const [project, setProject] = useState<Project | null>(null);
  const [jobs, setJobs] = useState<EtlJob[]>([]);
  const [conversations, setConversations] = useState<Conversation[]>([]);

  useEffect(() => {
    (async () => {
      setProject(await getProject(projectId));
      setJobs(await listJobs(projectId));
      setConversations(await listConversations(projectId));
    })();
  }, [projectId]);

  const jobColumns = [
    { title: "目标表", dataIndex: "target_table", key: "target_table" },
    { title: "步骤数", key: "steps", render: (_: unknown, r: EtlJob) => r.steps.length },
    { title: "创建时间", dataIndex: "created_at", key: "created_at" },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16, justifyContent: "space-between", width: "100%" }}>
        <h2 style={{ margin: 0 }}>{project?.name}</h2>
        <Button type="primary" onClick={() => navigate(`/projects/${projectId}/chat`)}>进入 Agent 对话</Button>
      </Space>
      <Tabs
        items={[
          {
            key: "conversations",
            label: "对话记录",
            children: (
              <Space direction="vertical" style={{ width: "100%" }}>
                {conversations.map((c) => (
                  <Card key={c.id} size="small" hoverable onClick={() => navigate(`/projects/${projectId}/chat`)}>
                    对话 #{c.id} · 状态 {c.state} · {c.created_at}
                  </Card>
                ))}
              </Space>
            ),
          },
          {
            key: "jobs",
            label: "ETL 作业",
            children: <Table rowKey="id" dataSource={jobs} columns={jobColumns} />,
          },
        ]}
      />
    </div>
  );
}
