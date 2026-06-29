import { useEffect, useRef, useState } from "react";
import { Modal, Button, Space, message, Select, Tag, Empty, Typography, Switch, Radio, Input } from "antd";
import { PlusOutlined, CloseOutlined, ThunderboltOutlined } from "@ant-design/icons";
import { lineageGraph, createRelation, updateRelation, deleteRelation } from "../../api/relations";
import { listTables } from "../../api/tables";
import type { LineageGraph, GraphNode, SourceTable, GraphColumn, GraphEdge, RelationSave } from "../../api/types";
import { LineageGraphCanvas } from "../relations/LineageGraphCanvas";
import type { LineageGraphCanvasHandle } from "../relations/LineageGraphCanvas";

interface Props {
  open: boolean;
  onClose: () => void;
  tableIds: number[];
  onSave?: (tableIds: number[]) => void;
}

interface EditRelationState {
  sourceTable: { id: number; name: string; columns: GraphColumn[] };
  targetTable: { id: number; name: string; columns: GraphColumn[] };
  relationType: string;
  description: string;
  columnPairs: { sourceColumnId: number | undefined; targetColumnId: number | undefined }[];
  existingRelationId?: number;
}

export function ConversationLineageModal({ open, onClose, tableIds, onSave }: Props) {
  const canvasRef = useRef<LineageGraphCanvasHandle>(null);
  const [allTables, setAllTables] = useState<SourceTable[]>([]);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [addValue, setAddValue] = useState<number | null>(null);
  const [lineMode, setLineMode] = useState(false);
  const [graphData, setGraphData] = useState<LineageGraph | null>(null);
  const [loading, setLoading] = useState(false);
  const [editRelation, setEditRelation] = useState<EditRelationState | null>(null);

  useEffect(() => {
    if (open) listTables("all").then(setAllTables);
  }, [open]);

  useEffect(() => {
    if (open) {
      setSelectedIds(tableIds);
      setAddValue(null);
      setLineMode(false);
    }
  }, [open, tableIds]);

  const reload = () => {
    if (selectedIds.length === 0) {
      setGraphData(null);
      return;
    }
    setLoading(true);
    lineageGraph(selectedIds)
      .then(setGraphData)
      .catch(() => message.error("获取血缘图失败"))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (open) reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, selectedIds]);

  const tableMap = new Map(allTables.map((t) => [t.id, t]));
  const selectedTables = selectedIds.map((id) => tableMap.get(id)).filter(Boolean) as SourceTable[];
  const addableOptions = allTables
    .filter((t) => !selectedIds.includes(t.id))
    .map((t) => ({ label: `${t.name} (${t.scope === 1 ? "公共" : "私有"})`, value: t.id }));

  const openEditor = (src: GraphNode, tgt: GraphNode, existing?: GraphEdge) => {
    setEditRelation({
      sourceTable: { id: src.table_id, name: src.table_name, columns: src.columns },
      targetTable: { id: tgt.table_id, name: tgt.table_name, columns: tgt.columns },
      relationType: existing?.relation_type ?? "ONE_TO_MANY",
      description: "",
      columnPairs:
        existing?.column_pairs?.map((cp) => ({
          sourceColumnId: cp.source_column_id,
          targetColumnId: cp.target_column_id,
        })) ?? [{ sourceColumnId: undefined, targetColumnId: undefined }],
      existingRelationId: existing?.relation_id,
    });
  };

  const handleSaveRelation = async () => {
    if (!editRelation) return;
    const pairs = editRelation.columnPairs
      .filter((p) => p.sourceColumnId != null && p.targetColumnId != null)
      .map((p) => ({ source_column_id: p.sourceColumnId!, target_column_id: p.targetColumnId! }));
    if (pairs.length === 0) {
      message.warning("请至少配置一对关联字段");
      return;
    }
    const payload: RelationSave = {
      source_table_id: editRelation.sourceTable.id,
      target_table_id: editRelation.targetTable.id,
      relation_type: editRelation.relationType,
      description: editRelation.description || undefined,
      column_pairs: pairs,
    };
    try {
      if (editRelation.existingRelationId != null) await updateRelation(editRelation.existingRelationId, payload);
      else await createRelation(payload);
      message.success("关系保存成功");
      setEditRelation(null);
      reload();
    } catch {
      message.error("保存关系失败");
    }
  };

  const handleDeleteRelation = async () => {
    if (!editRelation?.existingRelationId) return;
    try {
      await deleteRelation(editRelation.existingRelationId);
      message.success("关系已删除");
      setEditRelation(null);
      reload();
    } catch {
      message.error("删除关系失败");
    }
  };

  return (
    <>
      <Modal
        title={
          <Space size={8}>
            <ThunderboltOutlined style={{ color: "#4f6bed" }} />
            <span>表血缘图</span>
          </Space>
        }
        open={open}
        onCancel={onClose}
        footer={null}
        width="88%"
        style={{ top: 20 }}
        styles={{ body: { padding: 0 } }}
      >
        <div style={{ padding: "12px 16px", borderBottom: "1px solid #eef1f6", background: "#fbfcfe" }}>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 10 }}>
            <Typography.Text strong style={{ fontSize: 13, color: "#1e2940" }}>关联表管理</Typography.Text>
            <Space>
              <Button size="small" type="primary" disabled={selectedIds.length === 0} onClick={() => onSave?.(selectedIds)}>保存关联</Button>
              <Button size="small" disabled={!graphData} onClick={() => canvasRef.current?.exportImage()}>导出图片</Button>
              <Button size="small" disabled={!graphData} onClick={() => canvasRef.current?.exportJson()}>导出 JSON</Button>
            </Space>
          </div>

          <div style={{ marginBottom: 10 }}>
            {selectedTables.length > 0 ? (
              <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
                {selectedTables.map((t) => (
                  <Tag
                    key={t.id}
                    closable
                    onClose={() => setSelectedIds((prev) => prev.filter((id) => id !== t.id))}
                    color={t.scope === 1 ? "blue" : "green"}
                    style={{ fontSize: 12, lineHeight: "22px", padding: "0 4px 0 8px", margin: 0, borderRadius: 6 }}
                  >
                    {t.name}
                  </Tag>
                ))}
              </div>
            ) : (
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>尚未关联任何表</Typography.Text>
            )}
          </div>

          <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
            <Select
              style={{ flex: 1, minWidth: 0 }}
              placeholder="搜索并添加表…"
              value={addValue}
              onChange={(val) => {
                if (val != null) {
                  setSelectedIds((prev) => [...prev, val]);
                  setAddValue(null);
                }
              }}
              showSearch
              filterOption={(input, option) => (option?.label ?? "").toLowerCase().includes(input.toLowerCase())}
              options={addableOptions}
              notFoundContent="无可用表"
              disabled={lineMode}
            />
            <Button
              icon={<PlusOutlined />}
              disabled={lineMode || addValue == null}
              onClick={() => {
                if (addValue != null) {
                  setSelectedIds((prev) => [...prev, addValue]);
                  setAddValue(null);
                }
              }}
            >
              添加
            </Button>
            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: 8,
                paddingLeft: 12,
                marginLeft: 4,
                borderLeft: "1px solid #eef1f6",
              }}
            >
              <Switch size="small" checked={lineMode} onChange={setLineMode} />
              <span style={{ fontSize: 12, color: lineMode ? "#4f6bed" : "#666", fontWeight: lineMode ? 600 : 400 }}>
                连线模式
              </span>
              {lineMode && (
                <Tag color="geekblue" style={{ fontSize: 10, lineHeight: "16px", margin: 0, borderRadius: 5 }}>
                  依次点击两张表
                </Tag>
              )}
            </div>
          </div>
        </div>

        {selectedIds.length === 0 ? (
          <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: "72vh" }}>
            <Empty description="请在上方添加至少一张表" />
          </div>
        ) : (
          <div style={{ padding: 12 }}>
            <LineageGraphCanvas
              ref={canvasRef}
              data={graphData}
              loading={loading}
              height="72vh"
              lineMode={lineMode}
              onConnectPair={openEditor}
              onEditEdge={(edge, src, tgt) => openEditor(src, tgt, edge)}
            />
          </div>
        )}
      </Modal>

      <Modal
        title={
          editRelation
            ? `${editRelation.sourceTable.name}  ←→  ${editRelation.targetTable.name}`
            : "编辑关系"
        }
        open={editRelation !== null}
        onCancel={() => setEditRelation(null)}
        width={520}
        footer={null}
        destroyOnClose
      >
        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 13, color: "#333", marginBottom: 8 }}>关系类型</div>
          <Radio.Group
            value={editRelation?.relationType}
            onChange={(e) => setEditRelation((prev) => (prev ? { ...prev, relationType: e.target.value } : null))}
          >
            <Radio value="ONE_TO_ONE">一对一</Radio>
            <Radio value="ONE_TO_MANY">一对多</Radio>
            <Radio value="MANY_TO_ONE">多对一</Radio>
            <Radio value="MANY_TO_MANY">多对多</Radio>
          </Radio.Group>
        </div>

        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 13, color: "#333", marginBottom: 8 }}>
            字段映射
            <Button
              type="link"
              size="small"
              style={{ padding: 0, marginLeft: 8 }}
              onClick={() =>
                setEditRelation((prev) =>
                  prev
                    ? { ...prev, columnPairs: [...prev.columnPairs, { sourceColumnId: undefined, targetColumnId: undefined }] }
                    : null,
                )
              }
            >
              ＋ 添加
            </Button>
          </div>
          {editRelation?.columnPairs.map((pair, i) => (
            <div key={i} style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 6 }}>
              <Select
                style={{ flex: 1 }}
                placeholder="源字段"
                value={pair.sourceColumnId}
                onChange={(val) => {
                  const next = [...editRelation.columnPairs];
                  next[i] = { ...next[i], sourceColumnId: val };
                  setEditRelation({ ...editRelation, columnPairs: next });
                }}
                options={editRelation.sourceTable.columns.map((c) => ({ label: `${c.name} (${c.type})`, value: c.id }))}
                showSearch
                filterOption={(input, option) => (option?.label ?? "").toLowerCase().includes(input.toLowerCase())}
                getPopupContainer={(trigger) => trigger.parentElement!}
              />
              <span style={{ color: "#4f6bed", fontWeight: 700 }}>=</span>
              <Select
                style={{ flex: 1 }}
                placeholder="目标字段"
                value={pair.targetColumnId}
                onChange={(val) => {
                  const next = [...editRelation.columnPairs];
                  next[i] = { ...next[i], targetColumnId: val };
                  setEditRelation({ ...editRelation, columnPairs: next });
                }}
                options={editRelation.targetTable.columns.map((c) => ({ label: `${c.name} (${c.type})`, value: c.id }))}
                showSearch
                filterOption={(input, option) => (option?.label ?? "").toLowerCase().includes(input.toLowerCase())}
                getPopupContainer={(trigger) => trigger.parentElement!}
              />
              <Button
                type="text"
                size="small"
                danger
                icon={<CloseOutlined />}
                onClick={() => {
                  const next = editRelation.columnPairs.filter((_, j) => j !== i);
                  setEditRelation({ ...editRelation, columnPairs: next });
                }}
              />
            </div>
          ))}
        </div>

        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 13, color: "#333", marginBottom: 8 }}>描述（可选）</div>
          <Input.TextArea
            rows={2}
            value={editRelation?.description ?? ""}
            onChange={(e) => setEditRelation((prev) => (prev ? { ...prev, description: e.target.value } : null))}
            placeholder="描述该关系的业务含义…"
          />
        </div>

        <div style={{ display: "flex", justifyContent: "space-between" }}>
          <div>
            {editRelation?.existingRelationId != null && (
              <Button danger onClick={handleDeleteRelation}>删除关系</Button>
            )}
          </div>
          <Space>
            <Button onClick={() => setEditRelation(null)}>取消</Button>
            <Button type="primary" onClick={handleSaveRelation}>保存</Button>
          </Space>
        </div>
      </Modal>
    </>
  );
}
