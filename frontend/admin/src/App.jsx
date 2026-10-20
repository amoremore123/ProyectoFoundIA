import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { TemaProvider } from './context/TemaContext';
import { ToastProvider } from './context/ToastContext';
import AdminLayout from './layouts/AdminLayout';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Usuarios from './pages/Usuarios';
import Publicaciones from './pages/Publicaciones';
import Reportes from './pages/Reportes';
import Categorias from './pages/Categorias';
import { EXTRAS_HABILITADOS } from './config/alcance';

function RutaProtegida({ children }) {
  const { token } = useAuth();
  if (!token) return <Navigate to="/login" replace />;
  return children;
}

export default function App() {
  return (
    <TemaProvider>
      <ToastProvider>
        <AuthProvider>
          <BrowserRouter>
            <Routes>
              <Route path="/login" element={<Login />} />
              <Route
                element={
                  <RutaProtegida>
                    <AdminLayout />
                  </RutaProtegida>
                }
              >
                <Route path="/" element={<Dashboard />} />
                <Route path="/usuarios" element={<Usuarios />} />
                <Route path="/publicaciones" element={<Publicaciones />} />
                {EXTRAS_HABILITADOS && <Route path="/reportes" element={<Reportes />} />}
                {EXTRAS_HABILITADOS && <Route path="/categorias" element={<Categorias />} />}
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </ToastProvider>
    </TemaProvider>
  );
}
