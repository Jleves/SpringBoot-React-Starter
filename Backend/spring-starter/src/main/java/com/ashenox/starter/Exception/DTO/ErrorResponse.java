package com.ashenox.starter.Exception.DTO;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
@Data
@NoArgsConstructor
public class ErrorResponse {

    private LocalDate timestamp;
    private HttpStatus status;
    private String mensaje;
    private String ruta;

    public ErrorResponse(LocalDate timestamp, HttpStatus status, String mensaje, String ruta) {
        this.timestamp = timestamp;
        this.status = status;
        this.mensaje = mensaje;
        this.ruta = ruta;
    }
}
