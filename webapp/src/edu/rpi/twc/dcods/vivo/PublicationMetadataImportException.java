package edu.rpi.twc.dcods.vivo;

import java.util.ArrayList;

public class PublicationMetadataImportException extends Exception {
	
	private ArrayList<String> messages = new ArrayList<String>();

    public PublicationMetadataImportException(String message) {
        messages.add(message);
    }

    public void addError(String error) {
        messages.add(error);
    }

    public ArrayList<String> getErrorMessages() {
        return messages;
    }

    public String toString() {
        return messages.toString();
    }
}
