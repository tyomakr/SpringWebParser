const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8280';

export async function apiRequest(path, options = {}) {
  const { token, body, headers, ...rest } = options;
  const finalHeaders = {
    ...headers,
  };
  if (token) {
    finalHeaders.Authorization = `Bearer ${token}`;
  }
  let payload = body;
  if (body && typeof body === 'object' && !(body instanceof FormData)) {
    finalHeaders['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...rest,
    headers: finalHeaders,
    body: payload,
  });
  if (!response.ok) {
    const text = await response.text();
    const message = text || `Request failed: ${response.status}`;
    throw new Error(message);
  }
  if (response.status === 204) {
    return null;
  }
  return response.json();
}

export function recommendationBackfill(token, limit) {
  const body = typeof limit === 'number' ? { limit } : undefined;
  return apiRequest('/api/recommendations/backfill', {
    method: 'POST',
    token,
    body,
  });
}

export function recommendationTop(token, referenceAttachmentId, limit = 20) {
  const query = new URLSearchParams({
    referenceAttachmentId,
    limit: String(limit),
  });
  return apiRequest(`/api/recommendations/top?${query.toString()}`, { token });
}

export function recommendationFeedback(token, payload) {
  return apiRequest('/api/recommendations/feedback', {
    method: 'POST',
    token,
    body: payload,
  });
}
