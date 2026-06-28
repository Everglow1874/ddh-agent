import { useEffect, useState } from "react";
import { Modal, Input, Select, Form } from "antd";
import { listTables } from "../../api/tables";
import type { SourceTable } from "../../api/types";

interface Props {
  open: boolean;
  onCancel: () => void;
  onConfirm: (name: string, tableIds: number[]) => void;
}

export function NewConversationModal({ open, onCancel, onConfirm }: Props) {
  const [tables, setTables] = useState<SourceTable[]>([]);
  const [name, setName] = useState("");
  const [selected, setSelected] = useState<number[]>([]);

  useEffect(() => {
    if (open) {
      setName("");
      setSelected([]);
      listTables("all").then(setTables);
    }
  }, [open]);

  const handleOk = () => onConfirm(name.trim() || "", selected);

  return (
    <Modal
      title="新建对话"
      open={open}
      onCancel={onCancel}
      onOk={handleOk}
      okText="创建"
      okButtonProps={{ disabled: selected.length === 0 }}
    >
      <Form layout="vertical">
        <Form.Item label="对话名称">
          <Input
            placeholder="留空将自动生成"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </Form.Item>
        <Form.Item label="选择源表" required>
          <Select
            mode="multiple"
            style={{ width: "100%" }}
            placeholder="选择本次对话要用到的源表（可多选）"
            value={selected}
            onChange={setSelected}
            options={tables.map((t) => ({ label: t.name, value: t.id }))}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
