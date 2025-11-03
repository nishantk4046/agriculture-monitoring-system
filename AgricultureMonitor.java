

import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.text.SimpleDateFormat;

public class AgricultureMonitor extends Frame implements ActionListener, WindowListener {
    // --- DB Config: change these for your setup ---
    static final String DB_URL = "jdbc:mysql://localhost:3306/agri_db?useSSL=false&serverTimezone=UTC";
    static final String DB_USER = "root"; // change
    static final String DB_PASS = "1234"; // change

    // UI components
    Label lblId = new Label("ID:");
    TextField tfId = new TextField(10);
    Label lblName = new Label("Name:");
    TextField tfName = new TextField(20);
    Label lblLocation = new Label("Location:");
    TextField tfLocation = new TextField(20);
    Label lblReading = new Label("Reading:");
    TextField tfReading = new TextField(10);

    Button btnCreate = new Button("Create");
    Button btnRead = new Button("Read");
    Button btnUpdate = new Button("Update");
    Button btnDelete = new Button("Delete");
    Button btnList = new Button("List All");
    Button btnClear = new Button("Clear");

    TextArea taOutput = new TextArea(12, 60);

    Connection conn;

    public AgricultureMonitor() {
        super("Simple Agriculture Monitoring - AWT + MySQL");
        setLayout(new BorderLayout(8, 8));

        // Top input panel
        Panel top = new Panel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0; c.gridy = 0; top.add(lblId, c);
        c.gridx = 1; c.gridy = 0; top.add(tfId, c);

        c.gridx = 0; c.gridy = 1; top.add(lblName, c);
        c.gridx = 1; c.gridy = 1; top.add(tfName, c);

        c.gridx = 0; c.gridy = 2; top.add(lblLocation, c);
        c.gridx = 1; c.gridy = 2; top.add(tfLocation, c);

        c.gridx = 0; c.gridy = 3; top.add(lblReading, c);
        c.gridx = 1; c.gridy = 3; top.add(tfReading, c);

        add(top, BorderLayout.NORTH);

        // Middle buttons
        Panel buttons = new Panel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        buttons.add(btnCreate);
        buttons.add(btnRead);
        buttons.add(btnUpdate);
        buttons.add(btnDelete);
        buttons.add(btnList);
        buttons.add(btnClear);
        add(buttons, BorderLayout.CENTER);

        // Output area
        taOutput.setEditable(false);
        add(taOutput, BorderLayout.SOUTH);

        // Wire actions
        btnCreate.addActionListener(this);
        btnRead.addActionListener(this);
        btnUpdate.addActionListener(this);
        btnDelete.addActionListener(this);
        btnList.addActionListener(this);
        btnClear.addActionListener(this);

        // Window listener
        addWindowListener(this);

        setSize(700, 420);
        setLocationRelativeTo(null);
        setVisible(true);

        // Attempt DB connection
        connectToDB();
        taOutput.append("Connected to DB (or attempted). Click 'List All' to see records.\n");
    }

    // Connect to database
    void connectToDB() {
        try {
            // Load driver (optional with modern JDBC but harmless)
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        } catch (ClassNotFoundException cnf) {
            taOutput.append("JDBC Driver not found: " + cnf.getMessage() + "\n");
        } catch (SQLException se) {
            taOutput.append("DB connection error: " + se.getMessage() + "\n");
        }
    }

    void closeConnection() {
        try {
            if (conn != null && !conn.isClosed()) conn.close();
        } catch (SQLException e) {
            // ignore
        }
    }

