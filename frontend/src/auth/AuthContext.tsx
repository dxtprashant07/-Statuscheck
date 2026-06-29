import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { api, clearAuth, getStoredUsername, getToken, setAuth } from "../api/client";

interface AuthState {
  username: string | null;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(getToken());
  const [username, setUsername] = useState<string | null>(getStoredUsername());

  // The API layer fires this when a request returns 401 (expired/invalid token).
  useEffect(() => {
    const handleLogout = () => {
      setToken(null);
      setUsername(null);
    };
    window.addEventListener("auth:logout", handleLogout);
    return () => window.removeEventListener("auth:logout", handleLogout);
  }, []);

  async function login(u: string, p: string) {
    const result = await api.login(u, p);
    setAuth(result.token, result.username);
    setToken(result.token);
    setUsername(result.username);
  }

  async function register(u: string, p: string) {
    const result = await api.register(u, p);
    setAuth(result.token, result.username);
    setToken(result.token);
    setUsername(result.username);
  }

  function logout() {
    clearAuth();
    setToken(null);
    setUsername(null);
  }

  return (
    <AuthContext.Provider value={{ username, isAuthenticated: !!token, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return ctx;
}
