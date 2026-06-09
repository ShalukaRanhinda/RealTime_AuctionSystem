package lk.ijse.realtime_auctionsystem.Client2;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Optional;

/**
 * Client2Controller handles the UI interaction and socket communication
 * for a bidder participating in the real-time auction.
 * This acts as an identical secondary client for testing multiple bidders locally.
 */
public class Client2Controller {

    @FXML
    private TextArea txtArea;

    @FXML
    private TextField txtBidAmount;

    @FXML
    private Label lblHighestBid;

    @FXML
    private Label lblStatus;

    @FXML
    private Button btnPlaceBid;

    // Networking streams and connection
    private DataInputStream dis;
    private DataOutputStream dos;
    private Socket remoteSocket = null;
    
    // The display name of the bidder using this client
    private String clientName = "Anonymous";

    /**
     * Called automatically by JavaFX when the UI is loaded.
     * Connects to the server and sets up the listener thread.
     */
    public void initialize() {
        // Prompt the user for their bidding name before starting
        clientName = getClientName();

        // Start a background thread to handle socket networking without freezing the GUI
        new Thread(() -> {
            try {
                // Connect to the Auction Server on localhost at port 6000
                remoteSocket = new Socket("127.0.0.1", 6000);
                dis = new DataInputStream(remoteSocket.getInputStream());
                dos = new DataOutputStream(remoteSocket.getOutputStream());

                // Update UI on the JavaFX application thread
                Platform.runLater(() -> {
                    lblStatus.setText("Connected as " + clientName);
                    txtArea.appendText("Connected to Auction Server...\n");
                });

                // Listen continuously for incoming messages from the server
                while (true) {
                    String message = dis.readUTF();

                    // All UI updates must be run on the JavaFX Platform thread
                    Platform.runLater(() -> {
                        if (message.startsWith("UPDATE:")) {
                            // A new valid bid was placed. Format: UPDATE:<amount>:<bidderName>
                            String[] parts = message.split(":", 3);
                            if (parts.length >= 3) {
                                String amount = parts[1];
                                String bidder = parts[2];
                                lblHighestBid.setText("Current Highest Bid: $" + amount);
                                
                                // Highlight if the current user is winning
                                if (bidder.equals(clientName)) {
                                    lblStatus.setText("You are the highest bidder!");
                                    lblStatus.setStyle("-fx-text-fill: green;");
                                } else {
                                    lblStatus.setText("Highest bidder is " + bidder);
                                    lblStatus.setStyle("-fx-text-fill: #e67e22;");
                                }
                                txtArea.appendText("Bid updated: $" + amount + " by " + bidder + "\n");
                            }
                        } else if (message.startsWith("WINNER:")) {
                            // The server closed the auction. Format: WINNER:<winnerName>:<amount>
                            String[] parts = message.split(":", 3);
                            if (parts.length >= 3) {
                                String winner = parts[1];
                                String amount = parts[2];
                                
                                // Disable further bidding inputs
                                btnPlaceBid.setDisable(true);
                                txtBidAmount.setDisable(true);
                                
                                // Congratulate or inform the user
                                if (winner.equals(clientName)) {
                                    lblStatus.setText("Auction Closed! You won with $" + amount);
                                    lblStatus.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                                } else {
                                    lblStatus.setText("Auction Closed. Winner is " + winner);
                                    lblStatus.setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
                                }
                                txtArea.appendText("Auction Closed! Winner: " + winner + " ($" + amount + ")\n");
                            }
                        } else if (message.startsWith("ERROR:")) {
                            // Display a system error
                            String error = message.substring(6);
                            showAlert("Error", error);
                        } else if (message.startsWith("REJECTED:")) {
                            // The server rejected the user's bid (e.g., too low)
                            String reason = message.substring(9);
                            showAlert("Bid Rejected", reason);
                        }
                    });
                }
            } catch (IOException e) {
                // Handle disconnections gracefully
                Platform.runLater(() -> {
                    lblStatus.setText("Disconnected");
                    lblStatus.setStyle("-fx-text-fill: red;");
                    txtArea.appendText("Server disconnected.\n");
                    btnPlaceBid.setDisable(true);
                    txtBidAmount.setDisable(true);
                });
            }
        }).start();
    }

    /**
     * Prompts the user to input a bidder name via a dialog window.
     * Generates a random name if left blank.
     */
    private String getClientName() {
        while (true) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Join Auction");
            dialog.setHeaderText("Welcome to the Real-Time Auction!");
            dialog.setContentText("Please enter your bidder name:");
            Optional<String> result = dialog.showAndWait();
            
            if (result.isPresent() && !result.get().trim().isEmpty()) {
                return result.get().trim();
            } else {
                // Fallback name if the user hits cancel or leaves it blank
                return "Bidder" + (int)(Math.random() * 1000);
            }
        }
    }

    /**
     * Triggered when the "Place Bid" button is clicked.
     * Validates input locally, then transmits the bid to the server.
     */
    @FXML
    private void handlePlaceBid(ActionEvent event) {
        String amountStr = txtBidAmount.getText().trim();
        if (amountStr.isEmpty()) return;
        
        try {
            double amount = Double.parseDouble(amountStr);
            if (dos != null) {
                // Send the bid request using the expected protocol format
                dos.writeUTF("BID:" + amount + ":" + clientName);
                dos.flush();
            }
        } catch (NumberFormatException e) {
            // Local validation failure
            showAlert("Invalid Input", "Please enter a valid numeric amount.");
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // Clear the text field after sending
        txtBidAmount.clear();
    }
    
    /**
     * Helper method to display a generic warning popup to the user.
     */
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
