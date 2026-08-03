package com.ashenox.starter.email.config;


public class EmailUtils {

    public static String getEmailMessage(String name, String host, String token) {
        return "Holla " + name + ",\n\nSu nueva cuenta ha sido creada. Por favor haga clic en el enlace a continuación para verificar su cuenta. \n\n" +
                getVerificationUrl(host, token) + "\n\nThe support Team";
    }

    public static String getVerificationUrl(String host, String token) {
        return host + "/auth/check?token=" + token;
    }

    public static String getPasswordResetMessage(String name, String host, String token) {
        return "Hola " + name + ",\n\n" +
                "Recibimos una solicitud para restablecer tu contraseña. Hacé clic en el siguiente enlace para crear una nueva:\n\n" +
                getPasswordResetUrl(host, token) + "\n\n" +
                "Si no hiciste esta solicitud, simplemente ignorá este correo.\n\n" +
                "– El equipo de soporte.";
    }

    public static String getPasswordResetUrl(String host, String token) {
        return host + "/reset-password?token=" + token;
    }

}
