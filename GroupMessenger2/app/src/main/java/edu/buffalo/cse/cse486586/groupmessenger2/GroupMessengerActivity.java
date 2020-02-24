package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
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

import static android.content.ContentValues.TAG;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 * 
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {
    static final int SERVER_PORT=10000;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
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

            try {
                while (true) {
                    Socket socket = serverSocket.accept();
                    Log.d("Server:", "Connection Successfull");

                    DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                    String inputString = inputStream.readUTF();
                    Log.d("Server:", "Received String");
                    Log.d("Server", "Sending the Message to OnProgressUpdate");
                    publishProgress(inputString);
                    Log.d("Server", "Creating a dummy output stream so handshake will be complete and we can close the socket");
                    DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                    outputStream.writeUTF("Acknowledgement");
                    socket.close();
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
            TextView remoteTextView = (TextView) findViewById(R.id.textView1);
            remoteTextView.append(strReceived + "\t\n");
            TextView localTextView = (TextView) findViewById(R.id.textView1);
            localTextView.append("\n");

            //Storing Value to the Database Using Content Provider
            ContentValues keyValueToInsert = new ContentValues();

            // inserting <”key-to-insert”, “value-to-insert”>
            keyValueToInsert.put("key",this.sequence_number++);
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
            try {
                Socket socket=null;
                String remotePorts[] = new String[]{"11108", "11112", "11116", "11120", "11124"};

                for(String port:remotePorts) {
                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(port));


                    String msgToSend = msgs[0];
                    DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                    Log.d("Client", "Message to be Sent" + msgToSend);
                    outputStream.writeUTF(msgToSend);
                    Log.d("Client", "Message Sent");
                    Log.d("Server", "Creating a dummy output stream so handshake will be complete and we can close the socket");
                    DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                    String dummyString = inputStream.readUTF();
                    socket.close();
                }

            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            }

            return null;
        }
    }
}


