export interface UserDto {
  id: string;
  email: string;
  fullName: string;
  phone?: string | null;
  preferredLanguage?: string | null;
  status: string;
  roles: string[];
  organizationId?: string | null;
}

export interface AuthResponseDto {
  user: UserDto;
  accessToken: string;
}

export interface ApiResponse<T> {
  data: T;
  error: null | {
    code: string;
    message: string;
    details?: unknown;
  };
  correlationId?: string;
}

export interface RegisterRequestDto {
  email: string;
  password: string;
  fullName: string;
  role: "CUSTOMER" | "ORGANIZATION_ADMIN";
}

export interface LoginRequestDto {
  email: string;
  password: string;
}

export interface LoginResponse {
  user: UserDto;
  accessToken: string;
}

export interface RegisterResponse {
  user: UserDto;
  accessToken: string;
}

export type AuthResponse = LoginResponse;

export type RoleCode =
  | "SUPER_ADMIN"
  | "ORGANIZATION_ADMIN"
  | "BRANCH_ADMIN"
  | "STAFF"
  | "CUSTOMER";

export interface AuthError {
  code: string;
  message: string;
  details?: unknown;
}
