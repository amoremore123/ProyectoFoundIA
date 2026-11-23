"""H59: respaldos de fotos junto a los de la base, filtrados por tipo."""
from datetime import timedelta

import bcrypt
from django.utils import timezone
from rest_framework.test import APITestCase

from .models import Respaldo, Usuario
from .tests import PASSWORD, token_de


class RespaldosFotosTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        self.admin = Usuario.objects.create(
            nombre="Admin", apellido="QA", correo="admin-h59@prueba.invalid",
            password=hash_, rol="ADMIN", estado="ACTIVO")
        self.biblioteca = Usuario.objects.create(
            nombre="Beto", apellido="Biblioteca", correo="biblioteca-h59@prueba.invalid",
            password=hash_, rol="BIBLIOTECA", estado="ACTIVO")

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def crear(self, tipo, nombre, horas=0, estado="COMPLETADO"):
        momento = timezone.now() - timedelta(hours=horas)
        return Respaldo.objects.create(
            tipo=tipo, nombre_archivo=nombre, huella_sha256="b" * 64, tamano_bytes=4096,
            estado=estado, iniciado_en=momento, finalizado_en=momento)

    def test_la_lista_incluye_el_tipo_y_filtra_por_tipo(self):
        base = self.crear("BASE", "foundia_objetos_perdidos_db_20261120T030000Z.sql.gz", horas=1)
        fotos = self.crear("FOTOS", "foundia_fotos_20261120T030000Z.tar.gz")
        self.como(self.admin)

        todos = self.client.get("/api/admin/respaldos/")
        self.assertEqual(todos.status_code, 200)
        self.assertEqual({f["tipo"] for f in todos.data["results"]}, {"BASE", "FOTOS"})

        solo_fotos = self.client.get("/api/admin/respaldos/?tipo=FOTOS")
        self.assertEqual([f["id"] for f in solo_fotos.data["results"]], [fotos.id])
        solo_base = self.client.get("/api/admin/respaldos/?tipo=BASE")
        self.assertEqual([f["id"] for f in solo_base.data["results"]], [base.id])

    def test_ultimo_sin_tipo_sigue_siendo_el_de_la_base(self):
        base = self.crear("BASE", "foundia_objetos_perdidos_db_20261120T030000Z.sql.gz", horas=1)
        # El de fotos es más reciente, pero la tarjeta de H38 debe seguir mostrando la base
        self.crear("FOTOS", "foundia_fotos_20261120T030100Z.tar.gz")
        self.como(self.admin)

        r = self.client.get("/api/admin/respaldos/ultimo/")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data["ultimo"]["id"], base.id)
        self.assertEqual(r.data["ultimo"]["tipo"], "BASE")

    def test_ultimo_de_fotos_y_null_si_no_hay(self):
        self.como(self.admin)
        self.assertEqual(self.client.get("/api/admin/respaldos/ultimo/?tipo=FOTOS").data, {"ultimo": None})

        fallido = self.crear("FOTOS", "foundia_fotos_20261120T030000Z.tar.gz", estado="FALLIDO")
        r = self.client.get("/api/admin/respaldos/ultimo/?tipo=FOTOS")
        self.assertEqual(r.data["ultimo"]["id"], fallido.id)
        self.assertEqual(r.data["ultimo"]["estado"], "FALLIDO")

    def test_tipo_invalido_responde_400(self):
        self.como(self.admin)
        self.assertEqual(self.client.get("/api/admin/respaldos/?tipo=VIDEOS").status_code, 400)
        self.assertEqual(self.client.get("/api/admin/respaldos/ultimo/?tipo=base").status_code, 400)

    def test_solo_admin_ve_los_respaldos_de_fotos(self):
        self.crear("FOTOS", "foundia_fotos_20261120T030000Z.tar.gz")
        self.assertEqual(self.client.get("/api/admin/respaldos/ultimo/?tipo=FOTOS").status_code, 401)
        self.como(self.biblioteca)
        self.assertEqual(self.client.get("/api/admin/respaldos/?tipo=FOTOS").status_code, 403)
