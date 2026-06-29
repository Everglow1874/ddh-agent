import { useEffect, useRef, useState, useMemo } from "react";
import { Modal, Button, Space, message, Select, Tag, Empty } from "antd";
import { lineageGraph } from "../../api/relations";
import type { LineageGraph, SourceTable } from "../../api/types";
import { LineageGraphCanvas } from "./LineageGraphCanvas";
import type { LineageGraphCanvasHandle } from "./LineageGraphCanvas";

interface Props {
  open: boolean;
  tables: SourceTable[];
  onClose: () => void;
}

export function LineageGraphModal({ open, tables, onClose }: Props) {
  const canvasRef = useRef<LineageGraphCanvasHandle>(null);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [search, setSearch] = useState("");
  const [data, setData] = useState<LineageGraph | null>(null);
  const [loading, setLoading] = useState(false);

  const tableOptions = useMemo(
    () =>
      tables.map((t) => ({
        label: (
          <Space size={4}>
            <span>{t.name}</span>
            <Tag color={t.scope === 1 ? "blue" : "green"} style={{ fontSize: 10, lineHeight: "16px", padding: "0 4px" }}>
              {t.scope === 1 ? "公共" : "私有"}
            </Tag>
          </Space>
        ),
        value: t.id,
        searchText: `${t.name} ${t.scope === 1 ? "公共" : "私有"} ${t.description ?? ""}`,
      })),
    [tables],
  );

  const displayOptions = useMemo(() => {
    if (!search) return tableOptions.slice(0, 10);
    const q = search.toLowerCase();
    return tableOptions.filter((o) => o.searchText.toLowerCase().includes(q));
  }, [tableOptions, search]);

  useEffect(() => {
    if (open) {
      setSelectedIds([]);
      setSearch("");
      setData(null);
    }
  }, [open]);

  useEffect(() => {
    if (!open || selectedIds.length === 0) {
      setData(null);
      return;
    }
    setLoading(true);
    lineageGraph(selectedIds)
      .then(setData)
      .catch(() => message.error("获取血缘图失败"))
      .finally(() => setLoading(false));
  }, [open, selectedIds]);

  return (
    <Modal
      title="表血缘图"
      open={open}
      onCancel={onClose}
      footer={null}
      width="88%"
      style={{ top: 20 }}
      styles={{ body: { padding: 0 } }}
    >
      <div
        style={{
          padding: "10px 16px",
          borderBottom: "1px solid #eef1f6",
          background: "#fbfcfe",
          display: "flex",
          alignItems: "center",
          gap: 12,
          flexWrap: "wrap",
        }}
      >
        <span style={{ fontSize: 13, color: "#41506a", whiteSpace: "nowrap", fontWeight: 600 }}>选择表</span>
        <Select
          mode="multiple"
          style={{ minWidth: 320, maxWidth: 520, flex: 1 }}
          placeholder="搜索并选择表（默认显示前 10 条）"
          value={selectedIds}
          onChange={setSelectedIds}
          showSearch
          filterOption={false}
          onSearch={setSearch}
          onDropdownVisibleChange={(visible) => {
            if (!visible) setSearch("");
          }}
          notFoundContent={search ? "未找到匹配的表" : "暂无数据"}
          options={displayOptions}
          tagRender={(props) => {
            const t = tables.find((x) => x.id === props.value);
            return (
              <Tag
                closable={props.closable}
                onClose={props.onClose}
                color={t?.scope === 1 ? "blue" : "green"}
                style={{ marginRight: 3, borderRadius: 6 }}
              >
                {props.label}
              </Tag>
            );
          }}
        />
        <Space>
          <Button size="small" disabled={!data} onClick={() => canvasRef.current?.exportImage()}>导出图片</Button>
          <Button size="small" disabled={!data} onClick={() => canvasRef.current?.exportJson()}>导出 JSON</Button>
        </Space>
      </div>

      {selectedIds.length === 0 ? (
        <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: "72vh" }}>
          <Empty description="请在上方选择至少一个表" />
        </div>
      ) : (
        <div style={{ padding: 12 }}>
          <LineageGraphCanvas ref={canvasRef} data={data} loading={loading} height="72vh" />
        </div>
      )}
    </Modal>
  );
}
