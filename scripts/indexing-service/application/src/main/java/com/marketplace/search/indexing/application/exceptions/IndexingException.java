package com.marketplace.search.indexing.application.exceptions;

/**
 * Exceção específica para erros de indexação
 */
public class IndexingException extends RuntimeException {
	public IndexingException(String message, Throwable cause) {
		super(message, cause);
	}
}