package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.PriorityQueue;

import static android.content.ContentValues.TAG;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * import android.os.Bundle;
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final int SERVER_PORT=10000;
    // This is the sequence number for sender to manage FIFO along with TOTAL ordering
    int clientSequenceNumber=0;
    // This is the sequence number that will be proposed by server
    int serverSequenceNumber=0;
    // Senders Port which is the port number that this AVD listens on
    // Calculated via TelephonyManager
    int clientPort=0;
    String remotePorts[] = new String[]{"11108", "11112", "11116", "11120", "11124"};
    //https://stackoverflow.com/questions/683041/how-do-i-use-a-priorityqueue
    Comparator<Messenger> comparator = new MessageSequenceComparator();
    PriorityQueue<Messenger> messageQueue=new PriorityQueue<Messenger>(100,comparator);
    GroupMessengerHelper helper=new GroupMessengerHelper();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        /*
         * Calculate the port number that this AVD listens on.
         * It is just a hack that I came up with to get around the networking limitations of AVDs.
         * The explanation is provided in the PA1 spec.
         */
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        clientPort=Integer.parseInt(myPort);
        Log.d("Message Sender Port: ",Integer.toString(clientPort));
        try {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             *
             * AsyncTask is a simplified thread construct that Android provides. Please make sure
             * you know how it works by reading
             * http://developer.android.com/reference/android/os/AsyncTask.html
             */
            Log.d("ServerSocket","Creating a Server Socket");
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }
        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
        //https://developer.android.com/reference/android/widget/Button
        findViewById(R.id.button4).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText editText = (EditText) findViewById(R.id.editText1);

                String msg = editText.getText().toString() + "\n";
                Log.d("Edit Text","Text Received"+msg);
                editText.setText(""); // This is one way to reset the input box.
                TextView localTextView = (TextView) findViewById(R.id.textView1);
                localTextView.append("\t" + msg); // This is one way to display a string.

                // Call the Client Task onClicking the Send Button
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg);
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    /***
     * ServerTask is an AsyncTask that should handle incoming messages. It is created by
     * ServerTask.executeOnExecutor() call in SimpleMessengerActivity.
     */

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        // Message Sequence Number for DB
        int sequence_number=0;
        Uri providerUri= Uri.parse("content://edu.buffalo.cse.cse486586.groupmessenger2.provider");
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            String[] messageSplits;
            // Client Port: Sending AVD's Port Number
            int localClientPort=0;
            // Server Port: Receiving AVD's Port Number
            int localServerPort;
            try {
                while (true) {
                    try {
                        Socket socket = serverSocket.accept();
                        Log.d("Server:", "Connection Successful");
                        DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                        // Received Message From Client
                        String inputString = inputStream.readUTF();
                        Log.d("Server:", "Received String" + inputString);
                        messageSplits = inputString.split(":");
                        // Initial Priority received From Client
                        int receivedSequenceNumberFromClient = Integer.parseInt(messageSplits[0]);
                        // Client Port: Sending AVD's Port Number
                        localClientPort = Integer.parseInt(messageSplits[1]);
                        // Server Port: Receiving AVD's Port Number
                        localServerPort = Integer.parseInt(messageSplits[2]);
                        // Message Status: Deliverable or Not
                        boolean deliveryStatus = messageSplits[3].equals("false") ? false : true;
                        // Actual Message sent from the AVD
                        String receivedMessage = messageSplits[4];
                        // Message Type : Initial or Final
                        String receivedMessageType = messageSplits[5];
                        if (receivedMessageType.equals("message")) {
                            int tempSequenceNumber=serverSequenceNumber;
                            // server sequence number=max(own sequence number,client sent sequence number)
                            serverSequenceNumber=helper.calculateMax(receivedSequenceNumberFromClient,serverSequenceNumber);
                            // Message Object for the received message
                            Messenger serverMessage = new Messenger(serverSequenceNumber, localClientPort, localServerPort, deliveryStatus, receivedMessage, receivedMessageType);
                            serverSequenceNumber=(tempSequenceNumber==serverSequenceNumber)?serverSequenceNumber++:receivedSequenceNumberFromClient++;
                            // adding to the priority queue
                            messageQueue.add(serverMessage);
                            // Sending proposed sequence Number to client
                            outputStream.writeUTF(Integer.toString(serverSequenceNumber));
                            outputStream.flush();
                            Log.d("Server", "Sending the Proposed Sequence Number");
                        }
                        // Message with Final Priority from Client
                        String finalMessage = inputStream.readUTF();
                        String[] finalMessageSplits = finalMessage.split(":");
                        // Setting Deliverable as TRUE for the final Message
                        Messenger finalMessageObject = new Messenger(Integer.parseInt(finalMessageSplits[0]), Integer.parseInt(finalMessageSplits[1]), Integer.parseInt(finalMessageSplits[2]), true, finalMessageSplits[4], finalMessageSplits[5]);
                        Iterator messageIterator = messageQueue.iterator();
                        while (messageIterator.hasNext()) {
                            Messenger curMessage = (Messenger) messageIterator.next();
                            Log.d("iterator", Integer.toString(curMessage.sequenceNumber));
                            // Updating the message priority in the priority queue
                            if (curMessage.sendingPort == Integer.parseInt(finalMessageSplits[1]) && curMessage.message.equals(finalMessageSplits[4])) {
                                Log.d("server", "h2");
                                messageQueue.remove(curMessage);
                                messageQueue.add(finalMessageObject);
                            }
                        }
                        Iterator messageIterator1 = messageQueue.iterator();
                        while (messageIterator1.hasNext()) {
                            Messenger curMessage = (Messenger) messageIterator1.next();
                            Log.d("iterator", Integer.toString(curMessage.sequenceNumber));
                            // If top of the queue message is deliverable send it to publish progress
                            // and remove the message from the queue
                            if (curMessage.delivered) {
                                Log.d("Server", curMessage.message);
                                Log.d("server", "h3");
                                publishProgress(curMessage.message);
                                messageQueue.remove(curMessage);
                                Log.d("Server", "Sending the Message to OnProgressUpdate");
                                socket.close();
                            }
                        }
                    } catch (Exception e) {
                        Log.e("Server_msg", e.toString());
                        Log.d("Server","Failed AVD");
                        removePortMessage(localClientPort);
                       // continue;
                    }
                }
            }
            catch (Exception ex)
            {
                ex.getMessage();
            }
            return null;
        }

        protected void removePortMessage(int clientPort){
            Iterator messageIterator3=messageQueue.iterator();
            if(messageIterator3.hasNext()){
                Messenger curMessage= (Messenger)messageIterator3.next();
                if(curMessage.sendingPort==clientPort)
                {
                    messageQueue.remove(curMessage);
                }
            }
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\t\n");
            TextView localTextView = (TextView) findViewById(R.id.textView1);
            localTextView.append("\n");

            //https://docs.google.com/document/d/1fvmg29y0KFEXRFJg5wzw8Gchj1ukYuQRuhw0R1H3pi0/edit
            //Storing Value to the Database Using Content Provider
            ContentValues keyValueToInsert = new ContentValues();

            // inserting <”key-to-insert”, “value-to-insert”>
            keyValueToInsert.put("key",this.sequence_number);
            keyValueToInsert.put("value",strReceived);

            Uri newUri = getContentResolver().insert(
                    providerUri,
                    keyValueToInsert
            );
            sequence_number++;
            return;
        }
    }
    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko
     *
     */
    private class ClientTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            int maxPriority=0;
            // hash map to map the socket with a index number
            // so that a same socket which is multi casting the initial message
            // will multi cast the final message as well
            HashMap<Integer,Socket> socketMap=new HashMap<Integer,Socket>();
            String msgToSend = msgs[0];
            try {
                // Every time the send button gets clicked the client sequence number will increase
                // For example for the first message it will be 1
                // for second message from the same client it will be 2 (1+1)
                clientSequenceNumber++;
                //socket index number
                int socketCounter=0;
                for(String ports:remotePorts) {
                    try {
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(ports));
                        socketMap.put(socketCounter, socket);
                        socketCounter++;
                        // Creating the Final string to multi cast that will contain all the details of the message
                        // todo comments
                        String completeMessage = clientSequenceNumber + ":" + clientPort + ":" + Integer.parseInt(ports) + ":" + "false" + ":" + msgToSend + ":" + "message";
                        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                        DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                        Log.d("Client", "Message to be Sent" + completeMessage);
                        outputStream.writeUTF(completeMessage);
                        outputStream.flush();

                        Log.e("Client",socket.toString());
                        Log.d("Client", "Message Sent");
                        Log.d("Client", "Receiving Priority from Server");
                        int serverProposedPriority = Integer.parseInt(inputStream.readUTF());
                        Log.d("Client", Integer.toString(serverProposedPriority));
                        maxPriority = helper.calculateMax(maxPriority, serverProposedPriority);
                    }
                    catch (Exception e)
                    {
                        Log.d("Client",e.getMessage());
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            }
            try {
                int socketCounter = 0;
                for (String ports : remotePorts) {
                    try {
                        // Creating the Final string to multi cast that will contain all the details of the message
                        // todo comments
                        Log.d("Client", "final message");
                        Socket finalSocket = socketMap.get(socketCounter);
                        socketCounter++;
                        String completeMessage = maxPriority + ":" + clientPort + ":" + Integer.parseInt(ports) + ":" + "false" + ":" + msgToSend + ":" + "acknowledgement";
                        DataOutputStream outputStream = new DataOutputStream(finalSocket.getOutputStream());
                        Log.d("Client", "Message to be Sent final" + completeMessage);
                        outputStream.writeUTF(completeMessage);
                        outputStream.flush();
                        Log.e("Client", finalSocket.toString());
                        Log.d("Client", "Message Sent");
                        finalSocket.close();
                    }
                    catch (SocketTimeoutException e) {
                        Log.e("Client ex",e.getMessage());
                    }
                }
            }
            catch (UnknownHostException ex) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            }
            return null;
        }
    }
}

//https://stackoverflow.com/questions/683041/how-do-i-use-a-priorityqueue
class MessageSequenceComparator implements Comparator<Messenger>
{
    @Override
    public int compare(Messenger message1, Messenger message2)
    {
        if (message1.sequenceNumber<message2.sequenceNumber){
            return -1;
        }
        if (message1.sequenceNumber>message2.sequenceNumber){
            return 1;
        }
        return 0;
    }
}
