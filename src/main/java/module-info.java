module lk.ijse.realtime_auctionsystem {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires lk.ijse.realtime_auctionsystem;
    requires javafx.graphics;


    opens lk.ijse.realtime_auctionsystem to javafx.fxml;
    exports lk.ijse.realtime_auctionsystem;
}