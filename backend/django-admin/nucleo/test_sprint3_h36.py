"""H36: rol BIBLIOTECA, permisos y auditoría de cambios de rol."""
import bcrypt
from rest_framework.test import APITestCase

from .models import CambioRol, Usuario
from .tests import PASSWORD, token_de


class RolBibliotecaTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        self.admin = Usuario.objects.create(
            nombre="Admin", apellido="QA", correo="admin-h36@prueba.invalid",
            password=hash_, rol="ADMIN", estado="ACTIVO")
        self.biblioteca = Usuario.objects.create(
            nombre="Biblioteca", apellido="QA", correo="biblioteca-h36@prueba.invalid",
            password=hash_, rol="BIBLIOTECA", estado="ACTIVO")
        self.ana = Usuario.objects.create(
            nombre="Ana", apellido="QA", correo="ana-h36@prueba.invalid",
            password=hash_, rol="USUARIO", estado="ACTIVO")

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def test_biblioteca_inicia_sesion_pero_no_entra_a_usuarios(self):
        r = self.client.post("/api/admin/auth/login/", {
            "correo": self.biblioteca.correo, "password": PASSWORD}, format="json")
        self.assertEqual(r.status_code, 200)
        self.assertIn("token", r.data)
        self.como(self.biblioteca)
        self.assertEqual(self.client.get("/api/admin/usuarios/").status_code, 403)
        self.assertEqual(self.client.get("/api/admin/dashboard/").status_code, 403)

    def test_solo_admin_cambia_rol_y_queda_auditado(self):
        self.como(self.biblioteca)
        r = self.client.patch(f"/api/admin/usuarios/{self.ana.id}/",
                              {"rol": "BIBLIOTECA", "confirmar": True}, format="json")
        self.assertEqual(r.status_code, 403)

        self.como(self.admin)
        r = self.client.patch(f"/api/admin/usuarios/{self.ana.id}/",
                              {"rol": "BIBLIOTECA", "confirmar": True,
                               "motivo": "Apoya la custodia"}, format="json")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data["rol"], "BIBLIOTECA")
        self.assertEqual(r.data["cambioRol"]["rolAnterior"], "USUARIO")
        self.assertEqual(r.data["cambioRol"]["rolNuevo"], "BIBLIOTECA")
        self.assertEqual(r.data["cambioRol"]["cambiadoPor"], self.admin.correo)
        self.ana.refresh_from_db()
        self.assertEqual(self.ana.rol, "BIBLIOTECA")
        cambio = CambioRol.objects.get(usuario=self.ana)
        self.assertEqual((cambio.rol_anterior, cambio.rol_nuevo), ("USUARIO", "BIBLIOTECA"))

        r = self.client.get(f"/api/admin/usuarios/{self.ana.id}/cambios-rol/")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data[0]["rolNuevo"], "BIBLIOTECA")

    def test_cambio_sin_confirmacion_no_modifica(self):
        self.como(self.admin)
        for cuerpo in ({"rol": "BIBLIOTECA"}, {"rol": "BIBLIOTECA", "confirmar": False},
                       {"rol": "OTRO", "confirmar": True},
                       {"rol": "BIBLIOTECA", "confirmar": True, "estado": "ACTIVO"}):
            r = self.client.patch(f"/api/admin/usuarios/{self.ana.id}/", cuerpo, format="json")
            self.assertEqual(r.status_code, 400, cuerpo)
        self.ana.refresh_from_db()
        self.assertEqual(self.ana.rol, "USUARIO")
        self.assertFalse(CambioRol.objects.exists())

    def test_admin_no_se_quita_su_propio_rol(self):
        self.como(self.admin)
        r = self.client.patch(f"/api/admin/usuarios/{self.admin.id}/",
                              {"rol": "BIBLIOTECA", "confirmar": True}, format="json")
        self.assertEqual(r.status_code, 400)
        self.admin.refresh_from_db()
        self.assertEqual(self.admin.rol, "ADMIN")
        self.assertFalse(CambioRol.objects.exists())
