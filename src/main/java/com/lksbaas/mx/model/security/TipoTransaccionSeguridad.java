package com.lksbaas.mx.model.security;

/**
 * Enum que define los tipos de transacciones financieras u operaciones
 * que requieren autorización mediante código de seguridad de un solo uso (OTP).
 * 
 * Permite aislar los códigos generados de manera que un código emitido para
 * un tipo de transacción (e.g. SPEI) no pueda ser reutilizado para autorizar
 * otra operación (e.g. Cambio de contraseña o Dispersión).
 */
public enum TipoTransaccionSeguridad {
    TRANSFERENCIA_SPEI("Transferencia SPEI"),
    TRASPASO("Traspaso entre cuentas"),
    PAGO_SERVICIO("Pago de servicios"),
    DISPERSION_MASIVA("Dispersión masiva de pagos"),
    CAMBIO_CONTRASENA("Cambio de contraseña"),
    OPERACION_GENERICA("Operación bancaria general");

    private final String descripcion;

    TipoTransaccionSeguridad(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /**
     * Resuelve de forma segura el enum a partir de una cadena, ignorando mayúsculas/minúsculas.
     *
     * @param valor Nombre o clave del tipo de transacción
     * @return El enum correspondiente
     * @throws IllegalArgumentException si el tipo de transacción no es reconocido
     */
    public static TipoTransaccionSeguridad desdeCadena(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return TRANSFERENCIA_SPEI;
        }
        String normalizado = valor.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        for (TipoTransaccionSeguridad tipo : values()) {
            if (tipo.name().equalsIgnoreCase(normalizado)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de transacción no soportado o inválido: " + valor);
    }
}

