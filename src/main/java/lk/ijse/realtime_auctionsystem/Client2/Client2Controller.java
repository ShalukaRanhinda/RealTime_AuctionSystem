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
    private Label lblItemName;

    @FXML
    private Button btnPlaceBid;

    private DataInputStream dis;
    private DataOutputStream dos;
    private Socket remoteSocket = null;
    
    private String clientName = "Anonymous";


    public void initialize() {
        clientName = getClientName();

        new Thread(() -> {
            try {
                remoteSocket = new Socket("127.0.0.1", 6000);
                dis = new DataInputStream(remoteSocket.getInputStream());
                dos = new DataOutputStream(remoteSocket.getOutputStream());

                Platform.runLater(() -> {
                    lblStatus.setText("Connected as " + clientName);
                    txtArea.appendText("Connected to Auction Server...\n");
                });

                while (true) {
                    String message = dis.readUTF();

                    Platform.runLater(() -> {
                        if (message.startsWith("INFO:")) {
                            String[] parts = message.split(":", 3);
                            if (parts.length >= 3) {
                                lblItemName.setText("Item: " + parts[1]);
                                txtArea.appendText("Auction Started for: " + parts[1] + " (Starting Price: LKR " + parts[2] + ")\n");
                            }
                        } else if (message.startsWith("UPDATE:")) {
                            String[] parts = message.split(":", 3);
                            if (parts.length >= 3) {
                                String amount = parts[1];
                                String bidder = parts[2];
                                lblHighestBid.setText("Current Highest Bid: LKR " + amount);
                                
                                if (bidder.equals(clientName)) {
                                    lblStatus.setText("You are the highest bidder!");
                                    lblStatus.setStyle("-fx-text-fill: green;");
                                } else {
                                    lblStatus.setText("Highest bidder is " + bidder);
                                    lblStatus.setStyle("-fx-text-fill: #e67e22;");
                                }
                                txtArea.appendText("Bid updated: LKR " + amount + " by " + bidder + "\n");
                            }
                        } else if (message.startsWith("WINNER:")) {
                            String[] parts = message.split(":", 3);
                            if (parts.length >= 3) {
                                String winner = parts[1];
                                String amount = parts[2];
                                
                                btnPlaceBid.setDisable(true);
                                txtBidAmount.setDisable(true);
                                
                                if (winner.equals(clientName)) {
                                    lblStatus.setText("Auction Closed! You won with LKR " + amount);
                                    lblStatus.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                                } else {
                                    lblStatus.setText("Auction Closed. Winner is " + winner);
                                    lblStatus.setStyle("-fx-text-fill: #c0392b; -fx-font-weight: bold;");
                                }
                                txtArea.appendText("Auction Closed! Winner: " + winner + " (LKR " + amount + ")\n");
                            }
                        } else if (message.startsWith("ERROR:")) {
                            String error = message.substring(6);
                            showAlert("Error", error);
                        } else if (message.startsWith("REJECTED:")) {
                            String reason = message.substring(9);
                            showAlert("Bid Rejected", reason);
                        }
                    });
                }
            } catch (IOException e) {
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
                return "Bidder" + (int)(Math.random() * 1000);
            }
        }
    }


    @FXML
    private void handlePlaceBid(ActionEvent event) {
        String amountStr = txtBidAmount.getText().trim();
        if (amountStr.isEmpty()) return;
        
        try {
            double amount = Double.parseDouble(amountStr);
            if (dos != null) {

                dos.writeUTF("BID:" + amount + ":" + clientName);
                dos.flush();
            }
        } catch (NumberFormatException e) {
            showAlert("Invalid Input", "Please enter a valid numeric amount.");
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        txtBidAmount.clear();
    }
    

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
