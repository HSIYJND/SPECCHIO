package ch.specchio.gui;

import ch.specchio.client.SPECCHIOClient;
import ch.specchio.client.SPECCHIOClientException;
import ch.specchio.metadata.MDE_Spectrum_Controller;
import ch.specchio.queries.Query;
import ch.specchio.queries.QueryCondition;
import ch.specchio.queries.QueryConditionChangeInterface;
import ch.specchio.queries.SpectrumQueryCondition;
import ch.specchio.query_builder.QueryController;
import ch.specchio.types.Category;
import ch.specchio.types.attribute;
import ch.specchio.types.spectral_node_object;
import ch.specchio.types.spectrum_node;
import com.googlecode.cqengine.ConcurrentIndexedCollection;
import com.googlecode.cqengine.IndexedCollection;
import com.googlecode.cqengine.index.navigable.NavigableIndex;


import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.*;
import java.util.*;

import static com.googlecode.cqengine.query.QueryFactory.*;

public class DataSelectionPanel3 extends JPanel implements TreeSelectionListener, QueryConditionChangeInterface {
    private SPECCHIOClient specchioClient;
    private Frame frameRef;
    private SpectralDataBrowser hierarchySelect;
    private ArrayList<Integer> selectedIds;
    private JTextArea textArea;
    private MDE_Spectrum_Controller mdeSpectrumController;
    private QueryController queryController;
    private SpectrumMetadataCategoryList categoryList;
    private SpectrumFilterPanel spectrumFilterPanel;
    private Query query;
    private ArrayList<Integer> idsMatchingQuery;
    private JScrollPane filterScrollPane;
    private ArrayList<Category> availableCategories;
    private ArrayList<attribute> availableAttributes;
    private FileTransferHandler fileTransferHandler;
    private ArrayList<Integer> originalIds;
    private Boolean nothingSelected;
    private ConcurrentIndexedCollection<attribute> attributeFilteringList;
    private ConcurrentIndexedCollection<attribute> tmpAttributeFilteringList;
    private ConcurrentIndexedCollection<attribute> backupAttributeFilteringList;
    private HashMap<Integer, ConcurrentIndexedCollection<attribute>> filterArchive;

    public DataSelectionPanel3(SPECCHIOClient specchioClient, Frame frameReference){
        this.specchioClient = specchioClient;
        this.frameRef = frameReference;

        // SPECTRUM CONTROLLER
        mdeSpectrumController = new MDE_Spectrum_Controller(specchioClient);
        mdeSpectrumController.set_hierarchy_ids(new ArrayList<>(0));

        // CATEGORY LIST
        categoryList = new SpectrumMetadataCategoryList(mdeSpectrumController.getFormFactory());

        // QUERY CONTROLLER
        queryController = new QueryController(this.specchioClient, "Standard", categoryList.getFormDescriptor());
        queryController.addChangeListener(this);

        // QUERY
        // create a query object and initialise the matching ids to an empty list
        query = new Query("spectrum");
        query.addColumn("spectrum_id");
//        query.setStandardConditionFields(new ArrayList<QueryCondition>());
        nothingSelected = true;


        // DEFINE LAYOUT
        setLayout(new BorderLayout());
        initComp();
    }


    private void initComp() {
        // COMPONENTS ---->
        // SPECTRUM FILTER PANEL
        spectrumFilterPanel = new SpectrumFilterPanel(frameRef, mdeSpectrumController, queryController);
        // ADD A SCROLL PANE FOR THE FILTERPANEL
        filterScrollPane = new JScrollPane(spectrumFilterPanel);

        // HIERARCHY TREE-SELECTION
        hierarchySelect = new SpectralDataBrowser(specchioClient, false);
        hierarchySelect.build_tree();
        hierarchySelect.tree.addTreeSelectionListener(this);
        hierarchySelect.tree.setDragEnabled(true);
        query.setOrderBy(hierarchySelect.get_order_by_field());

        // TEXT PANEL
        textArea = new JTextArea();
        textArea.setDragEnabled(true);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);

