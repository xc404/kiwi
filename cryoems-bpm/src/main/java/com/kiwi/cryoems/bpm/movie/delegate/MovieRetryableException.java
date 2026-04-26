package com.kiwi.cryoems.bpm.movie.delegate;

public class MovieRetryableException extends RuntimeException {
    public MovieRetryableException(String message) {
        super(message);
    }

    public MovieRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
