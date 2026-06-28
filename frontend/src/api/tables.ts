import { client } from "./client";
import type { SourceTable, TableDetail, PageResponse, TableColumn } from "./types";

export async function listTables(scope: "public" | "private" | "all" = "all"): Promise<SourceTable[]> {
  const resp = await client.get<SourceTable[]>("/tables", { params: { scope } });
  return resp.data;
}

export async function listTablesPage(
  scope: "public" | "private",
  search = "",
  page = 1,
  size = 20
): Promise<PageResponse<SourceTable>> {
  const resp = await client.get<PageResponse<SourceTable>>("/tables/page", {
    params: { scope, search, page, size },
  });
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

export async function updateTable(id: number, data: { name: string; description?: string }): Promise<void> {
  await client.put(`/tables/${id}`, data);
}

export async function deleteTable(id: number): Promise<void> {
  await client.delete(`/tables/${id}`);
}

export interface ColumnRequest {
  column_name: string;
  data_type: string;
  comment?: string;
}

export async function addColumn(tableId: number, data: ColumnRequest): Promise<TableColumn> {
  const resp = await client.post<TableColumn>(`/tables/${tableId}/columns`, data);
  return resp.data;
}

export async function updateColumn(tableId: number, columnId: number, data: ColumnRequest): Promise<void> {
  await client.put(`/tables/${tableId}/columns/${columnId}`, data);
}

export async function deleteColumn(tableId: number, columnId: number): Promise<void> {
  await client.delete(`/tables/${tableId}/columns/${columnId}`);
}