        // TRANSFER HANDLERS
        fileTransferHandler = new FileTransferHandler(this);
        spectrumFilterPanel.setTransferHandler(fileTransferHandler);
        hierarchySelect.tree.setTransferHandler(fileTransferHandler);


        // ARRAYLIST FOR SELECTED SPECTRUM IDS
        selectedIds = new ArrayList<>();
        // ARRAYLIST FOR ORIGINAL SPECTRUM IDS
        originalIds = new ArrayList<>();

        // ARRAYLIST FOR MATCHING SPECTRUM IDS
        idsMatchingQuery = new ArrayList<>();
        // ARRAYLIST FOR MATCHING CATEGORIES
        availableCategories = new ArrayList<>();
        // ARRAYLIST FOR MATCHING ATTRIBUTES
        availableAttributes = new ArrayList<>();

        // FILTERING CONTAINER
        attributeFilteringList = new ConcurrentIndexedCollection<attribute>();
        tmpAttributeFilteringList = new ConcurrentIndexedCollection<attribute>();
        backupAttributeFilteringList = new ConcurrentIndexedCollection<attribute>();
        // HASHMAP AS FILTERING ARCHIVE
        filterArchive = new HashMap<>();

        // ADD TO LAYOUT
        add(hierarchySelect, BorderLayout.NORTH);
//        add(new JScrollPane(categoryList), BorderLayout.WEST);
        add(filterScrollPane, BorderLayout.CENTER);

    }

    @Override
    public void valueChanged(TreeSelectionEvent e) {

    }

    public void updateQueryBuilder(ArrayList<Integer> droppedIds) {
        try{
//            availableCategories = specchioClient.getNonNullCategories(droppedIds);
//            availableAttributes = specchioClient.getNonNullAttributes(droppedIds);
            filterArchive.clear();
            ArrayList<attribute> matchingAttributes = specchioClient.createFilterCollection(droppedIds);

//            cqEngine_attributes = new ConcurrentIndexedCollection<attribute>();
            // CREATE AN INDEX
            attributeFilteringList.addIndex(NavigableIndex.onAttribute(attribute.SPECTRUM_ID));
            attributeFilteringList.addIndex(NavigableIndex.onAttribute(attribute.INT_VALUE));
            attributeFilteringList.addIndex(NavigableIndex.onAttribute(attribute.DOUBLE_VALUE));
            attributeFilteringList.addIndex(NavigableIndex.onAttribute(attribute.NAME));
            for(attribute a : matchingAttributes){
                attributeFilteringList.add(a);
            }

            backupAttributeFilteringList =  attributeFilteringList;
            filterArchive.put(0, backupAttributeFilteringList);

            ArrayList<attribute> results = new ArrayList<>();
            com.googlecode.cqengine.query.Query<attribute> query1 = and(equal(attribute.NAME, "Irradiance Instability"), between(attribute.DOUBLE_VALUE, 0.0, 0.1));
            Iterator<attribute> matching = cqEngine_attributes.retrieve(query1).iterator();
            while(matching.hasNext()){
                results.add(matching.next());
            }
            IndexedCollection<attribute> filterStep = new ConcurrentIndexedCollection<attribute>();
            for(attribute a : results){
                filterStep.add(a);
            }


            selectedIds = droppedIds;
            spectrumFilterPanel.updateCategories(availableCategories, availableAttributes);
            nothingSelected = false;
        } catch (SPECCHIOClientException ex){
            ErrorDialog error = new ErrorDialog(this.frameRef, "Error", ex.getUserMessage(), ex);
            error.setVisible(true);
        }
    }

    public void updateBoundaries(ArrayList<Integer> filteredIds) {
        try{
            availableCategories = specchioClient.getNonNullCategories(filteredIds);
            availableAttributes = specchioClient.getNonNullAttributes(filteredIds);
            spectrumFilterPanel.updateCategories(availableCategories, availableAttributes);

        } catch (SPECCHIOClientException ex){
            ErrorDialog error = new ErrorDialog(this.frameRef, "Error", ex.getUserMessage(), ex);
            error.setVisible(true);
        }
    }


    @Override
    public void changed(Object source) {
        if(!nothingSelected) {
            query.remove_all_conditions();

            QueryController qc = (QueryController) source;
            for(QueryCondition qCond : qc.getListOfConditions()){
                query.add_condition(qCond);
            }

            SpectrumQueryCondition specCond = new SpectrumQueryCondition("spectrum", "spectrum_id");
            specCond.setValue(selectedIds);
            specCond.setOperator("in");
            query.add_condition(specCond);


            try {
                query.setQueryType(Query.SELECT_QUERY);
                idsMatchingQuery = specchioClient.getSpectrumIdsMatchingQuery(query);
//                updateBoundaries(idsMatchingQuery);
                System.out.println("NUMBER OF MATCHING SPECTRA = " + idsMatchingQuery.size());
            } catch (SPECCHIOClientException ex) {
                System.out.println(ex);
            }
        }
    }


}

