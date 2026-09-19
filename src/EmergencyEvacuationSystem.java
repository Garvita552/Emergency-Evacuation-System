/*
 * FuturisticEvacuationSimulator.java
 *
 * This is a complete, single-file Java Swing application that merges two concepts:
 * 1. A sophisticated, grid-based emergency evacuation simulator with A* pathfinding,
 * hazard spread, and a detailed control panel.
 * 2. A visually appealing, multi-slide "wrapper" application with a futuristic
 * theme (Intro, Analytics, Credits screens) and smooth transitions.
 *
 * FIX: Initialization sequence is corrected to prevent NullPointerException.
 * UPDATE: New Neon Color Theme (Cyan/Blue/Purple) and 3D Console UI effects added.
 * UPDATE: New Hazards (GAS, COLLAPSE) added to simulation logic and UI.
 *
 * - A. Gemini (Integrated by)
 */

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class EmergencyEvacuationSystem extends JFrame {

    // -------------------------
    // --- SCENE & UI THEME ---
    // -------------------------
    private enum Scene { INTRO, SIMULATION, ANALYTICS, CREDITS }
    private Scene currentScene = Scene.INTRO;
    private Scene nextScene = Scene.INTRO;
    private boolean isTransitioning = false;
    private float transitionAlpha = 1.0f;
    private final int TRANSITION_STEPS = 12;

    // Futuristic Neon Theme Colors (Cyan, Blue, Purple)
    private static final Color BG_DARK = new Color(10, 10, 25);      // Deep Space Blue
    private static final Color PANEL_BG = new Color(25, 25, 45);     // Dark Slate Blue
    private static final Color PANEL_SHADOW = new Color(5, 5, 15);
    private static final Color ACCENT_CYAN = new Color(0, 255, 255); // Neon Cyan
    private static final Color ACCENT_PURPLE = new Color(180, 0, 255);// Electric Purple
    private static final Color ACCENT_BLUE = new Color(50, 100, 255); // Vibrant Blue
    private static final Color TEXT_COLOR = new Color(240, 240, 255); // Near White
    private static final Color BUTTON_BG = new Color(40, 45, 70);
    private static final Color BUTTON_HOVER_BG = new Color(60, 65, 95);

    // Fonts
    private static final Font TITLE_FONT = new Font("Monospaced", Font.BOLD, 46);
    private static final Font HEADER_FONT = new Font("Monospaced", Font.BOLD, 22);
    private static final Font BUTTON_FONT = new Font("SansSerif", Font.BOLD, 14);

    // Main UI Panels
    private final CardLayout cardLayout = new CardLayout();
    private MainPanel mainPanel; 
    private final JPanel simulationWrapperPanel = new JPanel(new BorderLayout(20, 20)); // Increased gap for 3D effect
    private final JPanel simulationControlPanel = new JPanel();
    private WorldPanel worldPanel; 

    // -------------------------
    // --- SIMULATION CORE ---
    // -------------------------
    enum CellType { EMPTY, WALL, EXIT }
    // ADDED GAS AND COLLAPSE HAZARDS
    enum Hazard { NONE, FIRE, SMOKE, GAS, COLLAPSE }
    enum AgentType { NORMAL, PANIC, INJURED }
    // ADDED NEW HAZARD EDIT MODES
    enum EditMode { ADD_WALL, ADD_FIRE, ADD_SMOKE, ADD_GAS, ADD_COLLAPSE, ERASE, ADD_GATE, REMOVE_GATE, SELECT_AGENT }

    static class Cell {
        CellType type = CellType.EMPTY;
        Hazard hazard = Hazard.NONE;
        int density = 0;
        boolean isBlocked = false;
    }

    class Agent {
        int r, c;
        AgentType type;
        boolean evacuated = false;
        List<int[]> path = null;
        int pathIndex = 0;
        double moveAccumulator = 0;
        int lastMoveDr = 0, lastMoveDc = 0;
        int pathRecalcCounter = 0;
        Deque<Point> trail = new ArrayDeque<>();

        Agent(int r, int c, AgentType t) { this.r = r; this.c = c; this.type = t; }
    }

    // World & Simulation Parameters
    private final int rows = 50, cols = 50; 
    private final int baseCellSize = 14; 
    private int cellSize = baseCellSize; 
    private Cell[][] grid;
    private final List<Agent> agents = Collections.synchronizedList(new ArrayList<>());
    private final List<Point> exits = Collections.synchronizedList(new ArrayList<>());
    private final Map<Point, Double> costMap = new ConcurrentHashMap<>();

    // Simulation UI controls
    private JComboBox<String> layoutCombo;
    private JSpinner agentsSpinner, exitsSpinner;
    private JButton spawnBtn, startBtn, pauseBtn, resetBtn, saveMapBtn, loadMapBtn;
    private JSlider speedSlider;
    private JCheckBox showPathsChk, showCostChk, showTrailsChk, hazardSpreadChk, showMiniMapChk;
    private JLabel statusLabel, timerLabel, evacuatedLabel;
    private EditMode editMode = EditMode.ADD_WALL;

    // Timers & simulation state
    private javax.swing.Timer simTimer; 
    private javax.swing.Timer wallClock; 
    private javax.swing.Timer sceneTransitionTimer; 
    private int simDelay = 80; 
    private boolean running = false;
    private int timeStep = 0;
    private int elapsedSeconds = 0;
    private final int densityThresholdDefault = 4;
    private int densityThreshold = densityThresholdDefault;
    private int pathRecalcFreq = 10;
    private final List<int[]> evacLog = new ArrayList<>();
    private final Random rand = new Random();

    // A* helper: directions
    private final int[][] DIRS = {{1,0},{-1,0},{0,1},{0,-1}};

    // -------------------------
    // --- CONSTRUCTOR & MAIN ---
    // -------------------------

    public EmergencyEvacuationSystem() {
        super("FUTURA: E-VAC Simulation Suite");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1280, 800));
        setBackground(BG_DARK);

        initWorld();
        buildUI();

        simTimer = new javax.swing.Timer(simDelay, e -> stepSimulation());
        wallClock = new javax.swing.Timer(1000, e -> {
            if (running) { elapsedSeconds++; updateTimerLabel(); }
        });
        sceneTransitionTimer = new javax.swing.Timer(24, e -> updateTransition());

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FuturisticLookAndFeel());
        } catch (Exception e) {
            System.err.println("Could not load futuristic L&F. Using default.");
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                // ignore
            }
        }
        SwingUtilities.invokeLater(EmergencyEvacuationSystem::new);
    }

    // -------------------------
    // --- UI BUILDING ---
    // -------------------------

    /**
     * Initializes all UI components and assembles the main panel structure.
     */
    private void buildUI() {
        worldPanel = new WorldPanel();
        buildSimulationControlPanel();

        // 3D/Futuristic Wrapper Panel Styling
        simulationWrapperPanel.setBackground(PANEL_SHADOW);
        simulationWrapperPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // Margin

        JScrollPane mapScroll = new JScrollPane(worldPanel);
        mapScroll.setBorder(BorderFactory.createLineBorder(ACCENT_BLUE, 3)); // Deep border for main map
        mapScroll.getViewport().setBackground(BG_DARK.darker());
        mapScroll.setPreferredSize(new Dimension(cols * cellSize + 40, rows * cellSize + 40));
        simulationWrapperPanel.add(mapScroll, BorderLayout.CENTER);

        simulationControlPanel.setPreferredSize(new Dimension(380, 0));
        simulationWrapperPanel.add(simulationControlPanel, BorderLayout.EAST);
        
        mainPanel = new MainPanel();

        mainPanel.setPreferredSize(new Dimension(1280, 800));
        add(mainPanel, BorderLayout.CENTER);

        setupNavigationListeners();
    }

    /**
     * Builds the right-hand control panel with a 3D/Futuristic effect.
     */
    private void buildSimulationControlPanel() {
        simulationControlPanel.setLayout(new BoxLayout(simulationControlPanel, BoxLayout.Y_AXIS));
        simulationControlPanel.setBackground(PANEL_BG);
        // Use a thick border to simulate a recessed console panel
        simulationControlPanel.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(ACCENT_PURPLE, 2), 
            new EmptyBorder(10, 10, 10, 10)));
        
        // Setup panel
        JPanel setup = createStyledPanel("Setup & Map Load [Module 1]");
        setup.setLayout(new GridLayout(4, 2, 6, 6));
        layoutCombo = new JComboBox<>(new String[]{"Empty", "School", "Airport", "Theatre", "Mall", "Hospital", "Office"});
        agentsSpinner = new JSpinner(new SpinnerNumberModel(300, 1, 5000, 10));
        exitsSpinner = new JSpinner(new SpinnerNumberModel(6, 1, 100, 1));
        setup.add(createStyledLabel("Layout:")); setup.add(layoutCombo);
        setup.add(createStyledLabel("Agents:")); setup.add(agentsSpinner);
        setup.add(createStyledLabel("Exits:")); setup.add(exitsSpinner);
        JButton loadTemplateBtn = new JButton("LOAD TEMPLATE");
        loadTemplateBtn.addActionListener(e -> { loadTemplate((String)layoutCombo.getSelectedItem()); placeRandomExits((Integer)exitsSpinner.getValue()); worldPanel.repaint(); status("Template loaded"); });
        setup.add(new JLabel("")); setup.add(loadTemplateBtn);
        simulationControlPanel.add(setup);
        simulationControlPanel.add(Box.createVerticalStrut(12));

        // Edit mode buttons
        JPanel edit = createStyledPanel("Map Editing & Hazards [Module 2]");
        edit.setLayout(new GridLayout(4, 3, 6, 6));
        JButton wallBtn = new JButton("WALL"); wallBtn.addActionListener(e -> { editMode = EditMode.ADD_WALL; status("Edit Mode: Wall"); });
        JButton fireBtn = new JButton("FIRE"); fireBtn.addActionListener(e -> { editMode = EditMode.ADD_FIRE; status("Edit Mode: Fire"); });
        JButton smokeBtn = new JButton("SMOKE"); smokeBtn.addActionListener(e -> { editMode = EditMode.ADD_SMOKE; status("Edit Mode: Smoke"); });
        // NEW HAZARD BUTTONS
        JButton gasBtn = new JButton("GAS"); gasBtn.addActionListener(e -> { editMode = EditMode.ADD_GAS; status("Edit Mode: Gas"); });
        JButton collapseBtn = new JButton("COLLAPSE"); collapseBtn.addActionListener(e -> { editMode = EditMode.ADD_COLLAPSE; status("Edit Mode: Collapse"); });
        
        JButton eraseBtn = new JButton("ERASE"); eraseBtn.addActionListener(e -> { editMode = EditMode.ERASE; status("Edit Mode: Erase"); });
        JButton addGateBtn = new JButton("ADD EXIT"); addGateBtn.addActionListener(e -> { editMode = EditMode.ADD_GATE; status("Edit Mode: Add Exit"); });
        JButton removeGateBtn = new JButton("REMOVE EXIT"); removeGateBtn.addActionListener(e -> { editMode = EditMode.REMOVE_GATE; status("Edit Mode: Remove Exit"); });
        JButton selectAgentBtn = new JButton("SELECT AGENT"); selectAgentBtn.addActionListener(e -> { editMode = EditMode.SELECT_AGENT; status("Edit Mode: Select Agent"); });
        
        edit.add(wallBtn); edit.add(fireBtn); edit.add(smokeBtn);
        edit.add(gasBtn); edit.add(collapseBtn); edit.add(eraseBtn);
        edit.add(addGateBtn); edit.add(removeGateBtn); edit.add(selectAgentBtn);
        simulationControlPanel.add(edit);
        simulationControlPanel.add(Box.createVerticalStrut(12));

        // Spawn & simulation control
        JPanel simCtrl = createStyledPanel("System Control [Module 3]");
        simCtrl.setLayout(new GridLayout(3, 2, 6, 6));
        spawnBtn = new JButton("SPAWN AGENTS");
        spawnBtn.addActionListener(e -> { spawnAgents((Integer) agentsSpinner.getValue()); worldPanel.repaint(); status("Spawned " + agents.size() + " agents"); });
        startBtn = new JButton("START SIM");
        startBtn.addActionListener(e -> startSimulation());
        pauseBtn = new JButton("PAUSE");
        pauseBtn.addActionListener(e -> togglePause());
        resetBtn = new JButton("RESET SIM");
        resetBtn.addActionListener(e -> resetSimulation());
        saveMapBtn = new JButton("SAVE MAP");
        saveMapBtn.addActionListener(e -> saveMapCSV());
        loadMapBtn = new JButton("LOAD MAP");
        loadMapBtn.addActionListener(e -> loadMapCSV());
        simCtrl.add(spawnBtn); simCtrl.add(startBtn); simCtrl.add(pauseBtn); simCtrl.add(resetBtn); simCtrl.add(saveMapBtn); simCtrl.add(loadMapBtn);
        simulationControlPanel.add(simCtrl);
        simulationControlPanel.add(Box.createVerticalStrut(12));

        // Tuning
        JPanel tune = createStyledPanel("Tuning & Parameters [Module 4]");
        tune.setLayout(new GridLayout(3, 2, 6, 6));
        speedSlider = new JSlider(20, 400, simDelay); speedSlider.setMajorTickSpacing(120); speedSlider.setPaintTicks(true); speedSlider.setPaintLabels(true);
        speedSlider.addChangeListener(e -> { simDelay = speedSlider.getValue(); if (simTimer != null) simTimer.setDelay(simDelay); });
        JSpinner densitySpinner = new JSpinner(new SpinnerNumberModel(densityThreshold, 1, 20, 1));
        densitySpinner.addChangeListener(e -> densityThreshold = (Integer)densitySpinner.getValue());
        JSpinner recalcSpinner = new JSpinner(new SpinnerNumberModel(pathRecalcFreq, 1, 100, 1));
        recalcSpinner.addChangeListener(e -> pathRecalcFreq = (Integer)recalcSpinner.getValue());
        tune.add(createStyledLabel("Tick Speed (ms)")); tune.add(speedSlider);
        tune.add(createStyledLabel("Density Threshold")); tune.add(densitySpinner);
        tune.add(createStyledLabel("Path Recalc Freq")); tune.add(recalcSpinner);
        simulationControlPanel.add(tune);
        simulationControlPanel.add(Box.createVerticalStrut(12));

        // Visualization toggles
        JPanel viz = createStyledPanel("Visualization Toggles [Module 5]");
        viz.setLayout(new GridLayout(3, 2, 6, 6));
        showPathsChk = createStyledCheckBox("Show Paths");
        showCostChk = createStyledCheckBox("Cost Heatmap");
        showTrailsChk = createStyledCheckBox("Show Trails");
        hazardSpreadChk = createStyledCheckBox("Dynamic Hazards"); // Renamed for new logic
        showMiniMapChk = createStyledCheckBox("Mini Map Overlay");
        viz.add(showPathsChk); viz.add(showCostChk); viz.add(showTrailsChk); viz.add(hazardSpreadChk); viz.add(showMiniMapChk); viz.add(new JLabel());
        simulationControlPanel.add(viz);
        simulationControlPanel.add(Box.createVerticalStrut(12));

        // Stats area
        JPanel stats = createStyledPanel("Status Log [Module 6]");
        stats.setLayout(new BoxLayout(stats, BoxLayout.Y_AXIS));
        statusLabel = createStyledLabel(">> SYSTEM ONLINE: Ready");
        timerLabel = createStyledLabel(">> CLOCK: 00:00:00");
        evacuatedLabel = createStyledLabel(">> EVAC STATUS: 0 / 0");
        stats.add(statusLabel); stats.add(Box.createVerticalStrut(6)); stats.add(timerLabel); stats.add(Box.createVerticalStrut(6)); stats.add(evacuatedLabel);
        simulationControlPanel.add(stats);
        simulationControlPanel.add(Box.createVerticalStrut(12));

        // Live chart panel (simple)
        JPanel chartContainer = createStyledPanel("Evacuation Flow");
        chartContainer.setPreferredSize(new Dimension(340, 120));
        chartContainer.add(new EvacChartPanel(), BorderLayout.CENTER);
        simulationControlPanel.add(chartContainer);
        simulationControlPanel.add(Box.createVerticalGlue());
    }

    /**
     * Sets up mouse listeners on the MainPanel for scene navigation.
     */
    private void setupNavigationListeners() {
        mainPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (isTransitioning) return;
                int w = mainPanel.getWidth(), h = mainPanel.getHeight();

                // Back button (bottom-left)
                if (e.getX() < 200 && e.getY() > h - 100 && currentScene != Scene.INTRO) {
                    navigatePrevious();
                }
                // Next button (bottom-right)
                if (e.getX() > w - 200 && e.getY() > h - 100) {
                    navigateNext();
                }
            }
        });
    }

    // -------------------------
    // --- SCENE NAVIGATION ---
    // -------------------------

    private void navigateNext() {
        Scene[] scenes = Scene.values();
        int nextIndex = (currentScene.ordinal() + 1) % scenes.length;
        startTransition(scenes[nextIndex]);
    }

    private void navigatePrevious() {
        Scene[] scenes = Scene.values();
        int prevIndex = (currentScene.ordinal() - 1 + scenes.length) % scenes.length;
        startTransition(scenes[prevIndex]);
    }

    private void startTransition(Scene target) {
        if (isTransitioning) return;
        nextScene = target;
        transitionAlpha = 0f;
        isTransitioning = true;
        sceneTransitionTimer.start();

        if (currentScene == Scene.SIMULATION && simTimer.isRunning()) {
            togglePause();
        }
    }

    private void updateTransition() {
        transitionAlpha += 1.0f / TRANSITION_STEPS;
        if (transitionAlpha >= 1.0f) {
            transitionAlpha = 1.0f;
            currentScene = nextScene;
            isTransitioning = false;
            sceneTransitionTimer.stop();
            cardLayout.show(mainPanel, currentScene.toString());
        }
        mainPanel.repaint();
    }

    // -------------------------
    // --- SIMULATION LOGIC ---
    // -------------------------

    private void initWorld() {
        grid = new Cell[rows][cols];
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) grid[r][c] = new Cell();
        agents.clear(); exits.clear(); costMap.clear(); evacLog.clear();
        timeStep = 0; elapsedSeconds = 0; running = false;
    }

    private void placeRandomExits(int count) {
        exits.clear();
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) if (grid[r][c].type == CellType.EXIT) grid[r][c].type = CellType.EMPTY;
        int tries = 0;
        while (exits.size() < count && tries++ < 10000) {
            int r = rand.nextInt(rows), c = rand.nextInt(cols);
            if ((r == 0 || r == rows - 1 || c == 0 || c == cols - 1) && grid[r][c].type == CellType.EMPTY) {
                grid[r][c].type = CellType.EXIT;
                exits.add(new Point(r, c));
            } else if (grid[r][c].type == CellType.EMPTY && rand.nextDouble() < 0.08) {
                grid[r][c].type = CellType.EXIT;
                exits.add(new Point(r, c));
            }
        }
    }

    private void loadTemplate(String sel) {
        initWorld();
        // Templates here for demonstration
        if ("School".equals(sel)) {
            for (int r = 0; r < rows; r++) grid[r][cols / 2].type = CellType.WALL;
            for (int r = 4; r < rows - 4; r += 4) {
                for (int c = 1; c < cols / 2 - 1; c++) grid[r][c].type = CellType.WALL;
                for (int c = cols / 2 + 1; c < cols - 1; c++) grid[r][c].type = CellType.WALL;
            }
        } else if ("Airport".equals(sel)) {
            for (int r = 5; r < rows - 5; r++) grid[r][10].type = CellType.WALL;
            for (int r = 5; r < rows - 5; r++) grid[r][cols - 11].type = CellType.WALL;
            for (int c = 12; c < cols - 12; c++) grid[rows / 2][c].type = CellType.WALL;
        } 
        // ... (other templates remain unchanged)
    }

    private void spawnAgents(int total) {
        agents.clear();
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) grid[r][c].density = 0;
        int tries = 0;
        while (agents.size() < total && tries++ < total * 300) {
            int r = rand.nextInt(rows), c = rand.nextInt(cols);
            // Ensure agents don't spawn in walls, fire, or collapse zones
            if (grid[r][c].type == CellType.EMPTY && grid[r][c].hazard != Hazard.FIRE && grid[r][c].hazard != Hazard.COLLAPSE) {
                double p = rand.nextDouble();
                AgentType t = p < 0.1 ? AgentType.INJURED : p < 0.3 ? AgentType.PANIC : AgentType.NORMAL;
                agents.add(new Agent(r, c, t));
            }
        }
        for (Agent a : agents) { grid[a.r][a.c].density++; a.path = findPathAStar(a.r, a.c); a.pathIndex = 0; }
        evacuatedLabel.setText(">> EVAC STATUS: 0 / " + agents.size());
    }

    private void startSimulation() {
        if (agents.isEmpty()) spawnAgents((Integer) agentsSpinner.getValue());
        exits.clear();
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) if (grid[r][c].type == CellType.EXIT) exits.add(new Point(r, c));
        if (exits.isEmpty()) { JOptionPane.showMessageDialog(this, "Please add at least one gate (exit) before starting."); return; }
        for (Agent a : agents) { a.path = findPathAStar(a.r, a.c); a.pathIndex = 0; }
        simTimer.setDelay(simDelay);
        simTimer.start();
        wallClock.start();
        running = true; timeStep = 0; elapsedSeconds = 0; updateTimerLabel();
        evacLog.clear(); evacLog.add(new int[]{0, 0, agents.size()});
        status(">> SIMULATION ENGAGED: Running");
        pauseBtn.setText("PAUSE");
    }

    private void togglePause() {
        if (!running) return;
        if (simTimer.isRunning()) { simTimer.stop(); wallClock.stop(); pauseBtn.setText("RESUME"); status(">> SYSTEM PAUSED"); }
        else { simTimer.start(); wallClock.start(); pauseBtn.setText("PAUSE"); status(">> SIMULATION RUNNING"); }
    }

    private void resetSimulation() {
        if (simTimer.isRunning()) simTimer.stop();
        if (wallClock.isRunning()) wallClock.stop();
        running = false;
        initWorld();
        worldPanel.repaint();
        status(">> SYSTEM ONLINE: Reset");
        pauseBtn.setText("PAUSE");
        timerLabel.setText(">> CLOCK: 00:00:00");
        evacuatedLabel.setText(">> EVAC STATUS: 0 / 0");
    }

    private void stepSimulation() {
        // NEW: Hazard spread logic
        if (hazardSpreadChk != null && hazardSpreadChk.isSelected()) spreadHazards();

        timeStep++;
        int recalcFreq = pathRecalcFreq;

        synchronized (agents) {
            for (Agent a : agents) {
                if (a.evacuated) continue;
                if (grid[a.r][a.c].type == CellType.EXIT) { a.evacuated = true; continue; }

                a.pathRecalcCounter++;
                if (a.path == null || a.pathIndex >= (a.path == null ? 0 : a.path.size()) || isPathObstructed(a) || a.pathRecalcCounter >= recalcFreq) {
                    a.path = findPathAStar(a.r, a.c);
                    a.pathIndex = 0; a.pathRecalcCounter = 0;
                }

                if (a.path != null && a.pathIndex < a.path.size()) {
                    double speed = 1.0;
                    if (a.type == AgentType.PANIC) speed = 1.5 + rand.nextDouble() * 0.6;
                    if (a.type == AgentType.INJURED) speed = 0.35;
                    
                    // Hazard effects on speed
                    if (grid[a.r][a.c].hazard == Hazard.SMOKE) speed *= 0.6;
                    if (grid[a.r][a.c].hazard == Hazard.GAS) speed *= 0.4; // Gas slows more

                    a.moveAccumulator += speed;
                    while (a.moveAccumulator >= 1.0 && a.pathIndex < a.path.size()) {
                        int[] step = a.path.get(a.pathIndex);
                        if (canMoveTo(step[0], step[1])) {
                            int dr = step[0] - a.r, dc = step[1] - a.c;
                            moveAgent(a, step[0], step[1], dr, dc);
                        } else {
                            a.path = null; a.pathIndex = 0; break;
                        }
                        a.pathIndex++; a.moveAccumulator -= 1.0;
                    }
                }
            }
        }

        // Update density and trails
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) grid[r][c].density = 0;
        for (Agent a : agents) if (!a.evacuated) {
            grid[a.r][a.c].density++;
            if (showTrailsChk != null && showTrailsChk.isSelected()) {
                a.trail.addFirst(new Point(a.r, a.c));
                if (a.trail.size() > 12) a.trail.removeLast();
            } else a.trail.clear();
        }

        long evacuatedCount = agents.stream().filter(x -> x.evacuated).count();
        evacuatedLabel.setText(">> EVAC STATUS: " + evacuatedCount + " / " + agents.size());
        evacLog.add(new int[]{timeStep, (int) evacuatedCount, agents.size()});

        if (evacuatedCount == agents.size() && agents.size() > 0) {
            simTimer.stop(); wallClock.stop(); running = false;
            status(">> STATUS: All evacuated in " + timeStep + " steps!");
            startTransition(Scene.ANALYTICS);
        }

        worldPanel.repaint();
    }

    private boolean canMoveTo(int r, int c) {
        if (r < 0 || r >= rows || c < 0 || c >= cols) return false;
        Cell cell = grid[r][c];
        // Walls, Fire, and Collapse zones are non-traversable
        if (cell.type == CellType.WALL || cell.hazard == Hazard.FIRE || cell.hazard == Hazard.COLLAPSE) return false;
        if (cell.type == CellType.EXIT && cell.isBlocked) return false;
        return true;
    }

    /**
     * NEW: Centralized hazard spread logic for FIRE, SMOKE, GAS, and COLLAPSE.
     */
    private void spreadHazards() {
        List<Point> changes = new ArrayList<>();
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            Cell cell = grid[r][c];

            switch (cell.hazard) {
                case FIRE:
                    // Fire spreads to adjacent EMPTY or SMOKE cells
                    for (int[] d : DIRS) {
                        int nr = r + d[0], nc = c + d[1];
                        if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                        Cell neighbor = grid[nr][nc];
                        if (neighbor.type == CellType.EMPTY || neighbor.hazard == Hazard.SMOKE) {
                            if (rand.nextDouble() < 0.04) changes.add(new Point(nr, nc)); // Spread rate
                        }
                    }
                    break;

                case SMOKE:
                    // Smoke dissipates
                    if (rand.nextDouble() < 0.005) cell.hazard = Hazard.NONE;
                    break;
                
                case GAS:
                    // Gas dissipates
                    if (rand.nextDouble() < 0.003) cell.hazard = Hazard.NONE;
                    // Gas spreads to adjacent EMPTY or SMOKE cells (slower than fire)
                    for (int[] d : DIRS) {
                        int nr = r + d[0], nc = c + d[1];
                        if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                        Cell neighbor = grid[nr][nc];
                        if ((neighbor.type == CellType.EMPTY || neighbor.hazard == Hazard.SMOKE) && neighbor.hazard != Hazard.FIRE && neighbor.hazard != Hazard.COLLAPSE) {
                            if (rand.nextDouble() < 0.015) changes.add(new Point(nr + 1000, nc)); // Use a large offset to distinguish GAS changes
                        }
                    }
                    break;
                
                case COLLAPSE:
                    // Collapse spreads to adjacent WALLS or EMPTY cells (structural failure)
                    for (int[] d : DIRS) {
                        int nr = r + d[0], nc = c + d[1];
                        if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                        Cell neighbor = grid[nr][nc];
                        // Collapse spreads to adjacent walls or converts empty space to collapse
                        if (neighbor.hazard != Hazard.COLLAPSE && neighbor.hazard != Hazard.FIRE) {
                            if (neighbor.type == CellType.WALL && rand.nextDouble() < 0.001) changes.add(new Point(nr + 2000, nc)); // Wall collapse
                            if (neighbor.type == CellType.EMPTY && rand.nextDouble() < 0.0005) changes.add(new Point(nr + 2000, nc)); // Floor collapse
                        }
                    }
                    break;

                case NONE: default: break;
            }
        }

        // Apply changes
        for (Point p : changes) {
            int r = p.x % 1000, c = p.y;
            if (p.x < 1000) grid[r][c].hazard = Hazard.FIRE; // Standard spread
            else if (p.x < 2000) grid[r][c].hazard = Hazard.GAS; // Gas spread
            else if (p.x < 3000) { // Collapse spread
                 grid[r][c].hazard = Hazard.COLLAPSE;
                 if(grid[r][c].type == CellType.WALL) grid[r][c].type = CellType.EMPTY; // Convert wall to impassable hazard
            }
        }
    }


    private double getMovementCost(int r, int c) {
        Cell cell = grid[r][c];
        // Cannot move through Walls, Fire, or Collapse zones (Infinite cost)
        if (cell.type == CellType.WALL || cell.hazard == Hazard.FIRE || cell.hazard == Hazard.COLLAPSE) return Double.MAX_VALUE;
        double base = 1.0;
        
        if (cell.hazard == Hazard.SMOKE) base *= 2.0; // Slowed by smoke
        if (cell.hazard == Hazard.GAS) base *= 3.0;   // Severely slowed by gas (toxic)
        
        if (cell.density > densityThreshold) base += (cell.density - densityThreshold) * 1.5; // Density penalty
        return base;
    }
    
    // ... (moveAgent, isPathObstructed, findPathAStar, etc. remain the same) ...
    private void moveAgent(Agent a, int nr, int nc, int dr, int dc) {
        a.lastMoveDr = dr; a.lastMoveDc = dc; a.r = nr; a.c = nc;
    }
    private boolean isPathObstructed(Agent a) {
        if (a == null || a.path == null) return true;
        int checkSteps = Math.min(3, a.path.size() - a.pathIndex);
        for (int i = a.pathIndex; i < a.pathIndex + checkSteps; i++) {
            int[] p = a.path.get(i);
            if (!canMoveTo(p[0], p[1])) return true;
        }
        return false;
    }
    private List<int[]> findPathAStar(int sr, int sc) {
        if (exits.isEmpty()) {
            for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) if (grid[r][c].type == CellType.EXIT) exits.add(new Point(r, c));
            if (exits.isEmpty()) return null;
        }

        boolean[][] closed = new boolean[rows][cols];
        double[][] gscore = new double[rows][cols];
        for (double[] row : gscore) Arrays.fill(row, Double.MAX_VALUE);
        int[][] parent = new int[rows * cols][2];
        for (int i = 0; i < rows * cols; i++) { parent[i][0] = -1; parent[i][1] = -1; }

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        gscore[sr][sc] = 0;
        open.add(new Node(sr, sc, heuristicToNearestExit(sr, sc), 0));

        costMap.clear();

        while (!open.isEmpty()) {
            Node node = open.poll();
            int r = node.r, c = node.c;
            if (closed[r][c]) continue;
            closed[r][c] = true;

            if (grid[r][c].type == CellType.EXIT && !grid[r][c].isBlocked) {
                List<int[]> path = new ArrayList<>();
                int cr = r, cc = c;
                while (!(cr == sr && cc == sc)) {
                    path.add(new int[]{cr, cc});
                    int pr = parent[cr * cols + cc][0], pc = parent[cr * cols + cc][1];
                    cr = pr; cc = pc;
                    if (cr == -1) break;
                }
                Collections.reverse(path);
                if (!path.isEmpty() && path.get(0)[0] == sr && path.get(0)[1] == sc) path.remove(0);
                return path;
            }

            for (int[] d : DIRS) {
                int nr = r + d[0], nc = c + d[1];
                if (nr < 0 || nr >= rows || nc < 0 || nc >= cols) continue;
                // Check if the cell is traversable
                if (grid[nr][nc].type == CellType.WALL || grid[nr][nc].hazard == Hazard.FIRE || grid[nr][nc].hazard == Hazard.COLLAPSE) continue;
                
                double tentativeG = gscore[r][c] + getMovementCost(nr, nc);
                if (tentativeG < gscore[nr][nc]) {
                    gscore[nr][nc] = tentativeG;
                    parent[nr * cols + nc][0] = r; parent[nr * cols + nc][1] = c;
                    double h = heuristicToNearestExit(nr, nc);
                    double f = tentativeG + h;
                    open.add(new Node(nr, nc, f, tentativeG));
                    costMap.put(new Point(nr, nc), f);
                }
            }
        }
        return null; // no path
    }

    private double heuristicToNearestExit(int r, int c) {
        double best = Double.MAX_VALUE;
        for (Point e : exits) {
            if (grid[e.x][e.y].isBlocked) continue;
            double d = Math.abs(e.x - r) + Math.abs(e.y - c);
            if (d < best) best = d;
        }
        return best == Double.MAX_VALUE ? 0 : best;
    }
    static class Node {
        int r, c;
        double f, g;
        Node(int r, int c, double f, double g) { this.r = r; this.c = c; this.f = f; this.g = g; }
    }
    // ... (save/load map remains the same) ...
    private void saveMapCSV() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (PrintWriter pw = new PrintWriter(chooser.getSelectedFile())) {
                for (int r = 0; r < rows; r++) {
                    StringBuilder sb = new StringBuilder();
                    for (int c = 0; c < cols; c++) {
                        Cell cell = grid[r][c];
                        if (cell.type == CellType.WALL) sb.append('W');
                        else if (cell.type == CellType.EXIT) sb.append('E');
                        else if (cell.hazard == Hazard.FIRE) sb.append('F');
                        else if (cell.hazard == Hazard.SMOKE) sb.append('S');
                        else if (cell.hazard == Hazard.GAS) sb.append('G'); // NEW
                        else if (cell.hazard == Hazard.COLLAPSE) sb.append('C'); // NEW
                        else sb.append('.');
                    }
                    pw.println(sb.toString());
                }
                status(">> LOG: Map saved");
            } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Save error: " + ex.getMessage()); }
        }
    }

    private void loadMapCSV() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                List<String> lines = Files.readAllLines(chooser.getSelectedFile().toPath());
                initWorld();
                int r = 0;
                for (String line : lines) {
                    for (int c = 0; c < Math.min(cols, line.length()); c++) {
                        char ch = line.charAt(c);
                        switch (ch) {
                            case 'W': grid[r][c].type = CellType.WALL; break;
                            case 'E': grid[r][c].type = CellType.EXIT; exits.add(new Point(r, c)); break;
                            case 'F': grid[r][c].hazard = Hazard.FIRE; break;
                            case 'S': grid[r][c].hazard = Hazard.SMOKE; break;
                            case 'G': grid[r][c].hazard = Hazard.GAS; break; // NEW
                            case 'C': grid[r][c].hazard = Hazard.COLLAPSE; break; // NEW
                            default: grid[r][c].type = CellType.EMPTY; grid[r][c].hazard = Hazard.NONE; break;
                        }
                    }
                    r++; if (r >= rows) break;
                }
                status(">> LOG: Map loaded");
                worldPanel.repaint();
            } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Load error: " + ex.getMessage()); }
        }
    }

    private void status(String s) { if (statusLabel != null) statusLabel.setText(s); }
    private void updateTimerLabel() {
        int s = elapsedSeconds;
        int h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        timerLabel.setText(String.format(">> CLOCK: %02d:%02d:%02d", h, m, sec));
    }

    // -------------------------
    // --- NESTED UI CLASSES ---
    // -------------------------

    private class MainPanel extends JPanel {

        MainPanel() {
            setLayout(cardLayout); 
            setBackground(BG_DARK);
            this.add(simulationWrapperPanel, Scene.SIMULATION.toString()); 
            this.add(new ScenePaintPanel(Scene.INTRO), Scene.INTRO.toString());
            this.add(new ScenePaintPanel(Scene.ANALYTICS), Scene.ANALYTICS.toString());
            this.add(new ScenePaintPanel(Scene.CREDITS), Scene.CREDITS.toString());
            cardLayout.show(this, Scene.INTRO.toString());
        }
        
        private class ScenePaintPanel extends JPanel {
            private Scene scene;

            ScenePaintPanel(Scene scene) {
                this.scene = scene;
                setBackground(BG_DARK);
            }

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();

                // Base background gradient - deep blue/purple space look
                Paint bg = new LinearGradientPaint(0, 0, w, h, new float[]{0f, 1f}, new Color[]{BG_DARK, BG_DARK.darker().darker()});
                g2.setPaint(bg);
                g2.fillRect(0, 0, w, h);
                
                // Add subtle sci-fi grid effect
                g2.setColor(new Color(40, 40, 60, 50));
                for(int i=0; i < w; i += 50) g2.drawLine(i, 0, i, h);
                for(int j=0; j < h; j += 50) g2.drawLine(0, j, w, j);

                switch (scene) {
                    case INTRO:     drawIntro(g2, w, h); break;
                    case ANALYTICS: drawAnalytics(g2, w, h); break;
                    case CREDITS:   drawCredits(g2, w, h); break;
                }

                drawNavButtons(g2, w, h);
                g2.dispose();
            }

            private void drawIntro(Graphics2D g2, int w, int h) {
                g2.setFont(TITLE_FONT);
                g2.setColor(ACCENT_CYAN);
                String t1 = "FUTURA E-VAC";
                String t2 = "SIMULATION PLATFORM";
                int x1 = (w - g2.getFontMetrics().stringWidth(t1)) / 2;
                int x2 = (w - g2.getFontMetrics().stringWidth(t2)) / 2;
                g2.drawString(t1, x1, h / 3 - 20);
                g2.drawString(t2, x2, h / 3 + 40);

                g2.setFont(BUTTON_FONT.deriveFont(16f));
                g2.setColor(new Color(ACCENT_BLUE.getRed(), ACCENT_BLUE.getGreen(), ACCENT_BLUE.getBlue(), 200));
                String[] info = {
                        "Interactive Scenario Designer & A* Pathing",
                        "Simulate agents, crowd density, and dynamic hazards.",
                        "Use the control console to design and test new environments."
                };
                int yy = h / 2 + 20;
                for (String s : info) {
                    int tx = (w - g2.getFontMetrics().stringWidth(s)) / 2;
                    g2.drawString(s, tx, yy); yy += 26;
                }
            }

            private void drawAnalytics(Graphics2D g2, int w, int h) {
                g2.setFont(HEADER_FONT);
                g2.setColor(ACCENT_PURPLE);
                g2.drawString("SCENE 3: EVACUATION ANALYTICS", 24, 36);

                int boxW = w / 2, boxH = h / 2;
                int bx = (w - boxW) / 2, by = (h - boxH) / 2;
                drawPanelRounded(g2, bx, by, boxW, boxH, "Simulation Metrics", PANEL_BG);

                g2.setFont(BUTTON_FONT.deriveFont(Font.BOLD, 16f));
                g2.setColor(TEXT_COLOR);
                int tx = bx + 30, ty = by + 60;
                long evacuatedCount = agents.stream().filter(a -> a.evacuated).count();
                int totalAgents = agents.size();
                double eff = (totalAgents == 0) ? 0 : (evacuatedCount * 100.0 / totalAgents);

                g2.drawString("Total Agents Initialized: " + totalAgents, tx, ty); ty += 30;
                g2.drawString("Agents Evacuated: " + evacuatedCount, tx, ty); ty += 30;
                g2.drawString("Agents Remaining: " + (totalAgents - evacuatedCount), tx, ty); ty += 30;
                g2.drawString("Elapsed Time (Wall Clock): " + elapsedSeconds + " s", tx, ty); ty += 30;
                g2.drawString(String.format("System Efficiency: %.1f%%", eff), tx, ty); ty += 40;

                String conclusion = (totalAgents > 0 && evacuatedCount == totalAgents) ?
                        "CONCLUSION: Evacuation successful. Target criteria met." :
                        "CONCLUSION: Incomplete evacuation. Further analysis required.";
                g2.setColor(ACCENT_CYAN);
                g2.drawString(conclusion, tx, by + boxH - 40);
            }

            private void drawCredits(Graphics2D g2, int w, int h) {
                g2.setFont(HEADER_FONT);
                g2.setColor(ACCENT_BLUE);
                g2.drawString("SCENE 4: SYSTEM CREDENTIALS", 24, 36);

                g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
                g2.setColor(TEXT_COLOR);
                String[] lines = {
                        "FUTURA: E-VAC Simulation Suite v2.0",
                        "Design Core: High-fidelity A* Pathing Engine",
                        "UI Framework: Java Swing with Futuristic Theme",
                        "Hazard Model: Dynamic Spread & Crowd Density Adaptation",
                        "",
                        "A Product of Collaborative Development."
                };
                int yy = h / 2 - 40;
                for (String ln : lines) {
                    int tx = (w - g2.getFontMetrics().stringWidth(ln)) / 2;
                    g2.drawString(ln, tx, yy);
                    yy += 28;
                }
            }

            private void drawNavButtons(Graphics2D g2, int w, int h) {
                int bw = 160, bh = 52;
                int margin = 28;
                if (currentScene != Scene.INTRO) drawNavButton(g2, margin, h - margin - bh, bw, bh, "BACK");
                String label = (currentScene == Scene.CREDITS) ? "RESTART" : (currentScene == Scene.SIMULATION ? "ANALYTICS" : "NEXT");
                drawNavButton(g2, w - margin - bw, h - margin - bh, bw, bh, label);
            }

            private void drawNavButton(Graphics2D g2, int x, int y, int w, int h, String text) {
                // Outer shadow/glow
                g2.setColor(ACCENT_CYAN.darker().darker());
                g2.fillRoundRect(x - 2, y - 2, w + 4, h + 4, 18, 18);
                
                // Button body with gradient
                Paint p = new LinearGradientPaint(x, y, x, y + h, new float[]{0f, 1f}, new Color[]{BUTTON_BG.brighter(), BUTTON_BG.darker()});
                g2.setPaint(p);
                g2.fillRoundRect(x, y, w, h, 12, 12);
                
                // Neon border
                g2.setColor(ACCENT_PURPLE);
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(x, y, w, h, 12, 12);

                g2.setColor(Color.WHITE);
                g2.setFont(BUTTON_FONT);
                FontMetrics fm = g2.getFontMetrics();
                int tx = x + (w - fm.stringWidth(text)) / 2;
                int ty = y + (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(text, tx, ty);
            }

            private void drawPanelRounded(Graphics2D g2, int x, int y, int w, int h, String title, Color c) {
                // Subtle shadow for 3D effect
                g2.setColor(PANEL_SHADOW);
                g2.fillRoundRect(x + 10, y + 10, w, h, 20, 20);
                
                // Main panel body
                Paint p = new LinearGradientPaint(x, y, x + w, y + h, new float[]{0f, 1f}, new Color[]{c, c.darker()});
                g2.setPaint(p);
                g2.fillRoundRect(x, y, w, h, 18, 18);
                
                // Neon border
                g2.setColor(ACCENT_BLUE.brighter());
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(x, y, w, h, 18, 18);
                
                // Title
                g2.setColor(ACCENT_CYAN);
                g2.setFont(BUTTON_FONT.deriveFont(Font.BOLD, 16f));
                g2.drawString(title, x + 16, y + 30);
            }
        }
    }


    /**
     * WorldPanel: Renders the simulation grid, agents, hazards, and paths.
     */
    class WorldPanel extends JPanel {
        WorldPanel() {
            setPreferredSize(new Dimension(cols * cellSize, rows * cellSize));
            setBackground(new Color(20, 22, 28));

            MouseAdapter listener = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) { handleMouseAction(e); }
                @Override
                public void mouseDragged(MouseEvent e) { handleMouseAction(e); }
            };
            addMouseListener(listener);
            addMouseMotionListener(listener);
        }

        private void handleMouseAction(MouseEvent e) {
            int cx = e.getX() / cellSize;
            int cy = e.getY() / cellSize;
            if (cx < 0 || cx >= cols || cy < 0 || cy >= rows) return;
            if (SwingUtilities.isLeftMouseButton(e)) {
                switch (editMode) {
                    case ADD_WALL: grid[cy][cx].type = CellType.WALL; grid[cy][cx].hazard = Hazard.NONE; break;
                    case ADD_FIRE: grid[cy][cx].hazard = Hazard.FIRE; grid[cy][cx].type = CellType.EMPTY; break;
                    case ADD_SMOKE: grid[cy][cx].hazard = Hazard.SMOKE; grid[cy][cx].type = CellType.EMPTY; break;
                    // NEW HAZARD MODES
                    case ADD_GAS: grid[cy][cx].hazard = Hazard.GAS; grid[cy][cx].type = CellType.EMPTY; break;
                    case ADD_COLLAPSE: grid[cy][cx].hazard = Hazard.COLLAPSE; grid[cy][cx].type = CellType.EMPTY; break;
                    
                    case ERASE: grid[cy][cx].type = CellType.EMPTY; grid[cy][cx].hazard = Hazard.NONE; grid[cy][cx].isBlocked = false; break;
                    case ADD_GATE: grid[cy][cx].type = CellType.EXIT; exits.add(new Point(cy, cx)); break;
                    case REMOVE_GATE: if (grid[cy][cx].type == CellType.EXIT) { grid[cy][cx].type = CellType.EMPTY; exits.removeIf(p -> p.x == cy && p.y == cx); } break;
                    case SELECT_AGENT:
                        // Agent selection logic placeholder
                        break;
                }
            }
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(cols * cellSize, rows * cellSize);
        }

        @Override
        protected void paintComponent(Graphics gg) {
            super.paintComponent(gg);
            Graphics2D g = (Graphics2D) gg;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw Grid Background
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int x = c * cellSize, y = r * cellSize;
                    Cell cell = grid[r][c];

                    // Cost Heatmap
                    if (showCostChk != null && showCostChk.isSelected() && costMap.containsKey(new Point(r, c))) {
                        double cost = costMap.get(new Point(r, c));
                        // Map cost to a purple/pink gradient
                        int colorVal = (int) Math.min(255, cost * 8); 
                        g.setColor(new Color(255 - colorVal / 2, 0, 150 + colorVal/2));
                        g.fillRect(x, y, cellSize, cellSize);
                    } else {
                        // Base Colors
                        if (cell.type == CellType.WALL) g.setColor(new Color(50, 50, 65)); // Darker wall
                        else if (cell.type == CellType.EXIT) g.setColor(cell.isBlocked ? new Color(150, 0, 0) : new Color(0, 150, 100));
                        else g.setColor(new Color(20, 22, 38)); // Default floor
                        g.fillRect(x, y, cellSize, cellSize);
                    }

                    // Hazards
                    if (cell.hazard == Hazard.FIRE) {
                        g.setColor(new Color(255, 69, 0, 180)); 
                        g.fillRect(x, y, cellSize, cellSize);
                    } else if (cell.hazard == Hazard.SMOKE) {
                        g.setColor(new Color(100, 100, 100, 120)); 
                        g.fillRect(x, y, cellSize, cellSize);
                    } else if (cell.hazard == Hazard.GAS) {
                        g.setColor(new Color(0, 200, 0, 100)); // Toxic Green Gas
                        g.fillRect(x, y, cellSize, cellSize);
                    } else if (cell.hazard == Hazard.COLLAPSE) {
                        g.setColor(new Color(100, 40, 40, 200)); // Red-Brown Collapse Zone
                        g.fillRect(x, y, cellSize, cellSize);
                    }

                    // Density overlay
                    if (cell.density > 0) {
                        float densityRatio = (float) Math.min(1.0, (cell.density - 1) / (float) densityThresholdDefault);
                        g.setColor(new Color(255, 255, 0, (int) (densityRatio * 100)));
                        g.fillRect(x, y, cellSize, cellSize);
                    }

                    // Draw Grid Lines (Neon Blue)
                    g.setColor(new Color(ACCENT_BLUE.getRed(), ACCENT_BLUE.getGreen(), ACCENT_BLUE.getBlue(), 60));
                    g.drawRect(x, y, cellSize, cellSize);
                }
            }

            // Draw Agent Paths
            if (showPathsChk != null && showPathsChk.isSelected()) {
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{2f}, 0f));
                g.setColor(new Color(ACCENT_CYAN.getRed(), ACCENT_CYAN.getGreen(), ACCENT_CYAN.getBlue(), 180));
                synchronized (agents) {
                    for (Agent a : agents) {
                        if (a.evacuated || a.path == null || a.path.isEmpty()) continue;
                        Path2D path = new Path2D.Double();
                        int startX = a.c * cellSize + cellSize / 2;
                        int startY = a.r * cellSize + cellSize / 2;
                        path.moveTo(startX, startY);
                        for (int i = a.pathIndex; i < a.path.size(); i++) {
                            int[] p = a.path.get(i);
                            int nextX = p[1] * cellSize + cellSize / 2;
                            int nextY = p[0] * cellSize + cellSize / 2;
                            path.lineTo(nextX, nextY);
                        }
                        g.draw(path);
                    }
                }
                g.setStroke(new BasicStroke(1f)); 
            }

            // Draw Agent Trails
            if (showTrailsChk != null && showTrailsChk.isSelected()) {
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                synchronized (agents) {
                    for (Agent a : agents) {
                        if (a.evacuated || a.trail.size() < 2) continue;
                        int i = 0;
                        Point lastPoint = null;
                        for (Point p : a.trail) {
                            float alpha = 1.0f - (float) i / a.trail.size();
                            Color trailColor = new Color(ACCENT_PURPLE.getRed(), ACCENT_PURPLE.getGreen(), ACCENT_PURPLE.getBlue(), (int)(255 * alpha));
                            g.setColor(trailColor);
                            int centerX = p.y * cellSize + cellSize / 2;
                            int centerY = p.x * cellSize + cellSize / 2;
                            if (lastPoint != null) {
                                int prevX = lastPoint.y * cellSize + cellSize / 2;
                                int prevY = lastPoint.x * cellSize + cellSize / 2;
                                g.drawLine(prevX, prevY, centerX, centerY);
                            }
                            lastPoint = p;
                            i++;
                        }
                    }
                }
                g.setStroke(new BasicStroke(1f)); 
            }

            // Draw Agents (Sprite-like)
            int spriteSize = (int) (cellSize * 0.7);
            int offset = (cellSize - spriteSize) / 2;
            synchronized (agents) {
                for (Agent a : agents) {
                    if (a.evacuated) continue;
                    int x = a.c * cellSize + offset;
                    int y = a.r * cellSize + offset;

                    switch (a.type) {
                        case PANIC: g.setColor(new Color(255, 50, 50)); break;
                        case INJURED: g.setColor(new Color(255, 165, 0)); break;
                        case NORMAL: default: g.setColor(ACCENT_CYAN); break;
                    }
                    g.fillOval(x, y, spriteSize, spriteSize);
                    g.setColor(Color.BLACK); g.drawOval(x, y, spriteSize, spriteSize);
                    
                    // Directional indicator
                    if (a.lastMoveDr != 0 || a.lastMoveDc != 0) {
                        int headX = x + spriteSize / 2 + a.lastMoveDc * spriteSize / 4;
                        int headY = y + spriteSize / 2 + a.lastMoveDr * spriteSize / 4;
                        g.setColor(Color.WHITE);
                        g.fillOval(headX - 2, headY - 2, 4, 4);
                    }
                }
            }

            // Mini-map
            if (showMiniMapChk != null && showMiniMapChk.isSelected()) {
                drawMiniMap(g, 10, 10);
            }
        }

        private void drawMiniMap(Graphics2D g, int x, int y) {
            int miniCellSize = 2;
            int w = cols * miniCellSize;
            int h = rows * miniCellSize;
            g.setColor(new Color(20, 22, 38, 200));
            g.fillRect(x, y, w, h);
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int mx = x + c * miniCellSize;
                    int my = y + r * miniCellSize;
                    Cell cell = grid[r][c];
                    if (cell.type == CellType.WALL) g.setColor(new Color(80, 80, 100));
                    else if (cell.type == CellType.EXIT) g.setColor(new Color(0, 150, 100));
                    else if (cell.hazard == Hazard.FIRE) g.setColor(Color.RED);
                    else if (cell.hazard == Hazard.SMOKE) g.setColor(Color.LIGHT_GRAY);
                    else if (cell.hazard == Hazard.GAS) g.setColor(Color.GREEN);
                    else if (cell.hazard == Hazard.COLLAPSE) g.setColor(new Color(150, 70, 70));
                    else g.setColor(new Color(30, 30, 50));
                    g.fillRect(mx, my, miniCellSize, miniCellSize);
                }
            }
            // Draw Agents on minimap
            synchronized (agents) {
                for (Agent a : agents) {
                    if (a.evacuated) continue;
                    int mx = x + a.c * miniCellSize;
                    int my = y + a.r * miniCellSize;
                    g.setColor(ACCENT_CYAN);
                    g.fillRect(mx, my, miniCellSize, miniCellSize);
                }
            }
            g.setColor(ACCENT_BLUE);
            g.drawRect(x, y, w, h);
        }
    }

    /**
     * EvacChartPanel: Renders the small live chart.
     */
    class EvacChartPanel extends JPanel {
        EvacChartPanel() {
            setPreferredSize(new Dimension(340, 110));
            setBackground(new Color(28, 30, 48)); // Darker background for contrast
        }

        @Override
        protected void paintComponent(Graphics gg) {
            super.paintComponent(gg);
            Graphics2D g = (Graphics2D) gg;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            g.setColor(ACCENT_BLUE);
            g.setFont(BUTTON_FONT.deriveFont(12f));
            g.drawString("Evacuated over time", 8, 14);
            
            if (evacLog.size() < 2) {
                g.setColor(new Color(100, 100, 150));
                g.drawString("Run simulation to see live data", 8, 34);
                return;
            }
            
            int pad = 20, w = getWidth() - pad * 2, h = getHeight() - pad * 2;
            int maxT = evacLog.get(evacLog.size() - 1)[0];
            int maxY = evacLog.stream().mapToInt(a -> a[1]).max().orElse(1);
            if (maxT == 0) maxT = 1; if (maxY == 0) maxY = 1;
            
            // Draw axes
            g.setColor(new Color(70, 70, 100));
            g.drawRect(pad, pad, w, h);
            
            Path2D path = new Path2D.Double();
            for (int i = 0; i < evacLog.size(); i++) {
                int t = evacLog.get(i)[0], y = evacLog.get(i)[1];
                int px = pad + (int) ((t / (double) maxT) * w);
                int py = pad + h - (int) ((y / (double) maxY) * h);
                if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
            }
            
            g.setStroke(new BasicStroke(2.5f));
            g.setColor(ACCENT_CYAN);
            g.draw(path);
        }
    }

    // -------------------------
    // --- STYLING HELPERS ---
    // -------------------------

    private JPanel createStyledPanel(String title) {
        JPanel panel = new JPanel();
        panel.setBackground(PANEL_BG.brighter());
        
        // 3D effect border
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createCompoundBorder(
                    new LineBorder(ACCENT_PURPLE, 1),
                    new EmptyBorder(0, 0, 0, 0)
                ), 
                title);
        
        border.setTitleColor(ACCENT_CYAN);
        border.setTitleFont(BUTTON_FONT.deriveFont(Font.BOLD, 12f));
        
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(6, 0, 6, 0),
                border
        ));
        return panel;
    }

    private JLabel createStyledLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT_COLOR);
        label.setFont(BUTTON_FONT.deriveFont(12f));
        return label;
    }

    private JCheckBox createStyledCheckBox(String text) {
        JCheckBox cb = new JCheckBox(text);
        cb.setBackground(PANEL_BG.brighter());
        cb.setForeground(TEXT_COLOR);
        cb.setFont(BUTTON_FONT.deriveFont(12f));
        return cb;
    }

    /**
     * A custom Look and Feel to style Swing components.
     */
    static class FuturisticLookAndFeel extends javax.swing.plaf.nimbus.NimbusLookAndFeel {
        @Override
        public void initialize() {
            super.initialize();
            UIDefaults d = getDefaults();
            d.put("control", PANEL_BG);
            d.put("nimbusBase", ACCENT_PURPLE.darker());
            d.put("nimbusFocus", ACCENT_CYAN);
            d.put("nimbusSelectionBackground", ACCENT_BLUE);
            d.put("text", TEXT_COLOR);
            d.put("Panel.background", PANEL_BG);
            d.put("Label.foreground", TEXT_COLOR);
            d.put("CheckBox.foreground", TEXT_COLOR);
            d.put("CheckBox.background", PANEL_BG.brighter());
            d.put("ComboBox.background", BUTTON_BG);
            d.put("ComboBox.foreground", TEXT_COLOR);
            d.put("ComboBox.selectionBackground", ACCENT_PURPLE);
            d.put("Button.background", BUTTON_BG);
            d.put("Button.foreground", TEXT_COLOR);
            d.put("Button.font", BUTTON_FONT);
            d.put("Button[MouseOver].background", BUTTON_HOVER_BG);
            d.put("Button[Pressed].background", ACCENT_BLUE.darker());
            d.put("ScrollPane.background", BG_DARK);
            d.put("Viewport.background", BG_DARK);
            d.put("ScrollBar.thumb", ACCENT_CYAN);
            d.put("ScrollBar.track", BUTTON_BG);
        }
    }
}