import { client } from "./client";
import type { Conversation, Message, SchemaColumn, EtlStepProposal, TableDetailOut } from "./types";

export async function createConversation(projectId: number, tableIds: number[] = [], name?: string): Promise<Conversation> {
  const resp = await client.post<Conversation>(`/projects/${projectId}/conversations`, { name, table_ids: tableIds });
  return resp.data;
}

export async function updateConversation(conversationId: number, name: string): Promise<Conversation> {
  const resp = await client.put<Conversation>(`/conversations/${conversationId}`, { name });
  return resp.data;
}

export async function deleteConversation(conversationId: number): Promise<void> {
  await client.delete(`/conversations/${conversationId}`);
}

export async function getConversationTables(conversationId: number): Promise<TableDetailOut[]> {
  const resp = await client.get<TableDetailOut[]>(`/conversations/${conversationId}/tables`);
  return resp.data;
}

export async function setConversationTables(conversationId: number, tableIds: number[]): Promise<Conversation> {
  const resp = await client.put<Conversation>(`/conversations/${conversationId}/tables`, { table_ids: tableIds });
  return resp.data;
}

export async function listConversations(projectId: number): Promise<Conversation[]> {
  const resp = await client.get<Conversation[]>(`/projects/${projectId}/conversations`);
  return resp.data;
}

export async function sendChat(conversationId: number, message: string): Promise<void> {
  await client.post(`/conversations/${conversationId}/chat`, { message });
}

export async function confirmSchema(
  conversationId: number,
  targetTable: string,
  columns: SchemaColumn[]
): Promise<Conversation> {
  const resp = await client.post<Conversation>(`/conversations/${conversationId}/confirm-schema`, {
    target_table: targetTable,
    columns,
  });
  return resp.data;
}

export async function confirmSteps(conversationId: number, steps: EtlStepProposal[]): Promise<Conversation> {
  const resp = await client.post<Conversation>(`/conversations/${conversationId}/confirm-steps`, { steps });
  return resp.data;
}

export async function getMessages(conversationId: number): Promise<Message[]> {
  const resp = await client.get<Message[]>(`/conversations/${conversationId}/messages`);
  return resp.data;
}
