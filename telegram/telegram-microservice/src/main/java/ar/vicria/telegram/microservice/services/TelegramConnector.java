package ar.vicria.telegram.microservice.services;

import ar.vicria.telegram.microservice.properties.TelegramProperties;
import ar.vicria.telegram.microservice.services.callbacks.Query;
import ar.vicria.telegram.microservice.services.messages.TextMessage;
import ar.vicria.telegram.resources.AdapterResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramConnector extends TelegramLongPollingBot implements AdapterResource {

    private final TelegramProperties properties;

    private final List<Query> callbacks;
    private final List<TextMessage> messages;

    @PostConstruct
    private void init() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this);
        } catch (TelegramApiException e) {
            throw new RuntimeException("Unable to register telegram bot", e);
        }
    }

    @Deprecated
    @Override
    public void sendMessage(String text, String chatId) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode(text)
                .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Unable to send message", e);
        }
    }

    @Override
    @Deprecated
    public void updateText(Integer messageId, String text, String chatId) {
        EditMessageText message = EditMessageText.builder()
                .messageId(messageId)
                .chatId(chatId)
                .text(text)
                .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Unable to send message", e);
        }
    }

    /**
     * Метод получение контакта от пользователя
     *
     * @param chatId - id пользователя
     */
    @Deprecated
    private void postRequestContactMessage(String chatId) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("Для использования бота необходимо зарегистрироваться")
                .build();

        KeyboardRow row = new KeyboardRow();
        String buttonText = "Отправить свой контакт для регистрации";
        row.add(KeyboardButton.builder().text(buttonText).requestContact(true).build());
        message.setReplyMarkup(new ReplyKeyboardMarkup(Collections.singletonList(row)));

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Unable to send invite message", e);
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Message message = update.getMessage();
            log.warn("Received answer: name = {}; text = {}", message.getFrom().getFirstName(), message.getText());

            String chatId = message.getFrom().getId().toString();
            String msg = message.getText();

            SendMessage process = messages.stream()
                    .filter(m -> m.supports(msg))
                    .findFirst()
                    .map(m -> m.process(chatId))
                    .orElse(SendMessage.builder()
                            .chatId(chatId)
                            .text("Выберите пункт из меню")
                            .build());;

            try {
                execute(process);
            } catch (TelegramApiException e) {
                log.error("Unable to send message", e);
            }
        } else if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            processCallbackQuery(callbackQuery);
        }
    }

    private void processCallbackQuery(CallbackQuery callbackQuery) {
        Message message = callbackQuery.getMessage();
        String chatId = String.valueOf(callbackQuery.getMessage().getChat().getId());
        String messageText = callbackQuery.getData();

        log.error("Message from {}: {}", callbackQuery.getFrom().getUserName(), callbackQuery.getMessage().getText());

        if (AnswerData.match(messageText)) {
            AnswerData answerData = AnswerData.deserialize(messageText);
            String questionId = answerData.getQuestionId();
            log.debug("Received answer: question id = {}; answer code = {}", questionId, answerData.getAnswerCode());

            BotApiMethod msg = callbacks.stream()
                    .filter(c -> c.supports(answerData, message.getText()))
                    .distinct()
                    .findFirst()
                    .map(c -> c.process(message.getMessageId(), chatId, message.getText(), answerData))
                    .orElse(null);

            try {
                execute(msg);
            } catch (TelegramApiException e) {
                log.error("Unable to send message", e);
            }

        }
    }

    @Override
    public void onUpdatesReceived(List<Update> updates) {
        updates.forEach(this::onUpdateReceived);
    }

    @Override
    public String getBotToken() {
        return properties.getBotToken();
    }

    @Override
    public String getBotUsername() {
        return properties.getBotUserName();
    }
}
