package com.marketplace.search.application.usecases;

/**
 * Exceção específica para erros de busca.
 */
public class SearchException extends RuntimeException {
    public SearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
