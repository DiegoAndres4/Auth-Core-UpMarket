package com.pamplona.authcore.infrastructure.adapters.out.email;

/**
 * Contenido del correo de verificacion (espanol, texto plano + HTML sencillo).
 */
public final class VerificationEmailContent {

    public static final String SUBJECT = "Confirma tu correo en UPMarket";

    private VerificationEmailContent() {
    }

    public static String plainText(String link) {
        return "Hola,\n\n"
                + "Gracias por registrarte en UPMarket. Confirma tu correo con el siguiente enlace; expira en 24 horas:\n"
                + link + "\n\n"
                + "Si no creaste esta cuenta, ignora este correo.\n";
    }

    public static String html(String link) {
        String safeLink = escapeHtml(link);
        return "<p>Hola,</p>"
                + "<p>Gracias por registrarte en UPMarket. Confirma tu correo con el siguiente enlace; expira en 24 horas.</p>"
                + "<p><a href=\"" + safeLink + "\">Confirmar mi correo</a></p>"
                + "<p>Si no creaste esta cuenta, ignora este correo.</p>";
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
