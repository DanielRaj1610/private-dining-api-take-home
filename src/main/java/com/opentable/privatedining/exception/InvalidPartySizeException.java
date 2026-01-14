package com.opentable.privatedining.exception;

public class InvalidPartySizeException extends RuntimeException {

    public InvalidPartySizeException(int partySize, int minCapacity, int maxCapacity) {
        super("Party size " + partySize + " is outside the space capacity range of " + minCapacity + "-" + maxCapacity);
    }

    public InvalidPartySizeException(String message) {
        super(message);
    }
}