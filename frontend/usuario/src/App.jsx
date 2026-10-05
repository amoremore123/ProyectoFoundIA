import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import MainLayout from './layouts/MainLayout';
import Inicio from './pages/Inicio';
import Login from './pages/Login';
import Registro from './pages/Registro';
import Verificar from './pages/Verificar';
import Buscar from './pages/Buscar';
import Publicar from './pages/Publicar';
import Detalle from './pages/Detalle';
import Perfil from './pages/Perfil';
import MisPublicaciones from './pages/MisPublicaciones';
import Notificaciones from './pages/Notificaciones';

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
          <Route path="/registro" element={<Registro />} />
          <Route path="/verificar" element={<Verificar />} />
          <Route element={<MainLayout />}>
            <Route path="/" element={<Inicio />} />
            <Route path="/buscar" element={<Buscar />} />
            <Route path="/objeto/:id" element={<Detalle />} />
            <Route
              path="/publicar"
              element={
                <RutaProtegida>
                  <Publicar />
                </RutaProtegida>
              }
            />
            <Route
              path="/perfil"
              element={
                <RutaProtegida>
                  <Perfil />
                </RutaProtegida>
              }
            />
            <Route
              path="/mis-publicaciones"
              element={
                <RutaProtegida>
                  <MisPublicaciones />
                </RutaProtegida>
              }
            />
            <Route
              path="/notificaciones"
              element={
                <RutaProtegida>
                  <Notificaciones />
                </RutaProtegida>
              }
            />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
