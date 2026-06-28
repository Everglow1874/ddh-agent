import { useState, useEffect } from "react";
import { Modal, Form, Select, Input, Space, Button, message } from "antd";
import { getTable } from "../../api/tables";
import { createRelation, updateRelation } from "../../api/relations";
import { RELATION_TYPE_LABELS } from "../../api/types";
import type { Relation, RelationSave, SourceTable, TableColumn } from "../../api/types";

interface PairForm {
  sourceColumnId?: number;
  targetColumnId?: number;
}

interface Props {
  open: boolean;
  tables: SourceTable[];
  editing: Relation | null;
  onClose: () => void;
  onSaved: () => void;
}

export function RelationEditModal({ open, tables, editing, onClose, onSaved }: Props) {
  const [sourceTableId, setSourceTableId] = useState<number>();
  const [targetTableId, setTargetTableId] = useState<number>();
  const [relationType, setRelationType] = useState("ONE_TO_MANY");
  const [description, setDescription] = useState("");
  const [pairs, setPairs] = useState<PairForm[]>([{}]);
  const [sourceCols, setSourceCols] = useState<TableColumn[]>([]);
  const [targetCols, setTargetCols] = useState<TableColumn[]>([]);

  useEffect(() => {
    if (!open) return;
    if (editing) {
      setSourceTableId(editing.source_table_id);
      setTargetTableId(editing.target_table_id);
      setRelationType(editing.relation_type);
      setDescription(editing.description || "");
      setPairs(editing.column_pairs.map((p) => ({ sourceColumnId: p.source_column_id, targetColumnId: p.target_column_id })));
      Promise.all([getTable(editing.source_table_id), getTable(editing.target_table_id)]).then(([s, t]) => {
        setSourceCols(s.columns);
        setTargetCols(t.columns);
      });
    } else {
      setSourceTableId(undefined);
      setTargetTableId(undefined);
      setRelationType("ONE_TO_MANY");
      setDescription("");
      setPairs([{}]);
      setSourceCols([]);
      setTargetCols([]);
    }
  }, [open, editing]);

  const handleSource = async (id: number) => {
    setSourceTableId(id);
    setPairs((prev) => prev.map((p) => ({ ...p, sourceColumnId: undefined })));
    const d = await getTable(id);
    setSourceCols(d.columns);
  };

  const handleTarget = async (id: number) => {
    setTargetTableId(id);
    setPairs((prev) => prev.map((p) => ({ ...p, targetColumnId: undefined })));
    const d = await getTable(id);
    setTargetCols(d.columns);
  };

  const updatePair = (idx: number, key: keyof PairForm, val: number) =>
    setPairs((prev) => prev.map((p, i) => (i === idx ? { ...p, [key]: val } : p)));

  const handleSave = async () => {
    if (!sourceTableId || !targetTableId) {
      message.warning("请选择主表和关联表");
      return;
    }
    const valid = pairs.filter((p) => p.sourceColumnId && p.targetColumnId);
    if (valid.length === 0) {
      message.warning("请至少配置一对关联字段");
      return;
    }
    const payload: RelationSave = {
      source_table_id: sourceTableId,
      target_table_id: targetTableId,
      relation_type: relationType,
      description,
      column_pairs: valid.map((p) => ({ source_column_id: p.sourceColumnId!, target_column_id: p.targetColumnId! })),
    };
    try {
      if (editing) await updateRelation(editing.id, payload);
      else await createRelation(payload);
      message.success("保存成功");
      onSaved();
      onClose();
    } catch {
      message.error("保存失败");
    }
  };

  return (
    <Modal
      title={editing ? "编辑关系" : "新建关系"}
      open={open}
      onCancel={onClose}
      onOk={handleSave}
      width={680}
      okText="保存"
      cancelText="取消"
    >
      <Form layout="vertical" size="small">
        <Space align="start" style={{ width: "100%" }} size={12} wrap>
          <Form.Item label="主表(一/源侧)">
            <Select
              style={{ width: 180 }}
              value={sourceTableId}
              onChange={handleSource}
              showSearch
              optionFilterProp="label"
              options={tables.map((t) => ({ label: t.name, value: t.id }))}
            />
          </Form.Item>
          <Form.Item label="关联表(多/目标侧)">
            <Select
              style={{ width: 180 }}
              value={targetTableId}
              onChange={handleTarget}
              showSearch
              optionFilterProp="label"
              options={tables.map((t) => ({ label: t.name, value: t.id }))}
            />
          </Form.Item>
          <Form.Item label="关系类型">
            <Select
              style={{ width: 140 }}
              value={relationType}
              onChange={setRelationType}
              options={Object.entries(RELATION_TYPE_LABELS).map(([v, l]) => ({ label: l, value: v }))}
            />
          </Form.Item>
        </Space>

        {relationType === "MANY_TO_MANY" && (
          <div style={{ color: "#fa8c16", fontSize: 12, marginBottom: 8 }}>
            提示:多对多关系通常需要中间表,此处仅记录逻辑关联。
          </div>
        )}

        <Form.Item label="关联字段对(支持复合键)">
          {pairs.map((p, idx) => (
            <Space key={idx} style={{ display: "flex", marginBottom: 8 }}>
              <Select
                style={{ width: 220 }}
                placeholder="主表字段"
                value={p.sourceColumnId}
                onChange={(v) => updatePair(idx, "sourceColumnId", v)}
                showSearch
                optionFilterProp="label"
                options={sourceCols.map((c) => ({ label: c.column_name, value: c.id }))}
              />
              <span style={{ color: "#1677ff" }}>=</span>
              <Select
                style={{ width: 220 }}
                placeholder="关联表字段"
                value={p.targetColumnId}
                onChange={(v) => updatePair(idx, "targetColumnId", v)}
                showSearch
                optionFilterProp="label"
                options={targetCols.map((c) => ({ label: c.column_name, value: c.id }))}
              />
              {pairs.length > 1 && (
                <Button type="link" danger size="small" onClick={() => setPairs(pairs.filter((_, i) => i !== idx))}>
                  删除
                </Button>
              )}
            </Space>
          ))}
          <Button type="dashed" size="small" onClick={() => setPairs([...pairs, {}])}>
            + 添加字段对
          </Button>
        </Form.Item>

        <Form.Item label="关系说明(可选)">
          <Input
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="如:每个订单属于一个用户"
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
