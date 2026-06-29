import type { ComparisonResult, DocumentSummary, ItemStatus, Project, ProjectItemsResponse } from "../types";

const TOKEN_KEY = "statuscheck_token";
const USER_KEY = "statuscheck_user";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function getStoredUsername(): string | null {
  return localStorage.getItem(USER_KEY);
}

export function setAuth(token: string, username: string): void {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, username);
}

export function clearAuth(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

/** Authorization header for direct fetch() calls (e.g. file downloads). */
export function authHeaders(): Record<string, string> {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = { ...authHeaders() };
  if (!(init?.body instanceof FormData)) {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(`/api${path}`, {
    ...init,
    headers: { ...headers, ...((init?.headers as Record<string, string>) ?? {}) }
  });

  if (response.status === 401) {
    // Token missing/expired - drop it and let the app fall back to the login page.
    clearAuth();
    window.dispatchEvent(new Event("auth:logout"));
    throw new Error("Your session has expired. Please sign in again.");
  }

  if (!response.ok) {
    let message = `Request failed with status ${response.status}`;
    try {
      const body = await response.json();
      if (body?.error) message = body.error;
    } catch {
      // response had no JSON body - keep the generic message
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export interface AuthResult {
  token: string;
  username: string;
}

export const api = {
  register: (username: string, password: string) =>
    request<AuthResult>("/auth/register", { method: "POST", body: JSON.stringify({ username, password }) }),

  login: (username: string, password: string) =>
    request<AuthResult>("/auth/login", { method: "POST", body: JSON.stringify({ username, password }) }),

  listProjects: () => request<Project[]>("/projects"),

  createProject: (name: string, description: string) =>
    request<Project>("/projects", {
      method: "POST",
      body: JSON.stringify({ name, description })
    }),

  getProject: (id: number) => request<Project>(`/projects/${id}`),

  deleteProject: (id: number) => request<void>(`/projects/${id}`, { method: "DELETE" }),

  uploadProposal: (projectId: number, file: File) => {
    const form = new FormData();
    form.append("file", file);
    return request<DocumentSummary>(`/projects/${projectId}/proposal`, { method: "POST", body: form });
  },

  uploadStatusReport: (projectId: number, file: File) => {
    const form = new FormData();
    form.append("file", file);
    return request<DocumentSummary>(`/projects/${projectId}/status-reports`, { method: "POST", body: form });
  },

  getItems: (projectId: number) => request<ProjectItemsResponse>(`/projects/${projectId}/items`),

  runComparison: (projectId: number) =>
    request<ComparisonResult[]>(`/projects/${projectId}/comparison/run`, { method: "POST" }),

  getComparison: (projectId: number) => request<ComparisonResult[]>(`/projects/${projectId}/comparison`),

  updateComparison: (
    projectId: number,
    resultId: number,
    update: { status: ItemStatus; evidence: string | null }
  ) =>
    request<ComparisonResult>(`/projects/${projectId}/comparison/${resultId}`, {
      method: "PATCH",
      body: JSON.stringify(update)
    }),

  reportUrl: (projectId: number, kind: "word" | "ppt") => `/api/projects/${projectId}/report/${kind}`
};
