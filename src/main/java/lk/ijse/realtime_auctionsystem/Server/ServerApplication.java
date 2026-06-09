package lk.ijse.realtime_auctionsystem.Server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;


public class ServerApplication {

    private static final List<Socket> clients = new CopyOnWriteArrayList<>();
    
    private static final String ITEM_NAME = "Vintage watch";
    private static double highestBid = 5000.0; // Starting price
    private static String highestBidder = "None";
    private static boolean isAuctionOpen = true;


    public static void main(String[] args) {
        System.out.println("Starting Console Auction Server on port 6000...");
        System.out.println("Item for Auction: " + ITEM_NAME);
        System.out.println("Starting Price: LKR " + highestBid);
        
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(6000)) {
                System.out.println("Server listening for connections...");
                
                while (isAuctionOpen) {
                    Socket socket = serverSocket.accept();
                    clients.add(socket);
                    System.out.println("New client connected: " + socket.getInetAddress());
                    
                    sendToClient(socket, "INFO:" + ITEM_NAME + ":" + 5000.0);
                    sendToClient(socket, "UPDATE:" + highestBid + ":" + highestBidder);
                    
                    new Thread(() -> handleClient(socket)).start();
                }
            } catch (IOException e) {
                if (isAuctionOpen) {
                    System.err.println("Server error: " + e.getMessage());
                }
            }
        }).start();

        Scanner scanner = new Scanner(System.in);
        System.out.println("Type 'close' to end the auction.");
        
        while (isAuctionOpen) {
            String command = scanner.nextLine();
            if ("close".equalsIgnoreCase(command.trim())) {
                System.out.println("Closing auction...");
                isAuctionOpen = false; // Stop accepting bids
                
                broadcast("WINNER:" + highestBidder + ":" + highestBid);
                System.out.println("Winner announced: " + highestBidder + " with LKR " + highestBid);
                System.out.println("Server is shutting down.");
                
                System.exit(0);
            } else {
                System.out.println("Unknown command. Type 'close' to end the auction.");
            }
        }
    }


    private static void handleClient(Socket socket) {
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            
            while (true) {
                String message = in.readUTF();
                
                if (message.startsWith("BID:")) {
                    
                    if (!isAuctionOpen) {
                        sendToClient(socket, "ERROR:Auction is closed.");
                        continue;
                    }

                    String[] parts = message.split(":", 3);
                    if (parts.length >= 3) {
                        try {
                            double amount = Double.parseDouble(parts[1]);
                            String bidder = parts[2];

                            if (amount > highestBid) {
                                highestBid = amount;
                                highestBidder = bidder;
                                System.out.println("New highest bid: LKR " + amount + " by " + bidder);
                                
                                broadcast("UPDATE:" + highestBid + ":" + highestBidder);
                            } else {
                                sendToClient(socket, "REJECTED:Bid must be higher than LKR " + highestBid);
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
            clients.remove(socket);
            try {
                socket.close();
            } catch (IOException ex) {
            }
        }
    }


    private static void sendToClient(Socket socket, String message) {
        try {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF(message);
            out.flush();
        } catch (IOException e) {
        }
    }


    private static void broadcast(String message) {
        for (Socket socket : clients) {
            sendToClient(socket, message);
        }
    }
}