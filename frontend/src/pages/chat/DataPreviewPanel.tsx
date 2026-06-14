import { useState, useEffect } from "react";
import { Collapse, Table, Empty, Tag, Spin } from "antd";
import { getProjectTables } from "../../api/projects";
import type { ProjectTablesResult } from "../../api/types";
import { PRIMARY, TEXT_PRIMARY, TEXT_SECONDARY } from "../../theme";

/** 对话页左栏：本次项目勾选的表结构 + 表间关联预览。 */
export function DataPreviewPanel({ projectId }: { projectId: number }) {
  const [data, setData] = useState<ProjectTablesResult | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      setLoading(true);
      try {
        setData(await getProjectTables(projectId));
      } finally {
        setLoading(false);
      }
    })();
  }, [projectId]);

  if (loading) return <Spin size="small" style={{ display: "block", margin: "12px auto" }} />;
  if (!data || data.tables.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未关联原表" style={{ margin: "12px 0" }} />;
  }

  const nameById = (id: number) => data.tables.find((t) => t.id === id)?.name ?? `表#${id}`;

  return (
    <div style={{ fontSize: 12 }}>
      <Collapse
        size="small"
        ghost
        items={data.tables.map((t) => ({
          key: String(t.id),
          label: <span style={{ color: PRIMARY, fontWeight: 500 }}>{t.name}</span>,
          children: (
            <Table
              rowKey="id"
              size="small"
              pagination={false}
              showHeader={false}
              dataSource={t.columns}
              columns={[
                { dataIndex: "column_name", key: "n", render: (v: string) => <span style={{ color: TEXT_PRIMARY }}>{v}</span> },
                { dataIndex: "data_type", key: "t", render: (v: string) => <span style={{ color: TEXT_SECONDARY }}>{v}</span> },
                { dataIndex: "comment", key: "c", render: (v: string | null) => v ? <span style={{ color: "#6a7a9a" }}>{v}</span> : null },
              ]}
            />
          ),
        }))}
      />
      {data.relations.length > 0 && (
        <div style={{ padding: "8px 10px", color: TEXT_SECONDARY }}>
          <div style={{ marginBottom: 4, color: TEXT_SECONDARY }}>关联关系</div>
          {data.relations.map((r) => (
            <div key={r.id} style={{ marginBottom: 2 }}>
              <Tag color="blue" style={{ fontSize: 11 }}>{nameById(r.from_table_id)}.{r.from_column}</Tag>
              ↔
              <Tag color="green" style={{ fontSize: 11 }}>{nameById(r.to_table_id)}.{r.to_column}</Tag>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
