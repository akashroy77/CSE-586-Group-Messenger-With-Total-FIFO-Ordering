# CSE-586-Group-Messenger-PART-2
You need to implement total and FIFO guarantees. 

You will need to design an algorithm that does this and implement it. 

An important thing to keep in mind is that there will be a failure of an app instance in the middle of the execution. The requirements are:

Your app should multicast every user-entered message to all app instances (including the one that is sending the message). In the rest of the description, “multicast” always means sending a message to all app instances.

Your app should use B-multicast. It should not implement R-multicast.

You need to come up with an algorithm that provides a total-FIFO ordering under a failure.

There will be at most one failure of an app instance in the middle of execution.  We will emulate a failure only by force closing an app instance. We will not emulate a failure by killing an entire emulator instance. When a failure happens, the app instance will never come back during a run.

Each message should be used to detect a node failure.

For this purpose, you can use a timeout for a socket read; you can pick a reasonable timeout value (e.g., 500 ms), and if a node does not respond within the timeout, you can consider it a failure.

This means that you need to handle socket timeout exceptions in addition to socket creation/connection exceptions.

Do not just rely on socket creation or connect status to determine if a node has failed. Due to the Android emulator networking setup, it is not safe to just rely on socket creation or connect status to judge node failures. Please also use socket read timeout exceptions as described above.

You cannot assume which app instance will fail. In fact, the grader will run your group messenger multiple times and each time it will kill a different instance. Thus, you should not rely on chance (e.g., randomly picking a central sequencer) to handle failures. This is just hoping to avoid failures. Instead, you should implement a decentralized algorithm (e.g., something based on ISIS).

When handling a failure, it is important to make sure that your implementation does not stall. After you detect a failure, you need to clean up any state related to it, and move on.

When there is a node failure, the grader will not check how you are ordering the messages sent by the failed node. Please refer to the testing section below for details.

As with the previous PAs, we have fixed the ports & sockets.
Your app should open one server socket that listens on 10000.
You need to use run_avd.py and set_redir.py to set up the testing environment.
The grading will use 5 AVDs. The redirection ports are 11108, 11112, 11116, 11120, and 11124.
You should just hard-code the above 5 ports and use them to set up connections.
Please use the code snippet provided in PA1 on how to determine your local AVD.
emulator-5554: “5554”
emulator-5556: “5556”
emulator-5558: “5558”
emulator-5560: “5560”
emulator-5562: “5562”

Every message should be stored in your provider individually by all app instances. Each message should be stored as a <key, value> pair. The key should be the final delivery sequence number for the message (as a string); the value should be the actual message (again, as a string). The delivery sequence number should start from 0 and increase by 1 for each message.
