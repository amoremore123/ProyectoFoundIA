"""Las respuestas de error de la API de Django traen `status` y `mensaje`, como Spring, sin quitar `detail`."""
from datetime import timedelta

import bcrypt
from rest_framework.test import APITestCase

from .models import Campana, Usuario, hoy_en_lima
from .tests import PASSWORD, token_de


class FormatoDeErrorTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        self.admin = Usuario.objects.create(nombre="Admin", apellido="QA", correo="admin-err@prueba.invalid",
                                            password=hash_, rol="ADMIN", estado="ACTIVO")
        self.ana = Usuario.objects.create(nombre="Ana", apellido="QA", correo="ana-err@prueba.invalid",
                                          password=hash_, rol="USUARIO", estado="ACTIVO")

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def test_401_sin_sesion(self):
        cuerpo = self.client.get("/api/admin/campanas/").json()
        self.assertEqual(cuerpo["status"], 401)
        self.assertEqual(cuerpo["mensaje"], cuerpo["detail"])
        self.assertTrue(cuerpo["mensaje"])

    def test_403_sin_permiso(self):
        self.como(self.ana)
        r = self.client.get("/api/admin/campanas/")
        self.assertEqual(r.status_code, 403)
        self.assertEqual(r.json(), {"detail": "No autorizado: se requiere rol ADMIN",
                                    "mensaje": "No autorizado: se requiere rol ADMIN", "status": 403})

    def test_404_y_405(self):
        self.como(self.admin)
        cuerpo = self.client.get("/api/admin/campanas/999999/").json()
        self.assertEqual((cuerpo["status"], cuerpo["mensaje"]), (404, cuerpo["detail"]))
        # get_object_or_404 respondía en inglés («No Campana matches the given query.»)
        self.assertEqual(cuerpo["mensaje"], "No encontrado.")
        cuerpo = self.client.delete("/api/admin/campanas/999999/").json()
        self.assertEqual(cuerpo["status"], 405)
        self.assertIn("DELETE", cuerpo["mensaje"])

    def test_errores_por_campo_se_conservan_y_se_resume_el_primero(self):
        self.como(self.admin)
        r = self.client.post("/api/admin/campanas/", {"nombre": "ab", "origen": "Origen Malo"}, format="json")
        self.assertEqual(r.status_code, 400)
        cuerpo = r.json()
        self.assertIn("nombre", cuerpo)          # los errores por campo siguen igual
        self.assertIsInstance(cuerpo["nombre"], list)
        self.assertEqual(cuerpo["status"], 400)
        self.assertTrue(cuerpo["mensaje"].startswith("nombre: "))

    def test_una_lista_se_convierte_en_objeto(self):
        # ValidationError con un texto suelto: DRF responde una lista
        self.como(self.admin)
        r = self.client.get("/api/admin/dashboard/?filtro=raro")
        self.assertEqual(r.status_code, 400)
        cuerpo = r.json()
        self.assertEqual(cuerpo["status"], 400)
        self.assertEqual(cuerpo["mensaje"], "Filtros admitidos: desde y hasta (AAAA-MM-DD).")
        self.assertEqual(cuerpo["detail"], cuerpo["mensaje"])
        self.assertEqual(cuerpo["errores"], ["Filtros admitidos: desde y hasta (AAAA-MM-DD)."])

    def test_respuesta_armada_a_mano(self):
        # finalizar una campaña que aún no empieza responde {"detail": ...} construido en la vista
        self.como(self.admin)
        hoy = hoy_en_lima()
        campana = Campana.objects.create(nombre="Futura", origen="futura-err", fecha_inicio=hoy + timedelta(days=3),
                                         fecha_fin=hoy + timedelta(days=5), creada_por=self.admin)
        cuerpo = self.client.post(f"/api/admin/campanas/{campana.id}/finalizar/").json()
        self.assertEqual(cuerpo["status"], 400)
        self.assertEqual(cuerpo["mensaje"], cuerpo["detail"])

    def test_un_campo_que_se_llama_mensaje_no_se_pierde(self):
        # H69 tiene un campo «mensaje»: su error se conserva en «errores» y «mensaje» queda como texto
        self.como(self.admin)
        r = self.client.post("/api/admin/envios-masivos/vista-previa/",
                             {"titulo": "Aviso", "mensaje": "", "destinatarios": "TODOS"}, format="json")
        self.assertEqual(r.status_code, 400)
        cuerpo = r.json()
        self.assertIsInstance(cuerpo["mensaje"], str)
        self.assertTrue(cuerpo["mensaje"].startswith("mensaje: "))
        self.assertIsInstance(cuerpo["errores"]["mensaje"], list)

    def test_las_respuestas_correctas_no_cambian(self):
        self.como(self.admin)
        cuerpo = self.client.get("/api/admin/campanas/").json()
        self.assertNotIn("mensaje", cuerpo)
        self.assertNotIn("status", cuerpo)
        self.assertIn("results", cuerpo)

    def test_fuera_de_la_api_no_se_toca(self):
        r = self.client.get("/no-existe-fuera-de-la-api/")
        self.assertEqual(r.status_code, 404)
        self.assertNotIn(b'"mensaje"', r.content)
