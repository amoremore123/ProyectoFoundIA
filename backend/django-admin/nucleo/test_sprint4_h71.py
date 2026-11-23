"""H71: avisos destacados con vigencia, administrados desde el panel."""
from datetime import datetime, timedelta, timezone as dt_timezone
from unittest import mock

import bcrypt
from django.utils import timezone
from rest_framework.test import APITestCase

from . import views
from .models import AvisoDestacado, Campana, Usuario, hoy_en_lima
from .tests import PASSWORD, token_de

RUTA = "/api/admin/avisos-destacados/"


class AvisosDestacadosTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        self.admin = Usuario.objects.create(
            nombre="Admin", apellido="QA", correo="admin-h71@prueba.invalid",
            password=hash_, rol="ADMIN", estado="ACTIVO")
        self.biblioteca = Usuario.objects.create(
            nombre="Beto", apellido="Biblioteca", correo="biblioteca-h71@prueba.invalid",
            password=hash_, rol="BIBLIOTECA", estado="ACTIVO")
        self.hoy = hoy_en_lima()
        self.campana = Campana.objects.create(
            nombre="Semana de objetos perdidos", origen="semana-perdidos-2026", destino="/explorar",
            fecha_inicio=self.hoy, fecha_fin=self.hoy + timedelta(days=6), creada_por=self.admin)

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def cuerpo(self, **cambios):
        datos = {"titulo": "Semana de objetos perdidos",
                 "texto": "Revisa la mesa de objetos en la biblioteca.",
                 "destino": "/explorar",
                 "fechaInicio": str(self.hoy), "fechaFin": str(self.hoy + timedelta(days=6))}
        datos.update(cambios)
        return {k: v for k, v in datos.items() if v is not ...}

    def crear(self, **cambios):
        self.como(self.admin)
        return self.client.post(RUTA, self.cuerpo(**cambios), format="json")

    def aviso(self, inicio, fin, retirado=False, titulo="Aviso"):
        return AvisoDestacado.objects.create(
            titulo=titulo, texto="Texto del aviso", fecha_inicio=self.hoy + timedelta(days=inicio),
            fecha_fin=self.hoy + timedelta(days=fin), creado_por=self.admin,
            retirado_en=timezone.now() if retirado else None)

    def test_crear_con_ruta(self):
        r = self.crear()
        self.assertEqual(r.status_code, 201, r.content)
        self.assertEqual(r.data["estado"], "VIGENTE")
        self.assertEqual(r.data["enlace"], "/explorar")
        self.assertIsNone(r.data["campanaId"])
        self.assertEqual(r.data["creadoPor"]["id"], self.admin.id)
        self.assertIsNone(r.data["retiradoEn"])
        aviso = AvisoDestacado.objects.get(pk=r.data["id"])
        self.assertEqual(aviso.creado_por_id, self.admin.id)

    def test_crear_con_campana_usa_su_enlace_con_origen(self):
        r = self.crear(destino=..., campanaId=self.campana.id)
        self.assertEqual(r.status_code, 201, r.content)
        self.assertEqual(r.data["campanaId"], self.campana.id)
        self.assertEqual(r.data["enlace"], "/explorar?origen=semana-perdidos-2026")

    def test_sin_destino_ni_campana_no_tiene_enlace(self):
        r = self.crear(destino=None)
        self.assertEqual(r.status_code, 201, r.content)
        self.assertIsNone(r.data["enlace"])
        self.assertEqual(self.crear(destino="").data["enlace"], None)

    def test_validaciones(self):
        casos = [
            ({"titulo": "ab"}, "titulo"),
            ({"titulo": "x" * 81}, "titulo"),
            ({"texto": ""}, "texto"),
            ({"texto": "x" * 301}, "texto"),
            ({"destino": "https://otro.example/phishing"}, "destino"),
            ({"destino": "//otro.example"}, "destino"),
            ({"destino": "/explorar?x=1"}, "destino"),
            ({"campanaId": 999999, "destino": ...}, "campanaId"),
            ({"campanaId": None, "destino": "/explorar", "fechaFin": str(self.hoy - timedelta(days=1))}, "fechaFin"),
            ({"fechaInicio": "24/11/2026"}, "fechaInicio"),
            ({"fechaFin": ...}, "fechaFin"),
        ]
        for cambios, campo in casos:
            with self.subTest(cambios=cambios):
                r = self.crear(**cambios)
                self.assertEqual(r.status_code, 400, r.content)
                self.assertIn(campo, r.data)
        r = self.crear(campanaId=self.campana.id)  # con destino y campaña a la vez
        self.assertEqual(r.status_code, 400)
        self.assertIn("destino", r.data)
        self.assertEqual(AvisoDestacado.objects.count(), 0)

    def test_estado_por_fechas_de_lima(self):
        self.assertEqual(self.aviso(1, 3).estado(), "PROGRAMADO")
        self.assertEqual(self.aviso(-3, 0).estado(), "VIGENTE")  # el último día todavía se muestra
        self.assertEqual(self.aviso(0, 0).estado(), "VIGENTE")
        self.assertEqual(self.aviso(-5, -1).estado(), "VENCIDO")
        self.assertEqual(self.aviso(-1, 3, retirado=True).estado(), "RETIRADO")

    def test_hoy_es_el_de_lima_y_no_el_de_utc(self):
        # 2026-11-25 03:00 UTC son todavía las 22:00 del 24 en Lima
        instante = datetime(2026, 11, 25, 3, 0, tzinfo=dt_timezone.utc)
        with mock.patch("django.utils.timezone.now", return_value=instante):
            self.assertEqual(str(hoy_en_lima()), "2026-11-24")

    def test_filtro_por_estado(self):
        programado = self.aviso(2, 4)
        vigente = self.aviso(-1, 1)
        vencido = self.aviso(-9, -2)
        retirado = self.aviso(-1, 1, retirado=True)
        self.como(self.admin)
        esperado = {"PROGRAMADO": programado, "VIGENTE": vigente, "VENCIDO": vencido, "RETIRADO": retirado}
        for estado, aviso in esperado.items():
            with self.subTest(estado=estado):
                r = self.client.get(f"{RUTA}?estado={estado}")
                self.assertEqual([f["id"] for f in r.data["results"]], [aviso.id])
        self.assertEqual(self.client.get(f"{RUTA}?estado=ACTIVO").status_code, 400)
        self.assertEqual(self.client.get(RUTA).data["count"], 4)

    def test_editar_y_retirar(self):
        creado = self.crear().data
        self.assertEqual(self.client.patch(f"{RUTA}{creado['id']}/", {"texto": "Nuevo horario de la biblioteca."},
                                           format="json").status_code, 200)
        r = self.client.post(f"{RUTA}{creado['id']}/retirar/")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data["estado"], "RETIRADO")
        retirado_en = r.data["retiradoEn"]
        self.assertIsNotNone(retirado_en)
        # Retirar de nuevo no cambia nada
        self.assertEqual(self.client.post(f"{RUTA}{creado['id']}/retirar/").data["retiradoEn"], retirado_en)
        # Un retirado ya no se edita
        r = self.client.patch(f"{RUTA}{creado['id']}/", {"texto": "Otra vez"}, format="json")
        self.assertEqual(r.status_code, 400)
        self.assertEqual(AvisoDestacado.objects.get(pk=creado["id"]).texto, "Nuevo horario de la biblioteca.")

    def test_no_se_borra(self):
        creado = self.crear().data
        self.assertEqual(self.client.delete(f"{RUTA}{creado['id']}/").status_code, 405)
        self.assertEqual(AvisoDestacado.objects.count(), 1)

    def test_solo_admin(self):
        self.assertEqual(self.client.get(RUTA).status_code, 401)
        self.como(self.biblioteca)
        self.assertEqual(self.client.get(RUTA).status_code, 403)
        self.assertEqual(self.client.post(RUTA, self.cuerpo(), format="json").status_code, 403)
        self.assertEqual(AvisoDestacado.objects.count(), 0)
        self.assertEqual(self.client.get(f"{RUTA}999999/").status_code, 403)
        self.como(self.admin)
        self.assertEqual(self.client.get(f"{RUTA}999999/").status_code, 404)

    def test_la_vista_esta_registrada(self):
        self.assertTrue(hasattr(views, "AvisoDestacadoViewSet"))
