package com.kiwi.cryoems.bpm.movie.delegate;

public class MovieFatalException extends RuntimeException {
    public MovieFatalException(String message) {
        super(message);
    }

    public MovieFatalException(String message, Throwable cause) {
        super(message, cause);
    }
}
