import { client } from "./client";
import type { DialectTypeRule, DialectFunctionRule } from "./types";

export type TypeRuleInput = Omit<DialectTypeRule, "id">;
export type FunctionRuleInput = Omit<DialectFunctionRule, "id">;

// ===== 类型规则 =====
export async function listTypeRules(): Promise<DialectTypeRule[]> {
  const resp = await client.get<DialectTypeRule[]>("/admin/dialect/types");
  return resp.data;
}
export async function createTypeRule(data: Partial<TypeRuleInput>): Promise<DialectTypeRule> {
  const resp = await client.post<DialectTypeRule>("/admin/dialect/types", data);
  return resp.data;
}
export async function updateTypeRule(id: number, data: Partial<TypeRuleInput>): Promise<DialectTypeRule> {
  const resp = await client.put<DialectTypeRule>(`/admin/dialect/types/${id}`, data);
  return resp.data;
}
export async function deleteTypeRule(id: number): Promise<void> {
  await client.delete(`/admin/dialect/types/${id}`);
}

// ===== 内部函数规则 =====
export async function listFunctionRules(): Promise<DialectFunctionRule[]> {
  const resp = await client.get<DialectFunctionRule[]>("/admin/dialect/functions");
  return resp.data;
}
export async function createFunctionRule(data: Partial<FunctionRuleInput>): Promise<DialectFunctionRule> {
  const resp = await client.post<DialectFunctionRule>("/admin/dialect/functions", data);
  return resp.data;
}
export async function updateFunctionRule(id: number, data: Partial<FunctionRuleInput>): Promise<DialectFunctionRule> {
  const resp = await client.put<DialectFunctionRule>(`/admin/dialect/functions/${id}`, data);
  return resp.data;
}
export async function deleteFunctionRule(id: number): Promise<void> {
  await client.delete(`/admin/dialect/functions/${id}`);
}
