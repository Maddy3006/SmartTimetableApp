import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * Smart Time Table Conflict Checker & Generator
 * Single-file Java Swing application.
 *
 * Features:
 * - Add faculty with name, subject, weekly hours, room
 * - Click timetable slots to assign (must equal weekly hours)
 * - Conflict detection (same room same slot)
 * - Auto-generate remaining slots for faculty (round-robin)
 * - Save/Load using Java serialization
 *
 * Author: ChatGPT (adapted for Maddy)
 */
public class SmartTimetableApp extends JFrame {

    // Grid size
    private static final String[] DAYS = {"Mon", "Tue", "Wed", "Thu", "Fri"};
    private static final int HOURS_PER_DAY = 8;

    // UI components
    private JPanel gridPanel;
    private Map<String, JButton> slotButtons = new HashMap<>();
    private JTextArea statusArea;

    // Faculty input fields
    private JTextField nameField, subjectField, hoursField, roomField;
    private JButton startSelectButton, saveFacultyButton, autoGenButton, checkConfButton, resetButton, saveFileButton, loadFileButton;

    // Data structures
    private List<Faculty> facultyList = new ArrayList<>();
    private Timetable masterTimetable = new Timetable();

    // Temporary assignment state when user is selecting slots for a new faculty
    private Faculty selectingFaculty = null;
    private Set<String> selectingSlots = new HashSet<>();

