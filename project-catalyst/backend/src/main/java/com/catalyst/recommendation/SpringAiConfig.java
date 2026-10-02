package com.catalyst.recommendation;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiConfig {

    /**
     * Spring AI ChatModel exists only under the azure / ollama profiles (spring.ai.model.chat).
     */
    @Bean
    LlmClient llmClient(ObjectProvider<ChatModel> chatModel) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            return new LlmClient() {
                public boolean isAvailable() {
                    return false;
                }

                public String complete(String s, String u) {
                    throw new AgentException("No chat model configured");
                }
            };
        }
        ChatClient client = ChatClient.create(model);
        return new LlmClient() {
            public boolean isAvailable() {
                return true;
            }

            public String complete(String system, String user) {
                return client.prompt().system(system).user(user).call().content();
            }
        };
    }
}
