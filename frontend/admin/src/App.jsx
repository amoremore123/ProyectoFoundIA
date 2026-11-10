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
import Estadisticas from './pages/Estadisticas';
import Custodia from './pages/Custodia';
import Reclamos from './pages/Reclamos';
import Vencidos from './pages/Vencidos';
import { inicioDe, puedeVer } from './config/permisos';

function RutaProtegida({ children }) {
  const { token } = useAuth();
  if (!token) return <Navigate to="/login" replace />;
  return children;
}

// H36: si el rol no puede ver la ruta, se lo lleva a su inicio sin pedir nada a la API
function SoloRol({ ruta, children }) {
  const { user } = useAuth();
  if (!puedeVer(user, ruta)) return <Navigate to={inicioDe(user)} replace />;
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
                <Route path="/" element={<SoloRol ruta="/"><Dashboard /></SoloRol>} />
                <Route path="/usuarios" element={<SoloRol ruta="/usuarios"><Usuarios /></SoloRol>} />
                <Route path="/publicaciones" element={<SoloRol ruta="/publicaciones"><Publicaciones /></SoloRol>} />
                {/* H28 y H26 (Sprint 2) */}
                <Route path="/reportes" element={<SoloRol ruta="/reportes"><Reportes /></SoloRol>} />
                <Route path="/categorias" element={<SoloRol ruta="/categorias"><Categorias /></SoloRol>} />
                <Route path="/estadisticas" element={<SoloRol ruta="/estadisticas"><Estadisticas /></SoloRol>} />
                {/* H41 y H43 (Sprint 3): BIBLIOTECA y ADMIN */}
                <Route path="/custodia" element={<SoloRol ruta="/custodia"><Custodia /></SoloRol>} />
                <Route path="/reclamos" element={<SoloRol ruta="/reclamos"><Reclamos /></SoloRol>} />
                {/* H54 */}
                <Route path="/vencidos" element={<SoloRol ruta="/vencidos"><Vencidos /></SoloRol>} />
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </ToastProvider>
    </TemaProvider>
  );
}
