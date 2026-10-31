package com.lksbaas.mx.model;

public class ApiResponse {
    private int estado;
    private String mensaje;
    private Respuesta respuesta;

    // Getters y Setters
    public int getEstado() {
        return estado;
    }

    public void setEstado(int estado) {
        this.estado = estado;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public Respuesta getRespuesta() {
        return respuesta;
    }

    public void setRespuesta(Respuesta respuesta) {
        this.respuesta = respuesta;
    }

    public static class Respuesta {
        private double cargosPendientes;
        private double saldo;

        // Getters y Setters
        public double getCargosPendientes() {
            return cargosPendientes;
        }

        public void setCargosPendientes(double cargosPendientes) {
            this.cargosPendientes = cargosPendientes;
        }

        public double getSaldo() {
            return saldo;
        }

        public void setSaldo(double saldo) {
            this.saldo = saldo;
        }
    }
}
