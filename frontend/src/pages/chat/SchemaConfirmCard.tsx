import { Card, Table, Button, Space } from "antd";
import type { SchemaColumn } from "../../api/types";

interface Props {
  targetTable: string;
  columns: SchemaColumn[];
  onConfirm: () => void;
  onEdit: () => void;
  disabled?: boolean;
}

export function SchemaConfirmCard({ targetTable, columns, onConfirm, onEdit, disabled }: Props) {
  return (
    <Card
      title={`📋 建议目标表结构：${targetTable}`}
      style={{ background: "#fffbec", border: "1px solid #f0c040", marginBottom: 12 }}
      size="small"
    >
      <Table
        rowKey="name"
        size="small"
        pagination={false}
        dataSource={columns}
        columns={[
          { title: "字段", dataIndex: "name", key: "name" },
          { title: "类型", dataIndex: "type", key: "type" },
          { title: "注释", dataIndex: "comment", key: "comment", render: (v?: string) => v ?? "-" },
        ]}
      />
      <Space style={{ marginTop: 12 }}>
        <Button type="primary" onClick={onConfirm} disabled={disabled}>✓ 确认</Button>
        <Button onClick={onEdit} disabled={disabled}>✏ 修改</Button>
      </Space>
    </Card>
  );
}
