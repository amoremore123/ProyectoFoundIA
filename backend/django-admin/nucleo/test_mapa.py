"""Mapa de pérdidas: puntos agrupados por cercanía, sin datos personales."""
from datetime import date
from decimal import Decimal

import bcrypt
from rest_framework.test import APITestCase

from .metricas import metricas_mapa
from .models import Categoria, Objeto, Usuario
from .tests import PASSWORD, token_de


class MapaTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        self.admin = Usuario.objects.create(nombre="Admin", apellido="Mapa", correo="admin-mapa@prueba.invalid",
                                            password=hash_, rol="ADMIN", estado="ACTIVO")
        self.ana = Usuario.objects.create(nombre="Ana", apellido="Secreta", correo="ana-mapa@prueba.invalid",
                                          password=hash_, rol="USUARIO", estado="ACTIVO")
        self.categoria = Categoria.objects.create(nombre="Mapa")

    def objeto(self, tipo, lat, lng, ubicacion="Biblioteca", estado="ACTIVO"):
        return Objeto.objects.create(usuario=self.ana, categoria=self.categoria, nombre="x", descripcion="x",
                                     ubicacion=ubicacion, fecha_objeto=date.today(), tipo=tipo, estado=estado,
                                     latitud=Decimal(lat) if lat else None, longitud=Decimal(lng) if lng else None)

    def test_agrupa_puntos_cercanos_y_ordena_por_total(self):
        self.objeto("PERDIDO", "-12.0445101", "-76.9525101", "Biblioteca")
        self.objeto("ENCONTRADO", "-12.0445149", "-76.9525149", "Biblioteca central")
        self.objeto("PERDIDO", "-12.0445122", "-76.9525122", "Biblioteca")
        self.objeto("PERDIDO", "-12.0460000", "-76.9500000", "Cafetería")
        self.objeto("PERDIDO", None, None, "Sin GPS")
        self.objeto("PERDIDO", "-12.0470000", "-76.9510000", "Oculto", estado="OCULTO")
        datos = metricas_mapa({})
        self.assertEqual(len(datos["puntos"]), 2)
        primero = datos["puntos"][0]
        self.assertEqual((primero["total"], primero["perdidos"], primero["encontrados"]), (3, 2, 1))
        self.assertEqual(primero["lugar"], "Biblioteca")
        self.assertEqual(primero["lat"], -12.0445)
        self.assertEqual(datos["conCoordenadas"], 4)
        self.assertEqual(datos["sinCoordenadas"], 1)

    def test_filtra_por_tipo_y_valida(self):
        self.objeto("PERDIDO", "-12.0445", "-76.9525")
        self.objeto("ENCONTRADO", "-12.0460", "-76.9500")
        self.assertEqual(len(metricas_mapa({"tipo": "ENCONTRADO"})["puntos"]), 1)
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(self.admin)}")
        self.assertEqual(self.client.get("/api/admin/metricas/mapa/", {"tipo": "OTRO"}).status_code, 400)
        self.assertEqual(self.client.get("/api/admin/metricas/mapa/", {"desde": "2026-12-01", "hasta": "2026-11-01"}).status_code, 400)

    def test_solo_admin_y_sin_datos_personales(self):
        self.objeto("PERDIDO", "-12.0445", "-76.9525")
        self.assertEqual(self.client.get("/api/admin/metricas/mapa/").status_code, 401)
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(self.ana)}")
        self.assertEqual(self.client.get("/api/admin/metricas/mapa/").status_code, 403)
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(self.admin)}")
        r = self.client.get("/api/admin/metricas/mapa/")
        self.assertEqual(r.status_code, 200)
        self.assertNotIn("Secreta", str(r.content))
