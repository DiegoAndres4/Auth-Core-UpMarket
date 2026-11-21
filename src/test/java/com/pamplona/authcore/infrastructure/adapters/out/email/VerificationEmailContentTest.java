package com.pamplona.authcore.infrastructure.adapters.out.email;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica el HTML y el texto plano del correo de verificacion con marca UPMarket. */
class VerificationEmailContentTest {

    private static final String LINK = "http://localhost:5173/#/verificar?token=abc123";

    @Test
    void elMensajeTieneLasDosVersionesYElEnlaceExactamenteDosVeces() {
        String html = VerificationEmailContent.html(LINK);

        assertEquals(2, contar(html, LINK));
        assertEquals(1, contar(html, "href=\"" + LINK + "\""));

        String plano = VerificationEmailContent.plainText(LINK);
        assertTrue(plano.contains(LINK));
        assertTrue(plano.contains("expira en 24 horas"));
    }

    @Test
    void elHtmlSigueLaIdentidadDeUpMarket() {
        String html = VerificationEmailContent.html(LINK);

        assertTrue(html.contains("lang=\"es\""));
        assertTrue(html.contains("role=\"presentation\""));
        assertTrue(html.contains("name=\"color-scheme\" content=\"light dark\""));
        assertTrue(html.contains("name=\"supported-color-schemes\" content=\"light dark\""));
        assertTrue(html.contains("width=\"600\""));
        assertTrue(html.contains("#F2EDE3"));
        assertTrue(html.contains("#FFFFFF"));
        assertTrue(html.contains("#D62828"));
        assertTrue(html.contains(">UP<"));
        assertTrue(html.contains(">Market<"));
        assertTrue(html.contains("Confirma tu correo"));
        assertTrue(html.contains("Confirmar mi correo"));
        assertTrue(html.contains("#F5B700"));
        assertTrue(html.contains("¿El botón no funciona? Copia y pega este enlace:"));
        assertTrue(html.contains("#FAF7F0"));
        assertTrue(html.contains(
                "El enlace vence en 24 horas. Si no creaste esta cuenta, ignora este correo."));
        assertTrue(html.contains(
                "UPMarket · Proyecto Integrador · Universidad de Pamplona, sede Villa del Rosario"));
        assertTrue(html.contains(
                "Confirma tu correo para empezar a publicar y comprar en la sede."));
        assertTrue(html.contains("'Archivo', Arial, Helvetica, sans-serif"));
        assertFalse(html.contains("<style"));
    }

    @Test
    void escapaUnValorConScriptEnElEnlace() {
        String username = "<script>alert(1)</script>";
        String link = "http://localhost:5173/#/verificar?token=abc&usuario=" + username;

        String html = VerificationEmailContent.html(link);

        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("&amp;usuario="));
    }

    private static int contar(String texto, String fragmento) {
        int total = 0;
        int desde = texto.indexOf(fragmento);
        while (desde >= 0) {
            total++;
            desde = texto.indexOf(fragmento, desde + fragmento.length());
        }
        return total;
    }
}
