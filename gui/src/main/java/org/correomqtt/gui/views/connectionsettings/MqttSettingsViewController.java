package org.correomqtt.gui.views.connectionsettings;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.controlsfx.control.textfield.CustomTextField;
import org.correomqtt.core.CoreManager;
import org.correomqtt.core.model.Auth;
import org.correomqtt.core.model.ConnectionConfigDTO;
import org.correomqtt.core.model.CorreoMqttVersion;
import org.correomqtt.core.model.GenericTranslatable;
import org.correomqtt.core.model.Lwt;
import org.correomqtt.core.model.Proxy;
import org.correomqtt.core.model.Qos;
import org.correomqtt.core.model.TlsSsl;
import org.correomqtt.di.DefaultBean;
import org.correomqtt.di.Inject;
import org.correomqtt.gui.controls.ThemedFontIcon;
import org.correomqtt.gui.model.ConnectionPropertiesDTO;
import org.correomqtt.gui.plugin.spi.LwtSettingsHook;
import org.correomqtt.gui.theme.ThemeManager;
import org.correomqtt.gui.utils.CheckTopicHelper;
import org.correomqtt.gui.views.LoaderResult;
import org.correomqtt.gui.views.base.BaseControllerImpl;
import org.correomqtt.gui.views.cell.GenericCellFactory;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ResourceBundle;
import java.util.UUID;

