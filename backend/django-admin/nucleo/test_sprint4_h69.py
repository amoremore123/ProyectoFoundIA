"""H69: notificaciones masivas con vista previa, límite, confirmación y registro."""
from datetime import timedelta
from unittest import mock

import bcrypt
from django.utils import timezone
from rest_framework.test import APITestCase

from . import envios_masivos
from .models import EnvioMasivo, Notificacion, Usuario
from .tests import PASSWORD, token_de

RUTA = "/api/admin/envios-masivos/"


class EnviosMasivosTests(APITestCase):
    def setUp(self):
        self.hash = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        self.admin = self.usuario("Admin", "admin-h69@prueba.invalid", "ADMIN")
        self.otro_admin = self.usuario("Otra", "admin2-h69@prueba.invalid", "ADMIN")
        self.biblioteca = self.usuario("Beto", "biblioteca-h69@prueba.invalid", "BIBLIOTECA")
        self.ana = self.usuario("Ana", "ana-h69@prueba.invalid", "USUARIO")
        self.luis = self.usuario("Luis", "luis-h69@prueba.invalid", "USUARIO")
        self.suspendida = self.usuario("Carla", "carla-h69@prueba.invalid", "USUARIO", estado="SUSPENDIDO")

    def usuario(self, nombre, correo, rol, estado="ACTIVO"):
        return Usuario.objects.create(nombre=nombre, apellido="QA", correo=correo, password=self.hash,
                                      rol=rol, estado=estado)

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def cuerpo(self, **cambios):
        datos = {"titulo": "La biblioteca cierra el lunes",
                 "mensaje": "Por el feriado, la mesa de objetos atiende el martes desde las 8 a. m.",
                 "enlace": "/explorar", "destinatarios": "USUARIOS"}
        datos.update(cambios)
        return datos

    def vista_previa(self, **cambios):
        return self.client.post(f"{RUTA}vista-previa/", self.cuerpo(**cambios), format="json")

    def enviar(self, **cambios):
        previa = self.vista_previa(**{k: v for k, v in cambios.items() if k != "confirmacion"})
        self.assertEqual(previa.status_code, 200, previa.content)
        datos = self.cuerpo(**cambios)
        datos.setdefault("confirmacion", previa.data["confirmacion"])
        return self.client.post(RUTA, datos, format="json")

    def test_vista_previa_cuenta_por_grupo_solo_activas_y_no_envia(self):
        self.como(self.admin)
        r = self.vista_previa()
        self.assertEqual(r.status_code, 200, r.content)
        self.assertEqual(r.data["destinatarios"], 2)  # Ana y Luis; Carla está suspendida
        self.assertEqual({m["id"] for m in r.data["muestra"]}, {self.ana.id, self.luis.id})
        self.assertNotIn("correo", r.data["muestra"][0])
        self.assertIn("confirmacion", r.data)
        self.assertIn("expiraEn", r.data)
        self.assertEqual(self.vista_previa(destinatarios="BIBLIOTECA").data["destinatarios"], 1)
        self.assertEqual(self.vista_previa(destinatarios="TODOS").data["destinatarios"], 5)
        self.assertEqual(Notificacion.objects.count(), 0)
        self.assertEqual(EnvioMasivo.objects.count(), 0)

    def test_enviar_crea_un_aviso_por_destinatario_con_su_registro(self):
        self.como(self.admin)
        r = self.enviar()
        self.assertEqual(r.status_code, 201, r.content)
        self.assertEqual(r.data["totalDestinatarios"], 2)
        self.assertEqual(r.data["leidas"], 0)
        self.assertEqual(r.data["autor"]["id"], self.admin.id)
        self.assertEqual(r.data["destinatarios"], "USUARIOS")
        envio = EnvioMasivo.objects.get(pk=r.data["id"])
        avisos = Notificacion.objects.filter(envio_masivo=envio)
        self.assertEqual({n.usuario_id for n in avisos}, {self.ana.id, self.luis.id})
        for n in avisos:
            self.assertEqual(n.tipo, "SISTEMA")
            self.assertEqual(n.titulo, "La biblioteca cierra el lunes")
            self.assertEqual(n.enlace, "/explorar")
            self.assertFalse(n.leida)
        self.assertFalse(Notificacion.objects.filter(usuario=self.suspendida).exists())

    def test_validaciones(self):
        self.como(self.admin)
        for cambios, campo in [({"titulo": "ab"}, "titulo"), ({"mensaje": ""}, "mensaje"),
                               ({"mensaje": "x" * 1001}, "mensaje"), ({"destinatarios": "ADMINS"}, "destinatarios"),
                               ({"enlace": "https://otro.example"}, "enlace"), ({"enlace": "/a?b=1"}, "enlace")]:
            with self.subTest(cambios=cambios):
                r = self.vista_previa(**cambios)
                self.assertEqual(r.status_code, 400)
                self.assertIn(campo, r.data)
        sin_enlace = self.vista_previa(enlace=None)
        self.assertEqual(sin_enlace.status_code, 200)

    def test_confirmacion_obligatoria_y_ligada_al_contenido(self):
        self.como(self.admin)
        codigo = self.vista_previa().data["confirmacion"]
        casos = {
            "sin código": self.cuerpo(),
            "alterado": self.cuerpo(confirmacion=codigo[:-1] + ("0" if codigo[-1] != "0" else "1")),
            "basura": self.cuerpo(confirmacion="no-es-un-codigo"),
            "otro texto": self.cuerpo(mensaje="Otro mensaje distinto del que se vio", confirmacion=codigo),
            "otro grupo": self.cuerpo(destinatarios="TODOS", confirmacion=codigo),
            "otro enlace": self.cuerpo(enlace="/", confirmacion=codigo),
        }
        for nombre, datos in casos.items():
            with self.subTest(caso=nombre):
                r = self.client.post(RUTA, datos, format="json")
                self.assertEqual(r.status_code, 400, r.content)
                self.assertIn("confirmacion", r.data)
        # El código de un administrador no sirve para otro
        self.como(self.otro_admin)
        self.assertEqual(self.client.post(RUTA, self.cuerpo(confirmacion=codigo), format="json").status_code, 400)
        self.assertEqual(Notificacion.objects.count(), 0)
        self.assertEqual(EnvioMasivo.objects.count(), 0)

    def test_codigo_vencido(self):
        self.como(self.admin)
        codigo = self.vista_previa().data["confirmacion"]
        mas_tarde = timezone.now() + timedelta(minutes=11)
        with mock.patch("django.utils.timezone.now", return_value=mas_tarde):
            r = self.client.post(RUTA, self.cuerpo(confirmacion=codigo), format="json")
        self.assertEqual(r.status_code, 400)
        self.assertEqual(Notificacion.objects.count(), 0)

    def test_si_cambian_las_cuentas_hay_que_volver_a_ver_la_vista_previa(self):
        self.como(self.admin)
        codigo = self.vista_previa().data["confirmacion"]
        self.usuario("Nuevo", "nuevo-h69@prueba.invalid", "USUARIO")
        r = self.client.post(RUTA, self.cuerpo(confirmacion=codigo), format="json")
        self.assertEqual(r.status_code, 400)
        self.assertEqual(EnvioMasivo.objects.count(), 0)

    def test_limite_de_destinatarios(self):
        self.como(self.admin)
        with mock.patch.object(envios_masivos, "MAXIMO_DESTINATARIOS", 1):
            r = self.vista_previa()
            self.assertEqual(r.status_code, 400)
            self.assertIn("destinatarios", r.data)
        codigo = self.vista_previa().data["confirmacion"]
        with mock.patch.object(envios_masivos, "MAXIMO_DESTINATARIOS", 1):
            self.assertEqual(self.client.post(RUTA, self.cuerpo(confirmacion=codigo), format="json").status_code, 400)
        self.assertEqual(Notificacion.objects.count(), 0)

    def test_grupo_sin_cuentas(self):
        self.biblioteca.estado = "SUSPENDIDO"
        self.biblioteca.save()
        self.como(self.admin)
        r = self.vista_previa(destinatarios="BIBLIOTECA")
        self.assertEqual(r.status_code, 400)

    def test_limite_de_envios_por_dia(self):
        self.como(self.admin)
        for i in range(3):
            self.assertEqual(self.enviar(titulo=f"Aviso número {i}").status_code, 201)
        # El límite es entre todos los administradores
        self.como(self.otro_admin)
        r = self.enviar(titulo="El cuarto de hoy")
        self.assertEqual(r.status_code, 429)
        self.assertEqual(EnvioMasivo.objects.count(), 3)
        self.assertEqual(Notificacion.objects.count(), 6)
        # Un envío de ayer no cuenta para el límite de hoy
        EnvioMasivo.objects.update(enviado_en=timezone.now() - timedelta(days=1, hours=1))
        self.assertEqual(self.enviar(titulo="Al día siguiente").status_code, 201)

    def test_historial_detalle_y_destinatarios_con_leidas(self):
        self.como(self.admin)
        primero = self.enviar(titulo="Primero").data
        segundo = self.enviar(titulo="Segundo", destinatarios="TODOS").data
        Notificacion.objects.filter(envio_masivo_id=segundo["id"], usuario=self.ana).update(leida=True)

        lista = self.client.get(RUTA).data["results"]
        self.assertEqual([e["id"] for e in lista], [segundo["id"], primero["id"]])
        self.assertEqual(lista[0]["leidas"], 1)
        self.assertEqual(lista[0]["totalDestinatarios"], 5)
        detalle = self.client.get(f"{RUTA}{segundo['id']}/").data
        self.assertEqual(detalle["leidas"], 1)
        destinatarios = self.client.get(f"{RUTA}{segundo['id']}/destinatarios/").data
        self.assertEqual(destinatarios["count"], 5)
        leidas = {d["id"]: d["leida"] for d in destinatarios["results"]}
        self.assertTrue(leidas[self.ana.id])
        self.assertFalse(leidas[self.luis.id])
        self.assertNotIn("correo", destinatarios["results"][0])

    def test_no_se_edita_ni_se_borra(self):
        self.como(self.admin)
        envio = self.enviar().data
        ruta = f"{RUTA}{envio['id']}/"
        self.assertEqual(self.client.put(ruta, self.cuerpo(), format="json").status_code, 405)
        self.assertEqual(self.client.patch(ruta, {"titulo": "Otro"}, format="json").status_code, 405)
        self.assertEqual(self.client.delete(ruta).status_code, 405)
        self.assertEqual(EnvioMasivo.objects.get(pk=envio["id"]).titulo, "La biblioteca cierra el lunes")

    def test_solo_admin(self):
        self.assertEqual(self.client.post(f"{RUTA}vista-previa/", self.cuerpo(), format="json").status_code, 401)
        for usuario in (self.ana, self.biblioteca):
            self.como(usuario)
            with self.subTest(rol=usuario.rol):
                self.assertEqual(self.vista_previa().status_code, 403)
                self.assertEqual(self.client.post(RUTA, self.cuerpo(confirmacion="x"), format="json").status_code, 403)
                self.assertEqual(self.client.get(RUTA).status_code, 403)
        self.assertEqual(Notificacion.objects.count(), 0)
