package com.ashenox.starter.shared.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {
    private Security security = new Security();
    private Mail mail = new Mail();
    private General general = new General();

    private Init init = new Init();


    @Getter @Setter
    public static class Security {
        private Jwt jwt = new Jwt();
        private boolean enableCors;
    }

    @Getter @Setter
    public static class Jwt {
        private String secret;
        private long accessExpirationMinutes;
        private long refreshExpirationHours;
        private long refreshExpirationMinutes;
    }

    @Getter @Setter
    public static class Mail {
        private String host;
        private int port;
        private String username;
        private String password;
    }

    @Getter @Setter
    public static class General {
        private String appName;
        private String version;
        private String timezone;
    }





    @Getter
    @Setter
    public static class Init {
        private SuperAdmin superadmin = new SuperAdmin();
        private SeedUsers seedUsers = new SeedUsers();
    }

    @Getter
    @Setter
    public static class SuperAdmin {
        private boolean enabled;
        private String email;
        private String password;
    }

    @Getter
    @Setter
    public static class SeedUsers {
        private boolean enabled;
        private String file;
    }
}