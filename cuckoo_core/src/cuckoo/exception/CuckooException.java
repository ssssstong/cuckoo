package cuckoo.exception;

/**
 * @ClassName CuckooException
 * @Description
 * @Date 2023/7/11 20:00
 **/
public class CuckooException extends RuntimeException {

    public CuckooException() {
        super();
    }

    public CuckooException(String message) {
        super(message);
    }

    public CuckooException(String message, Throwable cause) {
        super(message, cause);
    }
}
