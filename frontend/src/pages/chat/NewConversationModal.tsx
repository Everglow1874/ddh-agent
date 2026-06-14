import { useEffect, useState } from "react";
import { Modal, Select } from "antd";
import { listTables } from "../../api/tables";
import type { SourceTable } from "../../api/types";

interface Props {
  open: boolean;
  onCancel: () => void;
  onConfirm: (tableIds: number[]) => void;
}

export function NewConversationModal({ open, onCancel, onConfirm }: Props) {
  const [tables, setTables] = useState<SourceTable[]>([]);
  const [selected, setSelected] = useState<number[]>([]);

  useEffect(() => {
    if (open) {
      setSelected([]);
      listTables("all").then(setTables);
    }
  }, [open]);

  return (
    <Modal
      title="新建对话 · 选择源表"
      open={open}
      onCancel={onCancel}
      onOk={() => onConfirm(selected)}
      okText="创建"
      okButtonProps={{ disabled: selected.length === 0 }}
    >
      <Select
        mode="multiple"
        style={{ width: "100%" }}
        placeholder="选择本次对话要用到的源表（可多选）"
        value={selected}
        onChange={setSelected}
        options={tables.map((t) => ({ label: t.name, value: t.id }))}
      />
    </Modal>
  );
}
