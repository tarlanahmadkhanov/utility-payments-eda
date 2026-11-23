package az.tarlan.utility.proxy.adapter.kafka;

public class InvalidIncomingEventException extends RuntimeException {

    public InvalidIncomingEventException(String message) {
        super(message);
    }

    public InvalidIncomingEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
