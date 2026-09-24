const TOKEN_KEY="airouter.personal.accessToken";const USER_KEY="airouter.personal.user";
export function getAccessToken(){return localStorage.getItem(TOKEN_KEY)}
export function saveSession(token:string,user:unknown){localStorage.setItem(TOKEN_KEY,token);localStorage.setItem(USER_KEY,JSON.stringify(user))}
export function getStoredUser<T>():T|null{const raw=localStorage.getItem(USER_KEY);if(!raw)return null;try{return JSON.parse(raw) as T}catch{return null}}
export function clearSession(){localStorage.removeItem(TOKEN_KEY);localStorage.removeItem(USER_KEY)}
export function isAuthenticated(){return Boolean(getAccessToken())}