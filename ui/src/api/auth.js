import { apiRequest } from './client.js';

export function loginRequest(username, password) {
  return apiRequest('/api/auth/login', {
    method: 'POST',
    body: { username, password },
  });
}
