package edu.buffalo.cse.cse486586.groupmessenger2;

public class Messenger {
    int sequenceNumber;
    int avdPort;
    boolean delivered;
    String message;
    String messageType;


    Messenger(int sequenceNumber,int avdPort,boolean delivered,String message,String messageType)
    {
        this.sequenceNumber=sequenceNumber;
        this.avdPort=avdPort;
        this.delivered=delivered;
        this.message=message;
        this.messageType=messageType;
    }
}
