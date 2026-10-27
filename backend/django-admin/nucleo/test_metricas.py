"""H29: indicadores verificados contra datos conocidos en SQLite temporal."""
from datetime import date, datetime, timezone

from django.test import override_settings
from rest_framework.test import APITestCase

from .models import Categoria, Objeto, Usuario
from .tests import token_de


class MetricasSprint2Tests(APITestCase):
    def setUp(self):
        self.admin = Usuario.objects.create(nombre="Admin", apellido="QA", correo="admin@qa.invalid",
                                            password="no-se-usa", rol="ADMIN")
        self.usuario = Usuario.objects.create(nombre="Ana", apellido="QA", correo="ana@qa.invalid",
                                              password="no-se-usa")
        self.categoria = Categoria.objects.create(nombre="Mochilas")
        self.crear("PERDIDO", "ACTIVO", 5)
        self.crear("PERDIDO", "RECUPERADO", 6)
        self.crear("ENCONTRADO", "ACTIVO", 7)
        self.crear("PERDIDO", "OCULTO", 8)
        self.crear("ENCONTRADO", "ELIMINADO", 9)
        self.crear("PERDIDO", "ACTIVO", 1)

    def crear(self, tipo, estado, dia, **extra):
        return Objeto.objects.create(usuario=self.usuario, categoria=self.categoria, nombre="Objeto QA",
                                      descripcion="Dato conocido", tipo=tipo, estado=estado,
                                      fecha_objeto=date(2026, 10, dia),
                                      fecha_publicacion=datetime(2026, 10, dia, 12, tzinfo=timezone.utc), **extra)

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def consultar(self, **filtros):
        self.como(self.admin)
        return self.client.get("/api/admin/dashboard/", filtros)

    def test_solo_administrador_activo_accede(self):
        self.assertEqual(self.client.get("/api/admin/dashboard/").status_code, 401)
        self.como(self.usuario)
        self.assertEqual(self.client.get("/api/admin/dashboard/").status_code, 403)
        self.admin.estado = "SUSPENDIDO"
        self.admin.save()
        self.como(self.admin)
        self.assertEqual(self.client.get("/api/admin/dashboard/").status_code, 403)

    def test_conteos_filtrados_no_suman_subconjuntos_dos_veces(self):
        r = self.consultar(desde="2026-10-05", hasta="2026-10-07")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data["objetos"], {"perdidos": 2, "encontrados": 1, "recuperados": 1, "activos": 2})
        self.assertEqual(r.data["rango"]["zonaHoraria"], "UTC")
        # Compatibilidad: las cuatro tarjetas originales siguen presentes.
        for campo in ("usuarios", "objetosActivos", "reportesPendientes", "categorias"):
            self.assertIn(campo, r.data)
        self.assertNotIn(self.usuario.correo, str(r.data))

    def test_tablero_no_depende_del_interruptor_del_panel_nativo(self):
        for habilitado in (False, True):
            with self.subTest(HABILITAR_EXTRAS=habilitado), override_settings(HABILITAR_EXTRAS=habilitado):
                r = self.consultar(desde="2026-10-05", hasta="2026-10-07")
                self.assertEqual(r.status_code, 200)
                self.assertEqual(r.data["objetos"], {
                    "perdidos": 2, "encontrados": 1, "recuperados": 1, "activos": 2,
                })
                self.assertEqual(r.data["usuarios"], 2)
                self.assertEqual(r.data["objetosActivos"], 3)
                self.assertEqual(r.data["reportesPendientes"], 0)
                self.assertEqual(r.data["categorias"], 1)

    def test_limites_inclusivos_y_rango_vacio(self):
        self.assertEqual(self.consultar(desde="2026-10-05", hasta="2026-10-05").data["objetos"]["perdidos"], 1)
        self.assertEqual(self.consultar(desde="2027-01-01").data["objetos"],
                         {"perdidos": 0, "encontrados": 0, "recuperados": 0, "activos": 0})

    def test_fecha_invalida_rango_invertido_y_filtro_desconocido_son_400(self):
        for filtros in ({"desde": "ayer"}, {"hasta": "2026-02-30"},
                        {"desde": "2026-10-08", "hasta": "2026-10-01"}, {"usuario": self.usuario.pk}):
            self.assertEqual(self.consultar(**filtros).status_code, 400)
