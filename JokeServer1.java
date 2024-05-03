/*

Name: Shashank Katasani
Date: 26thjan 2024
Java Version: 21.0.2 (build 21.0.2+13-58)
Compilation: go to file location and "javac JokeServer.java". this should create 4 class file in that same folder
Exicution: open more thank on terminal and got to location where you have the class file(compiled from befor code it should be same location)
"java ColorClient" to run client and send request to server
"java ColorServer" to run the server. And now you can start sending requests.

*/


import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Scanner;

// Joke Server class
/*
The Joke Server listens for incoming client connections on port 4545.
It has a worker thread to handle each client connection.
The server can toggle between "joke" and "proverb" modes using an admin connection on port 5050.
When a client connects, it receives either a joke cycle or a proverb cycle based on the current server mode.
The server maintains conversation state for each client, ensuring that they receive unique jokes or proverbs in each cycle.
*/

public class JokeServer {
    private static boolean isJokeMode = true; // Variable to keep track of the server's current mode: true for joke mode, false for proverb mode

    private static Map<String, Queue<String>> clientJokeState = new HashMap<>(); // Map to maintain conversation state for each client

    public static void main(String[] args) throws IOException {
        int serverPort = 4545; // Port on which the server will listen for connections

        // Display startup message for the Joke Server
        System.out.println("Server ready to connect at" + serverPort + ".\n");

        // Create a ServerSocket to listen for incoming client connections
        ServerSocket servSock = new ServerSocket(serverPort);
        System.out.println("Server open for requests"); // Indicates that the server is ready for connections

        // Start a separate thread to handle admin connections asynchronously
        ExecutorService adminExecutor = Executors.newSingleThreadExecutor();
        adminExecutor.submit(new AdminLooper());

        // Enter an infinite loop to continuously accept incoming client connections
        while (true) {
            Socket sock = servSock.accept(); // Accept request from client 
            System.out.println("Connected To " + sock + "\n"); // Display information about the client connection

            // Start a new JokeWorker thread to handle the client connection
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            String clientName = in.readLine(); // Read the name of the client
            new JokeWorker(sock, clientName).start();
        }
    }

    // Worker thread class to handle client connections
    static class JokeWorker extends Thread {
        String clientName;
        Socket sock;

        JokeWorker(Socket s, String clientName) {
            sock = s;
            this.clientName = clientName;
        }

        public void run() {
            try {
                // Display the present enabled mode
                System.out.println("Client '" + clientName + "' connected.");
                System.out.println("Current mode: " + (isJokeMode ? "Joke" : "Proverb"));

                // Get the conversation state for the client
                Queue<String> clientState = getClientState(clientName);

                /// Create an output stream for serializing and sending data to the client
                ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream());

                // Send a joke or proverb based on the server mode and client state
                String jokeOrProverb = getJokeOrProverb(clientState); // Modified to remove clientName parameter
                out.writeObject(new JokeMessage(clientName, jokeOrProverb));

                sock.close(); // The connection from client will be terminated here
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Method to get the conversation state for the client
        private Queue<String> getClientState(String clientName) {
            return clientJokeState.computeIfAbsent(clientName, k -> isJokeMode ? generateJokes() : generateProverbs());
        }

        // Method to generate jokes dynamically, inserting the client's name
        private Queue<String> generateJokes() {
            List<String> jokesList = Arrays.asList(
                    "JA " + clientName + ": Why did the guy name his dogs timex and rolex? Cause they watchdogs",
                    "JB " + clientName + ": What dating apps do cannibals use? Tender",
                    "JC " + clientName + ": What do u call a pig who knows how to use a knife? A pork chop",
                    "JD " + clientName + ": How can u tell if a pig is hot? Its bacon"
            );
            Collections.shuffle(jokesList); // Shuffle the list of jokes
            return new LinkedList<>(jokesList);
        }

        // Method to generate proverbs dynamically, inserting the client's name
        private Queue<String> generateProverbs() {
            List<String> proverbsList = Arrays.asList(
                    "PA " + clientName + ": Absence makes the heart grow fonder",
                    "PB " + clientName + ": All good things must come to an end",
                    "PC " + clientName + ": A watched pot never boils",
                    "PD " + clientName + ": Beggars can’t be choosers"
            );
            Collections.shuffle(proverbsList); // Shuffle the list of proverbs
            return new LinkedList<>(proverbsList);
        }

        // Method to get a joke or proverb based on the server mode and client state
        private String getJokeOrProverb(Queue<String> clientState) { // Modified to remove clientName parameter
            if (clientState.isEmpty()) {
                // Reset client's state
                clientState.addAll(isJokeMode ? generateJokes() : generateProverbs());
                return isJokeMode ? "JOKE CYCLE COMPLETED" : "PROVERB CYCLE COMPLETED";
            }

            String jokeOrProverb = clientState.poll();
            return jokeOrProverb;
        }
    }

