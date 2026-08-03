package com.ashenox.starter.auth.model;



import com.ashenox.starter.user.model.User;
import jakarta.persistence.*;

import java.time.Instant;


@Entity
@Table(name = "Refresh Token")
public class RefreshToken {



        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        private String token;

        private Instant expiryDate;

        @ManyToOne
        @JoinColumn(name = "user_id")
        private User user;

        public String getToken() {
                return token;
        }

        public void setToken(String token) {
                this.token = token;
        }

        public Instant getExpiryDate() {
                return expiryDate;
        }

        public void setExpiryDate(Instant expiryDate) {
                this.expiryDate = expiryDate;
        }

        public User getUser() {
                return user;
        }

        public void setUser(User user) {
                this.user = user;
        }
}
