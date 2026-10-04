import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import AdminLayout from './layouts/AdminLayout';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Usuarios from './pages/Usuarios';
import Publicaciones from './pages/Publicaciones';
import Reportes from './pages/Reportes';
import Categorias from './pages/Categorias';

function RutaProtegida({ children }) {
  const { token } = useAuth();
  if (!token) return <Navigate to="/login" replace />;
  return children;
}

export default function App() {
  return (
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
            <Route path="/reportes" element={<Reportes />} />
            <Route path="/categorias" element={<Categorias />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
