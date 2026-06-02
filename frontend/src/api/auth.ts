import { client } from "./client";
import type { TokenResponse, User } from "./types";

export async function register(username: string, email: string, password: string): Promise<User> {
  const resp = await client.post<User>("/auth/register", { username, email, password });
  return resp.data;
}

export async function login(username: string, password: string): Promise<TokenResponse> {
  const resp = await client.post<TokenResponse>("/auth/login", { username, password });
  return resp.data;
}
