package lk.ijse.realtime_auctionsystem.Server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * ServerApplication acts as the central hub for the Real-Time Auction System.
 * It manages connected clients, stores the current auction state (highest bid, winner),
 * and validates incoming bids to ensure they follow the rules.
 * This runs entirely as a Console Application.
 */
public class ServerApplication {

    // Thread-safe list to store all connected client sockets
    private static final List<Socket> clients = new CopyOnWriteArrayList<>();
    
    // Core auction state variables
    private static double highestBid = 0.0;
    private static String highestBidder = "None";
    private static boolean isAuctionOpen = true;

    /**
     * Main entry point for the server console application.
     * Starts the socket server and listens for console commands.
     */
    public static void main(String[] args) {
        System.out.println("Starting Console Auction Server on port 6000...");
        
        // Start a dedicated thread to listen for incoming client connections
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(6000)) {
                System.out.println("Server listening for connections...");
                
                // Keep accepting clients as long as the auction is open
                while (isAuctionOpen) {
                    Socket socket = serverSocket.accept();
                    clients.add(socket);
                    System.out.println("New client connected: " + socket.getInetAddress());
                    
                    // Immediately send the current auction state to the newly connected client
                    sendToClient(socket, "UPDATE:" + highestBid + ":" + highestBidder);
                    
                    // Handle further communication with this client in a separate thread
                    new Thread(() -> handleClient(socket)).start();
                }
            } catch (IOException e) {
                // Only log the error if we didn't intentionally close the auction
                if (isAuctionOpen) {
                    System.err.println("Server error: " + e.getMessage());
                }
            }
        }).start();

        // Listen for input from the server administrator via the console
        Scanner scanner = new Scanner(System.in);
        System.out.println("Type 'close' to end the auction.");
        
        // Command loop
        while (isAuctionOpen) {
            String command = scanner.nextLine();
            if ("close".equalsIgnoreCase(command.trim())) {
                System.out.println("Closing auction...");
                isAuctionOpen = false; // Stop accepting bids
                
                // Announce the winner to all connected clients
                broadcast("WINNER:" + highestBidder + ":" + highestBid);
                System.out.println("Winner announced: " + highestBidder + " with $" + highestBid);
                System.out.println("Server is shutting down.");
                
                // Terminate the server
                System.exit(0);
            } else {
                System.out.println("Unknown command. Type 'close' to end the auction.");
            }
        }
    }

    /**
     * Handles incoming messages from a specific client socket.
     * 
     * @param socket The client's socket connection
     */
    private static void handleClient(Socket socket) {
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            
            // Continuously listen for messages from the client
            while (true) {
                String message = in.readUTF();
                
                // Check if the message is a bid attempt
                if (message.startsWith("BID:")) {
                    
                    // Reject bids if the auction is already closed
                    if (!isAuctionOpen) {
                        sendToClient(socket, "ERROR:Auction is closed.");
                        continue;
                    }

                    // Parse the bid data, formatted as "BID:<amount>:<bidderName>"
                    String[] parts = message.split(":", 3);
                    if (parts.length >= 3) {
                        try {
                            double amount = Double.parseDouble(parts[1]);
                            String bidder = parts[2];

                            // Validate the bid: it must strictly be greater than the current highest bid
                            if (amount > highestBid) {
                                highestBid = amount;
                                highestBidder = bidder;
                                System.out.println("New highest bid: $" + amount + " by " + bidder);
                                
                                // Broadcast the new highest bid to all connected clients
                                broadcast("UPDATE:" + highestBid + ":" + highestBidder);
                            } else {
                                // Reject invalid bids and notify the specific client
                                sendToClient(socket, "REJECTED:Bid must be higher than $" + highestBid);
                            }
                        } catch (NumberFormatException e) {
                            sendToClient(socket, "ERROR:Invalid bid amount.");
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Client disconnected.");
        } finally {
            // Clean up the client from the list when they disconnect
            clients.remove(socket);
            try {
                socket.close();
            } catch (IOException ex) {
                // Ignore cleanup errors
            }
        }
    }

    /**
     * Sends a direct message to a specific client.
     * 
     * @param socket  The destination socket
     * @param message The raw message string
     */
    private static void sendToClient(Socket socket, String message) {
        try {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF(message);
            out.flush();
        } catch (IOException e) {
            // Ignore transmission errors
        }
    }

    /**
     * Broadcasts a message to all currently connected clients.
     * 
     * @param message The raw message string to broadcast
     */
    private static void broadcast(String message) {
        for (Socket socket : clients) {
            sendToClient(socket, message);
        }
    }
}