const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

export class ApiError extends Error {
  constructor(code, status) {
    super(code);
    this.code = code;
    this.status = status;
  }
}

async function parseJsonSafe(res) {
  try {
    return await res.json();
  } catch {
    return null;
  }
}

async function request(path, options) {
  const res = await fetch(`${API_BASE_URL}${path}`, options);
  const data = await parseJsonSafe(res);
  if (!res.ok) {
    throw new ApiError(data?.error, res.status);
  }
  return data;
}

export function login({ username, password, voucherCode }) {
  return request("/api/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password, voucherCode }),
  });
}

export function sendChatMessage(sessionId, message) {
  return request("/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ sessionId, message }),
  });
}

export function fetchSessionStatus(sessionId) {
  return request(`/api/session/${encodeURIComponent(sessionId)}`, { method: "GET" });
}

export function logoutSession(sessionId) {
  return request("/api/session/logout", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ sessionId }),
  });
}

export function fetchChatHistory(sessionId) {
  return request(`/api/chat/${encodeURIComponent(sessionId)}/history`, { method: "GET" });
}

export function extendSession(sessionId, voucherCode) {
  return request("/api/session/extend", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ sessionId, voucherCode }),
  });
}

export function exportChatTranscript(sessionId, email) {
  return request("/api/chat/export", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ sessionId, email }),
  });
}

export function fetchAdminSessions(adminKey) {
  return request("/api/admin/sessions", {
    method: "GET",
    headers: { "X-Admin-Key": adminKey },
  });
}

export function killSession(adminKey, sessionId) {
  return request(`/api/admin/sessions/${encodeURIComponent(sessionId)}`, {
    method: "DELETE",
    headers: { "X-Admin-Key": adminKey },
  });
}

export function issueCredential(adminKey, type) {
  return request("/api/admin/vouchers", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Admin-Key": adminKey },
    body: JSON.stringify({ type }),
  });
}
