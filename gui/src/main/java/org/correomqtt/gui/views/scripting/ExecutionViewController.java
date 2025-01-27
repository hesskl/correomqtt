package org.correomqtt.gui.views.scripting;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;
import lombok.AllArgsConstructor;
import org.correomqtt.core.CoreManager;
import org.correomqtt.core.events.ObservesFilterNames;
import org.correomqtt.core.scripting.BaseExecutionEvent;
import org.correomqtt.core.scripting.ExecutionDTO;
import org.correomqtt.core.scripting.ScriptExecuteTaskFactories;
import org.correomqtt.core.scripting.ScriptExecutionCancelledEvent;
import org.correomqtt.core.scripting.ScriptExecutionFailedEvent;
import org.correomqtt.core.scripting.ScriptExecutionProgressEvent;
import org.correomqtt.core.scripting.ScriptExecutionSuccessEvent;
import org.correomqtt.core.scripting.ScriptExecutionsDeletedEvent;
import org.correomqtt.core.scripting.ScriptingBackend;
import org.correomqtt.di.DefaultBean;
import org.correomqtt.di.Inject;
import org.correomqtt.di.Observes;
import org.correomqtt.di.ObservesFilter;
import org.correomqtt.gui.model.ConnectionPropertiesDTO;
import org.correomqtt.gui.theme.ThemeManager;
import org.correomqtt.gui.utils.AlertHelper;
import org.correomqtt.gui.views.LoaderResult;
import org.correomqtt.gui.views.base.BaseControllerImpl;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

@DefaultBean
public class ExecutionViewController extends BaseControllerImpl {

    private ResourceBundle resources;
    private final Map<String, ScriptExecutionState> executionStates = new HashMap<>();
    private final AlertHelper alertHelper;
    private final ExecutionCellFactory executionCellFactory;
    private final SingleExecutionViewControllerFactory executionViewCtrlFactory;
    private final ScriptExecuteTaskFactories scriptExecuteTaskFactories;
    @FXML
    private AnchorPane executionSidebar;
    @FXML
    private SplitPane splitPane;
    @FXML
    private AnchorPane emptyExecution;
    @FXML
    private Label headerLabel;
    @FXML
    private Label emptyLabel;
    @FXML
    private AnchorPane executionHolder;
    @FXML
    private ListView<ExecutionPropertiesDTO> executionListView;
    private ObservableList<ExecutionPropertiesDTO> executionList;
    private FilteredList<ExecutionPropertiesDTO> filteredList;
    private String currentName;

    @AllArgsConstructor
    private static class ScriptExecutionState {
        private SingleExecutionViewController controller;
        private Region region;
    }

    @Inject
    public ExecutionViewController(CoreManager coreManager,
                                   ThemeManager themeManager,
                                   AlertHelper alertHelper,
                                   ExecutionCellFactory executionCellFactory,
                                   SingleExecutionViewControllerFactory executionViewCtrlFactory,
                                   ScriptExecuteTaskFactories scriptExecuteTaskFactories
    ) {
        super(coreManager, themeManager);
        this.alertHelper = alertHelper;
        this.executionCellFactory = executionCellFactory;
        this.executionViewCtrlFactory = executionViewCtrlFactory;
        this.scriptExecuteTaskFactories = scriptExecuteTaskFactories;
    }

    public LoaderResult<ExecutionViewController> load() {
        LoaderResult<ExecutionViewController> result = load(ExecutionViewController.class, "executionView.fxml",
                () -> this);
        resources = result.getResourceBundle();
        return result;
    }

    public void renameScript(String oldName, String newName) {
        executionList.stream()
                .filter(e -> e.getScriptFilePropertiesDTO().getName().equals(oldName))
                .forEach(e -> e.getScriptFilePropertiesDTO().getNameProperty().set(newName));
    }

    public void onClearExecutionsClicked() {
        scriptExecuteTaskFactories.getDeleteExecutionsTask().create(currentName)
                .onError(error -> alertHelper.unexpectedAlert(error.getUnexpectedError()))
                .run();

    }

    @SuppressWarnings("unused")
    @Observes(ScriptExecutionsDeletedEvent.class)
    public void onExecutionsDeleted() {
        executionList.clear();
    }

    @ObservesFilter(ObservesFilterNames.SCRIPT_NAME)
    public String getScriptFileName() {
        return currentName;
    }

    public void cleanup() {
        for (ScriptExecutionState state : executionStates.values()) {
            state.controller.cleanup();
        }
    }

    public void filterByScript(String name) {
        currentName = name;
        filteredList.setPredicate(s -> s.getScriptFilePropertiesDTO().getName().equals(name));
        executionListView.getSelectionModel().selectFirst();
        headerLabel.setText(MessageFormat.format(resources.getString("scripting.executions"), name));
        updateExistence();
    }

