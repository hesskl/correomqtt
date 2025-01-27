package org.correomqtt.gui.views.connections;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import org.correomqtt.core.CoreManager;
import org.correomqtt.core.concurrent.SimpleTaskErrorResult;
import org.correomqtt.core.connection.ConnectionStateChangedEvent;
import org.correomqtt.core.fileprovider.PersistSubscribeHistoryUpdateEvent;
import org.correomqtt.core.model.ConnectionConfigDTO;
import org.correomqtt.core.model.ControllerType;
import org.correomqtt.core.model.MessageDTO;
import org.correomqtt.core.model.MessageListViewConfig;
import org.correomqtt.core.model.Qos;
import org.correomqtt.core.model.SubscriptionDTO;
import org.correomqtt.core.pubsub.IncomingMessageEvent;
import org.correomqtt.core.pubsub.PubSubTaskFactories;
import org.correomqtt.core.pubsub.SubscribeEvent;
import org.correomqtt.core.pubsub.UnsubscribeEvent;
import org.correomqtt.di.Assisted;
import org.correomqtt.di.DefaultBean;
import org.correomqtt.di.Inject;
import org.correomqtt.di.Observes;
import org.correomqtt.gui.contextmenu.SubscriptionListMessageContextMenu;
import org.correomqtt.gui.contextmenu.SubscriptionListMessageContextMenuDelegate;
import org.correomqtt.gui.contextmenu.SubscriptionListMessageContextMenuFactory;
import org.correomqtt.gui.model.MessagePropertiesDTO;
import org.correomqtt.gui.model.SubscriptionPropertiesDTO;
import org.correomqtt.gui.theme.ThemeManager;
import org.correomqtt.gui.transformer.MessageTransformer;
import org.correomqtt.gui.transformer.SubscriptionTransformer;
import org.correomqtt.gui.utils.AlertHelper;
import org.correomqtt.gui.utils.CheckTopicHelper;
import org.correomqtt.gui.views.LoaderResult;
import org.correomqtt.gui.views.cell.QosCellFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.correomqtt.core.connection.ConnectionState.DISCONNECTED_GRACEFUL;
import static org.correomqtt.core.connection.ConnectionState.DISCONNECTED_UNGRACEFUL;

