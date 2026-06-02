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
  state: number; // 1..5
  created_at: string;
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
  | { type: "stream_end" }
  | { type: "end" };
