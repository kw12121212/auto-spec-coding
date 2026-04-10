package org.specdriven.agent.question;

/**
 * Exception for mobile adapter-specific failures (connection, auth, rate-limit, parse errors).
 * Carries the channel type for error discrimination.
 */
public class MobileAdapterException extends RuntimeException {

    private final String channelType;

    public MobileAdapterException(String channelType, String message) {
        super(message);
        this.channelType = channelType;
    }

    public MobileAdapterException(String channelType, String message, Throwable cause) {
        super(message, cause);
        this.channelType = channelType;
    }

    public String channelType() {
        return channelType;
    }
}
