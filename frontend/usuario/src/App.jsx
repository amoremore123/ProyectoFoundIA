import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { TemaProvider } from './context/TemaContext';
import { ToastProvider } from './context/ToastContext';
import MainLayout from './layouts/MainLayout';
import Inicio from './pages/Inicio';
import HistorialObjetos from './components/HistorialObjetos';
import Portada from './pages/Portada';
import Login from './pages/Login';
import Registro from './pages/Registro';
import Verificar from './pages/Verificar';
import Buscar from './pages/Buscar';
import Publicar from './pages/Publicar';
import Detalle from './pages/Detalle';
import DetalleCoincidencia from './pages/DetalleCoincidencia';
import Perfil from './pages/Perfil';
import MisPublicaciones from './pages/MisPublicaciones';
import Notificaciones from './pages/Notificaciones';
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
              <Route path="/registro" element={<Registro />} />
              <Route path="/verificar" element={<Verificar />} />
              <Route element={<MainLayout />}>
                <Route path="/" element={<Portada />} />
                <Route path="/explorar" element={<Inicio />} />
                <Route path="/historial" element={EXTRAS_HABILITADOS ? <HistorialObjetos /> : <Navigate to="/explorar" replace />} />
                <Route path="/buscar" element={<Buscar />} />
                <Route path="/objeto/:id" element={<Detalle />} />
                <Route path="/coincidencias/:id" element={<DetalleCoincidencia />} />
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
                  path="/mis-publicaciones/:id/editar"
                  element={
                    <RutaProtegida>
                      <Publicar />
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
      </ToastProvider>
    </TemaProvider>
  );
}
