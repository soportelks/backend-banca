package com.lksbaas.mx.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ErrorOperacionExceptionTest {

    @Test
    @DisplayName("ErrorOperacionException constructores con mensaje y causa")
    void testConstructores() {
        ErrorOperacionException ex1 = new ErrorOperacionException("Error simple");
        assertEquals("Error simple", ex1.getMessage());

        Throwable cause = new IllegalArgumentException("Causa raíz");
        ErrorOperacionException ex2 = new ErrorOperacionException("Error con causa", cause);
        assertEquals("Error con causa", ex2.getMessage());
        assertEquals(cause, ex2.getCause());
        assertNotNull(ex2);
    }
}

