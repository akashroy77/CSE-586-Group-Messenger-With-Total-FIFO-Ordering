package edu.buffalo.cse.cse486586.groupmessenger2;


public class Messenger  {
    int sequenceNumber;
    int sendingPort;
    int receivingPort;
    boolean delivered;
    String message;
    String messageType;


    Messenger(int sequenceNumber,int sendingPort,int receivingPort, boolean delivered,String message,String messageType)
    {
        this.sequenceNumber=sequenceNumber;
        this.sendingPort=sendingPort;
        this.receivingPort=receivingPort;
        this.delivered=delivered;
        this.message=message;
        this.messageType=messageType;
    }
}
