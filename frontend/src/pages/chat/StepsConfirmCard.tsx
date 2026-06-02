import { Card, Table, Button, Space, Tag } from "antd";
import type { EtlStepProposal } from "../../api/types";

interface Props {
  steps: EtlStepProposal[];
  onConfirm: () => void;
  onEdit: () => void;
  disabled?: boolean;
}

export function StepsConfirmCard({ steps, onConfirm, onEdit, disabled }: Props) {
  return (
    <Card
      title="📋 建议的 ETL 步骤计划"
      style={{ background: "#fffbec", border: "1px solid #f0c040", marginBottom: 12 }}
      size="small"
    >
      <Table
        rowKey="step_order"
        size="small"
        pagination={false}
        dataSource={steps}
        columns={[
          { title: "#", dataIndex: "step_order", key: "step_order", width: 50 },
          { title: "步骤", dataIndex: "step_name", key: "step_name" },
          { title: "说明", dataIndex: "description", key: "description" },
          {
            title: "输出表",
            key: "output",
            render: (_: unknown, r: EtlStepProposal) => (
              <span>
                {r.output_table} {r.is_temp_table && <Tag color="orange">临时表</Tag>}
              </span>
            ),
          },
        ]}
      />
      <Space style={{ marginTop: 12 }}>
        <Button type="primary" onClick={onConfirm} disabled={disabled}>✓ 确认</Button>
        <Button onClick={onEdit} disabled={disabled}>✏ 修改</Button>
      </Space>
    </Card>
  );
}
