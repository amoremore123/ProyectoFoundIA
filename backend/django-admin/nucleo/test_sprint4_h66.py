"""H66: preparar y registrar promociones de campañas en redes (sin conectar las APIs de las redes)."""
from datetime import timedelta

import bcrypt
from django.conf import settings
from django.utils import timezone
from rest_framework.test import APITestCase

from .models import Campana, PromocionCampana, Usuario, hoy_en_lima
from .tests import PASSWORD, token_de

RUTA = "/api/admin/promociones/"


class PromocionesTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        self.admin = Usuario.objects.create(nombre="Admin", apellido="QA", correo="admin-h66@prueba.invalid",
                                            password=hash_, rol="ADMIN", estado="ACTIVO")
        self.ana = Usuario.objects.create(nombre="Ana", apellido="QA", correo="ana-h66@prueba.invalid",
                                          password=hash_, rol="USUARIO", estado="ACTIVO")
        hoy = hoy_en_lima()
        self.campana = Campana.objects.create(
            nombre="Semana de objetos perdidos", origen="semana-perdidos-2026", destino="/explorar",
            descripcion="Del 24 al 30 de noviembre revisa la mesa de objetos de la biblioteca.",
            fecha_inicio=hoy, fecha_fin=hoy + timedelta(days=6), creada_por=self.admin)
        self.otra = Campana.objects.create(nombre="Feria", origen="feria-2026", destino="/", fecha_inicio=hoy,
                                           fecha_fin=hoy, creada_por=self.admin)
        self.enlace = f"{settings.FOUNDIA_WEB_URL}/explorar?origen=semana-perdidos-2026"

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def cuerpo(self, **cambios):
        datos = {"campanaId": self.campana.id, "red": "INSTAGRAM",
                 "texto": "Semana de objetos perdidos: revisa la mesa de la biblioteca. Enlace en la bio.",
                 "publicadaEn": (timezone.now() - timedelta(hours=1)).isoformat(),
                 "urlPublicacion": "https://www.instagram.com/p/C0abc123/"}
        datos.update(cambios)
        return {k: v for k, v in datos.items() if v is not ...}

    def registrar(self, **cambios):
        self.como(self.admin)
        return self.client.post(RUTA, self.cuerpo(**cambios), format="json")

    def test_sugerencia_con_enlace_de_origen(self):
        self.como(self.admin)
        r = self.client.get(f"{RUTA}sugerencia/?campana={self.campana.id}&red=FACEBOOK")
        self.assertEqual(r.status_code, 200, r.content)
        self.assertEqual(r.data["enlace"], self.enlace)
        self.assertIn(self.enlace, r.data["texto"])
        self.assertIn("Semana de objetos perdidos", r.data["texto"])
        self.assertIn("revisa la mesa de objetos", r.data["texto"])
        self.assertEqual(r.data["maximo"], 2200)
        self.assertEqual(PromocionCampana.objects.count(), 0)

    def test_sugerencia_en_x_no_pasa_de_280_y_no_corta_el_enlace(self):
        self.campana.descripcion = "Texto largo de la campaña. " * 15
        self.campana.save()
        self.como(self.admin)
        r = self.client.get(f"{RUTA}sugerencia/?campana={self.campana.id}&red=X")
        self.assertEqual(r.status_code, 200)
        self.assertLessEqual(len(r.data["texto"]), 280)
        self.assertIn(self.enlace, r.data["texto"])
        self.assertIn("…", r.data["texto"])
        self.assertEqual(r.data["maximo"], 280)

    def test_sugerencia_con_datos_invalidos(self):
        self.como(self.admin)
        for consulta in ("?red=X", f"?campana={self.campana.id}", f"?campana={self.campana.id}&red=MYSPACE",
                         "?campana=999999&red=X", "?campana=abc&red=X"):
            with self.subTest(consulta=consulta):
                self.assertEqual(self.client.get(f"{RUTA}sugerencia/{consulta}").status_code, 400)

    def test_registrar_guarda_texto_enlace_fecha_y_autor(self):
        r = self.registrar()
        self.assertEqual(r.status_code, 201, r.content)
        self.assertEqual(r.data["enlace"], self.enlace)
        self.assertEqual(r.data["campana"], {"id": self.campana.id, "nombre": "Semana de objetos perdidos",
                                             "origen": "semana-perdidos-2026"})
        self.assertEqual(r.data["registradaPor"]["id"], self.admin.id)
        self.assertIsNotNone(r.data["fechaRegistro"])
        promo = PromocionCampana.objects.get(pk=r.data["id"])
        self.assertEqual(promo.registrada_por_id, self.admin.id)
        self.assertEqual(promo.url_publicacion, "https://www.instagram.com/p/C0abc123/")

    def test_el_enlace_lo_pone_el_servidor(self):
        r = self.registrar(enlace="https://malo.example/phishing")
        self.assertEqual(r.status_code, 201)
        self.assertEqual(r.data["enlace"], self.enlace)

    def test_sin_direccion_de_publicacion(self):
        r = self.registrar(red="WHATSAPP", urlPublicacion=...)
        self.assertEqual(r.status_code, 201, r.content)
        self.assertIsNone(r.data["urlPublicacion"])
        self.assertEqual(self.registrar(red="WHATSAPP", urlPublicacion="").data["urlPublicacion"], None)

    def test_validaciones(self):
        futuro = (timezone.now() + timedelta(hours=2)).isoformat()
        casos = [
            ({"red": "MYSPACE"}, "red"),
            ({"texto": "ab"}, "texto"),
            ({"red": "X", "texto": "x" * 281, "urlPublicacion": "https://x.com/foundia/status/1"}, "texto"),
            ({"texto": "x" * 2201}, "texto"),
            ({"publicadaEn": futuro}, "publicadaEn"),
            ({"publicadaEn": "2026-11-24T15:30:00"}, "publicadaEn"),
            ({"publicadaEn": "ayer"}, "publicadaEn"),
            ({"urlPublicacion": "http://www.instagram.com/p/C0abc123/"}, "urlPublicacion"),
            ({"urlPublicacion": "https://www.facebook.com/foundia/posts/1"}, "urlPublicacion"),
            ({"urlPublicacion": "https://instagram.com.malo.example/p/1"}, "urlPublicacion"),
            ({"urlPublicacion": "https://usuario:clave@instagram.com/p/1"}, "urlPublicacion"),
            ({"campanaId": 999999}, "campanaId"),
            ({"campanaId": ...}, "campanaId"),
        ]
        for cambios, campo in casos:
            with self.subTest(cambios=cambios):
                r = self.registrar(**cambios)
                self.assertEqual(r.status_code, 400, r.content)
                self.assertIn(campo, r.data)
        self.assertEqual(PromocionCampana.objects.count(), 0)
        # En X entran exactamente 280; y con zona Z también vale
        self.assertEqual(self.registrar(red="X", texto="x" * 280, urlPublicacion="https://twitter.com/foundia/status/1",
                                        publicadaEn="2026-11-01T10:00:00Z").status_code, 201)
        # OTRA acepta cualquier https
        self.assertEqual(self.registrar(red="OTRA", urlPublicacion="https://blog.tecsup.edu.pe/foundia").status_code, 201)

    def test_corregir_texto_fecha_y_direccion_pero_no_campana_ni_red(self):
        creada = self.registrar().data
        ruta = f"{RUTA}{creada['id']}/"
        r = self.client.patch(ruta, {"texto": "Texto corregido después de publicar.",
                                     "urlPublicacion": "https://instagram.com/p/OTRA/"}, format="json")
        self.assertEqual(r.status_code, 200, r.content)
        self.assertEqual(r.data["texto"], "Texto corregido después de publicar.")
        self.assertEqual(self.client.patch(ruta, {"campanaId": self.otra.id}, format="json").status_code, 400)
        self.assertEqual(self.client.patch(ruta, {"red": "TIKTOK"}, format="json").status_code, 400)
        # La dirección se valida con la red guardada
        self.assertEqual(self.client.patch(ruta, {"urlPublicacion": "https://www.tiktok.com/@foundia/video/1"},
                                           format="json").status_code, 400)
        self.assertEqual(self.client.delete(ruta).status_code, 405)
        promo = PromocionCampana.objects.get(pk=creada["id"])
        self.assertEqual((promo.campana_id, promo.red), (self.campana.id, "INSTAGRAM"))

    def test_lista_con_filtros_y_orden(self):
        vieja = self.registrar(publicadaEn=(timezone.now() - timedelta(days=2)).isoformat()).data
        nueva = self.registrar(red="FACEBOOK", urlPublicacion="https://fb.com/foundia/posts/9").data
        de_otra = self.registrar(campanaId=self.otra.id).data
        r = self.client.get(RUTA)
        self.assertEqual([p["id"] for p in r.data["results"]], [de_otra["id"], nueva["id"], vieja["id"]])
        r = self.client.get(f"{RUTA}?campana={self.campana.id}")
        self.assertEqual({p["id"] for p in r.data["results"]}, {nueva["id"], vieja["id"]})
        r = self.client.get(f"{RUTA}?campana={self.campana.id}&red=FACEBOOK")
        self.assertEqual([p["id"] for p in r.data["results"]], [nueva["id"]])
        self.assertEqual(self.client.get(f"{RUTA}?red=MYSPACE").status_code, 400)
        self.assertEqual(self.client.get(f"{RUTA}?campana=abc").status_code, 400)

    def test_permisos_y_no_existe(self):
        self.assertEqual(self.client.get(RUTA).status_code, 401)
        self.como(self.ana)
        self.assertEqual(self.client.get(RUTA).status_code, 403)
        self.assertEqual(self.client.post(RUTA, self.cuerpo(), format="json").status_code, 403)
        self.assertEqual(self.client.get(f"{RUTA}sugerencia/?campana={self.campana.id}&red=X").status_code, 403)
        self.assertEqual(PromocionCampana.objects.count(), 0)
        self.como(self.admin)
        self.assertEqual(self.client.get(f"{RUTA}999999/").status_code, 404)