    // Worker thread class to handle admin connections
    static class AdminLooper implements Runnable {
        public void run() {
            int adminPort = 5050; // Port for admin connections

            try {
                // ServerSocket to wait for incoming connections from the admin
                ServerSocket adminSock = new ServerSocket(adminPort);

                while (true) {
                    Socket admin = adminSock.accept(); // Accept the connection from admin 
                    System.out.println("Connected to admin from " + admin);

                    // Toggle server mode and display the current mode
                    isJokeMode = !isJokeMode;
                    System.out.println("Server mode toggled to " + (isJokeMode ? "joke" : "proverb"));

                    // Send the current mode to the client
                    PrintWriter out = new PrintWriter(admin.getOutputStream(), true);
                    out.println(isJokeMode ? "JOKE" : "PROVERB");

                    admin.close(); // The connection from the admin will be terminated here
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

/*
The Joke Client connects to the server on localhost and port 4545.
It sends its name to the server upon connection.
It continuously receives jokes or proverbs from the server and displays them.
The client prompts the user to press Enter for another joke or type 'quit' to exit.
When the server completes a cycle of jokes or proverbs, the client informs the user and continues receiving jokes or proverbs.
*/
// Joke Client class
class JokeClient {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        String serverName = "localhost"; // Server name (localhost for testing)
        int serverPort = 4545; // Port on which the server is listening

        // Setup input stream to read user input
        Scanner userInput = new Scanner(System.in);

        System.out.print("Enter your name: ");
        String clientName = userInput.nextLine(); // Read the name of the client

        while (true) {
            try {
                System.out.println("Connection in progress");

                // Connect to the server
                Socket socket = new Socket(serverName, serverPort);

                System.out.println("Connected to the server.");

                // Setup output stream to send the client name to the server
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println(clientName);

                // Setup input stream to receive serialized data from the server
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

                System.out.println("Waiting for server response...");

                // Read the serialized joke or proverb object received from the server
                Object responseObj = in.readObject();
                if (responseObj instanceof JokeMessage) {
                    JokeMessage response = (JokeMessage) responseObj;
                    String message = response.getMessage();

                    // Concatenate the client's name directly to the joke message
                    String formattedMessage = message;

                    System.out.println(formattedMessage);

                    // Check if the server response indicates cycle completion
                    if (message.equalsIgnoreCase("JOKE CYCLE COMPLETED") || message.equalsIgnoreCase("PROVERB CYCLE COMPLETED")) {
                        // Inform the user about cycle completion and continue
                        System.out.println("Server cycle completed. Getting another joke...\n");
                        continue;
                    }
                }

                // Prompt the user for input
                System.out.print("Press anything other than 'quit' to get another joke, or type 'quit' to exit: ");
                String userInputStr = userInput.nextLine();

                // Check if the user wants to exit
                if (userInputStr.equalsIgnoreCase("quit")) {
                    // Close the connection and exit the client
                    System.out.println("Exiting the client.");
                    socket.close();
                    break;
                }

                // Request another joke from the server
                System.out.println("Getting another joke...\n");

                // Close the connection
                socket.close();
            } catch (EOFException e) {
                // Handle EOFException (server closed the connection unexpectedly)
                System.err.println("Server closed the connection unexpectedly. Retrying...");
            } catch (ConnectException e) {
                // Handle ConnectException (failed to connect to the server)
                System.err.println("Server connection Failed. Retrying...");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Close the Scanner
        userInput.close();
    }
}

/*
The Joke Client Admin connects to the server admin port on localhost and port 5050.
It retrieves the current server mode (joke or proverb) and displays it.
The admin client allows toggling between joke and proverb modes by sending a "toggle" command to the server.
It provides an option to exit the admin client.
*/
class JokeClientAdmin {
    public static void main(String[] args) {
        // Server details
        String serverName = "localhost"; // Server name (localhost for testing)
        int adminPort = 5050; // Port for admin connections

        boolean connected = false;
        while (!connected) {
            try {
                // Following is to connect to server
                Socket socket = new Socket(serverName, adminPort);
                System.out.println("Connected to server admin port.");
                connected = true; // Mark as connected

                // Setup input stream to receive mode information from the server
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Send a request to the server to retrieve the mode
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println("MODE_REQUEST");

                // Read the server's response (mode)
                String mode = reader.readLine();
                System.out.println("Server mode: " + mode);

                // Close the connection
                socket.close();

                // Continuously listen for user input to toggle again or exit
                BufferedReader userInputReader = new BufferedReader(new InputStreamReader(System.in));
                while (true) {
                    System.out.print("press anything other than 'quit to toggle', or 'quit' to disconnect: ");
                    String userInput = userInputReader.readLine();

                    if ("quit".equalsIgnoreCase(userInput)) {
                        // Exit the program
                        System.out.println("Exiting...");
                        return;
                    } else {
                        // Toggle the mode again
                        connected = false; // Set connected to false to retry connecting
                        break; // Exit the inner loop to retry connecting
                    }
                }
            } catch (ConnectException e) {
                // Handle ConnectException (failed to connect to the server)
                System.err.println("Failed to connect to the server admin port. Retrying in 5 seconds...");
                try {
                    Thread.sleep(5000); // Wait for 5 seconds before retrying
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}


// Serializable class for data exchanged between client and server
class JokeMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private String clientName; // Name of the client or user
    private String message;

    public JokeMessage(String clientName, String message) {
        this.clientName = clientName;
        this.message = message;
    }

    public String getClientName() {
        return clientName;
    }

    public String getMessage() {
        return message;
    }
}





/*
OUTPUT LOG:-

Server
Last login: Sun Feb 11 18:59:13 on ttys404
shashank@shashanks-MacBook-Air ~ % cd Desktop/final
shashank@shashanks-MacBook-Air final % javac JokeServer.java
shashank@shashanks-MacBook-Air final % java JokeServer      
Server ready to connect at4545.

Server open for requests
Connected To Socket[addr=/127.0.0.1,port=57856,localport=4545]

Client 'shashank' connected.
Current mode: Joke
Connected To Socket[addr=/127.0.0.1,port=57857,localport=4545]

Client 'shashank' connected.
Current mode: Joke
Connected To Socket[addr=/127.0.0.1,port=57858,localport=4545]

Client 'shashank' connected.
Current mode: Joke
Connected To Socket[addr=/127.0.0.1,port=57859,localport=4545]

Client 'shashank' connected.
Current mode: Joke
Connected To Socket[addr=/127.0.0.1,port=57860,localport=4545]

Client 'shashank' connected.
Current mode: Joke
Connected To Socket[addr=/127.0.0.1,port=57861,localport=4545]

Client 'shashank' connected.
Current mode: Joke
Connected to admin from Socket[addr=/127.0.0.1,port=57862,localport=5050]
Server mode toggled to proverb
Connected To Socket[addr=/127.0.0.1,port=57863,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57864,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57865,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57866,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57867,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57868,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57869,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57870,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57871,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57872,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57873,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57874,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57875,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57876,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57877,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57878,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57879,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57880,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57881,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57882,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57883,localport=4545]

Client 'shashank' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57894,localport=4545]

Client 'sam' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57895,localport=4545]

Client 'sam' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57896,localport=4545]

Client 'sam' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57897,localport=4545]

Client 'sam' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57898,localport=4545]

Client 'sam' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57899,localport=4545]

Client 'sam' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57900,localport=4545]

Client 'sam' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57901,localport=4545]

Client 'sam' connected.
Current mode: Proverb
Connected To Socket[addr=/127.0.0.1,port=57902,localport=4545]

Client 'sam' connected.
Current mode: Proverb
Connected to admin from Socket[addr=/127.0.0.1,port=57903,localport=5050]
Server mode toggled to joke
Connected To Socket[addr=/127.0.0.1,port=57904,localport=4545]

Client 'sam' connected.
Current mode: Joke
Connected To Socket[addr=/127.0.0.1,port=57905,localport=4545]

Client 'sam' connected.
Current mode: Joke
Connected To Socket[addr=/127.0.0.1,port=57906,localport=4545]

Client 'sam' connected.
Current mode: Joke
Connected To Socket[addr=/127.0.0.1,port=57907,localport=4545]

Client 'sam' connected.
Current mode: Joke
Connected To Socket[addr=/127.0.0.1,port=57908,localport=4545]

Client 'sam' connected.
Current mode: Joke







Client
Last login: Sun Feb 11 19:07:12 on ttys401
shashank@shashanks-MacBook-Air ~ % java JokeClient      
Error: Could not find or load main class JokeClient
Caused by: java.lang.ClassNotFoundException: JokeClient
shashank@shashanks-MacBook-Air ~ % cd Desktop/final
shashank@shashanks-MacBook-Air final % java JokeClient 
Enter your name: shashank
Connection in progress
Connected to the server.
Waiting for server response...
JC shashank: What do u call a pig who knows how to use a knife? A pork chop
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
JB shashank: What dating apps do cannibals use? Tender
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
JA shashank: Why did the guy name his dogs timex and rolex? Cause they watchdogs
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
JD shashank: How can u tell if a pig is hot? Its bacon
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
JOKE CYCLE COMPLETED
Server cycle completed. Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
JA shashank: Why did the guy name his dogs timex and rolex? Cause they watchdogs
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
JD shashank: How can u tell if a pig is hot? Its bacon
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
JC shashank: What do u call a pig who knows how to use a knife? A pork chop
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
JB shashank: What dating apps do cannibals use? Tender
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PROVERB CYCLE COMPLETED
Server cycle completed. Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PD shashank: Beggars can’t be choosers
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PC shashank: A watched pot never boils
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PB shashank: All good things must come to an end
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PA shashank: Absence makes the heart grow fonder
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PROVERB CYCLE COMPLETED
Server cycle completed. Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PA shashank: Absence makes the heart grow fonder
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PB shashank: All good things must come to an end
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PC shashank: A watched pot never boils
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PD shashank: Beggars can’t be choosers
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PROVERB CYCLE COMPLETED
Server cycle completed. Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PA shashank: Absence makes the heart grow fonder
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PB shashank: All good things must come to an end
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PD shashank: Beggars can’t be choosers
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PC shashank: A watched pot never boils
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PROVERB CYCLE COMPLETED
Server cycle completed. Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PA shashank: Absence makes the heart grow fonder
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PC shashank: A watched pot never boils
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 





Client
Last login: Sun Feb 11 19:08:56 on ttys405
shashank@shashanks-MacBook-Air ~ % sam
zsh: command not found: sam
shashank@shashanks-MacBook-Air ~ % cd Desktop/final
shashank@shashanks-MacBook-Air final % java JokeClient      
Enter your name: sam
Connection in progress
Connected to the server.
Waiting for server response...
PC sam: A watched pot never boils
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PA sam: Absence makes the heart grow fonder
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PD sam: Beggars can’t be choosers
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PB sam: All good things must come to an end
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PROVERB CYCLE COMPLETED
Server cycle completed. Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PC sam: A watched pot never boils
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PD sam: Beggars can’t be choosers
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PA sam: Absence makes the heart grow fonder
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
PB sam: All good things must come to an end
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
JOKE CYCLE COMPLETED
Server cycle completed. Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
JC sam: What do u call a pig who knows how to use a knife? A pork chop
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
JD sam: How can u tell if a pig is hot? Its bacon
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
JA sam: Why did the guy name his dogs timex and rolex? Cause they watchdogs
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 
Getting another joke...

Connection in progress
Connected to the server.
Waiting for server response...
JB sam: What dating apps do cannibals use? Tender
Press anything other than 'quit' to get another joke, or type 'quit' to exit: 





Client admin
Last login: Sun Feb 11 19:07:36 on ttys404
shashank@shashanks-MacBook-Air ~ % cd Desktop/final
shashank@shashanks-MacBook-Air final % java JokeClientAdmin 
Connected to server admin port.
Server mode: PROVERB
press anything other than 'quit to toggle', or 'quit' to disconnect: 
Connected to server admin port.
Server mode: JOKE
press anything other than 'quit to toggle', or 'quit' to disconnect:  

*/

/*Comments:-
I did the same hashmapping but I was only able to toggle between cycles, if I was on joke mode toggled it to
proverb mode proverbs  were coming only after all jokes in cycle were completed even though mode was changed 
in server. Did you encounter similar problem while implementing?


I also tried same additional feature but I couldn't handle hashmap properly, I should have started little earlier&nbsp;
 */
