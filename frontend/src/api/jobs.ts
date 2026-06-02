import { client } from "./client";
import type { EtlJob } from "./types";

export async function listJobs(projectId: number): Promise<EtlJob[]> {
  const resp = await client.get<EtlJob[]>(`/projects/${projectId}/jobs`);
  return resp.data;
}

export async function getJob(jobId: number): Promise<EtlJob> {
  const resp = await client.get<EtlJob>(`/jobs/${jobId}`);
  return resp.data;
}

export async function getStepSql(
  jobId: number,
  stepId: number
): Promise<{ step_id: number; step_name: string; sql: string }> {
  const resp = await client.get(`/jobs/${jobId}/steps/${stepId}/sql`);
  return resp.data;
}

export function downloadUrl(jobId: number): string {
  return `/api/jobs/${jobId}/download`;
}
