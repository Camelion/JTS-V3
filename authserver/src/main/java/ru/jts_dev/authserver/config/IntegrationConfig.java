package ru.jts_dev.authserver.config;

import io.netty.buffer.ByteBuf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.ip.IpHeaders;
import org.springframework.integration.ip.tcp.TcpReceivingChannelAdapter;
import org.springframework.integration.ip.tcp.TcpSendingMessageHandler;
import org.springframework.integration.ip.tcp.connection.AbstractServerConnectionFactory;
import org.springframework.integration.ip.tcp.connection.TcpConnectionEvent;
import org.springframework.integration.ip.tcp.connection.TcpNioServerConnectionFactory;
import org.springframework.messaging.MessageChannel;
import ru.jts_dev.authserver.config.tcp.ProtocolByteArrayLengthHeaderSerializer;
import ru.jts_dev.authserver.model.SessionKeys;
import ru.jts_dev.authserver.packets.Init;
import ru.jts_dev.authserver.packets.MessageWrapper;
import ru.jts_dev.authserver.util.Encoder;

import java.security.interfaces.RSAPublicKey;

import static java.util.Collections.singletonMap;
import static ru.jts_dev.authserver.config.KeyGenerationConfig.scrambleModulus;
import static ru.jts_dev.authserver.util.Encoder.STATIC_KEY_HEADER;

/**
 * @author Camelion
 * @since 26.11.15
 */
@Configuration
@IntegrationComponentScan
public class IntegrationConfig {
    private volatile int sessionId = 0;

    @Autowired
    private Encoder encoder;

    /**
     * Server connection factory, for game client connections.
     * Set length serializer/deserializer for packets.
     *
     * @return - server factory bean
     */
    @Bean
    public TcpNioServerConnectionFactory serverConnectionFactory() {
        TcpNioServerConnectionFactory serverConnectionFactory = new TcpNioServerConnectionFactory(2106);

        serverConnectionFactory.setDeserializer(new ProtocolByteArrayLengthHeaderSerializer());
        serverConnectionFactory.setSerializer(new ProtocolByteArrayLengthHeaderSerializer());

        return serverConnectionFactory;
    }

    @Bean
    public TcpReceivingChannelAdapter tcpIn(AbstractServerConnectionFactory serverConnectionFactory) {
        TcpReceivingChannelAdapter gateway = new TcpReceivingChannelAdapter();
        gateway.setConnectionFactory(serverConnectionFactory);
        gateway.setOutputChannel(tcpInputChannel());

        return gateway;
    }

    @Bean
    public MessageChannel tcpInputChannel() {
        return new DirectChannel();
    }

    /**
     * Endpoint for output messages.
     * Receives message from tcpOutputChannel, and send it to client with corresponding {@link IpHeaders#CONNECTION_ID}
     *
     * @param serverConnectionFactory - server factory bean
     * @return - tcp message handler bean
     */
    @Bean
    @ServiceActivator(inputChannel = "tcpOutputChannel")
    public TcpSendingMessageHandler tcpOut(AbstractServerConnectionFactory serverConnectionFactory) {
        TcpSendingMessageHandler gateway = new TcpSendingMessageHandler();
        gateway.setConnectionFactory(serverConnectionFactory);

        return gateway;
    }

    /**
     * Channel for raw object messages, unwrited and unencrypted
     *
     * @return - channel
     */
    @Bean
    public MessageChannel messageOutputChannel() {
        return new DirectChannel();
    }

    /**
     * Channel for outgoing packets
     *
     * @return - channel
     */
    @Bean
    public MessageChannel tcpOutputChannel() {
        return new PublishSubscribeChannel();
    }

    /**
     * Event listener for {@link TcpConnectionEvent}.
     * Event receives when new connection accepted.
     *
     * @param event - event instance
     */
    @EventListener
    public void tcpConnectionEventListener(TcpConnectionEvent event) {
        String connectionId = event.getConnectionId();

        SessionKeys sessionKeys = encoder.getKeysFor(connectionId);
        byte[] scrambledModulus = scrambleModulus(((RSAPublicKey) sessionKeys.getRSAKeyPair().getPublic()).getModulus());
        byte[] blowfishKey = sessionKeys.getBlowfishKey();

        // TODO: 04.12.15 sessionId++ is possible Integer overflow bug
        MessageWrapper msg = new Init(sessionId++, scrambledModulus, blowfishKey);
        msg.getHeaders().put(IpHeaders.CONNECTION_ID, event.getConnectionId());

        messageOutputChannel().send(msg);
    }

    /**
     * Outgoing message flow
     *
     * @return - complete message transformations flow
     */
    @Bean
    public IntegrationFlow sendFlow() {
        return IntegrationFlows
                .from(messageOutputChannel())
                .transform(MessageWrapper.class, msg -> {
                    msg.write();
                    return msg;
                })
                .transform(MessageWrapper.class, msg -> {
                    encoder.appendPadding(msg.getPayload());
                    return msg;
                })
                .route(MessageWrapper.class, msg -> msg instanceof Init,
                        invoker -> invoker
                                .subFlowMapping("true",
                                        sf -> sf.transform(Init.class,
                                                i -> encoder.encWithXor(i.getPayload()))
                                                .enrichHeaders(singletonMap(STATIC_KEY_HEADER, true)))
                                .subFlowMapping("false",
                                        sf -> sf.transform(MessageWrapper.class,
                                                msg -> encoder.appendChecksum(msg.getPayload()))))
                .transform(ByteBuf.class, buf -> {
                    byte[] data = new byte[buf.readableBytes()];
                    buf.readBytes(data);
                    return data;
                })
                .transform(encoder, "encrypt")
                .headerFilter(STATIC_KEY_HEADER)
                .channel(tcpOutputChannel())
                .get();
    }

    @Bean
    public IntegrationFlow recvFlow() {
        return IntegrationFlows
                .from(tcpInputChannel())
                // uncrypt
                // handle
                .transform(encoder, "decrypt")
                .get();
    }
}