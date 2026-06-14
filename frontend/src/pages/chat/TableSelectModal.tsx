import { useState } from "react";
import { Modal, Checkbox, Empty, Spin, Tag, Typography } from "antd";
import type { TableDetailOut } from "../../api/types";

const { Text } = Typography;

interface Props {
  open: boolean;
  projectTables: TableDetailOut[];
  loading: boolean;
  initialSelected: number[];
  onConfirm: (selectedIds: number[]) => void;
  onCancel: () => void;
}

export function TableSelectModal({ open, projectTables, loading, initialSelected, onConfirm, onCancel }: Props) {
  const [selected, setSelected] = useState<number[]>(initialSelected);

  const toggle = (id: number, checked: boolean) => {
    setSelected((prev) => (checked ? [...prev, id] : prev.filter((x) => x !== id)));
  };

  const handleOk = () => {
    onConfirm(selected);
  };

  // Reset selection when modal opens with new initialSelected
  const handleOpenChange = (visible: boolean) => {
    if (visible) setSelected(initialSelected);
  };

  return (
    <Modal
      title="选择本次对话使用的源表"
      open={open}
      onOk={handleOk}
      onCancel={onCancel}
      okText="确认"
      cancelText="取消"
      width={600}
      afterOpenChange={handleOpenChange}
    >
      {loading ? (
        <Spin style={{ display: "block", margin: "32px auto" }} />
      ) : projectTables.length === 0 ? (
        <Empty description="该项目暂无关联源表，请先在项目详情页关联源表" />
      ) : (
        <div style={{ maxHeight: 400, overflowY: "auto", paddingRight: 8 }}>
          {projectTables.map((t) => (
            <div
              key={t.id}
              style={{
                display: "flex",
                alignItems: "flex-start",
                gap: 10,
                padding: "10px 12px",
                borderRadius: 8,
                marginBottom: 8,
                border: "1px solid #e8eef8",
                background: selected.includes(t.id) ? "#f0f4ff" : "#fafafa",
              }}
            >
              <Checkbox
                checked={selected.includes(t.id)}
                onChange={(e) => toggle(t.id, e.target.checked)}
                style={{ marginTop: 2 }}
              />
              <div style={{ flex: 1 }}>
                <Text strong style={{ fontSize: 14 }}>{t.name}</Text>
                {t.description && (
                  <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>{t.description}</Text>
                )}
                <div style={{ marginTop: 6, display: "flex", flexWrap: "wrap", gap: 4 }}>
                  {t.columns.slice(0, 5).map((c) => (
                    <Tag key={c.id} style={{ fontSize: 11 }}>
                      {c.column_name}
                      <Text type="secondary" style={{ marginLeft: 3, fontSize: 10 }}>{c.data_type}</Text>
                    </Tag>
                  ))}
                  {t.columns.length > 5 && (
                    <Tag style={{ fontSize: 11 }}>+{t.columns.length - 5} 更多字段</Tag>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </Modal>
  );
}