    public SmartTimetableApp() {
        super("Smart Time Table - Conflict Checker & Generator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);
        initUI();
    }

    private void initUI() {
        // Top control panel
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Left: Form inputs
        JPanel form = new JPanel();
        form.setLayout(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Add Faculty"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        nameField = new JTextField(12);
        subjectField = new JTextField(12);
        hoursField = new JTextField(4);
        roomField = new JTextField(6);

        gbc.gridx = 0; gbc.gridy = 0; form.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1; form.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; form.add(new JLabel("Subject:"), gbc);
        gbc.gridx = 1; form.add(subjectField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; form.add(new JLabel("Hours / week:"), gbc);
        gbc.gridx = 1; form.add(hoursField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; form.add(new JLabel("Room no.:"), gbc);
        gbc.gridx = 1; form.add(roomField, gbc);

        startSelectButton = new JButton("Start Slot Selection");
        saveFacultyButton = new JButton("Save Faculty");
        saveFacultyButton.setEnabled(false);
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        form.add(startSelectButton, gbc);

        gbc.gridy = 5;
        form.add(saveFacultyButton, gbc);

        topPanel.add(form, BorderLayout.WEST);

        // Center: Buttons for actions
        JPanel actions = new JPanel();
        actions.setBorder(BorderFactory.createTitledBorder("Actions"));
        autoGenButton = new JButton("Auto-Generate Timetable");
        checkConfButton = new JButton("Check Conflicts");
        resetButton = new JButton("Reset Timetable");
        saveFileButton = new JButton("Save to file");
        loadFileButton = new JButton("Load from file");

        actions.add(autoGenButton);
        actions.add(checkConfButton);
        actions.add(resetButton);
        actions.add(saveFileButton);
        actions.add(loadFileButton);

        topPanel.add(actions, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);

        // Center: grid
        gridPanel = new JPanel(new GridBagLayout());
        gridPanel.setBorder(BorderFactory.createTitledBorder("Timetable (Click to select slots when assigning)"));
        buildGrid();
        add(new JScrollPane(gridPanel), BorderLayout.CENTER);

        // Bottom: status and faculty list
        JPanel bottom = new JPanel(new BorderLayout());
        statusArea = new JTextArea(6, 40);
        statusArea.setEditable(false);
        statusArea.setLineWrap(true);
        bottom.add(new JScrollPane(statusArea), BorderLayout.CENTER);

        add(bottom, BorderLayout.SOUTH);

        // Hook actions
        startSelectButton.addActionListener(e -> enterSelectionMode());
        saveFacultyButton.addActionListener(e -> saveFacultyFromSelection());
        autoGenButton.addActionListener(e -> {
            autoGenerate();
            refreshGridDisplay();
        });
        checkConfButton.addActionListener(e -> {
            checkConflictsShow();
        });
        resetButton.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Reset entire timetable and faculty list?") == JOptionPane.YES_OPTION) {
                facultyList.clear();
                masterTimetable = new Timetable();
                selectingFaculty = null;
                selectingSlots.clear();
                refreshGridDisplay();
                appendStatus("Reset done.");
            }
        });
        saveFileButton.addActionListener(e -> saveToFile());
        loadFileButton.addActionListener(e -> {
            loadFromFile();
            refreshGridDisplay();
        });

        refreshGridDisplay();
    }

    private void buildGrid() {
        slotButtons.clear();
        gridPanel.removeAll();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;

        // top left corner blank
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.1; gbc.weighty = 0.1;
        gridPanel.add(new JLabel(""), gbc);

        // day headers
        for (int d = 0; d < DAYS.length; d++) {
            gbc.gridx = d + 1;
            JLabel dayLabel = new JLabel(DAYS[d], SwingConstants.CENTER);
            dayLabel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
            gridPanel.add(dayLabel, gbc);
        }

        // rows: hours
        for (int h = 1; h <= HOURS_PER_DAY; h++) {
            // hour label
            gbc.gridx = 0; gbc.gridy = h;
            JLabel hour = new JLabel("H" + h, SwingConstants.CENTER);
            hour.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
            gridPanel.add(hour, gbc);

            for (int d = 0; d < DAYS.length; d++) {
                gbc.gridx = d + 1; gbc.gridy = h;
                String slotId = DAYS[d] + "-" + h;
                JButton btn = new JButton("<html><center>Empty<br/>" + slotId + "</center></html>");
                btn.setPreferredSize(new Dimension(150, 60));
                btn.setBackground(new Color(245, 245, 245));
                btn.setOpaque(true);
                btn.setBorder(BorderFactory.createLineBorder(Color.GRAY));
                slotButtons.put(slotId, btn);

                btn.addActionListener(e -> onSlotClicked(slotId));
                gridPanel.add(btn, gbc);
            }
        }

        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private void onSlotClicked(String slotId) {
        // If we're in selection mode for a faculty: toggle selection
        if (selectingFaculty != null) {
            JButton btn = slotButtons.get(slotId);
            if (selectingSlots.contains(slotId)) {
                selectingSlots.remove(slotId);
                btn.setBackground(new Color(220, 255, 220)); // deselect slight green -> will refresh later
                btn.setText("<html><center>Empty<br/>" + slotId + "</center></html>");
            } else {
                // check if already occupied in master timetable by someone else
                if (masterTimetable.slotMap.containsKey(slotId)) {
                    Faculty f = masterTimetable.slotMap.get(slotId);
                    appendStatus("Slot " + slotId + " already occupied by " + f.name + " (" + f.room + ")");
                    btn.setBackground(new Color(255, 200, 200)); // show conflict briefly
                    return;
                }
                // check if selectingSlots would exceed required hours
                if (selectingSlots.size() >= selectingFaculty.hours) {
                    JOptionPane.showMessageDialog(this, "You've already selected the required number of slots (" + selectingFaculty.hours + ").");
                    return;
                }
                selectingSlots.add(slotId);
                btn.setBackground(new Color(120, 200, 120)); // green
                btn.setText("<html><center>Selected<br/>" + slotId + "</center></html>");
            }
            updateSelectionInfo();
            return;
        }

        // Otherwise, show info about the slot (occupied / empty)
        if (masterTimetable.slotMap.containsKey(slotId)) {
            Faculty f = masterTimetable.slotMap.get(slotId);
            JOptionPane.showMessageDialog(this, "Slot " + slotId + "\nAssigned to: " + f.name + "\nSubject: " + f.subject + "\nRoom: " + f.room);
        } else {
            JOptionPane.showMessageDialog(this, "Slot " + slotId + " is empty.");
        }
    }

    private void enterSelectionMode() {
        String name = nameField.getText().trim();
        String subject = subjectField.getText().trim();
        String hoursText = hoursField.getText().trim();
        String room = roomField.getText().trim();

        if (name.isEmpty() || subject.isEmpty() || hoursText.isEmpty() || room.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill all fields: name, subject, hours, room.");
            return;
        }
        int hours;
        try {
            hours = Integer.parseInt(hoursText);
            if (hours <= 0 || hours > DAYS.length * HOURS_PER_DAY) {
                JOptionPane.showMessageDialog(this, "Please enter a positive hour number within reasonable bounds.");
                return;
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid hours. Enter an integer.");
            return;
        }

        selectingFaculty = new Faculty(name, subject, hours, room);
        selectingSlots.clear();
        saveFacultyButton.setEnabled(true);
        startSelectButton.setEnabled(false);

        appendStatus("Selection mode started for " + name + " (needs " + hours + " slots). Click slots to choose them.");
    }

    private void updateSelectionInfo() {
        if (selectingFaculty == null) return;
        appendStatus("Selecting for " + selectingFaculty.name + ": " + selectingSlots.size() + " / " + selectingFaculty.hours + " chosen.");
    }

    private void saveFacultyFromSelection() {
        if (selectingFaculty == null) {
            JOptionPane.showMessageDialog(this, "Not currently selecting for any faculty.");
            return;
        }
        if (selectingSlots.size() != selectingFaculty.hours) {
            JOptionPane.showMessageDialog(this, "You must select exactly " + selectingFaculty.hours + " slots. Currently selected: " + selectingSlots.size());
            return;
        }

        // Check conflicts (room + same slot)
        List<String> conflicts = new ArrayList<>();
        for (String slot : selectingSlots) {
            if (masterTimetable.slotMap.containsKey(slot)) {
                Faculty f2 = masterTimetable.slotMap.get(slot);
                // if same room, it's direct conflict
                if (f2.room.equals(selectingFaculty.room)) {
                    conflicts.add("Room conflict at " + slot + ": " + f2.name + " (room " + f2.room + ")");
                } else {
                    // just warn if same slot used by someone else (shouldn't happen because we blocked earlier)
                    conflicts.add("Slot " + slot + " already assigned to " + f2.name + " (different room " + f2.room + ").");
                }
            }
        }
        if (!conflicts.isEmpty()) {
            String msg = String.join("\n", conflicts);
            JOptionPane.showMessageDialog(this, "Conflicts found:\n" + msg);
            return;
        }

        // All good — persist the faculty and assign slots in master timetable
        selectingFaculty.slots.addAll(selectingSlots);
        facultyList.add(selectingFaculty);
        for (String slot : selectingSlots) masterTimetable.slotMap.put(slot, selectingFaculty);

        appendStatus("Saved faculty " + selectingFaculty.name + " with slots: " + selectingSlots);
        selectingFaculty = null;
        selectingSlots.clear();
        saveFacultyButton.setEnabled(false);
        startSelectButton.setEnabled(true);
        refreshGridDisplay();
    }

    private void refreshGridDisplay() {
        // Reset all buttons to default then mark occupied
        for (Map.Entry<String, JButton> e : slotButtons.entrySet()) {
            String slotId = e.getKey();
            JButton btn = e.getValue();
            if (masterTimetable.slotMap.containsKey(slotId)) {
                Faculty f = masterTimetable.slotMap.get(slotId);
                btn.setBackground(new Color(175, 225, 225)); // cyan-ish for occupied
                btn.setText("<html><center>" + f.name + "<br/>" + f.subject + "<br/>" + slotId + "<br/>R:" + f.room + "</center></html>");
            } else {
                btn.setBackground(new Color(245, 245, 245));
                btn.setText("<html><center>Empty<br/>" + slotId + "</center></html>");
            }
        }

        // If in selecting mode, highlight selected slots differently
        if (selectingFaculty != null) {
            for (String s : selectingSlots) {
                JButton b = slotButtons.get(s);
                if (b != null) {
                    b.setBackground(new Color(120, 200, 120)); // green
                    b.setText("<html><center>Selected<br/>" + s + "</center></html>");
                }
            }
        }

        // If a conflict exists (rare), mark red (we'll compute conflicts)
        Set<String> conflictSlots = masterTimetable.detectRoomConflicts(facultyList);
        for (String s : conflictSlots) {
            JButton b = slotButtons.get(s);
            if (b != null) {
                b.setBackground(new Color(255, 160, 160));
                b.setText("<html><center>Conflict<br/>" + s + "</center></html>");
            }
        }

        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private void appendStatus(String msg) {
        statusArea.append(msg + "\n");
        statusArea.setCaretPosition(statusArea.getDocument().getLength());
    }

    // ========== Auto-generate (Improved) ==========
    private void autoGenerate() {
        // Build list of free slots and shuffle to randomize
        List<String> allSlots = getAllSlots();
        List<String> freeSlots = new ArrayList<>();
        for (String s : allSlots) if (!masterTimetable.slotMap.containsKey(s)) freeSlots.add(s);
        Collections.shuffle(freeSlots, new Random());

        // Build list of target faculties: existing saved faculties + currently selecting faculty (if any)
        List<Faculty> targets = new ArrayList<>(facultyList);
        if (selectingFaculty != null && !targets.contains(selectingFaculty)) {
            // include the in-progress faculty so auto-generate can fill their remaining hours
            targets.add(selectingFaculty);
            appendStatus("Including currently-selecting faculty in auto-generation: " + selectingFaculty.name);
        }

        // Build need map (faculty -> remaining slots required)
        LinkedHashMap<Faculty, Integer> needMap = new LinkedHashMap<>();
        for (Faculty f : targets) {
            int need = f.hours - f.slots.size();
            if (need > 0) needMap.put(f, need);
        }

        if (needMap.isEmpty()) {
            appendStatus("No faculty requires additional slots. Auto-generate skipped.");
            return;
        }

        // Round-robin assign one slot per faculty to keep distribution fair
        Iterator<String> slotIt = freeSlots.iterator();
        List<Faculty> round = new ArrayList<>(needMap.keySet());
        int rrIndex = 0;

        while (slotIt.hasNext() && !needMap.isEmpty()) {
            String slot = slotIt.next();
            // pick next faculty (skip ones that no longer need slots)
            if (rrIndex >= round.size()) rrIndex = 0;
            Faculty chosen = round.get(rrIndex);

            // Safety checks: slot must still be free (it should be), and chosen must still need slots
            if (masterTimetable.slotMap.containsKey(slot)) {
                // already assigned by some race — skip
                slotIt.remove();
                rrIndex++;
                continue;
            }

            int remaining = needMap.getOrDefault(chosen, 0);
            if (remaining <= 0) {
                // remove from rotation
                needMap.remove(chosen);
                round.remove(rrIndex);
                if (round.isEmpty()) break;
                // don't advance rrIndex (it now points to next)
                continue;
            }

            // Assign slot to chosen faculty
            masterTimetable.slotMap.put(slot, chosen);
            chosen.slots.add(slot);
            remaining--;
            if (remaining <= 0) {
                needMap.remove(chosen);
                round.remove(rrIndex);
            } else {
                needMap.put(chosen, remaining);
                rrIndex++; // move to next faculty
            }
        }

        // If selectingFaculty was part of the targets but not yet saved in facultyList, persist it now
        if (selectingFaculty != null && !facultyList.contains(selectingFaculty)) {
            facultyList.add(selectingFaculty);
            appendStatus("Auto-added currently-selecting faculty to saved list: " + selectingFaculty.name);
            // clear selection mode
            selectingFaculty = null;
            selectingSlots.clear();
            saveFacultyButton.setEnabled(false);
            startSelectButton.setEnabled(true);
        }

        // Report any remaining needs
        for (Map.Entry<Faculty, Integer> e : needMap.entrySet()) {
            appendStatus("Could not assign " + e.getValue() + " slots for " + e.getKey().name + ". Insufficient free slots.");
        }

        appendStatus("Auto-generation completed. Refresh the grid to review assignments.");
    }

    private List<String> getAllSlots() {
        List<String> all = new ArrayList<>();
        for (String d : DAYS) for (int h = 1; h <= HOURS_PER_DAY; h++) all.add(d + "-" + h);
        return all;
    }

    // ========== Conflicts ==========
    private void checkConflictsShow() {
        Set<String> conflicts = masterTimetable.detectRoomConflicts(facultyList);
        if (conflicts.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No room conflicts detected.");
            appendStatus("No room conflicts.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String s : conflicts) {
                List<Faculty> facs = masterTimetable.getFacultiesAtSlot(s, facultyList);
                sb.append("Conflict at ").append(s).append(" involving: ");
                for (Faculty f : facs) sb.append(f.name).append("(R:").append(f.room).append(") ");
                sb.append("\n");
            }
            JOptionPane.showMessageDialog(this, "Conflicts detected:\n" + sb.toString());
            appendStatus("Conflicts found at: " + conflicts);
        }
    }

    // ========== Save / Load ==========
    private void saveToFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save timetable");
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f))) {
                oos.writeObject(facultyList);
                oos.writeObject(masterTimetable);
                appendStatus("Saved to " + f.getAbsolutePath());
            } catch (IOException ex) {
                appendStatus("Error saving: " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "Error saving: " + ex.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadFromFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load timetable");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
                Object o1 = ois.readObject();
                Object o2 = ois.readObject();
                if (o1 instanceof List && o2 instanceof Timetable) {
                    facultyList = (List<Faculty>) o1;
                    masterTimetable = (Timetable) o2;
                    appendStatus("Loaded from " + f.getAbsolutePath() + ". Faculties: " + facultyList.size());
                } else {
                    appendStatus("Invalid file format.");
                    JOptionPane.showMessageDialog(this, "Invalid file contents.");
                }
                refreshGridDisplay();
            } catch (IOException | ClassNotFoundException ex) {
                appendStatus("Error loading: " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "Error loading: " + ex.getMessage());
            }
        }
    }

