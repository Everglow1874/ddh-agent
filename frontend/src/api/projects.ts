import { client } from "./client";
import type { Project } from "./types";

export async function listProjects(): Promise<Project[]> {
  const resp = await client.get<Project[]>("/projects");
  return resp.data;
}

export async function createProject(name: string, description?: string): Promise<Project> {
  const resp = await client.post<Project>("/projects", { name, description });
  return resp.data;
}

export async function getProject(id: number): Promise<Project> {
  const resp = await client.get<Project>(`/projects/${id}`);
  return resp.data;
}

export async function updateProject(
  id: number,
  data: Partial<Pick<Project, "name" | "description" | "status">>
): Promise<Project> {
  const resp = await client.put<Project>(`/projects/${id}`, data);
  return resp.data;
}

export async function deleteProject(id: number): Promise<void> {
  await client.delete(`/projects/${id}`);
}

export async function associateTables(projectId: number, tableIds: number[]): Promise<{ associated: number }> {
  const resp = await client.post<{ associated: number }>(`/projects/${projectId}/tables`, { table_ids: tableIds });
  return resp.data;
}

export async function removeTableFromProject(projectId: number, tableId: number): Promise<void> {
  await client.delete(`/projects/${projectId}/tables/${tableId}`);
}
