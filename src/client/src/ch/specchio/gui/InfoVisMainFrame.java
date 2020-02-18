package ch.specchio.gui;import ch.specchio.client.SPECCHIOClient;import javax.swing.*;import javax.swing.event.ChangeEvent;import javax.swing.event.ChangeListener;import javax.swing.event.TreeSelectionEvent;import javax.swing.event.TreeSelectionListener;import java.awt.*;import java.awt.dnd.*;import java.awt.event.ActionEvent;import java.awt.event.ActionListener;import java.awt.event.KeyEvent;//import ptolemy.plot.Plot;public class InfoVisMainFrame extends JFrame {    private TextPanel textPanel;    private Toolbar toolbar;    private DataPanel dataPanel;    private SpectralDataBrowser hierarchyBrowser;    private JCheckBox show_only_my_data;    private JTabbedPane data_selection_tabs;//    private Plot testPlot;    public InfoVisMainFrame(){        super("InfoVis Toolbox");        SPECCHIOClient specchioClient = SPECCHIOApplication.getInstance().getClient();        setLayout(new BorderLayout());        textPanel = new TextPanel();        toolbar = new Toolbar();        dataPanel = new DataPanel();        hierarchyBrowser = new SpectralDataBrowser(specchioClient, true);        data_selection_tabs = new JTabbedPane();        setupHierarchyBrowser();//        testPlot = new Plot();        // SETUP PLOTS//        testPlot.setSize(100, 100);//        testPlot.setButtons(true);//        testPlot.setTitle("Spectral Plot");//        testPlot.setMarksStyle("none");        // SETUP HIERARCHY BROWSER:        // load all campaigns        setJMenuBar(createMenuBar());        toolbar.setStringListener(new StringListener() {            @Override            public void textEmitted(String text) {                textPanel.appendTextArea(text);            }        });        dataPanel.setDataVisListener(new DataVisListener(){            public void dataVisEventOccurred(DataVisEvent e) {                String name = e.getName();                String occupation = e.getOccupation();                int ageCat = e.getAgeCategory();                String empCat = e.getEmpCat();                if(e.isUsCitizen()) {                    String taxId = e.getTaxId();                    textPanel.appendTextArea("Name = " + name + ", Occupation = " + occupation + ", Age Category = " + ageCat + ", Employment = " + empCat +                            ", US TAX ID = " + taxId + "\n");                } else {                    textPanel.appendTextArea("Name = " + name + ", Occupation = " + occupation + ", Age Category = " + ageCat + ", Employment = " + empCat + "\n");                }               textPanel.appendTextArea(e.getGender() + "\n");            }        });//        add(dataPanel, BorderLayout.WEST);        add(hierarchyBrowser, BorderLayout.WEST);        add(textPanel, BorderLayout.CENTER);        add(toolbar, BorderLayout.NORTH);//        add(testPlot, BorderLayout.CENTER);        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);        setMinimumSize(new Dimension(500, 500));        setSize(1000,1000);        setVisible(true);    }    private void setupHierarchyBrowser() {        hierarchyBrowser.build_tree();        // add tree listener        hierarchyBrowser.tree.addTreeSelectionListener(new TreeSelectionListener() {            @Override            public void valueChanged(TreeSelectionEvent e) {            }        });        hierarchyBrowser.order_by_box.addActionListener(new ActionListener() {            @Override            public void actionPerformed(ActionEvent e) {            }        });        hierarchyBrowser.tree.setDragEnabled(true);        show_only_my_data = new JCheckBox("Show only my data.");        show_only_my_data.addChangeListener(new ChangeListener() {            @Override            public void stateChanged(ChangeEvent e) {            }        });        JPanel sdb_panel = new JPanel();        sdb_panel.setLayout(new BorderLayout());        sdb_panel.add("North", show_only_my_data);        sdb_panel.add("Center", hierarchyBrowser);        data_selection_tabs.addTab("Browser", sdb_panel);    }    private JMenuBar createMenuBar(){        JMenuBar menuBar = new JMenuBar();        JMenu fileMenu = new JMenu("File");        JMenuItem exportDataItem = new JMenuItem("Export Data...");        JMenuItem importDataItem = new JMenuItem("Import Data...");        JMenuItem exitItem = new JMenuItem("Exit");        fileMenu.add(exportDataItem);        fileMenu.add(importDataItem);        fileMenu.addSeparator();        fileMenu.add(exitItem);        JMenu windowMenu = new JMenu("Window");        JMenu showMenu = new JMenu("Show");        JCheckBoxMenuItem showFormItem = new JCheckBoxMenuItem("Spectral Data Browser");        showFormItem.setSelected(true);        windowMenu.add(showMenu);        showMenu.add(showFormItem);        menuBar.add(fileMenu);        menuBar.add(windowMenu);        showFormItem.addActionListener(new ActionListener() {            @Override            public void actionPerformed(ActionEvent e) {                JCheckBoxMenuItem menuItem = (JCheckBoxMenuItem) e.getSource();                hierarchyBrowser.setVisible(menuItem.isSelected());            }        });        fileMenu.setMnemonic(KeyEvent.VK_F);        exitItem.setMnemonic(KeyEvent.VK_X);        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.CTRL_MASK));        exitItem.addActionListener(new ActionListener() {            @Override            public void actionPerformed(ActionEvent e) {                int action = JOptionPane.showConfirmDialog(InfoVisMainFrame.this, "Quit Toolbox?", "Confirm Exit", JOptionPane.OK_CANCEL_OPTION);                if(action == JOptionPane.OK_OPTION) {                    setVisible(false);                }            }        });        JMenu selectioMenu = new JMenu("Selection");        menuBar.add(selectioMenu);        JMenu toolsMenu = new JMenu("Tools");        menuBar.add(toolsMenu);        return menuBar;    }}