package org.correomqtt.core.pubsub;

import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import org.correomqtt.di.Assisted;
import org.correomqtt.di.DefaultBean;
import org.correomqtt.di.Inject;
import org.correomqtt.core.concurrent.SimpleTask;
import org.correomqtt.core.concurrent.SimpleTaskErrorResult;
import org.correomqtt.core.concurrent.TaskException;
import org.correomqtt.di.SoyEvents;
import org.correomqtt.core.model.MessageDTO;
import org.correomqtt.core.model.SubscriptionDTO;
import org.correomqtt.core.mqtt.CorreoMqttClient;
import org.correomqtt.core.plugin.PluginManager;
import org.correomqtt.core.plugin.model.MessageExtensionDTO;
import org.correomqtt.core.plugin.spi.IncomingMessageHook;
import org.correomqtt.core.plugin.spi.IncomingMessageHookDTO;
import org.correomqtt.core.transformer.MessageExtensionTransformer;
import org.correomqtt.core.utils.ConnectionManager;
import org.correomqtt.core.utils.LoggerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;


@DefaultBean
public class SubscribeTask extends SimpleTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubscribeTask.class);


    private final PluginManager pluginManager;
    private final LoggerUtils loggerUtils;
    private final ConnectionManager connectionManager;
    private final SoyEvents soyEvents;
    private final String connectionId;
    private final SubscriptionDTO subscriptionDTO;

    @Inject
    SubscribeTask(PluginManager pluginManager,
                  ConnectionManager connectionManager,
                  LoggerUtils loggerUtils,
                  SoyEvents soyEvents,
                  @Assisted String connectionId,
                  @Assisted SubscriptionDTO subscriptionDTO) {
        super(soyEvents);
        this.pluginManager = pluginManager;
        this.loggerUtils = loggerUtils;
        this.connectionManager = connectionManager;
        this.soyEvents = soyEvents;
        this.connectionId = connectionId;
        this.subscriptionDTO = subscriptionDTO;
    }

    @Override
    protected void execute() {
        CorreoMqttClient client = connectionManager.getClient(connectionId);
        try {
            client.subscribe(subscriptionDTO, this::onIncomingMessage);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new TaskException(e);
        }

        soyEvents.fireAsync(new SubscribeEvent(connectionId, subscriptionDTO));
    }

    @Override
    protected void errorHook(SimpleTaskErrorResult ignore) {
        soyEvents.fireAsync(new SubscribeFailedEvent(connectionId, subscriptionDTO));
    }

    private void onIncomingMessage(MessageDTO messageDTO) {

        MessageDTO manipulatedMessageDTO = executeOnMessageIncomingExtensions(messageDTO);
        soyEvents.fireAsync(new IncomingMessageEvent(connectionId, manipulatedMessageDTO, subscriptionDTO));
    }

    private MessageDTO executeOnMessageIncomingExtensions(MessageDTO messageDTO) {

        //TODO plugin stuff in business ... bäääh ... solve via EventBus
        MessageExtensionDTO messageExtensionDTO = new MessageExtensionDTO(messageDTO);
        for (IncomingMessageHook<?> p : pluginManager.getIncomingMessageHooks()) {
            IncomingMessageHookDTO config = p.getConfig();
            if (config != null && config.isEnableIncoming() && (config.getIncomingTopicFilter() == null ||
                    config.getIncomingTopicFilter()
                            .stream()
                            .anyMatch(tp -> MqttTopicFilter.of(tp)
                                    .matches(MqttTopic.of(messageDTO.getTopic()))
                            )
            )) {
                LOGGER.info(loggerUtils.getConnectionMarker(connectionId), "[HOOK] Manipulated incoming message on {} with {}", messageDTO.getTopic(), p.getClass().getName());
                messageExtensionDTO = p.onMessageIncoming(connectionId, messageExtensionDTO);
            }
        }
        return MessageExtensionTransformer.mergeDTO(messageExtensionDTO, messageDTO);
    }
}
