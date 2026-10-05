const AUTH_URL = process.env.REACT_APP_AUTH_URL || 'http://localhost:8081';

export interface UserSession {
  authenticated: boolean;
  userId?: string;
  username?: string;
  email?: string;
  identityProvider?: string;
  roles: string[];
  accessTokenExpiresAt?: string;
}

export async function fetchSession(): Promise<UserSession | null> {
  const response = await fetch(`${AUTH_URL}/auth/session`, { credentials: 'include' });
  if (response.status === 401) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`Auth service responded with ${response.status}`);
  }
  return response.json();
}

export async function refreshSession(): Promise<UserSession | null> {
  const response = await fetch(`${AUTH_URL}/auth/refresh`, {
    method: 'POST',
    credentials: 'include',
  });
  if (response.status === 401) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`Auth service responded with ${response.status}`);
  }
  return response.json();
}

export function startLogin(returnTo?: string): void {
  const target = returnTo ? `?returnTo=${encodeURIComponent(returnTo)}` : '';
  window.location.href = `${AUTH_URL}/auth/login${target}`;
}

export async function logout(): Promise<void> {
  await fetch(`${AUTH_URL}/auth/logout`, { method: 'POST', credentials: 'include' });
}
