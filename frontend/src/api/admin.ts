import { client } from "./client";
import type { LLMConfig } from "./types";

export async function getConfig(): Promise<LLMConfig> {
  const resp = await client.get<LLMConfig>("/admin/config");
  return resp.data;
}

export async function updateConfig(provider: string, model: string): Promise<LLMConfig> {
  const resp = await client.put<LLMConfig>("/admin/config", { provider, model });
  return resp.data;
}
