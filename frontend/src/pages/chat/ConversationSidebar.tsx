import { Button, List } from "antd";
import type { Conversation } from "../../api/types";

interface Props {
  conversations: Conversation[];
  activeId: number | null;
  onSelect: (id: number) => void;
  onNew: () => void;
}

export function ConversationSidebar({ conversations, activeId, onSelect, onNew }: Props) {
  return (
    <div style={{ padding: 8 }}>
      <Button type="primary" block onClick={onNew} style={{ marginBottom: 8 }}>+ 新对话</Button>
      <List
        size="small"
        dataSource={conversations}
        renderItem={(c) => (
          <List.Item
            onClick={() => onSelect(c.id)}
            style={{
              cursor: "pointer",
              background: c.id === activeId ? "#eef1ff" : undefined,
              borderRadius: 6,
              padding: "6px 10px",
            }}
          >
            对话 #{c.id}
          </List.Item>
        )}
      />
    </div>
  );
}
