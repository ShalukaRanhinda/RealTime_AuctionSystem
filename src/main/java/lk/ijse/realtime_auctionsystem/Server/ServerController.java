package lk.ijse.realtime_auctionsystem.Server;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerController {

    @FXML
    private TextArea txtArea;

    @FXML
    private Label lblCurrentBid;

    @FXML
    private Label lblHighestBidder;

    @FXML
    private Button btnCloseAuction;

    private ServerSocket serverSocket = null;
    private final List<Socket> clients = new CopyOnWriteArrayList<>();

    private double highestBid = 0.0;
    private String highestBidder = "None";
    private boolean isAuctionOpen = true;

    public void initialize() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(6000);
                Platform.runLater(() -> txtArea.appendText("Auction Server started on port 6000...\n"));

                while (true) {
                    Socket socket = serverSocket.accept();
                    clients.add(socket);

                    Platform.runLater(() -> txtArea.appendText("New client connected\n"));
                    
                    sendToClient(socket, "UPDATE:" + highestBid + ":" + highestBidder);
                    if (!isAuctionOpen) {
                        sendToClient(socket, "WINNER:" + highestBidder + ":" + highestBid);
                    }

                    new Thread(() -> handleClient(socket)).start();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleClient(Socket socket) {
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
                                
                                String logMsg = "New highest bid: $" + amount + " by " + bidder;
                                Platform.runLater(() -> {
                                    txtArea.appendText(logMsg + "\n");
                                    lblCurrentBid.setText("Current Highest Bid: $" + highestBid);
                                    lblHighestBidder.setText("Highest Bidder: " + highestBidder);
                                });

                                broadcast("UPDATE:" + highestBid + ":" + highestBidder);
                            } else {
                                sendToClient(socket, "REJECTED:Bid must be higher than current highest bid ($" + highestBid + ").");
                            }
                        } catch (NumberFormatException e) {
                            sendToClient(socket, "ERROR:Invalid bid amount.");
                        }
                    }
                }
            }
        } catch (IOException e) {
            Platform.runLater(() -> txtArea.appendText("Client disconnected\n"));
            e.printStackTrace();
        } finally {
            clients.remove(socket);
            try {
                socket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private void sendToClient(Socket socket, String message) {
        try {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeUTF(message);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void broadcast(String message) {
        for (Socket socket : clients) {
            sendToClient(socket, message);
        }
    }

    @FXML
    private void handleCloseAuction(ActionEvent event) {
        if (!isAuctionOpen) return;
        
        isAuctionOpen = false;
        btnCloseAuction.setDisable(true);
        String msg = "Auction closed! Winner is " + highestBidder + " with $" + highestBid;
        txtArea.appendText(msg + "\n");
        broadcast("WINNER:" + highestBidder + ":" + highestBid);
    }
}
