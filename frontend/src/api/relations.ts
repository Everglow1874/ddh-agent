import { client } from "./client";
import type { Relation, RelationSave, LineageGraph } from "./types";

export async function listRelations(): Promise<Relation[]> {
  const resp = await client.get<Relation[]>("/relations");
  return resp.data;
}

export async function createRelation(data: RelationSave): Promise<number> {
  const resp = await client.post<{ id: number }>("/relations", data);
  return resp.data.id;
}

export async function updateRelation(id: number, data: RelationSave): Promise<void> {
  await client.put(`/relations/${id}`, data);
}

export async function deleteRelation(id: number): Promise<void> {
  await client.delete(`/relations/${id}`);
}

export async function lineageGraph(tableIds: number[]): Promise<LineageGraph> {
  const resp = await client.post<LineageGraph>("/relations/graph", { tableIds });
  return resp.data;
}
