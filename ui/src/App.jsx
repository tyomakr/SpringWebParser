import React from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './store/auth.jsx';
import { ThemeProvider } from './store/theme.jsx';
import LoginPage from './pages/LoginPage.jsx';
import PipelinePage from './pages/PipelinePage.jsx';
import RecommendationsPage from './pages/RecommendationsPage.jsx';

function RequireAuth({ children }) {
  const { token } = useAuth();
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return children;
}

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              path="/pipeline"
              element={(
                <RequireAuth>
                  <PipelinePage />
                </RequireAuth>
              )}
            />
            <Route
              path="/recommendations"
              element={(
                <RequireAuth>
                  <RecommendationsPage />
                </RequireAuth>
              )}
            />
            <Route path="/" element={<Navigate to="/pipeline" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </ThemeProvider>
  );
}
