// O access token vive apenas nesta closure de módulo. Nunca vai para
// localStorage/sessionStorage: o refresh fica no cookie HttpOnly do backend.
let accessToken: string | null = null;

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string): void {
  accessToken = token;
}

export function clearAccessToken(): void {
  accessToken = null;
}
