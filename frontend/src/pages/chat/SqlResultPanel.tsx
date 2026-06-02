import { Tabs, Button, Empty } from "antd";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { downloadUrl } from "../../api/jobs";

export interface GeneratedStep {
  step_order: number;
  step_name: string;
  sql: string;
}

interface Props {
  steps: GeneratedStep[];
  jobId: number | null;
}

export function SqlResultPanel({ steps, jobId }: Props) {
  if (steps.length === 0) {
    return <Empty description="SQL 将在生成后显示" style={{ marginTop: 80 }} />;
  }

  return (
    <div>
      <Tabs
        items={[...steps]
          .sort((a, b) => a.step_order - b.step_order)
          .map((s) => ({
            key: String(s.step_order),
            label: `Step ${s.step_order}`,
            children: (
              <SyntaxHighlighter language="sql" customStyle={{ fontSize: 12, borderRadius: 6 }}>
                {s.sql}
              </SyntaxHighlighter>
            ),
          }))}
      />
      {jobId !== null && (
        <Button type="primary" block style={{ marginTop: 12 }} href={downloadUrl(jobId)} target="_blank">
          ⬇ 下载全部 (ZIP)
        </Button>
      )}
    </div>
  );
}
