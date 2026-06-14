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

/**
 * 通过带 JWT 的请求下载作业 ZIP。
 * 不能用 <a href>/window.open 直接导航——那样浏览器不会带上 Authorization 头，
 * 后端会 401。改为用 axios client 取回 blob，再用 object URL 触发下载。
 */
export async function downloadJobZip(jobId: number): Promise<void> {
  const resp = await client.get(`/jobs/${jobId}/download`, { responseType: "blob" });
  const blob = new Blob([resp.data], { type: "application/zip" });
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `etl_job_${jobId}.zip`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(url);
}