    // ========== Helper classes ==========
    static class Faculty implements Serializable {
        String name;
        String subject;
        int hours;
        String room;
        List<String> slots = new ArrayList<>();

        Faculty(String name, String subject, int hours, String room) {
            this.name = name;
            this.subject = subject;
            this.hours = hours;
            this.room = room;
        }

        @Override
        public String toString() {
            return name + " (" + subject + ") R:" + room + " H:" + hours + " slots:" + slots;
        }
    }

    static class Timetable implements Serializable {
        // slot -> faculty
        Map<String, Faculty> slotMap = new HashMap<>();

        // Detect slots where more than one faculty occupies the same slot and at least two of them use the same room.
        // This helps if loaded data is inconsistent (e.g., duplicate entries).
        Set<String> detectRoomConflicts(List<Faculty> facultyList) {
            Set<String> conflicts = new HashSet<>();
            // Build reverse map: slot -> list of faculties (from facultyList slots)
            Map<String, List<Faculty>> reverse = new HashMap<>();
            for (Faculty f : facultyList) {
                for (String s : f.slots) {
                    reverse.computeIfAbsent(s, k -> new ArrayList<>()).add(f);
                }
            }
            // Also include slotMap if there are faculties referenced there that might not be in facultyList
            for (Map.Entry<String, Faculty> e : slotMap.entrySet()) {
                String s = e.getKey();
                Faculty f = e.getValue();
                reverse.computeIfAbsent(s, k -> new ArrayList<>());
                if (!reverse.get(s).contains(f)) reverse.get(s).add(f);
            }

            for (Map.Entry<String, List<Faculty>> e : reverse.entrySet()) {
                List<Faculty> facs = e.getValue();
                if (facs.size() > 1) {
                    // check if two or more use same room
                    Map<String, Integer> roomCount = new HashMap<>();
                    for (Faculty f : facs) {
                        roomCount.put(f.room, roomCount.getOrDefault(f.room, 0) + 1);
                    }
                    for (Map.Entry<String, Integer> rc : roomCount.entrySet()) {
                        if (rc.getValue() > 1) {
                            conflicts.add(e.getKey());
                            break;
                        }
                    }
                }
            }
            return conflicts;
        }

        // Helper to get faculties listed at a slot (for reporting)
        List<Faculty> getFacultiesAtSlot(String slot, List<Faculty> facultyList) {
            List<Faculty> res = new ArrayList<>();
            for (Faculty f : facultyList) {
                if (f.slots.contains(slot)) res.add(f);
            }
            // Also check slotMap if someone isn't in facultyList
            if (slotMap.containsKey(slot)) {
                Faculty f = slotMap.get(slot);
                if (!res.contains(f)) res.add(f);
            }
            return res;
        }
    }
    
    // ========== Main ==========
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SmartTimetableApp app = new SmartTimetableApp();
            app.setVisible(true);
            app.appendStatus("Welcome — fill the form, click 'Start Slot Selection', then click exactly the number of slots equal to weekly hours, then 'Save Faculty'.");
            app.appendStatus("Use Auto-Generate to fill remaining slots. Use Save/Load to persist.");
        });
    }
}