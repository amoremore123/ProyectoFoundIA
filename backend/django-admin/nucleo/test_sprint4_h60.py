"""H60: campañas de difusión con enlace de origen, solo para ADMIN."""
import datetime as dt
from unittest import mock

import bcrypt
from django.test import override_settings
from rest_framework.test import APITestCase

from .models import Campana, Usuario, hoy_en_lima
from .tests import PASSWORD, token_de

RUTA = "/api/admin/campanas/"


@override_settings(FOUNDIA_WEB_URL="https://foundia.test")
class CampanasTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        crear = lambda rol, correo: Usuario.objects.create(
            nombre=rol.title(), apellido="QA", correo=correo, password=hash_, rol=rol, estado="ACTIVO")
        self.admin = crear("ADMIN", "admin-h60@prueba.invalid")
        self.otro_admin = crear("ADMIN", "admin2-h60@prueba.invalid")
        self.biblioteca = crear("BIBLIOTECA", "biblioteca-h60@prueba.invalid")
        self.usuario = crear("USUARIO", "ana-h60@prueba.invalid")
        self.hoy = hoy_en_lima()

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def datos(self, **cambios):
        base = {"nombre": "Semana de objetos perdidos", "descripcion": "Pabellones y redes",
                "origen": "semana-perdidos-2026", "destino": "/",
                "fechaInicio": str(self.hoy), "fechaFin": str(self.hoy + dt.timedelta(days=6))}
        base.update(cambios)
        return base

    def crear(self, **cambios):
        self.como(self.admin)
        r = self.client.post(RUTA, self.datos(**cambios), format="json")
        self.assertEqual(r.status_code, 201, r.data)
        return r.data

    def test_permisos(self):
        self.assertEqual(self.client.get(RUTA).status_code, 401)
        for rol in (self.biblioteca, self.usuario):
            self.como(rol)
            self.assertEqual(self.client.get(RUTA).status_code, 403)
            self.assertEqual(self.client.post(RUTA, self.datos(), format="json").status_code, 403)
        self.assertFalse(Campana.objects.exists())

    def test_crear_arma_el_enlace_y_pone_el_autor_del_servidor(self):
        c = self.crear(creadaPor={"id": self.otro_admin.id}, fechaCreacion="2000-01-01T00:00:00Z",
                       destino="/buscar")
        self.assertEqual(c["enlace"], "https://foundia.test/buscar?origen=semana-perdidos-2026")
        self.assertEqual(c["creadaPor"]["id"], self.admin.id)
        self.assertEqual(c["estado"], "ACTIVA")
        self.assertIsNone(c["finalizadaEn"])
        self.assertNotIn("2000", c["fechaCreacion"])
        self.assertNotIn("correo", str(c))

    def test_origen_unico_con_formato_y_sin_cambios(self):
        c = self.crear()
        for malo in ("semana-perdidos-2026", "Semana", "con espacio", "a_b", "ab", "-x-", "x" * 41):
            r = self.client.post(RUTA, self.datos(origen=malo), format="json")
            self.assertEqual(r.status_code, 400, malo)
            self.assertIn("origen", r.data)
        r = self.client.patch(f"{RUTA}{c['id']}/", {"origen": "otro-origen"}, format="json")
        self.assertEqual(r.status_code, 400)
        # Enviar el mismo origen al editar es válido
        r = self.client.patch(f"{RUTA}{c['id']}/", {"origen": c["origen"], "nombre": "Nuevo nombre"}, format="json")
        self.assertEqual(r.status_code, 200, r.data)
        self.assertEqual(r.data["nombre"], "Nuevo nombre")

    def test_fechas_y_destino_invalidos(self):
        self.como(self.admin)
        r = self.client.post(RUTA, self.datos(fechaInicio="2026-12-10", fechaFin="2026-12-01"), format="json")
        self.assertEqual(r.status_code, 400)
        self.assertIn("fechaFin", r.data)
        for destino in ("https://otro.com", "//otro.com", "buscar", "/x?y=1", "/x#y", "\\\\otro"):
            r = self.client.post(RUTA, self.datos(origen="destino-malo", destino=destino), format="json")
            self.assertEqual(r.status_code, 400, destino)
        for falta in ("nombre", "origen", "fechaInicio", "fechaFin"):
            datos = self.datos(origen="falta-campo"); datos.pop(falta)
            self.assertEqual(self.client.post(RUTA, datos, format="json").status_code, 400, falta)
        # Editar solo una fecha también se valida contra la otra
        c = self.crear(origen="editar-fecha")
        r = self.client.patch(f"{RUTA}{c['id']}/", {"fechaFin": str(self.hoy - dt.timedelta(days=1))}, format="json")
        self.assertEqual(r.status_code, 400)

    def test_estado_y_filtro_segun_la_fecha_de_lima(self):
        programada = self.crear(origen="programada", fechaInicio=str(self.hoy + dt.timedelta(days=3)),
                                fechaFin=str(self.hoy + dt.timedelta(days=9)))
        activa = self.crear(origen="activa")
        vencida = self.crear(origen="vencida", fechaInicio=str(self.hoy - dt.timedelta(days=9)),
                             fechaFin=str(self.hoy - dt.timedelta(days=1)))
        self.assertEqual((programada["estado"], activa["estado"], vencida["estado"]),
                         ("PROGRAMADA", "ACTIVA", "FINALIZADA"))
        for estado, esperado in (("PROGRAMADA", programada), ("ACTIVA", activa), ("FINALIZADA", vencida)):
            r = self.client.get(f"{RUTA}?estado={estado}")
            self.assertEqual([c["id"] for c in r.data["results"]], [esperado["id"]], estado)
        self.assertEqual(self.client.get(f"{RUTA}?estado=BORRADA").status_code, 400)

    def test_el_estado_usa_el_dia_de_lima_y_no_el_de_utc(self):
        c = self.crear(origen="cambio-de-dia", fechaInicio="2026-11-20", fechaFin="2026-11-20")
        campana = Campana.objects.get(pk=c["id"])
        # 21/11 01:00 UTC todavía es 20/11 20:00 en Lima
        with mock.patch("django.utils.timezone.now",
                        return_value=dt.datetime(2026, 11, 21, 1, 0, tzinfo=dt.timezone.utc)):
            self.assertEqual(campana.estado(), "ACTIVA")
        with mock.patch("django.utils.timezone.now",
                        return_value=dt.datetime(2026, 11, 21, 6, 0, tzinfo=dt.timezone.utc)):
            self.assertEqual(campana.estado(), "FINALIZADA")

    def test_finalizar(self):
        activa = self.crear(origen="activa", fechaFin=str(self.hoy + dt.timedelta(days=10)))
        r = self.client.post(f"{RUTA}{activa['id']}/finalizar/")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data["estado"], "FINALIZADA")
        self.assertEqual(r.data["fechaFin"], str(self.hoy))
        self.assertIsNotNone(r.data["finalizadaEn"])
        self.assertEqual([c["id"] for c in self.client.get(f"{RUTA}?estado=FINALIZADA").data["results"]], [activa["id"]])
        self.assertEqual(self.client.get(f"{RUTA}?estado=ACTIVA").data["count"], 0)
        # Repetir no cambia nada
        otra = self.client.post(f"{RUTA}{activa['id']}/finalizar/")
        self.assertEqual(otra.data["finalizadaEn"], r.data["finalizadaEn"])

        programada = self.crear(origen="futura", fechaInicio=str(self.hoy + dt.timedelta(days=2)),
                                fechaFin=str(self.hoy + dt.timedelta(days=4)))
        r = self.client.post(f"{RUTA}{programada['id']}/finalizar/")
        self.assertEqual(r.status_code, 400)
        self.assertEqual(Campana.objects.get(pk=programada["id"]).estado(), "PROGRAMADA")

    def test_no_se_borra(self):
        c = self.crear()
        self.assertEqual(self.client.delete(f"{RUTA}{c['id']}/").status_code, 405)
        self.assertTrue(Campana.objects.filter(pk=c["id"]).exists())
        self.assertEqual(self.client.get(f"{RUTA}999999/").status_code, 404)
