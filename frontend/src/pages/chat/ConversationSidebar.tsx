import { useState, useRef, useEffect } from "react";
import { Button, List, Input, Modal, Tag, Space, Tooltip } from "antd";
import { EditOutlined, DeleteOutlined, ApartmentOutlined } from "@ant-design/icons";
import type { Conversation, TableDetailOut } from "../../api/types";

interface Props {
  conversations: Conversation[];
  activeId: number | null;
  onSelect: (id: number) => void;
  onNew: () => void;
  onRename: (id: number, name: string) => void;
  onDelete: (id: number) => void;
  convTables: TableDetailOut[];
  onViewLineage: () => void;
}

const itemBase: React.CSSProperties = {
  cursor: "pointer",
  borderRadius: 6,
  padding: "6px 10px",
  color: "#9d9db5",
  border: "none",
  transition: "all 0.15s ease",
  display: "flex",
  alignItems: "center",
  justifyContent: "space-between",
};

const itemActive: React.CSSProperties = {
  background: "#2a2a4a",
  color: "#ffffff",
  fontWeight: 600,
};

export function ConversationSidebar({ conversations, activeId, onSelect, onNew, onRename, onDelete, convTables, onViewLineage }: Props) {
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editValue, setEditValue] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);
  const [deleteId, setDeleteId] = useState<number | null>(null);
  const [hoveredId, setHoveredId] = useState<number | null>(null);

  useEffect(() => {
    if (editingId !== null && inputRef.current) {
      inputRef.current.focus();
    }
  }, [editingId]);

  const startRename = (c: Conversation) => {
    setEditingId(c.id);
    setEditValue(c.name || "对话 #" + c.id);
  };

  const submitRename = () => {
    if (editingId !== null && editValue.trim()) {
      onRename(editingId, editValue.trim());
    }
    setEditingId(null);
  };

  return (
    <div style={{ padding: "0 8px 8px", display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
      {activeId !== null && (
        <div style={{ borderBottom: "1px solid rgba(255,255,255,0.08)", paddingBottom: 8, marginBottom: 8 }}>
          <div style={{ fontSize: 12, color: "#9d9db5", marginBottom: 6 }}>当前对话表</div>
          <div style={{ display: "flex", flexWrap: "wrap", gap: 4, marginBottom: 8 }}>
            {convTables.map((t) => (
              <Tag key={t.id} color="blue" style={{ fontSize: 11, lineHeight: "18px", margin: 0 }}>
                {t.name}
              </Tag>
            ))}
          </div>
          <div style={{ fontSize: 11, color: "#7a7a9a", marginBottom: 8 }}>
            共 {convTables.length} 张表
          </div>
          <Button
            size="small"
            block
            icon={<ApartmentOutlined />}
            onClick={onViewLineage}
            disabled={convTables.length === 0}
            style={{ color: "#9d9db5", borderColor: "rgba(255,255,255,0.15)" }}
            ghost
          >
            查看血缘
          </Button>
        </div>
      )}

      <Button type="primary" block onClick={onNew} style={{ marginBottom: 8 }} ghost>
        + 新对话
      </Button>

      <div style={{ flex: 1, overflowY: "auto", marginBottom: 8 }}>
        <List
          size="small"
          dataSource={conversations}
          split={false}
          renderItem={(c) => {
            const isActive = c.id === activeId;
            const isEditing = c.id === editingId;
            const isHovered = c.id === hoveredId;
            return (
              <List.Item
                style={{
                  ...itemBase,
                  ...(isActive ? itemActive : {}),
                }}
                onMouseEnter={() => setHoveredId(c.id)}
                onMouseLeave={() => setHoveredId(null)}
              >
                {isEditing ? (
                  <Input
                    ref={inputRef as React.Ref<any>}
                    size="small"
                    value={editValue}
                    onChange={(e) => setEditValue(e.target.value)}
                    onPressEnter={submitRename}
                    onBlur={submitRename}
                    onClick={(e) => e.stopPropagation()}
                    style={{ width: "100%" }}
                  />
                ) : (
                  <div
                    onClick={() => onSelect(c.id)}
                    style={{ flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap", color: "inherit" }}
                  >
                    {c.name || "对话 #" + c.id}
                  </div>
                )}
                {!isEditing && isHovered && (
                  <Space size={2} style={{ flexShrink: 0 }} onMouseEnter={(e) => e.stopPropagation()}>
                    <Tooltip title="重命名">
                      <Button
                        type="text"
                        size="small"
                        icon={<EditOutlined style={{ fontSize: 12, color: "#9d9db5" }} />}
                        onClick={(e) => { e.stopPropagation(); startRename(c); }}
                      />
                    </Tooltip>
                    <Tooltip title="删除">
                      <Button
                        type="text"
                        size="small"
                        icon={<DeleteOutlined style={{ fontSize: 12, color: "#9d9db5" }} />}
                        onClick={(e) => { e.stopPropagation(); setDeleteId(c.id); }}
                      />
                    </Tooltip>
                  </Space>
                )}
              </List.Item>
            );
          }}
        />
      </div>

      <Modal
        title="确认删除"
        open={deleteId !== null}
        onCancel={() => setDeleteId(null)}
        onOk={() => { if (deleteId !== null) onDelete(deleteId); setDeleteId(null); }}
        okText="删除"
        okButtonProps={{ danger: true }}
      >
        <p>确定要删除该对话吗？此操作不可撤销。</p>
      </Modal>
    </div>
  );
}
