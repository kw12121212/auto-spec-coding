package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MobileChannelHandleTest {

    @Test
    void recordExposesChannelAndCollector() {
        QuestionDeliveryChannel channel = new LoggingDeliveryChannel();
        QuestionReplyCollector collector = new InMemoryReplyCollector(
                new QuestionRuntime(new org.specdriven.agent.event.SimpleEventBus()));
        MobileChannelHandle handle = new MobileChannelHandle(channel, collector);
        assertSame(channel, handle.channel());
        assertSame(collector, handle.collector());
    }
}
