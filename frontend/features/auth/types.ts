export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  role: "USER" | "ADMIN";
}

export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresIn: number;
  user: UserProfile;
}

export interface RegisterPayload {
  fullName: string;
  email: string;
  password: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}