    private void updateExistence() {
        if (executionListView.getItems().isEmpty()) {
            emptyLabel.setText(MessageFormat.format(resources.getString("emptyScriptExecutionArea"), currentName));
            if (!splitPane.getItems().contains(emptyExecution)) {
                splitPane.getItems().add(emptyExecution);
            }
            splitPane.getItems().remove(executionSidebar);
            splitPane.getItems().remove(executionHolder);
        } else {

            splitPane.setDividerPositions(0.3, 0.7); //TODO persist and remember user choice instead of forcing

            splitPane.getItems().remove(emptyExecution);
            if (!splitPane.getItems().contains(executionSidebar)) {
                splitPane.getItems().add(executionSidebar);
            }

            if (!splitPane.getItems().contains(executionHolder)) {
                splitPane.getItems().add(executionHolder);
            }
        }
    }

    @FXML
    private void initialize() {

        executionList = FXCollections.observableArrayList(ScriptingBackend.getExecutions()
                .stream()
                .map(ExecutionTransformer::dtoToProps)
                .toList());
        filteredList = new FilteredList<>(executionList, e -> false);

        executionListView.setItems(filteredList);

        executionListView.setCellFactory(this::createExcecutionCell);
        executionListView.getSelectionModel().selectFirst();

    }

    private ListCell<ExecutionPropertiesDTO> createExcecutionCell(ListView<ExecutionPropertiesDTO> executionListView) {
        ExecutionCell cell = executionCellFactory.create(executionListView);
        cell.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (Boolean.TRUE.equals(newValue)) {
                onSelectExecution(cell.getItem());
            } else {
                if (executionListView.getSelectionModel().getSelectedIndices().isEmpty()) {
                    clearExecution();
                }
            }
        });
        return cell;
    }

    private void onSelectExecution(ExecutionPropertiesDTO selectedItem) {
        executionHolder.getChildren().clear();
        executionHolder.getChildren().add(getExecutionState(selectedItem).region);
    }

    private void clearExecution() {
        executionHolder.getChildren().clear();
    }

    private ScriptExecutionState getExecutionState(ExecutionPropertiesDTO dto) {
        return executionStates.computeIfAbsent(dto.getExecutionId(),
                id -> {
                    LoaderResult<SingleExecutionViewController> loaderResult = executionViewCtrlFactory.create(dto).load();
                    return new ScriptExecutionState(loaderResult.getController(), loaderResult.getMainRegion());
                });
    }

    public boolean addExecution(ScriptFilePropertiesDTO dto, ConnectionPropertiesDTO selectedConnection, String jsCode) {

        if (selectedConnection == null) {
            alertHelper.warn(resources.getString("scriptStartWithoutConnectionNotPossibleTitle"),
                    resources.getString("scriptStartWithoutConnectionNotPossibleContent"));
            return false;
        }

        if (dto == null) {
            alertHelper.warn(resources.getString("scriptStartWithoutConnectionNotPossibleTitle"), // TODO custom error here
                    resources.getString("scriptStartWithoutConnectionNotPossibleContent"));
            return false;
        }

        ExecutionDTO executionDTO = ExecutionDTO.builder()
                .jsCode(jsCode)
                .scriptFile(ScriptingTransformer.propsToDTO(dto))
                .connectionId(selectedConnection.getId())
                .build();

        ExecutionPropertiesDTO executionPropertyDTO = ExecutionTransformer.dtoToProps(executionDTO);
        executionList.add(0, executionPropertyDTO);
        executionListView.getSelectionModel().selectFirst();
        updateExistence();

        // Events used here to keep state in background even if window is closed in the meantime.
        scriptExecuteTaskFactories.getExecutionFactory().create(executionDTO)
                .onError(error -> alertHelper.unexpectedAlert(error.getUnexpectedError()))
                .run();
        return true;
    }

    @SuppressWarnings("unused")
    public void onScriptExecutionCancelled(@Observes ScriptExecutionCancelledEvent event) {
        handleScriptExecutionResult(event);
    }

    private void handleScriptExecutionResult(BaseExecutionEvent event) {
        ExecutionDTO dto = event.getExecutionDTO();
        if (dto == null)
            return;
        ExecutionPropertiesDTO props = executionList.stream()
                .filter(epd -> epd.getExecutionId().equals(dto.getExecutionId()))
                .findFirst()
                .orElseThrow();
        ExecutionTransformer.updatePropsByDto(props, dto);
    }

    @SuppressWarnings("unused")
    public void onScriptExecutionSuccess(@Observes ScriptExecutionSuccessEvent event) {
        handleScriptExecutionResult(event);
    }

    @SuppressWarnings("unused")
    public void onScriptExecutionFailed(@Observes ScriptExecutionFailedEvent event) {
        handleScriptExecutionResult(event);
    }

    @SuppressWarnings("unused")
    public void onScriptExecutionProgress(@Observes ScriptExecutionProgressEvent event) {
        handleScriptExecutionResult(event);
    }
}