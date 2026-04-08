package org.specdriven.agent.vault;

/**
 * Runtime exception for vault-specific errors (key not found, decryption failure, etc.).
 */
public class VaultException extends RuntimeException {

    public VaultException(String message) {
        super(message);
    }

    public VaultException(String message, Throwable cause) {
        super(message, cause);
    }
}