class FileTransferHandler extends TransferHandler {
    DataSelectionPanel3 dataSelectionPanel;
    DataFlavor localFileFlavor = new DataFlavor(ArrayList.class, "An ArrayList Object");
   // String localFileType = localFileFlavor.

    public FileTransferHandler(DataSelectionPanel3 dataSelectionPanel) {
        this.dataSelectionPanel = dataSelectionPanel;

    }

    public boolean importData(JComponent c, Transferable t) {
        if (!canImport(c, t.getTransferDataFlavors()))
            return false;
        else if (c instanceof JTree){
            return false;
        }

        try {
            ArrayList<Integer> myIds = (ArrayList<Integer>) t.getTransferData(localFileFlavor);

            this.dataSelectionPanel.updateQueryBuilder(myIds);

            return true;
        } catch (UnsupportedFlavorException ufe) {
            System.err.println("importData: unsupported data flavor - " +
                    ufe.getMessage());
        } catch (IOException ioe) {
            System.err.println("importData IO exception: " + ioe.getMessage());
        }
        return false;
    }

    /**
     * for the JTree
     */
    public Transferable createTransferable(JComponent c) {
        if (c instanceof JTextArea) {
            return null;
        }

        JTree tree = (JTree) c;

        Set<Integer> ids = new TreeSet<Integer>();
        // process all selected nodes
        TreePath[] paths = tree.getSelectionPaths();

        // paths can be null when collapsing tree event happened
        if (paths != null) {
            for (int i = 0; i < paths.length; i++) {
                SpectralDataBrowser.SpectralDataBrowserNode bn = (SpectralDataBrowser.SpectralDataBrowserNode) paths[i].getLastPathComponent();
                spectral_node_object sn = bn.getNode();

                if (sn instanceof spectrum_node) {
                    ids.add(sn.getId()); // avoid server call
                } else {
                    ids.addAll(SPECCHIOApplication.getInstance().getClient().getSpectrumIdsForNode(bn.getNode()));
                }

            }
        }
        return new TransferableArrayList(new ArrayList<Integer>(ids));
    }

    public int getSourceActions(JComponent c) {
        return COPY;
    }

    public boolean canImport(JComponent c, DataFlavor[] flavors) {
        if (localFileFlavor == null)
            return false;

        for (int j = 0; j < flavors.length; j++) {
            if (localFileFlavor.equals(flavors[j]))
                return true;
        }
        return false;
    }

    public class TransferableArrayList implements Transferable {

        DataFlavor arrayListFlavor = new DataFlavor(ArrayList.class, "An ArrayList Object");
        DataFlavor[] supportedFlavors = {arrayListFlavor, DataFlavor.stringFlavor};

        private final ArrayList<Integer> selectedIds;

        public TransferableArrayList(ArrayList<Integer> selectedIds) {
            this.selectedIds = selectedIds;
        }


        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return supportedFlavors;
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor.equals(arrayListFlavor) || flavor.equals(DataFlavor.stringFlavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (flavor.equals(arrayListFlavor)) {
                return selectedIds;
            } else if (flavor.equals(DataFlavor.stringFlavor)) {
                return selectedIds.toString();
            } else {
                throw new UnsupportedFlavorException(flavor);
            }
        }
    }
}