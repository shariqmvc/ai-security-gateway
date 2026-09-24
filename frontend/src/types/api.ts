export type Provider="OPENAI"|"ANTHROPIC"|"GEMINI"|"OLLAMA";
export interface PersonalUser{userId:string;accountId:string;email:string;displayName:string|null;plan:string;status:string;emailVerified:boolean}
export interface LoginResponse{accessToken:string;tokenType:string;expiresInSeconds:number;user:PersonalUser}
export interface SignupResponse{userId:string;accountId:string;email:string;displayName:string|null;plan:string;status:string;emailVerified:boolean;emailVerificationRequired:boolean;verificationToken:string|null}
export interface ModelCatalogItem{provider:Provider;modelId:string;displayName:string;capabilities:string[];enabled:boolean}
export interface UsageSummary{requestsToday:number;successfulRequestsToday:number;failedRequestsToday:number;requestsThisMonth:number;inputTokensThisMonth:number;outputTokensThisMonth:number;totalTokensThisMonth:number;costThisMonth:number|string;cacheHitsThisMonth:number;contextTokensSavedThisMonth:number;quotaRequestsRemainingToday:number;quotaTokensRemainingThisMonth:number}
export interface CreditWallet{id:string;personalAccountId:string;balance:number|string;reservedBalance:number|string;availableBalance?:number|string;createdAt:string;updatedAt:string}
export interface CreditLedgerEntry{id:string;personalAccountId:string;entryType:string;amount:number|string;referenceId:string|null;reservationId:string|null;description:string|null;createdAt:string}
export interface ApiKey{id:string;name:string;keyPrefix:string;scopes:string[];createdAt:string;lastUsedAt:string|null;expiresAt:string|null;revokedAt:string|null;active:boolean}
export interface ApiKeyCreateResponse extends Omit<ApiKey,"lastUsedAt"|"revokedAt"|"active">{apiKey:string}
export interface ProviderConnection{id:string;provider:Provider;displayName:string;status:string;maskedCredential:string;lastValidatedAt:string|null;validationMessage:string|null;createdAt:string;updatedAt:string}