@DefaultBean
public class SubscriptionViewController extends BaseMessageBasedViewController implements
        SubscriptionListMessageContextMenuDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionViewController.class);

    private final QosCellFactory qosCellFactory;
    private final SubscriptionViewCellFactory subscriptionViewCellFactory;
    private final TopicCellFactory topicCellFactory;
    private final AlertHelper alertHelper;
    private final SubscriptionListMessageContextMenuFactory subscriptionListMessageContextMenuFactory;
    private final SubscriptionViewDelegate delegate;
    private final PubSubTaskFactories pubSubTaskFactories;
    private ResourceBundle resources;
    @FXML
    private AnchorPane subscribeBodyViewAnchor;

    @FXML
    private ComboBox<Qos> qosComboBox;

    @FXML
    private ComboBox<String> subscribeTopicComboBox;

    @FXML
    private ListView<SubscriptionPropertiesDTO> subscriptionListView;

    @FXML
    private Button unsubscribeButton;

    @FXML
    private Button unsubscribeAllButton;

    @FXML
    private Button selectAllButton;

    @FXML
    private Button selectNoneButton;
    private boolean afterSubscribe;



    @Inject
    public SubscriptionViewController(CoreManager coreManager,
                                      PubSubTaskFactories pubSubTaskFactories,
                                      ThemeManager themeManager,
                                      MessageListViewControllerFactory messageListViewControllerFactory,
                                      QosCellFactory qosCellFactory,
                                      SubscriptionViewCellFactory subscriptionViewCellFactory,
                                      TopicCellFactory topicCellFactory,
                                      AlertHelper alertHelper,
                                      SubscriptionListMessageContextMenuFactory subscriptionListMessageContextMenuFactory,
                                      @Assisted String connectionId,
                                      @Assisted SubscriptionViewDelegate delegate) {
        super(coreManager, themeManager, messageListViewControllerFactory, connectionId);
        this.pubSubTaskFactories = pubSubTaskFactories;
        this.qosCellFactory = qosCellFactory;
        this.subscriptionViewCellFactory = subscriptionViewCellFactory;
        this.topicCellFactory = topicCellFactory;
        this.alertHelper = alertHelper;
        this.subscriptionListMessageContextMenuFactory = subscriptionListMessageContextMenuFactory;
        this.delegate = delegate;
    }

    LoaderResult<SubscriptionViewController> load() {
        LoaderResult<SubscriptionViewController> result = load(SubscriptionViewController.class, "subscriptionView.fxml",
                () -> this);
        resources = result.getResourceBundle();
        return result;
    }


    @FXML
    private void initialize() {

        initMessageListView();

        qosComboBox.setItems(FXCollections.observableArrayList(Qos.values()));
        qosComboBox.getSelectionModel().selectFirst();
        qosComboBox.setCellFactory(qosCellFactory::create);


        subscriptionListView.setItems(FXCollections.observableArrayList(SubscriptionPropertiesDTO.extractor()));
        subscriptionListView.setCellFactory(this::createCell);

        unsubscribeButton.setDisable(true);
        unsubscribeAllButton.setDisable(true);
        selectAllButton.setDisable(true);
        selectNoneButton.setDisable(true);

        subscribeTopicComboBox.getEditor().lengthProperty().addListener((observable, oldValue, newValue) -> {
            CheckTopicHelper.checkSubscribeTopic(subscribeTopicComboBox, false, afterSubscribe);
            if (!newValue.toString().isEmpty()) {
                afterSubscribe = false;
            }
        });

        coreManager.getSettingsManager().getConnectionConfigs().stream()
                .filter(c -> c.getId().equals(getConnectionId()))
                .findFirst()
                .ifPresent(c -> {
                    if (!splitPane.getDividers().isEmpty()) {
                        splitPane.getDividers().get(0).setPosition(c.getConnectionUISettings().getSubscribeDividerPosition());
                    }
                    super.messageListViewController.showDetailViewButton.setSelected(c.getConnectionUISettings().isSubscribeDetailActive());
                    super.messageListViewController.controllerType = ControllerType.SUBSCRIBE;
                    if (c.getConnectionUISettings().isSubscribeDetailActive()) {
                        super.messageListViewController.showDetailView();
                        if (!super.messageListViewController.splitPane.getDividers().isEmpty()) {
                            super.messageListViewController.splitPane.getDividers().get(0).setPosition(c.getConnectionUISettings().getSubscribeDetailDividerPosition());
                        }
                    }
                });

        initTopicComboBox();

    }

    private ListCell<SubscriptionPropertiesDTO> createCell(ListView<SubscriptionPropertiesDTO> listView) {
        SubscriptionViewCell cell = subscriptionViewCellFactory.create(listView);
        SubscriptionListMessageContextMenu contextMenu = subscriptionListMessageContextMenuFactory.create(this);
        cell.setContextMenu(contextMenu);
        cell.itemProperty().addListener((observable, oldValue, newValue) -> contextMenu.setObject(cell.getItem()));
        cell.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (Boolean.TRUE.equals(newValue)) {
                onSubscriptionSelected(cell.getItem());
            }
        });
        return cell;
    }

    private void initTopicComboBox() {
        List<String> topics = coreManager.getHistoryManager().activateSubscriptionHistory(getConnectionId()).getTopics(getConnectionId());
        subscribeTopicComboBox.setItems(FXCollections.observableArrayList(topics));
        subscribeTopicComboBox.setCellFactory(topicCellFactory::create);

    }

    @FXML
    private void onSubscriptionSelected(SubscriptionPropertiesDTO subscriptionDTO) {
        unsubscribeButton.setDisable(subscriptionDTO == null);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Subscription selected '{}': {}", subscriptionDTO == null ? "N/A" : subscriptionDTO.getTopic(), getConnectionId());
        }
    }

    @FXML
    private void onClickUnsubscribe() {

        SubscriptionPropertiesDTO selectedSubscription = getSelectedSubscription();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Unsubscribe from topic '{}' clicked: {}", selectedSubscription.getTopic(), getConnectionId());
        }
        if (selectedSubscription != null) {
            unsubscribe(selectedSubscription);
        }
    }

    private SubscriptionPropertiesDTO getSelectedSubscription() {
        return subscriptionListView.getSelectionModel().getSelectedItem();
    }

    @FXML
    private void onClickUnsubscribeAll() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Unsubscribe from all topics clicked: {}", getConnectionId());
        }
        unsubscribeAll();
    }

    public void unsubscribeAll() {
        coreManager.getConnectionManager()
                .getConnection(getConnectionId())
                .getClient()
                .getSubscriptions()
                .forEach(s -> pubSubTaskFactories.getUnsubscribeFactory().create(getConnectionId(), s).run());

        subscriptionListView.getItems().clear();

        unsubscribeButton.setDisable(true);
        unsubscribeAllButton.setDisable(true);
        selectAllButton.setDisable(true);
        selectNoneButton.setDisable(true);
    }

    @FXML
    public void selectNone() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Select none topic for filter clicked: {}", getConnectionId());
        }
        subscriptionListView.getItems().forEach(subscriptionDTO -> subscriptionDTO.setFiltered(false));
    }

    @FXML
    public void selectAll() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Select all topics for filter clicked: {}", getConnectionId());
        }
        subscriptionListView.getItems().forEach(subscriptionDTO -> subscriptionDTO.setFiltered(true));
    }

    @Override
    public void filterOnly(SubscriptionPropertiesDTO dto) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Filter only topic '{}': {}", dto.getTopic(), getConnectionId());
        }
        subscriptionListView.getItems().forEach(item -> item.setFiltered(dto.equals(item)));
    }

    public void unsubscribe(SubscriptionPropertiesDTO subscriptionDTO) {
        pubSubTaskFactories.getUnsubscribeFactory().create(getConnectionId(), SubscriptionTransformer.propsToDTO(subscriptionDTO)).run();
    }

    /**
     * @param actionEvent The event given by JavaFX.
     */
    public void onClickSubscribe(@SuppressWarnings("unused") ActionEvent actionEvent) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Subscribe to topic clicked: {}", getConnectionId());
        }
        subscribe();
    }

    private void subscribe() {
        if (!CheckTopicHelper.checkSubscribeTopic(subscribeTopicComboBox, true, afterSubscribe)) {
            return;
        }

        String topic = subscribeTopicComboBox.getValue();

        if (topic == null || topic.isEmpty()) {
            LOGGER.info("Topic must not be empty");
            subscribeTopicComboBox.getStyleClass().add("emptyError");
            return;
        }

        Qos selectedQos = qosComboBox.getSelectionModel().getSelectedItem();
        pubSubTaskFactories.getSubscribeFactory().create(getConnectionId(), SubscriptionDTO.builder()
                        .topic(topic)
                        .qos(selectedQos)
                        .build())
                .onError(this::onSubscribedFailed)
                .run();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Subscribing to topic '{}': {}", topic, getConnectionId());
        }
    }

    public void onSubscribedFailed(SimpleTaskErrorResult result) {
        String msg = "Exception in business layer: " + result.getUnexpectedError().getMessage();
        alertHelper.warn(resources.getString("subscribeViewControllerSubscriptionFailedTitle") + ": ", msg);
    }

    // TODO: if all existing subscriptions are not filtered and a new comes in, no new messages are shown in the list
    // only after reclick the checkbox it works

    public void onClickSubscribeKey(KeyEvent actionEvent) {
        if (actionEvent.getCode() == KeyCode.ENTER) {
            subscribeTopicComboBox.setValue(subscribeTopicComboBox.getEditor().getText());
            if (subscribeTopicComboBox.getValue() == null) {
                return;
            }
            subscribe();
        }
    }

    @SuppressWarnings("unused")
    @Observes
    public void onMessageIncoming(IncomingMessageEvent event) {
        MessagePropertiesDTO messagePropertiesDTO = MessageTransformer.dtoToProps(event.getMessageDTO());
        messagePropertiesDTO.getSubscriptionDTOProperty().setValue(SubscriptionTransformer.dtoToProps(event.getSubscriptionDTO()));
        messageListViewController.onNewMessage(messagePropertiesDTO);
    }

    @SuppressWarnings("unused")
    @Observes
    public void onSubscribedSucceeded(SubscribeEvent event) {
        afterSubscribe = true;
        subscribeTopicComboBox.getSelectionModel().select("");

        if (event.getSubscriptionDTO().isHidden()) {
            return;
        }

        SubscriptionPropertiesDTO subscriptionPropertiesDTO = SubscriptionTransformer.dtoToProps(event.getSubscriptionDTO());
        subscriptionListView.getItems().add(0, subscriptionPropertiesDTO);
        unsubscribeAllButton.setDisable(false);
        selectAllButton.setDisable(false); //TODO disable on demand
        selectNoneButton.setDisable(false); //TODO disable on demand

        subscriptionPropertiesDTO.getFilteredProperty().addListener((observable, oldValue, newValue) -> {
            if (!Objects.equals(oldValue, newValue)) {
                updateFilter();
            }
        });
    }

    private void updateFilter() {

        Set<String> filteredTopics = subscriptionListView.getItems()
                .stream()
                .filter(dto -> dto.getFilteredProperty().getValue())
                .map(dto -> dto.getTopicProperty().getValue())
                .collect(Collectors.toSet());

        messageListViewController.setFilterPredicate(m -> {
            SubscriptionPropertiesDTO subscription = m.getSubscription();
            if (subscription == null) {
                return false;
            }
            return filteredTopics.contains(subscription.getTopic());

        });

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Filter updated {}", getConnectionId());
        }
    }

    @SuppressWarnings("unused")
    public void onConnectionChangedEvent(@Observes ConnectionStateChangedEvent event) {
        if (event.getState() == DISCONNECTED_GRACEFUL || event.getState() == DISCONNECTED_UNGRACEFUL) {
            subscriptionListView.getItems().clear();
        }
    }

    @SuppressWarnings("unused")
    @Observes(PersistSubscribeHistoryUpdateEvent.class)
    public void updateSubscriptions() {
        initTopicComboBox();
    }

    @SuppressWarnings("unused")
    public void onUnsubscribeSucceeded(@Observes UnsubscribeEvent event) {

        SubscriptionPropertiesDTO subscriptionToRemove = subscriptionListView.getItems().stream()
                .filter(s -> s.getTopic().equals(event.getSubscriptionDTO().getTopic()))
                .findFirst()
                .orElse(null);

        if (subscriptionToRemove != null) {
            subscriptionListView.getItems().remove(subscriptionToRemove);
            unsubscribeButton.setDisable(true);

            if (subscriptionListView.getItems().isEmpty()) {
                unsubscribeAllButton.setDisable(true);
                selectAllButton.setDisable(true);
                selectNoneButton.setDisable(true);
            }
        }
    }

    @Override
    public void removeMessage(MessageDTO messageDTO) {
        // nothing to do
    }

    @Override
    public void clearMessages() {
        // nothing to do
    }

    @Override
    public void setTabDirty() {
        delegate.setTabDirty();
    }

    @Override
    public void setUpToForm(MessagePropertiesDTO messageDTO) {
        delegate.setUpToForm(messageDTO);
    }

    @Override
    public Supplier<MessageListViewConfig> produceListViewConfig() {
        return () -> coreManager.getSettingsManager()
                .getConnectionConfigs()
                .stream()
                .filter(c -> c.getId().equals(getConnectionId()))
                .findFirst()
                .orElse(ConnectionConfigDTO.builder().subscribeListViewConfig(new MessageListViewConfig()).build())
                .produceSubscribeListViewConfig();

    }

    public void cleanUp() {
        this.messageListViewController.cleanUp();
    }
}