@DefaultBean
public class MqttSettingsViewController extends BaseControllerImpl
        implements ConnectionSettingsDelegateController,
        LwtSettingsHook.OnSettingsChangedListener {

    private static final String TEXT_FIELD = "text-field";

    private static final String TEXT_INPUT = "text-input";

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttSettingsViewController.class);

    private static final int CLIENT_ID_MAX_SIZE = 64;

    public static final String EXCLAMATION_CIRCLE_SOLID = "exclamationCircleSolid";

    public static final String EMPTY_ERROR_CLASS = "emptyError";

    private ResourceBundle resources;
    private final GenericCellFactory<CorreoMqttVersion> correoMqttVersionGenericCellFactory;
    private final GenericCellFactory<TlsSsl> tlsSslGenericCellFactory;
    private final GenericCellFactory<Proxy> proxyGenericCellFactory;
    private final GenericCellFactory<Auth> authGenericCellFactory;
    private final GenericCellFactory<Lwt> lwtGenericCellFactory;
    @FXML
    private CheckBox sslHostVerificationCheckBox;

    private ConnectionPropertiesDTO config;

    @FXML
    private CustomTextField nameTextField;

    @FXML
    private CustomTextField urlTextField;

    @FXML
    private CustomTextField portTextField;

    @FXML
    private CustomTextField clientIdTextField;

    @FXML
    private CustomTextField usernameTextField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private CheckBox cleanSessionCheckBox;

    @FXML
    private ComboBox<CorreoMqttVersion> mqttVersionComboBox;

    @FXML
    private Label internalIdLabel;

    @FXML
    private ComboBox<TlsSsl> tlsComboBox;

    @FXML
    private GridPane tlsSslGridPane;

    @FXML
    private CustomTextField sslKeystoreTextField;

    @FXML
    private CustomTextField sslKeystorePasswordTextField;

    @FXML
    private ComboBox<Proxy> proxyComboBox;

    @FXML
    private GridPane proxyGridPane;

    @FXML
    private CustomTextField sshHostTextField;

    @FXML
    private CustomTextField sshPortTextField;

    @FXML
    private CustomTextField localPortTextField;

    @FXML
    private ComboBox<Auth> authComboBox;

    @FXML
    private CustomTextField authUsernameTextField;

    @FXML
    private PasswordField authPasswordField;

    @FXML
    private HBox authKeyfileHBox;

    @FXML
    private CustomTextField authKeyFileTextField;

    @FXML
    private TabPane containerAnchorPane;

    @FXML
    private VBox lwtContentVBox;

    @FXML
    private ComboBox<Lwt> lwtComboBox;

    @FXML
    private ComboBox<String> lwtTopicComboBox;

    @FXML
    private ComboBox<Qos> lwtQoSComboBox;

    @FXML
    private HBox lwtPluginControlBox;

    @FXML
    private CheckBox lwtRetainedCheckBox;

    @FXML
    private CodeArea lwtPayloadCodeArea;

    @FXML
    private Pane lwtPayloadPane;

    @Inject
    MqttSettingsViewController(CoreManager coreManager,
                               ThemeManager themeManager,
                               GenericCellFactory<CorreoMqttVersion> correoMqttVersionGenericCellFactory,
                               GenericCellFactory<TlsSsl> tlsSslGenericCellFactory,
                               GenericCellFactory<Proxy> proxyGenericCellFactory,
                               GenericCellFactory<Auth> authGenericCellFactory,
                               GenericCellFactory<Lwt> lwtGenericCellFactory) {
        super(coreManager, themeManager);
        this.correoMqttVersionGenericCellFactory = correoMqttVersionGenericCellFactory;
        this.tlsSslGenericCellFactory = tlsSslGenericCellFactory;
        this.proxyGenericCellFactory = proxyGenericCellFactory;
        this.authGenericCellFactory = authGenericCellFactory;
        this.lwtGenericCellFactory = lwtGenericCellFactory;
    }

    public LoaderResult<MqttSettingsViewController> load() {
        LoaderResult<MqttSettingsViewController> result = load(MqttSettingsViewController.class, "mqttSettingsView.fxml",
                () -> this);
        resources = result.getResourceBundle();
        return result;
    }

    @FXML
    private void initialize() {
        containerAnchorPane.getStyleClass().add(themeManager.getIconModeCssClass());
        mqttVersionComboBox.setItems(FXCollections.observableArrayList(CorreoMqttVersion.values()));
        mqttVersionComboBox.setCellFactory(correoMqttVersionGenericCellFactory::create);
        mqttVersionComboBox.setConverter(getStringConverter());
        tlsComboBox.setItems(FXCollections.observableArrayList(TlsSsl.values()));
        tlsComboBox.setCellFactory(tlsSslGenericCellFactory::create);
        tlsComboBox.setConverter(getStringConverter());
        proxyComboBox.setItems(FXCollections.observableArrayList(Proxy.values()));
        proxyComboBox.setCellFactory(proxyGenericCellFactory::create);
        proxyComboBox.setConverter(getStringConverter());
        authComboBox.setItems(FXCollections.observableArrayList(Auth.values()));
        authComboBox.setCellFactory(authGenericCellFactory::create);
        authComboBox.setConverter(getStringConverter());
        lwtComboBox.setItems(FXCollections.observableArrayList(Lwt.values()));
        lwtComboBox.setCellFactory(lwtGenericCellFactory::create);
        lwtComboBox.setConverter(getStringConverter());
        lwtQoSComboBox.setItems(FXCollections.observableArrayList(Qos.values()));
        lwtPayloadPane.getChildren().add(new VirtualizedScrollPane<>(lwtPayloadCodeArea));
        lwtPayloadCodeArea.prefWidthProperty().bind(lwtPayloadPane.widthProperty());
        lwtPayloadCodeArea.prefHeightProperty().bind(lwtPayloadPane.heightProperty());
        coreManager.getPluginManager().getExtensions(LwtSettingsHook.class)
                .forEach(p -> p.onAddItemsToLwtSettingsBox(this, lwtPluginControlBox));
        nameTextField.lengthProperty().addListener((observable, oldValue, newValue) ->
                checkName(nameTextField, false));
        urlTextField.lengthProperty().addListener(((observable, oldValue, newValue) ->
                checkUrl(urlTextField, false)));
        portTextField.lengthProperty().addListener(((observable, oldValue, newValue) ->
                checkPort(portTextField, false)));
        clientIdTextField.lengthProperty().addListener(((observable, oldValue, newValue) ->
                checkClientID(clientIdTextField, false)));
        nameTextField.textProperty().addListener((observable, oldValue, newValue) -> setDirty(true));
        urlTextField.textProperty().addListener((observable, oldValue, newValue) -> setDirty(true));
        portTextField.textProperty().addListener((observable, oldValue, newValue) -> setDirty(true));
        clientIdTextField.textProperty().addListener((observable, oldValue, newValue) -> setDirty(true));
        passwordField.textProperty().addListener((observable, oldValue, newValue) -> setDirty(true));
        usernameTextField.textProperty().addListener((observable, oldValue, newValue) -> setDirty(true));
        cleanSessionCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> setDirty(true));
        mqttVersionComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> setDirty(true));
        tlsComboBox.getSelectionModel().selectedItemProperty().addListener(((observable, oldValue, newValue) -> {
            setDirty(true);
            if (newValue.equals(TlsSsl.OFF)) {
                tlsSslGridPane.setDisable(true);
            } else if (newValue.equals(TlsSsl.KEYSTORE)) {
                tlsSslGridPane.setDisable(false);
            }
        }));
        sslKeystoreTextField.textProperty().addListener((observable, oldValue, newValue) -> setDirty(true));
        sslKeystorePasswordTextField.textProperty().addListener((observable, oldValue, newValue) -> setDirty(true));
        sslHostVerificationCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> setDirty(true));
        proxyComboBox.getSelectionModel().selectedItemProperty().addListener(((observable, oldValue, newValue) -> {
            setDirty(true);
            if (newValue.equals(Proxy.OFF)) {
                proxyGridPane.setDisable(true);
            } else if (newValue.equals(Proxy.SSH)) {
                proxyGridPane.setDisable(false);
            }
        }));
        sshHostTextField.textProperty().addListener((observable, oldValue, newValue) -> setDirty(true));
        sshPortTextField.textProperty().addListener((observable, oldValue, newValue) -> setDirty(true));
        localPortTextField.textProperty().addListener((observable, oldValue, newValue) -> setDirty(true));
        authComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            setDirty(true);
            if (newValue.equals(Auth.OFF)) {
                authUsernameTextField.setDisable(true);
                authPasswordField.setDisable(true);
                authKeyfileHBox.setDisable(true);
            } else if (newValue.equals(Auth.PASSWORD)) {
                authUsernameTextField.setDisable(false);
                authPasswordField.setDisable(false);
                authKeyfileHBox.setDisable(true);
            } else if (newValue.equals(Auth.KEYFILE)) {
                authUsernameTextField.setDisable(false);
                authPasswordField.setDisable(true);
                authKeyfileHBox.setDisable(false);
            }
        });
        authUsernameTextField.textProperty().addListener(((observable, oldValue, newValue) -> setDirty(true)));
        authPasswordField.textProperty().addListener((observable, oldValue, newValue) -> setDirty(true));
        authKeyFileTextField.textProperty().addListener((observable, oldValue, newValue) -> setDirty(true));
        lwtComboBox.getSelectionModel().selectedItemProperty().addListener(((observable, oldValue, newValue) -> {
            setDirty(true);
            if (newValue.equals(Lwt.OFF)) {
                lwtContentVBox.setDisable(true);
            } else if (newValue.equals(Lwt.ON)) {
                lwtContentVBox.setDisable(false);
            }
        }));
        lwtTopicComboBox.getEditor().lengthProperty().addListener(((observable, oldValue, newValue) -> {
            setDirty(true);
            CheckTopicHelper.checkPublishTopic(lwtTopicComboBox, false);
        }));
        lwtQoSComboBox.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> setDirty(true));
        lwtRetainedCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> setDirty(true));
        lwtPayloadCodeArea.textProperty().addListener(((observable, oldValue, newValue) -> setDirty(true)));
        internalIdLabel.setText("");
    }

    private <T extends GenericTranslatable> StringConverter<T> getStringConverter() {
        return new StringConverter<T>() {
            @Override
            public String toString(T object) {
                if (object == null) {
                    return null;
                }
                String translationKey = object.getLabelTranslationKey();
                if (translationKey != null) {
                    return resources.getString(translationKey);
                }
                return object.toString();
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        };
    }

    private boolean doChecks() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Do form checks for connection: {}", config.getId());
        }
        boolean checksPassed = !checkName(nameTextField, true);
        checksPassed |= !checkUrl(urlTextField, true);
        checksPassed |= !checkPort(portTextField, true);
        checksPassed |= !checkClientID(clientIdTextField, true);
        if (lwtComboBox.getSelectionModel().getSelectedItem().equals(Lwt.ON)) {
            checksPassed |= !CheckTopicHelper.checkPublishTopic(lwtTopicComboBox, true);
        }
        return !checksPassed;
    }

    private void initialFill() {
        nameTextField.setText(config.getName());
        urlTextField.setText(config.getUrl());
        portTextField.setText(Integer.toString(config.getPort()));
        clientIdTextField.setText(config.getClientId());
        usernameTextField.setText(config.getUsername());
        passwordField.setText(config.getPassword());
        cleanSessionCheckBox.setSelected(config.isCleanSession());
        mqttVersionComboBox.getSelectionModel().select(config.getMqttVersion());
        tlsSslGridPane.setDisable(config.getSslProperty().getValue().equals(TlsSsl.OFF));
        tlsComboBox.getSelectionModel().select(config.getSsl());
        sslKeystoreTextField.setText(config.getSslKeystore());
        sslKeystorePasswordTextField.setText(config.getSslKeystorePassword());
        sslHostVerificationCheckBox.setSelected(config.isSslHostVerification());
        proxyGridPane.setDisable(config.getProxyProperty().getValue().equals(Proxy.OFF));
        proxyComboBox.getSelectionModel().select(config.getProxy());
        sshHostTextField.setText(config.getSshHost());
        sshPortTextField.setText(Integer.toString(config.getSshPort()));
        localPortTextField.setText(Integer.toString(config.getLocalPort()));
        if (config.getAuth().equals(Auth.OFF)) {
            authUsernameTextField.setDisable(true);
            authPasswordField.setDisable(true);
            authKeyfileHBox.setDisable(true);
        } else if (config.getAuth().equals(Auth.PASSWORD)) {
            authUsernameTextField.setDisable(false);
            authPasswordField.setDisable(false);
            authKeyfileHBox.setDisable(true);
        } else if (config.getAuth().equals(Auth.KEYFILE)) {
            authUsernameTextField.setDisable(false);
            authPasswordField.setDisable(true);
            authKeyfileHBox.setDisable(false);
        }
        authComboBox.getSelectionModel().select(config.getAuth());
        authUsernameTextField.setText(config.getAuthUsername());
        authPasswordField.setText(config.getAuthPassword());
        authKeyFileTextField.setText(config.getAuthKeyfile());
        lwtContentVBox.setDisable(config.getLwt().equals(Lwt.OFF));
        lwtComboBox.getSelectionModel().select(config.getLwt());
        lwtTopicComboBox.getEditor().setText(config.getLwtTopic());
        lwtQoSComboBox.getSelectionModel().select(config.getLwtQos());
        lwtRetainedCheckBox.setSelected(config.isLwtRetained());
        if (config.getLwtPayload() != null) {
            lwtPayloadCodeArea.replaceText(config.getLwtPayload());
        }
        internalIdLabel.setText(resources.getString("connectionSettingsViewInternalIdLabel") +
                ": " + config.getId());
        config.getDirtyProperty().set(false);
    }

    @Override
    public void setDirty(boolean dirty) {
        config.getDirtyProperty().set(dirty);
    }

    @FXML
    private void onGenerateClientIdClick() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Generating Client ID for connection clicked: {}", config.getId());
        }
        clientIdTextField.setText(UUID.randomUUID().toString());
    }

    public boolean saveConnection() {
        if (doChecks()) {
            config.getNameProperty().set(nameTextField.getText());
            config.getUrlProperty().set(urlTextField.getText());
            config.getPortProperty().set(Integer.parseInt(portTextField.getText()));
            config.getClientIdProperty().set(clientIdTextField.getText());
            config.getUsernameProperty().set(usernameTextField.getText());
            config.getPasswordProperty().set(passwordField.getText());
            config.getCleanSessionProperty().set(cleanSessionCheckBox.isSelected());
            config.getMqttVersionProperty().setValue(mqttVersionComboBox.getSelectionModel().getSelectedItem());
            config.getSslProperty().setValue(tlsComboBox.getSelectionModel().getSelectedItem());
            config.getSslKeystoreProperty().set(sslKeystoreTextField.getText());
            config.getSslKeystorePasswordProperty().set(sslKeystorePasswordTextField.getText());
            config.getSslHostVerificationProperty().set(sslHostVerificationCheckBox.isSelected());
            config.getProxyProperty().setValue(proxyComboBox.getSelectionModel().getSelectedItem());
            config.getSshHostProperty().set(sshHostTextField.getText());
            config.getSshPortProperty().set(Integer.parseInt(sshPortTextField.getText()));
            config.getLocalPortProperty().set(Integer.parseInt(localPortTextField.getText()));
            if (authComboBox.getSelectionModel().getSelectedItem().equals(Auth.PASSWORD)) {
                config.getAuthProperty().setValue(Auth.PASSWORD);
            } else if (authComboBox.getSelectionModel().getSelectedItem().equals(Auth.KEYFILE)) {
                config.getAuthProperty().setValue(Auth.KEYFILE);
            } else {
                config.getAuthProperty().setValue(Auth.OFF);
            }
            config.getAuthUsernameProperty().set(authUsernameTextField.getText());
            config.getAuthPasswordProperty().set(authPasswordField.getText());
            config.getAuthKeyfileProperty().set(authKeyFileTextField.getText());
            config.getLwtProperty().setValue(lwtComboBox.getSelectionModel().getSelectedItem());
            config.getLwtTopicProperty().set(lwtTopicComboBox.getEditor().getText());
            config.getLwtQoSProperty().setValue(lwtQoSComboBox.getSelectionModel().getSelectedItem());
            config.getLwtRetainedProperty().set(lwtRetainedCheckBox.isSelected());
            config.getLwtPayloadProperty().set(lwtPayloadCodeArea.getText());
            config.getDirtyProperty().set(false);
            config.getNewProperty().set(false);
            config.getUnpersistedProperty().set(false);
            return true;
        }
        return false;
    }

    @Override
    public ConnectionPropertiesDTO getDTO() {
        return config;
    }

    @Override
    public void setDTO(ConnectionPropertiesDTO config) {
        this.config = config;
        this.initialFill();
    }

    @Override
    public void resetDTO() {
        initialFill();
    }

    private boolean checkName(CustomTextField textField, boolean save) {
        if (isEmpty(textField)) {
            setError(textField, save, resources.getString("validationNameIsEmpty"));
            return false;
        }
        //check name collision
        for (ConnectionConfigDTO configDTO : coreManager.getConnectionManager().getSortedConnections()) {
            if (configDTO.getId().equals(config.getId())) { // I do not want to check myself.
                continue;
            }
            if (configDTO.getName().equals(nameTextField.getText())) {
                setError(textField, save, resources.getString("validationNameAlreadyUsed"));
                return false;
            }
        }
        if (textField.getText().length() > 32) {
            setError(textField, save, resources.getString("validationNameIsTooLong"));
            return false;
        }
        unsetError(textField);
        return true;
    }

    private void unsetError(CustomTextField textField) {
        textField.setRight(null);
        textField.getStyleClass().clear();
        textField.getStyleClass().addAll(TEXT_FIELD, TEXT_INPUT);
    }

    private boolean checkUrl(CustomTextField textField, boolean save) {
        if (isEmpty(textField)) {
            setError(textField, save, resources.getString("validationConnectionIsEmpty"));
            return false;
        }
        unsetError(textField);
        return true;
    }

    private boolean checkPort(CustomTextField textField, boolean save) {
        if (isEmpty(textField)) {
            setError(textField, save, resources.getString("validationPortIsEmpty"));
            return false;
        } else if (!textField.getText().matches("-?\\d+") || textField.getText().length() > 5) {
            setError(textField, save, resources.getString("validationInvalidPort"));
            return false;
        }
        unsetError(textField);
        return true;
    }

    private boolean checkClientID(CustomTextField textField, boolean save) {
        if (isEmpty(textField)) {
            setError(textField, save, resources.getString("validationClientIdIsEmpty"));
            return false;
        } else if (textField.getText().length() > CLIENT_ID_MAX_SIZE) {
            setError(textField, save, resources.getString("validationClientIdIsTooLong"));
            return false;
        }
        unsetError(textField);
        return true;
    }

    private void setError(CustomTextField textField, boolean save, String tooltipText) {
        if (save) {
            textField.getStyleClass().add("errorOnSave");
        }
        textField.setTooltip(new Tooltip(tooltipText));
        textField.setRight(new ThemedFontIcon("mdi-alert-circle", Paint.valueOf("red")));
        textField.getStyleClass().add(EXCLAMATION_CIRCLE_SOLID);
    }

    private boolean isEmpty(TextField textField) {
        return textField.getText() == null || textField.getText().isEmpty();
    }

    @FXML
    private void selectKeystore() {
        FileChooser fileChooser = new FileChooser();
        File selectedFile = fileChooser.showOpenDialog(containerAnchorPane.getScene().getWindow());
        sslKeystoreTextField.setText(selectedFile.toString());
    }

    @FXML
    private void selectKeyfile() {
        FileChooser fileChooser = new FileChooser();
        File selectedFile = fileChooser.showOpenDialog(containerAnchorPane.getScene().getWindow());
        authKeyFileTextField.setText(selectedFile.toString());
    }

    @Override
    public void cleanUp() {
        // nothing to do
    }
}