    // CRUD operations
    void createSensor() {
        String name = tfName.getText().trim();
        String location = tfLocation.getText().trim();
        String readingStr = tfReading.getText().trim();
        if (name.isEmpty() || readingStr.isEmpty()) {
            taOutput.append("Name and Reading are required for creation.\n");
            return;
        }
        double reading;
        try { reading = Double.parseDouble(readingStr); } catch (NumberFormatException nfe) { taOutput.append("Invalid reading value.\n"); return; }

        String sql = "INSERT INTO sensors (name, location, reading) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, location);
            ps.setDouble(3, reading);
            int affected = ps.executeUpdate();
            if (affected == 1) {
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) {
                    int id = keys.getInt(1);
                    taOutput.append("Created sensor id=" + id + "\n");
                    tfId.setText(String.valueOf(id));
                } else {
                    taOutput.append("Created (no id returned)\n");
                }
            } else {
                taOutput.append("Create failed (0 rows).\n");
            }
        } catch (SQLException e) {
            taOutput.append("Create error: " + e.getMessage() + "\n");
        }
    }

    void readSensor() {
        String idStr = tfId.getText().trim();
        if (idStr.isEmpty()) { taOutput.append("Enter ID to read.\n"); return; }
        int id;
        try { id = Integer.parseInt(idStr); } catch (NumberFormatException nfe) { taOutput.append("Invalid ID.\n"); return; }

        String sql = "SELECT id, name, location, reading, last_updated FROM sensors WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                tfName.setText(rs.getString("name"));
                tfLocation.setText(rs.getString("location"));
                tfReading.setText(String.valueOf(rs.getDouble("reading")));
                taOutput.append(formatResultRow(rs));
            } else {
                taOutput.append("No sensor found with id=" + id + "\n");
            }
        } catch (SQLException e) {
            taOutput.append("Read error: " + e.getMessage() + "\n");
        }
    }

    void updateSensor() {
        String idStr = tfId.getText().trim();
        if (idStr.isEmpty()) { taOutput.append("Enter ID to update.\n"); return; }
        int id;
        try { id = Integer.parseInt(idStr); } catch (NumberFormatException nfe) { taOutput.append("Invalid ID.\n"); return; }

        String name = tfName.getText().trim();
        String location = tfLocation.getText().trim();
        String readingStr = tfReading.getText().trim();
        if (name.isEmpty() || readingStr.isEmpty()) { taOutput.append("Name and Reading are required to update.\n"); return; }
        double reading;
        try { reading = Double.parseDouble(readingStr); } catch (NumberFormatException nfe) { taOutput.append("Invalid reading.\n"); return; }

        String sql = "UPDATE sensors SET name = ?, location = ?, reading = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, location);
            ps.setDouble(3, reading);
            ps.setInt(4, id);
            int affected = ps.executeUpdate();
            taOutput.append("Updated rows: " + affected + "\n");
        } catch (SQLException e) {
            taOutput.append("Update error: " + e.getMessage() + "\n");
        }
    }

    void deleteSensor() {
        String idStr = tfId.getText().trim();
        if (idStr.isEmpty()) { taOutput.append("Enter ID to delete.\n"); return; }
        int id;
        try { id = Integer.parseInt(idStr); } catch (NumberFormatException nfe) { taOutput.append("Invalid ID.\n"); return; }

        String sql = "DELETE FROM sensors WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            int affected = ps.executeUpdate();
            taOutput.append("Deleted rows: " + affected + "\n");
            if (affected > 0) clearFields();
        } catch (SQLException e) {
            taOutput.append("Delete error: " + e.getMessage() + "\n");
        }
    }

    void listSensors() {
        String sql = "SELECT id, name, location, reading, last_updated FROM sensors ORDER BY id";
        try (Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery(sql);
            taOutput.append("--- Sensors list ---\n");
            int count = 0;
            while (rs.next()) {
                taOutput.append(formatResultRow(rs));
                count++;
            }
            taOutput.append("Total: " + count + "\n");
        } catch (SQLException e) {
            taOutput.append("List error: " + e.getMessage() + "\n");
        }
    }

    String formatResultRow(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String name = rs.getString("name");
        String loc = rs.getString("location");
        double reading = rs.getDouble("reading");
        Timestamp t = rs.getTimestamp("last_updated");
        String ts = (t == null) ? "-" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(t);
        return String.format("ID=%d | name=%s | loc=%s | reading=%.3f | updated=%s\n", id, name, loc, reading, ts);
    }

    void clearFields() {
        tfId.setText("");
        tfName.setText("");
        tfLocation.setText("");
        tfReading.setText("");
    }

    // Button handler
    public void actionPerformed(ActionEvent ae) {
        Object src = ae.getSource();
        if (src == btnCreate) createSensor();
        else if (src == btnRead) readSensor();
        else if (src == btnUpdate) updateSensor();
        else if (src == btnDelete) deleteSensor();
        else if (src == btnList) listSensors();
        else if (src == btnClear) clearFields();
    }

    //--- WindowListener implementation ---
    public void windowClosing(WindowEvent we) {
        closeConnection();
        dispose();
        System.exit(0);
    }
    // Unused WindowListener methods
    public void windowOpened(WindowEvent we) {}
    public void windowClosed(WindowEvent we) {}
    public void windowIconified(WindowEvent we) {}
    public void windowDeiconified(WindowEvent we) {}
    public void windowActivated(WindowEvent we) {}
    public void windowDeactivated(WindowEvent we) {}

    public static void main(String[] args) {
        // Basic AWT look-and-feel
        new AgricultureMonitor();
    }
}
