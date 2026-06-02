import { client } from "./client";
import type { SourceTable, TableDetail } from "./types";

export async function listTables(scope: "public" | "private" | "all" = "all"): Promise<SourceTable[]> {
  const resp = await client.get<SourceTable[]>("/tables", { params: { scope } });
  return resp.data;
}

export async function importTable(file: File, scope: number, description?: string): Promise<SourceTable> {
  const form = new FormData();
  form.append("file", file);
  form.append("scope", String(scope));
  if (description) form.append("description", description);
  const resp = await client.post<SourceTable>("/tables/import", form);
  return resp.data;
}

export async function getTable(id: number): Promise<TableDetail> {
  const resp = await client.get<TableDetail>(`/tables/${id}`);
  return resp.data;
}

export async function deleteTable(id: number): Promise<void> {
  await client.delete(`/tables/${id}`);
}
