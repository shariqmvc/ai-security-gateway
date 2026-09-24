import {getAccessToken,clearSession} from "./auth";
import type {LoginResponse,SignupResponse,PersonalUser,ModelCatalogItem,UsageSummary,CreditWallet,CreditLedgerEntry,ApiKey,ApiKeyCreateResponse,Provider,ProviderConnection} from "../types/api";
const API_BASE=(import.meta.env.VITE_API_BASE_URL??"http://localhost:8080").replace(/\/$/,"");
class ApiError extends Error{status:number;payload:unknown;constructor(status:number,payload:unknown){super(typeof payload==="object"&&payload&&"message" in payload?String((payload as {message?:unknown}).message):`Request failed with HTTP ${status}`);this.status=status;this.payload=payload}}
async function request<T>(path:string,init:RequestInit={},auth=true):Promise<T>{const headers=new Headers(init.headers);headers.set("Accept","application/json");if(init.body&&!(init.body instanceof FormData))headers.set("Content-Type","application/json");if(auth){const token=getAccessToken();if(token)headers.set("Authorization",`Bearer ${token}`)}const response=await fetch(`${API_BASE}${path}`,{...init,headers});const text=await response.text();let payload:unknown=null;try{payload=text?JSON.parse(text):null}catch{payload=text}if(response.status===401&&auth)clearSession();if(!response.ok)throw new ApiError(response.status,payload);return payload as T}
export const api={
login:(email:string,password:string)=>request<LoginResponse>("/public/auth/personal/login",{method:"POST",body:JSON.stringify({email,password})},false),
signup:(email:string,password:string,displayName:string)=>request<SignupResponse>("/public/auth/personal/signup",{method:"POST",body:JSON.stringify({email,password,displayName})},false),
logout:()=>request<void>("/public/auth/personal/logout",{method:"POST"}),
me:()=>request<PersonalUser>("/api/personal/me"),
models:async()=>{const result=await request<{data:ModelCatalogItem[]}>("/api/models");return result.data},
usageSummary:()=>request<UsageSummary>("/api/personal/usage/summary"),
wallet:()=>request<CreditWallet>("/api/personal/credits/wallet"),
ledger:()=>request<CreditLedgerEntry[]>("/api/personal/credits/ledger"),
apiKeys:()=>request<ApiKey[]>("/api/personal/api-keys"),
createApiKey:(body:{name:string;scopes:string[];expiresInDays?:number})=>request<ApiKeyCreateResponse>("/api/personal/api-keys",{method:"POST",body:JSON.stringify(body)}),
revokeApiKey:(id:string)=>request<void>(`/api/personal/api-keys/${id}`,{method:"DELETE"}),
rotateApiKey:(id:string)=>request<ApiKeyCreateResponse>(`/api/personal/api-keys/${id}/rotate`,{method:"POST"}),
providers:()=>request<ProviderConnection[]>("/api/personal/providers"),
connectProvider:(body:{provider:Provider;displayName:string;apiKey:string})=>request<ProviderConnection>("/api/personal/providers",{method:"POST",body:JSON.stringify(body)}),
validateProvider:(provider:Provider)=>request<{provider:Provider;valid:boolean;status:string;message:string;validatedAt:string}>(`/api/personal/providers/${provider}/validate`,{method:"POST"}),
disconnectProvider:(provider:Provider)=>request<void>(`/api/personal/providers/${provider}`,{method:"DELETE"}),
knowledgeBases:()=>request<unknown[]>("/api/knowledge-bases"),
chat:(body:Record<string,unknown>)=>request<{success:boolean;message:string;data:unknown}>("/api/chat",{method:"POST",body:JSON.stringify(body)}),
createPaymentIntent:(packageCode:string)=>request<Record<string,unknown>>("/api/personal/billing/intents",{method:"POST",body:JSON.stringify({packageCode,idempotencyKey:crypto.randomUUID()})}),
securityAssess:(prompt:string)=>request<Record<string,unknown>>("/api/personal/security/assess",{method:"POST",headers:{"X-Request-ID":crypto.randomUUID()},body:JSON.stringify({prompt})})
}