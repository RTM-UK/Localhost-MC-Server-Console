package com.RTM.minecraftconsole;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class MinecraftServerConsole extends JFrame {
    private static final Color BG = new Color(15, 18, 24);
    private static final Color SURFACE = new Color(24, 29, 38);
    private static final Color SURFACE_2 = new Color(31, 37, 48);
    private static final Color BORDER = new Color(50, 59, 74);
    private static final Color TEXT = new Color(235, 240, 246);
    private static final Color MUTED = new Color(148, 160, 176);
    private static final Color ACCENT = new Color(50, 184, 112);
    private static final Color BLUE = new Color(88, 166, 255);
    private static final Color AMBER = new Color(245, 177, 66);
    private static final Color DANGER = new Color(225, 82, 82);
    private static final Color CONSOLE_BG = new Color(8, 12, 17);

    private final Path configDir = Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "MC Local Server Console");
    private final Path configFile = configDir.resolve("config.properties");
    private final Properties config = new Properties();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private Path jarPath;
    private Path serverDir;
    private Process serverProcess;
    private BufferedWriter serverInput;

    private final JTextArea console = new JTextArea();
    private final JTextField commandField = new JTextField();
    private final JButton startButton = new JButton("Start");
    private final JButton stopButton = new JButton("Stop");
    private final JLabel statusLabel = new JLabel("Not configured");
    private final JLabel jarLabel = new JLabel();
    private final FileTableModel fileModel = new FileTableModel();
    private final JTable fileTable = new JTable(fileModel);
    private final PlayerTableModel playerModel = new PlayerTableModel();
    private final JTable playerTable = new JTable(playerModel);
    private final InventoryTableModel inventoryModel = new InventoryTableModel();
    private final JTable inventoryTable = new JTable(inventoryModel);
    private final JPanel inventoryPreviewPanel = new JPanel(new GridLayout(5, 9, 6, 6));
    private final JLabel inventoryStatusLabel = new JLabel("Select a player to preview inventory.");

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            installTheme();
            new MinecraftServerConsole().setVisible(true);
        });
    }

    public MinecraftServerConsole() {
        super("MC Local Server Console");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(980, 660));
        setLocationByPlatform(true);

        loadConfig();
        buildUi();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (serverProcess != null && serverProcess.isAlive()) {
                    int result = JOptionPane.showConfirmDialog(MinecraftServerConsole.this,
                            "The server is still running. Send stop and close?",
                            "Server running",
                            JOptionPane.YES_NO_CANCEL_OPTION);
                    if (result == JOptionPane.CANCEL_OPTION || result == JOptionPane.CLOSED_OPTION) {
                        return;
                    }
                    if (result == JOptionPane.YES_OPTION) {
                        stopServer();
                    }
                }
                executor.shutdownNow();
                dispose();
                System.exit(0);
            }
        });

        if (jarPath == null || !Files.exists(jarPath)) {
            SwingUtilities.invokeLater(this::showFirstRunDialog);
        } else {
            setServerJar(jarPath);
        }
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        setContentPane(root);

        JPanel topBar = new JPanel(new BorderLayout(18, 0));
        topBar.setOpaque(false);
        topBar.setBorder(new EmptyBorder(0, 0, 14, 0));

        JPanel titleBlock = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBlock.setOpaque(false);
        JLabel title = new JLabel("MC Local Server Console");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 23f));
        JLabel subtitle = new JLabel("Manage a local Minecraft server, console, plugins, and logs.");
        subtitle.setForeground(MUTED);
        subtitle.setFont(subtitle.getFont().deriveFont(13f));
        titleBlock.add(title);
        titleBlock.add(subtitle);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton chooseJarButton = styledButton("Choose Jar", SURFACE_2, TEXT);
        JButton openFolderButton = styledButton("Open Folder", SURFACE_2, TEXT);
        stylePrimaryButton(startButton);
        styleDangerButton(stopButton);
        stopButton.setEnabled(false);
        commandField.setEnabled(false);

        startButton.addActionListener(this::startServer);
        stopButton.addActionListener(e -> stopServer());
        chooseJarButton.addActionListener(e -> chooseJar());
        openFolderButton.addActionListener(e -> openPath(serverDir));

        actions.add(chooseJarButton);
        actions.add(openFolderButton);
        actions.add(startButton);
        actions.add(stopButton);

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 12f));
        statusLabel.setOpaque(true);
        statusLabel.setBorder(new EmptyBorder(7, 12, 7, 12));
        actions.add(statusLabel);
        topBar.add(titleBlock, BorderLayout.WEST);
        topBar.add(actions, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        CardLayout cardLayout = new CardLayout();
        JPanel cards = new JPanel(cardLayout);
        cards.setBackground(BG);
        cards.add(buildConsoleTab(), "console");
        cards.add(buildFilesTab(), "files");
        cards.add(buildPlayersTab(), "players");
        cards.add(buildSettingsTab(), "settings");

        JPanel body = new JPanel(new BorderLayout(14, 0));
        body.setOpaque(false);
        JPanel nav = new JPanel();
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setOpaque(false);
        nav.setPreferredSize(new Dimension(116, 120));

        JButton consoleTab = navButton("Console", ACCENT);
        JButton filesTab = navButton("Files", BLUE);
        JButton playersTab = navButton("Players", new Color(203, 142, 255));
        JButton settingsTab = navButton("Settings", AMBER);
        JButton[] navButtons = {consoleTab, filesTab, playersTab, settingsTab};

        consoleTab.addActionListener(e -> {
            cardLayout.show(cards, "console");
            selectNav(navButtons, consoleTab);
        });
        filesTab.addActionListener(e -> {
            cardLayout.show(cards, "files");
            selectNav(navButtons, filesTab);
        });
        playersTab.addActionListener(e -> {
            refreshPlayers();
            cardLayout.show(cards, "players");
            selectNav(navButtons, playersTab);
        });
        settingsTab.addActionListener(e -> {
            cardLayout.show(cards, "settings");
            selectNav(navButtons, settingsTab);
        });

        nav.add(consoleTab);
        nav.add(Box.createVerticalStrut(8));
        nav.add(filesTab);
        nav.add(Box.createVerticalStrut(8));
        nav.add(playersTab);
        nav.add(Box.createVerticalStrut(8));
        nav.add(settingsTab);
        selectNav(navButtons, consoleTab);

        body.add(nav, BorderLayout.WEST);
        body.add(cards, BorderLayout.CENTER);
        root.add(body, BorderLayout.CENTER);
    }

    private JPanel buildConsoleTab() {
        JPanel panel = contentPanel(new BorderLayout(0, 12));

        console.setEditable(false);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        console.setBackground(CONSOLE_BG);
        console.setForeground(new Color(214, 222, 232));
        console.setCaretColor(Color.WHITE);
        console.setBorder(new EmptyBorder(14, 14, 14, 14));
        console.setLineWrap(true);
        console.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(console);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
        scrollPane.getViewport().setBackground(CONSOLE_BG);
        styleScrollPane(scrollPane);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel commandBar = new JPanel(new BorderLayout(8, 0));
        commandBar.setOpaque(false);
        JButton sendButton = styledButton("Send", ACCENT, Color.WHITE);
        sendButton.addActionListener(e -> sendCommand());
        commandField.addActionListener(e -> sendCommand());
        commandField.setBackground(SURFACE_2);
        commandField.setForeground(TEXT);
        commandField.setCaretColor(TEXT);
        commandField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(9, 12, 9, 12)));
        commandBar.add(commandField, BorderLayout.CENTER);
        commandBar.add(sendButton, BorderLayout.EAST);
        panel.add(commandBar, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildFilesTab() {
        JPanel panel = contentPanel(new BorderLayout(0, 12));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.setOpaque(false);
        JButton refreshButton = styledButton("Refresh", SURFACE_2, TEXT);
        JButton openButton = styledButton("Open Selected", SURFACE_2, TEXT);
        JButton pluginsButton = styledButton("Plugins", SURFACE_2, TEXT);
        JButton logsButton = styledButton("Logs", SURFACE_2, TEXT);
        JButton createPluginsButton = styledButton("Create Plugins", ACCENT, Color.WHITE);

        refreshButton.addActionListener(e -> refreshFiles());
        openButton.addActionListener(e -> openSelectedFile());
        pluginsButton.addActionListener(e -> openPath(serverDir == null ? null : serverDir.resolve("plugins")));
        logsButton.addActionListener(e -> openPath(serverDir == null ? null : serverDir.resolve("logs")));
        createPluginsButton.addActionListener(e -> createPluginsFolder());
        JButton importButton = styledButton("Add Files", SURFACE_2, TEXT);
        JButton editButton = styledButton("Edit Text", SURFACE_2, TEXT);
        JButton deleteButton = styledButton("Delete", DANGER, Color.WHITE);
        JButton folderButton = styledButton("New Folder", SURFACE_2, TEXT);
        importButton.addActionListener(e -> importFiles());
        editButton.addActionListener(e -> editSelectedFile());
        deleteButton.addActionListener(e -> deleteSelectedFile());
        folderButton.addActionListener(e -> createFolder());

        toolbar.add(refreshButton);
        toolbar.add(openButton);
        toolbar.add(importButton);
        toolbar.add(editButton);
        toolbar.add(deleteButton);
        toolbar.add(folderButton);
        toolbar.add(pluginsButton);
        toolbar.add(logsButton);
        toolbar.add(createPluginsButton);
        panel.add(toolbar, BorderLayout.NORTH);

        fileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        fileTable.setAutoCreateRowSorter(true);
        styleTable(fileTable);
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(380);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(160);
        fileTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedFile();
                }
            }
        });
        JScrollPane scrollPane = new JScrollPane(fileTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
        scrollPane.getViewport().setBackground(SURFACE);
        styleScrollPane(scrollPane);
        fileTable.setTransferHandler(new FileDropHandler());
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildPlayersTab() {
        JPanel panel = contentPanel(new BorderLayout(12, 12));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.setOpaque(false);
        JButton refreshButton = styledButton("Refresh", SURFACE_2, TEXT);
        JButton loadButton = styledButton("Load Inventory", SURFACE_2, TEXT);
        JButton addItemButton = styledButton("Add Item", ACCENT, Color.WHITE);
        JButton removeItemButton = styledButton("Remove Item", DANGER, Color.WHITE);
        JButton saveButton = styledButton("Save Inventory", ACCENT, Color.WHITE);
        JButton kickButton = styledButton("Kick", SURFACE_2, TEXT);
        JButton opButton = styledButton("OP", SURFACE_2, TEXT);
        JButton deopButton = styledButton("De-OP", SURFACE_2, TEXT);

        refreshButton.addActionListener(e -> refreshPlayers());
        loadButton.addActionListener(e -> loadSelectedInventory(true));
        addItemButton.addActionListener(e -> showAddItemDialog());
        removeItemButton.addActionListener(e -> showRemoveItemDialog());
        saveButton.addActionListener(e -> saveSelectedInventory());
        kickButton.addActionListener(e -> sendPlayerCommand("kick"));
        opButton.addActionListener(e -> sendPlayerCommand("op"));
        deopButton.addActionListener(e -> sendPlayerCommand("deop"));

        toolbar.add(refreshButton);
        toolbar.add(loadButton);
        toolbar.add(addItemButton);
        toolbar.add(removeItemButton);
        toolbar.add(saveButton);
        toolbar.add(kickButton);
        toolbar.add(opButton);
        toolbar.add(deopButton);
        panel.add(toolbar, BorderLayout.NORTH);

        playerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleTable(playerTable);
        playerTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedInventory(false);
            }
        });
        JScrollPane playersScroll = new JScrollPane(playerTable);
        playersScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        playersScroll.getViewport().setBackground(SURFACE);
        styleScrollPane(playersScroll);

        inventoryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        styleTable(inventoryTable);
        inventoryModel.addTableModelListener(e -> updateInventoryPreview());
        JPanel inventoryPane = new JPanel(new BorderLayout(0, 10));
        inventoryPane.setOpaque(false);
        JPanel previewWrapper = new JPanel(new BorderLayout(0, 8));
        previewWrapper.setBackground(SURFACE);
        previewWrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(12, 12, 12, 12)));
        inventoryStatusLabel.setForeground(MUTED);
        inventoryStatusLabel.setFont(inventoryStatusLabel.getFont().deriveFont(Font.BOLD, 12f));
        inventoryPreviewPanel.setOpaque(false);
        previewWrapper.add(inventoryStatusLabel, BorderLayout.NORTH);
        previewWrapper.add(inventoryPreviewPanel, BorderLayout.CENTER);
        inventoryPane.add(previewWrapper, BorderLayout.NORTH);

        JScrollPane inventoryScroll = new JScrollPane(inventoryTable);
        inventoryScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        inventoryScroll.getViewport().setBackground(SURFACE);
        styleScrollPane(inventoryScroll);
        inventoryPane.add(inventoryScroll, BorderLayout.CENTER);
        updateInventoryPreview();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, playersScroll, inventoryPane);
        splitPane.setResizeWeight(0.28);
        splitPane.setBorder(null);
        splitPane.setDividerSize(8);
        splitPane.setBackground(SURFACE);
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSettingsTab() {
        JPanel panel = contentPanel(new BorderLayout());

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        form.add(label("Server jar"), gbc);

        jarLabel.setText(jarPath == null ? "No server jar selected" : jarPath.toString());
        jarLabel.setForeground(TEXT);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(jarLabel, gbc);

        JButton choose = styledButton("Change", SURFACE_2, TEXT);
        choose.addActionListener(e -> chooseJar());
        gbc.gridx = 2;
        gbc.weightx = 0;
        form.add(choose, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        form.add(label("Launch command"), gbc);

        JLabel command = new JLabel("java -jar <server.jar> nogui");
        command.setForeground(TEXT);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        form.add(command, gbc);

        panel.add(form, BorderLayout.NORTH);
        return panel;
    }

    private static void installTheme() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ignored) {
            // Keep default look and feel if the system one is not available.
        }
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));
        UIManager.put("TabbedPane.tabsOverlapBorder", true);
        UIManager.put("TabbedPane.selected", SURFACE);
        UIManager.put("TabbedPane.background", BG);
        UIManager.put("TabbedPane.foreground", TEXT);
        UIManager.put("Panel.background", BG);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageForeground", TEXT);
    }

    private static JPanel contentPanel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(16, 16, 16, 16)));
        return panel;
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        return label;
    }

    private static JButton styledButton(String text, Color background, Color foreground) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 12f));
        button.setBorder(new EmptyBorder(9, 14, 9, 14));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static JButton navButton(String text, Color foreground) {
        JButton button = styledButton(text, BG, foreground);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setBorder(new EmptyBorder(12, 14, 12, 14));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        return button;
    }

    private static void selectNav(JButton[] buttons, JButton selected) {
        for (JButton button : buttons) {
            boolean isSelected = button == selected;
            button.setBackground(isSelected ? SURFACE_2 : BG);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, isSelected ? 3 : 0, 0, 0, button.getForeground()),
                    new EmptyBorder(12, isSelected ? 11 : 14, 12, 14)));
        }
    }

    private static void stylePrimaryButton(JButton button) {
        copyButtonStyle(button, styledButton(button.getText(), ACCENT, Color.WHITE));
    }

    private static void styleDangerButton(JButton button) {
        copyButtonStyle(button, styledButton(button.getText(), DANGER, Color.WHITE));
    }

    private static void copyButtonStyle(JButton target, JButton source) {
        target.setFocusPainted(false);
        target.setBorderPainted(false);
        target.setContentAreaFilled(true);
        target.setOpaque(true);
        target.setBackground(source.getBackground());
        target.setForeground(source.getForeground());
        target.setFont(source.getFont());
        target.setBorder(source.getBorder());
        target.setCursor(source.getCursor());
    }

    private static void styleTable(JTable table) {
        table.setBackground(SURFACE);
        table.setForeground(TEXT);
        table.setGridColor(BORDER);
        table.setRowHeight(34);
        table.setShowVerticalLines(false);
        table.setSelectionBackground(new Color(42, 83, 61));
        table.setSelectionForeground(Color.WHITE);
        table.setFont(table.getFont().deriveFont(13f));
        table.getTableHeader().setBackground(SURFACE_2);
        table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD, 12f));
        table.getTableHeader().setBorder(BorderFactory.createLineBorder(BORDER));
    }

    private static void styleScrollPane(JScrollPane scrollPane) {
        scrollPane.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        scrollPane.getHorizontalScrollBar().setUI(new ModernScrollBarUI());
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(18);
        scrollPane.setCorner(JScrollPane.UPPER_RIGHT_CORNER, darkCorner());
        scrollPane.setCorner(JScrollPane.LOWER_RIGHT_CORNER, darkCorner());
        scrollPane.setCorner(JScrollPane.LOWER_LEFT_CORNER, darkCorner());
    }

    private static JComponent darkCorner() {
        JPanel panel = new JPanel();
        panel.setBackground(SURFACE);
        return panel;
    }

    private static class ModernScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(76, 88, 106);
            trackColor = SURFACE;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            g2.fillRoundRect(thumbBounds.x + 3, thumbBounds.y + 3,
                    Math.max(6, thumbBounds.width - 6),
                    Math.max(6, thumbBounds.height - 6),
                    10, 10);
            g2.dispose();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            g.setColor(trackColor);
            g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        }

        private static JButton zeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }
    }

    private void showFirstRunDialog() {
        int result = JOptionPane.showConfirmDialog(this,
                "Choose the Minecraft server .jar file to manage.",
                "First setup",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.INFORMATION_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            chooseJar();
        }
    }

    private void chooseJar() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Minecraft server jar");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Java archive (*.jar)", "jar"));
        if (jarPath != null) {
            chooser.setSelectedFile(jarPath.toFile());
        }
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            setServerJar(chooser.getSelectedFile().toPath());
            saveConfig();
        }
    }

    private void setServerJar(Path path) {
        jarPath = path.toAbsolutePath().normalize();
        serverDir = jarPath.getParent();
        jarLabel.setText(jarPath.toString());
        setStatus("Ready: " + serverDir);
        refreshFiles();
    }

    private void startServer(ActionEvent event) {
        if (jarPath == null || !Files.exists(jarPath)) {
            chooseJar();
            if (jarPath == null || !Files.exists(jarPath)) {
                return;
            }
        }
        if (serverProcess != null && serverProcess.isAlive()) {
            appendConsole("Server is already running.\n");
            return;
        }

        String javaExecutable = resolveJavaExecutable();
        ProcessBuilder builder = new ProcessBuilder(javaExecutable.toString(), "-jar", jarPath.getFileName().toString(), "nogui");
        builder.directory(serverDir.toFile());
        builder.redirectErrorStream(true);

        try {
            appendConsole("\n[" + timestamp() + "] Starting server in " + serverDir + "\n");
            serverProcess = builder.start();
            serverInput = new BufferedWriter(new OutputStreamWriter(serverProcess.getOutputStream(), StandardCharsets.UTF_8));
            setRunningUi(true);
            executor.submit(() -> readServerOutput(serverProcess));
            executor.submit(() -> {
                try {
                    int exit = serverProcess.waitFor();
                    SwingUtilities.invokeLater(() -> {
                        appendConsole("[" + timestamp() + "] Server stopped with exit code " + exit + "\n");
                        setRunningUi(false);
                        refreshFiles();
                    });
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
        } catch (IOException e) {
            appendConsole("Failed to start server: " + e.getMessage() + "\n");
            setRunningUi(false);
        }
    }

    private void readServerOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String text = line + "\n";
                SwingUtilities.invokeLater(() -> appendConsole(text));
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> appendConsole("Console reader stopped: " + e.getMessage() + "\n"));
        }
    }

    private void stopServer() {
        if (serverProcess == null || !serverProcess.isAlive()) {
            setRunningUi(false);
            return;
        }
        try {
            sendRawCommand("stop");
            appendConsole("[" + timestamp() + "] Sent stop command.\n");
        } catch (IOException e) {
            appendConsole("Could not send stop command, destroying process: " + e.getMessage() + "\n");
            serverProcess.destroy();
        }
    }

    private void sendCommand() {
        String command = commandField.getText().trim();
        if (command.isEmpty()) {
            return;
        }
        try {
            sendRawCommand(command);
            appendConsole("> " + command + "\n");
            commandField.setText("");
        } catch (IOException e) {
            appendConsole("Could not send command: " + e.getMessage() + "\n");
        }
    }

    private void sendRawCommand(String command) throws IOException {
        if (serverInput == null || serverProcess == null || !serverProcess.isAlive()) {
            throw new IOException("server is not running");
        }
        serverInput.write(command);
        serverInput.newLine();
        serverInput.flush();
    }

    private void setRunningUi(boolean running) {
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        commandField.setEnabled(running);
        setStatus(running ? "Running" : (serverDir == null ? "Not configured" : "Ready"));
    }

    private void setStatus(String status) {
        statusLabel.setText(status);
        if (status.toLowerCase().contains("running")) {
            statusLabel.setBackground(new Color(27, 96, 59));
            statusLabel.setForeground(new Color(221, 255, 235));
        } else if (status.toLowerCase().contains("not configured")) {
            statusLabel.setBackground(new Color(87, 62, 30));
            statusLabel.setForeground(new Color(255, 232, 198));
        } else {
            statusLabel.setBackground(new Color(42, 58, 78));
            statusLabel.setForeground(new Color(220, 230, 242));
        }
    }

    private void appendConsole(String text) {
        console.append(text);
        console.setCaretPosition(console.getDocument().getLength());
    }

    private void refreshFiles() {
        if (serverDir == null || !Files.isDirectory(serverDir)) {
            fileModel.setFiles(List.of());
            return;
        }
        try {
            List<FileRow> rows = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(serverDir)) {
                for (Path path : stream) {
                    rows.add(new FileRow(path));
                }
            }
            rows.sort(Comparator
                    .comparing((FileRow row) -> !row.directory)
                    .thenComparing(row -> row.name.toLowerCase()));
            fileModel.setFiles(rows);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not read server folder: " + e.getMessage(), "Files", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openSelectedFile() {
        int row = fileTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = fileTable.convertRowIndexToModel(row);
        openPath(fileModel.getPath(modelRow));
    }

    private void openPath(Path path) {
        if (path == null) {
            return;
        }
        try {
            if (!Files.exists(path)) {
                JOptionPane.showMessageDialog(this, "Path does not exist:\n" + path, "Open", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not open path: " + e.getMessage(), "Open", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createPluginsFolder() {
        if (serverDir == null) {
            return;
        }
        try {
            Files.createDirectories(serverDir.resolve("plugins"));
            refreshFiles();
            openPath(serverDir.resolve("plugins"));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not create plugins folder: " + e.getMessage(), "Plugins", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void importFiles() {
        if (serverDir == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle("Add files to server folder");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            for (File file : chooser.getSelectedFiles()) {
                copyIntoServerFolder(file.toPath());
            }
            refreshFiles();
        }
    }

    private void copyIntoServerFolder(Path source) {
        try {
            Path target = serverDir.resolve(source.getFileName()).normalize();
            if (!target.startsWith(serverDir)) {
                return;
            }
            if (Files.isDirectory(source)) {
                copyDirectory(source, target);
            } else {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not add file: " + e.getMessage(), "Files", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(path -> {
            try {
                Path destination = target.resolve(source.relativize(path).toString()).normalize();
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void editSelectedFile() {
        Path path = selectedFilePath();
        if (path == null || Files.isDirectory(path)) {
            return;
        }
        try {
            long size = Files.size(path);
            if (size > 1_000_000) {
                JOptionPane.showMessageDialog(this, "This editor is for text/config files under 1 MB.", "Edit Text", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String text = Files.readString(path, StandardCharsets.UTF_8);
            JTextArea editor = new JTextArea(text, 26, 90);
            editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            int result = JOptionPane.showConfirmDialog(this, new JScrollPane(editor),
                    "Edit " + path.getFileName(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result == JOptionPane.OK_OPTION) {
                Files.copy(path, path.resolveSibling(path.getFileName() + "." + System.currentTimeMillis() + ".bak"));
                Files.writeString(path, editor.getText(), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
                refreshFiles();
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not edit file: " + e.getMessage(), "Edit Text", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelectedFile() {
        Path path = selectedFilePath();
        if (path == null) {
            return;
        }
        int result = JOptionPane.showConfirmDialog(this,
                "Delete " + path.getFileName() + "?",
                "Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            if (Files.isDirectory(path)) {
                Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
            } else {
                Files.deleteIfExists(path);
            }
            refreshFiles();
        } catch (IOException | UncheckedIOException e) {
            JOptionPane.showMessageDialog(this, "Could not delete: " + e.getMessage(), "Delete", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createFolder() {
        if (serverDir == null) {
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Folder name", "New Folder", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            Path target = serverDir.resolve(name.trim()).normalize();
            if (!target.startsWith(serverDir)) {
                throw new IOException("Folder must stay inside the server directory.");
            }
            Files.createDirectories(target);
            refreshFiles();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not create folder: " + e.getMessage(), "New Folder", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Path selectedFilePath() {
        int row = fileTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return fileModel.getPath(fileTable.convertRowIndexToModel(row));
    }

    private void refreshPlayers() {
        if (serverDir == null) {
            playerModel.setPlayers(List.of());
            inventoryModel.setPlayer(null);
            updateInventoryPreview();
            return;
        }
        Path playerData = serverDir.resolve("world").resolve("playerdata");
        if (!Files.isDirectory(playerData)) {
            playerModel.setPlayers(List.of());
            inventoryModel.setPlayer(null);
            updateInventoryPreview();
            return;
        }
        Map<String, String> names = loadUserCacheNames();
        List<PlayerRow> rows = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(playerData, "*.dat")) {
            for (Path path : stream) {
                String uuid = path.getFileName().toString().replaceFirst("\\.dat$", "");
                rows.add(new PlayerRow(uuid, names.getOrDefault(uuid, uuid), path));
            }
            rows.sort(Comparator.comparing(row -> row.name.toLowerCase()));
            playerModel.setPlayers(rows);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not read players: " + e.getMessage(), "Players", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Map<String, String> loadUserCacheNames() {
        Map<String, String> names = new LinkedHashMap<>();
        Path cache = serverDir.resolve("usercache.json");
        if (!Files.isRegularFile(cache)) {
            return names;
        }
        try {
            String text = Files.readString(cache, StandardCharsets.UTF_8);
            String[] entries = text.split("\\{");
            for (String entry : entries) {
                String name = jsonValue(entry, "name");
                String uuid = jsonValue(entry, "uuid");
                if (name != null && uuid != null) {
                    names.put(uuid, name);
                }
            }
        } catch (IOException ignored) {
        }
        return names;
    }

    private static String jsonValue(String text, String key) {
        String marker = "\"" + key + "\"";
        int keyIndex = text.indexOf(marker);
        if (keyIndex < 0) {
            return null;
        }
        int colon = text.indexOf(':', keyIndex);
        int firstQuote = text.indexOf('"', colon + 1);
        int secondQuote = text.indexOf('"', firstQuote + 1);
        if (colon < 0 || firstQuote < 0 || secondQuote < 0) {
            return null;
        }
        return text.substring(firstQuote + 1, secondQuote);
    }

    private void loadSelectedInventory(boolean flushFirst) {
        int row = playerTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        if (flushFirst && serverProcess != null && serverProcess.isAlive()) {
            try {
                sendRawCommand("save-all flush");
                appendConsole("> save-all flush\n");
                Timer timer = new Timer(1200, e -> loadSelectedInventory(false));
                timer.setRepeats(false);
                timer.start();
                inventoryStatusLabel.setText("Saving player data, then loading inventory...");
                return;
            } catch (IOException e) {
                appendConsole("Could not flush player data before loading inventory: " + e.getMessage() + "\n");
            }
        }
        PlayerRow player = playerModel.getPlayer(playerTable.convertRowIndexToModel(row));
        try {
            NbtCompound root = NbtIo.read(player.path);
            inventoryModel.setPlayer(new PlayerInventory(player, root, itemsFromInventory(root)));
            updateInventoryPreview();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not load inventory: " + e.getMessage(), "Players", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateInventoryPreview() {
        inventoryPreviewPanel.removeAll();
        PlayerInventory inventory = inventoryModel.inventory;
        if (inventory == null) {
            inventoryStatusLabel.setText("Select a player to preview inventory.");
        } else {
            inventoryStatusLabel.setText(inventory.player.name + " inventory preview");
        }

        int[] slots = {
                9, 10, 11, 12, 13, 14, 15, 16, 17,
                18, 19, 20, 21, 22, 23, 24, 25, 26,
                27, 28, 29, 30, 31, 32, 33, 34, 35,
                0, 1, 2, 3, 4, 5, 6, 7, 8,
                100, 101, 102, 103, -106, -1, -1, -1, -1
        };
        for (int slot : slots) {
            JLabel cell = new JLabel(slot < 0 ? "" : previewText(slot), SwingConstants.CENTER);
            cell.setOpaque(true);
            cell.setBackground(slot < 0 ? SURFACE : new Color(34, 41, 53));
            cell.setForeground(TEXT);
            cell.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            cell.setBorder(BorderFactory.createLineBorder(slot < 0 ? SURFACE : BORDER));
            cell.setPreferredSize(new Dimension(86, 42));
            inventoryPreviewPanel.add(cell);
        }
        inventoryPreviewPanel.revalidate();
        inventoryPreviewPanel.repaint();
    }

    private String previewText(int slot) {
        PlayerInventory inventory = inventoryModel.inventory;
        if (inventory == null) {
            return slotLabel(slot);
        }
        for (InventoryItem item : inventory.items) {
            if (item.slot == slot) {
                return shortItemName(item.id) + " x" + item.count;
            }
        }
        return slotLabel(slot);
    }

    private static String slotLabel(int slot) {
        if (slot >= 0 && slot <= 8) {
            return "Hotbar " + (slot + 1);
        }
        if (slot >= 9 && slot <= 35) {
            return "Slot " + slot;
        }
        return switch (slot) {
            case 100 -> "Boots";
            case 101 -> "Legs";
            case 102 -> "Chest";
            case 103 -> "Head";
            case -106 -> "Offhand";
            default -> "Slot " + slot;
        };
    }

    private static String shortItemName(String id) {
        String name = id == null ? "" : id.replace("minecraft:", "");
        return name.length() <= 13 ? name : name.substring(0, 12) + "...";
    }

    private List<InventoryItem> itemsFromInventory(NbtCompound root) {
        NbtList list = root.list("Inventory");
        List<InventoryItem> items = new ArrayList<>();
        if (list == null) {
            return items;
        }
        for (NbtTag tag : list.values) {
            if (tag instanceof NbtCompound compound) {
                int count = compound.has("count") ? compound.intValue("count") : compound.numberValue("Count");
                items.add(new InventoryItem(compound.string("id"), Math.max(1, count), compound.numberValue("Slot"), compound));
            }
        }
        inventoryStatusLabel.setText(items.size() + " inventory item(s) loaded.");
        return items;
    }

    private void saveSelectedInventory() {
        writeInventoryToDisk(true);
    }

    private boolean writeInventoryToDisk(boolean showDialog) {
        PlayerInventory inventory = inventoryModel.inventory;
        if (inventory == null) {
            return false;
        }
        try {
            NbtList list = new NbtList((byte) 10);
            for (InventoryItem item : inventory.items) {
                if (item.id == null || item.id.isBlank()) {
                    continue;
                }
                NbtCompound compound = item.tag == null ? new NbtCompound() : item.tag;
                compound.put("id", new NbtString(item.id.trim()));
                if (compound.has("count") || !compound.has("Count")) {
                    compound.put("count", new NbtInt(Math.max(1, item.count)));
                    compound.remove("Count");
                } else {
                    compound.put("Count", new NbtByte((byte) Math.max(1, Math.min(127, item.count))));
                }
                compound.put("Slot", new NbtByte((byte) Math.max(-128, Math.min(127, item.slot))));
                list.values.add(compound);
            }
            inventory.root.put("Inventory", list);
            Path backup = inventory.player.path.resolveSibling(inventory.player.path.getFileName() + "." + System.currentTimeMillis() + ".bak");
            Files.copy(inventory.player.path, backup);
            NbtIo.write(inventory.player.path, inventory.root);
            inventoryStatusLabel.setText("Saved inventory. Backup: " + backup.getFileName());
            if (showDialog) {
                JOptionPane.showMessageDialog(this, "Saved inventory. Backup created:\n" + backup.getFileName(), "Players", JOptionPane.INFORMATION_MESSAGE);
            }
            return true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save inventory: " + e.getMessage(), "Players", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void showAddItemDialog() {
        PlayerInventory inventory = inventoryModel.inventory;
        if (inventory == null) {
            JOptionPane.showMessageDialog(this, "Load a player inventory first.", "Add Item", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JComboBox<String> itemBox = new JComboBox<>(COMMON_ITEMS);
        itemBox.setEditable(true);
        JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 127, 1));
        JComboBox<SlotChoice> slotBox = new JComboBox<>(slotChoices());
        slotBox.setSelectedItem(firstEmptySlotChoice(inventory));

        JPanel form = dialogForm();
        addDialogRow(form, "Item ID", itemBox);
        addDialogRow(form, "Count", countSpinner);
        addDialogRow(form, "Slot", slotBox);

        int result = JOptionPane.showConfirmDialog(this, form, "Add Item", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        String id = String.valueOf(itemBox.getEditor().getItem()).trim();
        if (id.isBlank()) {
            return;
        }
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        SlotChoice slot = (SlotChoice) slotBox.getSelectedItem();
        inventory.items.removeIf(item -> item.slot == slot.slot);
        InventoryItem newItem = new InventoryItem(id, (Integer) countSpinner.getValue(), slot.slot, new NbtCompound());
        inventory.items.add(newItem);
        inventoryModel.fireTableDataChanged();
        updateInventoryPreview();
        if (isServerRunning()) {
            applyLiveItemAdd(inventory.player, newItem);
        } else {
            writeInventoryToDisk(false);
        }
    }

    private void showRemoveItemDialog() {
        PlayerInventory inventory = inventoryModel.inventory;
        if (inventory == null) {
            JOptionPane.showMessageDialog(this, "Load a player inventory first.", "Remove Item", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (inventory.items.isEmpty()) {
            JOptionPane.showMessageDialog(this, "This inventory has no items to remove.", "Remove Item", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        InventoryItem[] items = inventory.items.toArray(new InventoryItem[0]);
        JComboBox<InventoryItem> itemBox = new JComboBox<>(items);
        itemBox.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value == null ? "" : slotLabel(value.slot) + " - " + value.id + " x" + value.count);
            label.setOpaque(true);
            label.setBorder(new EmptyBorder(6, 8, 6, 8));
            label.setBackground(isSelected ? new Color(42, 83, 61) : SURFACE);
            label.setForeground(TEXT);
            return label;
        });
        JPanel form = dialogForm();
        addDialogRow(form, "Remove", itemBox);
        int result = JOptionPane.showConfirmDialog(this, form, "Remove Item", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            InventoryItem item = (InventoryItem) itemBox.getSelectedItem();
            inventory.items.remove(item);
            inventoryModel.fireTableDataChanged();
            updateInventoryPreview();
            if (isServerRunning()) {
                applyLiveItemRemove(inventory.player, item);
            } else {
                writeInventoryToDisk(false);
            }
        }
    }

    private boolean isServerRunning() {
        return serverProcess != null && serverProcess.isAlive();
    }

    private void applyLiveItemAdd(PlayerRow player, InventoryItem item) {
        String command = "item replace entity " + player.name + " " + commandSlot(item.slot)
                + " with " + item.id + " " + Math.max(1, item.count);
        try {
            sendRawCommand(command);
            appendConsole("> " + command + "\n");
            inventoryStatusLabel.setText("Sent live add command. Reloading inventory...");
            reloadInventorySoon();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not send add command: " + e.getMessage(), "Add Item", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyLiveItemRemove(PlayerRow player, InventoryItem item) {
        String command = "clear " + player.name + " " + item.id + " " + Math.max(1, item.count);
        try {
            sendRawCommand(command);
            appendConsole("> " + command + "\n");
            inventoryStatusLabel.setText("Sent live remove command. Reloading inventory...");
            reloadInventorySoon();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not send remove command: " + e.getMessage(), "Remove Item", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void reloadInventorySoon() {
        Timer timer = new Timer(1000, e -> loadSelectedInventory(true));
        timer.setRepeats(false);
        timer.start();
    }

    private static String commandSlot(int slot) {
        if (slot >= 0 && slot <= 8) {
            return "hotbar." + slot;
        }
        if (slot >= 9 && slot <= 35) {
            return "inventory." + (slot - 9);
        }
        return switch (slot) {
            case 100 -> "armor.feet";
            case 101 -> "armor.legs";
            case 102 -> "armor.chest";
            case 103 -> "armor.head";
            case -106 -> "weapon.offhand";
            default -> "hotbar.0";
        };
    }

    private static JPanel dialogForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(8, 8, 8, 8));
        return form;
    }

    private static void addDialogRow(JPanel form, String labelText, JComponent field) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = form.getComponentCount() / 2;
        form.add(new JLabel(labelText), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        field.setPreferredSize(new Dimension(280, 30));
        form.add(field, gbc);
    }

    private static SlotChoice[] slotChoices() {
        List<SlotChoice> choices = new ArrayList<>();
        for (int i = 0; i <= 8; i++) choices.add(new SlotChoice(i, "Hotbar " + (i + 1)));
        for (int i = 9; i <= 35; i++) choices.add(new SlotChoice(i, "Inventory slot " + i));
        choices.add(new SlotChoice(100, "Boots"));
        choices.add(new SlotChoice(101, "Leggings"));
        choices.add(new SlotChoice(102, "Chestplate"));
        choices.add(new SlotChoice(103, "Helmet"));
        choices.add(new SlotChoice(-106, "Offhand"));
        return choices.toArray(new SlotChoice[0]);
    }

    private static SlotChoice firstEmptySlotChoice(PlayerInventory inventory) {
        for (SlotChoice choice : slotChoices()) {
            boolean used = false;
            for (InventoryItem item : inventory.items) {
                if (item.slot == choice.slot) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return choice;
            }
        }
        return slotChoices()[0];
    }

    private static final String[] COMMON_ITEMS = {
            "minecraft:diamond_sword",
            "minecraft:diamond_pickaxe",
            "minecraft:diamond_axe",
            "minecraft:diamond_shovel",
            "minecraft:bow",
            "minecraft:arrow",
            "minecraft:shield",
            "minecraft:elytra",
            "minecraft:golden_apple",
            "minecraft:enchanted_golden_apple",
            "minecraft:totem_of_undying",
            "minecraft:bread",
            "minecraft:cooked_beef",
            "minecraft:torch",
            "minecraft:oak_planks",
            "minecraft:stone",
            "minecraft:dirt",
            "minecraft:water_bucket",
            "minecraft:lava_bucket",
            "minecraft:ender_pearl"
    };

    private record SlotChoice(int slot, String label) {
        @Override
        public String toString() {
            return label + " (" + slot + ")";
        }
    }

    private void sendPlayerCommand(String command) {
        int row = playerTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        PlayerRow player = playerModel.getPlayer(playerTable.convertRowIndexToModel(row));
        try {
            sendRawCommand(command + " " + player.name);
            appendConsole("> " + command + " " + player.name + "\n");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Server must be running to send player commands.", "Players", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void loadConfig() {
        try {
            if (Files.exists(configFile)) {
                try (InputStream input = Files.newInputStream(configFile)) {
                    config.load(input);
                }
                String jar = config.getProperty("serverJar");
                if (jar != null && !jar.isBlank()) {
                    jarPath = Path.of(jar);
                    serverDir = jarPath.getParent();
                }
            }
        } catch (IOException ignored) {
            jarPath = null;
            serverDir = null;
        }
    }

    private void saveConfig() {
        try {
            Files.createDirectories(configDir);
            if (jarPath != null) {
                config.setProperty("serverJar", jarPath.toString());
            }
            try (OutputStream output = Files.newOutputStream(configFile)) {
                config.store(output, "MC Local Server Console settings");
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not save settings: " + e.getMessage(), "Settings", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }

    private static String resolveJavaExecutable() {
        Path bundledJava = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java");
        if (Files.isRegularFile(bundledJava)) {
            return bundledJava.toString();
        }
        return isWindows() ? "java.exe" : "java";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static class FileRow {
        final Path path;
        final String name;
        final boolean directory;
        final long size;
        final long modified;

        FileRow(Path path) throws IOException {
            this.path = path;
            this.name = path.getFileName().toString();
            this.directory = Files.isDirectory(path);
            this.size = directory ? -1 : Files.size(path);
            this.modified = Files.getLastModifiedTime(path).toMillis();
        }
    }

    private static class FileTableModel extends AbstractTableModel {
        private final String[] columns = {"Name", "Type", "Modified", "Size"};
        private List<FileRow> files = List.of();

        void setFiles(List<FileRow> files) {
            this.files = files;
            fireTableDataChanged();
        }

        Path getPath(int row) {
            return files.get(row).path;
        }

        @Override
        public int getRowCount() {
            return files.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            FileRow row = files.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.name;
                case 1 -> row.directory ? "Folder" : "File";
                case 2 -> new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(row.modified));
                case 3 -> row.directory ? "" : humanSize(row.size);
                default -> "";
            };
        }

        private static String humanSize(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            }
            double value = bytes;
            String[] units = {"KB", "MB", "GB"};
            int unit = -1;
            while (value >= 1024 && unit < units.length - 1) {
                value /= 1024;
                unit++;
            }
            return String.format("%.1f %s", value, units[unit]);
        }
    }

    private class FileDropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return serverDir != null && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                for (File file : files) {
                    copyIntoServerFolder(file.toPath());
                }
                refreshFiles();
                return true;
            } catch (Exception e) {
                JOptionPane.showMessageDialog(MinecraftServerConsole.this, "Could not import dropped files: " + e.getMessage(), "Files", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
    }

    private record PlayerRow(String uuid, String name, Path path) {
    }

    private static class PlayerTableModel extends AbstractTableModel {
        private final String[] columns = {"Player", "UUID"};
        private List<PlayerRow> players = List.of();

        void setPlayers(List<PlayerRow> players) {
            this.players = players;
            fireTableDataChanged();
        }

        PlayerRow getPlayer(int row) {
            return players.get(row);
        }

        @Override
        public int getRowCount() {
            return players.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PlayerRow row = players.get(rowIndex);
            return columnIndex == 0 ? row.name : row.uuid;
        }
    }

    private static class PlayerInventory {
        final PlayerRow player;
        final NbtCompound root;
        final List<InventoryItem> items;

        PlayerInventory(PlayerRow player, NbtCompound root, List<InventoryItem> items) {
            this.player = player;
            this.root = root;
            this.items = items;
        }
    }

    private static class InventoryItem {
        String id;
        int count;
        int slot;
        NbtCompound tag;

        InventoryItem(String id, int count, int slot, NbtCompound tag) {
            this.id = id == null || id.isBlank() ? "minecraft:stone" : id;
            this.count = count;
            this.slot = slot;
            this.tag = tag;
        }
    }

    private static class InventoryTableModel extends AbstractTableModel {
        private final String[] columns = {"Item ID", "Count", "Slot"};
        private PlayerInventory inventory;

        void setPlayer(PlayerInventory inventory) {
            this.inventory = inventory;
            fireTableDataChanged();
        }

        void addBlankItem() {
            if (inventory == null) {
                return;
            }
            inventory.items.add(new InventoryItem("minecraft:stone", 1, 0, new NbtCompound()));
            fireTableRowsInserted(inventory.items.size() - 1, inventory.items.size() - 1);
        }

        void remove(int viewRow) {
            if (inventory == null || viewRow < 0 || viewRow >= inventory.items.size()) {
                return;
            }
            inventory.items.remove(viewRow);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return inventory == null ? 0 : inventory.items.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            InventoryItem item = inventory.items.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> item.id;
                case 1 -> item.count;
                case 2 -> item.slot;
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            InventoryItem item = inventory.items.get(rowIndex);
            String text = String.valueOf(value).trim();
            try {
                if (columnIndex == 0) {
                    item.id = text;
                } else if (columnIndex == 1) {
                    item.count = Integer.parseInt(text);
                } else if (columnIndex == 2) {
                    item.slot = Integer.parseInt(text);
                }
                fireTableCellUpdated(rowIndex, columnIndex);
            } catch (NumberFormatException ignored) {
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? String.class : Integer.class;
        }
    }

    private sealed interface NbtTag permits NbtEnd, NbtByte, NbtShort, NbtInt, NbtLong, NbtFloat, NbtDouble, NbtByteArray, NbtString, NbtList, NbtCompound, NbtIntArray, NbtLongArray {
        byte id();
        void write(DataOutputStream output) throws IOException;
    }

    private record NbtEnd() implements NbtTag {
        public byte id() { return 0; }
        public void write(DataOutputStream output) {}
    }

    private record NbtByte(byte value) implements NbtTag {
        public byte id() { return 1; }
        public void write(DataOutputStream output) throws IOException { output.writeByte(value); }
    }

    private record NbtShort(short value) implements NbtTag {
        public byte id() { return 2; }
        public void write(DataOutputStream output) throws IOException { output.writeShort(value); }
    }

    private record NbtInt(int value) implements NbtTag {
        public byte id() { return 3; }
        public void write(DataOutputStream output) throws IOException { output.writeInt(value); }
    }

    private record NbtLong(long value) implements NbtTag {
        public byte id() { return 4; }
        public void write(DataOutputStream output) throws IOException { output.writeLong(value); }
    }

    private record NbtFloat(float value) implements NbtTag {
        public byte id() { return 5; }
        public void write(DataOutputStream output) throws IOException { output.writeFloat(value); }
    }

    private record NbtDouble(double value) implements NbtTag {
        public byte id() { return 6; }
        public void write(DataOutputStream output) throws IOException { output.writeDouble(value); }
    }

    private record NbtByteArray(byte[] value) implements NbtTag {
        public byte id() { return 7; }
        public void write(DataOutputStream output) throws IOException { output.writeInt(value.length); output.write(value); }
    }

    private record NbtString(String value) implements NbtTag {
        public byte id() { return 8; }
        public void write(DataOutputStream output) throws IOException { output.writeUTF(value); }
    }

    private static final class NbtList implements NbtTag {
        byte elementType;
        final List<NbtTag> values = new ArrayList<>();
        NbtList(byte elementType) { this.elementType = elementType; }
        public byte id() { return 9; }
        public void write(DataOutputStream output) throws IOException {
            output.writeByte(values.isEmpty() ? elementType : values.get(0).id());
            output.writeInt(values.size());
            for (NbtTag tag : values) {
                tag.write(output);
            }
        }
    }

    private static final class NbtCompound implements NbtTag {
        final Map<String, NbtTag> values = new LinkedHashMap<>();
        public byte id() { return 10; }
        void put(String name, NbtTag tag) { values.put(name, tag); }
        void remove(String name) { values.remove(name); }
        boolean has(String name) { return values.containsKey(name); }
        NbtList list(String name) { return values.get(name) instanceof NbtList list ? list : null; }
        String string(String name) { return values.get(name) instanceof NbtString s ? s.value : ""; }
        byte byteValue(String name) { return values.get(name) instanceof NbtByte b ? b.value : 0; }
        int intValue(String name) { return values.get(name) instanceof NbtInt i ? i.value : 0; }
        int numberValue(String name) {
            NbtTag tag = values.get(name);
            if (tag instanceof NbtByte b) return b.value;
            if (tag instanceof NbtShort s) return s.value;
            if (tag instanceof NbtInt i) return i.value;
            return 0;
        }
        public void write(DataOutputStream output) throws IOException {
            for (Map.Entry<String, NbtTag> entry : values.entrySet()) {
                output.writeByte(entry.getValue().id());
                output.writeUTF(entry.getKey());
                entry.getValue().write(output);
            }
            output.writeByte(0);
        }
    }

    private record NbtIntArray(int[] value) implements NbtTag {
        public byte id() { return 11; }
        public void write(DataOutputStream output) throws IOException {
            output.writeInt(value.length);
            for (int i : value) output.writeInt(i);
        }
    }

    private record NbtLongArray(long[] value) implements NbtTag {
        public byte id() { return 12; }
        public void write(DataOutputStream output) throws IOException {
            output.writeInt(value.length);
            for (long l : value) output.writeLong(l);
        }
    }

    private static class NbtIo {
        static NbtCompound read(Path path) throws IOException {
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(path))))) {
                byte type = input.readByte();
                if (type != 10) {
                    throw new IOException("Root tag is not a compound.");
                }
                input.readUTF();
                return (NbtCompound) readPayload(input, type);
            }
        }

        static void write(Path path, NbtCompound root) throws IOException {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(path))))) {
                output.writeByte(10);
                output.writeUTF("");
                root.write(output);
            }
        }

        private static NbtTag readPayload(DataInputStream input, byte type) throws IOException {
            return switch (type) {
                case 0 -> new NbtEnd();
                case 1 -> new NbtByte(input.readByte());
                case 2 -> new NbtShort(input.readShort());
                case 3 -> new NbtInt(input.readInt());
                case 4 -> new NbtLong(input.readLong());
                case 5 -> new NbtFloat(input.readFloat());
                case 6 -> new NbtDouble(input.readDouble());
                case 7 -> {
                    byte[] data = new byte[input.readInt()];
                    input.readFully(data);
                    yield new NbtByteArray(data);
                }
                case 8 -> new NbtString(input.readUTF());
                case 9 -> {
                    byte elementType = input.readByte();
                    int length = input.readInt();
                    NbtList list = new NbtList(elementType);
                    for (int i = 0; i < length; i++) {
                        list.values.add(readPayload(input, elementType));
                    }
                    yield list;
                }
                case 10 -> {
                    NbtCompound compound = new NbtCompound();
                    while (true) {
                        byte childType = input.readByte();
                        if (childType == 0) {
                            break;
                        }
                        String name = input.readUTF();
                        compound.put(name, readPayload(input, childType));
                    }
                    yield compound;
                }
                case 11 -> {
                    int[] data = new int[input.readInt()];
                    for (int i = 0; i < data.length; i++) data[i] = input.readInt();
                    yield new NbtIntArray(data);
                }
                case 12 -> {
                    long[] data = new long[input.readInt()];
                    for (int i = 0; i < data.length; i++) data[i] = input.readLong();
                    yield new NbtLongArray(data);
                }
                default -> throw new IOException("Unsupported NBT tag type " + type);
            };
        }
    }
}
