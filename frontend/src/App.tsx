import { BrowserRouter, Link, Navigate, Route, Routes } from "react-router-dom";
import ProjectsPage from "./pages/ProjectsPage";
import ProjectDetailPage from "./pages/ProjectDetailPage";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import { AuthProvider, useAuth } from "./auth/AuthContext";

function AppShell() {
  const { isAuthenticated, username, logout } = useAuth();

  return (
    <BrowserRouter>
      <header className="app-header">
        <div className="app-header__inner">
          <Link to="/" className="app-header__title">
            <span className="app-header__mark">SC</span>
            <span>Statuscheck</span>
          </Link>
          <span className="app-header__subtitle">proposal vs status report</span>
          {isAuthenticated && (
            <div className="app-header__user">
              <span className="muted">{username}</span>
              <button className="btn btn-quiet" onClick={logout}>
                Sign out
              </button>
            </div>
          )}
        </div>
      </header>
      <div className="shell">
        <Routes>
          {isAuthenticated ? (
            <>
              <Route path="/" element={<ProjectsPage />} />
              <Route path="/projects/:projectId" element={<ProjectDetailPage />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </>
          ) : (
            <>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route path="*" element={<Navigate to="/login" replace />} />
            </>
          )}
        </Routes>
      </div>
    </BrowserRouter>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <AppShell />
    </AuthProvider>
  );
}
