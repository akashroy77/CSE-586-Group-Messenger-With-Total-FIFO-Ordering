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
    int clientSequenceNumber=0;
    int serverSequenceNumber=0;
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
        int sequence_number=0;
        Uri providerUri= Uri.parse("content://edu.buffalo.cse.cse486586.groupmessenger2.provider");
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            String[] messageSplits;
            int localClientPort=0;
            int localServerPort=0;
            Log.e(TAG,"IN SERVER");
            try {
                while (true) {
                    try {
                        Socket socket = serverSocket.accept();
                        Log.d("Server:", "Connection Successful");
                        DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                        String inputString = inputStream.readUTF();
                        Log.d("Server:", "Received String" + inputString);
                        messageSplits = inputString.split(":");
                        int receivedSequenceNumberFromClient = Integer.parseInt(messageSplits[0]);
                        localClientPort = Integer.parseInt(messageSplits[1]);
                        localServerPort = Integer.parseInt(messageSplits[2]);
                        boolean deliveryStatus = messageSplits[3].equals("false") ? false : true;
                        String receivedMessage = messageSplits[4];
                        String receivedMessageType = messageSplits[5];
                        if (receivedMessageType.equals("message")) {
                            int tempSequenceNumber=serverSequenceNumber;
                            // server sequence number=max(own sequence number,client sent sequence number)
                            serverSequenceNumber=helper.calculateMax(receivedSequenceNumberFromClient,serverSequenceNumber);
                            // Sending proposed sequence Number to client
                            Messenger serverMessage = new Messenger(serverSequenceNumber, localClientPort, localServerPort, deliveryStatus, receivedMessage, receivedMessageType);
                            serverSequenceNumber=(tempSequenceNumber==serverSequenceNumber)?serverSequenceNumber++:receivedSequenceNumberFromClient++;
                            // adding to the priority queue
                            messageQueue.add(serverMessage);
                            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                            outputStream.writeUTF(Integer.toString(serverSequenceNumber));
                            outputStream.flush();
                            Log.d("Server", "Sending the Proposed Sequence Number");
                        }

                        String finalMessage = inputStream.readUTF();
                        String[] finalMessageSplits = finalMessage.split(":");
                        Messenger fMessage = new Messenger(Integer.parseInt(finalMessageSplits[0]), Integer.parseInt(finalMessageSplits[1]), Integer.parseInt(finalMessageSplits[2]), true, finalMessageSplits[4], finalMessageSplits[5]);
                        Iterator messageIterator = messageQueue.iterator();
                        while (messageIterator.hasNext()) {
                            Messenger curMessage = (Messenger) messageIterator.next();
                            Log.d("iterator", Integer.toString(curMessage.sequenceNumber));

                            if (curMessage.sendingPort == Integer.parseInt(finalMessageSplits[1]) && curMessage.message.equals(finalMessageSplits[4])) {
                                Log.d("server", "h2");
                                messageQueue.remove(curMessage);
                                messageQueue.add(fMessage);
                            }
                        }
                        Iterator messageIterator1 = messageQueue.iterator();
                        while (messageIterator1.hasNext()) {
                            Messenger curMessage = (Messenger) messageIterator1.next();
                            Log.d("iterator", Integer.toString(curMessage.sequenceNumber));
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
                        continue;
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
            HashMap<Integer,Socket> socketMap=new HashMap<Integer,Socket>();
            String msgToSend = msgs[0];
            try {
                // Every time the send button gets clicked the client sequence number will increase
                // For example for the first message it will be 1
                // for the next message from a client it will be 2 (1+1)
                clientSequenceNumber++;
                int socketCounter=0;
                for(String ports:remotePorts) {
                    try {
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(ports));
                        socketMap.put(socketCounter, socket);
                        socketCounter++;
                        // Sending the Message to Server
                        // Along with its local priority,sender's port,receiver's port,delivery status and type
                        String completeMessage = clientSequenceNumber+ ":" + clientPort + ":" + Integer.parseInt(ports) + ":" + "false" + ":" + msgToSend + ":" + "message";
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
                        Log.d("Client","Akash");
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            }
            try {
                int socketCounter = 0;
                for (String ports : remotePorts) {
                    try {
                        //Sending the message with its final priority
                        Log.d("Client", "final message");
                        //Mapping the socket
                        //so the sender of the multi cast will send the final message
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
                    } catch (Exception ex) {
                        Log.d("Client",ex.getMessage());
                    }
                }
            }catch (Exception e) {
                Log.e(TAG, "ClientTask UnknownHostException");
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
