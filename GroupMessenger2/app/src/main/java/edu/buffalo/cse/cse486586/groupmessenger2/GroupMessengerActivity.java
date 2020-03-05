package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaExtractor;
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


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.PriorityQueue;

import static android.content.ContentValues.TAG;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    GroupMessengerHelper helper=new GroupMessengerHelper();
    static final int SERVER_PORT=10000;
    int clientSequenceNumber=0;
    int serverSequenceNumber=0;
    int clientPort=0;
    int sequence_number=0;
    String remotePorts[] = new String[]{"11108", "11112", "11116", "11120", "11124"};
    PriorityQueue<Messenger> messageQueue=new PriorityQueue<Messenger>();
    HashMap<Integer,Integer> proposedSequenceNumberServerMap=new HashMap<Integer, Integer>();

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
            proposedSequenceNumberServerMap=helper.fillHashMap(proposedSequenceNumberServerMap,remotePorts);
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
        Uri providerUri= Uri.parse("content://edu.buffalo.cse.cse486586.groupmessenger2.provider");
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            int receivedPriority=0;
            try {
                while (true) {
                    ServerSocket serverSocket = sockets[0];
                    Socket socket = serverSocket.accept();
                    Log.d("Server:", "Connection Successful");
                    //https://stackoverflow.com/questions/34774147/how-to-read-different-object-using-objectinputstream-in-java
                    ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
                    ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
                    Messenger receivedMessage = (Messenger) inputStream.readObject();
                    Log.d("Server:", "Received Messenger Object");
                    Log.d("Server:", "Port Number"+receivedMessage.sendingPort);
                    Log.d("Server:", "Received Message Type"+receivedMessage.messageType);
                    Log.d("Server:", "Received Messenger Message"+receivedMessage.message);
                    String message=receivedMessage.message;
                    publishProgress(receivedMessage.message);
            /*        if(receivedMessage.messageType.equals("message")) {
                        Log.d("Server","Sending Sequence Number");
                        receivedPriority=receivedMessage.sequenceNumber;
                        Log.d("Server","Received Priority"+receivedPriority);
                        serverSequenceNumber=helper.calculateMax(proposedSequenceNumberServerMap.get(receivedMessage.sendingPort),receivedPriority);
                        serverSequenceNumber++;
                        proposedSequenceNumberServerMap.put(receivedMessage.sendingPort, serverSequenceNumber);
                        // Sending the Proposed Sequence Number for the corresponding Avd
                        Messenger sendingMessage = new Messenger(serverSequenceNumber,proposedSequenceNumberServerMap.get(receivedMessage.sendingPort), receivedMessage.receivingPort, false, receivedMessage.message, "proposal");
                        messageQueue.add(sendingMessage);
                        Log.d("Server",Integer.toString(messageQueue.size()));
                        Log.d("Client", "Sequence Number to be proposed" + proposedSequenceNumberServerMap.get(receivedMessage.sendingPort));
                        outputStream.writeObject(sendingMessage);
                        outputStream.flush();
                    }
                    else if(receivedMessage.messageType.equals("acknowledgement"))
                    {
                        Log.d("Server","h1");
                        receivedMessage.delivered=true;
                        //https://stackoverflow.com/questions/8129122/how-to-iterate-over-a-priorityqueue
                        Iterator messageIterator=messageQueue.iterator();
                        Log.d("Server","h2");
                        while (messageIterator.hasNext())
                        {
                            Log.d("Server","h3");
                            Messenger topMessage= messageQueue.poll();
                            Messenger curMessage= (Messenger)messageIterator.next();
                            Log.d("Server","h4");
                            Log.d("server",Integer.toString(messageQueue.size()));
                            Log.d("server",topMessage.message);
                            Log.d("server",Integer.toString(topMessage.sequenceNumber));
                            if(curMessage.sendingPort==receivedMessage.sendingPort && curMessage.message==receivedMessage.message)
                            {
                                messageQueue.remove(curMessage);
                                messageQueue.add(receivedMessage);
                            }
                            if(topMessage.delivered)
                            {
                                Log.d("Server","h4");
                                publishProgress(receivedMessage.message);
                                sequence_number=topMessage.sequenceNumber;
                            }

                        }
                    }*/
                }
            }
            catch (Exception ex)
            {
                ex.getMessage();
            }


            return null;

        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            Log.d("Server Inside",strReceived);
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\t\n");
            TextView localTextView = (TextView) findViewById(R.id.textView1);
            localTextView.append("\n");

            //Storing Value to the Database Using Content Provider
            ContentValues keyValueToInsert = new ContentValues();

            // inserting <”key-to-insert”, “value-to-insert”>
            keyValueToInsert.put("key",sequence_number++);
            keyValueToInsert.put("value",strReceived);

            Uri newUri = getContentResolver().insert(
                    providerUri,
                    keyValueToInsert
            );

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
            Socket socket=null;
            int maxPriority=0;
            int i=0;
            String msgToSend = msgs[0];
         //   Socket[] sockets=new Socket[5];
            // First Time multi casting the message to all the Clients and waiting for their reply
            // on the proposed sequence number
            try {
                for(String port:remotePorts) {
                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(port));
              //      sockets[i]=socket;
                    int sendingPort = Integer.parseInt(port);
                    int receivingPort=clientPort;
                    // Every time a we click on a send button of an avd the client sequence number will increase
                    Messenger sendingMessage = new Messenger(clientSequenceNumber++,sendingPort, receivingPort,false, msgToSend,"message");
                    //https://stackoverflow.com/questions/27736175/how-to-send-receive-objects-using-sockets-in-java
                    ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
                    Log.d("Client","The Message is sent to the Port Number"+sendingPort);
                    Log.d("Client", "Message to be Sent" + sendingMessage.message);
                    outputStream.writeObject(sendingMessage);
                    outputStream.flush();
                    Log.d("Client", "Message Sent");
                    // https://stackoverflow.com/questions/4969760/setting-a-timeout-for-socket-operations
                    // This will make sure that the avd will wait for the replies from all the five avd
        //            //socket.setSoTimeout(2000);
                    //https://stackoverflow.com/questions/27736175/how-to-send-receive-objects-using-sockets-in-java
                    ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
                    //https://stackoverflow.com/questions/34774147/how-to-read-different-object-using-objectinputstream-in-java
                    Messenger receivedMessageObject = (Messenger) inputStream.readObject();
                    int proposedSequenceNumberFromServer=receivedMessageObject.sequenceNumber;
                    Log.d("Client", "Receiving the proposed Sequence Number"+proposedSequenceNumberFromServer);
                    maxPriority=helper.calculateMax(maxPriority,proposedSequenceNumberFromServer);
                }

            }
            catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            }
            catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException"+e.getMessage());
                e.printStackTrace();
            }
            catch (Exception e)
            {
                Log.e(TAG,e.getMessage());
            }
            /*
            // Once a Client got all the sequence number from the servers it calculates the maximum
            // sequence number and then multi casts the message
            // This part contains the code for multi casting the message with final sequence number

                for (String port : remotePorts) {
                    try {
                        int receivingPort = Integer.parseInt(port);
                        Messenger sendingMessage = new Messenger(maxPriority, clientPort, receivingPort, true, msgToSend, "acknowledgement");
                        //https://stackoverflow.com/questions/27736175/how-to-send-receive-objects-using-sockets-in-java
                        ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
                        Log.d("Client", "Message to be Sent" + sendingMessage.message);
                        outputStream.writeObject(sendingMessage);
                        outputStream.flush();
                        Log.d("Client", "Message Sent Final");
              //          Log.d("Server", "Creating a dummy object stream so handshake will be complete and we can close the socket");
                        //https://stackoverflow.com/questions/27736175/how-to-send-receive-objects-using-sockets-in-java
                        //ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
                        //https://stackoverflow.com/questions/34774147/how-to-read-different-object-using-objectinputstream-in-java
                  //      Messenger dummyMessage = (Messenger) inputStream.readObject();
                     //   sockets[i].close();

                    } catch (UnknownHostException e) {
                        Log.e(TAG, "ClientTask UnknownHostException");
                    } catch (IOException e) {
                        Log.e(TAG, "ClientTask socket IOException" + e.getMessage());
                        e.printStackTrace();
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage());
                    }
                }*/
            return null;
        }
    }
}


