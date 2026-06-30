export interface User {
  id: number;
  username: string;
  email: string;
  role: number;
  created_at: string;
}

export interface TokenResponse {
  access_token: string;
  token_type: string;
}

export interface TableColumn {
  id: number;
  column_name: string;
  data_type: string;
  comment: string | null;
  sort_order: number;
  col_length: number | null;
  col_precision: number | null;
  is_distribution_key: number | null;
  is_partition_key: number | null;
  is_primary_key: number | null;
  is_nullable: number | null;
  code_info: string | null;
  default_value: string | null;
  downstream_job_count: number | null;
}

export interface SourceTable {
  id: number;
  name: string;
  description: string | null;
  scope: number; // 1=public 2=private
  owner_id: number | null;
  created_at: string;
}

export interface TableDetail extends SourceTable {
  columns: TableColumn[];
}

export interface Project {
  id: number;
  name: string;
  description: string | null;
  owner_id: number;
  status: number; // 1=draft 2=in_progress 3=done
  created_at: string;
}

export interface Conversation {
  id: number;
  project_id: number;
  name: string;
  state: number; // 1..5
  created_at: string;
  table_ids: number[];
}

export interface TableDetailOut {
  id: number;
  name: string;
  description: string | null;
  columns: TableColumn[];
}

export interface Message {
  id: number;
  conversation_id: number;
  role: string;
  content: string;
  created_at: string;
}

export interface EtlStep {
  id: number;
  job_id: number;
  step_order: number;
  step_name: string;
  is_temp_table: number;
  sql_file_path: string | null;
}

export interface EtlJob {
  id: number;
  project_id: number;
  target_table: string;
  target_schema: unknown[] | null;
  plan_md_path: string | null;
  created_at: string;
  steps: EtlStep[];
}

export interface LLMConfig {
  provider: string;
  model: string;
}

// SSE event shapes
export interface SchemaColumn {
  name: string;
  type: string;
  comment?: string;
}

export interface EtlStepProposal {
  step_order: number;
  step_name: string;
  description: string;
  is_temp_table: boolean;
  output_table: string;
}

export type SSEEvent =
  | { type: "token"; text: string }
  | { type: "schema_proposal"; target_table: string; columns: SchemaColumn[] }
  | { type: "steps_proposal"; steps: EtlStepProposal[] }
  | { type: "step_generated"; step_order: number; step_name: string; sql: string; file: string }
  | { type: "done"; job_id: number }
  | { type: "error"; message: string }
  | { type: "waiting"; state: number; message: string }
  | { type: "already_done" }
  | { type: "turn_end" }
  | { type: "stream_end" }
  | { type: "end" };

// ===== 表关系 =====

export interface ColumnPair {
  source_column_id: number;
  source_column_name?: string;
  target_column_id: number;
  target_column_name?: string;
}

export interface Relation {
  id: number;
  source_table_id: number;
  source_table_name?: string;
  source_table_comment?: string;
  target_table_id: number;
  target_table_name?: string;
  target_table_comment?: string;
  relation_type: "ONE_TO_ONE" | "ONE_TO_MANY" | "MANY_TO_ONE" | "MANY_TO_MANY";
  description?: string;
  column_pairs: ColumnPair[];
}

export interface RelationSave {
  source_table_id: number;
  target_table_id: number;
  relation_type: string;
  description?: string;
  column_pairs: { source_column_id: number; target_column_id: number }[];
}

export interface GraphColumn {
  id: number;
  name: string;
  type: string;
  comment?: string;
}

export interface GraphNode {
  id: string;
  table_id: number;
  table_name: string;
  table_comment?: string;
  columns: GraphColumn[];
}

export interface GraphEdge {
  source: string;
  target: string;
  relation_type: string;
  column_pairs: ColumnPair[];
  relation_id?: number;
}

export interface LineageGraph {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

export interface PageResponse<T> {
  content: T[];
  total: number;
  page: number;
  size: number;
}

export const RELATION_TYPE_LABELS: Record<string, string> = {
  ONE_TO_ONE: "一对一",
  ONE_TO_MANY: "一对多",
  MANY_TO_ONE: "多对一",
  MANY_TO_MANY: "多对多",
};
